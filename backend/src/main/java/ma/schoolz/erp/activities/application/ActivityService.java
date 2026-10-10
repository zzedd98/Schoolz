package ma.schoolz.erp.activities.application;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import ma.schoolz.erp.admissions.AdmissionPortalAccess.PortalIdentity;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.identity.IdentityAuditTrail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final IdentityAuditTrail audit;

    public ActivityService(JdbcTemplate j, Clock c, IdentityAuditTrail a) {
        jdbc = j;
        clock = c;
        audit = a;
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard(SchoolPrincipal a) {
        require(a, "activity.read");
        return data(a.tenantId(), null);
    }

    @Transactional(readOnly = true)
    public Dashboard family(PortalIdentity p) {
        return data(p.tenantId(), p.accountId());
    }

    @Transactional
    public EnrollmentResult enrollFamily(PortalIdentity p, EnrollCommand c) {
        UUID activity = req(c.activityId()), student = req(c.studentId());
        Integer linked =
                jdbc.queryForObject(
                        "SELECT count(*) FROM enrollment_portal_student_link WHERE tenant_id=? AND"
                                + " portal_account_id=? AND student_id=?",
                        Integer.class,
                        p.tenantId(),
                        p.accountId(),
                        student);
        if (linked == null || linked == 0)
            throw new IllegalArgumentException("Enfant non autorisé");
        ActivityView a = lock(p.tenantId(), activity);
        if (!a.allowParentEnrollment() || !"OPEN".equals(a.status()))
            throw new IllegalArgumentException("Inscription parent non autorisée");
        int used = a.enrolledCount();
        if (used >= a.maximumCapacity()) {
            if (!a.waitingListEnabled()) throw new IllegalArgumentException("Activité complète");
            int pos =
                    Optional.ofNullable(
                                            jdbc.queryForObject(
                                                    "SELECT max(position_number) FROM"
                                                        + " activity_waiting_list WHERE tenant_id=?"
                                                        + " AND activity_id=?",
                                                    Integer.class,
                                                    p.tenantId(),
                                                    activity))
                                    .orElse(0)
                            + 1;
            UUID id = UUID.randomUUID();
            jdbc.update(
                    "INSERT INTO"
                        + " activity_waiting_list(id,tenant_id,activity_id,student_id,requested_by_portal_account_id,position_number,status,requested_at)"
                        + " VALUES(?,?,?,?,?,?,'WAITING',?)",
                    id,
                    p.tenantId(),
                    activity,
                    student,
                    p.accountId(),
                    pos,
                    timestamp(clock.instant()));
            return new EnrollmentResult("WAITING_LIST", null, id, pos);
        }
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO"
                    + " activity_enrollment(id,tenant_id,activity_id,student_id,enrolled_by_kind,enrolled_by_reference_id,status,enrolled_at)"
                    + " VALUES(?,?,?,?,'PARENT',?,'ENROLLED',?)",
                id,
                p.tenantId(),
                activity,
                student,
                p.accountId(),
                timestamp(clock.instant()));
        if (a.price().signum() > 0) charge(p.tenantId(), activity, id, student, a);
        return new EnrollmentResult("ENROLLED", id, null, null);
    }

    private void charge(UUID t, UUID activity, UUID enrollment, UUID student, ActivityView a) {
        UUID account =
                jdbc.queryForObject(
                        "SELECT b.id FROM billing_family_account b JOIN"
                            + " enrollment_portal_student_link l ON"
                            + " l.portal_account_id=b.portal_account_id AND l.tenant_id=b.tenant_id"
                            + " WHERE b.tenant_id=? AND l.student_id=?",
                        UUID.class,
                        t,
                        student);
        UUID charge = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO"
                    + " billing_charge(id,tenant_id,family_account_id,student_id,service_type,label,description,quantity,unit_amount,gross_amount,discount_amount,net_amount,currency_code,charged_on,status,source_reference,created_at,updated_at,version)"
                    + " VALUES(?,?,?,?,'ACTIVITY',?,'Inscription"
                    + " activité',1,?,?,0,?,'MAD',?,'POSTED',?,?,?,0)",
                charge,
                t,
                account,
                student,
                a.name(),
                a.price(),
                a.price(),
                a.price(),
                LocalDate.now(clock),
                "ACTIVITY:" + enrollment,
                timestamp(clock.instant()),
                timestamp(clock.instant()));
        jdbc.update(
                "INSERT INTO"
                    + " activity_charge(id,tenant_id,activity_id,enrollment_id,student_id,billing_charge_id,amount,charged_on,created_at)"
                    + " VALUES(?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(),
                t,
                activity,
                enrollment,
                student,
                charge,
                a.price(),
                LocalDate.now(clock),
                timestamp(clock.instant()));
    }

    private Dashboard data(UUID t, UUID parent) {
        List<ActivityView> activities =
                jdbc.query(
                        "SELECT"
                            + " a.id,a.code,a.name,a.category,a.description,a.allow_parent_enrollment,a.price,a.status,c.maximum_enrollments,c.waiting_list_enabled,coach.display_name,(SELECT"
                            + " count(*) FROM activity_enrollment e WHERE e.tenant_id=a.tenant_id"
                            + " AND e.activity_id=a.id AND e.status='ENROLLED') AS enrolled,(SELECT"
                            + " count(*) FROM activity_waiting_list w WHERE w.tenant_id=a.tenant_id"
                            + " AND w.activity_id=a.id AND w.status='WAITING') AS waiting FROM"
                            + " activity a JOIN activity_capacity c ON c.activity_id=a.id LEFT JOIN"
                            + " activity_coach coach ON coach.id=a.coach_id WHERE a.tenant_id=?"
                            + " ORDER BY a.name",
                        (r, n) ->
                                new ActivityView(
                                        r.getObject("id", UUID.class),
                                        r.getString("code"),
                                        r.getString("name"),
                                        r.getString("category"),
                                        r.getString("description"),
                                        r.getString("display_name"),
                                        r.getBoolean("allow_parent_enrollment"),
                                        r.getBigDecimal("price"),
                                        r.getString("status"),
                                        r.getInt("maximum_enrollments"),
                                        r.getBoolean("waiting_list_enabled"),
                                        r.getInt("enrolled"),
                                        r.getInt("waiting"),
                                        sessions(t, r.getObject("id", UUID.class))),
                        t);
        List<StudentOption> students =
                parent == null
                        ? List.of()
                        : jdbc.query(
                                "SELECT sp.id,p.first_name,p.last_name FROM"
                                    + " enrollment_portal_student_link l JOIN student_profile sp ON"
                                    + " sp.id=l.student_id JOIN person p ON p.id=sp.person_id WHERE"
                                    + " l.tenant_id=? AND l.portal_account_id=?",
                                (r, n) ->
                                        new StudentOption(
                                                r.getObject("id", UUID.class),
                                                r.getString("first_name")
                                                        + " "
                                                        + r.getString("last_name")),
                                t,
                                parent);
        List<MyEnrollment> mine =
                parent == null
                        ? List.of()
                        : jdbc.query(
                                "SELECT"
                                    + " e.id,e.activity_id,a.name,e.student_id,p.first_name,p.last_name,e.status,e.enrolled_at"
                                    + " FROM activity_enrollment e JOIN activity a ON"
                                    + " a.id=e.activity_id JOIN student_profile sp ON"
                                    + " sp.id=e.student_id JOIN person p ON p.id=sp.person_id JOIN"
                                    + " enrollment_portal_student_link l ON"
                                    + " l.student_id=e.student_id AND l.tenant_id=e.tenant_id WHERE"
                                    + " e.tenant_id=? AND l.portal_account_id=?",
                                (r, n) ->
                                        new MyEnrollment(
                                                r.getObject("id", UUID.class),
                                                r.getObject("activity_id", UUID.class),
                                                r.getString("name"),
                                                r.getObject("student_id", UUID.class),
                                                r.getString("first_name")
                                                        + " "
                                                        + r.getString("last_name"),
                                                r.getString("status"),
                                                instant(r, "enrolled_at")),
                                t,
                                parent);
        return new Dashboard(clock.instant(), activities, students, mine);
    }

    private List<Session> sessions(UUID t, UUID a) {
        return jdbc.query(
                "SELECT id,starts_at,ends_at,location,status FROM activity_session WHERE"
                        + " tenant_id=? AND activity_id=? ORDER BY starts_at",
                (r, n) ->
                        new Session(
                                r.getObject("id", UUID.class),
                                instant(r, "starts_at"),
                                instant(r, "ends_at"),
                                r.getString("location"),
                                r.getString("status")),
                t,
                a);
    }

    private ActivityView lock(UUID t, UUID id) {
        jdbc.queryForObject(
                "SELECT id FROM activity_capacity WHERE tenant_id=? AND activity_id=? FOR UPDATE",
                UUID.class,
                t,
                id);
        return data(t, null).activities().stream()
                .filter(a -> a.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static UUID req(UUID v) {
        if (v == null) throw new IllegalArgumentException("Valeur requise");
        return v;
    }

    private static void require(SchoolPrincipal a, String p) {
        if (a == null || !a.permissions().contains(p))
            throw new org.springframework.security.access.AccessDeniedException(
                    "Permission requise : " + p);
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private static Instant instant(java.sql.ResultSet result, String column)
            throws java.sql.SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    public record EnrollCommand(UUID activityId, UUID studentId) {}

    public record EnrollmentResult(
            String status, UUID enrollmentId, UUID waitingListId, Integer waitingPosition) {}

    public record Session(
            UUID id, Instant startsAt, Instant endsAt, String location, String status) {}

    public record ActivityView(
            UUID id,
            String code,
            String name,
            String category,
            String description,
            String coachName,
            boolean allowParentEnrollment,
            BigDecimal price,
            String status,
            int maximumCapacity,
            boolean waitingListEnabled,
            int enrolledCount,
            int waitingCount,
            List<Session> sessions) {}

    public record StudentOption(UUID id, String displayName) {}

    public record MyEnrollment(
            UUID id,
            UUID activityId,
            String activityName,
            UUID studentId,
            String studentName,
            String status,
            Instant enrolledAt) {}

    public record Dashboard(
            Instant generatedAt,
            List<ActivityView> activities,
            List<StudentOption> students,
            List<MyEnrollment> enrollments) {}
}
