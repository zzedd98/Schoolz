package ma.schoolz.erp.familyportal.application;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.admissions.AdmissionPortalAccess.PortalIdentity;
import ma.schoolz.erp.enrollment.PortalStudentAccess;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FamilyPortalService {
    private static final ZoneId SCHOOL_ZONE = ZoneId.of("Africa/Casablanca");
    private static final int DATE_LIMIT_DAYS = 400;

    private final JdbcTemplate jdbc;
    private final PortalStudentAccess portalStudents;
    private final Clock clock;

    public FamilyPortalService(JdbcTemplate jdbc, PortalStudentAccess portalStudents, Clock clock) {
        this.jdbc = jdbc;
        this.portalStudents = portalStudents;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public FamilyDashboard dashboard(
            PortalIdentity parent, UUID requestedStudentId, LocalDate requestedDate) {
        LocalDate date = validateDate(requestedDate);
        Set<UUID> linked = portalStudents.linkedStudentIds(parent);
        List<ChildView> children = children(parent);
        if (requestedStudentId != null && !linked.contains(requestedStudentId)) {
            throw new FamilyPortalNotFoundException("Enfant lié introuvable");
        }
        UUID selectedStudentId = selectStudent(parent, requestedStudentId, children, linked);
        ParentAccount account =
                new ParentAccount(
                        parent.accountId(), parent.displayName(), parent.email(), parent.locale());
        if (selectedStudentId == null) {
            return new FamilyDashboard(
                    clock.instant(),
                    date,
                    account,
                    children,
                    null,
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of());
        }

        ChildView selected =
                children.stream()
                        .filter(child -> child.id().equals(selectedStudentId))
                        .findFirst()
                        .orElseThrow(
                                () -> new FamilyPortalNotFoundException("Enfant lié introuvable"));
        List<TimetableLesson> timetable = timetable(parent.tenantId(), selected, date);
        LocalDate nextSchoolDate =
                timetable.isEmpty() && selected.classroomId() != null
                        ? nextTeachingDate(parent.tenantId(), selected.classroomId(), date)
                        : date;
        List<GradeView> grades = grades(parent.tenantId(), selectedStudentId);
        List<ReportCardView> reportCards = reportCards(parent.tenantId(), selectedStudentId);
        List<AbsenceView> absences = absences(parent.tenantId(), selectedStudentId);
        HomeworkView nextHomework = nextHomework(parent.tenantId(), selectedStudentId);
        AttendanceToday attendance = attendance(parent.tenantId(), selectedStudentId, date);
        ChildOverview overview =
                new ChildOverview(
                        attendance,
                        grades.isEmpty() ? null : grades.getFirst(),
                        nextHomework,
                        nextSchoolDate,
                        countOpenHomework(parent.tenantId(), selectedStudentId),
                        countRecentAbsences(
                                parent.tenantId(), selectedStudentId, date.minusDays(30), date),
                        countUnexcusedAbsences(parent.tenantId(), selectedStudentId),
                        countUnreadNotifications(parent, selectedStudentId));
        return new FamilyDashboard(
                clock.instant(),
                date,
                account,
                children,
                selectedStudentId,
                overview,
                timetable,
                grades,
                reportCards,
                absences,
                modules(selected));
    }

    @Transactional
    public void prefer(PortalIdentity parent, PreferenceCommand command) {
        if (command == null || command.studentId() == null) {
            throw new FamilyPortalValidationException("L'enfant préféré est requis");
        }
        if (!portalStudents.linkedStudentIds(parent).contains(command.studentId())) {
            throw new FamilyPortalNotFoundException("Enfant lié introuvable");
        }
        Instant now = clock.instant();
        List<UUID> existing =
                jdbc.query(
                        "SELECT id FROM family_portal_preference WHERE tenant_id=? AND"
                                + " portal_account_id=?",
                        (result, row) -> result.getObject(1, UUID.class),
                        parent.tenantId(),
                        parent.accountId());
        if (existing.isEmpty()) {
            jdbc.update(
                    "INSERT INTO family_portal_preference"
                        + " (id,tenant_id,portal_account_id,preferred_student_id,created_at,updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(),
                    parent.tenantId(),
                    parent.accountId(),
                    command.studentId(),
                    timestamp(now),
                    timestamp(now));
        } else {
            jdbc.update(
                    "UPDATE family_portal_preference SET preferred_student_id=?,updated_at=?"
                            + " WHERE tenant_id=? AND portal_account_id=?",
                    command.studentId(),
                    timestamp(now),
                    parent.tenantId(),
                    parent.accountId());
        }
    }

    @Transactional(readOnly = true)
    public ReportCardDocument reportCard(PortalIdentity parent, UUID studentId, UUID reportCardId) {
        if (!portalStudents.linkedStudentIds(parent).contains(studentId)) {
            throw new FamilyPortalNotFoundException("Enfant lié introuvable");
        }
        List<ReportCardDocument> documents =
                jdbc.query(
                        """
                        SELECT rc.pdf_content,rc.file_name
                        FROM grading_report_card rc
                        JOIN enrollment_portal_student_link link
                          ON link.student_id=rc.student_id AND link.tenant_id=rc.tenant_id
                        WHERE rc.tenant_id=? AND rc.id=? AND rc.student_id=?
                          AND rc.status='PUBLISHED' AND rc.pdf_content IS NOT NULL
                          AND link.portal_account_id=?
                        """,
                        (result, row) ->
                                new ReportCardDocument(
                                        result.getBytes("pdf_content"),
                                        result.getString("file_name")),
                        parent.tenantId(),
                        reportCardId,
                        studentId,
                        parent.accountId());
        if (documents.isEmpty()) {
            throw new FamilyPortalNotFoundException("Bulletin publié introuvable");
        }
        return documents.getFirst();
    }

    private LocalDate validateDate(LocalDate requestedDate) {
        LocalDate today = LocalDate.now(clock.withZone(SCHOOL_ZONE));
        LocalDate date = requestedDate == null ? today : requestedDate;
        if (date.isBefore(today.minusDays(DATE_LIMIT_DAYS))
                || date.isAfter(today.plusDays(DATE_LIMIT_DAYS))) {
            throw new FamilyPortalValidationException(
                    "La date demandée est hors de la période autorisée");
        }
        return date;
    }

    private List<ChildView> children(PortalIdentity parent) {
        return jdbc.query(
                """
                SELECT DISTINCT sp.id,p.first_name,p.last_name,p.birth_date,
                       (SELECT si.identifier_value FROM student_identifier si
                        WHERE si.tenant_id=sp.tenant_id AND si.student_id=sp.id
                        ORDER BY si.primary_identifier DESC,si.created_at LIMIT 1)
                            AS student_number,
                       e.classroom_id,COALESCE(c.name,cg.name) AS classroom_name,
                       CASE WHEN gl.name IS NOT NULL THEN gl.name
                            WHEN cp.id IS NOT NULL THEN 'Préscolaire' END AS grade_level_name,
                       COALESCE(campus.name,childcare_campus.name) AS campus_name,
                       ay.name AS academic_year_name,e.status AS enrollment_status,
                       CASE WHEN cp.id IS NULL THEN FALSE ELSE TRUE END AS childcare
                FROM enrollment_portal_student_link link
                JOIN student_profile sp
                  ON sp.id=link.student_id AND sp.tenant_id=link.tenant_id
                JOIN person p ON p.id=sp.person_id AND p.tenant_id=sp.tenant_id
                LEFT JOIN student_enrollment e
                  ON e.student_id=sp.id AND e.tenant_id=sp.tenant_id AND e.status='ACTIVE'
                LEFT JOIN classroom c ON c.id=e.classroom_id AND c.tenant_id=e.tenant_id
                LEFT JOIN grade_level gl ON gl.id=c.grade_level_id AND gl.tenant_id=c.tenant_id
                LEFT JOIN school_campus campus ON campus.id=e.campus_id AND campus.tenant_id=e.tenant_id
                LEFT JOIN academic_year ay
                  ON ay.id=e.academic_year_id AND ay.tenant_id=e.tenant_id
                LEFT JOIN childcare_profile cp
                  ON cp.student_id=sp.id AND cp.tenant_id=sp.tenant_id AND cp.active=TRUE
                LEFT JOIN childcare_group cg
                  ON cg.id=cp.group_id AND cg.tenant_id=cp.tenant_id
                LEFT JOIN school_campus childcare_campus
                  ON childcare_campus.id=cg.campus_id AND childcare_campus.tenant_id=cg.tenant_id
                WHERE link.tenant_id=? AND link.portal_account_id=? AND sp.status='ACTIVE'
                ORDER BY p.first_name,p.last_name
                """,
                (result, row) -> child(result),
                parent.tenantId(),
                parent.accountId());
    }

    private ChildView child(ResultSet result) throws SQLException {
        return new ChildView(
                result.getObject("id", UUID.class),
                result.getString("first_name"),
                result.getString("last_name"),
                result.getString("first_name") + " " + result.getString("last_name"),
                result.getObject("birth_date", LocalDate.class),
                result.getString("student_number"),
                nullableUuid(result, "classroom_id"),
                result.getString("classroom_name"),
                result.getString("grade_level_name"),
                result.getString("campus_name"),
                result.getString("academic_year_name"),
                result.getString("enrollment_status"),
                result.getBoolean("childcare"));
    }

    private UUID selectStudent(
            PortalIdentity parent, UUID requested, List<ChildView> children, Set<UUID> linked) {
        if (requested != null) return requested;
        List<UUID> preferred =
                jdbc.query(
                        "SELECT preferred_student_id FROM family_portal_preference"
                                + " WHERE tenant_id=? AND portal_account_id=?",
                        (result, row) -> result.getObject(1, UUID.class),
                        parent.tenantId(),
                        parent.accountId());
        if (!preferred.isEmpty()
                && preferred.getFirst() != null
                && linked.contains(preferred.getFirst())) {
            return preferred.getFirst();
        }
        return children.stream()
                .sorted(
                        Comparator.comparing((ChildView child) -> child.classroomId() == null)
                                .thenComparing(ChildView::fullName))
                .map(ChildView::id)
                .findFirst()
                .orElse(null);
    }

    private AttendanceToday attendance(UUID tenantId, UUID studentId, LocalDate date) {
        List<AttendanceRow> rows =
                jdbc.query(
                        """
                        SELECT ar.status,s.subject_name,s.starts_at,s.ends_at
                        FROM attendance_record ar
                        JOIN attendance_session s
                          ON s.id=ar.session_id AND s.tenant_id=ar.tenant_id
                        WHERE ar.tenant_id=? AND ar.student_id=? AND s.session_date=?
                          AND s.status<>'CANCELLED'
                        ORDER BY s.starts_at
                        """,
                        (result, row) ->
                                new AttendanceRow(
                                        result.getString("status"),
                                        result.getString("subject_name"),
                                        result.getObject("starts_at", LocalTime.class),
                                        result.getObject("ends_at", LocalTime.class)),
                        tenantId,
                        studentId,
                        date);
        if (rows.isEmpty()) return new AttendanceToday("NO_SESSION", 0, null);
        String status =
                rows.stream()
                        .map(AttendanceRow::status)
                        .min(Comparator.comparingInt(this::attendancePriority))
                        .orElse("PRESENT");
        AttendanceRow exception =
                rows.stream()
                        .filter(row -> row.status().equals(status))
                        .findFirst()
                        .orElse(rows.getFirst());
        return new AttendanceToday(status, rows.size(), exception.subjectName());
    }

    private int attendancePriority(String status) {
        return switch (status) {
            case "ABSENT" -> 0;
            case "EARLY_DEPARTURE" -> 1;
            case "LATE" -> 2;
            default -> 3;
        };
    }

    private List<TimetableLesson> timetable(UUID tenantId, ChildView child, LocalDate date) {
        if (child.classroomId() == null) return List.of();
        return jdbc.query(
                """
                SELECT l.id,s.id AS subject_id,s.code AS subject_code,s.name_fr,s.name_ar,s.color,
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
                        new TimetableLesson(
                                result.getObject("id", UUID.class),
                                result.getObject("subject_id", UUID.class),
                                result.getString("subject_code"),
                                result.getString("name_fr"),
                                result.getString("name_ar"),
                                result.getString("color"),
                                result.getObject("starts_at", LocalTime.class),
                                result.getObject("ends_at", LocalTime.class),
                                result.getString("room_name"),
                                result.getString("teacher_name")),
                tenantId,
                child.classroomId(),
                date,
                date.getDayOfWeek().getValue());
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
        for (int offset = 1; offset <= DATE_LIMIT_DAYS; offset++) {
            LocalDate candidate = after.plusDays(offset);
            if (patterns.stream().anyMatch(pattern -> pattern.includes(candidate)))
                return candidate;
        }
        return null;
    }

    private List<GradeView> grades(UUID tenantId, UUID studentId) {
        return jdbc.query(
                """
                SELECT g.id,a.id AS assessment_id,a.title,a.assessment_date,a.maximum_score,
                       g.raw_score,g.grade_status,g.teacher_comment,
                       s.id AS subject_id,s.code AS subject_code,s.name_fr,s.name_ar,s.color
                FROM grading_grade g
                JOIN grading_assessment a
                  ON a.id=g.assessment_id AND a.tenant_id=g.tenant_id
                JOIN timetable_subject s
                  ON s.id=a.subject_id AND s.tenant_id=a.tenant_id
                WHERE g.tenant_id=? AND g.student_id=? AND a.status='PUBLISHED'
                ORDER BY a.assessment_date DESC,g.updated_at DESC LIMIT 6
                """,
                (result, row) ->
                        new GradeView(
                                result.getObject("id", UUID.class),
                                result.getObject("assessment_id", UUID.class),
                                result.getString("title"),
                                result.getObject("assessment_date", LocalDate.class),
                                result.getBigDecimal("raw_score"),
                                result.getBigDecimal("maximum_score"),
                                result.getString("grade_status"),
                                result.getString("teacher_comment"),
                                result.getObject("subject_id", UUID.class),
                                result.getString("subject_code"),
                                result.getString("name_fr"),
                                result.getString("name_ar"),
                                result.getString("color")),
                tenantId,
                studentId);
    }

    private HomeworkView nextHomework(UUID tenantId, UUID studentId) {
        List<HomeworkView> rows =
                jdbc.query(
                        """
                        SELECT h.id,h.title,h.instructions,h.due_at,h.estimated_minutes,
                               hs.status,s.code AS subject_code,s.name_fr,s.name_ar,s.color
                        FROM homework_student_status hs
                        JOIN homework_assignment h
                          ON h.id=hs.homework_id AND h.tenant_id=hs.tenant_id
                        JOIN timetable_subject s
                          ON s.id=h.subject_id AND s.tenant_id=h.tenant_id
                        WHERE hs.tenant_id=? AND hs.student_id=? AND hs.status='TO_DO'
                          AND h.status='PUBLISHED' AND h.due_at>=?
                        ORDER BY h.due_at LIMIT 1
                        """,
                        (result, row) ->
                                new HomeworkView(
                                        result.getObject("id", UUID.class),
                                        result.getString("title"),
                                        result.getString("instructions"),
                                        instant(result, "due_at"),
                                        nullableInteger(result, "estimated_minutes"),
                                        result.getString("status"),
                                        result.getString("subject_code"),
                                        result.getString("name_fr"),
                                        result.getString("name_ar"),
                                        result.getString("color")),
                        tenantId,
                        studentId,
                        timestamp(clock.instant()));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private List<ReportCardView> reportCards(UUID tenantId, UUID studentId) {
        return jdbc.query(
                """
                SELECT rc.id,rc.overall_average,rc.class_rank,rc.class_size,rc.version_number,
                       rc.language_mode,rc.file_name,rc.pdf_sha256,rc.published_at,
                       t.name AS term_name,ay.name AS academic_year_name
                FROM grading_report_card rc
                JOIN academic_term t
                  ON t.id=rc.academic_term_id AND t.tenant_id=rc.tenant_id
                JOIN academic_year ay
                  ON ay.id=rc.academic_year_id AND ay.tenant_id=rc.tenant_id
                WHERE rc.tenant_id=? AND rc.student_id=? AND rc.status='PUBLISHED'
                  AND rc.pdf_content IS NOT NULL
                ORDER BY rc.published_at DESC,rc.version_number DESC LIMIT 8
                """,
                (result, row) ->
                        new ReportCardView(
                                result.getObject("id", UUID.class),
                                result.getString("term_name"),
                                result.getString("academic_year_name"),
                                result.getBigDecimal("overall_average"),
                                nullableInteger(result, "class_rank"),
                                nullableInteger(result, "class_size"),
                                result.getInt("version_number"),
                                result.getString("language_mode"),
                                result.getString("file_name"),
                                result.getString("pdf_sha256"),
                                instant(result, "published_at")),
                tenantId,
                studentId);
    }

    private List<AbsenceView> absences(UUID tenantId, UUID studentId) {
        return jdbc.query(
                """
                SELECT a.id,a.occurred_on,a.resolution_status,s.subject_name,
                       r.name_fr AS reason_fr,r.name_ar AS reason_ar,
                       j.id AS justification_id,j.review_status
                FROM attendance_absence a
                JOIN attendance_record ar
                  ON ar.id=a.attendance_record_id AND ar.tenant_id=a.tenant_id
                JOIN attendance_session s
                  ON s.id=ar.session_id AND s.tenant_id=ar.tenant_id
                LEFT JOIN attendance_absence_reason r
                  ON r.id=a.absence_reason_id AND r.tenant_id=a.tenant_id
                LEFT JOIN attendance_excused_absence j
                  ON j.absence_id=a.id AND j.tenant_id=a.tenant_id
                WHERE a.tenant_id=? AND a.student_id=?
                ORDER BY a.occurred_on DESC,a.created_at DESC LIMIT 6
                """,
                (result, row) ->
                        new AbsenceView(
                                result.getObject("id", UUID.class),
                                result.getObject("occurred_on", LocalDate.class),
                                result.getString("resolution_status"),
                                result.getString("subject_name"),
                                result.getString("reason_fr"),
                                result.getString("reason_ar"),
                                nullableUuid(result, "justification_id"),
                                result.getString("review_status")),
                tenantId,
                studentId);
    }

    private int countOpenHomework(UUID tenantId, UUID studentId) {
        return count(
                """
                SELECT count(*) FROM homework_student_status hs
                JOIN homework_assignment h
                  ON h.id=hs.homework_id AND h.tenant_id=hs.tenant_id
                WHERE hs.tenant_id=? AND hs.student_id=? AND hs.status='TO_DO'
                  AND h.status='PUBLISHED'
                """,
                tenantId,
                studentId);
    }

    private int countRecentAbsences(UUID tenantId, UUID studentId, LocalDate from, LocalDate to) {
        return count(
                "SELECT count(*) FROM attendance_absence WHERE tenant_id=? AND student_id=?"
                        + " AND occurred_on BETWEEN ? AND ?",
                tenantId,
                studentId,
                from,
                to);
    }

    private int countUnexcusedAbsences(UUID tenantId, UUID studentId) {
        return count(
                "SELECT count(*) FROM attendance_absence WHERE tenant_id=? AND student_id=?"
                        + " AND resolution_status IN ('UNEXCUSED','REJECTED')",
                tenantId,
                studentId);
    }

    private int countUnreadNotifications(PortalIdentity parent, UUID studentId) {
        int attendance =
                count(
                        "SELECT count(*) FROM attendance_parent_notification WHERE tenant_id=?"
                                + " AND portal_account_id=? AND student_id=? AND status='UNREAD'",
                        parent.tenantId(),
                        parent.accountId(),
                        studentId);
        int communications =
                count(
                        "SELECT count(*) FROM communication_notification WHERE tenant_id=?"
                                + " AND recipient_portal_account_id=? AND read_at IS NULL"
                                + " AND (student_id IS NULL OR student_id=?)",
                        parent.tenantId(),
                        parent.accountId(),
                        studentId);
        return attendance + communications;
    }

    private int count(String sql, Object... parameters) {
        Long value = jdbc.queryForObject(sql, Long.class, parameters);
        return value == null ? 0 : Math.toIntExact(value);
    }

    private List<ModuleAccess> modules(ChildView child) {
        List<ModuleAccess> result = new ArrayList<>();
        result.add(new ModuleAccess("SCHOOLING", true, "#schooling", null));
        result.add(new ModuleAccess("TIMETABLE", child.classroomId() != null, "#timetable", null));
        result.add(new ModuleAccess("HOMEWORK", child.classroomId() != null, "/homework", null));
        result.add(new ModuleAccess("GRADES", child.classroomId() != null, "#grades", null));
        result.add(
                new ModuleAccess(
                        "REPORT_CARDS", child.classroomId() != null, "#report-cards", null));
        result.add(
                new ModuleAccess("ATTENDANCE", child.classroomId() != null, "/attendance", null));
        result.add(
                new ModuleAccess(
                        "JUSTIFICATION", child.classroomId() != null, "/attendance", null));
        result.add(new ModuleAccess("CHILDCARE", child.childcare(), "/childcare", null));
        result.add(new ModuleAccess("FINANCE", true, "/finance", null));
        result.add(new ModuleAccess("MESSAGES", true, "/messages", null));
        result.add(new ModuleAccess("DOCUMENTS", true, "/documents", null));
        result.add(new ModuleAccess("TRANSPORT", true, "/transport", null));
        result.add(new ModuleAccess("ACTIVITIES", true, "/activities", null));
        result.add(new ModuleAccess("LIBRARY", true, "/library", null));
        return List.copyOf(result);
    }

    private static UUID nullableUuid(ResultSet result, String column) throws SQLException {
        return result.getObject(column, UUID.class);
    }

    private static Integer nullableInteger(ResultSet result, String column) throws SQLException {
        int value = result.getInt(column);
        return result.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        return result.getTimestamp(column).toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    public record PreferenceCommand(UUID studentId) {}

    public record ParentAccount(UUID id, String displayName, String email, String locale) {}

    public record ChildView(
            UUID id,
            String firstName,
            String lastName,
            String fullName,
            LocalDate birthDate,
            String studentNumber,
            UUID classroomId,
            String classroomName,
            String gradeLevelName,
            String campusName,
            String academicYearName,
            String enrollmentStatus,
            boolean childcare) {}

    public record AttendanceToday(String status, int sessionCount, String exceptionSubject) {}

    public record GradeView(
            UUID id,
            UUID assessmentId,
            String title,
            LocalDate assessmentDate,
            BigDecimal score,
            BigDecimal maximumScore,
            String status,
            String teacherComment,
            UUID subjectId,
            String subjectCode,
            String subjectNameFr,
            String subjectNameAr,
            String color) {}

    public record HomeworkView(
            UUID id,
            String title,
            String instructions,
            Instant dueAt,
            Integer estimatedMinutes,
            String status,
            String subjectCode,
            String subjectNameFr,
            String subjectNameAr,
            String color) {}

    public record ChildOverview(
            AttendanceToday attendanceToday,
            GradeView latestGrade,
            HomeworkView nextHomework,
            LocalDate nextSchoolDate,
            int openHomeworkCount,
            int absenceCount30Days,
            int unexcusedAbsenceCount,
            int unreadNotificationCount) {}

    public record TimetableLesson(
            UUID id,
            UUID subjectId,
            String subjectCode,
            String subjectNameFr,
            String subjectNameAr,
            String color,
            LocalTime startsAt,
            LocalTime endsAt,
            String roomName,
            String teacherName) {}

    public record ReportCardView(
            UUID id,
            String termName,
            String academicYearName,
            BigDecimal overallAverage,
            Integer classRank,
            Integer classSize,
            int versionNumber,
            String languageMode,
            String fileName,
            String sha256,
            Instant publishedAt) {}

    public record AbsenceView(
            UUID id,
            LocalDate date,
            String resolutionStatus,
            String subjectName,
            String reasonFr,
            String reasonAr,
            UUID justificationId,
            String justificationStatus) {}

    public record ModuleAccess(
            String code, boolean available, String targetPath, Integer availableFromMilestone) {}

    public record FamilyDashboard(
            Instant generatedAt,
            LocalDate date,
            ParentAccount account,
            List<ChildView> children,
            UUID selectedStudentId,
            ChildOverview overview,
            List<TimetableLesson> timetable,
            List<GradeView> grades,
            List<ReportCardView> reportCards,
            List<AbsenceView> absences,
            List<ModuleAccess> modules) {}

    public record ReportCardDocument(byte[] content, String fileName) {}

    private record AttendanceRow(
            String status, String subjectName, LocalTime startsAt, LocalTime endsAt) {}

    private record TeachingPattern(int dayOfWeek, LocalDate validFrom, LocalDate validTo) {
        boolean includes(LocalDate date) {
            return date.getDayOfWeek().getValue() == dayOfWeek
                    && !date.isBefore(validFrom)
                    && !date.isAfter(validTo);
        }
    }
}
