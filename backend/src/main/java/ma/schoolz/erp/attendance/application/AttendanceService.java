package ma.schoolz.erp.attendance.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.admissions.AdmissionPortalAccess.PortalIdentity;
import ma.schoolz.erp.enrollment.PortalStudentAccess;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.identity.IdentityAuditTrail;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttendanceService {
    private static final Set<String> RECORD_STATUSES =
            Set.of("PRESENT", "ABSENT", "LATE", "EARLY_DEPARTURE");
    private static final Set<String> REVIEW_STATUSES = Set.of("APPROVED", "REJECTED");

    private final JdbcTemplate jdbc;
    private final PortalStudentAccess portalStudents;
    private final IdentityAuditTrail audit;
    private final Clock clock;

    public AttendanceService(
            JdbcTemplate jdbc,
            PortalStudentAccess portalStudents,
            IdentityAuditTrail audit,
            Clock clock) {
        this.jdbc = jdbc;
        this.portalStudents = portalStudents;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('attendance.read')")
    public List<SessionSummary> sessions(SchoolPrincipal actor, LocalDate date) {
        LocalDate selected = date == null ? LocalDate.now(clock) : date;
        return jdbc.query(
                """
                SELECT s.id,s.session_date,s.subject_code,s.subject_name,s.starts_at,s.ends_at,
                       s.status,c.name AS classroom_name,
                       CONCAT(p.first_name,' ',p.last_name) AS teacher_name,
                       (SELECT count(*) FROM attendance_record r WHERE r.tenant_id=s.tenant_id
                         AND r.session_id=s.id) AS total_count,
                       (SELECT count(*) FROM attendance_record r WHERE r.tenant_id=s.tenant_id
                         AND r.session_id=s.id AND r.status='PRESENT') AS present_count,
                       (SELECT count(*) FROM attendance_record r WHERE r.tenant_id=s.tenant_id
                         AND r.session_id=s.id AND r.status='ABSENT') AS absent_count,
                       (SELECT count(*) FROM attendance_record r WHERE r.tenant_id=s.tenant_id
                         AND r.session_id=s.id AND r.status='LATE') AS late_count
                FROM attendance_session s
                JOIN classroom c ON c.id=s.classroom_id AND c.tenant_id=s.tenant_id
                JOIN staff_profile sp ON sp.id=s.teacher_staff_id AND sp.tenant_id=s.tenant_id
                JOIN person p ON p.id=sp.person_id AND p.tenant_id=sp.tenant_id
                WHERE s.tenant_id=? AND s.session_date=?
                ORDER BY s.starts_at,c.name
                """,
                (result, row) -> sessionSummary(result),
                actor.tenantId(),
                selected);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('attendance.read')")
    public ReferenceData references(SchoolPrincipal actor) {
        List<ClassroomOption> classrooms =
                jdbc.query(
                        """
                        SELECT c.id,c.name,c.code,c.campus_id,c.academic_year_id
                        FROM classroom c
                        JOIN academic_year ay ON ay.id=c.academic_year_id AND ay.tenant_id=c.tenant_id
                        WHERE c.tenant_id=? AND c.active=TRUE AND ay.status='ACTIVE'
                        ORDER BY c.name
                        """,
                        (result, row) ->
                                new ClassroomOption(
                                        result.getObject("id", UUID.class),
                                        result.getString("code"),
                                        result.getString("name"),
                                        result.getObject("campus_id", UUID.class),
                                        result.getObject("academic_year_id", UUID.class)),
                        actor.tenantId());
        List<TeacherOption> teachers =
                jdbc.query(
                        """
                        SELECT sp.id,sp.employee_number,CONCAT(p.first_name,' ',p.last_name) AS name
                        FROM staff_profile sp
                        JOIN person p ON p.id=sp.person_id AND p.tenant_id=sp.tenant_id
                        WHERE sp.tenant_id=? AND sp.employment_status='ACTIVE'
                        ORDER BY p.last_name,p.first_name
                        """,
                        (result, row) ->
                                new TeacherOption(
                                        result.getObject("id", UUID.class),
                                        result.getString("employee_number"),
                                        result.getString("name")),
                        actor.tenantId());
        return new ReferenceData(classrooms, teachers, reasons(actor.tenantId()));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('attendance.read')")
    public SessionView session(SchoolPrincipal actor, UUID sessionId) {
        return session(actor.tenantId(), sessionId);
    }

    @Transactional
    @PreAuthorize("hasAuthority('attendance.write')")
    public SessionView createSession(
            SchoolPrincipal actor, SessionCommand command, String ipAddress) {
        if (command == null
                || command.classroomId() == null
                || command.teacherStaffId() == null
                || command.sessionDate() == null
                || command.startsAt() == null
                || command.endsAt() == null
                || !command.startsAt().isBefore(command.endsAt())
                || blank(command.subjectCode())
                || blank(command.subjectName())) {
            throw new AttendanceValidationException("Les informations du cours sont incomplètes");
        }
        Scope scope =
                jdbc
                        .query(
                                "SELECT campus_id,academic_year_id FROM classroom WHERE tenant_id=?"
                                        + " AND id=? AND active=TRUE",
                                (result, row) ->
                                        new Scope(
                                                result.getObject("campus_id", UUID.class),
                                                result.getObject("academic_year_id", UUID.class)),
                                actor.tenantId(),
                                command.classroomId())
                        .stream()
                        .findFirst()
                        .orElseThrow(() -> new AttendanceNotFoundException("Classe introuvable"));
        requireReference("staff_profile", actor.tenantId(), command.teacherStaffId());
        UUID sessionId = UUID.randomUUID();
        Instant now = clock.instant();
        try {
            jdbc.update(
                    """
                    INSERT INTO attendance_session
                        (id,tenant_id,campus_id,academic_year_id,classroom_id,teacher_staff_id,
                         session_date,subject_code,subject_name,starts_at,ends_at,status,
                         created_by_user_id,created_at,updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', ?, ?, ?)
                    """,
                    sessionId,
                    actor.tenantId(),
                    scope.campusId(),
                    scope.academicYearId(),
                    command.classroomId(),
                    command.teacherStaffId(),
                    command.sessionDate(),
                    command.subjectCode().strip().toUpperCase(Locale.ROOT),
                    command.subjectName().strip(),
                    command.startsAt(),
                    command.endsAt(),
                    actor.accountId(),
                    timestamp(now),
                    timestamp(now));
        } catch (DuplicateKeyException exception) {
            throw new AttendanceValidationException(
                    "Une session existe déjà pour cette classe et cet horaire");
        }
        List<EnrollmentRow> enrollmentRows =
                jdbc.query(
                        """
                        SELECT se.id,se.student_id FROM student_enrollment se
                        WHERE se.tenant_id=? AND se.classroom_id=? AND se.academic_year_id=?
                          AND se.status='ACTIVE'
                        ORDER BY se.created_at
                        """,
                        (result, row) ->
                                new EnrollmentRow(
                                        result.getObject("id", UUID.class),
                                        result.getObject("student_id", UUID.class)),
                        actor.tenantId(),
                        command.classroomId(),
                        scope.academicYearId());
        if (enrollmentRows.isEmpty()) {
            throw new AttendanceValidationException(
                    "La classe ne contient aucun élève inscrit et actif");
        }
        for (EnrollmentRow enrollment : enrollmentRows) {
            jdbc.update(
                    """
                    INSERT INTO attendance_record
                        (id,tenant_id,session_id,student_enrollment_id,student_id,status,
                         marked_at,marked_by_user_id,updated_at)
                    VALUES (?, ?, ?, ?, ?, 'PRESENT', ?, ?, ?)
                    """,
                    UUID.randomUUID(),
                    actor.tenantId(),
                    sessionId,
                    enrollment.enrollmentId(),
                    enrollment.studentId(),
                    timestamp(now),
                    actor.accountId(),
                    timestamp(now));
        }
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "attendance.session.created",
                "AttendanceSession",
                sessionId.toString(),
                ipAddress,
                Map.of("classroomId", command.classroomId(), "studentCount", enrollmentRows.size()),
                null,
                Map.of("status", "OPEN"));
        return session(actor.tenantId(), sessionId);
    }

    @Transactional
    @PreAuthorize("hasAuthority('attendance.write')")
    public SessionView updateRecords(
            SchoolPrincipal actor, UUID sessionId, RecordsCommand command, String ipAddress) {
        SessionHeader header = requireOpenSession(actor.tenantId(), sessionId);
        if (command == null || command.records() == null || command.records().isEmpty()) {
            throw new AttendanceValidationException("Au moins une présence doit être envoyée");
        }
        Instant now = clock.instant();
        for (RecordCommand record : command.records()) {
            String status = normalize(record.status());
            if (record.recordId() == null || !RECORD_STATUSES.contains(status)) {
                throw new AttendanceValidationException("Statut de présence invalide");
            }
            int updated =
                    jdbc.update(
                            """
                            UPDATE attendance_record SET status=?,notes=?,marked_at=?,
                                marked_by_user_id=?,updated_at=?,version=version+1
                            WHERE tenant_id=? AND id=? AND session_id=?
                            """,
                            status,
                            nullable(record.notes()),
                            timestamp(now),
                            actor.accountId(),
                            timestamp(now),
                            actor.tenantId(),
                            record.recordId(),
                            sessionId);
            if (updated == 0) {
                throw new AttendanceNotFoundException("Ligne d'appel introuvable");
            }
            jdbc.update(
                    "DELETE FROM attendance_late_arrival WHERE tenant_id=? AND"
                            + " attendance_record_id=?",
                    actor.tenantId(),
                    record.recordId());
            jdbc.update(
                    "DELETE FROM attendance_early_departure WHERE tenant_id=? AND"
                            + " attendance_record_id=?",
                    actor.tenantId(),
                    record.recordId());
            if ("LATE".equals(status)) {
                LocalTime arrivedAt = record.arrivedAt();
                if (arrivedAt == null || !arrivedAt.isAfter(header.startsAt())) {
                    throw new AttendanceValidationException(
                            "L'heure d'arrivée doit être postérieure au début du cours");
                }
                long minutes = Duration.between(header.startsAt(), arrivedAt).toMinutes();
                jdbc.update(
                        """
                        INSERT INTO attendance_late_arrival
                            (id,tenant_id,attendance_record_id,arrived_at,minutes_late,created_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                        UUID.randomUUID(),
                        actor.tenantId(),
                        record.recordId(),
                        arrivedAt,
                        minutes,
                        timestamp(now));
            }
            if ("EARLY_DEPARTURE".equals(status)) {
                if (record.leftAt() == null) {
                    throw new AttendanceValidationException("L'heure de départ est requise");
                }
                jdbc.update(
                        """
                        INSERT INTO attendance_early_departure
                            (id,tenant_id,attendance_record_id,left_at,reason,
                             pickup_person_name,created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                        UUID.randomUUID(),
                        actor.tenantId(),
                        record.recordId(),
                        record.leftAt(),
                        nullable(record.departureReason()),
                        nullable(record.pickupPersonName()),
                        timestamp(now));
            }
        }
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "attendance.records.updated",
                "AttendanceSession",
                sessionId.toString(),
                ipAddress,
                Map.of("recordCount", command.records().size()),
                null,
                Map.of("status", "OPEN"));
        return session(actor.tenantId(), sessionId);
    }

    @Transactional
    @PreAuthorize("hasAuthority('attendance.write')")
    public SessionView submit(SchoolPrincipal actor, UUID sessionId, String ipAddress) {
        requireOpenSession(actor.tenantId(), sessionId);
        Instant now = clock.instant();
        List<AbsentRow> absences =
                jdbc.query(
                        """
                        SELECT r.id,r.student_id,s.session_date,s.subject_name,c.name AS classroom_name
                        FROM attendance_record r
                        JOIN attendance_session s ON s.id=r.session_id AND s.tenant_id=r.tenant_id
                        JOIN classroom c ON c.id=s.classroom_id AND c.tenant_id=s.tenant_id
                        WHERE r.tenant_id=? AND r.session_id=? AND r.status='ABSENT'
                        """,
                        (result, row) ->
                                new AbsentRow(
                                        result.getObject("id", UUID.class),
                                        result.getObject("student_id", UUID.class),
                                        result.getObject("session_date", LocalDate.class),
                                        result.getString("subject_name"),
                                        result.getString("classroom_name")),
                        actor.tenantId(),
                        sessionId);
        for (AbsentRow absent : absences) {
            UUID absenceId = UUID.randomUUID();
            jdbc.update(
                    """
                    INSERT INTO attendance_absence
                        (id,tenant_id,attendance_record_id,student_id,occurred_on,
                         resolution_status,notified_at,created_at,updated_at)
                    VALUES (?, ?, ?, ?, ?, 'UNEXCUSED', ?, ?, ?)
                    """,
                    absenceId,
                    actor.tenantId(),
                    absent.recordId(),
                    absent.studentId(),
                    absent.date(),
                    timestamp(now),
                    timestamp(now),
                    timestamp(now));
            notifyParents(actor.tenantId(), absenceId, absent, now);
        }
        jdbc.update(
                """
                UPDATE attendance_session SET status='SUBMITTED',submitted_at=?,
                    submitted_by_user_id=?,updated_at=?,version=version+1
                WHERE tenant_id=? AND id=? AND status='OPEN'
                """,
                timestamp(now),
                actor.accountId(),
                timestamp(now),
                actor.tenantId(),
                sessionId);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "attendance.session.submitted",
                "AttendanceSession",
                sessionId.toString(),
                ipAddress,
                Map.of("absenceCount", absences.size()),
                null,
                Map.of("status", "SUBMITTED"));
        return session(actor.tenantId(), sessionId);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('attendance.manage')")
    public List<JustificationView> justifications(SchoolPrincipal actor, String status) {
        String selected = blank(status) ? "PENDING" : normalize(status);
        return jdbc.query(
                """
                SELECT j.id,j.absence_id,j.parent_statement,j.evidence_object_key,j.submitted_at,
                       j.review_status,j.reviewed_at,j.review_notes,r.id AS reason_id,
                       r.name_fr AS reason_name,p.first_name,p.last_name,a.occurred_on,
                       s.subject_name,c.name AS classroom_name
                FROM attendance_excused_absence j
                JOIN attendance_absence a ON a.id=j.absence_id AND a.tenant_id=j.tenant_id
                JOIN attendance_record ar ON ar.id=a.attendance_record_id AND ar.tenant_id=a.tenant_id
                JOIN attendance_session s ON s.id=ar.session_id AND s.tenant_id=ar.tenant_id
                JOIN classroom c ON c.id=s.classroom_id AND c.tenant_id=s.tenant_id
                JOIN student_profile sp ON sp.id=a.student_id AND sp.tenant_id=a.tenant_id
                JOIN person p ON p.id=sp.person_id AND p.tenant_id=sp.tenant_id
                JOIN attendance_absence_reason r ON r.id=j.absence_reason_id AND r.tenant_id=j.tenant_id
                WHERE j.tenant_id=? AND j.review_status=?
                ORDER BY j.submitted_at
                """,
                (result, row) -> justification(result),
                actor.tenantId(),
                selected);
    }

    @Transactional
    @PreAuthorize("hasAuthority('attendance.manage')")
    public JustificationView review(
            SchoolPrincipal actor, UUID justificationId, ReviewCommand command, String ipAddress) {
        String decision = command == null ? "" : normalize(command.decision());
        if (!REVIEW_STATUSES.contains(decision)) {
            throw new AttendanceValidationException("Décision de justification invalide");
        }
        JustificationRow justification =
                jdbc
                        .query(
                                """
                                SELECT j.absence_id,j.portal_account_id,j.absence_reason_id,
                                       a.student_id,j.review_status
                                FROM attendance_excused_absence j
                                JOIN attendance_absence a ON a.id=j.absence_id AND a.tenant_id=j.tenant_id
                                WHERE j.tenant_id=? AND j.id=?
                                """,
                                (result, row) ->
                                        new JustificationRow(
                                                result.getObject("absence_id", UUID.class),
                                                result.getObject("portal_account_id", UUID.class),
                                                result.getObject("absence_reason_id", UUID.class),
                                                result.getObject("student_id", UUID.class),
                                                result.getString("review_status")),
                                actor.tenantId(),
                                justificationId)
                        .stream()
                        .findFirst()
                        .orElseThrow(
                                () -> new AttendanceNotFoundException("Justification introuvable"));
        if (!"PENDING".equals(justification.reviewStatus())) {
            throw new AttendanceValidationException("Cette justification a déjà été traitée");
        }
        Instant now = clock.instant();
        jdbc.update(
                """
                UPDATE attendance_excused_absence SET review_status=?,reviewed_at=?,
                    reviewed_by_user_id=?,review_notes=? WHERE tenant_id=? AND id=?
                """,
                decision,
                timestamp(now),
                actor.accountId(),
                nullable(command.notes()),
                actor.tenantId(),
                justificationId);
        jdbc.update(
                """
                UPDATE attendance_absence SET resolution_status=?,absence_reason_id=?,updated_at=?
                WHERE tenant_id=? AND id=?
                """,
                "APPROVED".equals(decision) ? "EXCUSED" : "REJECTED",
                "APPROVED".equals(decision) ? justification.reasonId() : null,
                timestamp(now),
                actor.tenantId(),
                justification.absenceId());
        String notificationType =
                "APPROVED".equals(decision) ? "JUSTIFICATION_APPROVED" : "JUSTIFICATION_REJECTED";
        jdbc.update(
                """
                INSERT INTO attendance_parent_notification
                    (id,tenant_id,portal_account_id,student_id,absence_id,notification_type,
                     title,message,status,created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'UNREAD', ?)
                """,
                UUID.randomUUID(),
                actor.tenantId(),
                justification.portalAccountId(),
                justification.studentId(),
                justification.absenceId(),
                notificationType,
                "Décision sur votre justification",
                "APPROVED".equals(decision)
                        ? "La justification a été acceptée. / تم قبول التبرير."
                        : "La justification a été refusée. / تم رفض التبرير.",
                timestamp(now));
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "attendance.justification.reviewed",
                "ExcusedAbsence",
                justificationId.toString(),
                ipAddress,
                Map.of("decision", decision),
                Map.of("status", "PENDING"),
                Map.of("status", decision));
        return justifications(actor, decision).stream()
                .filter(item -> item.id().equals(justificationId))
                .findFirst()
                .orElseThrow(() -> new AttendanceNotFoundException("Justification introuvable"));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('attendance.read')")
    public Statistics statistics(
            SchoolPrincipal actor, LocalDate from, LocalDate to, UUID classroomId, UUID studentId) {
        LocalDate end = to == null ? LocalDate.now(clock) : to;
        LocalDate start = from == null ? end.minusDays(30) : from;
        if (start.isAfter(end)) {
            throw new AttendanceValidationException("La période statistique est invalide");
        }
        String filter =
                " WHERE r.tenant_id=? AND s.session_date BETWEEN ? AND ?"
                        + (classroomId == null ? "" : " AND s.classroom_id=?")
                        + (studentId == null ? "" : " AND r.student_id=?");
        List<Object> arguments = new ArrayList<>(List.of(actor.tenantId(), start, end));
        if (classroomId != null) arguments.add(classroomId);
        if (studentId != null) arguments.add(studentId);
        Aggregate aggregate =
                jdbc.queryForObject(
                        """
                        SELECT count(*) AS total,
                               SUM(CASE WHEN r.status='PRESENT' THEN 1 ELSE 0 END) AS present,
                               SUM(CASE WHEN r.status='ABSENT' THEN 1 ELSE 0 END) AS absent,
                               SUM(CASE WHEN r.status='LATE' THEN 1 ELSE 0 END) AS late,
                               SUM(CASE WHEN r.status='EARLY_DEPARTURE' THEN 1 ELSE 0 END) AS early
                        FROM attendance_record r
                        JOIN attendance_session s ON s.id=r.session_id AND s.tenant_id=r.tenant_id
                        """
                                + filter,
                        (result, row) ->
                                new Aggregate(
                                        result.getInt("total"),
                                        result.getInt("present"),
                                        result.getInt("absent"),
                                        result.getInt("late"),
                                        result.getInt("early")),
                        arguments.toArray());
        int unexcused =
                jdbc.queryForObject(
                        """
                        SELECT count(*) FROM attendance_absence a
                        JOIN attendance_record r ON r.id=a.attendance_record_id AND r.tenant_id=a.tenant_id
                        JOIN attendance_session s ON s.id=r.session_id AND s.tenant_id=r.tenant_id
                        """
                                + filter
                                + " AND a.resolution_status IN ('UNEXCUSED','PENDING','REJECTED')",
                        Integer.class,
                        arguments.toArray());
        List<TrendPoint> trend =
                jdbc.query(
                        """
                        SELECT s.session_date,
                               SUM(CASE WHEN r.status='ABSENT' THEN 1 ELSE 0 END) AS absent,
                               SUM(CASE WHEN r.status='LATE' THEN 1 ELSE 0 END) AS late,
                               count(*) AS total
                        FROM attendance_record r
                        JOIN attendance_session s ON s.id=r.session_id AND s.tenant_id=r.tenant_id
                        """
                                + filter
                                + " GROUP BY s.session_date ORDER BY s.session_date",
                        (result, row) ->
                                new TrendPoint(
                                        result.getObject("session_date", LocalDate.class),
                                        result.getInt("absent"),
                                        result.getInt("late"),
                                        result.getInt("total")),
                        arguments.toArray());
        int total = aggregate == null ? 0 : aggregate.total();
        return new Statistics(
                start,
                end,
                total,
                aggregate == null ? 0 : aggregate.present(),
                aggregate == null ? 0 : aggregate.absent(),
                aggregate == null ? 0 : aggregate.late(),
                aggregate == null ? 0 : aggregate.early(),
                unexcused,
                total == 0 ? 0 : Math.round((aggregate.absent() * 10000.0 / total)) / 100.0,
                trend);
    }

    @Transactional
    @PreAuthorize("hasAuthority('discipline.write')")
    public IncidentView createIncident(
            SchoolPrincipal actor, IncidentCommand command, String ipAddress) {
        if (command == null
                || command.studentId() == null
                || blank(command.category())
                || blank(command.description())) {
            throw new AttendanceValidationException("L'incident est incomplet");
        }
        requireReference("student_profile", actor.tenantId(), command.studentId());
        if (command.classroomId() != null) {
            requireReference("classroom", actor.tenantId(), command.classroomId());
        }
        String severity = blank(command.severity()) ? "LOW" : normalize(command.severity());
        if (!Set.of("LOW", "MEDIUM", "HIGH").contains(severity)) {
            throw new AttendanceValidationException("Gravité invalide");
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                """
                INSERT INTO disciplinary_incident
                    (id,tenant_id,student_id,classroom_id,reported_by_user_id,occurred_at,
                     category,severity,description,status,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', ?, ?)
                """,
                id,
                actor.tenantId(),
                command.studentId(),
                command.classroomId(),
                actor.accountId(),
                timestamp(command.occurredAt() == null ? now : command.occurredAt()),
                command.category().strip().toUpperCase(Locale.ROOT),
                severity,
                command.description().strip(),
                timestamp(now),
                timestamp(now));
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "discipline.incident.created",
                "DisciplinaryIncident",
                id.toString(),
                ipAddress,
                Map.of("studentId", command.studentId(), "severity", severity),
                null,
                Map.of("status", "OPEN"));
        return incident(actor.tenantId(), id);
    }

    @Transactional
    @PreAuthorize("hasAuthority('discipline.write')")
    public SanctionView createSanction(
            SchoolPrincipal actor, UUID incidentId, SanctionCommand command, String ipAddress) {
        requireReference("disciplinary_incident", actor.tenantId(), incidentId);
        if (command == null
                || blank(command.sanctionType())
                || blank(command.description())
                || command.startsOn() == null
                || (command.endsOn() != null && command.startsOn().isAfter(command.endsOn()))) {
            throw new AttendanceValidationException("La sanction est invalide");
        }
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO attendance_sanction
                    (id,tenant_id,incident_id,sanction_type,description,starts_on,ends_on,
                     status,decided_by_user_id,created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
                """,
                id,
                actor.tenantId(),
                incidentId,
                command.sanctionType().strip().toUpperCase(Locale.ROOT),
                command.description().strip(),
                command.startsOn(),
                command.endsOn(),
                actor.accountId(),
                timestamp(clock.instant()));
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "discipline.sanction.created",
                "Sanction",
                id.toString(),
                ipAddress,
                Map.of("incidentId", incidentId),
                null,
                Map.of("status", "ACTIVE"));
        return new SanctionView(
                id,
                incidentId,
                command.sanctionType().strip().toUpperCase(Locale.ROOT),
                command.description().strip(),
                command.startsOn(),
                command.endsOn(),
                "ACTIVE");
    }

    @Transactional
    @PreAuthorize("hasAuthority('discipline.write')")
    public ObservationView createObservation(
            SchoolPrincipal actor, ObservationCommand command, String ipAddress) {
        if (command == null
                || command.studentId() == null
                || command.teacherStaffId() == null
                || blank(command.category())
                || blank(command.observation())) {
            throw new AttendanceValidationException("L'observation est incomplète");
        }
        requireReference("student_profile", actor.tenantId(), command.studentId());
        requireReference("staff_profile", actor.tenantId(), command.teacherStaffId());
        UUID id = UUID.randomUUID();
        LocalDate date =
                command.observationDate() == null
                        ? LocalDate.now(clock)
                        : command.observationDate();
        jdbc.update(
                """
                INSERT INTO teacher_observation
                    (id,tenant_id,student_id,classroom_id,teacher_staff_id,observation_date,
                     category,observation,visible_to_parent,created_by_user_id,created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                actor.tenantId(),
                command.studentId(),
                command.classroomId(),
                command.teacherStaffId(),
                date,
                command.category().strip().toUpperCase(Locale.ROOT),
                command.observation().strip(),
                command.visibleToParent(),
                actor.accountId(),
                timestamp(clock.instant()));
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "attendance.teacher-observation.created",
                "TeacherObservation",
                id.toString(),
                ipAddress,
                Map.of(
                        "studentId",
                        command.studentId(),
                        "visibleToParent",
                        command.visibleToParent()),
                null,
                Map.of("category", command.category().strip().toUpperCase(Locale.ROOT)));
        return new ObservationView(
                id,
                command.studentId(),
                command.classroomId(),
                command.teacherStaffId(),
                date,
                command.category().strip().toUpperCase(Locale.ROOT),
                command.observation().strip(),
                command.visibleToParent());
    }

    @Transactional(readOnly = true)
    public ParentDashboard parentDashboard(PortalIdentity parent, LocalDate from, LocalDate to) {
        Set<UUID> studentIds = portalStudents.linkedStudentIds(parent);
        LocalDate end = to == null ? LocalDate.now(clock) : to;
        LocalDate start = from == null ? end.minusDays(30) : from;
        List<ParentAbsence> absences = new ArrayList<>();
        for (UUID studentId : studentIds) {
            absences.addAll(
                    jdbc.query(
                            """
                            SELECT a.id,a.occurred_on,a.resolution_status,p.first_name,p.last_name,
                                   s.subject_name,c.name AS classroom_name,r.name_fr AS reason_name,
                                   j.id AS justification_id,j.review_status,j.parent_statement
                            FROM attendance_absence a
                            JOIN attendance_record ar ON ar.id=a.attendance_record_id AND ar.tenant_id=a.tenant_id
                            JOIN attendance_session s ON s.id=ar.session_id AND s.tenant_id=ar.tenant_id
                            JOIN classroom c ON c.id=s.classroom_id AND c.tenant_id=s.tenant_id
                            JOIN student_profile sp ON sp.id=a.student_id AND sp.tenant_id=a.tenant_id
                            JOIN person p ON p.id=sp.person_id AND p.tenant_id=sp.tenant_id
                            LEFT JOIN attendance_absence_reason r ON r.id=a.absence_reason_id AND r.tenant_id=a.tenant_id
                            LEFT JOIN attendance_excused_absence j ON j.absence_id=a.id AND j.tenant_id=a.tenant_id
                            WHERE a.tenant_id=? AND a.student_id=? AND a.occurred_on BETWEEN ? AND ?
                            ORDER BY a.occurred_on DESC
                            """,
                            (result, row) ->
                                    new ParentAbsence(
                                            result.getObject("id", UUID.class),
                                            studentId,
                                            result.getString("first_name")
                                                    + " "
                                                    + result.getString("last_name"),
                                            result.getObject("occurred_on", LocalDate.class),
                                            result.getString("subject_name"),
                                            result.getString("classroom_name"),
                                            result.getString("resolution_status"),
                                            result.getString("reason_name"),
                                            result.getObject("justification_id", UUID.class),
                                            result.getString("review_status"),
                                            result.getString("parent_statement")),
                            parent.tenantId(),
                            studentId,
                            start,
                            end));
        }
        List<NotificationView> notifications =
                jdbc.query(
                        """
                        SELECT n.id,n.student_id,n.absence_id,n.notification_type,n.title,n.message,
                               n.status,n.created_at,n.read_at,CONCAT(p.first_name,' ',p.last_name) AS student_name
                        FROM attendance_parent_notification n
                        JOIN student_profile sp ON sp.id=n.student_id AND sp.tenant_id=n.tenant_id
                        JOIN person p ON p.id=sp.person_id AND p.tenant_id=sp.tenant_id
                        WHERE n.tenant_id=? AND n.portal_account_id=?
                        ORDER BY n.created_at DESC
                        """,
                        (result, row) -> notification(result),
                        parent.tenantId(),
                        parent.accountId());
        return new ParentDashboard(start, end, absences, reasons(parent.tenantId()), notifications);
    }

    @Transactional
    public ParentAbsence justify(
            PortalIdentity parent, UUID absenceId, JustificationCommand command, String ipAddress) {
        if (command == null
                || command.reasonId() == null
                || blank(command.statement())
                || command.statement().strip().length() < 10) {
            throw new AttendanceValidationException(
                    "Le motif et une explication d'au moins 10 caractères sont requis");
        }
        Set<UUID> linked = portalStudents.linkedStudentIds(parent);
        UUID studentId =
                jdbc
                        .query(
                                "SELECT student_id FROM attendance_absence WHERE tenant_id=? AND"
                                        + " id=?",
                                (result, row) -> result.getObject(1, UUID.class),
                                parent.tenantId(),
                                absenceId)
                        .stream()
                        .findFirst()
                        .orElseThrow(() -> new AttendanceNotFoundException("Absence introuvable"));
        if (!linked.contains(studentId)) {
            throw new AttendanceNotFoundException("Absence introuvable");
        }
        int reasonExists =
                count(
                        "SELECT count(*) FROM attendance_absence_reason WHERE tenant_id=? AND id=?"
                                + " AND active=TRUE",
                        parent.tenantId(),
                        command.reasonId());
        if (reasonExists == 0) {
            throw new AttendanceValidationException("Motif d'absence invalide");
        }
        Instant now = clock.instant();
        List<ExistingJustification> existing =
                jdbc.query(
                        "SELECT id,review_status FROM attendance_excused_absence WHERE tenant_id=?"
                                + " AND absence_id=?",
                        (result, row) ->
                                new ExistingJustification(
                                        result.getObject("id", UUID.class),
                                        result.getString("review_status")),
                        parent.tenantId(),
                        absenceId);
        UUID justificationId;
        if (existing.isEmpty()) {
            justificationId = UUID.randomUUID();
            jdbc.update(
                    """
                    INSERT INTO attendance_excused_absence
                        (id,tenant_id,absence_id,portal_account_id,absence_reason_id,
                         parent_statement,evidence_object_key,submitted_at,review_status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING')
                    """,
                    justificationId,
                    parent.tenantId(),
                    absenceId,
                    parent.accountId(),
                    command.reasonId(),
                    command.statement().strip(),
                    nullable(command.evidenceObjectKey()),
                    timestamp(now));
        } else {
            ExistingJustification current = existing.getFirst();
            if (!"REJECTED".equals(current.status())) {
                throw new AttendanceValidationException("Une justification est déjà en cours");
            }
            justificationId = current.id();
            jdbc.update(
                    """
                    UPDATE attendance_excused_absence SET absence_reason_id=?,parent_statement=?,
                        evidence_object_key=?,submitted_at=?,review_status='PENDING',reviewed_at=NULL,
                        reviewed_by_user_id=NULL,review_notes=NULL WHERE tenant_id=? AND id=?
                    """,
                    command.reasonId(),
                    command.statement().strip(),
                    nullable(command.evidenceObjectKey()),
                    timestamp(now),
                    parent.tenantId(),
                    justificationId);
        }
        jdbc.update(
                "UPDATE attendance_absence SET resolution_status='PENDING',updated_at=?"
                        + " WHERE tenant_id=? AND id=?",
                timestamp(now),
                parent.tenantId(),
                absenceId);
        audit.record(
                parent.tenantId(),
                null,
                "attendance.justification.submitted",
                "ExcusedAbsence",
                justificationId.toString(),
                ipAddress,
                Map.of("portalAccountId", parent.accountId(), "absenceId", absenceId),
                null,
                Map.of("status", "PENDING"));
        return parentDashboard(parent, null, null).absences().stream()
                .filter(absence -> absence.absenceId().equals(absenceId))
                .findFirst()
                .orElseThrow(() -> new AttendanceNotFoundException("Absence introuvable"));
    }

    @Transactional
    public void readNotification(PortalIdentity parent, UUID notificationId) {
        int updated =
                jdbc.update(
                        """
                        UPDATE attendance_parent_notification SET status='READ',read_at=?
                        WHERE tenant_id=? AND portal_account_id=? AND id=?
                        """,
                        timestamp(clock.instant()),
                        parent.tenantId(),
                        parent.accountId(),
                        notificationId);
        if (updated == 0) {
            throw new AttendanceNotFoundException("Notification introuvable");
        }
    }

    private SessionView session(UUID tenantId, UUID sessionId) {
        SessionDetail detail =
                jdbc
                        .query(
                                """
                                SELECT s.id,s.session_date,s.subject_code,s.subject_name,s.starts_at,
                                       s.ends_at,s.status,s.submitted_at,s.classroom_id,c.name AS classroom_name,
                                       s.teacher_staff_id,CONCAT(p.first_name,' ',p.last_name) AS teacher_name
                                FROM attendance_session s
                                JOIN classroom c ON c.id=s.classroom_id AND c.tenant_id=s.tenant_id
                                JOIN staff_profile sp ON sp.id=s.teacher_staff_id AND sp.tenant_id=s.tenant_id
                                JOIN person p ON p.id=sp.person_id AND p.tenant_id=sp.tenant_id
                                WHERE s.tenant_id=? AND s.id=?
                                """,
                                (result, row) ->
                                        new SessionDetail(
                                                result.getObject("id", UUID.class),
                                                result.getObject("session_date", LocalDate.class),
                                                result.getString("subject_code"),
                                                result.getString("subject_name"),
                                                result.getObject("starts_at", LocalTime.class),
                                                result.getObject("ends_at", LocalTime.class),
                                                result.getString("status"),
                                                instant(result, "submitted_at"),
                                                result.getObject("classroom_id", UUID.class),
                                                result.getString("classroom_name"),
                                                result.getObject("teacher_staff_id", UUID.class),
                                                result.getString("teacher_name")),
                                tenantId,
                                sessionId)
                        .stream()
                        .findFirst()
                        .orElseThrow(() -> new AttendanceNotFoundException("Session introuvable"));
        List<RecordView> records =
                jdbc.query(
                        """
                        SELECT ar.id,ar.student_id,ar.status,ar.notes,
                               CONCAT(p.first_name,' ',p.last_name) AS student_name,
                               la.arrived_at,la.minutes_late,ed.left_at,ed.reason AS departure_reason,
                               ed.pickup_person_name,a.id AS absence_id,a.resolution_status,
                               j.id AS justification_id,j.review_status
                        FROM attendance_record ar
                        JOIN student_profile sp ON sp.id=ar.student_id AND sp.tenant_id=ar.tenant_id
                        JOIN person p ON p.id=sp.person_id AND p.tenant_id=sp.tenant_id
                        LEFT JOIN attendance_late_arrival la ON la.attendance_record_id=ar.id
                            AND la.tenant_id=ar.tenant_id
                        LEFT JOIN attendance_early_departure ed ON ed.attendance_record_id=ar.id
                            AND ed.tenant_id=ar.tenant_id
                        LEFT JOIN attendance_absence a ON a.attendance_record_id=ar.id
                            AND a.tenant_id=ar.tenant_id
                        LEFT JOIN attendance_excused_absence j ON j.absence_id=a.id
                            AND j.tenant_id=a.tenant_id
                        WHERE ar.tenant_id=? AND ar.session_id=?
                        ORDER BY p.last_name,p.first_name
                        """,
                        (result, row) ->
                                new RecordView(
                                        result.getObject("id", UUID.class),
                                        result.getObject("student_id", UUID.class),
                                        result.getString("student_name"),
                                        result.getString("status"),
                                        result.getString("notes"),
                                        result.getObject("arrived_at", LocalTime.class),
                                        nullableInteger(result, "minutes_late"),
                                        result.getObject("left_at", LocalTime.class),
                                        result.getString("departure_reason"),
                                        result.getString("pickup_person_name"),
                                        result.getObject("absence_id", UUID.class),
                                        result.getString("resolution_status"),
                                        result.getObject("justification_id", UUID.class),
                                        result.getString("review_status")),
                        tenantId,
                        sessionId);
        return new SessionView(
                detail.id(),
                detail.date(),
                detail.subjectCode(),
                detail.subjectName(),
                detail.startsAt(),
                detail.endsAt(),
                detail.status(),
                detail.submittedAt(),
                detail.classroomId(),
                detail.classroomName(),
                detail.teacherStaffId(),
                detail.teacherName(),
                records);
    }

    private SessionHeader requireOpenSession(UUID tenantId, UUID sessionId) {
        SessionHeader header =
                jdbc
                        .query(
                                "SELECT status,starts_at FROM attendance_session WHERE tenant_id=?"
                                        + " AND id=?",
                                (result, row) ->
                                        new SessionHeader(
                                                result.getString("status"),
                                                result.getObject("starts_at", LocalTime.class)),
                                tenantId,
                                sessionId)
                        .stream()
                        .findFirst()
                        .orElseThrow(() -> new AttendanceNotFoundException("Session introuvable"));
        if (!"OPEN".equals(header.status())) {
            throw new AttendanceValidationException("Cette session d'appel est déjà clôturée");
        }
        return header;
    }

    private void notifyParents(UUID tenantId, UUID absenceId, AbsentRow absent, Instant now) {
        List<UUID> parents =
                jdbc.query(
                        "SELECT portal_account_id FROM enrollment_portal_student_link"
                                + " WHERE tenant_id=? AND student_id=?",
                        (result, row) -> result.getObject(1, UUID.class),
                        tenantId,
                        absent.studentId());
        for (UUID portalId : parents) {
            jdbc.update(
                    """
                    INSERT INTO attendance_parent_notification
                        (id,tenant_id,portal_account_id,student_id,absence_id,notification_type,
                         title,message,status,created_at)
                    VALUES (?, ?, ?, ?, ?, 'ABSENCE_RECORDED', ?, ?, 'UNREAD', ?)
                    """,
                    UUID.randomUUID(),
                    tenantId,
                    portalId,
                    absent.studentId(),
                    absenceId,
                    "Absence enregistrée",
                    "Absence en "
                            + absent.subjectName()
                            + " ("
                            + absent.classroomName()
                            + ") le "
                            + absent.date()
                            + ". / تم تسجيل غياب.",
                    timestamp(now));
        }
    }

    private List<ReasonView> reasons(UUID tenantId) {
        return jdbc.query(
                """
                SELECT id,code,name_fr,name_ar,category,requires_document
                FROM attendance_absence_reason WHERE tenant_id=? AND active=TRUE ORDER BY name_fr
                """,
                (result, row) ->
                        new ReasonView(
                                result.getObject("id", UUID.class),
                                result.getString("code"),
                                result.getString("name_fr"),
                                result.getString("name_ar"),
                                result.getString("category"),
                                result.getBoolean("requires_document")),
                tenantId);
    }

    private JustificationView justification(ResultSet result) throws SQLException {
        return new JustificationView(
                result.getObject("id", UUID.class),
                result.getObject("absence_id", UUID.class),
                result.getString("first_name") + " " + result.getString("last_name"),
                result.getObject("occurred_on", LocalDate.class),
                result.getString("subject_name"),
                result.getString("classroom_name"),
                result.getObject("reason_id", UUID.class),
                result.getString("reason_name"),
                result.getString("parent_statement"),
                result.getString("evidence_object_key"),
                instant(result, "submitted_at"),
                result.getString("review_status"),
                instant(result, "reviewed_at"),
                result.getString("review_notes"));
    }

    private IncidentView incident(UUID tenantId, UUID id) {
        return jdbc
                .query(
                        """
                        SELECT i.id,i.student_id,CONCAT(p.first_name,' ',p.last_name) AS student_name,
                               i.classroom_id,i.occurred_at,i.category,i.severity,i.description,i.status
                        FROM disciplinary_incident i
                        JOIN student_profile sp ON sp.id=i.student_id AND sp.tenant_id=i.tenant_id
                        JOIN person p ON p.id=sp.person_id AND p.tenant_id=sp.tenant_id
                        WHERE i.tenant_id=? AND i.id=?
                        """,
                        (result, row) ->
                                new IncidentView(
                                        result.getObject("id", UUID.class),
                                        result.getObject("student_id", UUID.class),
                                        result.getString("student_name"),
                                        result.getObject("classroom_id", UUID.class),
                                        instant(result, "occurred_at"),
                                        result.getString("category"),
                                        result.getString("severity"),
                                        result.getString("description"),
                                        result.getString("status")),
                        tenantId,
                        id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new AttendanceNotFoundException("Incident introuvable"));
    }

    private NotificationView notification(ResultSet result) throws SQLException {
        return new NotificationView(
                result.getObject("id", UUID.class),
                result.getObject("student_id", UUID.class),
                result.getString("student_name"),
                result.getObject("absence_id", UUID.class),
                result.getString("notification_type"),
                result.getString("title"),
                result.getString("message"),
                result.getString("status"),
                instant(result, "created_at"),
                instant(result, "read_at"));
    }

    private SessionSummary sessionSummary(ResultSet result) throws SQLException {
        return new SessionSummary(
                result.getObject("id", UUID.class),
                result.getObject("session_date", LocalDate.class),
                result.getString("subject_code"),
                result.getString("subject_name"),
                result.getObject("starts_at", LocalTime.class),
                result.getObject("ends_at", LocalTime.class),
                result.getString("status"),
                result.getString("classroom_name"),
                result.getString("teacher_name"),
                result.getInt("total_count"),
                result.getInt("present_count"),
                result.getInt("absent_count"),
                result.getInt("late_count"));
    }

    private void requireReference(String table, UUID tenantId, UUID id) {
        if (id == null
                || count(
                                "SELECT count(*) FROM " + table + " WHERE tenant_id=? AND id=?",
                                tenantId,
                                id)
                        == 0) {
            throw new AttendanceNotFoundException("Référence introuvable");
        }
    }

    private int count(String sql, Object... arguments) {
        Integer value = jdbc.queryForObject(sql, Integer.class, arguments);
        return value == null ? 0 : value;
    }

    private static String normalize(String value) {
        return blank(value) ? "" : value.strip().toUpperCase(Locale.ROOT);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String nullable(String value) {
        return blank(value) ? null : value.strip();
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Integer nullableInteger(ResultSet result, String column) throws SQLException {
        int value = result.getInt(column);
        return result.wasNull() ? null : value;
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    public record ReferenceData(
            List<ClassroomOption> classrooms,
            List<TeacherOption> teachers,
            List<ReasonView> absenceReasons) {}

    public record ClassroomOption(
            UUID id, String code, String name, UUID campusId, UUID academicYearId) {}

    public record TeacherOption(UUID id, String employeeNumber, String name) {}

    public record ReasonView(
            UUID id,
            String code,
            String nameFr,
            String nameAr,
            String category,
            boolean requiresDocument) {}

    public record SessionSummary(
            UUID id,
            LocalDate date,
            String subjectCode,
            String subjectName,
            LocalTime startsAt,
            LocalTime endsAt,
            String status,
            String classroomName,
            String teacherName,
            int totalCount,
            int presentCount,
            int absentCount,
            int lateCount) {}

    public record SessionView(
            UUID id,
            LocalDate date,
            String subjectCode,
            String subjectName,
            LocalTime startsAt,
            LocalTime endsAt,
            String status,
            Instant submittedAt,
            UUID classroomId,
            String classroomName,
            UUID teacherStaffId,
            String teacherName,
            List<RecordView> records) {}

    public record RecordView(
            UUID id,
            UUID studentId,
            String studentName,
            String status,
            String notes,
            LocalTime arrivedAt,
            Integer minutesLate,
            LocalTime leftAt,
            String departureReason,
            String pickupPersonName,
            UUID absenceId,
            String absenceResolution,
            UUID justificationId,
            String justificationStatus) {}

    public record JustificationView(
            UUID id,
            UUID absenceId,
            String studentName,
            LocalDate absenceDate,
            String subjectName,
            String classroomName,
            UUID reasonId,
            String reasonName,
            String parentStatement,
            String evidenceObjectKey,
            Instant submittedAt,
            String reviewStatus,
            Instant reviewedAt,
            String reviewNotes) {}

    public record Statistics(
            LocalDate from,
            LocalDate to,
            int totalRecords,
            int presentCount,
            int absentCount,
            int lateCount,
            int earlyDepartureCount,
            int unexcusedCount,
            double absenceRate,
            List<TrendPoint> trend) {}

    public record TrendPoint(LocalDate date, int absentCount, int lateCount, int totalCount) {}

    public record ParentDashboard(
            LocalDate from,
            LocalDate to,
            List<ParentAbsence> absences,
            List<ReasonView> absenceReasons,
            List<NotificationView> notifications) {}

    public record ParentAbsence(
            UUID absenceId,
            UUID studentId,
            String studentName,
            LocalDate date,
            String subjectName,
            String classroomName,
            String resolutionStatus,
            String reasonName,
            UUID justificationId,
            String justificationStatus,
            String parentStatement) {}

    public record NotificationView(
            UUID id,
            UUID studentId,
            String studentName,
            UUID absenceId,
            String notificationType,
            String title,
            String message,
            String status,
            Instant createdAt,
            Instant readAt) {}

    public record IncidentView(
            UUID id,
            UUID studentId,
            String studentName,
            UUID classroomId,
            Instant occurredAt,
            String category,
            String severity,
            String description,
            String status) {}

    public record SanctionView(
            UUID id,
            UUID incidentId,
            String sanctionType,
            String description,
            LocalDate startsOn,
            LocalDate endsOn,
            String status) {}

    public record ObservationView(
            UUID id,
            UUID studentId,
            UUID classroomId,
            UUID teacherStaffId,
            LocalDate observationDate,
            String category,
            String observation,
            boolean visibleToParent) {}

    public record SessionCommand(
            UUID classroomId,
            UUID teacherStaffId,
            LocalDate sessionDate,
            String subjectCode,
            String subjectName,
            LocalTime startsAt,
            LocalTime endsAt) {}

    public record RecordsCommand(List<RecordCommand> records) {}

    public record RecordCommand(
            UUID recordId,
            String status,
            String notes,
            LocalTime arrivedAt,
            LocalTime leftAt,
            String departureReason,
            String pickupPersonName) {}

    public record ReviewCommand(String decision, String notes) {}

    public record JustificationCommand(UUID reasonId, String statement, String evidenceObjectKey) {}

    public record IncidentCommand(
            UUID studentId,
            UUID classroomId,
            Instant occurredAt,
            String category,
            String severity,
            String description) {}

    public record SanctionCommand(
            String sanctionType, String description, LocalDate startsOn, LocalDate endsOn) {}

    public record ObservationCommand(
            UUID studentId,
            UUID classroomId,
            UUID teacherStaffId,
            LocalDate observationDate,
            String category,
            String observation,
            boolean visibleToParent) {}

    private record Scope(UUID campusId, UUID academicYearId) {}

    private record EnrollmentRow(UUID enrollmentId, UUID studentId) {}

    private record AbsentRow(
            UUID recordId,
            UUID studentId,
            LocalDate date,
            String subjectName,
            String classroomName) {}

    private record SessionHeader(String status, LocalTime startsAt) {}

    private record SessionDetail(
            UUID id,
            LocalDate date,
            String subjectCode,
            String subjectName,
            LocalTime startsAt,
            LocalTime endsAt,
            String status,
            Instant submittedAt,
            UUID classroomId,
            String classroomName,
            UUID teacherStaffId,
            String teacherName) {}

    private record JustificationRow(
            UUID absenceId,
            UUID portalAccountId,
            UUID reasonId,
            UUID studentId,
            String reviewStatus) {}

    private record ExistingJustification(UUID id, String status) {}

    private record Aggregate(int total, int present, int absent, int late, int early) {}
}
