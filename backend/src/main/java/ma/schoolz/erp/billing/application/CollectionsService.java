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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.admissions.AdmissionPortalAccess.PortalIdentity;
import ma.schoolz.erp.billing.application.ReminderChannelAdapter.ReminderMessage;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.identity.IdentityAuditTrail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CollectionsService {
    private static final ZoneId SCHOOL_ZONE = ZoneId.of("Africa/Casablanca");
    private static final String CURRENCY = "MAD";
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final Set<String> FREQUENCIES =
            Set.of("MONTHLY", "QUARTERLY", "ANNUAL", "CUSTOM");
    private static final Set<String> CHANNELS = Set.of("PUSH", "EMAIL", "SMS", "WHATSAPP");

    private final JdbcTemplate jdbc;
    private final IdentityAuditTrail audit;
    private final Clock clock;
    private final List<ReminderChannelAdapter> adapters;

    public CollectionsService(
            JdbcTemplate jdbc,
            IdentityAuditTrail audit,
            Clock clock,
            List<ReminderChannelAdapter> adapters) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
        this.adapters = List.copyOf(adapters);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('collections.read')")
    public CollectionsDashboard dashboard(SchoolPrincipal actor, LocalDate requestedAsOf) {
        LocalDate asOf = asOf(requestedAsOf);
        List<InstallmentPlanView> plans = plans(actor.tenantId(), null, asOf);
        BigDecimal expected =
                queryMoney(
                        """
                        SELECT COALESCE(SUM(s.amount),0)
                        FROM billing_payment_schedule s
                        JOIN billing_installment_plan p
                          ON p.id=s.installment_plan_id AND p.tenant_id=s.tenant_id
                        WHERE s.tenant_id=? AND p.status='ACTIVE' AND s.due_date<=?
                        """,
                        actor.tenantId(),
                        asOf);
        BigDecimal received =
                queryMoney(
                        """
                        SELECT COALESCE(SUM(CASE WHEN s.paid_amount<s.amount
                                                THEN s.paid_amount ELSE s.amount END),0)
                        FROM billing_payment_schedule s
                        JOIN billing_installment_plan p
                          ON p.id=s.installment_plan_id AND p.tenant_id=s.tenant_id
                        WHERE s.tenant_id=? AND p.status='ACTIVE' AND s.due_date<=?
                        """,
                        actor.tenantId(),
                        asOf);
        BigDecimal unpaid = overdueAmount(actor.tenantId(), asOf, asOf);
        BigDecimal over7 = overdueAmount(actor.tenantId(), asOf, asOf.minusDays(7));
        BigDecimal over30 = overdueAmount(actor.tenantId(), asOf, asOf.minusDays(30));
        BigDecimal over60 = overdueAmount(actor.tenantId(), asOf, asOf.minusDays(60));
        int unpaidInstallments =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM billing_payment_schedule s
                        JOIN billing_installment_plan p
                          ON p.id=s.installment_plan_id AND p.tenant_id=s.tenant_id
                        WHERE s.tenant_id=? AND p.status='ACTIVE' AND s.due_date<?
                          AND s.paid_amount<s.amount
                        """,
                        Integer.class,
                        actor.tenantId(),
                        asOf);
        BigDecimal rate =
                expected.signum() == 0
                        ? ZERO
                        : received.multiply(new BigDecimal("100"))
                                .divide(expected, 2, RoundingMode.HALF_UP);
        return new CollectionsDashboard(
                clock.instant(),
                asOf,
                CURRENCY,
                expected,
                received,
                rate,
                unpaid,
                over7,
                over30,
                over60,
                unpaidInstallments,
                plans,
                reminderRules(actor.tenantId()),
                actions(actor.tenantId(), 50));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('collections.read')")
    public List<InstallmentPlanView> accountPlans(
            SchoolPrincipal actor, UUID accountId, LocalDate requestedAsOf) {
        requireAccount(actor.tenantId(), accountId);
        return plans(actor.tenantId(), accountId, asOf(requestedAsOf));
    }

    @Transactional(readOnly = true)
    public List<InstallmentPlanView> familyPlans(PortalIdentity parent, LocalDate requestedAsOf) {
        UUID accountId =
                jdbc
                        .query(
                                "SELECT id FROM billing_family_account WHERE tenant_id=?"
                                        + " AND portal_account_id=? AND status='ACTIVE'",
                                (result, row) -> result.getObject(1, UUID.class),
                                parent.tenantId(),
                                parent.accountId())
                        .stream()
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new BillingNotFoundException(
                                                "Aucun compte financier actif pour cette famille"));
        return plans(parent.tenantId(), accountId, asOf(requestedAsOf));
    }

    @Transactional
    @PreAuthorize("hasAuthority('collections.manage')")
    public InstallmentPlanView createPlan(
            SchoolPrincipal actor,
            UUID accountId,
            InstallmentPlanCommand command,
            String ipAddress) {
        requireAccount(actor.tenantId(), accountId);
        if (command == null) {
            throw new BillingValidationException("L'échéancier est requis");
        }
        String name = requiredText(command.name(), 180, "Le nom");
        String frequency = enumValue(command.frequency(), FREQUENCIES, "La fréquence");
        BigDecimal total = positiveMoney(command.totalAmount(), "Le montant total");
        LocalDate startsOn = command.startsOn();
        if (startsOn == null) {
            throw new BillingValidationException("La date de départ est requise");
        }
        if (command.invoiceId() != null) {
            int invoiceCount =
                    jdbc.queryForObject(
                            "SELECT count(*) FROM billing_invoice WHERE tenant_id=? AND id=?"
                                    + " AND family_account_id=? AND status<>'DRAFT'",
                            Integer.class,
                            actor.tenantId(),
                            command.invoiceId(),
                            accountId);
            if (invoiceCount == 0) {
                throw new BillingNotFoundException("Facture émise du compte introuvable");
            }
        }
        List<PreparedSchedule> schedules = prepareSchedules(command, frequency, total, startsOn);
        Instant now = clock.instant();
        UUID planId = UUID.randomUUID();
        String planNumber = nextNumber(actor.tenantId(), "INSTALLMENT_PLAN", "ECH", now);
        jdbc.update(
                """
                INSERT INTO billing_installment_plan
                  (id,tenant_id,family_account_id,plan_number,name,frequency,total_amount,
                   currency_code,starts_on,status,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'MAD', ?, 'ACTIVE', ?, ?)
                """,
                planId,
                actor.tenantId(),
                accountId,
                planNumber,
                name,
                frequency,
                total,
                startsOn,
                timestamp(now),
                timestamp(now));
        for (PreparedSchedule schedule : schedules) {
            jdbc.update(
                    """
                    INSERT INTO billing_payment_schedule
                      (id,tenant_id,installment_plan_id,invoice_id,due_date,amount,status,paid_at,
                       created_at,schedule_position,paid_amount,updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, 'SCHEDULED', NULL, ?, ?, 0, ?)
                    """,
                    UUID.randomUUID(),
                    actor.tenantId(),
                    planId,
                    command.invoiceId(),
                    schedule.dueDate(),
                    schedule.amount(),
                    timestamp(now),
                    schedule.position(),
                    timestamp(now));
        }
        InstallmentPlanView created = plan(actor.tenantId(), planId, asOf(null));
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "billing.installment-plan.created",
                "InstallmentPlan",
                planId.toString(),
                ipAddress,
                Map.of(
                        "planNumber", planNumber,
                        "frequency", frequency,
                        "installmentCount", schedules.size()),
                null,
                created);
        return created;
    }

    @Transactional
    @PreAuthorize("hasAuthority('collections.manage')")
    public ReminderRuleView createReminderRule(
            SchoolPrincipal actor, ReminderRuleCommand command, String ipAddress) {
        PreparedRule rule = prepareRule(command);
        Instant now = clock.instant();
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO billing_reminder_rule
                  (id,tenant_id,name,offset_days,channel,automatic,active,template_key,
                   created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                actor.tenantId(),
                rule.name(),
                rule.offsetDays(),
                rule.channel(),
                rule.automatic(),
                rule.active(),
                rule.templateKey(),
                timestamp(now),
                timestamp(now));
        ReminderRuleView created = reminderRule(actor.tenantId(), id);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "billing.reminder-rule.created",
                "ReminderRule",
                id.toString(),
                ipAddress,
                Map.of("channel", rule.channel(), "offsetDays", rule.offsetDays()),
                null,
                created);
        return created;
    }

    @Transactional
    @PreAuthorize("hasAuthority('collections.manage')")
    public ReminderRuleView updateReminderRule(
            SchoolPrincipal actor, UUID ruleId, ReminderRuleCommand command, String ipAddress) {
        ReminderRuleView before = reminderRule(actor.tenantId(), ruleId);
        PreparedRule rule = prepareRule(command);
        jdbc.update(
                """
                UPDATE billing_reminder_rule
                SET name=?,offset_days=?,channel=?,automatic=?,active=?,template_key=?,updated_at=?
                WHERE tenant_id=? AND id=?
                """,
                rule.name(),
                rule.offsetDays(),
                rule.channel(),
                rule.automatic(),
                rule.active(),
                rule.templateKey(),
                timestamp(clock.instant()),
                actor.tenantId(),
                ruleId);
        ReminderRuleView updated = reminderRule(actor.tenantId(), ruleId);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "billing.reminder-rule.updated",
                "ReminderRule",
                ruleId.toString(),
                ipAddress,
                Map.of("channel", rule.channel(), "offsetDays", rule.offsetDays()),
                before,
                updated);
        return updated;
    }

    @Transactional
    @PreAuthorize("hasAuthority('collections.manage')")
    public CollectionActionView manualReminder(
            SchoolPrincipal actor,
            UUID scheduleId,
            ManualReminderCommand command,
            String ipAddress) {
        ScheduleContext schedule = scheduleContext(actor.tenantId(), scheduleId);
        if (schedule.remaining().signum() == 0) {
            throw new BillingConflictException("Cette échéance est déjà réglée");
        }
        String channel =
                enumValue(command == null ? null : command.channel(), CHANNELS, "Le canal");
        String message =
                command == null || command.message() == null || command.message().isBlank()
                        ? defaultMessage(schedule)
                        : requiredText(command.message(), 1000, "Le message");
        String key = "manual:" + scheduleId + ":" + UUID.randomUUID();
        CollectionActionView action =
                dispatch(
                        actor.tenantId(),
                        schedule,
                        null,
                        "MANUAL",
                        channel,
                        message,
                        LocalDate.now(clock.withZone(SCHOOL_ZONE)),
                        key,
                        actor.accountId());
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "billing.collection.manual-reminder",
                "PaymentSchedule",
                scheduleId.toString(),
                ipAddress,
                Map.of("channel", channel, "status", action.status()),
                null,
                action);
        return action;
    }

    @Transactional
    @PreAuthorize("hasAuthority('collections.manage')")
    public AutomaticRunResult runAutomaticReminders(
            SchoolPrincipal actor, LocalDate requestedAsOf, String ipAddress) {
        LocalDate asOf = asOf(requestedAsOf);
        int created = 0;
        int skipped = 0;
        for (ReminderRuleView rule :
                reminderRules(actor.tenantId()).stream()
                        .filter(candidate -> candidate.active() && candidate.automatic())
                        .toList()) {
            List<ScheduleContext> due =
                    jdbc.query(
                            """
                            SELECT s.id,p.family_account_id,p.plan_number,s.due_date,s.amount,
                                   s.paid_amount,fa.email
                            FROM billing_payment_schedule s
                            JOIN billing_installment_plan p
                              ON p.id=s.installment_plan_id AND p.tenant_id=s.tenant_id
                            JOIN (
                                SELECT fa.id,fa.tenant_id,pa.email
                                FROM billing_family_account fa
                                JOIN admission_portal_account pa
                                  ON pa.id=fa.portal_account_id AND pa.tenant_id=fa.tenant_id
                            ) fa ON fa.id=p.family_account_id AND fa.tenant_id=p.tenant_id
                            WHERE s.tenant_id=? AND p.status='ACTIVE' AND s.paid_amount<s.amount
                              AND s.due_date<=?
                            """,
                            (result, row) -> scheduleContext(result),
                            actor.tenantId(),
                            asOf.minusDays(rule.offsetDays()));
            for (ScheduleContext schedule : due) {
                LocalDate triggerDate = schedule.dueDate().plusDays(rule.offsetDays());
                String key = "auto:" + rule.id() + ":" + schedule.id() + ":" + triggerDate;
                if (actionExists(actor.tenantId(), key)) {
                    skipped++;
                    continue;
                }
                dispatch(
                        actor.tenantId(),
                        schedule,
                        rule.id(),
                        "AUTOMATIC",
                        rule.channel(),
                        defaultMessage(schedule),
                        triggerDate,
                        key,
                        actor.accountId());
                created++;
            }
        }
        AutomaticRunResult result = new AutomaticRunResult(asOf, created, skipped);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "billing.collection.automatic-run",
                "CollectionRun",
                asOf.toString(),
                ipAddress,
                Map.of("created", created, "skipped", skipped),
                null,
                result);
        return result;
    }

    private List<PreparedSchedule> prepareSchedules(
            InstallmentPlanCommand command,
            String frequency,
            BigDecimal total,
            LocalDate startsOn) {
        if ("CUSTOM".equals(frequency)) {
            List<CustomInstallmentCommand> requested =
                    command.installments() == null ? List.of() : command.installments();
            if (requested.isEmpty()) {
                throw new BillingValidationException(
                        "Un échéancier personnalisé exige au moins une échéance");
            }
            List<PreparedSchedule> result = new ArrayList<>();
            Set<LocalDate> dates = new HashSet<>();
            BigDecimal sum = ZERO;
            int position = 1;
            for (CustomInstallmentCommand installment : requested) {
                if (installment == null || installment.dueDate() == null) {
                    throw new BillingValidationException("Chaque date d'échéance est requise");
                }
                if (!dates.add(installment.dueDate())) {
                    throw new BillingValidationException(
                            "Deux échéances ne peuvent partager une date");
                }
                BigDecimal amount = positiveMoney(installment.amount(), "Le montant de l'échéance");
                result.add(new PreparedSchedule(position++, installment.dueDate(), amount));
                sum = sum.add(amount);
            }
            if (money(sum).compareTo(total) != 0) {
                throw new BillingValidationException(
                        "La somme des échéances doit correspondre au montant total");
            }
            return result.stream()
                    .sorted((left, right) -> left.dueDate().compareTo(right.dueDate()))
                    .toList();
        }
        int defaultCount =
                switch (frequency) {
                    case "MONTHLY" -> 10;
                    case "QUARTERLY" -> 4;
                    case "ANNUAL" -> 1;
                    default -> throw new BillingValidationException("Fréquence inconnue");
                };
        int count = command.installmentCount() == null ? defaultCount : command.installmentCount();
        if (count < 1 || count > 60) {
            throw new BillingValidationException(
                    "Le nombre d'échéances doit être compris entre 1 et 60");
        }
        int months = "MONTHLY".equals(frequency) ? 1 : "QUARTERLY".equals(frequency) ? 3 : 12;
        BigDecimal base = total.divide(BigDecimal.valueOf(count), 2, RoundingMode.DOWN);
        List<PreparedSchedule> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            BigDecimal amount =
                    index == count - 1
                            ? money(total.subtract(base.multiply(BigDecimal.valueOf(count - 1L))))
                            : base;
            result.add(
                    new PreparedSchedule(
                            index + 1, startsOn.plusMonths((long) index * months), amount));
        }
        return List.copyOf(result);
    }

    private List<InstallmentPlanView> plans(UUID tenantId, UUID accountId, LocalDate asOf) {
        String accountClause = accountId == null ? "" : " AND p.family_account_id=?";
        Object[] parameters =
                accountId == null ? new Object[] {tenantId} : new Object[] {tenantId, accountId};
        return jdbc
                .query(
                        "SELECT p.id FROM billing_installment_plan p WHERE p.tenant_id=?"
                                + accountClause
                                + " ORDER BY p.starts_on DESC,p.plan_number DESC",
                        (result, row) -> result.getObject(1, UUID.class),
                        parameters)
                .stream()
                .map(id -> plan(tenantId, id, asOf))
                .toList();
    }

    private InstallmentPlanView plan(UUID tenantId, UUID planId, LocalDate asOf) {
        return jdbc
                .query(
                        """
                        SELECT p.*,fa.account_number,fa.display_name
                        FROM billing_installment_plan p
                        JOIN billing_family_account fa
                          ON fa.id=p.family_account_id AND fa.tenant_id=p.tenant_id
                        WHERE p.tenant_id=? AND p.id=?
                        """,
                        (result, row) -> {
                            List<PaymentScheduleView> schedules = schedules(tenantId, planId, asOf);
                            BigDecimal paid =
                                    schedules.stream()
                                            .map(PaymentScheduleView::paidAmount)
                                            .reduce(ZERO, BigDecimal::add);
                            PaymentScheduleView next =
                                    schedules.stream()
                                            .filter(
                                                    schedule ->
                                                            schedule.remainingAmount().signum() > 0)
                                            .findFirst()
                                            .orElse(null);
                            return new InstallmentPlanView(
                                    result.getObject("id", UUID.class),
                                    result.getObject("family_account_id", UUID.class),
                                    result.getString("account_number"),
                                    result.getString("display_name"),
                                    result.getString("plan_number"),
                                    result.getString("name"),
                                    result.getString("frequency"),
                                    money(result.getBigDecimal("total_amount")),
                                    money(paid),
                                    money(
                                            result.getBigDecimal("total_amount")
                                                    .subtract(paid)
                                                    .max(ZERO)),
                                    result.getString("currency_code"),
                                    result.getObject("starts_on", LocalDate.class),
                                    result.getString("status"),
                                    next == null ? null : next.dueDate(),
                                    schedules);
                        },
                        tenantId,
                        planId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new BillingNotFoundException("Échéancier introuvable"));
    }

    private List<PaymentScheduleView> schedules(UUID tenantId, UUID planId, LocalDate asOf) {
        return jdbc.query(
                "SELECT * FROM billing_payment_schedule WHERE tenant_id=? AND installment_plan_id=?"
                        + " ORDER BY due_date,schedule_position",
                (result, row) -> scheduleView(result, asOf),
                tenantId,
                planId);
    }

    private PaymentScheduleView scheduleView(ResultSet result, LocalDate asOf) throws SQLException {
        BigDecimal amount = money(result.getBigDecimal("amount"));
        BigDecimal paid = money(result.getBigDecimal("paid_amount"));
        BigDecimal remaining = money(amount.subtract(paid).max(ZERO));
        LocalDate dueDate = result.getObject("due_date", LocalDate.class);
        long daysOverdue =
                dueDate.isBefore(asOf)
                        ? java.time.temporal.ChronoUnit.DAYS.between(dueDate, asOf)
                        : 0;
        String status;
        if (remaining.signum() == 0) {
            status = "PAID";
        } else if (daysOverdue > 0 && paid.signum() > 0) {
            status = "PARTIALLY_PAID_OVERDUE";
        } else if (daysOverdue > 0) {
            status = "OVERDUE";
        } else if (paid.signum() > 0) {
            status = "PARTIALLY_PAID";
        } else {
            status = "UPCOMING";
        }
        return new PaymentScheduleView(
                result.getObject("id", UUID.class),
                result.getObject("invoice_id", UUID.class),
                result.getInt("schedule_position"),
                dueDate,
                amount,
                paid,
                remaining,
                status,
                daysOverdue,
                instant(result, "paid_at"));
    }

    private List<ReminderRuleView> reminderRules(UUID tenantId) {
        return jdbc.query(
                "SELECT * FROM billing_reminder_rule WHERE tenant_id=? ORDER BY"
                        + " offset_days,channel",
                (result, row) -> reminderRuleView(result),
                tenantId);
    }

    private ReminderRuleView reminderRule(UUID tenantId, UUID ruleId) {
        return jdbc
                .query(
                        "SELECT * FROM billing_reminder_rule WHERE tenant_id=? AND id=?",
                        (result, row) -> reminderRuleView(result),
                        tenantId,
                        ruleId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new BillingNotFoundException("Règle de relance introuvable"));
    }

    private ReminderRuleView reminderRuleView(ResultSet result) throws SQLException {
        return new ReminderRuleView(
                result.getObject("id", UUID.class),
                result.getString("name"),
                result.getInt("offset_days"),
                result.getString("channel"),
                result.getBoolean("automatic"),
                result.getBoolean("active"),
                result.getString("template_key"));
    }

    private List<CollectionActionView> actions(UUID tenantId, int limit) {
        return jdbc.query(
                """
                SELECT a.*,fa.account_number,fa.display_name,p.plan_number,s.due_date
                FROM billing_collection_action a
                JOIN billing_family_account fa
                  ON fa.id=a.family_account_id AND fa.tenant_id=a.tenant_id
                LEFT JOIN billing_payment_schedule s
                  ON s.id=a.payment_schedule_id AND s.tenant_id=a.tenant_id
                LEFT JOIN billing_installment_plan p
                  ON p.id=s.installment_plan_id AND p.tenant_id=s.tenant_id
                WHERE a.tenant_id=? ORDER BY a.created_at DESC
                LIMIT ?
                """,
                (result, row) -> actionView(result),
                tenantId,
                limit);
    }

    private CollectionActionView action(UUID tenantId, UUID actionId) {
        return jdbc
                .query(
                        """
                        SELECT a.*,fa.account_number,fa.display_name,p.plan_number,s.due_date
                        FROM billing_collection_action a
                        JOIN billing_family_account fa
                          ON fa.id=a.family_account_id AND fa.tenant_id=a.tenant_id
                        LEFT JOIN billing_payment_schedule s
                          ON s.id=a.payment_schedule_id AND s.tenant_id=a.tenant_id
                        LEFT JOIN billing_installment_plan p
                          ON p.id=s.installment_plan_id AND p.tenant_id=s.tenant_id
                        WHERE a.tenant_id=? AND a.id=?
                        """,
                        (result, row) -> actionView(result),
                        tenantId,
                        actionId)
                .stream()
                .findFirst()
                .orElseThrow(
                        () -> new BillingNotFoundException("Action de recouvrement introuvable"));
    }

    private CollectionActionView actionView(ResultSet result) throws SQLException {
        return new CollectionActionView(
                result.getObject("id", UUID.class),
                result.getObject("family_account_id", UUID.class),
                result.getString("account_number"),
                result.getString("display_name"),
                result.getObject("payment_schedule_id", UUID.class),
                result.getString("plan_number"),
                result.getObject("due_date", LocalDate.class),
                result.getString("action_type"),
                result.getString("channel"),
                result.getString("status"),
                result.getString("adapter_key"),
                result.getString("message"),
                result.getObject("planned_for", LocalDate.class),
                instant(result, "executed_at"));
    }

    private CollectionActionView dispatch(
            UUID tenantId,
            ScheduleContext schedule,
            UUID ruleId,
            String type,
            String channel,
            String message,
            LocalDate plannedFor,
            String idempotencyKey,
            UUID actorId) {
        ReminderChannelAdapter adapter =
                adapters.stream()
                        .filter(candidate -> candidate.supports(channel))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new BillingConflictException(
                                                "Adaptateur de relance indisponible"));
        ReminderChannelAdapter.DispatchResult result =
                adapter.dispatch(
                        new ReminderMessage(
                                tenantId,
                                schedule.familyAccountId(),
                                schedule.email(),
                                channel,
                                message,
                                idempotencyKey));
        Instant now = clock.instant();
        UUID actionId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO billing_collection_action
                  (id,tenant_id,family_account_id,payment_schedule_id,reminder_rule_id,
                   action_type,channel,status,adapter_key,message,planned_for,executed_at,
                   idempotency_key,created_by,created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                actionId,
                tenantId,
                schedule.familyAccountId(),
                schedule.id(),
                ruleId,
                type,
                channel,
                result.status(),
                result.adapterKey(),
                message,
                plannedFor,
                timestamp(now),
                idempotencyKey,
                actorId,
                timestamp(now));
        return action(tenantId, actionId);
    }

    private ScheduleContext scheduleContext(UUID tenantId, UUID scheduleId) {
        return jdbc
                .query(
                        """
                        SELECT s.id,p.family_account_id,p.plan_number,s.due_date,s.amount,
                               s.paid_amount,pa.email
                        FROM billing_payment_schedule s
                        JOIN billing_installment_plan p
                          ON p.id=s.installment_plan_id AND p.tenant_id=s.tenant_id
                        JOIN billing_family_account fa
                          ON fa.id=p.family_account_id AND fa.tenant_id=p.tenant_id
                        JOIN admission_portal_account pa
                          ON pa.id=fa.portal_account_id AND pa.tenant_id=fa.tenant_id
                        WHERE s.tenant_id=? AND s.id=? AND p.status='ACTIVE'
                        """,
                        (result, row) -> scheduleContext(result),
                        tenantId,
                        scheduleId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new BillingNotFoundException("Échéance active introuvable"));
    }

    private ScheduleContext scheduleContext(ResultSet result) throws SQLException {
        BigDecimal amount = money(result.getBigDecimal("amount"));
        BigDecimal paid = money(result.getBigDecimal("paid_amount"));
        return new ScheduleContext(
                result.getObject("id", UUID.class),
                result.getObject("family_account_id", UUID.class),
                result.getString("plan_number"),
                result.getObject("due_date", LocalDate.class),
                money(amount.subtract(paid).max(ZERO)),
                result.getString("email"));
    }

    private PreparedRule prepareRule(ReminderRuleCommand command) {
        if (command == null || command.offsetDays() == null) {
            throw new BillingValidationException("Le décalage de relance est requis");
        }
        if (command.offsetDays() < -365 || command.offsetDays() > 365) {
            throw new BillingValidationException(
                    "Le décalage doit rester compris entre -365 et 365 jours");
        }
        return new PreparedRule(
                requiredText(command.name(), 120, "Le nom"),
                command.offsetDays(),
                enumValue(command.channel(), CHANNELS, "Le canal"),
                command.automatic(),
                command.active(),
                requiredText(command.templateKey(), 120, "Le modèle"));
    }

    private BigDecimal overdueAmount(UUID tenantId, LocalDate asOf, LocalDate olderThan) {
        return queryMoney(
                """
                SELECT COALESCE(SUM(s.amount-s.paid_amount),0)
                FROM billing_payment_schedule s
                JOIN billing_installment_plan p
                  ON p.id=s.installment_plan_id AND p.tenant_id=s.tenant_id
                WHERE s.tenant_id=? AND p.status='ACTIVE' AND s.paid_amount<s.amount
                  AND s.due_date<? AND s.due_date<?
                """,
                tenantId,
                asOf,
                olderThan);
    }

    private boolean actionExists(UUID tenantId, String key) {
        return jdbc.queryForObject(
                        "SELECT count(*) FROM billing_collection_action WHERE tenant_id=?"
                                + " AND idempotency_key=?",
                        Integer.class,
                        tenantId,
                        key)
                > 0;
    }

    private String defaultMessage(ScheduleContext schedule) {
        return "Échéance %s du %s : %s MAD restant."
                .formatted(schedule.planNumber(), schedule.dueDate(), schedule.remaining());
    }

    private void requireAccount(UUID tenantId, UUID accountId) {
        if (accountId == null
                || jdbc.queryForObject(
                                "SELECT count(*) FROM billing_family_account WHERE tenant_id=?"
                                        + " AND id=? AND status='ACTIVE'",
                                Integer.class,
                                tenantId,
                                accountId)
                        == 0) {
            throw new BillingNotFoundException("Compte famille actif introuvable");
        }
    }

    private String nextNumber(UUID tenantId, String type, String prefix, Instant instant) {
        int year = instant.atZone(SCHOOL_ZONE).getYear();
        jdbc.queryForObject(
                "SELECT id FROM identity_tenant WHERE id=? FOR UPDATE", UUID.class, tenantId);
        Integer count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM billing_document_sequence WHERE tenant_id=?"
                                + " AND document_type=? AND calendar_year=?",
                        Integer.class,
                        tenantId,
                        type,
                        year);
        if (count == null || count == 0) {
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

    private LocalDate asOf(LocalDate value) {
        LocalDate today = LocalDate.now(clock.withZone(SCHOOL_ZONE));
        if (value == null) return today;
        if (value.isBefore(today.minusYears(5)) || value.isAfter(today.plusYears(5))) {
            throw new BillingValidationException("La date de situation est hors périmètre");
        }
        return value;
    }

    private BigDecimal queryMoney(String sql, Object... parameters) {
        BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class, parameters);
        return money(value);
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal positiveMoney(BigDecimal value, String label) {
        BigDecimal normalized = money(value);
        if (normalized.signum() <= 0) {
            throw new BillingValidationException(label + " doit être strictement positif");
        }
        return normalized;
    }

    private static String enumValue(String value, Set<String> allowed, String label) {
        String normalized = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new BillingValidationException(label + " est invalide");
        }
        return normalized;
    }

    private static String requiredText(String value, int maxLength, String label) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new BillingValidationException(
                    label + " est requis et limité à " + maxLength + " caractères");
        }
        return normalized;
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private record PreparedSchedule(int position, LocalDate dueDate, BigDecimal amount) {}

    private record PreparedRule(
            String name,
            int offsetDays,
            String channel,
            boolean automatic,
            boolean active,
            String templateKey) {}

    private record ScheduleContext(
            UUID id,
            UUID familyAccountId,
            String planNumber,
            LocalDate dueDate,
            BigDecimal remaining,
            String email) {}

    public record InstallmentPlanCommand(
            String name,
            String frequency,
            BigDecimal totalAmount,
            LocalDate startsOn,
            UUID invoiceId,
            Integer installmentCount,
            List<CustomInstallmentCommand> installments) {}

    public record CustomInstallmentCommand(LocalDate dueDate, BigDecimal amount) {}

    public record ReminderRuleCommand(
            String name,
            Integer offsetDays,
            String channel,
            boolean automatic,
            boolean active,
            String templateKey) {}

    public record ManualReminderCommand(String channel, String message) {}

    public record CollectionsDashboard(
            Instant generatedAt,
            LocalDate asOf,
            String currencyCode,
            BigDecimal totalExpected,
            BigDecimal totalReceived,
            BigDecimal collectionRate,
            BigDecimal unpaid,
            BigDecimal overdueOver7Days,
            BigDecimal overdueOver30Days,
            BigDecimal overdueOver60Days,
            int unpaidInstallments,
            List<InstallmentPlanView> plans,
            List<ReminderRuleView> reminderRules,
            List<CollectionActionView> recentActions) {}

    public record InstallmentPlanView(
            UUID id,
            UUID familyAccountId,
            String accountNumber,
            String familyName,
            String planNumber,
            String name,
            String frequency,
            BigDecimal totalAmount,
            BigDecimal paidAmount,
            BigDecimal remainingAmount,
            String currencyCode,
            LocalDate startsOn,
            String status,
            LocalDate nextDueDate,
            List<PaymentScheduleView> schedules) {}

    public record PaymentScheduleView(
            UUID id,
            UUID invoiceId,
            int position,
            LocalDate dueDate,
            BigDecimal amount,
            BigDecimal paidAmount,
            BigDecimal remainingAmount,
            String status,
            long daysOverdue,
            Instant paidAt) {}

    public record ReminderRuleView(
            UUID id,
            String name,
            int offsetDays,
            String channel,
            boolean automatic,
            boolean active,
            String templateKey) {}

    public record CollectionActionView(
            UUID id,
            UUID familyAccountId,
            String accountNumber,
            String familyName,
            UUID paymentScheduleId,
            String planNumber,
            LocalDate dueDate,
            String actionType,
            String channel,
            String status,
            String adapterKey,
            String message,
            LocalDate plannedFor,
            Instant executedAt) {}

    public record AutomaticRunResult(LocalDate asOf, int createdActions, int skippedActions) {}
}
