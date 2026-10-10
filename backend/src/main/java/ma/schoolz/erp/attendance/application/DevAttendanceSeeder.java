package ma.schoolz.erp.attendance.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ma.schoolz.erp.identity.TenantDirectory;
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
@Order(70)
public class DevAttendanceSeeder implements ApplicationRunner {
    private static final String TEACHER_NUMBER = "DEMO-TEACH-001";

    private final TenantDirectory tenants;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final boolean enabled;
    private final String tenantSlug;

    public DevAttendanceSeeder(
            TenantDirectory tenants,
            JdbcTemplate jdbc,
            Clock clock,
            @Value("${schoolz.demo.seed-enabled:false}") boolean enabled,
            @Value("${schoolz.demo.tenant-slug:}") String tenantSlug) {
        this.tenants = tenants;
        this.jdbc = jdbc;
        this.clock = clock;
        this.enabled = enabled;
        this.tenantSlug = tenantSlug;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (!enabled) return;
        UUID tenantId =
                tenants.findActiveBySlug(tenantSlug)
                        .orElseThrow(() -> new IllegalStateException("Demo tenant was not seeded"))
                        .id();
        Timestamp now = Timestamp.from(clock.instant());
        seedReasons(tenantId, now);

        UUID classroomId =
                id("SELECT id FROM classroom WHERE tenant_id=? AND code='1AP-A'", tenantId);
        LocalDate today = LocalDate.now(clock);
        Integer existing =
                jdbc.queryForObject(
                        "SELECT count(*) FROM attendance_session WHERE tenant_id=? AND"
                                + " classroom_id=? AND session_date=? AND starts_at='08:00'",
                        Integer.class,
                        tenantId,
                        classroomId,
                        today);
        if (existing != null && existing > 0) return;

        UUID campusId =
                id(
                        "SELECT campus_id FROM classroom WHERE tenant_id=? AND id=?",
                        tenantId,
                        classroomId);
        UUID yearId =
                id(
                        "SELECT academic_year_id FROM classroom WHERE tenant_id=? AND id=?",
                        tenantId,
                        classroomId);
        UUID actorId =
                id(
                        "SELECT id FROM identity_user_account WHERE tenant_id=? ORDER BY created_at"
                                + " FETCH FIRST 1 ROW ONLY",
                        tenantId);
        UUID teacherId = teacher(tenantId, now);
        UUID yasmineId =
                id(
                        """
                        SELECT student_id FROM student_identifier
                        WHERE tenant_id=? AND identifier_value='SZ-2026-0001'
                        """,
                        tenantId);
        UUID imaneId =
                student(
                        tenantId,
                        campusId,
                        yearId,
                        classroomId,
                        "SZ-2026-0002",
                        "Imane",
                        "El Mansouri",
                        now);
        UUID adamId =
                student(
                        tenantId,
                        campusId,
                        yearId,
                        classroomId,
                        "SZ-2026-0003",
                        "Adam",
                        "Berrada",
                        now);
        UUID parentId =
                id(
                        """
                        SELECT id FROM admission_portal_account
                        WHERE tenant_id=? AND normalized_email='parent.demo@schoolz.ma'
                        """,
                        tenantId);
        linkParent(tenantId, parentId, yasmineId, now);

        UUID sessionId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO attendance_session
                    (id,tenant_id,campus_id,academic_year_id,classroom_id,teacher_staff_id,
                     session_date,subject_code,subject_name,starts_at,ends_at,status,submitted_at,
                     submitted_by_user_id,created_by_user_id,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'MATH', 'Mathématiques', '08:00', '09:00',
                        'SUBMITTED', ?, ?, ?, ?, ?)
                """,
                sessionId,
                tenantId,
                campusId,
                yearId,
                classroomId,
                teacherId,
                today,
                now,
                actorId,
                actorId,
                now,
                now);
        UUID yasmineRecord = record(tenantId, sessionId, yasmineId, "ABSENT", actorId, now);
        record(tenantId, sessionId, imaneId, "PRESENT", actorId, now);
        UUID adamRecord = record(tenantId, sessionId, adamId, "LATE", actorId, now);
        jdbc.update(
                """
                INSERT INTO attendance_late_arrival
                    (id,tenant_id,attendance_record_id,arrived_at,minutes_late,created_at)
                VALUES (?, ?, ?, '08:12', 12, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                adamRecord,
                now);
        UUID absenceId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO attendance_absence
                    (id,tenant_id,attendance_record_id,student_id,occurred_on,resolution_status,
                     notified_at,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, 'UNEXCUSED', ?, ?, ?)
                """,
                absenceId,
                tenantId,
                yasmineRecord,
                yasmineId,
                today,
                now,
                now,
                now);
        jdbc.update(
                """
                INSERT INTO attendance_parent_notification
                    (id,tenant_id,portal_account_id,student_id,absence_id,notification_type,
                     title,message,status,created_at)
                VALUES (?, ?, ?, ?, ?, 'ABSENCE_RECORDED', 'Absence enregistrée',
                        'Absence en Mathématiques. / تم تسجيل غياب في الرياضيات.', 'UNREAD', ?)
                """,
                UUID.randomUUID(),
                tenantId,
                parentId,
                yasmineId,
                absenceId,
                now);
        seedSchoolLife(tenantId, classroomId, teacherId, actorId, adamId, imaneId, today, now);
    }

    private void seedReasons(UUID tenantId, Timestamp now) {
        List<ReasonSeed> reasons =
                List.of(
                        new ReasonSeed("MALADIE", "Maladie", "مرض", "MEDICAL", true),
                        new ReasonSeed("RENDEZ_VOUS", "Rendez-vous", "موعد", "MEDICAL", true),
                        new ReasonSeed("FAMILLE", "Motif familial", "سبب عائلي", "FAMILY", false),
                        new ReasonSeed(
                                "TRANSPORT",
                                "Problème de transport",
                                "مشكلة نقل",
                                "TRANSPORT",
                                false),
                        new ReasonSeed("AUTRE", "Autre motif", "سبب آخر", "OTHER", false));
        for (ReasonSeed reason : reasons) {
            Integer count =
                    jdbc.queryForObject(
                            "SELECT count(*) FROM attendance_absence_reason WHERE tenant_id=? AND"
                                    + " code=?",
                            Integer.class,
                            tenantId,
                            reason.code());
            if (count != null && count > 0) continue;
            jdbc.update(
                    """
                    INSERT INTO attendance_absence_reason
                        (id,tenant_id,code,name_fr,name_ar,category,requires_document,
                         active,created_at,updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?)
                    """,
                    UUID.randomUUID(),
                    tenantId,
                    reason.code(),
                    reason.nameFr(),
                    reason.nameAr(),
                    reason.category(),
                    reason.requiresDocument(),
                    now,
                    now);
        }
    }

    private UUID teacher(UUID tenantId, Timestamp now) {
        List<UUID> existing =
                jdbc.query(
                        "SELECT id FROM staff_profile WHERE tenant_id=? AND employee_number=?",
                        (result, row) -> result.getObject(1, UUID.class),
                        tenantId,
                        TEACHER_NUMBER);
        if (!existing.isEmpty()) return existing.getFirst();
        UUID personId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO person
                    (id,tenant_id,first_name,last_name,birth_date,nationality_code,status,
                     created_at,updated_at)
                VALUES (?, ?, 'Fouad', 'Mellouli', '1985-05-02', 'MA', 'ACTIVE', ?, ?)
                """,
                personId,
                tenantId,
                now,
                now);
        jdbc.update(
                """
                INSERT INTO staff_profile
                    (id,tenant_id,person_id,employee_number,job_title,employment_status,
                     hired_on,created_at,updated_at)
                VALUES (?, ?, ?, ?, 'Professeur de mathématiques', 'ACTIVE', '2024-09-01', ?, ?)
                """,
                staffId,
                tenantId,
                personId,
                TEACHER_NUMBER,
                now,
                now);
        return staffId;
    }

    private UUID student(
            UUID tenantId,
            UUID campusId,
            UUID yearId,
            UUID classroomId,
            String identifier,
            String firstName,
            String lastName,
            Timestamp now) {
        List<UUID> existing =
                jdbc.query(
                        "SELECT student_id FROM student_identifier WHERE tenant_id=? AND"
                                + " identifier_value=?",
                        (result, row) -> result.getObject(1, UUID.class),
                        tenantId,
                        identifier);
        if (!existing.isEmpty()) return existing.getFirst();
        UUID personId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO person
                    (id,tenant_id,first_name,last_name,birth_date,nationality_code,status,
                     created_at,updated_at)
                VALUES (?, ?, ?, ?, '2020-06-10', 'MA', 'ACTIVE', ?, ?)
                """,
                personId,
                tenantId,
                firstName,
                lastName,
                now,
                now);
        jdbc.update(
                "INSERT INTO student_profile (id,tenant_id,person_id,status,created_at,updated_at)"
                        + " VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                studentId,
                tenantId,
                personId,
                now,
                now);
        jdbc.update(
                """
                INSERT INTO student_identifier
                    (id,tenant_id,student_id,identifier_type,identifier_value,issuer,
                     primary_identifier,created_at)
                VALUES (?, ?, ?, 'SCHOOL', ?, 'SchoolZ', TRUE, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                studentId,
                identifier,
                now);
        jdbc.update(
                """
                INSERT INTO student_enrollment
                    (id,tenant_id,student_id,campus_id,academic_year_id,classroom_id,status,
                     enrolled_on,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', '2026-09-01', ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                studentId,
                campusId,
                yearId,
                classroomId,
                now,
                now);
        return studentId;
    }

    private UUID record(
            UUID tenantId,
            UUID sessionId,
            UUID studentId,
            String status,
            UUID actorId,
            Timestamp now) {
        UUID enrollmentId =
                jdbc.queryForObject(
                        "SELECT id FROM student_enrollment WHERE tenant_id=? AND student_id=?"
                                + " AND status='ACTIVE'",
                        UUID.class,
                        tenantId,
                        studentId);
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO attendance_record
                    (id,tenant_id,session_id,student_enrollment_id,student_id,status,
                     marked_at,marked_by_user_id,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                tenantId,
                sessionId,
                enrollmentId,
                studentId,
                status,
                now,
                actorId,
                now);
        return id;
    }

    private void linkParent(UUID tenantId, UUID parentId, UUID studentId, Timestamp now) {
        Integer count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM enrollment_portal_student_link WHERE tenant_id=?"
                                + " AND portal_account_id=? AND student_id=?",
                        Integer.class,
                        tenantId,
                        parentId,
                        studentId);
        if (count != null && count > 0) return;
        jdbc.update(
                "INSERT INTO enrollment_portal_student_link"
                    + " (id,tenant_id,portal_account_id,student_id,created_at) VALUES (?, ?, ?, ?,"
                    + " ?)",
                UUID.randomUUID(),
                tenantId,
                parentId,
                studentId,
                now);
    }

    private void seedSchoolLife(
            UUID tenantId,
            UUID classroomId,
            UUID teacherId,
            UUID actorId,
            UUID incidentStudentId,
            UUID observedStudentId,
            LocalDate today,
            Timestamp now) {
        UUID incidentId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO disciplinary_incident
                    (id,tenant_id,student_id,classroom_id,reported_by_user_id,occurred_at,
                     category,severity,description,status,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'BEHAVIOUR', 'LOW',
                        'Interruption répétée du cours.', 'OPEN', ?, ?)
                """,
                incidentId,
                tenantId,
                incidentStudentId,
                classroomId,
                actorId,
                now,
                now,
                now);
        jdbc.update(
                """
                INSERT INTO attendance_sanction
                    (id,tenant_id,incident_id,sanction_type,description,starts_on,status,
                     decided_by_user_id,created_at)
                VALUES (?, ?, ?, 'WARNING', 'Rappel formel du règlement.', ?, 'ACTIVE', ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                incidentId,
                today,
                actorId,
                now);
        jdbc.update(
                """
                INSERT INTO teacher_observation
                    (id,tenant_id,student_id,classroom_id,teacher_staff_id,observation_date,
                     category,observation,visible_to_parent,created_by_user_id,created_at)
                VALUES (?, ?, ?, ?, ?, ?, 'PROGRESS',
                        'Participation en nette progression.', TRUE, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                observedStudentId,
                classroomId,
                teacherId,
                today,
                actorId,
                now);
    }

    private UUID id(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, UUID.class, arguments);
    }

    private record ReasonSeed(
            String code, String nameFr, String nameAr, String category, boolean requiresDocument) {}
}
