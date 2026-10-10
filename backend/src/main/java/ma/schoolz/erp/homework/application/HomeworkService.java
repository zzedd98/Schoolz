package ma.schoolz.erp.homework.application;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.admissions.AdmissionPortalAccess.PortalIdentity;
import ma.schoolz.erp.enrollment.PortalStudentAccess;
import ma.schoolz.erp.foundation.security.FileSecurityScanner;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.identity.IdentityAuditTrail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HomeworkService {
    private static final long MAX_ATTACHMENT_SIZE = 10L * 1024 * 1024;
    private static final Set<String> ATTACHMENT_TYPES =
            Set.of(
                    "application/pdf",
                    "image/jpeg",
                    "image/png",
                    "text/plain",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation");
    private static final Set<String> STUDENT_STATUSES = Set.of("TO_DO", "DONE");
    private static final ZoneId SCHOOL_ZONE = ZoneId.of("Africa/Casablanca");

    private final JdbcTemplate jdbc;
    private final PortalStudentAccess portalStudents;
    private final IdentityAuditTrail audit;
    private final FileSecurityScanner fileSecurity;
    private final Clock clock;

    public HomeworkService(
            JdbcTemplate jdbc,
            PortalStudentAccess portalStudents,
            IdentityAuditTrail audit,
            FileSecurityScanner fileSecurity,
            Clock clock) {
        this.jdbc = jdbc;
        this.portalStudents = portalStudents;
        this.audit = audit;
        this.fileSecurity = fileSecurity;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('homework.read')")
    public ReferenceData references(SchoolPrincipal actor) {
        List<TermOption> terms =
                jdbc.query(
                        """
                        SELECT t.id,t.academic_year_id,t.code,t.name,t.starts_on,t.ends_on
                        FROM academic_term t
                        JOIN academic_year y ON y.id=t.academic_year_id AND y.tenant_id=t.tenant_id
                        WHERE t.tenant_id=?
                        ORDER BY y.starts_on DESC,t.sequence_number
                        """,
                        (rs, row) ->
                                new TermOption(
                                        uuid(rs, "id"),
                                        uuid(rs, "academic_year_id"),
                                        rs.getString("code"),
                                        rs.getString("name"),
                                        rs.getObject("starts_on", LocalDate.class),
                                        rs.getObject("ends_on", LocalDate.class)),
                        actor.tenantId());
        List<ClassroomOption> classrooms =
                jdbc.query(
                        """
                        SELECT c.id,c.academic_year_id,c.code,c.name,gl.name AS grade_level_name,
                               sl.name AS school_level_name
                        FROM classroom c
                        JOIN grade_level gl ON gl.id=c.grade_level_id AND gl.tenant_id=c.tenant_id
                        JOIN school_level sl ON sl.id=gl.school_level_id AND sl.tenant_id=gl.tenant_id
                        WHERE c.tenant_id=? AND c.active=TRUE
                        ORDER BY sl.sequence_number,gl.sequence_number,c.name
                        """,
                        (rs, row) ->
                                new ClassroomOption(
                                        uuid(rs, "id"),
                                        uuid(rs, "academic_year_id"),
                                        rs.getString("code"),
                                        rs.getString("name"),
                                        rs.getString("grade_level_name"),
                                        rs.getString("school_level_name")),
                        actor.tenantId());
        List<AssignmentOption> assignments =
                jdbc.query(
                        assignmentSql()
                                + " WHERE a.tenant_id=? AND a.active=TRUE AND s.status IN"
                                + " ('DRAFT','PUBLISHED') ORDER BY c.name,sub.name_fr,p.last_name",
                        HomeworkService::assignmentOption,
                        actor.tenantId());
        return new ReferenceData(terms, classrooms, assignments);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('homework.read')")
    public Dashboard dashboard(
            SchoolPrincipal actor,
            UUID classroomId,
            UUID academicTermId,
            LocalDate from,
            LocalDate to) {
        ClassTermContext context = classTerm(actor.tenantId(), classroomId, academicTermId);
        DateRange range = range(from, to, context.startsOn(), context.endsOn());
        List<LessonContentView> lessons =
                lessonContents(
                        actor.tenantId(), classroomId, academicTermId, range.from(), range.to());
        List<HomeworkView> assignments =
                homework(actor.tenantId(), classroomId, academicTermId, range.from(), range.to());
        int published =
                (int) assignments.stream().filter(item -> !"DRAFT".equals(item.status())).count();
        int totalStudents =
                jdbc.queryForObject(
                        """
                        SELECT count(*) FROM student_enrollment e
                        WHERE e.tenant_id=? AND e.classroom_id=? AND e.academic_year_id=?
                          AND e.status='ACTIVE'
                        """,
                        Integer.class,
                        actor.tenantId(),
                        classroomId,
                        context.academicYearId());
        return new Dashboard(
                context.classroomId(),
                context.classroomName(),
                context.termId(),
                context.termName(),
                range.from(),
                range.to(),
                totalStudents,
                lessons,
                assignments,
                published);
    }

    @Transactional
    @PreAuthorize("hasAuthority('homework.write')")
    public LessonContentView createLessonContent(
            SchoolPrincipal actor, LessonContentCommand command, String ipAddress) {
        validateLesson(command);
        AssignmentOption assignment = assignment(actor.tenantId(), command.teacherAssignmentId());
        TermContext term =
                term(actor.tenantId(), command.academicTermId(), assignment.academicYearId());
        requireDateInTerm(command.lessonDate(), term, "La date du cours");
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                """
                INSERT INTO lesson_content
                    (id,tenant_id,teacher_assignment_id,classroom_id,academic_term_id,subject_id,
                     teacher_staff_id,lesson_date,title,summary,content,status,created_by_user_id,
                     created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?)
                """,
                id,
                actor.tenantId(),
                assignment.id(),
                assignment.classroomId(),
                command.academicTermId(),
                assignment.subjectId(),
                assignment.teacherStaffId(),
                command.lessonDate(),
                clean(command.title()),
                optional(command.summary()),
                clean(command.content()),
                actor.accountId(),
                timestamp(now),
                timestamp(now));
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "homework.lesson-content.created",
                "LessonContent",
                id.toString(),
                ipAddress,
                Map.of(
                        "classroomId",
                        assignment.classroomId(),
                        "subjectId",
                        assignment.subjectId()),
                null,
                Map.of("status", "DRAFT", "lessonDate", command.lessonDate()));
        return lessonContent(actor.tenantId(), id);
    }

    @Transactional
    @PreAuthorize("hasAuthority('homework.write')")
    public LessonContentView updateLessonContent(
            SchoolPrincipal actor,
            UUID lessonContentId,
            LessonContentCommand command,
            String ipAddress) {
        validateLesson(command);
        LessonHeader current = requireDraftLesson(actor.tenantId(), lessonContentId);
        AssignmentOption assignment = assignment(actor.tenantId(), command.teacherAssignmentId());
        if (!assignment.classroomId().equals(current.classroomId())
                || !assignment.subjectId().equals(current.subjectId())
                || !assignment.teacherStaffId().equals(current.teacherStaffId())) {
            throw new HomeworkConflictException(
                    "L'affectation d'un contenu de cours existant ne peut pas être remplacée");
        }
        TermContext term =
                term(actor.tenantId(), command.academicTermId(), assignment.academicYearId());
        requireDateInTerm(command.lessonDate(), term, "La date du cours");
        Instant now = clock.instant();
        jdbc.update(
                """
                UPDATE lesson_content SET academic_term_id=?,lesson_date=?,title=?,summary=?,content=?,
                       updated_at=?,version=version+1
                WHERE tenant_id=? AND id=? AND status='DRAFT'
                """,
                command.academicTermId(),
                command.lessonDate(),
                clean(command.title()),
                optional(command.summary()),
                clean(command.content()),
                timestamp(now),
                actor.tenantId(),
                lessonContentId);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "homework.lesson-content.updated",
                "LessonContent",
                lessonContentId.toString(),
                ipAddress,
                Map.of(),
                Map.of("lessonDate", current.lessonDate(), "title", current.title()),
                Map.of("lessonDate", command.lessonDate(), "title", clean(command.title())));
        return lessonContent(actor.tenantId(), lessonContentId);
    }

    @Transactional
    @PreAuthorize("hasAuthority('homework.publish')")
    public LessonContentView publishLessonContent(
            SchoolPrincipal actor, UUID lessonContentId, String ipAddress) {
        requireDraftLesson(actor.tenantId(), lessonContentId);
        Instant now = clock.instant();
        jdbc.update(
                """
                UPDATE lesson_content SET status='PUBLISHED',published_at=?,updated_at=?,
                       version=version+1 WHERE tenant_id=? AND id=? AND status='DRAFT'
                """,
                timestamp(now),
                timestamp(now),
                actor.tenantId(),
                lessonContentId);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "homework.lesson-content.published",
                "LessonContent",
                lessonContentId.toString(),
                ipAddress,
                Map.of(),
                Map.of("status", "DRAFT"),
                Map.of("status", "PUBLISHED"));
        return lessonContent(actor.tenantId(), lessonContentId);
    }

    @Transactional
    @PreAuthorize("hasAuthority('homework.write')")
    public HomeworkView createHomework(
            SchoolPrincipal actor, HomeworkCommand command, String ipAddress) {
        validateHomework(command);
        AssignmentOption assignment = assignment(actor.tenantId(), command.teacherAssignmentId());
        TermContext term =
                term(actor.tenantId(), command.academicTermId(), assignment.academicYearId());
        requireDateInTerm(command.assignedOn(), term, "La date d'attribution");
        requireDateInTerm(
                command.dueAt().atZoneSameInstant(SCHOOL_ZONE).toLocalDate(), term, "L'échéance");
        if (!command.dueAt()
                .toInstant()
                .isAfter(command.assignedOn().atStartOfDay(SCHOOL_ZONE).toInstant())) {
            throw new HomeworkValidationException("L'échéance doit suivre la date d'attribution");
        }
        if (command.lessonContentId() != null) {
            LessonHeader lesson = lesson(actor.tenantId(), command.lessonContentId());
            if (!lesson.classroomId().equals(assignment.classroomId())
                    || !lesson.subjectId().equals(assignment.subjectId())
                    || !lesson.academicTermId().equals(command.academicTermId())) {
                throw new HomeworkConflictException(
                        "Le contenu de cours lié ne correspond pas à la classe, la matière et la"
                                + " période");
            }
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                """
                INSERT INTO homework_assignment
                    (id,tenant_id,teacher_assignment_id,lesson_content_id,classroom_id,
                     academic_term_id,subject_id,teacher_staff_id,title,instructions,assigned_on,
                     due_at,estimated_minutes,status,created_by_user_id,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?)
                """,
                id,
                actor.tenantId(),
                assignment.id(),
                command.lessonContentId(),
                assignment.classroomId(),
                command.academicTermId(),
                assignment.subjectId(),
                assignment.teacherStaffId(),
                clean(command.title()),
                clean(command.instructions()),
                command.assignedOn(),
                timestamp(command.dueAt().toInstant()),
                command.estimatedMinutes(),
                actor.accountId(),
                timestamp(now),
                timestamp(now));
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "homework.assignment.created",
                "Homework",
                id.toString(),
                ipAddress,
                Map.of(
                        "classroomId",
                        assignment.classroomId(),
                        "subjectId",
                        assignment.subjectId()),
                null,
                Map.of("status", "DRAFT", "dueAt", command.dueAt().toString()));
        return homework(actor.tenantId(), id);
    }

    @Transactional
    @PreAuthorize("hasAuthority('homework.write')")
    public HomeworkView updateHomework(
            SchoolPrincipal actor, UUID homeworkId, HomeworkCommand command, String ipAddress) {
        validateHomework(command);
        HomeworkHeader current = requireDraftHomework(actor.tenantId(), homeworkId);
        AssignmentOption assignment = assignment(actor.tenantId(), command.teacherAssignmentId());
        if (!assignment.classroomId().equals(current.classroomId())
                || !assignment.subjectId().equals(current.subjectId())
                || !assignment.teacherStaffId().equals(current.teacherStaffId())) {
            throw new HomeworkConflictException("L'affectation d'un devoir publié est stable");
        }
        TermContext term =
                term(actor.tenantId(), command.academicTermId(), assignment.academicYearId());
        requireDateInTerm(command.assignedOn(), term, "La date d'attribution");
        requireDateInTerm(
                command.dueAt().atZoneSameInstant(SCHOOL_ZONE).toLocalDate(), term, "L'échéance");
        if (command.lessonContentId() != null) {
            LessonHeader lesson = lesson(actor.tenantId(), command.lessonContentId());
            if (!lesson.classroomId().equals(current.classroomId())
                    || !lesson.subjectId().equals(current.subjectId())
                    || !lesson.academicTermId().equals(command.academicTermId())) {
                throw new HomeworkConflictException("Le contenu de cours lié est incompatible");
            }
        }
        Instant now = clock.instant();
        jdbc.update(
                """
                UPDATE homework_assignment
                SET academic_term_id=?,lesson_content_id=?,title=?,instructions=?,assigned_on=?,
                    due_at=?,estimated_minutes=?,updated_at=?,version=version+1
                WHERE tenant_id=? AND id=? AND status='DRAFT'
                """,
                command.academicTermId(),
                command.lessonContentId(),
                clean(command.title()),
                clean(command.instructions()),
                command.assignedOn(),
                timestamp(command.dueAt().toInstant()),
                command.estimatedMinutes(),
                timestamp(now),
                actor.tenantId(),
                homeworkId);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "homework.assignment.updated",
                "Homework",
                homeworkId.toString(),
                ipAddress,
                Map.of(),
                Map.of("title", current.title(), "dueAt", current.dueAt().toString()),
                Map.of("title", clean(command.title()), "dueAt", command.dueAt().toString()));
        return homework(actor.tenantId(), homeworkId);
    }

    @Transactional
    @PreAuthorize("hasAuthority('homework.publish')")
    public HomeworkView publishHomework(SchoolPrincipal actor, UUID homeworkId, String ipAddress) {
        HomeworkHeader current = requireDraftHomework(actor.tenantId(), homeworkId);
        if (current.lessonContentId() != null
                && !"PUBLISHED"
                        .equals(lesson(actor.tenantId(), current.lessonContentId()).status())) {
            throw new HomeworkConflictException(
                    "Publiez le contenu de cours lié avant de publier ce devoir");
        }
        Instant now = clock.instant();
        jdbc.update(
                """
                UPDATE homework_assignment SET status='PUBLISHED',published_at=?,updated_at=?,
                       version=version+1 WHERE tenant_id=? AND id=? AND status='DRAFT'
                """,
                timestamp(now),
                timestamp(now),
                actor.tenantId(),
                homeworkId);
        List<UUID> students =
                jdbc.query(
                        """
                        SELECT e.student_id FROM student_enrollment e
                        JOIN classroom c ON c.id=e.classroom_id AND c.tenant_id=e.tenant_id
                        WHERE e.tenant_id=? AND e.classroom_id=? AND e.status='ACTIVE'
                          AND c.academic_year_id=e.academic_year_id
                        ORDER BY e.student_id
                        """,
                        (rs, row) -> uuid(rs, "student_id"),
                        actor.tenantId(),
                        current.classroomId());
        for (UUID studentId : students) {
            jdbc.update(
                    """
                    INSERT INTO homework_student_status
                        (id,tenant_id,homework_id,student_id,status,updated_at)
                    VALUES (?, ?, ?, ?, 'TO_DO', ?)
                    """,
                    UUID.randomUUID(),
                    actor.tenantId(),
                    homeworkId,
                    studentId,
                    timestamp(now));
        }
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "homework.assignment.published",
                "Homework",
                homeworkId.toString(),
                ipAddress,
                Map.of("students", students.size()),
                Map.of("status", "DRAFT"),
                Map.of("status", "PUBLISHED"));
        return homework(actor.tenantId(), homeworkId);
    }

    @Transactional
    @PreAuthorize("hasAuthority('homework.publish')")
    public HomeworkView closeHomework(SchoolPrincipal actor, UUID homeworkId, String ipAddress) {
        HomeworkHeader current = homeworkHeader(actor.tenantId(), homeworkId);
        if (!"PUBLISHED".equals(current.status())) {
            throw new HomeworkConflictException("Seul un devoir publié peut être clôturé");
        }
        Instant now = clock.instant();
        jdbc.update(
                """
                UPDATE homework_assignment SET status='CLOSED',closed_at=?,updated_at=?,
                       version=version+1 WHERE tenant_id=? AND id=? AND status='PUBLISHED'
                """,
                timestamp(now),
                timestamp(now),
                actor.tenantId(),
                homeworkId);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "homework.assignment.closed",
                "Homework",
                homeworkId.toString(),
                ipAddress,
                Map.of(),
                Map.of("status", "PUBLISHED"),
                Map.of("status", "CLOSED"));
        return homework(actor.tenantId(), homeworkId);
    }

    @Transactional
    @PreAuthorize("hasAuthority('homework.write')")
    public AttachmentView uploadAttachment(
            SchoolPrincipal actor,
            String ownerType,
            UUID ownerId,
            String originalFileName,
            String contentType,
            byte[] bytes,
            String ipAddress) {
        String normalizedOwner = normalizeOwner(ownerType);
        validateAttachment(originalFileName, contentType, bytes);
        fileSecurity.inspect(
                originalFileName, contentType, bytes, ATTACHMENT_TYPES, MAX_ATTACHMENT_SIZE);
        if ("LESSON_CONTENT".equals(normalizedOwner)) {
            requireDraftLesson(actor.tenantId(), ownerId);
        } else {
            requireDraftHomework(actor.tenantId(), ownerId);
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        String safeName = safeFileName(originalFileName);
        String normalizedType = contentType.strip().toLowerCase(Locale.ROOT);
        jdbc.update(
                """
                INSERT INTO homework_attachment
                    (id,tenant_id,lesson_content_id,homework_id,original_file_name,content_type,
                     size_bytes,sha256,binary_data,created_by_user_id,created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                actor.tenantId(),
                "LESSON_CONTENT".equals(normalizedOwner) ? ownerId : null,
                "HOMEWORK".equals(normalizedOwner) ? ownerId : null,
                safeName,
                normalizedType,
                bytes.length,
                sha256(bytes),
                bytes,
                actor.accountId(),
                timestamp(now));
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "homework.attachment.uploaded",
                "HomeworkAttachment",
                id.toString(),
                ipAddress,
                Map.of("ownerType", normalizedOwner, "ownerId", ownerId, "size", bytes.length),
                null,
                Map.of("fileName", safeName, "sha256", sha256(bytes)));
        return attachment(actor.tenantId(), id);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('homework.read')")
    public AttachmentContent attachment(SchoolPrincipal actor, UUID attachmentId) {
        return attachmentContent(actor.tenantId(), attachmentId, false, null);
    }

    @Transactional(readOnly = true)
    public ParentDashboard parentDashboard(
            PortalIdentity parent, LocalDate from, LocalDate to, UUID selectedStudentId) {
        Set<UUID> linked = portalStudents.linkedStudentIds(parent);
        if (selectedStudentId != null && !linked.contains(selectedStudentId)) {
            throw new HomeworkNotFoundException("Élève lié introuvable");
        }
        LocalDate today = LocalDate.now(clock.withZone(SCHOOL_ZONE));
        DateRange range = range(from, to, today.minusDays(7), today.plusDays(60));
        List<ChildOption> children =
                jdbc.query(
                        """
                        SELECT DISTINCT sp.id,CONCAT(p.first_name,' ',p.last_name) AS student_name,
                               c.id AS classroom_id,c.name AS classroom_name
                        FROM enrollment_portal_student_link link
                        JOIN student_profile sp ON sp.id=link.student_id AND sp.tenant_id=link.tenant_id
                        JOIN person p ON p.id=sp.person_id AND p.tenant_id=sp.tenant_id
                        JOIN student_enrollment e ON e.student_id=sp.id AND e.tenant_id=sp.tenant_id
                        JOIN classroom c ON c.id=e.classroom_id AND c.tenant_id=e.tenant_id
                        WHERE link.tenant_id=? AND link.portal_account_id=? AND e.status='ACTIVE'
                        ORDER BY student_name
                        """,
                        (rs, row) ->
                                new ChildOption(
                                        uuid(rs, "id"),
                                        rs.getString("student_name"),
                                        uuid(rs, "classroom_id"),
                                        rs.getString("classroom_name")),
                        parent.tenantId(),
                        parent.accountId());
        List<ParentLesson> lessons = parentLessons(parent, range, selectedStudentId);
        List<ParentHomework> assignments = parentHomework(parent, range, selectedStudentId);
        return new ParentDashboard(
                clock.instant(), range.from(), range.to(), children, lessons, assignments);
    }

    @Transactional
    public ParentHomework markStatus(
            PortalIdentity parent,
            UUID homeworkId,
            StudentStatusCommand command,
            String ipAddress) {
        if (command == null
                || command.studentId() == null
                || command.status() == null
                || !STUDENT_STATUSES.contains(command.status().strip().toUpperCase(Locale.ROOT))) {
            throw new HomeworkValidationException("Statut de devoir invalide");
        }
        String status = command.status().strip().toUpperCase(Locale.ROOT);
        List<String> current =
                jdbc.query(
                        """
                        SELECT hs.status FROM homework_student_status hs
                        JOIN enrollment_portal_student_link link
                          ON link.student_id=hs.student_id AND link.tenant_id=hs.tenant_id
                        JOIN homework_assignment h
                          ON h.id=hs.homework_id AND h.tenant_id=hs.tenant_id
                        WHERE hs.tenant_id=? AND hs.homework_id=? AND hs.student_id=?
                          AND link.portal_account_id=? AND h.status IN ('PUBLISHED','CLOSED')
                        """,
                        (rs, row) -> rs.getString(1),
                        parent.tenantId(),
                        homeworkId,
                        command.studentId(),
                        parent.accountId());
        if (current.isEmpty()) throw new HomeworkNotFoundException("Devoir lié introuvable");
        Instant now = clock.instant();
        jdbc.update(
                """
                UPDATE homework_student_status SET status=?,completed_at=?,
                       marked_by_portal_account_id=?,updated_at=?
                WHERE tenant_id=? AND homework_id=? AND student_id=?
                """,
                status,
                "DONE".equals(status) ? timestamp(now) : null,
                parent.accountId(),
                timestamp(now),
                parent.tenantId(),
                homeworkId,
                command.studentId());
        audit.record(
                parent.tenantId(),
                null,
                "homework.assignment.status-marked",
                "Homework",
                homeworkId.toString(),
                ipAddress,
                Map.of("portalAccountId", parent.accountId(), "studentId", command.studentId()),
                Map.of("status", current.getFirst()),
                Map.of("status", status));
        DateRange broad = new DateRange(LocalDate.of(2000, 1, 1), LocalDate.of(2100, 1, 1));
        return parentHomework(parent, broad, command.studentId()).stream()
                .filter(item -> item.id().equals(homeworkId))
                .findFirst()
                .orElseThrow(() -> new HomeworkNotFoundException("Devoir lié introuvable"));
    }

    @Transactional(readOnly = true)
    public AttachmentContent parentAttachment(PortalIdentity parent, UUID attachmentId) {
        return attachmentContent(parent.tenantId(), attachmentId, true, parent.accountId());
    }

    private List<LessonContentView> lessonContents(
            UUID tenantId, UUID classroomId, UUID termId, LocalDate from, LocalDate to) {
        return jdbc.query(
                lessonSql()
                        + " WHERE lc.tenant_id=? AND lc.classroom_id=? AND lc.academic_term_id=?"
                        + " AND lc.lesson_date BETWEEN ? AND ? ORDER BY lc.lesson_date"
                        + " DESC,lc.created_at DESC",
                (rs, row) ->
                        lessonView(rs, attachments(tenantId, "LESSON_CONTENT", uuid(rs, "id"))),
                tenantId,
                classroomId,
                termId,
                from,
                to);
    }

    private LessonContentView lessonContent(UUID tenantId, UUID id) {
        List<LessonContentView> rows =
                jdbc.query(
                        lessonSql() + " WHERE lc.tenant_id=? AND lc.id=?",
                        (rs, row) -> lessonView(rs, attachments(tenantId, "LESSON_CONTENT", id)),
                        tenantId,
                        id);
        if (rows.isEmpty()) throw new HomeworkNotFoundException("Contenu de cours introuvable");
        return rows.getFirst();
    }

    private List<HomeworkView> homework(
            UUID tenantId, UUID classroomId, UUID termId, LocalDate from, LocalDate to) {
        Instant start = from.atStartOfDay(SCHOOL_ZONE).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(SCHOOL_ZONE).toInstant();
        return jdbc.query(
                homeworkSql()
                        + " WHERE h.tenant_id=? AND h.classroom_id=? AND h.academic_term_id=?"
                        + " AND h.due_at>=? AND h.due_at<? ORDER BY h.due_at,h.created_at",
                (rs, row) -> homeworkView(rs, attachments(tenantId, "HOMEWORK", uuid(rs, "id"))),
                tenantId,
                classroomId,
                termId,
                timestamp(start),
                timestamp(end));
    }

    private HomeworkView homework(UUID tenantId, UUID id) {
        List<HomeworkView> rows =
                jdbc.query(
                        homeworkSql() + " WHERE h.tenant_id=? AND h.id=?",
                        (rs, row) -> homeworkView(rs, attachments(tenantId, "HOMEWORK", id)),
                        tenantId,
                        id);
        if (rows.isEmpty()) throw new HomeworkNotFoundException("Devoir introuvable");
        return rows.getFirst();
    }

    private List<AttachmentView> attachments(UUID tenantId, String ownerType, UUID ownerId) {
        String column = "LESSON_CONTENT".equals(ownerType) ? "lesson_content_id" : "homework_id";
        return jdbc.query(
                "SELECT id,original_file_name,content_type,size_bytes,sha256,created_at FROM"
                        + " homework_attachment WHERE tenant_id=? AND "
                        + column
                        + "=? ORDER BY created_at",
                HomeworkService::attachmentView,
                tenantId,
                ownerId);
    }

    private AttachmentView attachment(UUID tenantId, UUID attachmentId) {
        List<AttachmentView> rows =
                jdbc.query(
                        "SELECT id,original_file_name,content_type,size_bytes,sha256,created_at"
                                + " FROM homework_attachment WHERE tenant_id=? AND id=?",
                        HomeworkService::attachmentView,
                        tenantId,
                        attachmentId);
        if (rows.isEmpty()) throw new HomeworkNotFoundException("Pièce jointe introuvable");
        return rows.getFirst();
    }

    private AttachmentContent attachmentContent(
            UUID tenantId, UUID attachmentId, boolean parentAccess, UUID portalAccountId) {
        String access =
                parentAccess
                        ? """
                        AND (
                          (a.homework_id IS NOT NULL AND EXISTS (
                            SELECT 1 FROM homework_student_status hs
                            JOIN enrollment_portal_student_link link
                              ON link.student_id=hs.student_id AND link.tenant_id=hs.tenant_id
                            JOIN homework_assignment h
                              ON h.id=hs.homework_id AND h.tenant_id=hs.tenant_id
                            WHERE hs.tenant_id=a.tenant_id AND hs.homework_id=a.homework_id
                              AND link.portal_account_id=? AND h.status IN ('PUBLISHED','CLOSED')
                          ))
                          OR
                          (a.lesson_content_id IS NOT NULL AND EXISTS (
                            SELECT 1 FROM lesson_content lc
                            JOIN student_enrollment e
                              ON e.classroom_id=lc.classroom_id AND e.tenant_id=lc.tenant_id
                            JOIN enrollment_portal_student_link link
                              ON link.student_id=e.student_id AND link.tenant_id=e.tenant_id
                            WHERE lc.id=a.lesson_content_id AND lc.tenant_id=a.tenant_id
                              AND lc.status='PUBLISHED' AND e.status='ACTIVE'
                              AND link.portal_account_id=?
                          ))
                        )
                        """
                        : "";
        Object[] params =
                parentAccess
                        ? new Object[] {tenantId, attachmentId, portalAccountId, portalAccountId}
                        : new Object[] {tenantId, attachmentId};
        List<AttachmentContent> rows =
                jdbc.query(
                        "SELECT a.original_file_name,a.content_type,a.binary_data,a.sha256 FROM"
                                + " homework_attachment a WHERE a.tenant_id=? AND a.id=? "
                                + access,
                        (rs, row) ->
                                new AttachmentContent(
                                        rs.getBytes("binary_data"),
                                        rs.getString("original_file_name"),
                                        rs.getString("content_type"),
                                        rs.getString("sha256")),
                        params);
        if (rows.isEmpty()) throw new HomeworkNotFoundException("Pièce jointe introuvable");
        return rows.getFirst();
    }

    private List<ParentLesson> parentLessons(
            PortalIdentity parent, DateRange range, UUID selectedStudentId) {
        String studentFilter = selectedStudentId == null ? "" : " AND sp.id=?";
        Object[] params =
                selectedStudentId == null
                        ? new Object[] {
                            parent.tenantId(), parent.accountId(), range.from(), range.to()
                        }
                        : new Object[] {
                            parent.tenantId(),
                            parent.accountId(),
                            range.from(),
                            range.to(),
                            selectedStudentId
                        };
        return jdbc.query(
                """
                SELECT DISTINCT lc.id,sp.id AS student_id,
                       CONCAT(p.first_name,' ',p.last_name) AS student_name,
                       lc.lesson_date,lc.title,lc.summary,lc.content,
                       sub.code AS subject_code,sub.name_fr AS subject_name_fr,
                       sub.name_ar AS subject_name_ar,sub.color,c.name AS classroom_name
                FROM lesson_content lc
                JOIN student_enrollment e
                  ON e.classroom_id=lc.classroom_id AND e.tenant_id=lc.tenant_id
                JOIN student_profile sp ON sp.id=e.student_id AND sp.tenant_id=e.tenant_id
                JOIN person p ON p.id=sp.person_id AND p.tenant_id=sp.tenant_id
                JOIN enrollment_portal_student_link link
                  ON link.student_id=sp.id AND link.tenant_id=sp.tenant_id
                JOIN timetable_subject sub ON sub.id=lc.subject_id AND sub.tenant_id=lc.tenant_id
                JOIN classroom c ON c.id=lc.classroom_id AND c.tenant_id=lc.tenant_id
                WHERE lc.tenant_id=? AND link.portal_account_id=? AND e.status='ACTIVE'
                  AND lc.status='PUBLISHED' AND lc.lesson_date BETWEEN ? AND ?
                """
                        + studentFilter
                        + " ORDER BY lc.lesson_date,student_name,sub.name_fr",
                (rs, row) -> {
                    UUID id = uuid(rs, "id");
                    return new ParentLesson(
                            id,
                            uuid(rs, "student_id"),
                            rs.getString("student_name"),
                            rs.getString("classroom_name"),
                            rs.getString("subject_code"),
                            rs.getString("subject_name_fr"),
                            rs.getString("subject_name_ar"),
                            rs.getString("color"),
                            rs.getObject("lesson_date", LocalDate.class),
                            rs.getString("title"),
                            rs.getString("summary"),
                            rs.getString("content"),
                            attachments(parent.tenantId(), "LESSON_CONTENT", id));
                },
                params);
    }

    private List<ParentHomework> parentHomework(
            PortalIdentity parent, DateRange range, UUID selectedStudentId) {
        String studentFilter = selectedStudentId == null ? "" : " AND sp.id=?";
        Instant start = range.from().atStartOfDay(SCHOOL_ZONE).toInstant();
        Instant end = range.to().plusDays(1).atStartOfDay(SCHOOL_ZONE).toInstant();
        Object[] params =
                selectedStudentId == null
                        ? new Object[] {
                            parent.tenantId(), parent.accountId(), timestamp(start), timestamp(end)
                        }
                        : new Object[] {
                            parent.tenantId(),
                            parent.accountId(),
                            timestamp(start),
                            timestamp(end),
                            selectedStudentId
                        };
        return jdbc.query(
                """
                SELECT h.id,sp.id AS student_id,
                       CONCAT(p.first_name,' ',p.last_name) AS student_name,
                       c.name AS classroom_name,sub.code AS subject_code,
                       sub.name_fr AS subject_name_fr,sub.name_ar AS subject_name_ar,sub.color,
                       h.title,h.instructions,h.assigned_on,h.due_at,h.estimated_minutes,
                       h.status AS publication_status,hs.status AS student_status,hs.completed_at
                FROM homework_student_status hs
                JOIN homework_assignment h ON h.id=hs.homework_id AND h.tenant_id=hs.tenant_id
                JOIN student_profile sp ON sp.id=hs.student_id AND sp.tenant_id=hs.tenant_id
                JOIN person p ON p.id=sp.person_id AND p.tenant_id=sp.tenant_id
                JOIN enrollment_portal_student_link link
                  ON link.student_id=sp.id AND link.tenant_id=sp.tenant_id
                JOIN classroom c ON c.id=h.classroom_id AND c.tenant_id=h.tenant_id
                JOIN timetable_subject sub ON sub.id=h.subject_id AND sub.tenant_id=h.tenant_id
                WHERE h.tenant_id=? AND link.portal_account_id=?
                  AND h.status IN ('PUBLISHED','CLOSED') AND h.due_at>=? AND h.due_at<?
                """
                        + studentFilter
                        + " ORDER BY h.due_at,student_name,sub.name_fr",
                (rs, row) -> {
                    UUID id = uuid(rs, "id");
                    Instant dueAt = instant(rs, "due_at");
                    String studentStatus = rs.getString("student_status");
                    return new ParentHomework(
                            id,
                            uuid(rs, "student_id"),
                            rs.getString("student_name"),
                            rs.getString("classroom_name"),
                            rs.getString("subject_code"),
                            rs.getString("subject_name_fr"),
                            rs.getString("subject_name_ar"),
                            rs.getString("color"),
                            rs.getString("title"),
                            rs.getString("instructions"),
                            rs.getObject("assigned_on", LocalDate.class),
                            dueAt,
                            nullableInteger(rs, "estimated_minutes"),
                            rs.getString("publication_status"),
                            studentStatus,
                            nullableInstant(rs, "completed_at"),
                            "TO_DO".equals(studentStatus) && dueAt.isBefore(clock.instant()),
                            attachments(parent.tenantId(), "HOMEWORK", id));
                },
                params);
    }

    private AssignmentOption assignment(UUID tenantId, UUID assignmentId) {
        if (assignmentId == null) throw new HomeworkValidationException("Affectation requise");
        List<AssignmentOption> rows =
                jdbc.query(
                        assignmentSql() + " WHERE a.tenant_id=? AND a.id=? AND a.active=TRUE",
                        HomeworkService::assignmentOption,
                        tenantId,
                        assignmentId);
        if (rows.isEmpty())
            throw new HomeworkNotFoundException("Affectation professeur introuvable");
        return rows.getFirst();
    }

    private ClassTermContext classTerm(UUID tenantId, UUID classroomId, UUID termId) {
        List<ClassTermContext> rows =
                jdbc.query(
                        """
                        SELECT c.id AS classroom_id,c.name AS classroom_name,c.academic_year_id,
                               t.id AS term_id,t.name AS term_name,t.starts_on,t.ends_on
                        FROM classroom c
                        JOIN academic_term t ON t.academic_year_id=c.academic_year_id
                                             AND t.tenant_id=c.tenant_id
                        WHERE c.tenant_id=? AND c.id=? AND t.id=?
                        """,
                        (rs, row) ->
                                new ClassTermContext(
                                        uuid(rs, "classroom_id"),
                                        rs.getString("classroom_name"),
                                        uuid(rs, "academic_year_id"),
                                        uuid(rs, "term_id"),
                                        rs.getString("term_name"),
                                        rs.getObject("starts_on", LocalDate.class),
                                        rs.getObject("ends_on", LocalDate.class)),
                        tenantId,
                        classroomId,
                        termId);
        if (rows.isEmpty()) throw new HomeworkNotFoundException("Classe ou période introuvable");
        return rows.getFirst();
    }

    private TermContext term(UUID tenantId, UUID termId, UUID academicYearId) {
        List<TermContext> rows =
                jdbc.query(
                        """
                        SELECT id,starts_on,ends_on FROM academic_term
                        WHERE tenant_id=? AND id=? AND academic_year_id=?
                        """,
                        (rs, row) ->
                                new TermContext(
                                        uuid(rs, "id"),
                                        rs.getObject("starts_on", LocalDate.class),
                                        rs.getObject("ends_on", LocalDate.class)),
                        tenantId,
                        termId,
                        academicYearId);
        if (rows.isEmpty()) {
            throw new HomeworkConflictException(
                    "La période ne correspond pas à l'année de l'affectation");
        }
        return rows.getFirst();
    }

    private LessonHeader requireDraftLesson(UUID tenantId, UUID id) {
        LessonHeader header = lesson(tenantId, id);
        if (!"DRAFT".equals(header.status())) {
            throw new HomeworkConflictException("Un contenu de cours publié est immuable");
        }
        return header;
    }

    private LessonHeader lesson(UUID tenantId, UUID id) {
        List<LessonHeader> rows =
                jdbc.query(
                        """
                        SELECT id,classroom_id,academic_term_id,subject_id,teacher_staff_id,
                               lesson_date,title,status FROM lesson_content
                        WHERE tenant_id=? AND id=?
                        """,
                        (rs, row) ->
                                new LessonHeader(
                                        uuid(rs, "id"),
                                        uuid(rs, "classroom_id"),
                                        uuid(rs, "academic_term_id"),
                                        uuid(rs, "subject_id"),
                                        uuid(rs, "teacher_staff_id"),
                                        rs.getObject("lesson_date", LocalDate.class),
                                        rs.getString("title"),
                                        rs.getString("status")),
                        tenantId,
                        id);
        if (rows.isEmpty()) throw new HomeworkNotFoundException("Contenu de cours introuvable");
        return rows.getFirst();
    }

    private HomeworkHeader requireDraftHomework(UUID tenantId, UUID id) {
        HomeworkHeader header = homeworkHeader(tenantId, id);
        if (!"DRAFT".equals(header.status())) {
            throw new HomeworkConflictException("Un devoir publié est immuable");
        }
        return header;
    }

    private HomeworkHeader homeworkHeader(UUID tenantId, UUID id) {
        List<HomeworkHeader> rows =
                jdbc.query(
                        """
                        SELECT id,lesson_content_id,classroom_id,academic_term_id,subject_id,
                               teacher_staff_id,title,due_at,status FROM homework_assignment
                        WHERE tenant_id=? AND id=?
                        """,
                        (rs, row) ->
                                new HomeworkHeader(
                                        uuid(rs, "id"),
                                        nullableUuid(rs, "lesson_content_id"),
                                        uuid(rs, "classroom_id"),
                                        uuid(rs, "academic_term_id"),
                                        uuid(rs, "subject_id"),
                                        uuid(rs, "teacher_staff_id"),
                                        rs.getString("title"),
                                        instant(rs, "due_at"),
                                        rs.getString("status")),
                        tenantId,
                        id);
        if (rows.isEmpty()) throw new HomeworkNotFoundException("Devoir introuvable");
        return rows.getFirst();
    }

    private void validateLesson(LessonContentCommand command) {
        if (command == null
                || command.teacherAssignmentId() == null
                || command.academicTermId() == null
                || command.lessonDate() == null
                || blank(command.title())
                || clean(command.title()).length() > 180
                || blank(command.content())
                || clean(command.content()).length() > 20_000
                || (command.summary() != null && command.summary().strip().length() > 1000)) {
            throw new HomeworkValidationException("Contenu de cours invalide");
        }
    }

    private void validateHomework(HomeworkCommand command) {
        if (command == null
                || command.teacherAssignmentId() == null
                || command.academicTermId() == null
                || command.assignedOn() == null
                || command.dueAt() == null
                || blank(command.title())
                || clean(command.title()).length() > 180
                || blank(command.instructions())
                || clean(command.instructions()).length() > 20_000
                || (command.estimatedMinutes() != null
                        && (command.estimatedMinutes() < 1 || command.estimatedMinutes() > 600))) {
            throw new HomeworkValidationException("Devoir invalide");
        }
    }

    private void validateAttachment(String originalFileName, String contentType, byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_ATTACHMENT_SIZE) {
            throw new HomeworkValidationException("Le fichier doit peser entre 1 octet et 10 Mo");
        }
        if (blank(contentType)
                || !ATTACHMENT_TYPES.contains(contentType.strip().toLowerCase(Locale.ROOT))) {
            throw new HomeworkValidationException(
                    "Types acceptés : PDF, JPEG, PNG, TXT, DOCX et PPTX");
        }
        if (blank(originalFileName)) {
            throw new HomeworkValidationException("Nom de fichier requis");
        }
    }

    private static void requireDateInTerm(LocalDate value, TermContext term, String label) {
        if (value.isBefore(term.startsOn()) || value.isAfter(term.endsOn())) {
            throw new HomeworkValidationException(
                    label + " doit appartenir à la période sélectionnée");
        }
    }

    private static DateRange range(
            LocalDate from, LocalDate to, LocalDate defaultFrom, LocalDate defaultTo) {
        LocalDate selectedFrom = from == null ? defaultFrom : from;
        LocalDate selectedTo = to == null ? defaultTo : to;
        if (selectedFrom.isAfter(selectedTo)) {
            throw new HomeworkValidationException("La date de début doit précéder la date de fin");
        }
        if (selectedFrom.plusDays(366).isBefore(selectedTo)) {
            throw new HomeworkValidationException("La plage est limitée à 366 jours");
        }
        return new DateRange(selectedFrom, selectedTo);
    }

    private static String normalizeOwner(String value) {
        if (value == null) throw new HomeworkValidationException("Propriétaire de fichier requis");
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        if (!Set.of("LESSON_CONTENT", "HOMEWORK").contains(normalized)) {
            throw new HomeworkValidationException("Propriétaire de fichier invalide");
        }
        return normalized;
    }

    private static String safeFileName(String value) {
        String cleaned =
                value.replace('\\', '_')
                        .replace('/', '_')
                        .replace('\r', '_')
                        .replace('\n', '_')
                        .strip();
        if (cleaned.isEmpty()) return "piece-jointe";
        return cleaned.length() > 255 ? cleaned.substring(cleaned.length() - 255) : cleaned;
    }

    private static String clean(String value) {
        return value.strip();
    }

    private static String optional(String value) {
        return blank(value) ? null : value.strip();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponible", exception);
        }
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    private static UUID nullableUuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column) == null ? null : rs.getObject(column, UUID.class);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column).toInstant();
    }

    private static Instant nullableInstant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : ((Number) value).intValue();
    }

    private static String assignmentSql() {
        return """
        SELECT a.id,a.classroom_id,a.subject_id,a.teacher_staff_id,s.academic_year_id,
               c.code AS classroom_code,c.name AS classroom_name,sub.code AS subject_code,
               sub.name_fr AS subject_name_fr,sub.name_ar AS subject_name_ar,sub.color,
               CONCAT(p.first_name,' ',p.last_name) AS teacher_name
        FROM timetable_teacher_assignment a
        JOIN timetable_schedule s ON s.id=a.schedule_id AND s.tenant_id=a.tenant_id
        JOIN classroom c ON c.id=a.classroom_id AND c.tenant_id=a.tenant_id
        JOIN timetable_subject sub ON sub.id=a.subject_id AND sub.tenant_id=a.tenant_id
        JOIN staff_profile sp ON sp.id=a.teacher_staff_id AND sp.tenant_id=a.tenant_id
        JOIN person p ON p.id=sp.person_id AND p.tenant_id=sp.tenant_id
        """;
    }

    private static AssignmentOption assignmentOption(ResultSet rs, int row) throws SQLException {
        return new AssignmentOption(
                uuid(rs, "id"),
                uuid(rs, "academic_year_id"),
                uuid(rs, "classroom_id"),
                rs.getString("classroom_code"),
                rs.getString("classroom_name"),
                uuid(rs, "subject_id"),
                rs.getString("subject_code"),
                rs.getString("subject_name_fr"),
                rs.getString("subject_name_ar"),
                rs.getString("color"),
                uuid(rs, "teacher_staff_id"),
                rs.getString("teacher_name"));
    }

    private static String lessonSql() {
        return """
        SELECT lc.id,lc.teacher_assignment_id,lc.classroom_id,lc.academic_term_id,
               lc.subject_id,lc.teacher_staff_id,lc.lesson_date,lc.title,lc.summary,lc.content,
               lc.status,lc.published_at,lc.created_at,lc.updated_at,lc.version,
               c.name AS classroom_name,t.name AS term_name,sub.code AS subject_code,
               sub.name_fr AS subject_name_fr,sub.name_ar AS subject_name_ar,sub.color,
               CONCAT(p.first_name,' ',p.last_name) AS teacher_name
        FROM lesson_content lc
        JOIN classroom c ON c.id=lc.classroom_id AND c.tenant_id=lc.tenant_id
        JOIN academic_term t ON t.id=lc.academic_term_id AND t.tenant_id=lc.tenant_id
        JOIN timetable_subject sub ON sub.id=lc.subject_id AND sub.tenant_id=lc.tenant_id
        JOIN staff_profile sp ON sp.id=lc.teacher_staff_id AND sp.tenant_id=lc.tenant_id
        JOIN person p ON p.id=sp.person_id AND p.tenant_id=sp.tenant_id
        """;
    }

    private static LessonContentView lessonView(ResultSet rs, List<AttachmentView> attachments)
            throws SQLException {
        return new LessonContentView(
                uuid(rs, "id"),
                uuid(rs, "teacher_assignment_id"),
                uuid(rs, "classroom_id"),
                rs.getString("classroom_name"),
                uuid(rs, "academic_term_id"),
                rs.getString("term_name"),
                uuid(rs, "subject_id"),
                rs.getString("subject_code"),
                rs.getString("subject_name_fr"),
                rs.getString("subject_name_ar"),
                rs.getString("color"),
                uuid(rs, "teacher_staff_id"),
                rs.getString("teacher_name"),
                rs.getObject("lesson_date", LocalDate.class),
                rs.getString("title"),
                rs.getString("summary"),
                rs.getString("content"),
                rs.getString("status"),
                nullableInstant(rs, "published_at"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"),
                rs.getLong("version"),
                attachments);
    }

    private static String homeworkSql() {
        return """
        SELECT h.id,h.teacher_assignment_id,h.lesson_content_id,h.classroom_id,
               h.academic_term_id,h.subject_id,h.teacher_staff_id,h.title,h.instructions,
               h.assigned_on,h.due_at,h.estimated_minutes,h.status,h.published_at,h.closed_at,
               h.created_at,h.updated_at,h.version,c.name AS classroom_name,t.name AS term_name,
               sub.code AS subject_code,sub.name_fr AS subject_name_fr,
               sub.name_ar AS subject_name_ar,sub.color,
               CONCAT(p.first_name,' ',p.last_name) AS teacher_name,
               (SELECT count(*) FROM homework_student_status hs
                 WHERE hs.tenant_id=h.tenant_id AND hs.homework_id=h.id) AS total_students,
               (SELECT count(*) FROM homework_student_status hs
                 WHERE hs.tenant_id=h.tenant_id AND hs.homework_id=h.id
                   AND hs.status='DONE') AS completed_students
        FROM homework_assignment h
        JOIN classroom c ON c.id=h.classroom_id AND c.tenant_id=h.tenant_id
        JOIN academic_term t ON t.id=h.academic_term_id AND t.tenant_id=h.tenant_id
        JOIN timetable_subject sub ON sub.id=h.subject_id AND sub.tenant_id=h.tenant_id
        JOIN staff_profile sp ON sp.id=h.teacher_staff_id AND sp.tenant_id=h.tenant_id
        JOIN person p ON p.id=sp.person_id AND p.tenant_id=sp.tenant_id
        """;
    }

    private HomeworkView homeworkView(ResultSet rs, List<AttachmentView> attachments)
            throws SQLException {
        Instant dueAt = instant(rs, "due_at");
        return new HomeworkView(
                uuid(rs, "id"),
                uuid(rs, "teacher_assignment_id"),
                nullableUuid(rs, "lesson_content_id"),
                uuid(rs, "classroom_id"),
                rs.getString("classroom_name"),
                uuid(rs, "academic_term_id"),
                rs.getString("term_name"),
                uuid(rs, "subject_id"),
                rs.getString("subject_code"),
                rs.getString("subject_name_fr"),
                rs.getString("subject_name_ar"),
                rs.getString("color"),
                uuid(rs, "teacher_staff_id"),
                rs.getString("teacher_name"),
                rs.getString("title"),
                rs.getString("instructions"),
                rs.getObject("assigned_on", LocalDate.class),
                dueAt,
                nullableInteger(rs, "estimated_minutes"),
                rs.getString("status"),
                nullableInstant(rs, "published_at"),
                nullableInstant(rs, "closed_at"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"),
                rs.getLong("version"),
                rs.getInt("total_students"),
                rs.getInt("completed_students"),
                !"DRAFT".equals(rs.getString("status")) && dueAt.isBefore(clock.instant()),
                attachments);
    }

    private static AttachmentView attachmentView(ResultSet rs, int row) throws SQLException {
        return new AttachmentView(
                uuid(rs, "id"),
                rs.getString("original_file_name"),
                rs.getString("content_type"),
                rs.getLong("size_bytes"),
                rs.getString("sha256"),
                instant(rs, "created_at"));
    }

    public record LessonContentCommand(
            UUID teacherAssignmentId,
            UUID academicTermId,
            LocalDate lessonDate,
            String title,
            String summary,
            String content) {}

    public record HomeworkCommand(
            UUID teacherAssignmentId,
            UUID academicTermId,
            UUID lessonContentId,
            String title,
            String instructions,
            LocalDate assignedOn,
            OffsetDateTime dueAt,
            Integer estimatedMinutes) {}

    public record StudentStatusCommand(UUID studentId, String status) {}

    public record TermOption(
            UUID id,
            UUID academicYearId,
            String code,
            String name,
            LocalDate startsOn,
            LocalDate endsOn) {}

    public record ClassroomOption(
            UUID id,
            UUID academicYearId,
            String code,
            String name,
            String gradeLevelName,
            String schoolLevelName) {}

    public record AssignmentOption(
            UUID id,
            UUID academicYearId,
            UUID classroomId,
            String classroomCode,
            String classroomName,
            UUID subjectId,
            String subjectCode,
            String subjectNameFr,
            String subjectNameAr,
            String color,
            UUID teacherStaffId,
            String teacherName) {}

    public record ReferenceData(
            List<TermOption> terms,
            List<ClassroomOption> classrooms,
            List<AssignmentOption> assignments) {}

    public record AttachmentView(
            UUID id,
            String fileName,
            String contentType,
            long sizeBytes,
            String sha256,
            Instant createdAt) {}

    public record AttachmentContent(
            byte[] bytes, String fileName, String contentType, String sha256) {}

    public record LessonContentView(
            UUID id,
            UUID teacherAssignmentId,
            UUID classroomId,
            String classroomName,
            UUID academicTermId,
            String termName,
            UUID subjectId,
            String subjectCode,
            String subjectNameFr,
            String subjectNameAr,
            String color,
            UUID teacherStaffId,
            String teacherName,
            LocalDate lessonDate,
            String title,
            String summary,
            String content,
            String status,
            Instant publishedAt,
            Instant createdAt,
            Instant updatedAt,
            long version,
            List<AttachmentView> attachments) {}

    public record HomeworkView(
            UUID id,
            UUID teacherAssignmentId,
            UUID lessonContentId,
            UUID classroomId,
            String classroomName,
            UUID academicTermId,
            String termName,
            UUID subjectId,
            String subjectCode,
            String subjectNameFr,
            String subjectNameAr,
            String color,
            UUID teacherStaffId,
            String teacherName,
            String title,
            String instructions,
            LocalDate assignedOn,
            Instant dueAt,
            Integer estimatedMinutes,
            String status,
            Instant publishedAt,
            Instant closedAt,
            Instant createdAt,
            Instant updatedAt,
            long version,
            int totalStudents,
            int completedStudents,
            boolean overdue,
            List<AttachmentView> attachments) {}

    public record Dashboard(
            UUID classroomId,
            String classroomName,
            UUID academicTermId,
            String termName,
            LocalDate from,
            LocalDate to,
            int studentCount,
            List<LessonContentView> lessonContents,
            List<HomeworkView> homework,
            int publishedHomeworkCount) {}

    public record ChildOption(UUID id, String name, UUID classroomId, String classroomName) {}

    public record ParentLesson(
            UUID id,
            UUID studentId,
            String studentName,
            String classroomName,
            String subjectCode,
            String subjectNameFr,
            String subjectNameAr,
            String color,
            LocalDate lessonDate,
            String title,
            String summary,
            String content,
            List<AttachmentView> attachments) {}

    public record ParentHomework(
            UUID id,
            UUID studentId,
            String studentName,
            String classroomName,
            String subjectCode,
            String subjectNameFr,
            String subjectNameAr,
            String color,
            String title,
            String instructions,
            LocalDate assignedOn,
            Instant dueAt,
            Integer estimatedMinutes,
            String publicationStatus,
            String studentStatus,
            Instant completedAt,
            boolean overdue,
            List<AttachmentView> attachments) {}

    public record ParentDashboard(
            Instant generatedAt,
            LocalDate from,
            LocalDate to,
            List<ChildOption> children,
            List<ParentLesson> lessonContents,
            List<ParentHomework> homework) {}

    private record ClassTermContext(
            UUID classroomId,
            String classroomName,
            UUID academicYearId,
            UUID termId,
            String termName,
            LocalDate startsOn,
            LocalDate endsOn) {}

    private record TermContext(UUID id, LocalDate startsOn, LocalDate endsOn) {}

    private record DateRange(LocalDate from, LocalDate to) {}

    private record LessonHeader(
            UUID id,
            UUID classroomId,
            UUID academicTermId,
            UUID subjectId,
            UUID teacherStaffId,
            LocalDate lessonDate,
            String title,
            String status) {}

    private record HomeworkHeader(
            UUID id,
            UUID lessonContentId,
            UUID classroomId,
            UUID academicTermId,
            UUID subjectId,
            UUID teacherStaffId,
            String title,
            Instant dueAt,
            String status) {}
}
