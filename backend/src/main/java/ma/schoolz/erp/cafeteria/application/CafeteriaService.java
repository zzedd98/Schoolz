package ma.schoolz.erp.cafeteria.application;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ma.schoolz.erp.admissions.AdmissionPortalAccess.PortalIdentity;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.identity.IdentityAuditTrail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CafeteriaService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final IdentityAuditTrail audit;

    public CafeteriaService(JdbcTemplate jdbc, Clock clock, IdentityAuditTrail audit) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard(SchoolPrincipal actor) {
        require(actor, "cafeteria.read");
        return new Dashboard(
                clock.instant(),
                plans(actor.tenantId()),
                menus(actor.tenantId()),
                subscriptions(actor.tenantId(), null),
                attendances(actor.tenantId(), null),
                actor.permissions().contains("cafeteria.health")
                        ? restrictions(actor.tenantId(), null)
                        : List.of(),
                actor.permissions().contains("cafeteria.health"));
    }

    @Transactional(readOnly = true)
    public Dashboard family(PortalIdentity parent) {
        return new Dashboard(
                clock.instant(),
                plans(parent.tenantId()),
                menus(parent.tenantId()),
                subscriptions(parent.tenantId(), parent.accountId()),
                attendances(parent.tenantId(), parent.accountId()),
                restrictions(parent.tenantId(), parent.accountId()),
                true);
    }

    @Transactional
    public Subscription subscribe(SchoolPrincipal actor, SubscribeCommand c, String ip) {
        require(actor, "cafeteria.manage");
        UUID student = req(c.studentId(), "Élève requis");
        Plan plan = plan(actor.tenantId(), req(c.mealPlanId(), "Plan requis"));
        LocalDate start = c.startsOn() == null ? LocalDate.now(clock) : c.startsOn();
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO"
                    + " cafeteria_meal_subscription(id,tenant_id,student_id,meal_plan_id,starts_on,ends_on,status,billing_mode,created_at)"
                    + " VALUES(?,?,?,?,?,?,'ACTIVE',?,?)",
                id,
                actor.tenantId(),
                student,
                plan.id(),
                start,
                c.endsOn(),
                "SINGLE".equals(plan.type()) ? "PER_MEAL" : "MONTHLY",
                timestamp(clock.instant()));
        if (!"SINGLE".equals(plan.type()))
            createCharge(
                    actor.tenantId(), student, id, null, "MONTHLY", plan.monthlyPrice(), start);
        audit(actor, "cafeteria.subscription.create", id, ip);
        return subscriptions(actor.tenantId(), null).stream()
                .filter(x -> x.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public Attendance attend(SchoolPrincipal actor, AttendanceCommand c, String ip) {
        require(actor, "cafeteria.attendance");
        UUID student = req(c.studentId(), "Élève requis");
        UUID menu = req(c.menuId(), "Menu requis");
        UUID subscription = c.subscriptionId();
        String type = subscription == null ? "ONE_OFF" : "SUBSCRIPTION";
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO"
                    + " cafeteria_meal_attendance(id,tenant_id,student_id,menu_id,subscription_id,attendance_type,occurred_at,recorded_by_user_id)"
                    + " VALUES(?,?,?,?,?,?,?,?)",
                id,
                actor.tenantId(),
                student,
                menu,
                subscription,
                type,
                timestamp(clock.instant()),
                actor.accountId());
        if (subscription == null) {
            BigDecimal price =
                    plans(actor.tenantId()).stream()
                            .filter(p -> "SINGLE".equals(p.type()) || "HYBRID".equals(p.type()))
                            .map(Plan::singleMealPrice)
                            .filter(java.util.Objects::nonNull)
                            .findFirst()
                            .orElse(BigDecimal.ZERO);
            createCharge(
                    actor.tenantId(), student, null, id, "ONE_OFF", price, LocalDate.now(clock));
        }
        audit(actor, "cafeteria.attendance.record", id, ip);
        return attendances(actor.tenantId(), null).stream()
                .filter(x -> x.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private void createCharge(
            UUID tenant,
            UUID student,
            UUID subscription,
            UUID attendance,
            String type,
            BigDecimal amount,
            LocalDate date) {
        UUID account =
                jdbc.queryForObject(
                        "SELECT b.id FROM billing_family_account b JOIN"
                            + " enrollment_portal_student_link l ON"
                            + " l.portal_account_id=b.portal_account_id AND l.tenant_id=b.tenant_id"
                            + " WHERE b.tenant_id=? AND l.student_id=?",
                        UUID.class,
                        tenant,
                        student);
        UUID charge = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO"
                    + " billing_charge(id,tenant_id,family_account_id,student_id,service_type,label,description,quantity,unit_amount,gross_amount,discount_amount,net_amount,currency_code,charged_on,status,source_reference,created_at,updated_at,version)"
                    + " VALUES(?,?,?,?,'CAFETERIA','Service cantine','Généré automatiquement par la"
                    + " cantine',1,?, ?,0,?,'MAD',?,'POSTED',?,?,?,0)",
                charge,
                tenant,
                account,
                student,
                amount,
                amount,
                amount,
                date,
                "CAFETERIA:" + (subscription == null ? attendance : subscription),
                timestamp(clock.instant()),
                timestamp(clock.instant()));
        jdbc.update(
                "INSERT INTO"
                    + " cafeteria_meal_charge(id,tenant_id,student_id,subscription_id,attendance_id,billing_charge_id,charge_type,amount,charged_on,created_at)"
                    + " VALUES(?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(),
                tenant,
                student,
                subscription,
                attendance,
                charge,
                type,
                amount,
                date,
                timestamp(clock.instant()));
    }

    private List<Plan> plans(UUID t) {
        return jdbc.query(
                "SELECT id,code,name,plan_type,monthly_price,single_meal_price,active FROM"
                        + " cafeteria_meal_plan WHERE tenant_id=?",
                (r, n) ->
                        new Plan(
                                r.getObject("id", UUID.class),
                                r.getString("code"),
                                r.getString("name"),
                                r.getString("plan_type"),
                                r.getBigDecimal("monthly_price"),
                                r.getBigDecimal("single_meal_price"),
                                r.getBoolean("active")),
                t);
    }

    private Plan plan(UUID t, UUID id) {
        return plans(t).stream()
                .filter(x -> x.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Plan introuvable"));
    }

    private List<Menu> menus(UUID t) {
        return jdbc.query(
                "SELECT id,service_date,meal_type,title,description,published FROM cafeteria_menu"
                        + " WHERE tenant_id=? ORDER BY service_date DESC",
                (r, n) ->
                        new Menu(
                                r.getObject("id", UUID.class),
                                r.getObject("service_date", LocalDate.class),
                                r.getString("meal_type"),
                                r.getString("title"),
                                r.getString("description"),
                                r.getBoolean("published")),
                t);
    }

    private String familyJoin(String alias) {
        return " JOIN enrollment_portal_student_link link ON link.tenant_id="
                + alias
                + ".tenant_id AND link.student_id="
                + alias
                + ".student_id";
    }

    private List<Subscription> subscriptions(UUID t, UUID parent) {
        String j = parent == null ? "" : familyJoin("s");
        Object[] a = parent == null ? new Object[] {t} : new Object[] {t, parent};
        return jdbc.query(
                "SELECT s.id,s.student_id,p.first_name,p.last_name,mp.name AS"
                        + " plan_name,s.starts_on,s.ends_on,s.status,s.billing_mode FROM"
                        + " cafeteria_meal_subscription s "
                        + j
                        + " JOIN student_profile sp ON sp.id=s.student_id JOIN person p ON"
                        + " p.id=sp.person_id JOIN cafeteria_meal_plan mp ON mp.id=s.meal_plan_id"
                        + " WHERE s.tenant_id=?"
                        + (parent == null ? "" : " AND link.portal_account_id=?")
                        + " ORDER BY p.first_name",
                (r, n) ->
                        new Subscription(
                                r.getObject("id", UUID.class),
                                r.getObject("student_id", UUID.class),
                                r.getString("first_name") + " " + r.getString("last_name"),
                                r.getString("plan_name"),
                                r.getObject("starts_on", LocalDate.class),
                                r.getObject("ends_on", LocalDate.class),
                                r.getString("status"),
                                r.getString("billing_mode")),
                a);
    }

    private List<Attendance> attendances(UUID t, UUID parent) {
        String j = parent == null ? "" : familyJoin("a");
        Object[] x = parent == null ? new Object[] {t} : new Object[] {t, parent};
        return jdbc.query(
                "SELECT"
                    + " a.id,a.student_id,p.first_name,p.last_name,m.title,m.service_date,a.attendance_type,a.occurred_at"
                    + " FROM cafeteria_meal_attendance a "
                        + j
                        + " JOIN student_profile sp ON sp.id=a.student_id JOIN person p ON"
                        + " p.id=sp.person_id JOIN cafeteria_menu m ON m.id=a.menu_id WHERE"
                        + " a.tenant_id=?"
                        + (parent == null ? "" : " AND link.portal_account_id=?")
                        + " ORDER BY a.occurred_at DESC",
                (r, n) ->
                        new Attendance(
                                r.getObject("id", UUID.class),
                                r.getObject("student_id", UUID.class),
                                r.getString("first_name") + " " + r.getString("last_name"),
                                r.getString("title"),
                                r.getObject("service_date", LocalDate.class),
                                r.getString("attendance_type"),
                                instant(r, "occurred_at")),
                x);
    }

    private List<Restriction> restrictions(UUID t, UUID parent) {
        String j = parent == null ? "" : familyJoin("f");
        Object[] x = parent == null ? new Object[] {t} : new Object[] {t, parent};
        return jdbc.query(
                "SELECT"
                    + " f.id,f.student_id,p.first_name,p.last_name,f.restriction_type,a.label_fr,f.details,f.severity,f.emergency_action"
                    + " FROM cafeteria_food_restriction f "
                        + j
                        + " JOIN student_profile sp ON sp.id=f.student_id JOIN person p ON"
                        + " p.id=sp.person_id LEFT JOIN cafeteria_allergen a ON a.id=f.allergen_id"
                        + " WHERE f.tenant_id=? AND f.active=TRUE"
                        + (parent == null ? "" : " AND link.portal_account_id=?"),
                (r, n) ->
                        new Restriction(
                                r.getObject("id", UUID.class),
                                r.getObject("student_id", UUID.class),
                                r.getString("first_name") + " " + r.getString("last_name"),
                                r.getString("restriction_type"),
                                r.getString("label_fr"),
                                r.getString("details"),
                                r.getString("severity"),
                                r.getString("emergency_action")),
                x);
    }

    private void audit(SchoolPrincipal a, String action, UUID id, String ip) {
        audit.record(
                a.tenantId(),
                a.accountId(),
                action,
                "Cafeteria",
                id.toString(),
                ip,
                Map.of(),
                null,
                Map.of("id", id));
    }

    private static void require(SchoolPrincipal a, String p) {
        if (a == null || !a.permissions().contains(p))
            throw new org.springframework.security.access.AccessDeniedException(
                    "Permission requise : " + p);
    }

    private static UUID req(UUID v, String m) {
        if (v == null) throw new IllegalArgumentException(m);
        return v;
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private static Instant instant(java.sql.ResultSet result, String column)
            throws java.sql.SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    public record SubscribeCommand(
            UUID studentId, UUID mealPlanId, LocalDate startsOn, LocalDate endsOn) {}

    public record AttendanceCommand(UUID studentId, UUID menuId, UUID subscriptionId) {}

    public record Plan(
            UUID id,
            String code,
            String name,
            String type,
            BigDecimal monthlyPrice,
            BigDecimal singleMealPrice,
            boolean active) {}

    public record Menu(
            UUID id,
            LocalDate serviceDate,
            String mealType,
            String title,
            String description,
            boolean published) {}

    public record Subscription(
            UUID id,
            UUID studentId,
            String studentName,
            String planName,
            LocalDate startsOn,
            LocalDate endsOn,
            String status,
            String billingMode) {}

    public record Attendance(
            UUID id,
            UUID studentId,
            String studentName,
            String menuTitle,
            LocalDate serviceDate,
            String attendanceType,
            Instant occurredAt) {}

    public record Restriction(
            UUID id,
            UUID studentId,
            String studentName,
            String type,
            String allergen,
            String details,
            String severity,
            String emergencyAction) {}

    public record Dashboard(
            Instant generatedAt,
            List<Plan> plans,
            List<Menu> menus,
            List<Subscription> subscriptions,
            List<Attendance> attendances,
            List<Restriction> restrictions,
            boolean healthDataVisible) {}
}
