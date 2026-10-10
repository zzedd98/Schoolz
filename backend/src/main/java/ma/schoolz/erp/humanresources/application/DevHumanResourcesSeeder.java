package ma.schoolz.erp.humanresources.application;

import java.sql.Time;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
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
@Order(210)
public class DevHumanResourcesSeeder implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final boolean enabled;
    private final String tenantSlug;

    public DevHumanResourcesSeeder(
            JdbcTemplate jdbc,
            Clock clock,
            @Value("${schoolz.demo.seed-enabled:false}") boolean enabled,
            @Value("${schoolz.demo.tenant-slug:ecole-demo}") String tenantSlug) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.enabled = enabled;
        this.tenantSlug = tenantSlug;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (!enabled) return;
        UUID tenantId = id("SELECT id FROM identity_tenant WHERE slug=?", tenantSlug);
        if (count("SELECT count(*) FROM hr_department WHERE tenant_id=?", tenantId) > 0) return;
        UUID campusId =
                id(
                        "SELECT id FROM school_campus WHERE tenant_id=? ORDER BY created_at FETCH"
                                + " FIRST 1 ROW ONLY",
                        tenantId);
        UUID adminId =
                id(
                        "SELECT id FROM identity_user_account WHERE tenant_id=? AND"
                                + " normalized_email='admin@schoolz.ma'",
                        tenantId);
        Instant now = clock.instant();

        UUID pedagogy = department(tenantId, campusId, "PEDAGOGY", "Pédagogie", now);
        UUID earlyChildhood = department(tenantId, campusId, "EARLY", "Petite enfance", now);
        department(tenantId, campusId, "ADMIN", "Administration", now);

        UUID coordinator =
                position(tenantId, pedagogy, "PED-COORD", "Coordinateur pédagogique", true, now);
        UUID teacher = position(tenantId, pedagogy, "TEACHER", "Enseignant", true, now);
        UUID educator =
                position(
                        tenantId,
                        earlyChildhood,
                        "EARLY-EDUCATOR",
                        "Éducatrice petite enfance",
                        false,
                        now);

        UUID fouad =
                employee(
                        tenantId,
                        "DEMO-TEACH-001",
                        pedagogy,
                        coordinator,
                        null,
                        "fouad.mellouli@ecole-demo.ma",
                        now);
        UUID nadia =
                employee(
                        tenantId,
                        "DEMO-TEACH-002",
                        pedagogy,
                        teacher,
                        fouad,
                        "nadia.elfassi@ecole-demo.ma",
                        now);
        UUID leila =
                employee(
                        tenantId,
                        "DEMO-TEACH-003",
                        pedagogy,
                        teacher,
                        fouad,
                        "leila.chraibi@ecole-demo.ma",
                        now);
        UUID salma =
                employee(
                        tenantId,
                        "EDU-CRECHE-001",
                        earlyChildhood,
                        educator,
                        fouad,
                        "salma.bennani@ecole-demo.ma",
                        now);

        contract(tenantId, fouad, "CTR-DEMO-001", "PERMANENT", "40.00", adminId, now);
        contract(tenantId, nadia, "CTR-DEMO-002", "PERMANENT", "38.00", adminId, now);
        contract(tenantId, leila, "CTR-DEMO-003", "FIXED_TERM", "32.00", adminId, now);
        contract(tenantId, salma, "CTR-DEMO-004", "PERMANENT", "40.00", adminId, now);

        for (UUID employeeId : List.of(fouad, nadia, leila, salma)) {
            for (int day = 1; day <= 5; day++) {
                schedule(tenantId, employeeId, day, now);
            }
        }

        leave(
                tenantId,
                nadia,
                "PERSONAL",
                LocalDate.now(clock).plusDays(18),
                2,
                "REQUESTED",
                null,
                adminId,
                now);
        leave(
                tenantId,
                leila,
                "ANNUAL",
                LocalDate.now(clock).plusDays(30),
                5,
                "MANAGER_REVIEW",
                null,
                adminId,
                now);
        leave(
                tenantId,
                salma,
                "TRAINING",
                LocalDate.now(clock).minusDays(12),
                1,
                "APPROVED",
                "Formation petite enfance validée.",
                adminId,
                now);

        attendance(tenantId, fouad, "PRESENT", adminId, now, 8, 3);
        attendance(tenantId, nadia, "PRESENT", adminId, now, 8, 12);
        attendance(tenantId, leila, "REMOTE", adminId, now, 8, 25);
        attendance(tenantId, salma, "PRESENT", adminId, now, 7, 52);

        document(
                tenantId,
                nadia,
                "IDENTITY",
                "piece-identite-nadia.pdf",
                "demo/hr/nadia/identity.pdf",
                LocalDate.now(clock).plusDays(22),
                "EXPIRING",
                adminId,
                now);
        document(
                tenantId,
                fouad,
                "CONTRACT",
                "contrat-fouad.pdf",
                "demo/hr/fouad/contract.pdf",
                LocalDate.now(clock).plusYears(3),
                "VALID",
                adminId,
                now);

        qualification(
                tenantId,
                fouad,
                "DIPLOMA",
                "Master enseignement des mathématiques",
                "Université Hassan II",
                "Mathématiques",
                adminId,
                now);
        qualification(
                tenantId,
                nadia,
                "CERTIFICATION",
                "Didactique du français au primaire",
                "Centre régional de formation",
                "Français",
                adminId,
                now);
        qualification(
                tenantId,
                leila,
                "DIPLOMA",
                "Licence sciences de la vie",
                "Université Mohammed V",
                "Sciences",
                adminId,
                now);
    }

    private UUID department(UUID tenantId, UUID campusId, String code, String name, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO hr_department(
                    id,tenant_id,campus_id,code,name,description,active,created_at,updated_at)
                VALUES(?,?,?,?,?,'Structure RH de démonstration',TRUE,?,?)
                """,
                id,
                tenantId,
                campusId,
                code,
                name,
                timestamp(now),
                timestamp(now));
        return id;
    }

    private UUID position(
            UUID tenantId,
            UUID departmentId,
            String code,
            String title,
            boolean teacher,
            Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO hr_position(
                    id,tenant_id,department_id,code,title,description,teacher_position,active,
                    created_at,updated_at)
                VALUES(?,?,?,?,?,'Poste de démonstration',?,TRUE,?,?)
                """,
                id,
                tenantId,
                departmentId,
                code,
                title,
                teacher,
                timestamp(now),
                timestamp(now));
        return id;
    }

    private UUID employee(
            UUID tenantId,
            String employeeNumber,
            UUID departmentId,
            UUID positionId,
            UUID managerId,
            String email,
            Instant now) {
        List<StaffRow> staff =
                jdbc.query(
                        "SELECT id,person_id,hired_on FROM staff_profile WHERE tenant_id=? AND"
                                + " employee_number=?",
                        (result, row) ->
                                new StaffRow(
                                        result.getObject("id", UUID.class),
                                        result.getObject("person_id", UUID.class),
                                        result.getObject("hired_on", LocalDate.class)),
                        tenantId,
                        employeeNumber);
        if (staff.isEmpty()) {
            throw new IllegalStateException(
                    "Profil personnel de démonstration absent : " + employeeNumber);
        }
        StaffRow row = staff.getFirst();
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO hr_employee(
                    id,tenant_id,person_id,staff_profile_id,department_id,position_id,
                    manager_employee_id,work_email,work_phone,status,joined_on,created_at,
                    updated_at,version)
                VALUES(?,?,?,?,?,?,?,?,'+212 5 22 00 20 26','ACTIVE',?,?,?,0)
                """,
                id,
                tenantId,
                row.personId(),
                row.staffId(),
                departmentId,
                positionId,
                managerId,
                email.toLowerCase(Locale.ROOT),
                row.hiredOn(),
                timestamp(now),
                timestamp(now));
        return id;
    }

    private void contract(
            UUID tenantId,
            UUID employeeId,
            String number,
            String type,
            String weeklyHours,
            UUID adminId,
            Instant now) {
        jdbc.update(
                """
                INSERT INTO hr_employment_contract(
                    id,tenant_id,employee_id,contract_number,contract_type,starts_on,ends_on,
                    weekly_hours,status,notes,created_by_user_id,created_at,updated_at)
                VALUES(?,?,?,?,?,? ,NULL,?,'ACTIVE','Contrat RH de démonstration',?,?,?)
                """,
                UUID.randomUUID(),
                tenantId,
                employeeId,
                number,
                type,
                LocalDate.of(2024, 9, 1),
                new java.math.BigDecimal(weeklyHours),
                adminId,
                timestamp(now),
                timestamp(now));
    }

    private void schedule(UUID tenantId, UUID employeeId, int day, Instant now) {
        jdbc.update(
                """
                INSERT INTO hr_work_schedule(
                    id,tenant_id,employee_id,day_of_week,starts_at,ends_at,break_minutes,
                    effective_from,effective_to,created_at)
                VALUES(?,?,?,?,?,?,60,?,NULL,?)
                """,
                UUID.randomUUID(),
                tenantId,
                employeeId,
                day,
                Time.valueOf(LocalTime.of(8, 0)),
                Time.valueOf(LocalTime.of(16, 30)),
                LocalDate.of(2026, 9, 1),
                timestamp(now));
    }

    private void leave(
            UUID tenantId,
            UUID employeeId,
            String type,
            LocalDate startsOn,
            int days,
            String status,
            String comment,
            UUID adminId,
            Instant now) {
        boolean reviewed = !"REQUESTED".equals(status);
        boolean decided = "APPROVED".equals(status) || "REJECTED".equals(status);
        jdbc.update(
                """
                INSERT INTO hr_leave_request(
                    id,tenant_id,employee_id,leave_type,starts_on,ends_on,working_days,reason,
                    status,requested_at,manager_reviewed_by_user_id,manager_reviewed_at,
                    decided_by_user_id,decided_at,decision_comment,updated_at,version)
                VALUES(?,?,?,?,?,?,?,'Demande RH de démonstration',?,?,?,?,?,?,?,?,0)
                """,
                UUID.randomUUID(),
                tenantId,
                employeeId,
                type,
                startsOn,
                startsOn.plusDays(days - 1L),
                new java.math.BigDecimal(days),
                status,
                timestamp(now.minusSeconds(86_400)),
                reviewed ? adminId : null,
                reviewed ? timestamp(now.minusSeconds(43_200)) : null,
                decided ? adminId : null,
                decided ? timestamp(now.minusSeconds(21_600)) : null,
                comment,
                timestamp(now));
    }

    private void attendance(
            UUID tenantId,
            UUID employeeId,
            String status,
            UUID adminId,
            Instant now,
            int hour,
            int minute) {
        LocalDate date = LocalDate.now(clock);
        Instant checkIn = date.atTime(hour, minute).atZone(clock.getZone()).toInstant();
        jdbc.update(
                """
                INSERT INTO hr_staff_attendance(
                    id,tenant_id,employee_id,work_date,checked_in_at,checked_out_at,status,source,
                    note,recorded_by_user_id,created_at,updated_at)
                VALUES(?,?,?,?,?,NULL,?,'DEMO','Pointage de démonstration',?,?,?)
                """,
                UUID.randomUUID(),
                tenantId,
                employeeId,
                date,
                timestamp(checkIn),
                status,
                adminId,
                timestamp(now),
                timestamp(now));
    }

    private void document(
            UUID tenantId,
            UUID employeeId,
            String type,
            String fileName,
            String objectKey,
            LocalDate expiresOn,
            String status,
            UUID adminId,
            Instant now) {
        jdbc.update(
                """
                INSERT INTO hr_staff_document(
                    id,tenant_id,employee_id,document_type,file_name,object_key,issued_on,
                    expires_on,status,uploaded_by_user_id,created_at)
                VALUES(?,?,?,?,?,?,'2025-09-01',?,?,?,?)
                """,
                UUID.randomUUID(),
                tenantId,
                employeeId,
                type,
                fileName,
                objectKey,
                expiresOn,
                status,
                adminId,
                timestamp(now));
    }

    private void qualification(
            UUID tenantId,
            UUID employeeId,
            String type,
            String title,
            String institution,
            String subject,
            UUID adminId,
            Instant now) {
        jdbc.update(
                """
                INSERT INTO hr_teacher_qualification(
                    id,tenant_id,employee_id,qualification_type,title,institution,subject_area,
                    obtained_on,expires_on,verified,verified_by_user_id,verified_at,created_at)
                VALUES(?,?,?,?,?,?,?,'2019-06-30',NULL,TRUE,?,?,?)
                """,
                UUID.randomUUID(),
                tenantId,
                employeeId,
                type,
                title,
                institution,
                subject,
                adminId,
                timestamp(now),
                timestamp(now));
    }

    private UUID id(String sql, Object... arguments) {
        List<UUID> values =
                jdbc.query(sql, (result, row) -> result.getObject(1, UUID.class), arguments);
        if (values.isEmpty()) throw new IllegalStateException("Donnée de démonstration absente");
        return values.getFirst();
    }

    private int count(String sql, Object... arguments) {
        Integer value = jdbc.queryForObject(sql, Integer.class, arguments);
        return value == null ? 0 : value;
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private record StaffRow(UUID staffId, UUID personId, LocalDate hiredOn) {}
}
