package ma.schoolz.erp.accounting.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.accounting.application.AccountingService.AccountCommand;
import ma.schoolz.erp.accounting.application.AccountingService.AccountView;
import ma.schoolz.erp.accounting.application.AccountingService.BankAccountCommand;
import ma.schoolz.erp.accounting.application.AccountingService.BankAccountView;
import ma.schoolz.erp.accounting.application.AccountingService.ConfigurationCommand;
import ma.schoolz.erp.accounting.application.AccountingService.CostCenterCommand;
import ma.schoolz.erp.accounting.application.AccountingService.CostCenterView;
import ma.schoolz.erp.accounting.application.AccountingService.ExpenseCommand;
import ma.schoolz.erp.accounting.application.AccountingService.ExpenseView;
import ma.schoolz.erp.accounting.application.AccountingService.FiscalPeriodCommand;
import ma.schoolz.erp.accounting.application.AccountingService.JournalCommand;
import ma.schoolz.erp.accounting.application.AccountingService.JournalView;
import ma.schoolz.erp.accounting.application.AccountingService.MatchCommand;
import ma.schoolz.erp.accounting.application.AccountingService.ReconciliationCommand;
import ma.schoolz.erp.accounting.application.AccountingService.ReconciliationView;
import ma.schoolz.erp.accounting.application.AccountingService.SupplierCommand;
import ma.schoolz.erp.accounting.application.AccountingService.SupplierInvoiceCommand;
import ma.schoolz.erp.accounting.application.AccountingService.SupplierInvoiceView;
import ma.schoolz.erp.accounting.application.AccountingService.SupplierView;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@Order(230)
public class DevAccountingSeeder implements ApplicationRunner {
    private static final Set<String> PERMISSIONS =
            Set.of(
                    "accounting.read",
                    "accounting.configure",
                    "accounting.post",
                    "accounting.reconcile");

    private final JdbcTemplate jdbc;
    private final AccountingService accounting;
    private final Clock clock;
    private final boolean enabled;
    private final String tenantSlug;

    public DevAccountingSeeder(
            JdbcTemplate jdbc,
            AccountingService accounting,
            Clock clock,
            @Value("${schoolz.demo.seed-enabled:false}") boolean enabled,
            @Value("${schoolz.demo.tenant-slug:ecole-demo}") String tenantSlug) {
        this.jdbc = jdbc;
        this.accounting = accounting;
        this.clock = clock;
        this.enabled = enabled;
        this.tenantSlug = tenantSlug;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (!enabled) return;
        UUID tenantId = id("SELECT id FROM identity_tenant WHERE slug=?", tenantSlug);
        if (count("SELECT count(*) FROM accounting_configuration WHERE tenant_id=?", tenantId)
                > 0) {
            return;
        }
        UUID adminId =
                id(
                        "SELECT id FROM identity_user_account WHERE tenant_id=? AND"
                                + " normalized_email=?",
                        tenantId,
                        "admin@schoolz.ma");
        SchoolPrincipal actor =
                new SchoolPrincipal(
                        adminId,
                        tenantId,
                        "admin@schoolz.ma",
                        "Démonstration comptabilité",
                        "fr-MA",
                        Set.of("SUPER_ADMIN"),
                        PERMISSIONS);
        String ip = "127.0.0.1";

        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext seeded = SecurityContextHolder.createEmptyContext();
        seeded.setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        actor,
                        null,
                        PERMISSIONS.stream().map(SimpleGrantedAuthority::new).toList()));
        SecurityContextHolder.setContext(seeded);
        try {

            accounting.configure(actor, new ConfigurationCommand(true, "MAD", true), ip);
            accounting.createFiscalPeriod(
                    actor,
                    new FiscalPeriodCommand(
                            "FY-2026",
                            "Exercice 2026",
                            LocalDate.of(2026, 1, 1),
                            LocalDate.of(2026, 12, 31)),
                    ip);

            AccountView bank = account(actor, "111100", "Banque — compte principal", "ASSET");
            AccountView cash = account(actor, "111200", "Caisse", "ASSET");
            AccountView receivables = account(actor, "342100", "Créances familles", "ASSET");
            AccountView familyCredits =
                    account(actor, "448100", "Avoirs familles à restituer", "LIABILITY");
            AccountView suppliersPayable =
                    account(actor, "441100", "Fournisseurs à payer", "LIABILITY");
            account(actor, "611100", "Charges de personnel", "EXPENSE");
            AccountView operatingExpense = account(actor, "613100", "Frais généraux", "EXPENSE");
            AccountView teachingSupplies =
                    account(actor, "614100", "Fournitures pédagogiques", "EXPENSE");
            AccountView tuitionRevenue =
                    account(actor, "711100", "Produits de scolarité", "REVENUE");
            AccountView otherRevenue =
                    account(actor, "758100", "Autres produits scolaires", "REVENUE");

            JournalView sales = journal(actor, "VEN", "Ventes & familles", "SALES");
            JournalView purchases = journal(actor, "ACH", "Achats", "PURCHASE");
            JournalView bankJournal = journal(actor, "BNK", "Banque", "BANK");
            JournalView cashJournal = journal(actor, "CAI", "Caisse", "CASH");
            journal(actor, "OD", "Opérations diverses", "GENERAL");

            CostCenterView primary =
                    accounting.createCostCenter(
                            actor,
                            new CostCenterCommand(
                                    "PRIMAIRE",
                                    "Cycle primaire",
                                    "Suivi analytique du cycle primaire"),
                            ip);
            CostCenterView administration =
                    accounting.createCostCenter(
                            actor,
                            new CostCenterCommand(
                                    "ADMIN",
                                    "Administration",
                                    "Fonctions support de l’établissement"),
                            ip);

            mapping(tenantId, "INVOICE_ISSUED", sales, receivables, tuitionRevenue);
            mapping(tenantId, "PAYMENT_VALIDATED", bankJournal, bank, receivables);
            mapping(tenantId, "PAYMENT_REVERSED", bankJournal, receivables, bank);
            mapping(tenantId, "CREDIT_ISSUED", sales, tuitionRevenue, familyCredits);
            mapping(tenantId, "REFUND_VALIDATED", bankJournal, familyCredits, bank);
            mapping(tenantId, "ADJUSTMENT_DEBIT", sales, receivables, otherRevenue);
            mapping(tenantId, "ADJUSTMENT_CREDIT", sales, otherRevenue, receivables);

            accounting.syncFamilyLedgerForTenant(tenantId, adminId);

            SupplierView supplier =
                    accounting.createSupplier(
                            actor,
                            new SupplierCommand(
                                    "FOUR-DEMO-01",
                                    "Papeterie Atlas",
                                    "ICE-DEMO-001",
                                    "contact@papeterie-atlas.demo",
                                    "+212 5 00 00 00 00",
                                    "Casablanca — coordonnées de démonstration"),
                            ip);
            SupplierInvoiceView supplierInvoice =
                    accounting.createSupplierInvoice(
                            actor,
                            new SupplierInvoiceCommand(
                                    supplier.id(),
                                    purchases.id(),
                                    teachingSupplies.id(),
                                    suppliersPayable.id(),
                                    primary.id(),
                                    "FA-ATLAS-2026-081",
                                    LocalDate.of(2026, 8, 5),
                                    LocalDate.of(2026, 9, 4),
                                    "Cahiers et fournitures de rentrée — démonstration",
                                    new BigDecimal("2450.00"),
                                    "MAD"),
                            ip);
            accounting.postSupplierInvoice(actor, supplierInvoice.id(), ip);

            ExpenseView expense =
                    accounting.createExpense(
                            actor,
                            new ExpenseCommand(
                                    supplier.id(),
                                    cashJournal.id(),
                                    operatingExpense.id(),
                                    cash.id(),
                                    administration.id(),
                                    "DEP-DEMO-2026-08-01",
                                    LocalDate.of(2026, 8, 8),
                                    "Frais de maintenance légère — démonstration",
                                    new BigDecimal("1200.00"),
                                    "MAD",
                                    "CASH"),
                            ip);
            accounting.postExpense(actor, expense.id(), ip);

            seedReconciliation(actor, bank, bankJournal, ip);
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    private void seedReconciliation(
            SchoolPrincipal actor, AccountView bank, JournalView bankJournal, String ip) {
        List<BankMovement> movements =
                jdbc.query(
                        "SELECT e.id,e.total_debit FROM accounting_journal_entry e"
                                + " JOIN accounting_journal_line l ON l.journal_entry_id=e.id"
                                + " AND l.tenant_id=e.tenant_id WHERE e.tenant_id=?"
                                + " AND e.source_module='FAMILY_LEDGER'"
                                + " AND e.source_type='PAYMENT_VALIDATED' AND l.account_id=?"
                                + " ORDER BY e.entry_date,e.posted_at LIMIT 1",
                        (result, row) ->
                                new BankMovement(
                                        result.getObject("id", UUID.class),
                                        result.getBigDecimal("total_debit")),
                        actor.tenantId(),
                        bank.id());
        if (movements.isEmpty()) return;
        BankAccountView bankAccount =
                accounting.createBankAccount(
                        actor,
                        new BankAccountCommand(
                                bank.id(),
                                bankJournal.id(),
                                "BANK-DEMO",
                                "Banque SchoolZ Démo",
                                "Compte d’exploitation",
                                "MA•• •••• •••• •••• 2026",
                                "MAD"),
                        ip);
        BankMovement movement = movements.getFirst();
        ReconciliationView reconciliation =
                accounting.createReconciliation(
                        actor,
                        new ReconciliationCommand(
                                bankAccount.id(),
                                "REL-2026-08-DEMO",
                                LocalDate.of(2026, 8, 1),
                                LocalDate.of(2026, 8, 31),
                                BigDecimal.ZERO,
                                movement.amount()),
                        ip);
        accounting.matchReconciliationEntry(
                actor,
                reconciliation.id(),
                movement.entryId(),
                new MatchCommand(movement.amount()),
                ip);
        accounting.completeReconciliation(actor, reconciliation.id(), ip);
    }

    private AccountView account(SchoolPrincipal actor, String code, String name, String type) {
        return accounting.createAccount(
                actor, new AccountCommand(null, code, name, type, null, true), "127.0.0.1");
    }

    private JournalView journal(SchoolPrincipal actor, String code, String name, String type) {
        return accounting.createJournal(actor, new JournalCommand(code, name, type), "127.0.0.1");
    }

    private void mapping(
            UUID tenantId,
            String eventType,
            JournalView journal,
            AccountView debit,
            AccountView credit) {
        UUID id = UUID.randomUUID();
        java.sql.Timestamp now = java.sql.Timestamp.from(clock.instant());
        jdbc.update(
                "INSERT INTO accounting_event_mapping"
                        + " (id,tenant_id,event_type,journal_id,debit_account_id,credit_account_id,"
                        + "active,created_at,updated_at) VALUES (?, ?, ?, ?, ?, ?, TRUE, ?, ?)",
                id,
                tenantId,
                eventType,
                journal.id(),
                debit.id(),
                credit.id(),
                now,
                now);
    }

    private UUID id(String sql, Object... arguments) {
        UUID value = jdbc.queryForObject(sql, UUID.class, arguments);
        if (value == null) throw new IllegalStateException("Donnée de démonstration introuvable");
        return value;
    }

    private int count(String sql, Object... arguments) {
        Integer value = jdbc.queryForObject(sql, Integer.class, arguments);
        return value == null ? 0 : value;
    }

    private record BankMovement(UUID entryId, BigDecimal amount) {}
}
