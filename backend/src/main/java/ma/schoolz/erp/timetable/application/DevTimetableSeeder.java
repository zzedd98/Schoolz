package ma.schoolz.erp.timetable.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
@Order(80)
public class DevTimetableSeeder implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final TenantDirectory tenants;
    private final Clock clock;
    private final boolean enabled;
    private final String tenantSlug;

    public DevTimetableSeeder(
            JdbcTemplate jdbc,
            TenantDirectory tenants,
            Clock clock,
            @Value("${schoolz.demo.seed-enabled:false}") boolean enabled,
            @Value("${schoolz.demo.tenant-slug:}") String tenantSlug) {
        this.jdbc = jdbc;
        this.tenants = tenants;
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
        Integer existing =
                jdbc.queryForObject(
                        "SELECT count(*) FROM timetable_schedule WHERE tenant_id=? AND"
                                + " code='CENTRE-2026'",
                        Integer.class,
                        tenantId);
        if (existing != null && existing > 0) return;

        Instant now = clock.instant();
        UUID campusId =
                id(
                        "SELECT id FROM school_campus WHERE tenant_id=? AND code='CASA-CENTRE'",
                        tenantId);
        UUID yearId =
                id("SELECT id FROM academic_year WHERE tenant_id=? AND code='2026-2027'", tenantId);
        UUID classroomId =
                id("SELECT id FROM classroom WHERE tenant_id=? AND code='1AP-A'", tenantId);
        UUID actorId =
                id(
                        "SELECT id FROM identity_user_account WHERE tenant_id=? ORDER BY created_at"
                                + " FETCH FIRST 1 ROW ONLY",
                        tenantId);
        UUID mathTeacher =
                id(
                        "SELECT id FROM staff_profile WHERE tenant_id=? AND"
                                + " employee_number='DEMO-TEACH-001'",
                        tenantId);
        UUID frenchTeacher =
                teacher(
                        tenantId,
                        "DEMO-TEACH-002",
                        "Nadia",
                        "El Fassi",
                        "Professeure de français",
                        now);
        UUID scienceTeacher =
                teacher(
                        tenantId,
                        "DEMO-TEACH-003",
                        "Leïla",
                        "Chraïbi",
                        "Professeure de sciences",
                        now);
        UUID homeRoom =
                id("SELECT id FROM physical_room WHERE tenant_id=? AND code='A-101'", tenantId);
        UUID labRoom = labRoom(tenantId, campusId, now);
        equipment(tenantId, homeRoom, "VIDEO_PROJECTOR");
        equipment(tenantId, homeRoom, "WHITEBOARD");
        equipment(tenantId, labRoom, "VIDEO_PROJECTOR");
        equipment(tenantId, labRoom, "SCIENCE_BENCHES");

        UUID math = subject(tenantId, "MATH", "Mathématiques", "الرياضيات", "#2563EB", now);
        UUID french = subject(tenantId, "FR", "Français", "الفرنسية", "#7C3AED", now);
        UUID science = subject(tenantId, "SCI", "Sciences", "العلوم", "#059669", now);
        UUID scheduleId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO timetable_schedule
                    (id,tenant_id,campus_id,academic_year_id,code,name,status,valid_from,valid_to,
                     created_by_user_id,created_at,updated_at)
                VALUES (?, ?, ?, ?, 'CENTRE-2026', 'Emploi du temps — Primaire', 'DRAFT',
                        ?, ?, ?, ?, ?)
                """,
                scheduleId,
                tenantId,
                campusId,
                yearId,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2027, 6, 30),
                actorId,
                timestamp(now),
                timestamp(now));

        List<PeriodSeed> periods =
                List.of(
                        new PeriodSeed("P1", "08:00–09:00", 1, 8, 0, 9, 0, true),
                        new PeriodSeed("P2", "09:00–10:00", 2, 9, 0, 10, 0, true),
                        new PeriodSeed("BREAK", "Récréation", 3, 10, 0, 10, 15, false),
                        new PeriodSeed("P3", "10:15–11:15", 4, 10, 15, 11, 15, true),
                        new PeriodSeed("P4", "11:15–12:15", 5, 11, 15, 12, 15, true),
                        new PeriodSeed("P5", "14:00–15:00", 6, 14, 0, 15, 0, true),
                        new PeriodSeed("P6", "15:00–16:00", 7, 15, 0, 16, 0, true));
        for (PeriodSeed period : periods) {
            UUID periodId = UUID.randomUUID();
            jdbc.update(
                    """
                    INSERT INTO timetable_period
                        (id,tenant_id,schedule_id,code,name,sequence_number,starts_at,ends_at,teaching)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    periodId,
                    tenantId,
                    scheduleId,
                    period.code(),
                    period.name(),
                    period.sequence(),
                    LocalTime.of(period.startHour(), period.startMinute()),
                    LocalTime.of(period.endHour(), period.endMinute()),
                    period.teaching());
            for (int day = 1; day <= 5; day++) {
                jdbc.update(
                        "INSERT INTO timetable_time_slot"
                                + " (id,tenant_id,schedule_id,period_id,day_of_week)"
                                + " VALUES (?, ?, ?, ?, ?)",
                        UUID.randomUUID(),
                        tenantId,
                        scheduleId,
                        periodId,
                        day);
            }
        }

        UUID mathAssignment =
                assignment(
                        tenantId,
                        scheduleId,
                        classroomId,
                        math,
                        mathTeacher,
                        4,
                        "CLASSROOM",
                        "WHITEBOARD",
                        now);
        UUID frenchAssignment =
                assignment(
                        tenantId,
                        scheduleId,
                        classroomId,
                        french,
                        frenchTeacher,
                        3,
                        "CLASSROOM",
                        "",
                        now);
        UUID scienceAssignment =
                assignment(
                        tenantId,
                        scheduleId,
                        classroomId,
                        science,
                        scienceTeacher,
                        2,
                        "LAB",
                        "SCIENCE_BENCHES,VIDEO_PROJECTOR",
                        now);

        lesson(
                tenantId,
                scheduleId,
                mathAssignment,
                classroomId,
                math,
                mathTeacher,
                homeRoom,
                slot(tenantId, scheduleId, 1, "P1"),
                actorId,
                "MANUAL",
                now);
        lesson(
                tenantId,
                scheduleId,
                frenchAssignment,
                classroomId,
                french,
                frenchTeacher,
                homeRoom,
                slot(tenantId, scheduleId, 1, "P2"),
                actorId,
                "MANUAL",
                now);
        lesson(
                tenantId,
                scheduleId,
                mathAssignment,
                classroomId,
                math,
                mathTeacher,
                homeRoom,
                slot(tenantId, scheduleId, 2, "P1"),
                actorId,
                "MANUAL",
                now);
        lesson(
                tenantId,
                scheduleId,
                frenchAssignment,
                classroomId,
                french,
                frenchTeacher,
                homeRoom,
                slot(tenantId, scheduleId, 2, "P2"),
                actorId,
                "MANUAL",
                now);
        lesson(
                tenantId,
                scheduleId,
                scienceAssignment,
                classroomId,
                science,
                scienceTeacher,
                labRoom,
                slot(tenantId, scheduleId, 3, "P3"),
                actorId,
                "MANUAL",
                now);

        availability(
                "timetable_teacher_availability",
                "teacher_staff_id",
                tenantId,
                scheduleId,
                mathTeacher,
                slot(tenantId, scheduleId, 5, "P6"),
                "AVOID");
        availability(
                "timetable_teacher_availability",
                "teacher_staff_id",
                tenantId,
                scheduleId,
                scienceTeacher,
                slot(tenantId, scheduleId, 1, "P1"),
                "UNAVAILABLE");
        availability(
                "timetable_room_availability",
                "room_id",
                tenantId,
                scheduleId,
                labRoom,
                slot(tenantId, scheduleId, 5, "P6"),
                "UNAVAILABLE");
        constraint(tenantId, scheduleId, mathTeacher, "MAX_HOURS_PER_DAY", 4, true, now);
        constraint(tenantId, scheduleId, mathTeacher, "MAX_CONSECUTIVE_HOURS", 3, true, now);
        constraint(tenantId, scheduleId, frenchTeacher, "MAX_HOURS_PER_DAY", 4, true, now);
        constraint(tenantId, scheduleId, scienceTeacher, "MAX_HOURS_PER_DAY", 3, true, now);
    }

    private UUID teacher(
            UUID tenantId,
            String employeeNumber,
            String firstName,
            String lastName,
            String title,
            Instant now) {
        List<UUID> existing =
                jdbc.query(
                        "SELECT id FROM staff_profile WHERE tenant_id=? AND employee_number=?",
                        (result, row) -> result.getObject("id", UUID.class),
                        tenantId,
                        employeeNumber);
        if (!existing.isEmpty()) return existing.getFirst();
        UUID personId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO person
                    (id,tenant_id,first_name,last_name,status,created_at,updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)
                """,
                personId,
                tenantId,
                firstName,
                lastName,
                timestamp(now),
                timestamp(now));
        jdbc.update(
                """
                INSERT INTO staff_profile
                    (id,tenant_id,person_id,employee_number,job_title,employment_status,
                     hired_on,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
                """,
                staffId,
                tenantId,
                personId,
                employeeNumber,
                title,
                LocalDate.of(2022, 9, 1),
                timestamp(now),
                timestamp(now));
        return staffId;
    }

    private UUID labRoom(UUID tenantId, UUID campusId, Instant now) {
        List<UUID> existing =
                jdbc.query(
                        "SELECT id FROM physical_room WHERE tenant_id=? AND code='LAB-MULTI'",
                        (result, row) -> result.getObject("id", UUID.class),
                        tenantId);
        if (!existing.isEmpty()) return existing.getFirst();
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO physical_room
                    (id,tenant_id,campus_id,code,name,room_type,capacity,floor_label,active,
                     created_at,updated_at)
                VALUES (?, ?, ?, 'LAB-MULTI', 'Laboratoire polyvalent', 'LAB', 32, '2', TRUE, ?, ?)
                """,
                id,
                tenantId,
                campusId,
                timestamp(now),
                timestamp(now));
        return id;
    }

    private UUID subject(
            UUID tenantId, String code, String nameFr, String nameAr, String color, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO timetable_subject
                    (id,tenant_id,code,name_fr,name_ar,color,active,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, TRUE, ?, ?)
                """,
                id,
                tenantId,
                code,
                nameFr,
                nameAr,
                color,
                timestamp(now),
                timestamp(now));
        return id;
    }

    private void equipment(UUID tenantId, UUID roomId, String code) {
        Integer existing =
                jdbc.queryForObject(
                        "SELECT count(*) FROM timetable_room_equipment WHERE tenant_id=? AND"
                                + " room_id=? AND equipment_code=?",
                        Integer.class,
                        tenantId,
                        roomId,
                        code);
        if (existing != null && existing > 0) return;
        jdbc.update(
                "INSERT INTO timetable_room_equipment"
                        + " (id,tenant_id,room_id,equipment_code,quantity) VALUES (?, ?, ?, ?, 1)",
                UUID.randomUUID(),
                tenantId,
                roomId,
                code);
    }

    private UUID assignment(
            UUID tenantId,
            UUID scheduleId,
            UUID classroomId,
            UUID subjectId,
            UUID teacherId,
            int weekly,
            String roomType,
            String equipment,
            Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO timetable_teacher_assignment
                    (id,tenant_id,schedule_id,classroom_id,subject_id,teacher_staff_id,
                     weekly_periods,required_room_type,required_equipment,active,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?)
                """,
                id,
                tenantId,
                scheduleId,
                classroomId,
                subjectId,
                teacherId,
                weekly,
                roomType,
                equipment,
                timestamp(now),
                timestamp(now));
        return id;
    }

    private void lesson(
            UUID tenantId,
            UUID scheduleId,
            UUID assignmentId,
            UUID classroomId,
            UUID subjectId,
            UUID teacherId,
            UUID roomId,
            UUID slotId,
            UUID actorId,
            String source,
            Instant now) {
        jdbc.update(
                """
                INSERT INTO timetable_lesson
                    (id,tenant_id,schedule_id,assignment_id,classroom_id,subject_id,
                     teacher_staff_id,room_id,time_slot_id,source,created_by_user_id,
                     created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                scheduleId,
                assignmentId,
                classroomId,
                subjectId,
                teacherId,
                roomId,
                slotId,
                source,
                actorId,
                timestamp(now),
                timestamp(now));
    }

    private void availability(
            String table,
            String targetColumn,
            UUID tenantId,
            UUID scheduleId,
            UUID targetId,
            UUID slotId,
            String availability) {
        jdbc.update(
                "INSERT INTO "
                        + table
                        + " (id,tenant_id,schedule_id,"
                        + targetColumn
                        + ",time_slot_id,availability) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                tenantId,
                scheduleId,
                targetId,
                slotId,
                availability);
    }

    private void constraint(
            UUID tenantId,
            UUID scheduleId,
            UUID teacherId,
            String type,
            int maxPeriods,
            boolean hard,
            Instant now) {
        jdbc.update(
                """
                INSERT INTO timetable_constraint
                    (id,tenant_id,schedule_id,constraint_type,scope_type,target_id,configuration,
                     hard_constraint,weight,enabled,created_at,updated_at)
                VALUES (?, ?, ?, ?, 'TEACHER', ?, ?, ?, 100, TRUE, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                scheduleId,
                type,
                teacherId,
                "{\"maxPeriods\":" + maxPeriods + "}",
                hard,
                timestamp(now),
                timestamp(now));
    }

    private UUID slot(UUID tenantId, UUID scheduleId, int day, String periodCode) {
        return id(
                """
                SELECT ts.id FROM timetable_time_slot ts
                JOIN timetable_period p ON p.id=ts.period_id AND p.tenant_id=ts.tenant_id
                WHERE ts.tenant_id=? AND ts.schedule_id=? AND ts.day_of_week=? AND p.code=?
                """,
                tenantId,
                scheduleId,
                day,
                periodCode);
    }

    private UUID id(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, UUID.class, arguments);
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    private record PeriodSeed(
            String code,
            String name,
            int sequence,
            int startHour,
            int startMinute,
            int endHour,
            int endMinute,
            boolean teaching) {}
}
