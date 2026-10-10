package ma.schoolz.erp.accounting.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.accounting.ProcurementAccountingPort;
import ma.schoolz.erp.billing.FamilyLedgerAccountingEvent;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.identity.IdentityAuditTrail;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountingService implements ProcurementAccountingPort {
    private static final ZoneId SCHOOL_ZONE = ZoneId.of("Africa/Casablanca");
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final Set<String> ACCOUNT_TYPES =
            Set.of("ASSET", "LIABILITY", "EQUITY", "REVENUE", "EXPENSE");
    private static final Set<String> JOURNAL_TYPES =
            Set.of("SALES", "PURCHASE", "BANK", "CASH", "PAYROLL", "GENERAL");
    private static final Set<String> PAYMENT_METHODS =
            Set.of("CASH", "BANK_TRANSFER", "CHECK", "CARD", "ONLINE_PAYMENT", "OTHER");

    private final JdbcTemplate jdbc;
    private final IdentityAuditTrail audit;
    private final Clock clock;

    public AccountingService(JdbcTemplate jdbc, IdentityAuditTrail audit, Clock clock) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('accounting.read')")
    public Dashboard dashboard(SchoolPrincipal actor) {
        UUID tenantId = actor.tenantId();
        ConfigurationView configuration = configuration(tenantId);
        List<AccountView> accounts = accounts(tenantId);
        List<JournalView> journals = journals(tenantId);
        List<FiscalPeriodView> periods = periods(tenantId);
        List<CostCenterView> costCenters = costCenters(tenantId);
        List<EntryView> entries = entries(tenantId, 60);
        List<SupplierView> suppliers = suppliers(tenantId);
        List<SupplierInvoiceView> supplierInvoices = supplierInvoices(tenantId);
        List<ExpenseView> expenses = expenses(tenantId);
        List<BankAccountView> bankAccounts = bankAccounts(tenantId);
        List<ReconciliationView> reconciliations = reconciliations(tenantId);
        List<TrialBalanceLine> trialBalance = trialBalance(tenantId);
        AccountingTotals totals =
                jdbc.queryForObject(
                        "SELECT COALESCE(SUM(total_debit),0) total_debit,"
                                + "COALESCE(SUM(total_credit),0) total_credit FROM"
                                + " accounting_journal_entry WHERE tenant_id=? AND status='POSTED'",
                        (result, row) ->
                                new AccountingTotals(
                                        money(result.getBigDecimal("total_debit")),
                                        money(result.getBigDecimal("total_credit"))),
                        tenantId);
        int bridgedEntries =
                jdbc.queryForObject(
                        "SELECT count(*) FROM accounting_journal_entry"
                                + " WHERE tenant_id=? AND source_module='FAMILY_LEDGER'",
                        Integer.class,
                        tenantId);
        return new Dashboard(
                clock.instant(),
                configuration,
                totals.totalDebit(),
                totals.totalCredit(),
                totals.totalDebit().compareTo(totals.totalCredit()) == 0,
                bridgedEntries,
                accounts,
                journals,
                periods,
                costCenters,
                entries,
                suppliers,
                supplierInvoices,
                expenses,
                bankAccounts,
                reconciliations,
                trialBalance);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('procurement.read')")
    public ProcurementAccountingPort.ReferenceData procurementReferences(SchoolPrincipal actor) {
        ProcurementReferenceData internal = procurementReferenceData(actor);
        return new ProcurementAccountingPort.ReferenceData(
                internal.suppliers().stream().map(this::procurementSupplier).toList(),
                internal.journals().stream()
                        .map(
                                item ->
                                        new ProcurementAccountingPort.JournalView(
                                                item.id(),
                                                item.code(),
                                                item.name(),
                                                item.journalType(),
                                                item.active()))
                        .toList(),
                internal.accounts().stream()
                        .map(
                                item ->
                                        new ProcurementAccountingPort.AccountView(
                                                item.id(),
                                                item.parentAccountId(),
                                                item.code(),
                                                item.name(),
                                                item.accountType(),
                                                item.description(),
                                                item.allowPosting(),
                                                item.active()))
                        .toList(),
                internal.costCenters().stream()
                        .map(
                                item ->
                                        new ProcurementAccountingPort.CostCenterView(
                                                item.id(),
                                                item.code(),
                                                item.name(),
                                                item.description(),
                                                item.active()))
                        .toList(),
                internal.supplierInvoices().stream().map(this::procurementInvoice).toList(),
                internal.expenses().stream().map(this::procurementExpense).toList());
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('procurement.configure')")
    public ProcurementAccountingPort.SupplierView createProcurementSupplier(
            SchoolPrincipal actor,
            ProcurementAccountingPort.SupplierCommand command,
            String ipAddress) {
        SupplierView supplier =
                createSupplier(
                        actor,
                        new SupplierCommand(
                                command.code(),
                                command.name(),
                                command.taxIdentifier(),
                                command.email(),
                                command.phone(),
                                command.address()),
                        ipAddress);
        return procurementSupplier(supplier);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('procurement.invoice')")
    public ProcurementAccountingPort.SupplierInvoiceView postProcurementInvoice(
            SchoolPrincipal actor,
            ProcurementAccountingPort.SupplierInvoiceCommand command,
            String ipAddress) {
        SupplierInvoiceView invoice =
                recordProcurementInvoice(
                        actor,
                        new SupplierInvoiceCommand(
                                command.supplierId(),
                                command.journalId(),
                                command.expenseAccountId(),
                                command.payableAccountId(),
                                command.costCenterId(),
                                command.invoiceNumber(),
                                command.invoiceDate(),
                                command.dueDate(),
                                command.description(),
                                command.totalAmount(),
                                command.currency()),
                        ipAddress);
        return procurementInvoice(invoice);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('procurement.invoice')")
    public ProcurementAccountingPort.ExpenseView postProcurementExpense(
            SchoolPrincipal actor,
            ProcurementAccountingPort.ExpenseCommand command,
            String ipAddress) {
        ExpenseView expense =
                recordProcurementExpense(
                        actor,
                        new ExpenseCommand(
                                command.supplierId(),
                                command.journalId(),
                                command.expenseAccountId(),
                                command.paymentAccountId(),
                                command.costCenterId(),
                                command.expenseReference(),
                                command.expenseDate(),
                                command.description(),
                                command.amount(),
                                command.currency(),
                                command.paymentMethod()),
                        ipAddress);
        return procurementExpense(expense);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('procurement.pay')")
    public ProcurementAccountingPort.EntryResult postProcurementPayment(
            SchoolPrincipal actor, ProcurementAccountingPort.PaymentPosting command) {
        EntryView entry =
                recordProcurementPayment(
                        actor,
                        new ProcurementPaymentPosting(
                                command.paymentId(),
                                command.supplierId(),
                                command.journalId(),
                                command.payableAccountId(),
                                command.paymentAccountId(),
                                command.paidOn(),
                                command.reference(),
                                command.description(),
                                command.amount(),
                                command.currency()));
        return new ProcurementAccountingPort.EntryResult(
                entry.id(), entry.entryNumber(), entry.totalDebit(), entry.totalCredit());
    }

    @Transactional
    @PreAuthorize("hasAuthority('accounting.configure')")
    public ConfigurationView configure(
            SchoolPrincipal actor, ConfigurationCommand command, String ipAddress) {
        if (command == null) {
            throw new AccountingValidationException("La configuration est requise");
        }
        String currency = currency(command.baseCurrency());
        Instant now = clock.instant();
        int updated =
                jdbc.update(
                        "UPDATE accounting_configuration SET enabled=?,base_currency=?,"
                                + "family_ledger_auto_post=?,updated_at=? WHERE tenant_id=?",
                        command.enabled(),
                        currency,
                        command.familyLedgerAutoPost(),
                        timestamp(now),
                        actor.tenantId());
        if (updated == 0) {
            jdbc.update(
                    "INSERT INTO accounting_configuration"
                        + " (tenant_id,enabled,base_currency,family_ledger_auto_post,created_at,updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                    actor.tenantId(),
                    command.enabled(),
                    currency,
                    command.familyLedgerAutoPost(),
                    timestamp(now),
                    timestamp(now));
        }
        ConfigurationView result = configuration(actor.tenantId());
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "accounting.configuration.updated",
                "AccountingConfiguration",
                actor.tenantId().toString(),
                ipAddress,
                Map.of(
                        "enabled",
                        result.enabled(),
                        "familyLedgerAutoPost",
                        result.familyLedgerAutoPost()),
                null,
                result);
        return result;
    }

    @Transactional
    @PreAuthorize("hasAuthority('accounting.configure')")
    public AccountView createAccount(
            SchoolPrincipal actor, AccountCommand command, String ipAddress) {
        if (command == null) {
            throw new AccountingValidationException("Le compte est requis");
        }
        String code = required(command.code(), 40, "Le code du compte").toUpperCase(Locale.ROOT);
        String name = required(command.name(), 180, "Le nom du compte");
        String type = enumValue(command.accountType(), ACCOUNT_TYPES, "Le type de compte");
        if (command.parentAccountId() != null) {
            account(actor.tenantId(), command.parentAccountId(), false);
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                "INSERT INTO accounting_chart_of_account"
                    + " (id,tenant_id,parent_account_id,code,name,account_type,description,allow_posting,active,created_at,updated_at)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?)",
                id,
                actor.tenantId(),
                command.parentAccountId(),
                code,
                name,
                type,
                optional(command.description(), 700),
                command.allowPosting(),
                timestamp(now),
                timestamp(now));
        AccountView result = account(actor.tenantId(), id, false);
        auditCreate(actor, "accounting.account.created", "ChartOfAccount", id, ipAddress, result);
        return result;
    }

    @Transactional
    @PreAuthorize("hasAuthority('accounting.configure')")
    public JournalView createJournal(
            SchoolPrincipal actor, JournalCommand command, String ipAddress) {
        if (command == null) {
            throw new AccountingValidationException("Le journal est requis");
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                "INSERT INTO accounting_journal"
                        + " (id,tenant_id,code,name,journal_type,active,created_at,updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, TRUE, ?, ?)",
                id,
                actor.tenantId(),
                required(command.code(), 30, "Le code du journal").toUpperCase(Locale.ROOT),
                required(command.name(), 180, "Le nom du journal"),
                enumValue(command.journalType(), JOURNAL_TYPES, "Le type de journal"),
                timestamp(now),
                timestamp(now));
        JournalView result = journal(actor.tenantId(), id);
        auditCreate(actor, "accounting.journal.created", "Journal", id, ipAddress, result);
        return result;
    }

    @Transactional
    @PreAuthorize("hasAuthority('accounting.configure')")
    public FiscalPeriodView createFiscalPeriod(
            SchoolPrincipal actor, FiscalPeriodCommand command, String ipAddress) {
        if (command == null || command.startsOn() == null || command.endsOn() == null) {
            throw new AccountingValidationException("Les dates de la période sont requises");
        }
        if (command.endsOn().isBefore(command.startsOn())) {
            throw new AccountingValidationException("La fin de période précède son début");
        }
        int overlaps =
                jdbc.queryForObject(
                        "SELECT count(*) FROM accounting_fiscal_period WHERE tenant_id=?"
                                + " AND starts_on<=? AND ends_on>=?",
                        Integer.class,
                        actor.tenantId(),
                        command.endsOn(),
                        command.startsOn());
        if (overlaps > 0) {
            throw new AccountingConflictException("Cette période chevauche une période existante");
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                "INSERT INTO accounting_fiscal_period"
                    + " (id,tenant_id,code,label,starts_on,ends_on,status,closed_by_user_id,closed_at,created_at,updated_at)"
                    + " VALUES (?, ?, ?, ?, ?, ?, 'OPEN', NULL, NULL, ?, ?)",
                id,
                actor.tenantId(),
                required(command.code(), 40, "Le code de période").toUpperCase(Locale.ROOT),
                required(command.label(), 180, "Le libellé de période"),
                command.startsOn(),
                command.endsOn(),
                timestamp(now),
                timestamp(now));
        FiscalPeriodView result = fiscalPeriod(actor.tenantId(), id);
        auditCreate(actor, "accounting.period.created", "FiscalPeriod", id, ipAddress, result);
        return result;
    }

    @Transactional
    @PreAuthorize("hasAuthority('accounting.configure')")
    public FiscalPeriodView closeFiscalPeriod(
            SchoolPrincipal actor, UUID periodId, String ipAddress) {
        FiscalPeriodView before = fiscalPeriod(actor.tenantId(), periodId);
        if ("CLOSED".equals(before.status())) {
            return before;
        }
        Instant now = clock.instant();
        jdbc.update(
                "UPDATE accounting_fiscal_period SET"
                        + " status='CLOSED',closed_by_user_id=?,closed_at=?,updated_at=? WHERE"
                        + " tenant_id=? AND id=? AND status='OPEN'",
                actor.accountId(),
                timestamp(now),
                timestamp(now),
                actor.tenantId(),
                periodId);
        FiscalPeriodView result = fiscalPeriod(actor.tenantId(), periodId);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "accounting.period.closed",
                "FiscalPeriod",
                periodId.toString(),
                ipAddress,
                Map.of(),
                before,
                result);
        return result;
    }

    @Transactional
    @PreAuthorize("hasAuthority('accounting.configure')")
    public CostCenterView createCostCenter(
            SchoolPrincipal actor, CostCenterCommand command, String ipAddress) {
        if (command == null) {
            throw new AccountingValidationException("Le centre de coût est requis");
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                "INSERT INTO accounting_cost_center"
                        + " (id,tenant_id,code,name,description,active,created_at,updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, TRUE, ?, ?)",
                id,
                actor.tenantId(),
                required(command.code(), 40, "Le code du centre").toUpperCase(Locale.ROOT),
                required(command.name(), 180, "Le nom du centre"),
                optional(command.description(), 700),
                timestamp(now),
                timestamp(now));
        CostCenterView result = costCenter(actor.tenantId(), id);
        auditCreate(actor, "accounting.cost-center.created", "CostCenter", id, ipAddress, result);
        return result;
    }

    @Transactional
    @PreAuthorize("hasAuthority('accounting.configure') or hasAuthority('procurement.configure')")
    public SupplierView createSupplier(
            SchoolPrincipal actor, SupplierCommand command, String ipAddress) {
        if (command == null) {
            throw new AccountingValidationException("Le fournisseur est requis");
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                "INSERT INTO accounting_supplier"
                    + " (id,tenant_id,code,name,tax_identifier,email,phone,address,status,created_at,updated_at)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)",
                id,
                actor.tenantId(),
                required(command.code(), 40, "Le code fournisseur").toUpperCase(Locale.ROOT),
                required(command.name(), 180, "Le nom du fournisseur"),
                optional(command.taxIdentifier(), 80),
                optional(command.email(), 254),
                optional(command.phone(), 60),
                optional(command.address(), 700),
                timestamp(now),
                timestamp(now));
        SupplierView result = supplier(actor.tenantId(), id);
        auditCreate(actor, "accounting.supplier.created", "Supplier", id, ipAddress, result);
        return result;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('procurement.read')")
    public ProcurementReferenceData procurementReferenceData(SchoolPrincipal actor) {
        return new ProcurementReferenceData(
                suppliers(actor.tenantId()),
                journals(actor.tenantId()),
                accounts(actor.tenantId()),
                costCenters(actor.tenantId()),
                supplierInvoices(actor.tenantId()),
                expenses(actor.tenantId()));
    }

    @Transactional
    @PreAuthorize("hasAuthority('procurement.invoice')")
    public SupplierInvoiceView recordProcurementInvoice(
            SchoolPrincipal actor, SupplierInvoiceCommand command, String ipAddress) {
        SupplierInvoiceView invoice = createSupplierInvoice(actor, command, ipAddress);
        return postSupplierInvoice(actor, invoice.id(), ipAddress);
    }

    @Transactional
    @PreAuthorize("hasAuthority('procurement.invoice')")
    public ExpenseView recordProcurementExpense(
            SchoolPrincipal actor, ExpenseCommand command, String ipAddress) {
        ExpenseView expense = createExpense(actor, command, ipAddress);
        return postExpense(actor, expense.id(), ipAddress);
    }

    @Transactional
    @PreAuthorize("hasAuthority('procurement.pay')")
    public EntryView recordProcurementPayment(
            SchoolPrincipal actor, ProcurementPaymentPosting command) {
        if (command == null
                || command.paymentId() == null
                || command.supplierId() == null
                || command.journalId() == null
                || command.payableAccountId() == null
                || command.paymentAccountId() == null
                || command.paidOn() == null) {
            throw new AccountingValidationException(
                    "Le paiement fournisseur et ses comptes sont requis");
        }
        supplier(actor.tenantId(), command.supplierId());
        journal(actor.tenantId(), command.journalId());
        requireAccountType(actor.tenantId(), command.payableAccountId(), "LIABILITY");
        requireAccountType(actor.tenantId(), command.paymentAccountId(), "ASSET");
        BigDecimal amount = positiveMoney(command.amount(), "Le montant du paiement");
        String reference = required(command.reference(), 90, "La référence du paiement");
        String description = required(command.description(), 700, "La description du paiement");
        return postBalancedEntry(
                actor.tenantId(),
                actor.accountId(),
                command.journalId(),
                command.paidOn(),
                reference,
                description,
                "PROCUREMENT",
                "SUPPLIER_PAYMENT",
                command.paymentId().toString(),
                currency(command.currency()),
                List.of(
                        new PostingLine(
                                command.payableAccountId(),
                                null,
                                "Extinction dette " + reference,
                                amount,
                                ZERO),
                        new PostingLine(
                                command.paymentAccountId(), null, description, ZERO, amount)));
    }

    @Transactional
    @PreAuthorize("hasAuthority('accounting.configure')")
    public BankAccountView createBankAccount(
            SchoolPrincipal actor, BankAccountCommand command, String ipAddress) {
        if (command == null || command.ledgerAccountId() == null || command.journalId() == null) {
            throw new AccountingValidationException(
                    "Le compte comptable et le journal sont requis");
        }
        AccountView account = account(actor.tenantId(), command.ledgerAccountId(), true);
        if (!"ASSET".equals(account.accountType())) {
            throw new AccountingValidationException(
                    "Un compte bancaire doit utiliser un compte d’actif");
        }
        journal(actor.tenantId(), command.journalId());
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                "INSERT INTO accounting_bank_account"
                    + " (id,tenant_id,ledger_account_id,journal_id,code,bank_name,account_label,iban_masked,currency,active,created_at,updated_at)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?)",
                id,
                actor.tenantId(),
                command.ledgerAccountId(),
                command.journalId(),
                required(command.code(), 40, "Le code bancaire").toUpperCase(Locale.ROOT),
                required(command.bankName(), 180, "Le nom de la banque"),
                required(command.accountLabel(), 180, "Le libellé du compte"),
                optional(command.ibanMasked(), 80),
                currency(command.currency()),
                timestamp(now),
                timestamp(now));
        BankAccountView result = bankAccount(actor.tenantId(), id);
        auditCreate(actor, "accounting.bank-account.created", "BankAccount", id, ipAddress, result);
        return result;
    }

    @Transactional
    @PreAuthorize("hasAuthority('accounting.post')")
    public SupplierInvoiceView createSupplierInvoice(
            SchoolPrincipal actor, SupplierInvoiceCommand command, String ipAddress) {
        validateSupplierInvoiceCommand(command);
        supplier(actor.tenantId(), command.supplierId());
        journal(actor.tenantId(), command.journalId());
        requireAccountType(actor.tenantId(), command.expenseAccountId(), "EXPENSE");
        requireAccountType(actor.tenantId(), command.payableAccountId(), "LIABILITY");
        if (command.costCenterId() != null) {
            costCenter(actor.tenantId(), command.costCenterId());
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                "INSERT INTO accounting_supplier_invoice"
                    + " (id,tenant_id,supplier_id,journal_id,expense_account_id,payable_account_id,"
                    + "cost_center_id,journal_entry_id,invoice_number,invoice_date,due_date,description,total_amount,currency,status,created_at,posted_at)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, NULL)",
                id,
                actor.tenantId(),
                command.supplierId(),
                command.journalId(),
                command.expenseAccountId(),
                command.payableAccountId(),
                command.costCenterId(),
                required(command.invoiceNumber(), 90, "Le numéro de facture"),
                command.invoiceDate(),
                command.dueDate(),
                required(command.description(), 700, "La description"),
                positiveMoney(command.totalAmount(), "Le total"),
                currency(command.currency()),
                timestamp(now));
        SupplierInvoiceView result = supplierInvoice(actor.tenantId(), id);
        auditCreate(
                actor,
                "accounting.supplier-invoice.created",
                "SupplierInvoice",
                id,
                ipAddress,
                result);
        return result;
    }

    @Transactional
    @PreAuthorize("hasAuthority('accounting.post')")
    public SupplierInvoiceView postSupplierInvoice(
            SchoolPrincipal actor, UUID invoiceId, String ipAddress) {
        SupplierInvoiceView before = supplierInvoice(actor.tenantId(), invoiceId);
        if (!"DRAFT".equals(before.status())) {
            throw new AccountingConflictException(
                    "Seule une facture brouillon peut être comptabilisée");
        }
        EntryView entry =
                postBalancedEntry(
                        actor.tenantId(),
                        actor.accountId(),
                        before.journalId(),
                        before.invoiceDate(),
                        before.invoiceNumber(),
                        before.description(),
                        "SUPPLIER",
                        "SUPPLIER_INVOICE",
                        before.id().toString(),
                        before.currency(),
                        List.of(
                                new PostingLine(
                                        before.expenseAccountId(),
                                        before.costCenterId(),
                                        before.description(),
                                        before.totalAmount(),
                                        ZERO),
                                new PostingLine(
                                        before.payableAccountId(),
                                        null,
                                        "Dette fournisseur " + before.invoiceNumber(),
                                        ZERO,
                                        before.totalAmount())));
        Instant now = clock.instant();
        jdbc.update(
                "UPDATE accounting_supplier_invoice SET"
                    + " status='POSTED',journal_entry_id=?,posted_at=? WHERE tenant_id=? AND id=?"
                    + " AND status='DRAFT'",
                entry.id(),
                timestamp(now),
                actor.tenantId(),
                invoiceId);
        SupplierInvoiceView result = supplierInvoice(actor.tenantId(), invoiceId);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "accounting.supplier-invoice.posted",
                "SupplierInvoice",
                invoiceId.toString(),
                ipAddress,
                Map.of("entryNumber", entry.entryNumber()),
                before,
                result);
        return result;
    }

    @Transactional
    @PreAuthorize("hasAuthority('accounting.post')")
    public ExpenseView createExpense(
            SchoolPrincipal actor, ExpenseCommand command, String ipAddress) {
        if (command == null
                || command.journalId() == null
                || command.expenseAccountId() == null
                || command.paymentAccountId() == null
                || command.expenseDate() == null) {
            throw new AccountingValidationException(
                    "Les comptes, le journal et la date sont requis");
        }
        if (command.supplierId() != null) {
            supplier(actor.tenantId(), command.supplierId());
        }
        journal(actor.tenantId(), command.journalId());
        requireAccountType(actor.tenantId(), command.expenseAccountId(), "EXPENSE");
        account(actor.tenantId(), command.paymentAccountId(), true);
        if (command.costCenterId() != null) {
            costCenter(actor.tenantId(), command.costCenterId());
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        String reference =
                command.expenseReference() == null || command.expenseReference().isBlank()
                        ? "DEP-" + id.toString().substring(0, 8).toUpperCase(Locale.ROOT)
                        : required(command.expenseReference(), 90, "La référence");
        jdbc.update(
                "INSERT INTO accounting_expense"
                    + " (id,tenant_id,supplier_id,journal_id,expense_account_id,payment_account_id,"
                    + "cost_center_id,journal_entry_id,expense_reference,expense_date,description,amount,currency,payment_method,status,created_at,posted_at)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, NULL)",
                id,
                actor.tenantId(),
                command.supplierId(),
                command.journalId(),
                command.expenseAccountId(),
                command.paymentAccountId(),
                command.costCenterId(),
                reference,
                command.expenseDate(),
                required(command.description(), 700, "La description"),
                positiveMoney(command.amount(), "Le montant"),
                currency(command.currency()),
                enumValue(command.paymentMethod(), PAYMENT_METHODS, "Le mode de paiement"),
                timestamp(now));
        ExpenseView result = expense(actor.tenantId(), id);
        auditCreate(actor, "accounting.expense.created", "Expense", id, ipAddress, result);
        return result;
    }

    @Transactional
    @PreAuthorize("hasAuthority('accounting.post')")
    public ExpenseView postExpense(SchoolPrincipal actor, UUID expenseId, String ipAddress) {
        ExpenseView before = expense(actor.tenantId(), expenseId);
        if (!"DRAFT".equals(before.status())) {
            throw new AccountingConflictException(
                    "Seule une dépense brouillon peut être comptabilisée");
        }
        EntryView entry =
                postBalancedEntry(
                        actor.tenantId(),
                        actor.accountId(),
                        before.journalId(),
                        before.expenseDate(),
                        before.expenseReference(),
                        before.description(),
                        "EXPENSE",
                        "EXPENSE",
                        before.id().toString(),
                        before.currency(),
                        List.of(
                                new PostingLine(
                                        before.expenseAccountId(),
                                        before.costCenterId(),
                                        before.description(),
                                        before.amount(),
                                        ZERO),
                                new PostingLine(
                                        before.paymentAccountId(),
                                        null,
                                        "Paiement " + before.expenseReference(),
                                        ZERO,
                                        before.amount())));
        jdbc.update(
                "UPDATE accounting_expense SET status='POSTED',journal_entry_id=?,posted_at=?"
                        + " WHERE tenant_id=? AND id=? AND status='DRAFT'",
                entry.id(),
                timestamp(clock.instant()),
                actor.tenantId(),
                expenseId);
        ExpenseView result = expense(actor.tenantId(), expenseId);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "accounting.expense.posted",
                "Expense",
                expenseId.toString(),
                ipAddress,
                Map.of("entryNumber", entry.entryNumber()),
                before,
                result);
        return result;
    }

    @Transactional
    @PreAuthorize("hasAuthority('accounting.post')")
    public EntryView reverseEntry(
            SchoolPrincipal actor, UUID entryId, ReversalCommand command, String ipAddress) {
        EntryView before = entry(actor.tenantId(), entryId);
        if ("REVERSED".equals(before.status())) {
            throw new AccountingConflictException("Cette écriture est déjà extournée");
        }
        String reason = required(command == null ? null : command.reason(), 700, "Le motif");
        List<PostingLine> reverseLines =
                before.lines().stream()
                        .map(
                                line ->
                                        new PostingLine(
                                                line.accountId(),
                                                line.costCenterId(),
                                                "Extourne — " + line.label(),
                                                line.creditAmount(),
                                                line.debitAmount()))
                        .toList();
        EntryView reversal =
                postBalancedEntry(
                        actor.tenantId(),
                        actor.accountId(),
                        before.journalId(),
                        LocalDate.now(clock.withZone(SCHOOL_ZONE)),
                        "EXT-" + before.entryNumber(),
                        reason,
                        "ACCOUNTING",
                        "REVERSAL",
                        before.id().toString(),
                        before.currency(),
                        reverseLines);
        jdbc.update(
                "UPDATE accounting_journal_entry SET status='REVERSED',reversed_at=?"
                        + " WHERE tenant_id=? AND id=? AND status='POSTED'",
                timestamp(clock.instant()),
                actor.tenantId(),
                entryId);
        jdbc.update(
                "UPDATE accounting_journal_entry SET reversal_of_entry_id=? WHERE tenant_id=? AND"
                        + " id=?",
                entryId,
                actor.tenantId(),
                reversal.id());
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "accounting.entry.reversed",
                "JournalEntry",
                entryId.toString(),
                ipAddress,
                Map.of("reversalEntryNumber", reversal.entryNumber(), "reason", reason),
                before,
                reversal);
        return entry(actor.tenantId(), reversal.id());
    }

    @Transactional
    @PreAuthorize("hasAuthority('accounting.reconcile')")
    public ReconciliationView createReconciliation(
            SchoolPrincipal actor, ReconciliationCommand command, String ipAddress) {
        if (command == null
                || command.bankAccountId() == null
                || command.startsOn() == null
                || command.endsOn() == null) {
            throw new AccountingValidationException("Le compte et les dates du relevé sont requis");
        }
        if (command.endsOn().isBefore(command.startsOn())) {
            throw new AccountingValidationException("La fin du relevé précède son début");
        }
        bankAccount(actor.tenantId(), command.bankAccountId());
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                "INSERT INTO accounting_reconciliation"
                    + " (id,tenant_id,bank_account_id,statement_reference,starts_on,ends_on,"
                    + "opening_balance,closing_balance,matched_amount,status,completed_by_user_id,completed_at,created_at,updated_at)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, 'DRAFT', NULL, NULL, ?, ?)",
                id,
                actor.tenantId(),
                command.bankAccountId(),
                required(command.statementReference(), 100, "La référence du relevé"),
                command.startsOn(),
                command.endsOn(),
                money(command.openingBalance()),
                money(command.closingBalance()),
                timestamp(now),
                timestamp(now));
        ReconciliationView result = reconciliation(actor.tenantId(), id);
        auditCreate(
                actor,
                "accounting.reconciliation.created",
                "Reconciliation",
                id,
                ipAddress,
                result);
        return result;
    }

    @Transactional
    @PreAuthorize("hasAuthority('accounting.reconcile')")
    public ReconciliationView matchReconciliationEntry(
            SchoolPrincipal actor,
            UUID reconciliationId,
            UUID entryId,
            MatchCommand command,
            String ipAddress) {
        ReconciliationView before = reconciliation(actor.tenantId(), reconciliationId);
        if (!"DRAFT".equals(before.status())) {
            throw new AccountingConflictException("Un rapprochement terminé est immuable");
        }
        EntryView entry = entry(actor.tenantId(), entryId);
        BankAccountView bank = bankAccount(actor.tenantId(), before.bankAccountId());
        BigDecimal bankMovement =
                entry.lines().stream()
                        .filter(line -> line.accountId().equals(bank.ledgerAccountId()))
                        .map(line -> line.debitAmount().max(line.creditAmount()))
                        .reduce(ZERO, BigDecimal::add);
        if (bankMovement.signum() <= 0) {
            throw new AccountingValidationException(
                    "L’écriture ne contient aucun mouvement sur ce compte bancaire");
        }
        BigDecimal amount =
                command == null || command.amount() == null
                        ? money(bankMovement)
                        : positiveMoney(command.amount(), "Le montant rapproché");
        if (amount.compareTo(bankMovement) > 0) {
            throw new AccountingValidationException(
                    "Le montant rapproché dépasse le mouvement bancaire");
        }
        Instant now = clock.instant();
        jdbc.update(
                "INSERT INTO accounting_reconciliation_item"
                    + " (id,tenant_id,reconciliation_id,journal_entry_id,matched_amount,matched_at)"
                    + " VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                actor.tenantId(),
                reconciliationId,
                entryId,
                amount,
                timestamp(now));
        jdbc.update(
                "UPDATE accounting_reconciliation SET matched_amount=matched_amount+?,updated_at=?"
                        + " WHERE tenant_id=? AND id=?",
                amount,
                timestamp(now),
                actor.tenantId(),
                reconciliationId);
        ReconciliationView result = reconciliation(actor.tenantId(), reconciliationId);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "accounting.reconciliation.entry-matched",
                "Reconciliation",
                reconciliationId.toString(),
                ipAddress,
                Map.of("entryNumber", entry.entryNumber(), "amount", amount),
                before,
                result);
        return result;
    }

    @Transactional
    @PreAuthorize("hasAuthority('accounting.reconcile')")
    public ReconciliationView completeReconciliation(
            SchoolPrincipal actor, UUID reconciliationId, String ipAddress) {
        ReconciliationView before = reconciliation(actor.tenantId(), reconciliationId);
        if ("COMPLETED".equals(before.status())) {
            return before;
        }
        BigDecimal expectedMovement =
                money(before.closingBalance().subtract(before.openingBalance()).abs());
        if (before.itemCount() == 0 || before.matchedAmount().compareTo(expectedMovement) != 0) {
            throw new AccountingValidationException(
                    "Le total rapproché doit correspondre au mouvement net du relevé");
        }
        Instant now = clock.instant();
        jdbc.update(
                "UPDATE accounting_reconciliation SET"
                    + " status='COMPLETED',completed_by_user_id=?,completed_at=?,updated_at=? WHERE"
                    + " tenant_id=? AND id=? AND status='DRAFT'",
                actor.accountId(),
                timestamp(now),
                timestamp(now),
                actor.tenantId(),
                reconciliationId);
        ReconciliationView result = reconciliation(actor.tenantId(), reconciliationId);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "accounting.reconciliation.completed",
                "Reconciliation",
                reconciliationId.toString(),
                ipAddress,
                Map.of("matchedAmount", result.matchedAmount()),
                before,
                result);
        return result;
    }

    @Transactional
    @PreAuthorize("hasAuthority('accounting.post')")
    public SyncResult syncFamilyLedger(SchoolPrincipal actor, String ipAddress) {
        int posted = syncFamilyLedgerForTenant(actor.tenantId(), actor.accountId());
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "accounting.family-ledger.synchronized",
                "AccountingBridge",
                actor.tenantId().toString(),
                ipAddress,
                Map.of("postedEntries", posted),
                null,
                null);
        return new SyncResult(posted, familyLedgerEntryCount(actor.tenantId()));
    }

    @Transactional
    public int syncFamilyLedgerForTenant(UUID tenantId, UUID actorId) {
        ConfigurationView config = configuration(tenantId);
        if (!config.enabled() || !config.familyLedgerAutoPost()) {
            return 0;
        }
        List<FamilyLedgerAccountingEvent> sourceEvents = new ArrayList<>();
        sourceEvents.addAll(
                jdbc.query(
                        "SELECT id,invoice_number,issue_date,total_amount,currency_code"
                                + " FROM billing_invoice WHERE tenant_id=? AND status<>'DRAFT'",
                        (result, row) ->
                                familyEvent(
                                        tenantId,
                                        actorId,
                                        "INVOICE_ISSUED",
                                        result.getObject("id", UUID.class),
                                        result.getObject("issue_date", LocalDate.class),
                                        result.getBigDecimal("total_amount"),
                                        result.getString("currency_code"),
                                        result.getString("invoice_number"),
                                        "Facture famille " + result.getString("invoice_number")),
                        tenantId));
        sourceEvents.addAll(
                jdbc.query(
                        "SELECT id,payment_number,payment_type,amount,currency_code,received_at"
                                + " FROM billing_payment WHERE tenant_id=?",
                        (result, row) -> {
                            String type = result.getString("payment_type");
                            return familyEvent(
                                    tenantId,
                                    actorId,
                                    "REVERSAL".equals(type)
                                            ? "PAYMENT_REVERSED"
                                            : "PAYMENT_VALIDATED",
                                    result.getObject("id", UUID.class),
                                    result.getTimestamp("received_at")
                                            .toInstant()
                                            .atZone(SCHOOL_ZONE)
                                            .toLocalDate(),
                                    result.getBigDecimal("amount"),
                                    result.getString("currency_code"),
                                    result.getString("payment_number"),
                                    ("REVERSAL".equals(type) ? "Annulation " : "Encaissement ")
                                            + result.getString("payment_number"));
                        },
                        tenantId));
        sourceEvents.addAll(
                jdbc.query(
                        "SELECT id,credit_number,amount,currency_code,issued_at FROM billing_credit"
                                + " WHERE tenant_id=?",
                        (result, row) ->
                                familyEvent(
                                        tenantId,
                                        actorId,
                                        "CREDIT_ISSUED",
                                        result.getObject("id", UUID.class),
                                        result.getTimestamp("issued_at")
                                                .toInstant()
                                                .atZone(SCHOOL_ZONE)
                                                .toLocalDate(),
                                        result.getBigDecimal("amount"),
                                        result.getString("currency_code"),
                                        result.getString("credit_number"),
                                        "Avoir famille " + result.getString("credit_number")),
                        tenantId));
        sourceEvents.addAll(
                jdbc.query(
                        "SELECT id,refund_number,amount,currency_code,refunded_at FROM"
                                + " billing_refund WHERE tenant_id=?",
                        (result, row) ->
                                familyEvent(
                                        tenantId,
                                        actorId,
                                        "REFUND_VALIDATED",
                                        result.getObject("id", UUID.class),
                                        result.getTimestamp("refunded_at")
                                                .toInstant()
                                                .atZone(SCHOOL_ZONE)
                                                .toLocalDate(),
                                        result.getBigDecimal("amount"),
                                        result.getString("currency_code"),
                                        result.getString("refund_number"),
                                        "Remboursement famille "
                                                + result.getString("refund_number")),
                        tenantId));
        sourceEvents.addAll(
                jdbc.query(
                        "SELECT id,adjustment_number,direction,amount,currency_code,posted_at FROM"
                            + " billing_financial_adjustment WHERE tenant_id=? AND status='POSTED'",
                        (result, row) ->
                                familyEvent(
                                        tenantId,
                                        actorId,
                                        "DEBIT".equals(result.getString("direction"))
                                                ? "ADJUSTMENT_DEBIT"
                                                : "ADJUSTMENT_CREDIT",
                                        result.getObject("id", UUID.class),
                                        result.getTimestamp("posted_at")
                                                .toInstant()
                                                .atZone(SCHOOL_ZONE)
                                                .toLocalDate(),
                                        result.getBigDecimal("amount"),
                                        result.getString("currency_code"),
                                        result.getString("adjustment_number"),
                                        "Ajustement famille "
                                                + result.getString("adjustment_number")),
                        tenantId));
        int posted = 0;
        for (FamilyLedgerAccountingEvent event : sourceEvents) {
            posted += postFamilyLedgerEvent(event) ? 1 : 0;
        }
        return posted;
    }

    @EventListener
    @Transactional
    public void onFamilyLedgerAccountingEvent(FamilyLedgerAccountingEvent event) {
        postFamilyLedgerEvent(event);
    }

    private boolean postFamilyLedgerEvent(FamilyLedgerAccountingEvent event) {
        ConfigurationView config = configuration(event.tenantId());
        if (!config.enabled() || !config.familyLedgerAutoPost()) {
            return false;
        }
        int exists =
                jdbc.queryForObject(
                        "SELECT count(*) FROM accounting_journal_entry WHERE tenant_id=? AND"
                            + " source_module='FAMILY_LEDGER' AND source_type=? AND source_id=?",
                        Integer.class,
                        event.tenantId(),
                        event.eventType(),
                        event.sourceId());
        if (exists > 0) {
            return false;
        }
        EventMapping mapping = eventMapping(event.tenantId(), event.eventType());
        postBalancedEntry(
                event.tenantId(),
                event.actorId(),
                mapping.journalId(),
                event.accountingDate(),
                event.reference(),
                event.description(),
                "FAMILY_LEDGER",
                event.eventType(),
                event.sourceId(),
                event.currency(),
                List.of(
                        new PostingLine(
                                mapping.debitAccountId(),
                                null,
                                event.description(),
                                event.amount(),
                                ZERO),
                        new PostingLine(
                                mapping.creditAccountId(),
                                null,
                                event.description(),
                                ZERO,
                                event.amount())));
        return true;
    }

    private EntryView postBalancedEntry(
            UUID tenantId,
            UUID actorId,
            UUID journalId,
            LocalDate entryDate,
            String reference,
            String description,
            String sourceModule,
            String sourceType,
            String sourceId,
            String currency,
            List<PostingLine> rawLines) {
        if (rawLines == null || rawLines.size() < 2) {
            throw new AccountingValidationException("Une écriture exige au moins deux lignes");
        }
        journal(tenantId, journalId);
        FiscalPeriodView period = openPeriodFor(tenantId, entryDate);
        List<PostingLine> lines =
                rawLines.stream()
                        .map(
                                line -> {
                                    account(tenantId, line.accountId(), true);
                                    if (line.costCenterId() != null) {
                                        costCenter(tenantId, line.costCenterId());
                                    }
                                    return new PostingLine(
                                            line.accountId(),
                                            line.costCenterId(),
                                            required(line.label(), 300, "Le libellé de ligne"),
                                            money(line.debitAmount()),
                                            money(line.creditAmount()));
                                })
                        .toList();
        for (PostingLine line : lines) {
            boolean debit = line.debitAmount().signum() > 0 && line.creditAmount().signum() == 0;
            boolean credit = line.creditAmount().signum() > 0 && line.debitAmount().signum() == 0;
            if (!debit && !credit) {
                throw new AccountingValidationException(
                        "Chaque ligne doit porter un débit ou un crédit strictement positif");
            }
        }
        BigDecimal totalDebit =
                money(lines.stream().map(PostingLine::debitAmount).reduce(ZERO, BigDecimal::add));
        BigDecimal totalCredit =
                money(lines.stream().map(PostingLine::creditAmount).reduce(ZERO, BigDecimal::add));
        if (totalDebit.signum() <= 0 || totalDebit.compareTo(totalCredit) != 0) {
            throw new AccountingValidationException(
                    "Total débit et total crédit doivent être égaux");
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        String number =
                "GL-"
                        + entryDate.getYear()
                        + "-"
                        + id.toString().substring(0, 8).toUpperCase(Locale.ROOT);
        jdbc.update(
                "INSERT INTO accounting_journal_entry"
                    + " (id,tenant_id,journal_id,fiscal_period_id,reversal_of_entry_id,entry_number,"
                    + "entry_date,reference,description,source_module,source_type,source_id,currency,"
                    + "total_debit,total_credit,status,created_by_user_id,posted_at,reversed_at)"
                    + " VALUES (?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'POSTED', ?, ?,"
                    + " NULL)",
                id,
                tenantId,
                journalId,
                period.id(),
                number,
                entryDate,
                required(reference, 120, "La référence"),
                required(description, 700, "La description"),
                required(sourceModule, 50, "Le module source"),
                required(sourceType, 60, "Le type source"),
                required(sourceId, 100, "L’identifiant source"),
                currency(currency),
                totalDebit,
                totalCredit,
                actorId,
                timestamp(now));
        int position = 1;
        for (PostingLine line : lines) {
            jdbc.update(
                    "INSERT INTO accounting_journal_line"
                        + " (id,tenant_id,journal_entry_id,account_id,cost_center_id,line_position,label,debit_amount,credit_amount,created_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(),
                    tenantId,
                    id,
                    line.accountId(),
                    line.costCenterId(),
                    position++,
                    line.label(),
                    line.debitAmount(),
                    line.creditAmount(),
                    timestamp(now));
        }
        return entry(tenantId, id);
    }

    private ConfigurationView configuration(UUID tenantId) {
        return jdbc
                .query(
                        "SELECT enabled,base_currency,family_ledger_auto_post"
                                + " FROM accounting_configuration WHERE tenant_id=?",
                        (result, row) ->
                                new ConfigurationView(
                                        result.getBoolean("enabled"),
                                        result.getString("base_currency"),
                                        result.getBoolean("family_ledger_auto_post")),
                        tenantId)
                .stream()
                .findFirst()
                .orElse(new ConfigurationView(false, "MAD", false));
    }

    private List<AccountView> accounts(UUID tenantId) {
        return jdbc.query(
                "SELECT"
                    + " id,parent_account_id,code,name,account_type,description,allow_posting,active"
                    + " FROM accounting_chart_of_account WHERE tenant_id=? ORDER BY code",
                (result, row) -> accountView(result),
                tenantId);
    }

    private AccountView account(UUID tenantId, UUID id, boolean requirePosting) {
        AccountView result =
                jdbc
                        .query(
                                "SELECT"
                                    + " id,parent_account_id,code,name,account_type,description,allow_posting,active"
                                    + " FROM accounting_chart_of_account WHERE tenant_id=? AND"
                                    + " id=?",
                                (row, number) -> accountView(row),
                                tenantId,
                                id)
                        .stream()
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new AccountingNotFoundException(
                                                "Compte comptable introuvable"));
        if (!result.active() || (requirePosting && !result.allowPosting())) {
            throw new AccountingValidationException("Ce compte comptable n’accepte pas d’écriture");
        }
        return result;
    }

    private AccountView accountView(ResultSet result) throws SQLException {
        return new AccountView(
                result.getObject("id", UUID.class),
                result.getObject("parent_account_id", UUID.class),
                result.getString("code"),
                result.getString("name"),
                result.getString("account_type"),
                result.getString("description"),
                result.getBoolean("allow_posting"),
                result.getBoolean("active"));
    }

    private List<JournalView> journals(UUID tenantId) {
        return jdbc.query(
                "SELECT id,code,name,journal_type,active FROM accounting_journal"
                        + " WHERE tenant_id=? ORDER BY code",
                (result, row) -> journalView(result),
                tenantId);
    }

    private JournalView journal(UUID tenantId, UUID id) {
        JournalView result =
                jdbc
                        .query(
                                "SELECT id,code,name,journal_type,active FROM accounting_journal"
                                        + " WHERE tenant_id=? AND id=?",
                                (row, number) -> journalView(row),
                                tenantId,
                                id)
                        .stream()
                        .findFirst()
                        .orElseThrow(() -> new AccountingNotFoundException("Journal introuvable"));
        if (!result.active()) {
            throw new AccountingValidationException("Ce journal est inactif");
        }
        return result;
    }

    private JournalView journalView(ResultSet result) throws SQLException {
        return new JournalView(
                result.getObject("id", UUID.class),
                result.getString("code"),
                result.getString("name"),
                result.getString("journal_type"),
                result.getBoolean("active"));
    }

    private List<FiscalPeriodView> periods(UUID tenantId) {
        return jdbc.query(
                "SELECT id,code,label,starts_on,ends_on,status,closed_at FROM"
                        + " accounting_fiscal_period WHERE tenant_id=? ORDER BY starts_on DESC",
                (result, row) -> fiscalPeriodView(result),
                tenantId);
    }

    private FiscalPeriodView fiscalPeriod(UUID tenantId, UUID id) {
        return jdbc
                .query(
                        "SELECT id,code,label,starts_on,ends_on,status,closed_at"
                                + " FROM accounting_fiscal_period WHERE tenant_id=? AND id=?",
                        (result, row) -> fiscalPeriodView(result),
                        tenantId,
                        id)
                .stream()
                .findFirst()
                .orElseThrow(
                        () -> new AccountingNotFoundException("Période comptable introuvable"));
    }

    private FiscalPeriodView openPeriodFor(UUID tenantId, LocalDate date) {
        return jdbc
                .query(
                        "SELECT id,code,label,starts_on,ends_on,status,closed_at FROM"
                            + " accounting_fiscal_period WHERE tenant_id=? AND status='OPEN' AND"
                            + " starts_on<=? AND ends_on>=? ORDER BY starts_on DESC",
                        (result, row) -> fiscalPeriodView(result),
                        tenantId,
                        date,
                        date)
                .stream()
                .findFirst()
                .orElseThrow(
                        () ->
                                new AccountingValidationException(
                                        "Aucune période comptable ouverte pour le " + date));
    }

    private FiscalPeriodView fiscalPeriodView(ResultSet result) throws SQLException {
        Timestamp closedAt = result.getTimestamp("closed_at");
        return new FiscalPeriodView(
                result.getObject("id", UUID.class),
                result.getString("code"),
                result.getString("label"),
                result.getObject("starts_on", LocalDate.class),
                result.getObject("ends_on", LocalDate.class),
                result.getString("status"),
                closedAt == null ? null : closedAt.toInstant());
    }

    private List<CostCenterView> costCenters(UUID tenantId) {
        return jdbc.query(
                "SELECT id,code,name,description,active FROM accounting_cost_center"
                        + " WHERE tenant_id=? ORDER BY code",
                (result, row) -> costCenterView(result),
                tenantId);
    }

    private CostCenterView costCenter(UUID tenantId, UUID id) {
        return jdbc
                .query(
                        "SELECT id,code,name,description,active FROM accounting_cost_center"
                                + " WHERE tenant_id=? AND id=?",
                        (result, row) -> costCenterView(result),
                        tenantId,
                        id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new AccountingNotFoundException("Centre de coût introuvable"));
    }

    private CostCenterView costCenterView(ResultSet result) throws SQLException {
        return new CostCenterView(
                result.getObject("id", UUID.class),
                result.getString("code"),
                result.getString("name"),
                result.getString("description"),
                result.getBoolean("active"));
    }

    private List<EntryView> entries(UUID tenantId, int limit) {
        return jdbc.query(
                "SELECT e.id FROM accounting_journal_entry e WHERE e.tenant_id=?"
                        + " ORDER BY e.entry_date DESC,e.posted_at DESC LIMIT ?",
                (result, row) -> entry(tenantId, result.getObject("id", UUID.class)),
                tenantId,
                limit);
    }

    private record AccountingTotals(BigDecimal totalDebit, BigDecimal totalCredit) {}

    private EntryView entry(UUID tenantId, UUID entryId) {
        EntryHeader header =
                jdbc
                        .query(
                                "SELECT e.id,e.journal_id,j.code journal_code,e.fiscal_period_id,"
                                    + "e.reversal_of_entry_id,e.entry_number,e.entry_date,e.reference,"
                                    + "e.description,e.source_module,e.source_type,e.source_id,e.currency,e.total_debit,e.total_credit,e.status,e.posted_at"
                                    + " FROM accounting_journal_entry e JOIN accounting_journal j"
                                    + " ON j.id=e.journal_id AND j.tenant_id=e.tenant_id WHERE"
                                    + " e.tenant_id=? AND e.id=?",
                                (result, row) -> entryHeader(result),
                                tenantId,
                                entryId)
                        .stream()
                        .findFirst()
                        .orElseThrow(() -> new AccountingNotFoundException("Écriture introuvable"));
        List<EntryLineView> lines =
                jdbc.query(
                        "SELECT l.id,l.account_id,a.code account_code,a.name"
                            + " account_name,l.cost_center_id,c.code"
                            + " cost_center_code,l.line_position,l.label,l.debit_amount,l.credit_amount"
                            + " FROM accounting_journal_line l JOIN accounting_chart_of_account a"
                            + " ON a.id=l.account_id AND a.tenant_id=l.tenant_id LEFT JOIN"
                            + " accounting_cost_center c ON c.id=l.cost_center_id AND"
                            + " c.tenant_id=l.tenant_id WHERE l.tenant_id=? AND"
                            + " l.journal_entry_id=? ORDER BY l.line_position",
                        (result, row) -> entryLineView(result),
                        tenantId,
                        entryId);
        return new EntryView(
                header.id(),
                header.journalId(),
                header.journalCode(),
                header.fiscalPeriodId(),
                header.reversalOfEntryId(),
                header.entryNumber(),
                header.entryDate(),
                header.reference(),
                header.description(),
                header.sourceModule(),
                header.sourceType(),
                header.sourceId(),
                header.currency(),
                header.totalDebit(),
                header.totalCredit(),
                header.status(),
                header.postedAt(),
                lines);
    }

    private EntryHeader entryHeader(ResultSet result) throws SQLException {
        return new EntryHeader(
                result.getObject("id", UUID.class),
                result.getObject("journal_id", UUID.class),
                result.getString("journal_code"),
                result.getObject("fiscal_period_id", UUID.class),
                result.getObject("reversal_of_entry_id", UUID.class),
                result.getString("entry_number"),
                result.getObject("entry_date", LocalDate.class),
                result.getString("reference"),
                result.getString("description"),
                result.getString("source_module"),
                result.getString("source_type"),
                result.getString("source_id"),
                result.getString("currency"),
                result.getBigDecimal("total_debit"),
                result.getBigDecimal("total_credit"),
                result.getString("status"),
                result.getTimestamp("posted_at").toInstant());
    }

    private EntryLineView entryLineView(ResultSet result) throws SQLException {
        return new EntryLineView(
                result.getObject("id", UUID.class),
                result.getObject("account_id", UUID.class),
                result.getString("account_code"),
                result.getString("account_name"),
                result.getObject("cost_center_id", UUID.class),
                result.getString("cost_center_code"),
                result.getInt("line_position"),
                result.getString("label"),
                result.getBigDecimal("debit_amount"),
                result.getBigDecimal("credit_amount"));
    }

    private List<SupplierView> suppliers(UUID tenantId) {
        return jdbc.query(
                "SELECT id,code,name,tax_identifier,email,phone,address,status"
                        + " FROM accounting_supplier WHERE tenant_id=? ORDER BY name",
                (result, row) -> supplierView(result),
                tenantId);
    }

    private SupplierView supplier(UUID tenantId, UUID id) {
        return jdbc
                .query(
                        "SELECT id,code,name,tax_identifier,email,phone,address,status"
                                + " FROM accounting_supplier WHERE tenant_id=? AND id=?",
                        (result, row) -> supplierView(result),
                        tenantId,
                        id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new AccountingNotFoundException("Fournisseur introuvable"));
    }

    private SupplierView supplierView(ResultSet result) throws SQLException {
        return new SupplierView(
                result.getObject("id", UUID.class),
                result.getString("code"),
                result.getString("name"),
                result.getString("tax_identifier"),
                result.getString("email"),
                result.getString("phone"),
                result.getString("address"),
                result.getString("status"));
    }

    private List<SupplierInvoiceView> supplierInvoices(UUID tenantId) {
        return jdbc.query(
                "SELECT i.id FROM accounting_supplier_invoice i WHERE i.tenant_id=?"
                        + " ORDER BY i.invoice_date DESC,i.created_at DESC",
                (result, row) -> supplierInvoice(tenantId, result.getObject("id", UUID.class)),
                tenantId);
    }

    private SupplierInvoiceView supplierInvoice(UUID tenantId, UUID id) {
        return jdbc
                .query(
                        "SELECT i.id,i.supplier_id,s.name"
                            + " supplier_name,i.journal_id,i.expense_account_id,"
                            + "i.payable_account_id,i.cost_center_id,i.journal_entry_id,i.invoice_number,"
                            + "i.invoice_date,i.due_date,i.description,i.total_amount,i.currency,i.status,i.created_at,i.posted_at"
                            + " FROM accounting_supplier_invoice i JOIN accounting_supplier s ON"
                            + " s.id=i.supplier_id AND s.tenant_id=i.tenant_id WHERE i.tenant_id=?"
                            + " AND i.id=?",
                        (result, row) -> supplierInvoiceView(result),
                        tenantId,
                        id)
                .stream()
                .findFirst()
                .orElseThrow(
                        () -> new AccountingNotFoundException("Facture fournisseur introuvable"));
    }

    private SupplierInvoiceView supplierInvoiceView(ResultSet result) throws SQLException {
        Timestamp posted = result.getTimestamp("posted_at");
        return new SupplierInvoiceView(
                result.getObject("id", UUID.class),
                result.getObject("supplier_id", UUID.class),
                result.getString("supplier_name"),
                result.getObject("journal_id", UUID.class),
                result.getObject("expense_account_id", UUID.class),
                result.getObject("payable_account_id", UUID.class),
                result.getObject("cost_center_id", UUID.class),
                result.getObject("journal_entry_id", UUID.class),
                result.getString("invoice_number"),
                result.getObject("invoice_date", LocalDate.class),
                result.getObject("due_date", LocalDate.class),
                result.getString("description"),
                result.getBigDecimal("total_amount"),
                result.getString("currency"),
                result.getString("status"),
                result.getTimestamp("created_at").toInstant(),
                posted == null ? null : posted.toInstant());
    }

    private List<ExpenseView> expenses(UUID tenantId) {
        return jdbc.query(
                "SELECT id FROM accounting_expense WHERE tenant_id=?"
                        + " ORDER BY expense_date DESC,created_at DESC",
                (result, row) -> expense(tenantId, result.getObject("id", UUID.class)),
                tenantId);
    }

    private ExpenseView expense(UUID tenantId, UUID id) {
        return jdbc
                .query(
                        "SELECT e.id,e.supplier_id,s.name"
                            + " supplier_name,e.journal_id,e.expense_account_id,"
                            + "e.payment_account_id,e.cost_center_id,e.journal_entry_id,e.expense_reference,"
                            + "e.expense_date,e.description,e.amount,e.currency,e.payment_method,e.status,e.created_at,e.posted_at"
                            + " FROM accounting_expense e LEFT JOIN accounting_supplier s ON"
                            + " s.id=e.supplier_id AND s.tenant_id=e.tenant_id WHERE e.tenant_id=?"
                            + " AND e.id=?",
                        (result, row) -> expenseView(result),
                        tenantId,
                        id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new AccountingNotFoundException("Dépense introuvable"));
    }

    private ExpenseView expenseView(ResultSet result) throws SQLException {
        Timestamp posted = result.getTimestamp("posted_at");
        return new ExpenseView(
                result.getObject("id", UUID.class),
                result.getObject("supplier_id", UUID.class),
                result.getString("supplier_name"),
                result.getObject("journal_id", UUID.class),
                result.getObject("expense_account_id", UUID.class),
                result.getObject("payment_account_id", UUID.class),
                result.getObject("cost_center_id", UUID.class),
                result.getObject("journal_entry_id", UUID.class),
                result.getString("expense_reference"),
                result.getObject("expense_date", LocalDate.class),
                result.getString("description"),
                result.getBigDecimal("amount"),
                result.getString("currency"),
                result.getString("payment_method"),
                result.getString("status"),
                result.getTimestamp("created_at").toInstant(),
                posted == null ? null : posted.toInstant());
    }

    private List<BankAccountView> bankAccounts(UUID tenantId) {
        return jdbc.query(
                "SELECT b.id,b.ledger_account_id,a.code ledger_account_code,b.journal_id,b.code,"
                        + "b.bank_name,b.account_label,b.iban_masked,b.currency,b.active"
                        + " FROM accounting_bank_account b JOIN accounting_chart_of_account a"
                        + " ON a.id=b.ledger_account_id AND a.tenant_id=b.tenant_id"
                        + " WHERE b.tenant_id=? ORDER BY b.code",
                (result, row) -> bankAccountView(result),
                tenantId);
    }

    private BankAccountView bankAccount(UUID tenantId, UUID id) {
        return jdbc
                .query(
                        "SELECT b.id,b.ledger_account_id,a.code"
                            + " ledger_account_code,b.journal_id,b.code,b.bank_name,b.account_label,b.iban_masked,b.currency,b.active"
                            + " FROM accounting_bank_account b JOIN accounting_chart_of_account a"
                            + " ON a.id=b.ledger_account_id AND a.tenant_id=b.tenant_id WHERE"
                            + " b.tenant_id=? AND b.id=?",
                        (result, row) -> bankAccountView(result),
                        tenantId,
                        id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new AccountingNotFoundException("Compte bancaire introuvable"));
    }

    private BankAccountView bankAccountView(ResultSet result) throws SQLException {
        return new BankAccountView(
                result.getObject("id", UUID.class),
                result.getObject("ledger_account_id", UUID.class),
                result.getString("ledger_account_code"),
                result.getObject("journal_id", UUID.class),
                result.getString("code"),
                result.getString("bank_name"),
                result.getString("account_label"),
                result.getString("iban_masked"),
                result.getString("currency"),
                result.getBoolean("active"));
    }

    private List<ReconciliationView> reconciliations(UUID tenantId) {
        return jdbc.query(
                "SELECT id FROM accounting_reconciliation WHERE tenant_id=?"
                        + " ORDER BY ends_on DESC,created_at DESC",
                (result, row) -> reconciliation(tenantId, result.getObject("id", UUID.class)),
                tenantId);
    }

    private ReconciliationView reconciliation(UUID tenantId, UUID id) {
        return jdbc
                .query(
                        "SELECT r.id,r.bank_account_id,b.code"
                            + " bank_code,r.statement_reference,r.starts_on,"
                            + "r.ends_on,r.opening_balance,r.closing_balance,r.matched_amount,r.status,r.completed_at,r.created_at,(SELECT"
                            + " count(*) FROM accounting_reconciliation_item ri WHERE"
                            + " ri.tenant_id=r.tenant_id AND ri.reconciliation_id=r.id) item_count"
                            + " FROM accounting_reconciliation r JOIN accounting_bank_account b ON"
                            + " b.id=r.bank_account_id AND b.tenant_id=r.tenant_id WHERE"
                            + " r.tenant_id=? AND r.id=?",
                        (result, row) -> reconciliationView(result),
                        tenantId,
                        id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new AccountingNotFoundException("Rapprochement introuvable"));
    }

    private ReconciliationView reconciliationView(ResultSet result) throws SQLException {
        Timestamp completed = result.getTimestamp("completed_at");
        return new ReconciliationView(
                result.getObject("id", UUID.class),
                result.getObject("bank_account_id", UUID.class),
                result.getString("bank_code"),
                result.getString("statement_reference"),
                result.getObject("starts_on", LocalDate.class),
                result.getObject("ends_on", LocalDate.class),
                result.getBigDecimal("opening_balance"),
                result.getBigDecimal("closing_balance"),
                result.getBigDecimal("matched_amount"),
                result.getString("status"),
                result.getInt("item_count"),
                completed == null ? null : completed.toInstant(),
                result.getTimestamp("created_at").toInstant());
    }

    private List<TrialBalanceLine> trialBalance(UUID tenantId) {
        return jdbc.query(
                "SELECT a.id,a.code,a.name,a.account_type,COALESCE(SUM(l.debit_amount),0)"
                    + " total_debit,COALESCE(SUM(l.credit_amount),0) total_credit FROM"
                    + " accounting_chart_of_account a LEFT JOIN accounting_journal_line l ON"
                    + " l.account_id=a.id AND l.tenant_id=a.tenant_id WHERE a.tenant_id=? GROUP BY"
                    + " a.id,a.code,a.name,a.account_type ORDER BY a.code",
                (result, row) -> {
                    BigDecimal debit = money(result.getBigDecimal("total_debit"));
                    BigDecimal credit = money(result.getBigDecimal("total_credit"));
                    return new TrialBalanceLine(
                            result.getObject("id", UUID.class),
                            result.getString("code"),
                            result.getString("name"),
                            result.getString("account_type"),
                            debit,
                            credit,
                            money(debit.subtract(credit)));
                },
                tenantId);
    }

    private EventMapping eventMapping(UUID tenantId, String eventType) {
        return jdbc
                .query(
                        "SELECT journal_id,debit_account_id,credit_account_id FROM"
                                + " accounting_event_mapping WHERE tenant_id=? AND event_type=? AND"
                                + " active=TRUE",
                        (result, row) ->
                                new EventMapping(
                                        result.getObject("journal_id", UUID.class),
                                        result.getObject("debit_account_id", UUID.class),
                                        result.getObject("credit_account_id", UUID.class)),
                        tenantId,
                        eventType)
                .stream()
                .findFirst()
                .orElseThrow(
                        () ->
                                new AccountingValidationException(
                                        "Aucun mapping comptable actif pour l’événement "
                                                + eventType));
    }

    private int familyLedgerEntryCount(UUID tenantId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM accounting_journal_entry"
                        + " WHERE tenant_id=? AND source_module='FAMILY_LEDGER'",
                Integer.class,
                tenantId);
    }

    private void validateSupplierInvoiceCommand(SupplierInvoiceCommand command) {
        if (command == null
                || command.supplierId() == null
                || command.journalId() == null
                || command.expenseAccountId() == null
                || command.payableAccountId() == null
                || command.invoiceDate() == null
                || command.dueDate() == null) {
            throw new AccountingValidationException(
                    "Le fournisseur, les comptes, le journal et les dates sont requis");
        }
        if (command.dueDate().isBefore(command.invoiceDate())) {
            throw new AccountingValidationException("L’échéance précède la date de facture");
        }
    }

    private void requireAccountType(UUID tenantId, UUID accountId, String type) {
        AccountView selected = account(tenantId, accountId, true);
        if (!type.equals(selected.accountType())) {
            throw new AccountingValidationException(
                    "Le compte " + selected.code() + " doit être de type " + type);
        }
    }

    private FamilyLedgerAccountingEvent familyEvent(
            UUID tenantId,
            UUID actorId,
            String type,
            UUID sourceId,
            LocalDate date,
            BigDecimal amount,
            String currency,
            String reference,
            String description) {
        return new FamilyLedgerAccountingEvent(
                tenantId,
                actorId,
                type,
                sourceId.toString(),
                date,
                money(amount),
                currency,
                reference,
                description);
    }

    private String required(String value, int max, String field) {
        if (value == null || value.isBlank()) {
            throw new AccountingValidationException(field + " est requis");
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw new AccountingValidationException(field + " dépasse " + max + " caractères");
        }
        return normalized;
    }

    private String optional(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw new AccountingValidationException("Une valeur dépasse " + max + " caractères");
        }
        return normalized;
    }

    private String enumValue(String value, Set<String> accepted, String field) {
        String normalized = required(value, 60, field).toUpperCase(Locale.ROOT);
        if (!accepted.contains(normalized)) {
            throw new AccountingValidationException(field + " est invalide");
        }
        return normalized;
    }

    private String currency(String value) {
        String normalized = required(value, 3, "La devise").toUpperCase(Locale.ROOT);
        if (normalized.length() != 3) {
            throw new AccountingValidationException("La devise doit comporter trois lettres");
        }
        return normalized;
    }

    private BigDecimal positiveMoney(BigDecimal value, String field) {
        BigDecimal normalized = money(value);
        if (normalized.signum() <= 0) {
            throw new AccountingValidationException(field + " doit être strictement positif");
        }
        return normalized;
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private void auditCreate(
            SchoolPrincipal actor,
            String action,
            String aggregateType,
            UUID id,
            String ipAddress,
            Object result) {
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                action,
                aggregateType,
                id.toString(),
                ipAddress,
                Map.of(),
                null,
                result);
    }

    private ProcurementAccountingPort.SupplierView procurementSupplier(SupplierView item) {
        return new ProcurementAccountingPort.SupplierView(
                item.id(),
                item.code(),
                item.name(),
                item.taxIdentifier(),
                item.email(),
                item.phone(),
                item.address(),
                item.status());
    }

    private ProcurementAccountingPort.SupplierInvoiceView procurementInvoice(
            SupplierInvoiceView item) {
        return new ProcurementAccountingPort.SupplierInvoiceView(
                item.id(),
                item.supplierId(),
                item.supplierName(),
                item.journalId(),
                item.expenseAccountId(),
                item.payableAccountId(),
                item.costCenterId(),
                item.journalEntryId(),
                item.invoiceNumber(),
                item.invoiceDate(),
                item.dueDate(),
                item.description(),
                item.totalAmount(),
                item.currency(),
                item.status(),
                item.createdAt(),
                item.postedAt());
    }

    private ProcurementAccountingPort.ExpenseView procurementExpense(ExpenseView item) {
        return new ProcurementAccountingPort.ExpenseView(
                item.id(),
                item.supplierId(),
                item.supplierName(),
                item.journalId(),
                item.expenseAccountId(),
                item.paymentAccountId(),
                item.costCenterId(),
                item.journalEntryId(),
                item.expenseReference(),
                item.expenseDate(),
                item.description(),
                item.amount(),
                item.currency(),
                item.paymentMethod(),
                item.status(),
                item.createdAt(),
                item.postedAt());
    }

    public record ConfigurationCommand(
            boolean enabled, String baseCurrency, boolean familyLedgerAutoPost) {}

    public record AccountCommand(
            UUID parentAccountId,
            String code,
            String name,
            String accountType,
            String description,
            boolean allowPosting) {}

    public record JournalCommand(String code, String name, String journalType) {}

    public record FiscalPeriodCommand(
            String code, String label, LocalDate startsOn, LocalDate endsOn) {}

    public record CostCenterCommand(String code, String name, String description) {}

    public record SupplierCommand(
            String code,
            String name,
            String taxIdentifier,
            String email,
            String phone,
            String address) {}

    public record BankAccountCommand(
            UUID ledgerAccountId,
            UUID journalId,
            String code,
            String bankName,
            String accountLabel,
            String ibanMasked,
            String currency) {}

    public record SupplierInvoiceCommand(
            UUID supplierId,
            UUID journalId,
            UUID expenseAccountId,
            UUID payableAccountId,
            UUID costCenterId,
            String invoiceNumber,
            LocalDate invoiceDate,
            LocalDate dueDate,
            String description,
            BigDecimal totalAmount,
            String currency) {}

    public record ExpenseCommand(
            UUID supplierId,
            UUID journalId,
            UUID expenseAccountId,
            UUID paymentAccountId,
            UUID costCenterId,
            String expenseReference,
            LocalDate expenseDate,
            String description,
            BigDecimal amount,
            String currency,
            String paymentMethod) {}

    public record ReversalCommand(String reason) {}

    public record ReconciliationCommand(
            UUID bankAccountId,
            String statementReference,
            LocalDate startsOn,
            LocalDate endsOn,
            BigDecimal openingBalance,
            BigDecimal closingBalance) {}

    public record MatchCommand(BigDecimal amount) {}

    public record ProcurementPaymentPosting(
            UUID paymentId,
            UUID supplierId,
            UUID journalId,
            UUID payableAccountId,
            UUID paymentAccountId,
            LocalDate paidOn,
            String reference,
            String description,
            BigDecimal amount,
            String currency) {}

    public record ConfigurationView(
            boolean enabled, String baseCurrency, boolean familyLedgerAutoPost) {}

    public record ProcurementReferenceData(
            List<SupplierView> suppliers,
            List<JournalView> journals,
            List<AccountView> accounts,
            List<CostCenterView> costCenters,
            List<SupplierInvoiceView> supplierInvoices,
            List<ExpenseView> expenses) {}

    public record AccountView(
            UUID id,
            UUID parentAccountId,
            String code,
            String name,
            String accountType,
            String description,
            boolean allowPosting,
            boolean active) {}

    public record JournalView(
            UUID id, String code, String name, String journalType, boolean active) {}

    public record FiscalPeriodView(
            UUID id,
            String code,
            String label,
            LocalDate startsOn,
            LocalDate endsOn,
            String status,
            Instant closedAt) {}

    public record CostCenterView(
            UUID id, String code, String name, String description, boolean active) {}

    public record EntryLineView(
            UUID id,
            UUID accountId,
            String accountCode,
            String accountName,
            UUID costCenterId,
            String costCenterCode,
            int position,
            String label,
            BigDecimal debitAmount,
            BigDecimal creditAmount) {}

    public record EntryView(
            UUID id,
            UUID journalId,
            String journalCode,
            UUID fiscalPeriodId,
            UUID reversalOfEntryId,
            String entryNumber,
            LocalDate entryDate,
            String reference,
            String description,
            String sourceModule,
            String sourceType,
            String sourceId,
            String currency,
            BigDecimal totalDebit,
            BigDecimal totalCredit,
            String status,
            Instant postedAt,
            List<EntryLineView> lines) {}

    public record SupplierView(
            UUID id,
            String code,
            String name,
            String taxIdentifier,
            String email,
            String phone,
            String address,
            String status) {}

    public record SupplierInvoiceView(
            UUID id,
            UUID supplierId,
            String supplierName,
            UUID journalId,
            UUID expenseAccountId,
            UUID payableAccountId,
            UUID costCenterId,
            UUID journalEntryId,
            String invoiceNumber,
            LocalDate invoiceDate,
            LocalDate dueDate,
            String description,
            BigDecimal totalAmount,
            String currency,
            String status,
            Instant createdAt,
            Instant postedAt) {}

    public record ExpenseView(
            UUID id,
            UUID supplierId,
            String supplierName,
            UUID journalId,
            UUID expenseAccountId,
            UUID paymentAccountId,
            UUID costCenterId,
            UUID journalEntryId,
            String expenseReference,
            LocalDate expenseDate,
            String description,
            BigDecimal amount,
            String currency,
            String paymentMethod,
            String status,
            Instant createdAt,
            Instant postedAt) {}

    public record BankAccountView(
            UUID id,
            UUID ledgerAccountId,
            String ledgerAccountCode,
            UUID journalId,
            String code,
            String bankName,
            String accountLabel,
            String ibanMasked,
            String currency,
            boolean active) {}

    public record ReconciliationView(
            UUID id,
            UUID bankAccountId,
            String bankCode,
            String statementReference,
            LocalDate startsOn,
            LocalDate endsOn,
            BigDecimal openingBalance,
            BigDecimal closingBalance,
            BigDecimal matchedAmount,
            String status,
            int itemCount,
            Instant completedAt,
            Instant createdAt) {}

    public record TrialBalanceLine(
            UUID accountId,
            String accountCode,
            String accountName,
            String accountType,
            BigDecimal totalDebit,
            BigDecimal totalCredit,
            BigDecimal balance) {}

    public record Dashboard(
            Instant generatedAt,
            ConfigurationView configuration,
            BigDecimal totalDebit,
            BigDecimal totalCredit,
            boolean balanced,
            int familyLedgerEntryCount,
            List<AccountView> accounts,
            List<JournalView> journals,
            List<FiscalPeriodView> fiscalPeriods,
            List<CostCenterView> costCenters,
            List<EntryView> entries,
            List<SupplierView> suppliers,
            List<SupplierInvoiceView> supplierInvoices,
            List<ExpenseView> expenses,
            List<BankAccountView> bankAccounts,
            List<ReconciliationView> reconciliations,
            List<TrialBalanceLine> trialBalance) {}

    public record SyncResult(int postedEntries, int totalFamilyLedgerEntries) {}

    private record PostingLine(
            UUID accountId,
            UUID costCenterId,
            String label,
            BigDecimal debitAmount,
            BigDecimal creditAmount) {}

    private record EntryHeader(
            UUID id,
            UUID journalId,
            String journalCode,
            UUID fiscalPeriodId,
            UUID reversalOfEntryId,
            String entryNumber,
            LocalDate entryDate,
            String reference,
            String description,
            String sourceModule,
            String sourceType,
            String sourceId,
            String currency,
            BigDecimal totalDebit,
            BigDecimal totalCredit,
            String status,
            Instant postedAt) {}

    private record EventMapping(UUID journalId, UUID debitAccountId, UUID creditAccountId) {}
}
