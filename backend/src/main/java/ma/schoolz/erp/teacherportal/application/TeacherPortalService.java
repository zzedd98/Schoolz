package ma.schoolz.erp.teacherportal.application;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TeacherPortalService {
    private static final ZoneId SCHOOL_ZONE = ZoneId.of("Africa/Casablanca");
    private static final int DATE_WINDOW_DAYS = 400;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public TeacherPortalService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @PreAuthorize("hasAuthority('teacher-portal.read')")
    public TeacherDashboard dashboard(SchoolPrincipal actor, LocalDate requestedDate) {
        LocalDate today = LocalDate.now(clock.withZone(SCHOOL_ZONE));
        LocalDate date = requestedDate == null ? today : requestedDate;
        if (date.isBefore(today.minusDays(DATE_WINDOW_DAYS))
                || date.isAfter(today.plusDays(DATE_WINDOW_DAYS))) {
            throw new TeacherPortalValidationException(
                    "La date doit rester dans une fenêtre de 400 jours autour d'aujourd'hui");
        }

        TeacherProfile teacher = teacher(actor);
        LocalTime now = LocalTime.now(clock.withZone(SCHOOL_ZONE));
        List<TeacherLesson> lessons = lessons(actor.tenantId(), teacher.staffProfileId(), date);
        List<TeacherLesson> classified = classify(lessons, date, today, now);
        TeacherLesson focus = focus(classified);
        List<QuickAction> actions = actions(actor.permissions(), teacher, date, focus);
        LocalDate nextTeachingDate =
                classified.isEmpty()
                        ? nextTeachingDate(actor.tenantId(), teacher.staffProfileId(), date)
                        : null;

        long completed =
                classified.stream()
                        .filter(lesson -> "COMPLETED".equals(lesson.timelineStatus()))
                        .count();
        long attendanceDone =
                classified.stream()
                        .filter(lesson -> "SUBMITTED".equals(lesson.attendanceStatus()))
                        .count();
        return new TeacherDashboard(
                date,
                clock.instant(),
                teacher,
                classified,
                new DaySummary(classified.size(), completed, attendanceDone),
                nextTeachingDate,
                actions);
    }

    private TeacherProfile teacher(SchoolPrincipal actor) {
        List<TeacherProfile> profiles =
                jdbc.query(
                        """
                        SELECT tp.staff_profile_id,sp.employee_number,p.first_name,p.last_name,
                               sp.job_title
                        FROM teacher_portal_profile tp
                        JOIN staff_profile sp ON sp.id=tp.staff_profile_id
                            AND sp.tenant_id=tp.tenant_id
                        JOIN person p ON p.id=sp.person_id AND p.tenant_id=sp.tenant_id
                        WHERE tp.tenant_id=? AND tp.user_account_id=? AND tp.active=TRUE
                          AND sp.employment_status='ACTIVE'
                        """,
                        (rs, row) ->
                                new TeacherProfile(
                                        rs.getObject("staff_profile_id", UUID.class),
                                        rs.getString("employee_number"),
                                        rs.getString("first_name"),
                                        rs.getString("last_name"),
                                        rs.getString("job_title")),
                        actor.tenantId(),
                        actor.accountId());
        if (profiles.isEmpty()) {
            throw new TeacherPortalNotFoundException(
                    "Aucun profil professeur actif n'est rattaché à ce compte");
        }
        return profiles.getFirst();
    }

    private List<TeacherLesson> lessons(UUID tenantId, UUID staffId, LocalDate date) {
        return jdbc.query(
                """
                SELECT l.id,l.assignment_id,l.classroom_id,c.code AS classroom_code,
                       c.name AS classroom_name,l.subject_id,s.code AS subject_code,
                       s.name_fr AS subject_name_fr,s.name_ar AS subject_name_ar,s.color,
                       r.id AS room_id,r.name AS room_name,p.starts_at,p.ends_at,
                       ats.id AS attendance_session_id,ats.status AS attendance_status
                FROM timetable_lesson l
                JOIN timetable_schedule sch ON sch.id=l.schedule_id AND sch.tenant_id=l.tenant_id
                JOIN timetable_time_slot ts ON ts.id=l.time_slot_id AND ts.tenant_id=l.tenant_id
                JOIN timetable_period p ON p.id=ts.period_id AND p.tenant_id=ts.tenant_id
                JOIN classroom c ON c.id=l.classroom_id AND c.tenant_id=l.tenant_id
                JOIN timetable_subject s ON s.id=l.subject_id AND s.tenant_id=l.tenant_id
                JOIN physical_room r ON r.id=l.room_id AND r.tenant_id=l.tenant_id
                LEFT JOIN attendance_session ats ON ats.tenant_id=l.tenant_id
                    AND ats.classroom_id=l.classroom_id
                    AND ats.teacher_staff_id=l.teacher_staff_id
                    AND ats.session_date=? AND ats.starts_at=p.starts_at
                WHERE l.tenant_id=? AND l.teacher_staff_id=? AND sch.status='PUBLISHED'
                  AND ? BETWEEN sch.valid_from AND sch.valid_to
                  AND ts.day_of_week=? AND p.teaching=TRUE
                ORDER BY p.starts_at,l.id
                """,
                (rs, row) ->
                        new TeacherLesson(
                                rs.getObject("id", UUID.class),
                                rs.getObject("assignment_id", UUID.class),
                                rs.getObject("classroom_id", UUID.class),
                                rs.getString("classroom_code"),
                                rs.getString("classroom_name"),
                                rs.getObject("subject_id", UUID.class),
                                rs.getString("subject_code"),
                                rs.getString("subject_name_fr"),
                                rs.getString("subject_name_ar"),
                                rs.getString("color"),
                                rs.getObject("room_id", UUID.class),
                                rs.getString("room_name"),
                                rs.getTime("starts_at").toLocalTime(),
                                rs.getTime("ends_at").toLocalTime(),
                                null,
                                rs.getObject("attendance_session_id", UUID.class),
                                rs.getString("attendance_status")),
                date,
                tenantId,
                staffId,
                date,
                date.getDayOfWeek().getValue());
    }

    private List<TeacherLesson> classify(
            List<TeacherLesson> lessons, LocalDate date, LocalDate today, LocalTime now) {
        List<TeacherLesson> result = new ArrayList<>(lessons.size());
        boolean nextAssigned = false;
        for (TeacherLesson lesson : lessons) {
            String status;
            if (date.isBefore(today) || (date.equals(today) && !now.isBefore(lesson.endsAt()))) {
                status = "COMPLETED";
            } else if (date.equals(today)
                    && !now.isBefore(lesson.startsAt())
                    && now.isBefore(lesson.endsAt())) {
                status = "IN_PROGRESS";
            } else if (!nextAssigned) {
                status = "NEXT";
                nextAssigned = true;
            } else {
                status = "UPCOMING";
            }
            result.add(lesson.withTimelineStatus(status));
        }
        return List.copyOf(result);
    }

    private TeacherLesson focus(List<TeacherLesson> lessons) {
        return lessons.stream()
                .filter(
                        lesson ->
                                "IN_PROGRESS".equals(lesson.timelineStatus())
                                        || "NEXT".equals(lesson.timelineStatus()))
                .findFirst()
                .orElse(lessons.isEmpty() ? null : lessons.getFirst());
    }

    private List<QuickAction> actions(
            Set<String> permissions, TeacherProfile teacher, LocalDate date, TeacherLesson lesson) {
        return List.of(
                action(
                        "TAKE_ATTENDANCE",
                        permissions.contains("attendance.write"),
                        lesson,
                        attendancePath(teacher, lesson, date, "call")),
                action(
                        "ENTER_GRADES",
                        permissions.contains("grade.write"),
                        lesson,
                        lesson == null
                                ? null
                                : "/app/grading?classroomId="
                                        + lesson.classroomId()
                                        + "&subjectId="
                                        + lesson.subjectId()
                                        + "&teacherStaffId="
                                        + teacher.staffProfileId()
                                        + "&date="
                                        + date),
                action(
                        "ADD_HOMEWORK",
                        permissions.contains("homework.write"),
                        lesson,
                        lesson == null
                                ? null
                                : "/app/homework?classroomId="
                                        + lesson.classroomId()
                                        + "&teacherAssignmentId="
                                        + lesson.assignmentId()
                                        + "&date="
                                        + date
                                        + "&action=add"),
                action(
                        "OBSERVATION",
                        permissions.contains("discipline.write"),
                        lesson,
                        attendancePath(teacher, lesson, date, "observation")),
                action(
                        "MESSAGE",
                        permissions.contains("communication.write"),
                        lesson,
                        lesson == null
                                ? null
                                : "/app/communications?classroomId=" + lesson.classroomId()));
    }

    private QuickAction action(
            String code, boolean permitted, TeacherLesson lesson, String targetPath) {
        if (!permitted) return new QuickAction(code, false, null, "MISSING_PERMISSION");
        if (lesson == null) return new QuickAction(code, false, null, "NO_LESSON_CONTEXT");
        return new QuickAction(code, true, targetPath, null);
    }

    private String attendancePath(
            TeacherProfile teacher, TeacherLesson lesson, LocalDate date, String action) {
        if (lesson == null) return null;
        String path =
                "/app/attendance?classroomId="
                        + lesson.classroomId()
                        + "&teacherStaffId="
                        + teacher.staffProfileId()
                        + "&date="
                        + date
                        + "&startsAt="
                        + lesson.startsAt()
                        + "&endsAt="
                        + lesson.endsAt()
                        + "&subjectCode="
                        + encode(lesson.subjectCode())
                        + "&subjectName="
                        + encode(lesson.subjectNameFr())
                        + "&action="
                        + action;
        return lesson.attendanceSessionId() == null
                ? path
                : path + "&sessionId=" + lesson.attendanceSessionId();
    }

    private LocalDate nextTeachingDate(UUID tenantId, UUID staffId, LocalDate after) {
        List<TeachingPattern> patterns =
                jdbc.query(
                        """
                        SELECT DISTINCT ts.day_of_week,sch.valid_from,sch.valid_to
                        FROM timetable_lesson l
                        JOIN timetable_schedule sch ON sch.id=l.schedule_id
                            AND sch.tenant_id=l.tenant_id
                        JOIN timetable_time_slot ts ON ts.id=l.time_slot_id
                            AND ts.tenant_id=l.tenant_id
                        JOIN timetable_period p ON p.id=ts.period_id AND p.tenant_id=ts.tenant_id
                        WHERE l.tenant_id=? AND l.teacher_staff_id=?
                          AND sch.status='PUBLISHED' AND p.teaching=TRUE AND sch.valid_to>?
                        """,
                        (rs, row) ->
                                new TeachingPattern(
                                        rs.getInt("day_of_week"),
                                        rs.getObject("valid_from", LocalDate.class),
                                        rs.getObject("valid_to", LocalDate.class)),
                        tenantId,
                        staffId,
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

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    public record TeacherDashboard(
            LocalDate date,
            Instant generatedAt,
            TeacherProfile teacher,
            List<TeacherLesson> lessons,
            DaySummary summary,
            LocalDate nextTeachingDate,
            List<QuickAction> quickActions) {}

    public record TeacherProfile(
            UUID staffProfileId,
            String employeeNumber,
            String firstName,
            String lastName,
            String jobTitle) {}

    public record TeacherLesson(
            UUID lessonId,
            UUID assignmentId,
            UUID classroomId,
            String classroomCode,
            String classroomName,
            UUID subjectId,
            String subjectCode,
            String subjectNameFr,
            String subjectNameAr,
            String color,
            UUID roomId,
            String roomName,
            LocalTime startsAt,
            LocalTime endsAt,
            String timelineStatus,
            UUID attendanceSessionId,
            String attendanceStatus) {
        TeacherLesson withTimelineStatus(String status) {
            return new TeacherLesson(
                    lessonId,
                    assignmentId,
                    classroomId,
                    classroomCode,
                    classroomName,
                    subjectId,
                    subjectCode,
                    subjectNameFr,
                    subjectNameAr,
                    color,
                    roomId,
                    roomName,
                    startsAt,
                    endsAt,
                    status,
                    attendanceSessionId,
                    attendanceStatus);
        }
    }

    public record DaySummary(int lessonCount, long completedCount, long attendanceSubmittedCount) {}

    public record QuickAction(
            String code, boolean enabled, String targetPath, String unavailableReason) {}

    private record TeachingPattern(int dayOfWeek, LocalDate validFrom, LocalDate validTo) {}
}
