package ma.schoolz.erp.billing.application;

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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.admissions.AdmissionPortalAccess.PortalIdentity;
import ma.schoolz.erp.billing.FamilyLedgerAccountingEvent;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.identity.IdentityAuditTrail;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingService {
    private static final ZoneId SCHOOL_ZONE = ZoneId.of("Africa/Casablanca");
    private static final String CURRENCY = "MAD";
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final Set<String> SERVICE_TYPES =
            Set.of(
                    "TUITION",
                    "REGISTRATION",
                    "TRANSPORT",
                    "CAFETERIA",
                    "ACTIVITY",
                    "DAYCARE",
                    "OTHER_FEE");
    private static final Set<String> PAYMENT_METHODS =
            Set.of("CASH", "BANK_TRANSFER", "CHECK", "CARD", "ONLINE_PAYMENT", "OTHER");

    private final JdbcTemplate jdbc;
    private final IdentityAuditTrail audit;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public BillingService(
            JdbcTemplate jdbc,
            IdentityAuditTrail audit,
            Clock clock,
            ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
        this.events = events;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('invoice.read')")
    public FinanceDashboard dashboard(SchoolPrincipal actor) {
        List<FamilyAccountView> accounts =
                jdbc.query(
                        """
                        SELECT fa.id,fa.portal_account_id,fa.account_number,fa.display_name,
                               fa.currency_code,fa.status,pa.email
                        FROM billing_family_account fa
                        JOIN admission_portal_account pa
                          ON pa.id=fa.portal_account_id AND pa.tenant_id=fa.tenant_id
                        WHERE fa.tenant_id=?
                        ORDER BY fa.display_name,fa.account_number
                        """,
                        (result, row) -> accountView(result),
                        actor.tenantId());
        List<AccountSummary> summaries =
                accounts.stream().map(account -> summary(actor.tenantId(), account)).toList();
        BigDecimal billed = sum(summaries, AccountSummary::totalBilled);
        BigDecimal paid = sum(summaries, AccountSummary::totalPaid);
        BigDecimal outstanding = sum(summaries, AccountSummary::balance);
        BigDecimal overdue = sum(summaries, AccountSummary::overdue);
        return new FinanceDashboard(
                clock.instant(),
                CURRENCY,
                accounts.size(),
                billed,
                paid,
                outstanding,
                overdue,
                summaries);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('invoice.read')")
    public FamilyLedger ledger(SchoolPrincipal actor, UUID accountId) {
        return ledgerFor(account(actor.tenantId(), accountId), true);
    }

    @Transactional(readOnly = true)
    public FamilyLedger familyLedger(PortalIdentity parent) {
        FamilyAccountView account =
                jdbc
                        .query(
                                """
                                SELECT fa.id,fa.portal_account_id,fa.account_number,fa.display_name,
                                       fa.currency_code,fa.status,pa.email
                                FROM billing_family_account fa
                                JOIN admission_portal_account pa
                                  ON pa.id=fa.portal_account_id AND pa.tenant_id=fa.tenant_id
                                WHERE fa.tenant_id=? AND fa.portal_account_id=? AND fa.status='ACTIVE'
                                """,
                                (result, row) -> accountView(result),
                                parent.tenantId(),
                                parent.accountId())
                        .stream()
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new BillingNotFoundException(
                                                "Aucun compte financier actif pour cette famille"));
        return ledgerFor(account, false);
    }

    @Transactional
    @PreAuthorize("hasAuthority('invoice.create')")
    public FamilyAccountView createAccount(
            SchoolPrincipal actor, AccountCommand command, String ipAddress) {
        if (command == null || command.portalAccountId() == null) {
            throw new BillingValidationException("Le compte parent est requis");
        }
        List<PortalAccount> parents =
                jdbc.query(
                        "SELECT id,display_name,email FROM admission_portal_account"
                                + " WHERE tenant_id=? AND id=? AND status='ACTIVE'",
                        (result, row) ->
                                new PortalAccount(
                                        result.getObject("id", UUID.class),
                                        result.getString("display_name"),
                                        result.getString("email")),
                        actor.tenantId(),
                        command.portalAccountId());
        if (parents.isEmpty()) {
            throw new BillingNotFoundException("Compte parent actif introuvable");
        }
        if (jdbc.queryForObject(
                        "SELECT count(*) FROM billing_family_account WHERE tenant_id=?"
                                + " AND portal_account_id=?",
                        Integer.class,
                        actor.tenantId(),
                        command.portalAccountId())
                > 0) {
            throw new BillingConflictException(
                    "Un compte financier existe déjà pour cette famille");
        }
        PortalAccount parent = parents.getFirst();
        Instant now = clock.instant();
        UUID id = UUID.randomUUID();
        String displayName = text(command.displayName(), parent.displayName(), "Nom de famille");
        String accountNumber = nextNumber(actor.tenantId(), "FAMILY_ACCOUNT", "FAM", now);
        jdbc.update(
                "INSERT INTO billing_family_account"
                    + " (id,tenant_id,portal_account_id,account_number,display_name,currency_code,"
                    + " status,created_at,updated_at,version) VALUES (?, ?, ?, ?, ?, 'MAD',"
                    + " 'ACTIVE', ?, ?, 0)",
                id,
                actor.tenantId(),
                parent.id(),
                accountNumber,
                displayName,
                timestamp(now),
                timestamp(now));
        FamilyAccountView created = account(actor.tenantId(), id);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "billing.family-account.created",
                "FamilyAccount",
                id.toString(),
                ipAddress,
                Map.of("accountNumber", accountNumber),
                null,
                created);
        return created;
    }

    @Transactional
    @PreAuthorize("hasAuthority('invoice.create')")
    public InvoiceView createInvoice(
            SchoolPrincipal actor, UUID accountId, InvoiceCommand command, String ipAddress) {
        FamilyAccountView account = account(actor.tenantId(), accountId);
        if (command == null || command.lines() == null || command.lines().isEmpty()) {
            throw new BillingValidationException("La facture doit contenir au moins une ligne");
        }
        if (command.lines().size() > 50) {
            throw new BillingValidationException("Une facture est limitée à 50 lignes");
        }
        LocalDate issueDate =
                command.issueDate() == null
                        ? LocalDate.now(clock.withZone(SCHOOL_ZONE))
                        : command.issueDate();
        LocalDate dueDate = command.dueDate() == null ? issueDate.plusDays(30) : command.dueDate();
        if (dueDate.isBefore(issueDate)) {
            throw new BillingValidationException("L'échéance ne peut pas précéder l'émission");
        }
        List<PreparedLine> lines = new ArrayList<>();
        int position = 1;
        for (InvoiceLineCommand raw : command.lines()) {
            lines.add(prepareLine(actor.tenantId(), account, raw, position++));
        }
        BigDecimal subtotal =
                lines.stream().map(PreparedLine::grossAmount).reduce(ZERO, BigDecimal::add);
        BigDecimal discount =
                lines.stream().map(PreparedLine::discountAmount).reduce(ZERO, BigDecimal::add);
        BigDecimal total = money(subtotal.subtract(discount));
        if (total.signum() <= 0) {
            throw new BillingValidationException("Le total de la facture doit être positif");
        }
        Instant now = clock.instant();
        UUID invoiceId = UUID.randomUUID();
        String invoiceNumber = nextNumber(actor.tenantId(), "INVOICE", "FAC", now);
        jdbc.update(
                """
                INSERT INTO billing_invoice
                  (id,tenant_id,family_account_id,invoice_number,issue_date,due_date,currency_code,
                   status,subtotal_amount,discount_amount,total_amount,paid_amount,balance_amount,
                   issued_at,created_at,updated_at,version)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?, 0, ?, NULL, ?, ?, 0)
                """,
                invoiceId,
                actor.tenantId(),
                accountId,
                invoiceNumber,
                issueDate,
                dueDate,
                account.currencyCode(),
                subtotal,
                discount,
                total,
                total,
                timestamp(now),
                timestamp(now));
        for (PreparedLine line : lines) {
            UUID chargeId = UUID.randomUUID();
            jdbc.update(
                    """
                    INSERT INTO billing_charge
                      (id,tenant_id,family_account_id,student_id,discount_id,service_type,label,
                       description,quantity,unit_amount,gross_amount,discount_amount,net_amount,
                       currency_code,charged_on,status,source_reference,created_at,updated_at,version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'POSTED', ?, ?, ?, 0)
                    """,
                    chargeId,
                    actor.tenantId(),
                    accountId,
                    line.studentId(),
                    line.discountId(),
                    line.serviceType(),
                    line.label(),
                    line.description(),
                    line.quantity(),
                    line.unitAmount(),
                    line.grossAmount(),
                    line.discountAmount(),
                    line.totalAmount(),
                    account.currencyCode(),
                    issueDate,
                    invoiceNumber,
                    timestamp(now),
                    timestamp(now));
            jdbc.update(
                    """
                    INSERT INTO billing_invoice_line
                      (id,tenant_id,invoice_id,charge_id,student_id,service_type,label,description,
                       quantity,unit_amount,gross_amount,discount_amount,line_total,line_position,created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    UUID.randomUUID(),
                    actor.tenantId(),
                    invoiceId,
                    chargeId,
                    line.studentId(),
                    line.serviceType(),
                    line.label(),
                    line.description(),
                    line.quantity(),
                    line.unitAmount(),
                    line.grossAmount(),
                    line.discountAmount(),
                    line.totalAmount(),
                    line.position(),
                    timestamp(now));
        }
        InvoiceView created = invoice(actor.tenantId(), invoiceId, true);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "billing.invoice.created",
                "Invoice",
                invoiceId.toString(),
                ipAddress,
                Map.of("invoiceNumber", invoiceNumber, "lineCount", lines.size()),
                null,
                created);
        return created;
    }

    @Transactional
    @PreAuthorize("hasAuthority('invoice.create')")
    public InvoiceView issueInvoice(SchoolPrincipal actor, UUID invoiceId, String ipAddress) {
        InvoiceView before = invoice(actor.tenantId(), invoiceId, true);
        if (!"DRAFT".equals(before.status())) {
            throw new BillingConflictException("Seule une facture brouillon peut être émise");
        }
        Instant now = clock.instant();
        jdbc.update(
                "UPDATE billing_invoice SET"
                        + " status='ISSUED',issued_at=?,updated_at=?,version=version+1 WHERE"
                        + " tenant_id=? AND id=? AND status='DRAFT'",
                timestamp(now),
                timestamp(now),
                actor.tenantId(),
                invoiceId);
        jdbc.update(
                "UPDATE billing_charge SET status='INVOICED',updated_at=?,version=version+1"
                        + " WHERE tenant_id=? AND id IN (SELECT charge_id FROM billing_invoice_line"
                        + " WHERE tenant_id=? AND invoice_id=?)",
                timestamp(now),
                actor.tenantId(),
                actor.tenantId(),
                invoiceId);
        InvoiceView issued = invoice(actor.tenantId(), invoiceId, true);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "billing.invoice.issued",
                "Invoice",
                invoiceId.toString(),
                ipAddress,
                Map.of("invoiceNumber", issued.invoiceNumber()),
                before,
                issued);
        publishAccountingEvent(
                actor,
                "INVOICE_ISSUED",
                invoiceId,
                issued.issueDate(),
                issued.totalAmount(),
                issued.currencyCode(),
                issued.invoiceNumber(),
                "Facture famille " + issued.invoiceNumber());
        return issued;
    }

    @Transactional
    @PreAuthorize("hasAuthority('payment.record')")
    public PaymentView recordPayment(
            SchoolPrincipal actor, UUID accountId, PaymentCommand command, String ipAddress) {
        return recordPaymentInternal(actor, accountId, command, ipAddress);
    }

    PaymentView recordOnlinePayment(
            UUID tenantId, UUID accountId, PaymentCommand command, String ipAddress) {
        SchoolPrincipal providerActor =
                new SchoolPrincipal(
                        null,
                        tenantId,
                        "payment-provider@schoolz.internal",
                        "Payment provider",
                        "fr-MA",
                        Set.of(),
                        Set.of());
        return recordPaymentInternal(providerActor, accountId, command, ipAddress);
    }

    private PaymentView recordPaymentInternal(
            SchoolPrincipal actor, UUID accountId, PaymentCommand command, String ipAddress) {
        FamilyAccountView account = account(actor.tenantId(), accountId);
        if (command == null) {
            throw new BillingValidationException("Le paiement est requis");
        }
        BigDecimal amount = positiveMoney(command.amount(), "Le montant du paiement");
        String method = enumValue(command.paymentMethod(), PAYMENT_METHODS, "Mode de paiement");
        String reference = optionalText(command.externalReference(), 120);
        List<AllocationCommand> requested =
                command.allocations() == null ? List.of() : command.allocations();
        Set<UUID> invoiceIds = new HashSet<>();
        List<PreparedAllocation> allocations = new ArrayList<>();
        BigDecimal allocated = ZERO;
        for (AllocationCommand allocation : requested) {
            if (allocation == null || allocation.invoiceId() == null) {
                throw new BillingValidationException("Chaque affectation doit cibler une facture");
            }
            if (!invoiceIds.add(allocation.invoiceId())) {
                throw new BillingValidationException("Une facture ne peut apparaître qu'une fois");
            }
            BigDecimal allocationAmount = positiveMoney(allocation.amount(), "Le montant affecté");
            InvoiceView invoice = invoice(actor.tenantId(), allocation.invoiceId(), true);
            if (!invoice.familyAccountId().equals(accountId)) {
                throw new BillingNotFoundException("Facture du compte famille introuvable");
            }
            if ("DRAFT".equals(invoice.status()) || "CANCELLED".equals(invoice.status())) {
                throw new BillingConflictException("Une facture non émise ne peut pas être réglée");
            }
            if (allocationAmount.compareTo(invoice.balanceAmount()) > 0) {
                throw new BillingValidationException(
                        "L'affectation dépasse le solde de " + invoice.invoiceNumber());
            }
            allocations.add(new PreparedAllocation(invoice.id(), allocationAmount));
            allocated = allocated.add(allocationAmount);
        }
        if (money(allocated).compareTo(amount) > 0) {
            throw new BillingValidationException(
                    "Le total affecté ne peut pas dépasser le paiement");
        }
        List<ScheduleAllocationCommand> requestedSchedules =
                command.scheduleAllocations() == null ? List.of() : command.scheduleAllocations();
        Set<UUID> scheduleIds = new HashSet<>();
        List<PreparedScheduleAllocation> scheduleAllocations = new ArrayList<>();
        BigDecimal scheduled = ZERO;
        for (ScheduleAllocationCommand allocation : requestedSchedules) {
            if (allocation == null || allocation.paymentScheduleId() == null) {
                throw new BillingValidationException("Chaque affectation doit cibler une échéance");
            }
            if (!scheduleIds.add(allocation.paymentScheduleId())) {
                throw new BillingValidationException("Une échéance ne peut apparaître qu'une fois");
            }
            BigDecimal allocationAmount =
                    positiveMoney(allocation.amount(), "Le montant affecté à l'échéance");
            ScheduleBalance schedule =
                    jdbc
                            .query(
                                    """
                                    SELECT s.amount,s.paid_amount,p.family_account_id
                                    FROM billing_payment_schedule s
                                    JOIN billing_installment_plan p
                                      ON p.id=s.installment_plan_id AND p.tenant_id=s.tenant_id
                                    WHERE s.tenant_id=? AND s.id=? AND p.status='ACTIVE'
                                    """,
                                    (result, row) ->
                                            new ScheduleBalance(
                                                    result.getObject(
                                                            "family_account_id", UUID.class),
                                                    money(result.getBigDecimal("amount")),
                                                    money(result.getBigDecimal("paid_amount"))),
                                    actor.tenantId(),
                                    allocation.paymentScheduleId())
                            .stream()
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new BillingNotFoundException(
                                                    "Échéance active introuvable"));
            if (!schedule.familyAccountId().equals(accountId)) {
                throw new BillingNotFoundException("Échéance du compte famille introuvable");
            }
            if (allocationAmount.compareTo(schedule.amount().subtract(schedule.paidAmount())) > 0) {
                throw new BillingValidationException(
                        "L'affectation dépasse le reste de l'échéance");
            }
            scheduleAllocations.add(
                    new PreparedScheduleAllocation(
                            allocation.paymentScheduleId(), allocationAmount));
            scheduled = scheduled.add(allocationAmount);
        }
        if (money(scheduled).compareTo(amount) > 0) {
            throw new BillingValidationException(
                    "Le total affecté aux échéances ne peut pas dépasser le paiement");
        }
        Instant receivedAt = command.receivedAt() == null ? clock.instant() : command.receivedAt();
        Instant now = clock.instant();
        UUID paymentId = UUID.randomUUID();
        String paymentNumber = nextNumber(actor.tenantId(), "PAYMENT", "PAY", now);
        jdbc.update(
                """
                INSERT INTO billing_payment
                  (id,tenant_id,family_account_id,payment_number,payment_type,reversal_of_payment_id,
                   payment_method,amount,currency_code,external_reference,status,received_at,
                   validated_at,reversal_reason,created_by,created_at)
                VALUES (?, ?, ?, ?, 'PAYMENT', NULL, ?, ?, ?, ?, 'VALIDATED', ?, ?, NULL, ?, ?)
                """,
                paymentId,
                actor.tenantId(),
                accountId,
                paymentNumber,
                method,
                amount,
                account.currencyCode(),
                reference,
                timestamp(receivedAt),
                timestamp(now),
                actor.accountId(),
                timestamp(now));
        for (PreparedAllocation allocation : allocations) {
            jdbc.update(
                    "INSERT INTO billing_payment_allocation"
                            + " (id,tenant_id,payment_id,invoice_id,amount,allocated_at)"
                            + " VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(),
                    actor.tenantId(),
                    paymentId,
                    allocation.invoiceId(),
                    allocation.amount(),
                    timestamp(now));
        }
        for (PreparedScheduleAllocation allocation : scheduleAllocations) {
            jdbc.update(
                    "INSERT INTO billing_schedule_payment_allocation"
                            + " (id,tenant_id,payment_id,payment_schedule_id,amount,allocated_at)"
                            + " VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(),
                    actor.tenantId(),
                    paymentId,
                    allocation.scheduleId(),
                    allocation.amount(),
                    timestamp(now));
        }
        createReceipt(
                actor.tenantId(),
                accountId,
                paymentId,
                amount,
                account.currencyCode(),
                "RECEIPT",
                now);
        allocations.forEach(
                allocation -> recalculateInvoice(actor.tenantId(), allocation.invoiceId()));
        scheduleAllocations.forEach(
                allocation -> recalculateSchedule(actor.tenantId(), allocation.scheduleId()));
        PaymentView created = payment(actor.tenantId(), paymentId);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "billing.payment.validated",
                "Payment",
                paymentId.toString(),
                ipAddress,
                Map.of(
                        "paymentNumber",
                        paymentNumber,
                        "allocationCount",
                        allocations.size(),
                        "scheduleAllocationCount",
                        scheduleAllocations.size(),
                        "method",
                        method),
                null,
                created);
        publishAccountingEvent(
                actor,
                "PAYMENT_VALIDATED",
                paymentId,
                receivedAt.atZone(SCHOOL_ZONE).toLocalDate(),
                created.amount(),
                created.currencyCode(),
                created.paymentNumber(),
                "Encaissement famille " + created.paymentNumber());
        return created;
    }

    @Transactional
    @PreAuthorize("hasAuthority('payment.reverse')")
    public PaymentView reversePayment(
            SchoolPrincipal actor, UUID paymentId, ReversalCommand command, String ipAddress) {
        return reversePaymentInternal(actor, paymentId, command, ipAddress);
    }

    PaymentView reverseOnlinePayment(
            UUID tenantId, UUID paymentId, String reason, String ipAddress) {
        SchoolPrincipal providerActor =
                new SchoolPrincipal(
                        null,
                        tenantId,
                        "payment-provider@schoolz.internal",
                        "Payment provider",
                        "fr-MA",
                        Set.of(),
                        Set.of());
        return reversePaymentInternal(
                providerActor, paymentId, new ReversalCommand(reason), ipAddress);
    }

    private PaymentView reversePaymentInternal(
            SchoolPrincipal actor, UUID paymentId, ReversalCommand command, String ipAddress) {
        PaymentView original = payment(actor.tenantId(), paymentId);
        if (!"PAYMENT".equals(original.paymentType())) {
            throw new BillingConflictException("Une annulation ne peut pas être annulée à nouveau");
        }
        if (!"VALIDATED".equals(original.status())) {
            throw new BillingConflictException("Ce paiement a déjà été annulé");
        }
        String reason = requiredText(command == null ? null : command.reason(), 500, "Le motif");
        Instant now = clock.instant();
        UUID reversalId = UUID.randomUUID();
        String reversalNumber = nextNumber(actor.tenantId(), "PAYMENT_REVERSAL", "ANN", now);
        jdbc.update(
                """
                INSERT INTO billing_payment
                  (id,tenant_id,family_account_id,payment_number,payment_type,reversal_of_payment_id,
                   payment_method,amount,currency_code,external_reference,status,received_at,
                   validated_at,reversal_reason,created_by,created_at)
                VALUES (?, ?, ?, ?, 'REVERSAL', ?, ?, ?, ?, NULL, 'VALIDATED', ?, ?, ?, ?, ?)
                """,
                reversalId,
                actor.tenantId(),
                original.familyAccountId(),
                reversalNumber,
                paymentId,
                original.paymentMethod(),
                original.amount(),
                original.currencyCode(),
                timestamp(now),
                timestamp(now),
                reason,
                actor.accountId(),
                timestamp(now));
        for (PaymentAllocationView allocation : original.allocations()) {
            jdbc.update(
                    "INSERT INTO billing_payment_allocation"
                            + " (id,tenant_id,payment_id,invoice_id,amount,allocated_at)"
                            + " VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(),
                    actor.tenantId(),
                    reversalId,
                    allocation.invoiceId(),
                    allocation.amount().negate(),
                    timestamp(now));
        }
        for (SchedulePaymentAllocationView allocation : original.scheduleAllocations()) {
            jdbc.update(
                    "INSERT INTO billing_schedule_payment_allocation"
                            + " (id,tenant_id,payment_id,payment_schedule_id,amount,allocated_at)"
                            + " VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(),
                    actor.tenantId(),
                    reversalId,
                    allocation.paymentScheduleId(),
                    allocation.amount().negate(),
                    timestamp(now));
        }
        jdbc.update(
                "UPDATE billing_payment SET status='REVERSED' WHERE tenant_id=? AND id=?",
                actor.tenantId(),
                paymentId);
        jdbc.update(
                "UPDATE billing_receipt SET status='REVERSED' WHERE tenant_id=? AND payment_id=?",
                actor.tenantId(),
                paymentId);
        createReceipt(
                actor.tenantId(),
                original.familyAccountId(),
                reversalId,
                original.amount(),
                original.currencyCode(),
                "REVERSAL_NOTICE",
                now);
        original.allocations()
                .forEach(
                        allocation -> recalculateInvoice(actor.tenantId(), allocation.invoiceId()));
        original.scheduleAllocations()
                .forEach(
                        allocation ->
                                recalculateSchedule(
                                        actor.tenantId(), allocation.paymentScheduleId()));
        PaymentView reversal = payment(actor.tenantId(), reversalId);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "billing.payment.reversed",
                "Payment",
                paymentId.toString(),
                ipAddress,
                Map.of(
                        "originalPaymentNumber",
                        original.paymentNumber(),
                        "reversalPaymentNumber",
                        reversalNumber,
                        "reason",
                        reason),
                original,
                reversal);
        publishAccountingEvent(
                actor,
                "PAYMENT_REVERSED",
                reversalId,
                LocalDate.now(clock.withZone(SCHOOL_ZONE)),
                reversal.amount(),
                reversal.currencyCode(),
                reversal.paymentNumber(),
                "Annulation encaissement " + original.paymentNumber());
        return reversal;
    }

    @Transactional
    @PreAuthorize("hasAuthority('invoice.adjust')")
    public CreditView createCredit(
            SchoolPrincipal actor, UUID accountId, CreditCommand command, String ipAddress) {
        FamilyAccountView account = account(actor.tenantId(), accountId);
        BigDecimal amount =
                positiveMoney(command == null ? null : command.amount(), "Le montant de l'avoir");
        String reason = requiredText(command == null ? null : command.reason(), 500, "Le motif");
        Instant now = clock.instant();
        UUID id = UUID.randomUUID();
        String number = nextNumber(actor.tenantId(), "CREDIT", "AVO", now);
        jdbc.update(
                "INSERT INTO billing_credit"
                        + " (id,tenant_id,family_account_id,credit_number,amount,available_amount,"
                        + " currency_code,reason,status,issued_at,created_by)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'AVAILABLE', ?, ?)",
                id,
                actor.tenantId(),
                accountId,
                number,
                amount,
                amount,
                account.currencyCode(),
                reason,
                timestamp(now),
                actor.accountId());
        CreditView created = credit(actor.tenantId(), id);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "billing.credit.created",
                "Credit",
                id.toString(),
                ipAddress,
                Map.of("creditNumber", number, "reason", reason),
                null,
                created);
        publishAccountingEvent(
                actor,
                "CREDIT_ISSUED",
                id,
                LocalDate.now(clock.withZone(SCHOOL_ZONE)),
                created.amount(),
                created.currencyCode(),
                created.creditNumber(),
                "Avoir famille " + created.creditNumber());
        return created;
    }

    @Transactional
    @PreAuthorize("hasAuthority('payment.record')")
    public RefundView refund(
            SchoolPrincipal actor, UUID accountId, RefundCommand command, String ipAddress) {
        FamilyAccountView account = account(actor.tenantId(), accountId);
        if (command == null || command.creditId() == null) {
            throw new BillingValidationException("L'avoir à rembourser est requis");
        }
        CreditView before = credit(actor.tenantId(), command.creditId());
        if (!before.familyAccountId().equals(accountId)) {
            throw new BillingNotFoundException("Avoir du compte famille introuvable");
        }
        BigDecimal amount = positiveMoney(command.amount(), "Le montant du remboursement");
        if (amount.compareTo(before.availableAmount()) > 0) {
            throw new BillingValidationException("Le remboursement dépasse l'avoir disponible");
        }
        String method = enumValue(command.refundMethod(), PAYMENT_METHODS, "Mode de remboursement");
        String reason = requiredText(command.reason(), 500, "Le motif");
        Instant now = clock.instant();
        UUID id = UUID.randomUUID();
        String number = nextNumber(actor.tenantId(), "REFUND", "RMB", now);
        jdbc.update(
                "INSERT INTO billing_refund"
                    + " (id,tenant_id,family_account_id,credit_id,refund_number,amount,currency_code,"
                    + " refund_method,reason,status,refunded_at,created_by) VALUES (?, ?, ?, ?, ?,"
                    + " ?, ?, ?, ?, 'VALIDATED', ?, ?)",
                id,
                actor.tenantId(),
                accountId,
                command.creditId(),
                number,
                amount,
                account.currencyCode(),
                method,
                reason,
                timestamp(now),
                actor.accountId());
        BigDecimal available = money(before.availableAmount().subtract(amount));
        jdbc.update(
                "UPDATE billing_credit SET available_amount=?,status=? WHERE tenant_id=? AND id=?",
                available,
                available.signum() == 0 ? "APPLIED" : "AVAILABLE",
                actor.tenantId(),
                command.creditId());
        RefundView created = refund(actor.tenantId(), id);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "billing.refund.validated",
                "Refund",
                id.toString(),
                ipAddress,
                Map.of("refundNumber", number, "creditNumber", before.creditNumber()),
                before,
                created);
        publishAccountingEvent(
                actor,
                "REFUND_VALIDATED",
                id,
                LocalDate.now(clock.withZone(SCHOOL_ZONE)),
                created.amount(),
                created.currencyCode(),
                created.refundNumber(),
                "Remboursement famille " + created.refundNumber());
        return created;
    }

    @Transactional
    @PreAuthorize("hasAuthority('invoice.adjust')")
    public AdjustmentView adjust(
            SchoolPrincipal actor,
            UUID accountId,
            FinancialAdjustmentCommand command,
            String ipAddress) {
        FamilyAccountView account = account(actor.tenantId(), accountId);
        if (command == null) {
            throw new BillingValidationException("L'ajustement est requis");
        }
        String direction = enumValue(command.direction(), Set.of("DEBIT", "CREDIT"), "Sens");
        BigDecimal amount = positiveMoney(command.amount(), "Le montant de l'ajustement");
        String reason = requiredText(command.reason(), 500, "Le motif");
        if (command.invoiceId() != null) {
            InvoiceView invoice = invoice(actor.tenantId(), command.invoiceId(), true);
            if (!invoice.familyAccountId().equals(accountId)) {
                throw new BillingNotFoundException("Facture du compte famille introuvable");
            }
            if ("DRAFT".equals(invoice.status())) {
                throw new BillingConflictException("Émettez la facture avant de l'ajuster");
            }
        }
        Instant now = clock.instant();
        UUID id = UUID.randomUUID();
        String number = nextNumber(actor.tenantId(), "ADJUSTMENT", "AJU", now);
        jdbc.update(
                "INSERT INTO billing_financial_adjustment"
                    + " (id,tenant_id,family_account_id,invoice_id,adjustment_number,direction,amount,"
                    + " currency_code,reason,status,reversal_of_adjustment_id,posted_at,created_by)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'POSTED', NULL, ?, ?)",
                id,
                actor.tenantId(),
                accountId,
                command.invoiceId(),
                number,
                direction,
                amount,
                account.currencyCode(),
                reason,
                timestamp(now),
                actor.accountId());
        if (command.invoiceId() != null) {
            recalculateInvoice(actor.tenantId(), command.invoiceId());
        }
        AdjustmentView created = adjustment(actor.tenantId(), id);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "billing.adjustment.posted",
                "FinancialAdjustment",
                id.toString(),
                ipAddress,
                Map.of("adjustmentNumber", number, "direction", direction, "reason", reason),
                null,
                created);
        publishAccountingEvent(
                actor,
                "DEBIT".equals(direction) ? "ADJUSTMENT_DEBIT" : "ADJUSTMENT_CREDIT",
                id,
                LocalDate.now(clock.withZone(SCHOOL_ZONE)),
                created.amount(),
                created.currencyCode(),
                created.adjustmentNumber(),
                "Ajustement famille " + created.adjustmentNumber());
        return created;
    }

    private void publishAccountingEvent(
            SchoolPrincipal actor,
            String eventType,
            UUID sourceId,
            LocalDate accountingDate,
            BigDecimal amount,
            String currency,
            String reference,
            String description) {
        events.publishEvent(
                new FamilyLedgerAccountingEvent(
                        actor.tenantId(),
                        actor.accountId(),
                        eventType,
                        sourceId.toString(),
                        accountingDate,
                        money(amount),
                        currency,
                        reference,
                        description));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('invoice.read')")
    public ReceiptView receipt(SchoolPrincipal actor, UUID receiptId) {
        return receiptFor(actor.tenantId(), receiptId, null);
    }

    @Transactional(readOnly = true)
    public ReceiptView familyReceipt(PortalIdentity parent, UUID receiptId) {
        return receiptFor(parent.tenantId(), receiptId, parent.accountId());
    }

    private FamilyLedger ledgerFor(FamilyAccountView account, boolean includeDrafts) {
        UUID tenantId = tenantId(account.id());
        String draftClause = includeDrafts ? "" : " AND status<>'DRAFT'";
        List<InvoiceView> invoices =
                jdbc
                        .query(
                                "SELECT id FROM billing_invoice WHERE tenant_id=?"
                                        + " AND family_account_id=?"
                                        + draftClause
                                        + " ORDER BY issue_date DESC,invoice_number DESC",
                                (result, row) -> result.getObject(1, UUID.class),
                                tenantId,
                                account.id())
                        .stream()
                        .map(id -> invoice(tenantId, id, includeDrafts))
                        .toList();
        List<PaymentView> payments =
                jdbc
                        .query(
                                "SELECT id FROM billing_payment WHERE tenant_id=? AND"
                                    + " family_account_id=? ORDER BY received_at DESC,created_at"
                                    + " DESC",
                                (result, row) -> result.getObject(1, UUID.class),
                                tenantId,
                                account.id())
                        .stream()
                        .map(id -> payment(tenantId, id))
                        .toList();
        List<CreditView> credits = credits(tenantId, account.id());
        List<RefundView> refunds = refunds(tenantId, account.id());
        List<AdjustmentView> adjustments = adjustments(tenantId, account.id());
        List<ReceiptView> receipts = receipts(tenantId, account.id());
        AccountSummary summary = summary(tenantId, account);
        return new FamilyLedger(
                clock.instant(),
                account,
                summary,
                invoices,
                payments,
                credits,
                refunds,
                adjustments,
                receipts,
                ledgerEntries(invoices, payments, credits, refunds, adjustments));
    }

    private AccountSummary summary(UUID tenantId, FamilyAccountView account) {
        BigDecimal invoices =
                queryMoney(
                        "SELECT COALESCE(SUM(total_amount),0) FROM billing_invoice"
                                + " WHERE tenant_id=? AND family_account_id=? AND status<>'DRAFT'",
                        tenantId,
                        account.id());
        BigDecimal adjustmentTotal =
                queryMoney(
                        "SELECT COALESCE(SUM(CASE WHEN direction='DEBIT' THEN amount ELSE -amount"
                                + " END),0) FROM billing_financial_adjustment WHERE tenant_id=? AND"
                                + " family_account_id=? AND status='POSTED'",
                        tenantId,
                        account.id());
        BigDecimal payments =
                queryMoney(
                        "SELECT COALESCE(SUM(CASE WHEN payment_type='PAYMENT' THEN amount ELSE"
                                + " -amount END),0) FROM billing_payment WHERE tenant_id=? AND"
                                + " family_account_id=? AND status IN ('VALIDATED','REVERSED')",
                        tenantId,
                        account.id());
        BigDecimal credits =
                queryMoney(
                        "SELECT COALESCE(SUM(amount),0) FROM billing_credit WHERE tenant_id=?"
                                + " AND family_account_id=? AND status<>'REVERSED'",
                        tenantId,
                        account.id());
        BigDecimal refunds =
                queryMoney(
                        "SELECT COALESCE(SUM(amount),0) FROM billing_refund WHERE tenant_id=?"
                                + " AND family_account_id=? AND status='VALIDATED'",
                        tenantId,
                        account.id());
        BigDecimal overdue =
                queryMoney(
                        "SELECT COALESCE(SUM(balance_amount),0) FROM billing_invoice"
                                + " WHERE tenant_id=? AND family_account_id=?"
                                + " AND status IN ('ISSUED','PARTIALLY_PAID') AND due_date<?",
                        tenantId,
                        account.id(),
                        LocalDate.now(clock.withZone(SCHOOL_ZONE)));
        BigDecimal balance =
                money(
                        invoices.add(adjustmentTotal)
                                .subtract(payments)
                                .subtract(credits)
                                .add(refunds));
        return new AccountSummary(
                account, invoices, payments, credits, refunds, adjustmentTotal, balance, overdue);
    }

    private List<LedgerEntry> ledgerEntries(
            List<InvoiceView> invoices,
            List<PaymentView> payments,
            List<CreditView> credits,
            List<RefundView> refunds,
            List<AdjustmentView> adjustments) {
        List<RawLedgerEntry> entries = new ArrayList<>();
        invoices.stream()
                .filter(invoice -> !"DRAFT".equals(invoice.status()))
                .forEach(
                        invoice ->
                                entries.add(
                                        new RawLedgerEntry(
                                                invoice.id(),
                                                "INVOICE",
                                                invoice.invoiceNumber(),
                                                "Facture " + invoice.invoiceNumber(),
                                                invoice.totalAmount(),
                                                invoice.issuedAt(),
                                                invoice.status())));
        payments.forEach(
                payment ->
                        entries.add(
                                new RawLedgerEntry(
                                        payment.id(),
                                        payment.paymentType(),
                                        payment.paymentNumber(),
                                        "PAYMENT".equals(payment.paymentType())
                                                ? "Paiement " + payment.paymentNumber()
                                                : "Annulation " + payment.paymentNumber(),
                                        "PAYMENT".equals(payment.paymentType())
                                                ? payment.amount().negate()
                                                : payment.amount(),
                                        payment.receivedAt(),
                                        payment.status())));
        credits.forEach(
                credit ->
                        entries.add(
                                new RawLedgerEntry(
                                        credit.id(),
                                        "CREDIT",
                                        credit.creditNumber(),
                                        credit.reason(),
                                        credit.amount().negate(),
                                        credit.issuedAt(),
                                        credit.status())));
        refunds.forEach(
                refund ->
                        entries.add(
                                new RawLedgerEntry(
                                        refund.id(),
                                        "REFUND",
                                        refund.refundNumber(),
                                        refund.reason(),
                                        refund.amount(),
                                        refund.refundedAt(),
                                        refund.status())));
        adjustments.forEach(
                adjustment ->
                        entries.add(
                                new RawLedgerEntry(
                                        adjustment.id(),
                                        "ADJUSTMENT",
                                        adjustment.adjustmentNumber(),
                                        adjustment.reason(),
                                        "DEBIT".equals(adjustment.direction())
                                                ? adjustment.amount()
                                                : adjustment.amount().negate(),
                                        adjustment.postedAt(),
                                        adjustment.status())));
        entries.sort(Comparator.comparing(RawLedgerEntry::occurredAt));
        BigDecimal running = ZERO;
        List<LedgerEntry> result = new ArrayList<>();
        for (RawLedgerEntry entry : entries) {
            running = money(running.add(entry.amount()));
            result.add(
                    new LedgerEntry(
                            entry.id(),
                            entry.type(),
                            entry.reference(),
                            entry.label(),
                            entry.amount(),
                            entry.occurredAt(),
                            entry.status(),
                            running));
        }
        result.sort(Comparator.comparing(LedgerEntry::occurredAt).reversed());
        return List.copyOf(result);
    }

    private PreparedLine prepareLine(
            UUID tenantId, FamilyAccountView account, InvoiceLineCommand raw, int position) {
        if (raw == null) {
            throw new BillingValidationException("Une ligne de facture est vide");
        }
        String serviceType = enumValue(raw.serviceType(), SERVICE_TYPES, "Service");
        String label = requiredText(raw.label(), 180, "Le libellé");
        String description = optionalText(raw.description(), 1000);
        BigDecimal quantity = positiveDecimal(raw.quantity(), "La quantité", 2);
        BigDecimal unitAmount = nonNegativeMoney(raw.unitAmount(), "Le prix unitaire");
        BigDecimal gross = money(quantity.multiply(unitAmount));
        if (raw.studentId() != null) {
            requireLinkedStudent(tenantId, account.portalAccountId(), raw.studentId());
        }
        BigDecimal discount;
        UUID discountId = raw.discountId();
        if (discountId != null && raw.discountAmount() != null) {
            throw new BillingValidationException(
                    "Choisissez une remise configurée ou un montant manuel, pas les deux");
        }
        if (discountId != null) {
            DiscountRule rule = discountRule(tenantId, account.id(), discountId);
            discount =
                    "PERCENTAGE".equals(rule.type())
                            ? money(
                                    gross.multiply(rule.value())
                                            .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP))
                            : money(rule.value().min(gross));
        } else {
            discount = nonNegativeMoney(raw.discountAmount(), "La remise");
        }
        if (discount.compareTo(gross) > 0) {
            throw new BillingValidationException("La remise dépasse le montant brut de la ligne");
        }
        return new PreparedLine(
                position,
                raw.studentId(),
                discountId,
                serviceType,
                label,
                description,
                quantity,
                unitAmount,
                gross,
                discount,
                money(gross.subtract(discount)));
    }

    private DiscountRule discountRule(UUID tenantId, UUID accountId, UUID discountId) {
        LocalDate today = LocalDate.now(clock.withZone(SCHOOL_ZONE));
        return jdbc
                .query(
                        """
                        SELECT discount_type,discount_value FROM billing_discount
                        WHERE tenant_id=? AND family_account_id=? AND id=? AND active=TRUE
                          AND (starts_on IS NULL OR starts_on<=?)
                          AND (ends_on IS NULL OR ends_on>=?)
                        """,
                        (result, row) ->
                                new DiscountRule(
                                        result.getString("discount_type"),
                                        money(result.getBigDecimal("discount_value"))),
                        tenantId,
                        accountId,
                        discountId,
                        today,
                        today)
                .stream()
                .findFirst()
                .orElseThrow(() -> new BillingNotFoundException("Remise active introuvable"));
    }

    private void requireLinkedStudent(UUID tenantId, UUID portalAccountId, UUID studentId) {
        Integer count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM enrollment_portal_student_link WHERE tenant_id=?"
                                + " AND portal_account_id=? AND student_id=?",
                        Integer.class,
                        tenantId,
                        portalAccountId,
                        studentId);
        if (count == null || count == 0) {
            throw new BillingNotFoundException("Élève lié au compte famille introuvable");
        }
    }

    private void recalculateInvoice(UUID tenantId, UUID invoiceId) {
        InvoiceView invoice = invoice(tenantId, invoiceId, true);
        BigDecimal paid =
                queryMoney(
                        "SELECT COALESCE(SUM(a.amount),0) FROM billing_payment_allocation a JOIN"
                            + " billing_payment p ON p.id=a.payment_id AND p.tenant_id=a.tenant_id"
                            + " WHERE a.tenant_id=? AND a.invoice_id=? AND p.status IN"
                            + " ('VALIDATED','REVERSED')",
                        tenantId,
                        invoiceId);
        BigDecimal adjustments =
                queryMoney(
                        "SELECT COALESCE(SUM(CASE WHEN direction='DEBIT' THEN amount ELSE -amount"
                                + " END),0) FROM billing_financial_adjustment WHERE tenant_id=? AND"
                                + " invoice_id=? AND status='POSTED'",
                        tenantId,
                        invoiceId);
        BigDecimal balance = money(invoice.totalAmount().add(adjustments).subtract(paid).max(ZERO));
        String status;
        if (balance.signum() == 0) {
            status = "PAID";
        } else if (paid.signum() > 0 || adjustments.signum() != 0) {
            status = "PARTIALLY_PAID";
        } else {
            status = "ISSUED";
        }
        jdbc.update(
                "UPDATE billing_invoice SET paid_amount=?,balance_amount=?,status=?,updated_at=?,"
                        + " version=version+1 WHERE tenant_id=? AND id=?",
                paid.max(ZERO),
                balance,
                status,
                timestamp(clock.instant()),
                tenantId,
                invoiceId);
    }

    private void recalculateSchedule(UUID tenantId, UUID scheduleId) {
        ScheduleBalance schedule =
                jdbc
                        .query(
                                """
                                SELECT p.family_account_id,s.amount,s.paid_amount
                                FROM billing_payment_schedule s
                                JOIN billing_installment_plan p
                                  ON p.id=s.installment_plan_id AND p.tenant_id=s.tenant_id
                                WHERE s.tenant_id=? AND s.id=?
                                """,
                                (result, row) ->
                                        new ScheduleBalance(
                                                result.getObject("family_account_id", UUID.class),
                                                money(result.getBigDecimal("amount")),
                                                money(result.getBigDecimal("paid_amount"))),
                                tenantId,
                                scheduleId)
                        .stream()
                        .findFirst()
                        .orElseThrow(() -> new BillingNotFoundException("Échéance introuvable"));
        BigDecimal paid =
                queryMoney(
                        "SELECT COALESCE(SUM(a.amount),0)"
                                + " FROM billing_schedule_payment_allocation a"
                                + " JOIN billing_payment p ON p.id=a.payment_id"
                                + " AND p.tenant_id=a.tenant_id"
                                + " WHERE a.tenant_id=? AND a.payment_schedule_id=?"
                                + " AND p.status IN ('VALIDATED','REVERSED')",
                        tenantId,
                        scheduleId);
        paid = money(paid.max(ZERO).min(schedule.amount()));
        boolean settled = paid.compareTo(schedule.amount()) == 0;
        jdbc.update(
                "UPDATE billing_payment_schedule SET paid_amount=?,status=?,paid_at=?,updated_at=?"
                        + " WHERE tenant_id=? AND id=?",
                paid,
                settled ? "PAID" : "SCHEDULED",
                settled ? timestamp(clock.instant()) : null,
                timestamp(clock.instant()),
                tenantId,
                scheduleId);
    }

    private void createReceipt(
            UUID tenantId,
            UUID accountId,
            UUID paymentId,
            BigDecimal amount,
            String currency,
            String type,
            Instant now) {
        String receiptNumber = nextNumber(tenantId, "RECEIPT", "REC", now);
        jdbc.update(
                "INSERT INTO billing_receipt"
                    + " (id,tenant_id,family_account_id,payment_id,receipt_number,receipt_type,"
                    + " amount,currency_code,issued_at,status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?,"
                    + " 'VALID')",
                UUID.randomUUID(),
                tenantId,
                accountId,
                paymentId,
                receiptNumber,
                type,
                amount,
                currency,
                timestamp(now));
    }

    private String nextNumber(UUID tenantId, String type, String prefix, Instant instant) {
        int year = instant.atZone(SCHOOL_ZONE).getYear();
        jdbc.queryForObject(
                "SELECT id FROM identity_tenant WHERE id=? FOR UPDATE", UUID.class, tenantId);
        Integer sequenceCount =
                jdbc.queryForObject(
                        "SELECT count(*) FROM billing_document_sequence WHERE tenant_id=?"
                                + " AND document_type=? AND calendar_year=?",
                        Integer.class,
                        tenantId,
                        type,
                        year);
        if (sequenceCount == null || sequenceCount == 0) {
            jdbc.update(
                    "INSERT INTO billing_document_sequence"
                        + " (tenant_id,document_type,calendar_year,next_value) VALUES (?, ?, ?, 1)",
                    tenantId,
                    type,
                    year);
        }
        Long value =
                jdbc.queryForObject(
                        "SELECT next_value FROM billing_document_sequence WHERE tenant_id=?"
                                + " AND document_type=? AND calendar_year=? FOR UPDATE",
                        Long.class,
                        tenantId,
                        type,
                        year);
        if (value == null) {
            throw new IllegalStateException("Séquence financière indisponible");
        }
        jdbc.update(
                "UPDATE billing_document_sequence SET next_value=? WHERE tenant_id=?"
                        + " AND document_type=? AND calendar_year=?",
                value + 1,
                tenantId,
                type,
                year);
        return "%s-%d-%06d".formatted(prefix, year, value);
    }

    private FamilyAccountView account(UUID tenantId, UUID accountId) {
        return jdbc
                .query(
                        """
                        SELECT fa.id,fa.portal_account_id,fa.account_number,fa.display_name,
                               fa.currency_code,fa.status,pa.email
                        FROM billing_family_account fa
                        JOIN admission_portal_account pa
                          ON pa.id=fa.portal_account_id AND pa.tenant_id=fa.tenant_id
                        WHERE fa.tenant_id=? AND fa.id=?
                        """,
                        (result, row) -> accountView(result),
                        tenantId,
                        accountId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new BillingNotFoundException("Compte famille introuvable"));
    }

    private UUID tenantId(UUID accountId) {
        return jdbc
                .query(
                        "SELECT tenant_id FROM billing_family_account WHERE id=?",
                        (result, row) -> result.getObject(1, UUID.class),
                        accountId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new BillingNotFoundException("Compte famille introuvable"));
    }

    private InvoiceView invoice(UUID tenantId, UUID invoiceId, boolean includeDraft) {
        String draftClause = includeDraft ? "" : " AND status<>'DRAFT'";
        return jdbc
                .query(
                        "SELECT * FROM billing_invoice WHERE tenant_id=? AND id=?" + draftClause,
                        (result, row) -> invoiceView(result),
                        tenantId,
                        invoiceId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new BillingNotFoundException("Facture introuvable"));
    }

    private InvoiceView invoiceView(ResultSet result) throws SQLException {
        UUID tenantId = result.getObject("tenant_id", UUID.class);
        UUID invoiceId = result.getObject("id", UUID.class);
        List<InvoiceLineView> lines =
                jdbc.query(
                        """
                        SELECT l.id,l.student_id,l.service_type,l.label,l.description,l.quantity,
                               l.unit_amount,l.gross_amount,l.discount_amount,l.line_total,l.line_position,
                               p.first_name,p.last_name
                        FROM billing_invoice_line l
                        LEFT JOIN student_profile sp
                          ON sp.id=l.student_id AND sp.tenant_id=l.tenant_id
                        LEFT JOIN person p ON p.id=sp.person_id AND p.tenant_id=sp.tenant_id
                        WHERE l.tenant_id=? AND l.invoice_id=? ORDER BY l.line_position
                        """,
                        (line, row) ->
                                new InvoiceLineView(
                                        line.getObject("id", UUID.class),
                                        line.getObject("student_id", UUID.class),
                                        names(
                                                line.getString("first_name"),
                                                line.getString("last_name")),
                                        line.getString("service_type"),
                                        line.getString("label"),
                                        line.getString("description"),
                                        line.getBigDecimal("quantity"),
                                        money(line.getBigDecimal("unit_amount")),
                                        money(line.getBigDecimal("gross_amount")),
                                        money(line.getBigDecimal("discount_amount")),
                                        money(line.getBigDecimal("line_total")),
                                        line.getInt("line_position")),
                        tenantId,
                        invoiceId);
        return new InvoiceView(
                invoiceId,
                result.getObject("family_account_id", UUID.class),
                result.getString("invoice_number"),
                result.getObject("issue_date", LocalDate.class),
                result.getObject("due_date", LocalDate.class),
                result.getString("currency_code"),
                result.getString("status"),
                money(result.getBigDecimal("subtotal_amount")),
                money(result.getBigDecimal("discount_amount")),
                money(result.getBigDecimal("total_amount")),
                money(result.getBigDecimal("paid_amount")),
                money(result.getBigDecimal("balance_amount")),
                instant(result, "issued_at"),
                lines);
    }

    private PaymentView payment(UUID tenantId, UUID paymentId) {
        return jdbc
                .query(
                        """
                        SELECT p.*,r.id AS receipt_id,r.receipt_number,r.status AS receipt_status
                        FROM billing_payment p
                        LEFT JOIN billing_receipt r
                          ON r.payment_id=p.id AND r.tenant_id=p.tenant_id
                        WHERE p.tenant_id=? AND p.id=?
                        """,
                        (result, row) -> paymentView(result),
                        tenantId,
                        paymentId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new BillingNotFoundException("Paiement introuvable"));
    }

    private PaymentView paymentView(ResultSet result) throws SQLException {
        UUID tenantId = result.getObject("tenant_id", UUID.class);
        UUID paymentId = result.getObject("id", UUID.class);
        List<PaymentAllocationView> allocations =
                jdbc.query(
                        """
                        SELECT a.invoice_id,i.invoice_number,a.amount
                        FROM billing_payment_allocation a
                        JOIN billing_invoice i
                          ON i.id=a.invoice_id AND i.tenant_id=a.tenant_id
                        WHERE a.tenant_id=? AND a.payment_id=? ORDER BY i.invoice_number
                        """,
                        (allocation, row) ->
                                new PaymentAllocationView(
                                        allocation.getObject("invoice_id", UUID.class),
                                        allocation.getString("invoice_number"),
                                        money(allocation.getBigDecimal("amount"))),
                        tenantId,
                        paymentId);
        List<SchedulePaymentAllocationView> scheduleAllocations =
                jdbc.query(
                        """
                        SELECT a.payment_schedule_id,p.plan_number,s.due_date,a.amount
                        FROM billing_schedule_payment_allocation a
                        JOIN billing_payment_schedule s
                          ON s.id=a.payment_schedule_id AND s.tenant_id=a.tenant_id
                        JOIN billing_installment_plan p
                          ON p.id=s.installment_plan_id AND p.tenant_id=s.tenant_id
                        WHERE a.tenant_id=? AND a.payment_id=?
                        ORDER BY s.due_date,p.plan_number
                        """,
                        (allocation, row) ->
                                new SchedulePaymentAllocationView(
                                        allocation.getObject("payment_schedule_id", UUID.class),
                                        allocation.getString("plan_number"),
                                        allocation.getObject("due_date", LocalDate.class),
                                        money(allocation.getBigDecimal("amount"))),
                        tenantId,
                        paymentId);
        return new PaymentView(
                paymentId,
                result.getObject("family_account_id", UUID.class),
                result.getString("payment_number"),
                result.getString("payment_type"),
                result.getObject("reversal_of_payment_id", UUID.class),
                result.getString("payment_method"),
                money(result.getBigDecimal("amount")),
                result.getString("currency_code"),
                result.getString("external_reference"),
                result.getString("status"),
                instant(result, "received_at"),
                result.getString("reversal_reason"),
                result.getObject("receipt_id", UUID.class),
                result.getString("receipt_number"),
                result.getString("receipt_status"),
                allocations,
                scheduleAllocations);
    }

    private CreditView credit(UUID tenantId, UUID id) {
        return credits(tenantId, null).stream()
                .filter(credit -> credit.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new BillingNotFoundException("Avoir introuvable"));
    }

    private List<CreditView> credits(UUID tenantId, UUID accountId) {
        String accountClause = accountId == null ? "" : " AND family_account_id=?";
        Object[] parameters =
                accountId == null ? new Object[] {tenantId} : new Object[] {tenantId, accountId};
        return jdbc.query(
                "SELECT * FROM billing_credit WHERE tenant_id=?"
                        + accountClause
                        + " ORDER BY issued_at DESC",
                (result, row) ->
                        new CreditView(
                                result.getObject("id", UUID.class),
                                result.getObject("family_account_id", UUID.class),
                                result.getString("credit_number"),
                                money(result.getBigDecimal("amount")),
                                money(result.getBigDecimal("available_amount")),
                                result.getString("currency_code"),
                                result.getString("reason"),
                                result.getString("status"),
                                instant(result, "issued_at")),
                parameters);
    }

    private RefundView refund(UUID tenantId, UUID id) {
        return refunds(tenantId, null).stream()
                .filter(refund -> refund.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new BillingNotFoundException("Remboursement introuvable"));
    }

    private List<RefundView> refunds(UUID tenantId, UUID accountId) {
        String accountClause = accountId == null ? "" : " AND family_account_id=?";
        Object[] parameters =
                accountId == null ? new Object[] {tenantId} : new Object[] {tenantId, accountId};
        return jdbc.query(
                "SELECT * FROM billing_refund WHERE tenant_id=?"
                        + accountClause
                        + " ORDER BY refunded_at DESC",
                (result, row) ->
                        new RefundView(
                                result.getObject("id", UUID.class),
                                result.getObject("family_account_id", UUID.class),
                                result.getObject("credit_id", UUID.class),
                                result.getString("refund_number"),
                                money(result.getBigDecimal("amount")),
                                result.getString("currency_code"),
                                result.getString("refund_method"),
                                result.getString("reason"),
                                result.getString("status"),
                                instant(result, "refunded_at")),
                parameters);
    }

    private AdjustmentView adjustment(UUID tenantId, UUID id) {
        return adjustments(tenantId, null).stream()
                .filter(adjustment -> adjustment.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new BillingNotFoundException("Ajustement introuvable"));
    }

    private List<AdjustmentView> adjustments(UUID tenantId, UUID accountId) {
        String accountClause = accountId == null ? "" : " AND family_account_id=?";
        Object[] parameters =
                accountId == null ? new Object[] {tenantId} : new Object[] {tenantId, accountId};
        return jdbc.query(
                "SELECT * FROM billing_financial_adjustment WHERE tenant_id=?"
                        + accountClause
                        + " ORDER BY posted_at DESC",
                (result, row) ->
                        new AdjustmentView(
                                result.getObject("id", UUID.class),
                                result.getObject("family_account_id", UUID.class),
                                result.getObject("invoice_id", UUID.class),
                                result.getString("adjustment_number"),
                                result.getString("direction"),
                                money(result.getBigDecimal("amount")),
                                result.getString("currency_code"),
                                result.getString("reason"),
                                result.getString("status"),
                                instant(result, "posted_at")),
                parameters);
    }

    private List<ReceiptView> receipts(UUID tenantId, UUID accountId) {
        return jdbc.query(
                """
                SELECT r.*,p.payment_number,p.payment_method
                FROM billing_receipt r
                JOIN billing_payment p ON p.id=r.payment_id AND p.tenant_id=r.tenant_id
                WHERE r.tenant_id=? AND r.family_account_id=?
                ORDER BY r.issued_at DESC
                """,
                (result, row) -> receiptView(result),
                tenantId,
                accountId);
    }

    private ReceiptView receiptFor(UUID tenantId, UUID receiptId, UUID portalAccountId) {
        String parentJoin =
                portalAccountId == null
                        ? ""
                        : " JOIN billing_family_account fa ON fa.id=r.family_account_id"
                                + " AND fa.tenant_id=r.tenant_id";
        String parentClause = portalAccountId == null ? "" : " AND fa.portal_account_id=?";
        Object[] parameters =
                portalAccountId == null
                        ? new Object[] {tenantId, receiptId}
                        : new Object[] {tenantId, receiptId, portalAccountId};
        return jdbc
                .query(
                        "SELECT r.*,p.payment_number,p.payment_method FROM billing_receipt r JOIN"
                            + " billing_payment p ON p.id=r.payment_id AND p.tenant_id=r.tenant_id"
                                + parentJoin
                                + " WHERE r.tenant_id=? AND r.id=?"
                                + parentClause,
                        (result, row) -> receiptView(result),
                        parameters)
                .stream()
                .findFirst()
                .orElseThrow(() -> new BillingNotFoundException("Reçu introuvable"));
    }

    private static ReceiptView receiptView(ResultSet result) throws SQLException {
        return new ReceiptView(
                result.getObject("id", UUID.class),
                result.getObject("family_account_id", UUID.class),
                result.getObject("payment_id", UUID.class),
                result.getString("payment_number"),
                result.getString("payment_method"),
                result.getString("receipt_number"),
                result.getString("receipt_type"),
                money(result.getBigDecimal("amount")),
                result.getString("currency_code"),
                instant(result, "issued_at"),
                result.getString("status"));
    }

    private static FamilyAccountView accountView(ResultSet result) throws SQLException {
        return new FamilyAccountView(
                result.getObject("id", UUID.class),
                result.getObject("portal_account_id", UUID.class),
                result.getString("account_number"),
                result.getString("display_name"),
                result.getString("email"),
                result.getString("currency_code"),
                result.getString("status"));
    }

    private BigDecimal queryMoney(String sql, Object... parameters) {
        BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class, parameters);
        return money(value);
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal positiveMoney(BigDecimal value, String label) {
        BigDecimal normalized = money(value);
        if (normalized.signum() <= 0) {
            throw new BillingValidationException(label + " doit être strictement positif");
        }
        return normalized;
    }

    private static BigDecimal nonNegativeMoney(BigDecimal value, String label) {
        BigDecimal normalized = money(value);
        if (normalized.signum() < 0) {
            throw new BillingValidationException(label + " ne peut pas être négatif");
        }
        return normalized;
    }

    private static BigDecimal positiveDecimal(BigDecimal value, String label, int scale) {
        if (value == null || value.signum() <= 0) {
            throw new BillingValidationException(label + " doit être strictement positive");
        }
        return value.setScale(scale, RoundingMode.HALF_UP);
    }

    private static String enumValue(String value, Set<String> allowed, String label) {
        if (value == null || value.isBlank()) {
            throw new BillingValidationException(label + " requis");
        }
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new BillingValidationException(label + " invalide");
        }
        return normalized;
    }

    private static String requiredText(String value, int max, String label) {
        if (value == null || value.isBlank()) {
            throw new BillingValidationException(label + " est requis");
        }
        String normalized = value.strip();
        if (normalized.length() > max) {
            throw new BillingValidationException(label + " dépasse " + max + " caractères");
        }
        return normalized;
    }

    private static String optionalText(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.strip();
        if (normalized.length() > max) {
            throw new BillingValidationException("Le texte dépasse " + max + " caractères");
        }
        return normalized;
    }

    private static String text(String preferred, String fallback, String label) {
        return preferred == null || preferred.isBlank()
                ? requiredText(fallback, 180, label)
                : requiredText(preferred, 180, label);
    }

    private static String names(String firstName, String lastName) {
        if (firstName == null && lastName == null) return null;
        return ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName))
                .strip();
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static <T> BigDecimal sum(
            List<T> values, java.util.function.Function<T, BigDecimal> extractor) {
        return values.stream().map(extractor).reduce(ZERO, BigDecimal::add);
    }

    private record PortalAccount(UUID id, String displayName, String email) {}

    private record PreparedLine(
            int position,
            UUID studentId,
            UUID discountId,
            String serviceType,
            String label,
            String description,
            BigDecimal quantity,
            BigDecimal unitAmount,
            BigDecimal grossAmount,
            BigDecimal discountAmount,
            BigDecimal totalAmount) {}

    private record PreparedAllocation(UUID invoiceId, BigDecimal amount) {}

    private record PreparedScheduleAllocation(UUID scheduleId, BigDecimal amount) {}

    private record ScheduleBalance(
            UUID familyAccountId, BigDecimal amount, BigDecimal paidAmount) {}

    private record DiscountRule(String type, BigDecimal value) {}

    private record RawLedgerEntry(
            UUID id,
            String type,
            String reference,
            String label,
            BigDecimal amount,
            Instant occurredAt,
            String status) {}

    public record AccountCommand(UUID portalAccountId, String displayName) {}

    public record InvoiceLineCommand(
            UUID studentId,
            UUID discountId,
            String serviceType,
            String label,
            String description,
            BigDecimal quantity,
            BigDecimal unitAmount,
            BigDecimal discountAmount) {}

    public record InvoiceCommand(
            LocalDate issueDate, LocalDate dueDate, List<InvoiceLineCommand> lines) {}

    public record AllocationCommand(UUID invoiceId, BigDecimal amount) {}

    public record ScheduleAllocationCommand(UUID paymentScheduleId, BigDecimal amount) {}

    public record PaymentCommand(
            BigDecimal amount,
            String paymentMethod,
            String externalReference,
            Instant receivedAt,
            List<AllocationCommand> allocations,
            List<ScheduleAllocationCommand> scheduleAllocations) {}

    public record ReversalCommand(String reason) {}

    public record CreditCommand(BigDecimal amount, String reason) {}

    public record RefundCommand(
            UUID creditId, BigDecimal amount, String refundMethod, String reason) {}

    public record FinancialAdjustmentCommand(
            UUID invoiceId, String direction, BigDecimal amount, String reason) {}

    public record FinanceDashboard(
            Instant generatedAt,
            String currencyCode,
            int familyAccountCount,
            BigDecimal totalBilled,
            BigDecimal totalPaid,
            BigDecimal totalOutstanding,
            BigDecimal totalOverdue,
            List<AccountSummary> accounts) {}

    public record FamilyAccountView(
            UUID id,
            UUID portalAccountId,
            String accountNumber,
            String displayName,
            String email,
            String currencyCode,
            String status) {}

    public record AccountSummary(
            FamilyAccountView account,
            BigDecimal totalBilled,
            BigDecimal totalPaid,
            BigDecimal totalCredits,
            BigDecimal totalRefunded,
            BigDecimal totalAdjustments,
            BigDecimal balance,
            BigDecimal overdue) {}

    public record FamilyLedger(
            Instant generatedAt,
            FamilyAccountView account,
            AccountSummary summary,
            List<InvoiceView> invoices,
            List<PaymentView> payments,
            List<CreditView> credits,
            List<RefundView> refunds,
            List<AdjustmentView> adjustments,
            List<ReceiptView> receipts,
            List<LedgerEntry> entries) {}

    public record InvoiceLineView(
            UUID id,
            UUID studentId,
            String studentName,
            String serviceType,
            String label,
            String description,
            BigDecimal quantity,
            BigDecimal unitAmount,
            BigDecimal grossAmount,
            BigDecimal discountAmount,
            BigDecimal lineTotal,
            int position) {}

    public record InvoiceView(
            UUID id,
            UUID familyAccountId,
            String invoiceNumber,
            LocalDate issueDate,
            LocalDate dueDate,
            String currencyCode,
            String status,
            BigDecimal subtotalAmount,
            BigDecimal discountAmount,
            BigDecimal totalAmount,
            BigDecimal paidAmount,
            BigDecimal balanceAmount,
            Instant issuedAt,
            List<InvoiceLineView> lines) {}

    public record PaymentAllocationView(UUID invoiceId, String invoiceNumber, BigDecimal amount) {}

    public record SchedulePaymentAllocationView(
            UUID paymentScheduleId, String planNumber, LocalDate dueDate, BigDecimal amount) {}

    public record PaymentView(
            UUID id,
            UUID familyAccountId,
            String paymentNumber,
            String paymentType,
            UUID reversalOfPaymentId,
            String paymentMethod,
            BigDecimal amount,
            String currencyCode,
            String externalReference,
            String status,
            Instant receivedAt,
            String reversalReason,
            UUID receiptId,
            String receiptNumber,
            String receiptStatus,
            List<PaymentAllocationView> allocations,
            List<SchedulePaymentAllocationView> scheduleAllocations) {}

    public record CreditView(
            UUID id,
            UUID familyAccountId,
            String creditNumber,
            BigDecimal amount,
            BigDecimal availableAmount,
            String currencyCode,
            String reason,
            String status,
            Instant issuedAt) {}

    public record RefundView(
            UUID id,
            UUID familyAccountId,
            UUID creditId,
            String refundNumber,
            BigDecimal amount,
            String currencyCode,
            String refundMethod,
            String reason,
            String status,
            Instant refundedAt) {}

    public record AdjustmentView(
            UUID id,
            UUID familyAccountId,
            UUID invoiceId,
            String adjustmentNumber,
            String direction,
            BigDecimal amount,
            String currencyCode,
            String reason,
            String status,
            Instant postedAt) {}

    public record ReceiptView(
            UUID id,
            UUID familyAccountId,
            UUID paymentId,
            String paymentNumber,
            String paymentMethod,
            String receiptNumber,
            String receiptType,
            BigDecimal amount,
            String currencyCode,
            Instant issuedAt,
            String status) {}

    public record LedgerEntry(
            UUID id,
            String type,
            String reference,
            String label,
            BigDecimal amount,
            Instant occurredAt,
            String status,
            BigDecimal balanceAfter) {}
}
