package ma.schoolz.erp.studentportal.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StudentPortalService {
    private static final ZoneId SCHOOL_ZONE = ZoneId.of("Africa/Casablanca");
    private static final int DATE_WINDOW_DAYS = 400;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public StudentPortalService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @PreAuthorize("hasAuthority('student-portal.read')")
    public StudentDashboard dashboard(SchoolPrincipal actor, LocalDate requestedDate) {
        return dashboard(actor, requestedDate, false);
    }

    @PreAuthorize("hasAuthority('student-portal.read')")
    public StudentDashboard fullDashboard(SchoolPrincipal actor, LocalDate requestedDate) {
        return dashboard(actor, requestedDate, true);
    }

    private StudentDashboard dashboard(
            SchoolPrincipal actor, LocalDate requestedDate, boolean includeAll) {
        LocalDate today = LocalDate.now(clock.withZone(SCHOOL_ZONE));
        LocalDate queryDate = requestedDate == null ? today : requestedDate;
        validateDate(today, queryDate);
        StudentProfile student = student(actor);

        List<Lesson> lessons = lessons(actor.tenantId(), student.classroomId(), queryDate);
        LocalDate nextSchoolDate = null;
        LocalDate scheduleDate = queryDate;
        if (lessons.isEmpty() && requestedDate == null && student.classroomId() != null) {
            nextSchoolDate = nextTeachingDate(actor.tenantId(), student.classroomId(), queryDate);
            if (nextSchoolDate != null) {
                scheduleDate = nextSchoolDate;
                lessons = lessons(actor.tenantId(), student.classroomId(), scheduleDate);
            }
        }

        List<Homework> homework = homework(actor.tenantId(), student, queryDate, includeAll);
        List<Assessment> assessments =
                assessments(actor.tenantId(), student, queryDate, includeAll);
        List<Grade> grades = grades(actor.tenantId(), student.studentId(), includeAll);
        List<Course> courses = courses(actor.tenantId(), student.classroomId(), includeAll);
        StudentSummary summary = summary(actor.tenantId(), student.studentId(), grades, homework);

        return new StudentDashboard(
                queryDate,
                scheduleDate,
                nextSchoolDate,
                !scheduleDate.equals(queryDate),
                clock.instant(),
                student,
                lessons,
                homework,
                assessments,
                grades,
                courses,
                summary);
    }

    @Transactional
    @PreAuthorize("hasAuthority('student-portal.write')")
    public void markHomework(
            SchoolPrincipal actor, UUID homeworkId, HomeworkStatusCommand command) {
        if (command == null
                || command.status() == null
                || !("TO_DO".equals(command.status()) || "DONE".equals(command.status()))) {
            throw new StudentPortalValidationException("Le statut du devoir est invalide");
        }
        StudentProfile student = student(actor);
        Instant now = clock.instant();
        int updated =
                jdbc.update(
                        """
                        UPDATE homework_student_status hs
                        SET status=?,completed_at=?,marked_by_portal_account_id=NULL,updated_at=?
                        WHERE hs.tenant_id=? AND hs.student_id=? AND hs.homework_id=?
                          AND EXISTS (
                            SELECT 1 FROM homework_assignment h
                            WHERE h.id=hs.homework_id AND h.tenant_id=hs.tenant_id
                              AND h.status IN ('PUBLISHED','CLOSED')
                          )
                        """,
                        command.status(),
                        "DONE".equals(command.status()) ? timestamp(now) : null,
                        timestamp(now),
                        actor.tenantId(),
                        student.studentId(),
                        homeworkId);
        if (updated == 0) {
            throw new StudentPortalNotFoundException("Devoir introuvable pour cet élève");
        }
    }

    private void validateDate(LocalDate today, LocalDate requested) {
        if (requested.isBefore(today.minusDays(DATE_WINDOW_DAYS))
                || requested.isAfter(today.plusDays(DATE_WINDOW_DAYS))) {
            throw new StudentPortalValidationException(
                    "La date doit rester dans une fenêtre de 400 jours autour d'aujourd'hui");
        }
    }

    private StudentProfile student(SchoolPrincipal actor) {
        List<StudentProfile> profiles =
                jdbc.query(
                        """
                        SELECT spp.student_id,p.first_name,p.last_name,p.preferred_name,
                               (SELECT si.identifier_value FROM student_identifier si
                                WHERE si.tenant_id=spp.tenant_id AND si.student_id=spp.student_id
                                ORDER BY si.primary_identifier DESC,si.created_at LIMIT 1)
                                   AS student_number,
                               e.classroom_id,c.code AS classroom_code,c.name AS classroom_name,
                               gl.name AS grade_level_name,campus.name AS campus_name,
                               ay.name AS academic_year_name
                        FROM student_portal_profile spp
                        JOIN student_profile sp
                          ON sp.id=spp.student_id AND sp.tenant_id=spp.tenant_id
                        JOIN person p ON p.id=sp.person_id AND p.tenant_id=sp.tenant_id
                        LEFT JOIN student_enrollment e
                          ON e.student_id=sp.id AND e.tenant_id=sp.tenant_id AND e.status='ACTIVE'
                        LEFT JOIN classroom c ON c.id=e.classroom_id AND c.tenant_id=e.tenant_id
                        LEFT JOIN grade_level gl
                          ON gl.id=c.grade_level_id AND gl.tenant_id=c.tenant_id
                        LEFT JOIN school_campus campus
                          ON campus.id=e.campus_id AND campus.tenant_id=e.tenant_id
                        LEFT JOIN academic_year ay
                          ON ay.id=e.academic_year_id AND ay.tenant_id=e.tenant_id
                        WHERE spp.tenant_id=? AND spp.user_account_id=? AND spp.active=TRUE
                          AND sp.status='ACTIVE'
                        """,
                        (result, row) ->
                                new StudentProfile(
                                        result.getObject("student_id", UUID.class),
                                        result.getString("first_name"),
                                        result.getString("last_name"),
                                        result.getString("preferred_name"),
                                        result.getString("student_number"),
                                        nullableUuid(result, "classroom_id"),
                                        result.getString("classroom_code"),
                                        result.getString("classroom_name"),
                                        result.getString("grade_level_name"),
                                        result.getString("campus_name"),
                                        result.getString("academic_year_name")),
                        actor.tenantId(),
                        actor.accountId());
        if (profiles.isEmpty()) {
            throw new StudentPortalNotFoundException(
                    "Aucun dossier élève actif n'est rattaché à ce compte");
        }
        return profiles.getFirst();
    }

    private List<Lesson> lessons(UUID tenantId, UUID classroomId, LocalDate date) {
        if (classroomId == null) return List.of();
        return jdbc.query(
                """
                SELECT l.id,s.code AS subject_code,s.name_fr,s.name_ar,s.color,
                       p.starts_at,p.ends_at,r.name AS room_name,
                       CONCAT(tp.first_name,' ',tp.last_name) AS teacher_name
                FROM timetable_lesson l
                JOIN timetable_schedule sch
                  ON sch.id=l.schedule_id AND sch.tenant_id=l.tenant_id
                JOIN timetable_time_slot ts
                  ON ts.id=l.time_slot_id AND ts.tenant_id=l.tenant_id
                JOIN timetable_period p
                  ON p.id=ts.period_id AND p.tenant_id=ts.tenant_id
                JOIN timetable_subject s
                  ON s.id=l.subject_id AND s.tenant_id=l.tenant_id
                JOIN physical_room r ON r.id=l.room_id AND r.tenant_id=l.tenant_id
                JOIN staff_profile staff
                  ON staff.id=l.teacher_staff_id AND staff.tenant_id=l.tenant_id
                JOIN person tp ON tp.id=staff.person_id AND tp.tenant_id=staff.tenant_id
                WHERE l.tenant_id=? AND l.classroom_id=? AND sch.status='PUBLISHED'
                  AND ? BETWEEN sch.valid_from AND sch.valid_to AND ts.day_of_week=?
                  AND p.teaching=TRUE
                ORDER BY p.starts_at,l.id
                """,
                (result, row) ->
                        new Lesson(
                                result.getObject("id", UUID.class),
                                result.getString("subject_code"),
                                result.getString("name_fr"),
                                result.getString("name_ar"),
                                result.getString("color"),
                                result.getObject("starts_at", LocalTime.class),
                                result.getObject("ends_at", LocalTime.class),
                                result.getString("room_name"),
                                result.getString("teacher_name")),
                tenantId,
                classroomId,
                date,
                date.getDayOfWeek().getValue());
    }

    private List<Homework> homework(
            UUID tenantId, StudentProfile student, LocalDate from, boolean includeAll) {
        if (student.classroomId() == null) return List.of();
        String sql =
                """
                SELECT h.id,h.title,h.instructions,h.assigned_on,h.due_at,h.estimated_minutes,
                       hs.status,s.code AS subject_code,s.name_fr,s.name_ar,s.color
                FROM homework_assignment h
                JOIN homework_student_status hs
                  ON hs.homework_id=h.id AND hs.tenant_id=h.tenant_id AND hs.student_id=?
                JOIN timetable_subject s ON s.id=h.subject_id AND s.tenant_id=h.tenant_id
                WHERE h.tenant_id=? AND h.classroom_id=? AND h.status IN ('PUBLISHED','CLOSED')
                  AND h.due_at>=?
                ORDER BY CASE WHEN hs.status='TO_DO' THEN 0 ELSE 1 END,h.due_at
                """
                        + (includeAll ? "" : " LIMIT 8");
        return jdbc.query(
                sql,
                (result, row) ->
                        new Homework(
                                result.getObject("id", UUID.class),
                                result.getString("title"),
                                result.getString("instructions"),
                                result.getObject("assigned_on", LocalDate.class),
                                instant(result, "due_at"),
                                nullableInteger(result, "estimated_minutes"),
                                result.getString("status"),
                                result.getString("subject_code"),
                                result.getString("name_fr"),
                                result.getString("name_ar"),
                                result.getString("color")),
                student.studentId(),
                tenantId,
                student.classroomId(),
                timestamp(from.atStartOfDay(SCHOOL_ZONE).toInstant()));
    }

    private List<Assessment> assessments(
            UUID tenantId, StudentProfile student, LocalDate from, boolean includeAll) {
        if (student.classroomId() == null) return List.of();
        String sql =
                """
                SELECT a.id,a.title,a.description,a.assessment_date,a.maximum_score,a.coefficient,
                       at.name_fr AS type_name,s.code AS subject_code,s.name_fr,s.name_ar,s.color,
                       CONCAT(p.first_name,' ',p.last_name) AS teacher_name
                FROM grading_assessment a
                JOIN grading_assessment_type at
                  ON at.id=a.assessment_type_id AND at.tenant_id=a.tenant_id
                JOIN timetable_subject s ON s.id=a.subject_id AND s.tenant_id=a.tenant_id
                JOIN staff_profile staff
                  ON staff.id=a.teacher_staff_id AND staff.tenant_id=a.tenant_id
                JOIN person p ON p.id=staff.person_id AND p.tenant_id=staff.tenant_id
                WHERE a.tenant_id=? AND a.classroom_id=? AND a.status='PUBLISHED'
                  AND a.assessment_date>=?
                ORDER BY a.assessment_date,a.created_at
                """
                        + (includeAll ? "" : " LIMIT 6");
        return jdbc.query(
                sql,
                (result, row) ->
                        new Assessment(
                                result.getObject("id", UUID.class),
                                result.getString("title"),
                                result.getString("description"),
                                result.getObject("assessment_date", LocalDate.class),
                                result.getBigDecimal("maximum_score"),
                                result.getBigDecimal("coefficient"),
                                result.getString("type_name"),
                                result.getString("subject_code"),
                                result.getString("name_fr"),
                                result.getString("name_ar"),
                                result.getString("color"),
                                result.getString("teacher_name")),
                tenantId,
                student.classroomId(),
                from);
    }

    private List<Grade> grades(UUID tenantId, UUID studentId, boolean includeAll) {
        String sql =
                """
                SELECT g.id,a.title,a.assessment_date,a.maximum_score,g.raw_score,
                       g.grade_status,g.teacher_comment,s.code AS subject_code,
                       s.name_fr,s.name_ar,s.color
                FROM grading_grade g
                JOIN grading_assessment a
                  ON a.id=g.assessment_id AND a.tenant_id=g.tenant_id
                JOIN timetable_subject s ON s.id=a.subject_id AND s.tenant_id=a.tenant_id
                WHERE g.tenant_id=? AND g.student_id=? AND a.status='PUBLISHED'
                ORDER BY a.assessment_date DESC,g.updated_at DESC
                """
                        + (includeAll ? "" : " LIMIT 8");
        return jdbc.query(
                sql,
                (result, row) ->
                        new Grade(
                                result.getObject("id", UUID.class),
                                result.getString("title"),
                                result.getObject("assessment_date", LocalDate.class),
                                result.getBigDecimal("raw_score"),
                                result.getBigDecimal("maximum_score"),
                                result.getString("grade_status"),
                                result.getString("teacher_comment"),
                                result.getString("subject_code"),
                                result.getString("name_fr"),
                                result.getString("name_ar"),
                                result.getString("color")),
                tenantId,
                studentId);
    }

    private List<Course> courses(UUID tenantId, UUID classroomId, boolean includeAll) {
        if (classroomId == null) return List.of();
        String sql =
                """
                SELECT lc.id,lc.lesson_date,lc.title,lc.summary,lc.content,
                       s.code AS subject_code,s.name_fr,s.name_ar,s.color,
                       CONCAT(p.first_name,' ',p.last_name) AS teacher_name,
                       (SELECT count(*) FROM homework_attachment a
                        WHERE a.tenant_id=lc.tenant_id AND a.lesson_content_id=lc.id)
                           AS attachment_count
                FROM lesson_content lc
                JOIN timetable_subject s ON s.id=lc.subject_id AND s.tenant_id=lc.tenant_id
                JOIN staff_profile staff
                  ON staff.id=lc.teacher_staff_id AND staff.tenant_id=lc.tenant_id
                JOIN person p ON p.id=staff.person_id AND p.tenant_id=staff.tenant_id
                WHERE lc.tenant_id=? AND lc.classroom_id=? AND lc.status='PUBLISHED'
                ORDER BY lc.lesson_date DESC,lc.published_at DESC
                """
                        + (includeAll ? "" : " LIMIT 6");
        return jdbc.query(
                sql,
                (result, row) ->
                        new Course(
                                result.getObject("id", UUID.class),
                                result.getObject("lesson_date", LocalDate.class),
                                result.getString("title"),
                                result.getString("summary"),
                                result.getString("content"),
                                result.getString("subject_code"),
                                result.getString("name_fr"),
                                result.getString("name_ar"),
                                result.getString("color"),
                                result.getString("teacher_name"),
                                result.getInt("attachment_count")),
                tenantId,
                classroomId);
    }

    private StudentSummary summary(
            UUID tenantId, UUID studentId, List<Grade> grades, List<Homework> homework) {
        BigDecimal average =
                grades.stream()
                        .filter(grade -> grade.score() != null && grade.maximumScore() != null)
                        .map(
                                grade ->
                                        grade.score()
                                                .multiply(BigDecimal.valueOf(20))
                                                .divide(
                                                        grade.maximumScore(),
                                                        2,
                                                        RoundingMode.HALF_UP))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        long graded =
                grades.stream()
                        .filter(grade -> grade.score() != null && grade.maximumScore() != null)
                        .count();
        if (graded > 0) {
            average = average.divide(BigDecimal.valueOf(graded), 2, RoundingMode.HALF_UP);
        } else {
            average = null;
        }
        Integer absences =
                jdbc.queryForObject(
                        """
                        SELECT count(*) FROM attendance_record ar
                        JOIN attendance_session s
                          ON s.id=ar.session_id AND s.tenant_id=ar.tenant_id
                        WHERE ar.tenant_id=? AND ar.student_id=? AND ar.status='ABSENT'
                          AND s.session_date>=?
                        """,
                        Integer.class,
                        tenantId,
                        studentId,
                        LocalDate.now(clock.withZone(SCHOOL_ZONE)).minusDays(30));
        long open = homework.stream().filter(item -> "TO_DO".equals(item.status())).count();
        long completed = homework.stream().filter(item -> "DONE".equals(item.status())).count();
        return new StudentSummary(average, open, completed, absences == null ? 0 : absences);
    }

    private LocalDate nextTeachingDate(UUID tenantId, UUID classroomId, LocalDate after) {
        List<TeachingPattern> patterns =
                jdbc.query(
                        """
                        SELECT DISTINCT ts.day_of_week,sch.valid_from,sch.valid_to
                        FROM timetable_lesson l
                        JOIN timetable_schedule sch
                          ON sch.id=l.schedule_id AND sch.tenant_id=l.tenant_id
                        JOIN timetable_time_slot ts
                          ON ts.id=l.time_slot_id AND ts.tenant_id=l.tenant_id
                        JOIN timetable_period p
                          ON p.id=ts.period_id AND p.tenant_id=ts.tenant_id
                        WHERE l.tenant_id=? AND l.classroom_id=? AND sch.status='PUBLISHED'
                          AND sch.valid_to>? AND p.teaching=TRUE
                        """,
                        (result, row) ->
                                new TeachingPattern(
                                        result.getInt("day_of_week"),
                                        result.getObject("valid_from", LocalDate.class),
                                        result.getObject("valid_to", LocalDate.class)),
                        tenantId,
                        classroomId,
                        after);
        for (int offset = 1; offset <= DATE_WINDOW_DAYS; offset++) {
            LocalDate candidate = after.plusDays(offset);
            boolean matches =
                    patterns.stream()
                            .anyMatch(
                                    pattern ->
                                            pattern.dayOfWeek()
                                                            == candidate.getDayOfWeek().getValue()
                                                    && !candidate.isBefore(pattern.validFrom())
                                                    && !candidate.isAfter(pattern.validTo()));
            if (matches) return candidate;
        }
        return null;
    }

    private static UUID nullableUuid(ResultSet result, String column) throws SQLException {
        return result.getObject(column) == null ? null : result.getObject(column, UUID.class);
    }

    private static Integer nullableInteger(ResultSet result, String column) throws SQLException {
        int value = result.getInt(column);
        return result.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    public record StudentDashboard(
            LocalDate requestedDate,
            LocalDate scheduleDate,
            LocalDate nextSchoolDate,
            boolean showingNextSchoolDay,
            Instant generatedAt,
            StudentProfile student,
            List<Lesson> lessons,
            List<Homework> homework,
            List<Assessment> assessments,
            List<Grade> grades,
            List<Course> courses,
            StudentSummary summary) {}

    public record StudentProfile(
            UUID studentId,
            String firstName,
            String lastName,
            String preferredName,
            String studentNumber,
            UUID classroomId,
            String classroomCode,
            String classroomName,
            String gradeLevelName,
            String campusName,
            String academicYearName) {}

    public record Lesson(
            UUID id,
            String subjectCode,
            String subjectNameFr,
            String subjectNameAr,
            String color,
            LocalTime startsAt,
            LocalTime endsAt,
            String roomName,
            String teacherName) {}

    public record Homework(
            UUID id,
            String title,
            String instructions,
            LocalDate assignedOn,
            Instant dueAt,
            Integer estimatedMinutes,
            String status,
            String subjectCode,
            String subjectNameFr,
            String subjectNameAr,
            String color) {}

    public record Assessment(
            UUID id,
            String title,
            String description,
            LocalDate assessmentDate,
            BigDecimal maximumScore,
            BigDecimal coefficient,
            String typeName,
            String subjectCode,
            String subjectNameFr,
            String subjectNameAr,
            String color,
            String teacherName) {}

    public record Grade(
            UUID id,
            String title,
            LocalDate assessmentDate,
            BigDecimal score,
            BigDecimal maximumScore,
            String status,
            String teacherComment,
            String subjectCode,
            String subjectNameFr,
            String subjectNameAr,
            String color) {}

    public record Course(
            UUID id,
            LocalDate lessonDate,
            String title,
            String summary,
            String content,
            String subjectCode,
            String subjectNameFr,
            String subjectNameAr,
            String color,
            String teacherName,
            int attachmentCount) {}

    public record StudentSummary(
            BigDecimal averageOn20,
            long openHomeworkCount,
            long completedHomeworkCount,
            int absenceCount30Days) {}

    public record HomeworkStatusCommand(String status) {}

    private record TeachingPattern(int dayOfWeek, LocalDate validFrom, LocalDate validTo) {}
}
