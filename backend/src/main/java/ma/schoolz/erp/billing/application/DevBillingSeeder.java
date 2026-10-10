package ma.schoolz.erp.billing.application;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ma.schoolz.erp.identity.IdentityAuditTrail;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@Order(130)
public class DevBillingSeeder implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final IdentityAuditTrail audit;
    private final Clock clock;
    private final boolean enabled;
    private final String tenantSlug;
    private final String parentEmail;

    public DevBillingSeeder(
            JdbcTemplate jdbc,
            IdentityAuditTrail audit,
            Clock clock,
            @Value("${schoolz.demo.seed-enabled:false}") boolean enabled,
            @Value("${schoolz.demo.tenant-slug:}") String tenantSlug,
            @Value("${schoolz.demo.parent-email:parent.demo@schoolz.ma}") String parentEmail) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
        this.enabled = enabled;
        this.tenantSlug = tenantSlug;
        this.parentEmail = parentEmail;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (!enabled || tenantSlug.isBlank() || parentEmail.isBlank()) return;
        List<UUID> tenants =
                jdbc.query(
                        "SELECT id FROM identity_tenant WHERE slug=? AND active=TRUE",
                        (result, row) -> result.getObject(1, UUID.class),
                        tenantSlug);
        if (tenants.isEmpty()) return;
        UUID tenantId = tenants.getFirst();
        List<PortalAccount> parents =
                jdbc.query(
                        "SELECT id,display_name FROM admission_portal_account WHERE tenant_id=?"
                                + " AND normalized_email=? AND status='ACTIVE'",
                        (result, row) ->
                                new PortalAccount(
                                        result.getObject("id", UUID.class),
                                        result.getString("display_name")),
                        tenantId,
                        parentEmail.strip().toLowerCase(java.util.Locale.ROOT));
        if (parents.isEmpty()) return;
        PortalAccount parent = parents.getFirst();
        List<UUID> existingAccounts =
                jdbc.query(
                        "SELECT id FROM billing_family_account WHERE tenant_id=?"
                                + " AND portal_account_id=?",
                        (result, row) -> result.getObject(1, UUID.class),
                        tenantId,
                        parent.id());
        if (!existingAccounts.isEmpty()) {
            UUID existingActor = firstActiveActor(tenantId);
            UUID existingPayment =
                    jdbc
                            .query(
                                    "SELECT id FROM billing_payment WHERE tenant_id=?"
                                            + " AND family_account_id=? AND payment_type='PAYMENT'"
                                            + " ORDER BY received_at LIMIT 1",
                                    (result, row) -> result.getObject(1, UUID.class),
                                    tenantId,
                                    existingAccounts.getFirst())
                            .stream()
                            .findFirst()
                            .orElse(null);
            seedCollections(
                    tenantId,
                    existingAccounts.getFirst(),
                    existingPayment,
                    existingActor,
                    clock.instant());
            return;
        }
        List<UUID> students =
                jdbc.query(
                        """
                        SELECT link.student_id
                        FROM enrollment_portal_student_link link
                        LEFT JOIN student_enrollment e
                          ON e.student_id=link.student_id AND e.tenant_id=link.tenant_id
                          AND e.status='ACTIVE'
                        WHERE link.tenant_id=? AND link.portal_account_id=?
                        ORDER BY CASE WHEN e.id IS NULL THEN 1 ELSE 0 END
                        LIMIT 1
                        """,
                        (result, row) -> result.getObject(1, UUID.class),
                        tenantId,
                        parent.id());
        if (students.isEmpty()) return;

        UUID actorId = firstActiveActor(tenantId);
        Instant now = clock.instant();
        UUID accountId = UUID.randomUUID();
        UUID studentId = students.getFirst();
        UUID discountId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID receiptId = UUID.randomUUID();
        LocalDate issueDate = LocalDate.of(2026, 8, 1);
        LocalDate dueDate = LocalDate.of(2026, 9, 5);

        jdbc.update(
                "INSERT INTO billing_family_account"
                    + " (id,tenant_id,portal_account_id,account_number,display_name,currency_code,"
                    + " status,created_at,updated_at,version) VALUES (?, ?, ?, 'FAM-DEMO-0001', ?,"
                    + " 'MAD', 'ACTIVE', ?, ?, 0)",
                accountId,
                tenantId,
                parent.id(),
                parent.displayName(),
                timestamp(now),
                timestamp(now));
        jdbc.update(
                "INSERT INTO billing_discount"
                    + " (id,tenant_id,family_account_id,student_id,code,label,discount_type,discount_value,"
                    + " service_type,starts_on,ends_on,active,created_at,updated_at) VALUES (?, ?,"
                    + " ?, ?, 'FRATRIE', 'Remise fratrie', 'FIXED', 2000, 'TUITION', '2026-08-01',"
                    + " '2027-07-31', TRUE, ?, ?)",
                discountId,
                tenantId,
                accountId,
                studentId,
                timestamp(now),
                timestamp(now));
        jdbc.update(
                "INSERT INTO billing_invoice"
                    + " (id,tenant_id,family_account_id,invoice_number,issue_date,due_date,"
                    + " currency_code,status,subtotal_amount,discount_amount,total_amount,paid_amount,"
                    + " balance_amount,issued_at,created_at,updated_at,version) VALUES (?, ?, ?,"
                    + " 'FAC-DEMO-2026-0001', ?, ?, 'MAD', 'PARTIALLY_PAID',"
                    + " 41500,2000,39500,38000,1500, ?, ?, ?, 1)",
                invoiceId,
                tenantId,
                accountId,
                issueDate,
                dueDate,
                timestamp(now.minusSeconds(3600)),
                timestamp(now.minusSeconds(3600)),
                timestamp(now));
        addLine(
                tenantId,
                accountId,
                invoiceId,
                studentId,
                discountId,
                issueDate,
                1,
                "TUITION",
                "Scolarité annuelle 2026-2027",
                new BigDecimal("32000.00"),
                new BigDecimal("2000.00"),
                now);
        addLine(
                tenantId,
                accountId,
                invoiceId,
                studentId,
                null,
                issueDate,
                2,
                "TRANSPORT",
                "Transport scolaire annuel",
                new BigDecimal("6000.00"),
                BigDecimal.ZERO,
                now);
        addLine(
                tenantId,
                accountId,
                invoiceId,
                studentId,
                null,
                issueDate,
                3,
                "CAFETERIA",
                "Cantine scolaire annuelle",
                new BigDecimal("3500.00"),
                BigDecimal.ZERO,
                now);
        jdbc.update(
                "INSERT INTO billing_payment"
                    + " (id,tenant_id,family_account_id,payment_number,payment_type,"
                    + " reversal_of_payment_id,payment_method,amount,currency_code,external_reference,"
                    + " status,received_at,validated_at,reversal_reason,created_by,created_at)"
                    + " VALUES (?, ?, ?, 'PAY-DEMO-2026-0001', 'PAYMENT', NULL, 'BANK_TRANSFER',"
                    + " 38000, 'MAD', 'VIR-DEMO-2026-0001', 'VALIDATED', ?, ?, NULL, ?, ?)",
                paymentId,
                tenantId,
                accountId,
                timestamp(now.minusSeconds(600)),
                timestamp(now.minusSeconds(600)),
                actorId,
                timestamp(now.minusSeconds(600)));
        jdbc.update(
                "INSERT INTO billing_payment_allocation"
                        + " (id,tenant_id,payment_id,invoice_id,amount,allocated_at)"
                        + " VALUES (?, ?, ?, ?, 38000, ?)",
                UUID.randomUUID(),
                tenantId,
                paymentId,
                invoiceId,
                timestamp(now.minusSeconds(600)));
        jdbc.update(
                "INSERT INTO billing_receipt"
                        + " (id,tenant_id,family_account_id,payment_id,receipt_number,receipt_type,"
                        + " amount,currency_code,issued_at,status) VALUES (?, ?, ?, ?,"
                        + " 'REC-DEMO-2026-0001', 'RECEIPT', 38000, 'MAD', ?, 'VALID')",
                receiptId,
                tenantId,
                accountId,
                paymentId,
                timestamp(now.minusSeconds(600)));
        seedCollections(tenantId, accountId, paymentId, actorId, now);
        audit.record(
                tenantId,
                actorId,
                "billing.demo.seeded",
                "FamilyAccount",
                accountId.toString(),
                "local-seed",
                Map.of(
                        "accountNumber",
                        "FAM-DEMO-0001",
                        "invoiceNumber",
                        "FAC-DEMO-2026-0001",
                        "receiptNumber",
                        "REC-DEMO-2026-0001"),
                null,
                Map.of("total", 39500, "paid", 38000, "balance", 1500));
    }

    private void seedCollections(
            UUID tenantId, UUID accountId, UUID paymentId, UUID actorId, Instant now) {
        if (jdbc.queryForObject(
                        "SELECT count(*) FROM billing_installment_plan WHERE tenant_id=?"
                                + " AND family_account_id=?",
                        Integer.class,
                        tenantId,
                        accountId)
                > 0) {
            return;
        }
        UUID planId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO billing_installment_plan"
                    + " (id,tenant_id,family_account_id,plan_number,name,frequency,total_amount,"
                    + " currency_code,starts_on,status,created_at,updated_at) VALUES (?, ?, ?,"
                    + " 'ECH-DEMO-2026-0001', 'Échéancier scolarité', 'MONTHLY', 12000, 'MAD',"
                    + " '2026-05-05', 'ACTIVE', ?, ?)",
                planId,
                tenantId,
                accountId,
                timestamp(now),
                timestamp(now));
        UUID first = addSchedule(tenantId, planId, 1, LocalDate.of(2026, 5, 5), true, now);
        UUID second = addSchedule(tenantId, planId, 2, LocalDate.of(2026, 6, 5), true, now);
        UUID third = addSchedule(tenantId, planId, 3, LocalDate.of(2026, 7, 5), false, now);
        if (paymentId != null) {
            addScheduleAllocation(tenantId, paymentId, first, now);
            addScheduleAllocation(tenantId, paymentId, second, now);
        }
        UUID pushRule =
                addReminderRule(
                        tenantId,
                        "J+1 · notification",
                        1,
                        "PUSH",
                        true,
                        "finance-due-j-plus-1",
                        now);
        addReminderRule(
                tenantId, "J-3 · courriel", -3, "EMAIL", true, "finance-due-j-minus-3", now);
        addReminderRule(tenantId, "J+7 · SMS", 7, "SMS", false, "finance-due-j-plus-7", now);
        addReminderRule(
                tenantId, "J+15 · WhatsApp", 15, "WHATSAPP", false, "finance-due-j-plus-15", now);
        jdbc.update(
                "INSERT INTO billing_collection_action"
                    + " (id,tenant_id,family_account_id,payment_schedule_id,reminder_rule_id,"
                    + " action_type,channel,status,adapter_key,message,planned_for,executed_at,"
                    + " idempotency_key,created_by,created_at) VALUES (?, ?, ?, ?, ?,"
                    + " 'AUTOMATIC','PUSH','PREVIEW','preview:push',?, '2026-07-06', ?, ?, ?, ?)",
                UUID.randomUUID(),
                tenantId,
                accountId,
                third,
                pushRule,
                "Échéance ECH-DEMO-2026-0001 du 2026-07-05 : 4000.00 MAD restant.",
                timestamp(now.minusSeconds(300)),
                "demo:auto:j-plus-1:" + third,
                actorId,
                timestamp(now.minusSeconds(300)));
    }

    private UUID addSchedule(
            UUID tenantId,
            UUID planId,
            int position,
            LocalDate dueDate,
            boolean paid,
            Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO billing_payment_schedule"
                        + " (id,tenant_id,installment_plan_id,invoice_id,due_date,amount,status,"
                        + " paid_at,created_at,schedule_position,paid_amount,updated_at)"
                        + " VALUES (?, ?, ?, NULL, ?, 4000, ?, ?, ?, ?, ?, ?)",
                id,
                tenantId,
                planId,
                dueDate,
                paid ? "PAID" : "SCHEDULED",
                paid ? timestamp(now.minusSeconds(600)) : null,
                timestamp(now),
                position,
                paid ? new BigDecimal("4000.00") : BigDecimal.ZERO,
                timestamp(now));
        return id;
    }

    private void addScheduleAllocation(
            UUID tenantId, UUID paymentId, UUID scheduleId, Instant now) {
        jdbc.update(
                "INSERT INTO billing_schedule_payment_allocation"
                        + " (id,tenant_id,payment_id,payment_schedule_id,amount,allocated_at)"
                        + " VALUES (?, ?, ?, ?, 4000, ?)",
                UUID.randomUUID(),
                tenantId,
                paymentId,
                scheduleId,
                timestamp(now.minusSeconds(600)));
    }

    private UUID addReminderRule(
            UUID tenantId,
            String name,
            int offset,
            String channel,
            boolean automatic,
            String template,
            Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO billing_reminder_rule"
                        + " (id,tenant_id,name,offset_days,channel,automatic,active,template_key,"
                        + " created_at,updated_at) VALUES (?, ?, ?, ?, ?, ?, TRUE, ?, ?, ?)",
                id,
                tenantId,
                name,
                offset,
                channel,
                automatic,
                template,
                timestamp(now),
                timestamp(now));
        return id;
    }

    private UUID firstActiveActor(UUID tenantId) {
        return jdbc
                .query(
                        "SELECT id FROM identity_user_account WHERE tenant_id=?"
                                + " AND status='ACTIVE' ORDER BY created_at LIMIT 1",
                        (result, row) -> result.getObject(1, UUID.class),
                        tenantId)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private void addLine(
            UUID tenantId,
            UUID accountId,
            UUID invoiceId,
            UUID studentId,
            UUID discountId,
            LocalDate issueDate,
            int position,
            String serviceType,
            String label,
            BigDecimal gross,
            BigDecimal discount,
            Instant now) {
        UUID chargeId = UUID.randomUUID();
        BigDecimal total = gross.subtract(discount);
        jdbc.update(
                "INSERT INTO billing_charge"
                    + " (id,tenant_id,family_account_id,student_id,discount_id,service_type,label,"
                    + " description,quantity,unit_amount,gross_amount,discount_amount,net_amount,"
                    + " currency_code,charged_on,status,source_reference,created_at,updated_at,version)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, NULL, 1, ?, ?, ?, ?, 'MAD', ?, 'INVOICED',"
                    + " 'FAC-DEMO-2026-0001', ?, ?, 0)",
                chargeId,
                tenantId,
                accountId,
                studentId,
                discountId,
                serviceType,
                label,
                gross,
                gross,
                discount,
                total,
                issueDate,
                timestamp(now),
                timestamp(now));
        jdbc.update(
                "INSERT INTO billing_invoice_line"
                    + " (id,tenant_id,invoice_id,charge_id,student_id,service_type,label,description,"
                    + " quantity,unit_amount,gross_amount,discount_amount,line_total,line_position,created_at)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, NULL, 1, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                tenantId,
                invoiceId,
                chargeId,
                studentId,
                serviceType,
                label,
                gross,
                gross,
                discount,
                total,
                position,
                timestamp(now));
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    private record PortalAccount(UUID id, String displayName) {}
}
