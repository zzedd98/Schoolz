package ma.schoolz.erp.timetable.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.identity.IdentityAuditTrail;
import ma.schoolz.erp.timetable.engine.TimetablePlanningEngine;
import ma.schoolz.erp.timetable.engine.TimetablePlanningEngine.Assignment;
import ma.schoolz.erp.timetable.engine.TimetablePlanningEngine.Conflict;
import ma.schoolz.erp.timetable.engine.TimetablePlanningEngine.OptimizationResult;
import ma.schoolz.erp.timetable.engine.TimetablePlanningEngine.Placement;
import ma.schoolz.erp.timetable.engine.TimetablePlanningEngine.PlanningSnapshot;
import ma.schoolz.erp.timetable.engine.TimetablePlanningEngine.Room;
import ma.schoolz.erp.timetable.engine.TimetablePlanningEngine.Slot;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TimetableService {
    private static final Set<String> AVAILABILITY =
            Set.of("AVAILABLE", "UNAVAILABLE", "PREFERRED", "AVOID");
    private static final Set<String> CONSTRAINT_TYPES =
            Set.of(
                    "MAX_HOURS_PER_DAY",
                    "MAX_CONSECUTIVE_HOURS",
                    "SUBJECT_HOURS_PER_WEEK",
                    "ROOM_CAPACITY",
                    "ROOM_EQUIPMENT",
                    "TIME_PREFERENCE");
    private static final Set<String> CONSTRAINT_SCOPES =
            Set.of("SCHEDULE", "TEACHER", "CLASSROOM", "SUBJECT", "ROOM", "ASSIGNMENT");

    private final JdbcTemplate jdbc;
    private final TimetablePlanningEngine engine;
    private final IdentityAuditTrail audit;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public TimetableService(
            JdbcTemplate jdbc,
            TimetablePlanningEngine engine,
            IdentityAuditTrail audit,
            ObjectMapper objectMapper,
            Clock clock) {
        this.jdbc = jdbc;
        this.engine = engine;
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('timetable.read')")
    public ReferenceData references(SchoolPrincipal actor) {
        List<CampusOption> campuses =
                jdbc.query(
                        "SELECT id,code,name FROM school_campus WHERE tenant_id=? AND active=TRUE"
                                + " ORDER BY name",
                        (result, row) ->
                                new CampusOption(
                                        uuid(result, "id"),
                                        result.getString("code"),
                                        result.getString("name")),
                        actor.tenantId());
        List<YearOption> years =
                jdbc.query(
                        "SELECT id,code,name,status,starts_on,ends_on FROM academic_year WHERE"
                                + " tenant_id=? ORDER BY starts_on DESC",
                        (result, row) ->
                                new YearOption(
                                        uuid(result, "id"),
                                        result.getString("code"),
                                        result.getString("name"),
                                        result.getString("status"),
                                        result.getObject("starts_on", LocalDate.class),
                                        result.getObject("ends_on", LocalDate.class)),
                        actor.tenantId());
        List<ClassroomOption> classrooms =
                jdbc.query(
                        "SELECT id,campus_id,academic_year_id,code,name,capacity FROM classroom"
                                + " WHERE tenant_id=? AND active=TRUE ORDER BY name",
                        (result, row) ->
                                new ClassroomOption(
                                        uuid(result, "id"),
                                        uuid(result, "campus_id"),
                                        uuid(result, "academic_year_id"),
                                        result.getString("code"),
                                        result.getString("name"),
                                        result.getInt("capacity")),
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
                                        uuid(result, "id"),
                                        result.getString("employee_number"),
                                        result.getString("name")),
                        actor.tenantId());
        List<RoomView> rooms = rooms(actor.tenantId(), null);
        return new ReferenceData(campuses, years, classrooms, teachers, rooms);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('timetable.read')")
    public List<SubjectView> subjects(SchoolPrincipal actor) {
        return subjects(actor.tenantId());
    }

    @Transactional
    @PreAuthorize("hasAuthority('timetable.write')")
    public SubjectView createSubject(
            SchoolPrincipal actor, SubjectCommand command, String ipAddress) {
        if (command == null || blank(command.code()) || blank(command.nameFr())) {
            throw new TimetableValidationException("Le code et le nom de la matière sont requis");
        }
        String color = blank(command.color()) ? "#2563eb" : command.color().strip();
        if (!color.matches("#[0-9A-Fa-f]{6}")) {
            throw new TimetableValidationException("La couleur doit utiliser le format #RRGGBB");
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        try {
            jdbc.update(
                    """
                    INSERT INTO timetable_subject
                        (id,tenant_id,code,name_fr,name_ar,color,active,created_at,updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, TRUE, ?, ?)
                    """,
                    id,
                    actor.tenantId(),
                    command.code().strip().toUpperCase(Locale.ROOT),
                    command.nameFr().strip(),
                    trim(command.nameAr()),
                    color,
                    timestamp(now),
                    timestamp(now));
        } catch (DuplicateKeyException exception) {
            throw new TimetableValidationException("Ce code matière existe déjà");
        }
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "timetable.subject.created",
                "Subject",
                id.toString(),
                ipAddress,
                Map.of("code", command.code().strip().toUpperCase(Locale.ROOT)),
                null,
                Map.of("active", true));
        return subject(actor.tenantId(), id);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('timetable.read')")
    public List<ScheduleSummary> schedules(SchoolPrincipal actor) {
        return jdbc.query(
                """
                SELECT s.id,s.code,s.name,s.status,s.valid_from,s.valid_to,s.campus_id,
                       c.name AS campus_name,s.academic_year_id,y.name AS year_name,
                       (SELECT count(*) FROM timetable_lesson l
                         WHERE l.tenant_id=s.tenant_id AND l.schedule_id=s.id) AS lesson_count
                FROM timetable_schedule s
                JOIN school_campus c ON c.id=s.campus_id AND c.tenant_id=s.tenant_id
                JOIN academic_year y ON y.id=s.academic_year_id AND y.tenant_id=s.tenant_id
                WHERE s.tenant_id=?
                ORDER BY s.valid_from DESC,s.name
                """,
                (result, row) -> scheduleSummary(result),
                actor.tenantId());
    }

    @Transactional
    @PreAuthorize("hasAuthority('timetable.write')")
    public Board createSchedule(SchoolPrincipal actor, ScheduleCommand command, String ipAddress) {
        validateScheduleCommand(command);
        Scope scope = requireScope(actor.tenantId(), command.campusId(), command.academicYearId());
        if (command.validFrom().isBefore(scope.yearStartsOn())
                || command.validTo().isAfter(scope.yearEndsOn())) {
            throw new TimetableValidationException(
                    "La validité du planning doit rester dans l'année scolaire");
        }
        UUID scheduleId = UUID.randomUUID();
        Instant now = clock.instant();
        try {
            jdbc.update(
                    """
                    INSERT INTO timetable_schedule
                        (id,tenant_id,campus_id,academic_year_id,code,name,status,valid_from,
                         valid_to,created_by_user_id,created_at,updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?, ?)
                    """,
                    scheduleId,
                    actor.tenantId(),
                    command.campusId(),
                    command.academicYearId(),
                    command.code().strip().toUpperCase(Locale.ROOT),
                    command.name().strip(),
                    command.validFrom(),
                    command.validTo(),
                    actor.accountId(),
                    timestamp(now),
                    timestamp(now));
        } catch (DuplicateKeyException exception) {
            throw new TimetableValidationException("Ce code de planning existe déjà");
        }
        Set<Integer> sequences = new HashSet<>();
        for (PeriodCommand period : command.periods()) {
            validatePeriod(period, sequences);
            UUID periodId = UUID.randomUUID();
            jdbc.update(
                    """
                    INSERT INTO timetable_period
                        (id,tenant_id,schedule_id,code,name,sequence_number,starts_at,ends_at,teaching)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    periodId,
                    actor.tenantId(),
                    scheduleId,
                    period.code().strip().toUpperCase(Locale.ROOT),
                    period.name().strip(),
                    period.sequenceNumber(),
                    period.startsAt(),
                    period.endsAt(),
                    period.teaching());
            for (Integer day : command.workingDays()) {
                jdbc.update(
                        "INSERT INTO timetable_time_slot"
                                + " (id,tenant_id,schedule_id,period_id,day_of_week)"
                                + " VALUES (?, ?, ?, ?, ?)",
                        UUID.randomUUID(),
                        actor.tenantId(),
                        scheduleId,
                        periodId,
                        day);
            }
        }
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "timetable.schedule.created",
                "Schedule",
                scheduleId.toString(),
                ipAddress,
                Map.of(
                        "campusId",
                        command.campusId(),
                        "periodCount",
                        command.periods().size(),
                        "workingDays",
                        command.workingDays().size()),
                null,
                Map.of("status", "DRAFT"));
        return board(actor.tenantId(), scheduleId);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('timetable.read')")
    public Board board(SchoolPrincipal actor, UUID scheduleId) {
        return board(actor.tenantId(), scheduleId);
    }

    @Transactional
    @PreAuthorize("hasAuthority('timetable.write')")
    public RoomView addEquipment(
            SchoolPrincipal actor, UUID roomId, RoomEquipmentCommand command, String ipAddress) {
        if (command == null || blank(command.equipmentCode()) || command.quantity() < 1) {
            throw new TimetableValidationException("L'équipement et sa quantité sont requis");
        }
        requireTenantReference("physical_room", actor.tenantId(), roomId, "Salle introuvable");
        String code = command.equipmentCode().strip().toUpperCase(Locale.ROOT);
        jdbc.update(
                "DELETE FROM timetable_room_equipment WHERE tenant_id=? AND room_id=? AND"
                        + " equipment_code=?",
                actor.tenantId(),
                roomId,
                code);
        jdbc.update(
                "INSERT INTO timetable_room_equipment"
                        + " (id,tenant_id,room_id,equipment_code,quantity) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                actor.tenantId(),
                roomId,
                code,
                command.quantity());
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "timetable.room.equipment.updated",
                "Room",
                roomId.toString(),
                ipAddress,
                Map.of("equipment", code, "quantity", command.quantity()),
                null,
                null);
        return rooms(actor.tenantId(), null).stream()
                .filter(room -> room.id().equals(roomId))
                .findFirst()
                .orElseThrow(() -> new TimetableNotFoundException("Salle introuvable"));
    }

    @Transactional
    @PreAuthorize("hasAuthority('timetable.write')")
    public AssignmentView createAssignment(
            SchoolPrincipal actor, UUID scheduleId, AssignmentCommand command, String ipAddress) {
        requireDraft(actor.tenantId(), scheduleId);
        if (command == null
                || command.classroomId() == null
                || command.subjectId() == null
                || command.teacherStaffId() == null
                || command.weeklyPeriods() < 1
                || command.weeklyPeriods() > 20) {
            throw new TimetableValidationException("L'affectation enseignant est incomplète");
        }
        ScheduleScope schedule = scheduleScope(actor.tenantId(), scheduleId);
        Integer classroomMatches =
                jdbc.queryForObject(
                        "SELECT count(*) FROM classroom WHERE tenant_id=? AND id=? AND campus_id=?"
                                + " AND academic_year_id=? AND active=TRUE",
                        Integer.class,
                        actor.tenantId(),
                        command.classroomId(),
                        schedule.campusId(),
                        schedule.academicYearId());
        if (classroomMatches == null || classroomMatches == 0) {
            throw new TimetableValidationException(
                    "La classe ne correspond pas au campus et à l'année du planning");
        }
        requireTenantReference(
                "timetable_subject", actor.tenantId(), command.subjectId(), "Matière introuvable");
        requireTenantReference(
                "staff_profile",
                actor.tenantId(),
                command.teacherStaffId(),
                "Enseignant introuvable");
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        String equipment = joinEquipment(command.requiredEquipment());
        try {
            jdbc.update(
                    """
                    INSERT INTO timetable_teacher_assignment
                        (id,tenant_id,schedule_id,classroom_id,subject_id,teacher_staff_id,
                         weekly_periods,required_room_type,required_equipment,active,created_at,updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?)
                    """,
                    id,
                    actor.tenantId(),
                    scheduleId,
                    command.classroomId(),
                    command.subjectId(),
                    command.teacherStaffId(),
                    command.weeklyPeriods(),
                    upper(command.requiredRoomType()),
                    equipment,
                    timestamp(now),
                    timestamp(now));
        } catch (DuplicateKeyException exception) {
            throw new TimetableValidationException("Cette affectation existe déjà");
        }
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "timetable.assignment.created",
                "TeacherAssignment",
                id.toString(),
                ipAddress,
                Map.of(
                        "scheduleId", scheduleId,
                        "classroomId", command.classroomId(),
                        "weeklyPeriods", command.weeklyPeriods()),
                null,
                Map.of("active", true));
        return assignment(actor.tenantId(), id);
    }

    @Transactional
    @PreAuthorize("hasAuthority('timetable.write')")
    public AvailabilityView teacherAvailability(
            SchoolPrincipal actor,
            UUID scheduleId,
            UUID teacherId,
            UUID slotId,
            AvailabilityCommand command,
            String ipAddress) {
        requireDraft(actor.tenantId(), scheduleId);
        validateAvailability(command);
        requireTenantReference(
                "staff_profile", actor.tenantId(), teacherId, "Enseignant introuvable");
        requireSlot(actor.tenantId(), scheduleId, slotId);
        replaceAvailability(
                "timetable_teacher_availability",
                "teacher_staff_id",
                actor.tenantId(),
                scheduleId,
                teacherId,
                slotId,
                command);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "timetable.teacher.availability.updated",
                "TeacherAvailability",
                teacherId + ":" + slotId,
                ipAddress,
                Map.of("scheduleId", scheduleId),
                null,
                Map.of("availability", normalize(command.availability())));
        return new AvailabilityView(
                "TEACHER",
                teacherId,
                slotId,
                normalize(command.availability()),
                trim(command.note()));
    }

    @Transactional
    @PreAuthorize("hasAuthority('timetable.write')")
    public AvailabilityView roomAvailability(
            SchoolPrincipal actor,
            UUID scheduleId,
            UUID roomId,
            UUID slotId,
            AvailabilityCommand command,
            String ipAddress) {
        requireDraft(actor.tenantId(), scheduleId);
        validateAvailability(command);
        requireRoomForSchedule(actor.tenantId(), scheduleId, roomId);
        requireSlot(actor.tenantId(), scheduleId, slotId);
        replaceAvailability(
                "timetable_room_availability",
                "room_id",
                actor.tenantId(),
                scheduleId,
                roomId,
                slotId,
                command);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "timetable.room.availability.updated",
                "RoomAvailability",
                roomId + ":" + slotId,
                ipAddress,
                Map.of("scheduleId", scheduleId),
                null,
                Map.of("availability", normalize(command.availability())));
        return new AvailabilityView(
                "ROOM", roomId, slotId, normalize(command.availability()), trim(command.note()));
    }

    @Transactional
    @PreAuthorize("hasAuthority('timetable.write')")
    public ConstraintView createConstraint(
            SchoolPrincipal actor, UUID scheduleId, ConstraintCommand command, String ipAddress) {
        requireDraft(actor.tenantId(), scheduleId);
        validateConstraint(command);
        String type = normalize(command.constraintType());
        String scope = normalize(command.scopeType());
        String configuration = canonicalJson(command.configuration());
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        if (command.targetId() == null) {
            jdbc.update(
                    "DELETE FROM timetable_constraint WHERE tenant_id=? AND schedule_id=? AND"
                            + " constraint_type=? AND scope_type=? AND target_id IS NULL",
                    actor.tenantId(),
                    scheduleId,
                    type,
                    scope);
        } else {
            jdbc.update(
                    "DELETE FROM timetable_constraint WHERE tenant_id=? AND schedule_id=? AND"
                            + " constraint_type=? AND scope_type=? AND target_id=?",
                    actor.tenantId(),
                    scheduleId,
                    type,
                    scope,
                    command.targetId());
        }
        jdbc.update(
                """
                INSERT INTO timetable_constraint
                    (id,tenant_id,schedule_id,constraint_type,scope_type,target_id,configuration,
                     hard_constraint,weight,enabled,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?)
                """,
                id,
                actor.tenantId(),
                scheduleId,
                type,
                scope,
                command.targetId(),
                configuration,
                command.hardConstraint(),
                command.weight(),
                timestamp(now),
                timestamp(now));
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "timetable.constraint.updated",
                "Constraint",
                id.toString(),
                ipAddress,
                Map.of("scheduleId", scheduleId, "type", type, "scope", scope),
                null,
                Map.of("hard", command.hardConstraint(), "weight", command.weight()));
        return constraint(actor.tenantId(), id);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('timetable.read')")
    public ValidationView validatePlacement(
            SchoolPrincipal actor, UUID scheduleId, PlacementCommand command) {
        requirePlacementCommand(command);
        PlanningSnapshot snapshot = snapshot(actor.tenantId(), scheduleId);
        Assignment assignment = snapshot.assignments().get(command.assignmentId());
        if (assignment == null) {
            throw new TimetableNotFoundException("Affectation introuvable");
        }
        Placement placement =
                new Placement(
                        UUID.randomUUID(),
                        assignment.id(),
                        assignment.classroomId(),
                        assignment.subjectId(),
                        assignment.teacherId(),
                        command.roomId(),
                        command.slotId());
        List<Conflict> conflicts = engine.conflicts(snapshot, placement);
        return new ValidationView(conflicts.stream().noneMatch(Conflict::hard), conflicts);
    }

    @Transactional
    @PreAuthorize("hasAuthority('timetable.write')")
    public LessonView createLesson(
            SchoolPrincipal actor, UUID scheduleId, PlacementCommand command, String ipAddress) {
        requireDraft(actor.tenantId(), scheduleId);
        requirePlacementCommand(command);
        PlanningSnapshot snapshot = snapshot(actor.tenantId(), scheduleId);
        Assignment assignment = requireAssignment(snapshot, command.assignmentId());
        Placement placement =
                new Placement(
                        UUID.randomUUID(),
                        assignment.id(),
                        assignment.classroomId(),
                        assignment.subjectId(),
                        assignment.teacherId(),
                        command.roomId(),
                        command.slotId());
        requireNoHardConflict(engine.conflicts(snapshot, placement));
        persistLesson(
                actor,
                scheduleId,
                placement,
                command.title(),
                command.notes(),
                "MANUAL",
                ipAddress);
        return lesson(actor.tenantId(), placement.id());
    }

    @Transactional
    @PreAuthorize("hasAuthority('timetable.write')")
    public LessonView moveLesson(
            SchoolPrincipal actor,
            UUID scheduleId,
            UUID lessonId,
            MoveCommand command,
            String ipAddress) {
        requireDraft(actor.tenantId(), scheduleId);
        if (command == null || command.roomId() == null || command.slotId() == null) {
            throw new TimetableValidationException("La salle et le créneau sont requis");
        }
        LessonIdentity current = lessonIdentity(actor.tenantId(), scheduleId, lessonId);
        PlanningSnapshot snapshot = snapshot(actor.tenantId(), scheduleId);
        Assignment assignment = requireAssignment(snapshot, current.assignmentId());
        Placement placement =
                new Placement(
                        lessonId,
                        assignment.id(),
                        assignment.classroomId(),
                        assignment.subjectId(),
                        assignment.teacherId(),
                        command.roomId(),
                        command.slotId());
        requireNoHardConflict(engine.conflicts(snapshot, placement));
        try {
            jdbc.update(
                    "UPDATE timetable_lesson SET room_id=?,time_slot_id=?,updated_at=? WHERE"
                            + " tenant_id=? AND schedule_id=? AND id=?",
                    command.roomId(),
                    command.slotId(),
                    timestamp(clock.instant()),
                    actor.tenantId(),
                    scheduleId,
                    lessonId);
        } catch (DuplicateKeyException exception) {
            throw new TimetableConflictException(
                    List.of(
                            new Conflict(
                                    "OCCUPANCY_CONFLICT", "HARD", "Le placement est déjà occupé")));
        }
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "timetable.lesson.moved",
                "Lesson",
                lessonId.toString(),
                ipAddress,
                Map.of("scheduleId", scheduleId),
                Map.of("roomId", current.roomId(), "slotId", current.slotId()),
                Map.of("roomId", command.roomId(), "slotId", command.slotId()));
        return lesson(actor.tenantId(), lessonId);
    }

    @Transactional
    @PreAuthorize("hasAuthority('timetable.write')")
    public void deleteLesson(
            SchoolPrincipal actor, UUID scheduleId, UUID lessonId, String ipAddress) {
        requireDraft(actor.tenantId(), scheduleId);
        LessonIdentity current = lessonIdentity(actor.tenantId(), scheduleId, lessonId);
        jdbc.update(
                "DELETE FROM timetable_lesson WHERE tenant_id=? AND schedule_id=? AND id=?",
                actor.tenantId(),
                scheduleId,
                lessonId);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "timetable.lesson.deleted",
                "Lesson",
                lessonId.toString(),
                ipAddress,
                Map.of("scheduleId", scheduleId),
                Map.of("roomId", current.roomId(), "slotId", current.slotId()),
                null);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('timetable.optimize')")
    public OptimizationView optimize(SchoolPrincipal actor, UUID scheduleId) {
        requireDraft(actor.tenantId(), scheduleId);
        PlanningSnapshot snapshot = snapshot(actor.tenantId(), scheduleId);
        OptimizationResult result = engine.optimize(snapshot);
        List<PlacementProposal> proposals =
                result.proposals().stream()
                        .map(
                                placement ->
                                        new PlacementProposal(
                                                placement.assignmentId(),
                                                placement.roomId(),
                                                placement.slotId()))
                        .toList();
        return new OptimizationView(proposals, result.unscheduled(), result.score());
    }

    @Transactional
    @PreAuthorize("hasAuthority('timetable.optimize')")
    public Board applyOptimization(
            SchoolPrincipal actor,
            UUID scheduleId,
            ApplyOptimizationCommand command,
            String ipAddress) {
        requireDraft(actor.tenantId(), scheduleId);
        if (command == null || command.proposals() == null || command.proposals().isEmpty()) {
            throw new TimetableValidationException("Aucune proposition à appliquer");
        }
        int applied = 0;
        for (PlacementProposal proposal : command.proposals()) {
            PlanningSnapshot snapshot = snapshot(actor.tenantId(), scheduleId);
            Assignment assignment = requireAssignment(snapshot, proposal.assignmentId());
            Placement placement =
                    new Placement(
                            UUID.randomUUID(),
                            assignment.id(),
                            assignment.classroomId(),
                            assignment.subjectId(),
                            assignment.teacherId(),
                            proposal.roomId(),
                            proposal.slotId());
            requireNoHardConflict(engine.conflicts(snapshot, placement));
            persistLesson(actor, scheduleId, placement, null, null, "OPTIMIZER", ipAddress);
            applied++;
        }
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "timetable.optimization.applied",
                "Schedule",
                scheduleId.toString(),
                ipAddress,
                Map.of("proposalCount", command.proposals().size()),
                null,
                Map.of("applied", applied));
        return board(actor.tenantId(), scheduleId);
    }

    @Transactional
    @PreAuthorize("hasAuthority('timetable.publish')")
    public Board publish(SchoolPrincipal actor, UUID scheduleId, String ipAddress) {
        requireDraft(actor.tenantId(), scheduleId);
        PlanningSnapshot snapshot = snapshot(actor.tenantId(), scheduleId);
        List<Conflict> conflicts = new ArrayList<>();
        for (Placement placement : snapshot.placements()) {
            conflicts.addAll(
                    engine.conflicts(snapshot, placement).stream().filter(Conflict::hard).toList());
        }
        for (Assignment assignment : snapshot.assignments().values()) {
            long count =
                    snapshot.placements().stream()
                            .filter(value -> value.assignmentId().equals(assignment.id()))
                            .count();
            if (count != assignment.weeklyPeriods()) {
                conflicts.add(
                        new Conflict(
                                "SUBJECT_WEEKLY_VOLUME",
                                "HARD",
                                "Une affectation ne possède pas son volume hebdomadaire complet"));
            }
        }
        requireNoHardConflict(conflicts);
        jdbc.update(
                "UPDATE timetable_schedule SET status='PUBLISHED',updated_at=? WHERE tenant_id=?"
                        + " AND id=?",
                timestamp(clock.instant()),
                actor.tenantId(),
                scheduleId);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "timetable.schedule.published",
                "Schedule",
                scheduleId.toString(),
                ipAddress,
                Map.of("lessonCount", snapshot.placements().size()),
                Map.of("status", "DRAFT"),
                Map.of("status", "PUBLISHED"));
        return board(actor.tenantId(), scheduleId);
    }

    private Board board(UUID tenantId, UUID scheduleId) {
        ScheduleSummary schedule =
                jdbc
                        .query(
                                """
                                SELECT s.id,s.code,s.name,s.status,s.valid_from,s.valid_to,s.campus_id,
                                       c.name AS campus_name,s.academic_year_id,y.name AS year_name,
                                       (SELECT count(*) FROM timetable_lesson l
                                         WHERE l.tenant_id=s.tenant_id AND l.schedule_id=s.id) AS lesson_count
                                FROM timetable_schedule s
                                JOIN school_campus c ON c.id=s.campus_id AND c.tenant_id=s.tenant_id
                                JOIN academic_year y ON y.id=s.academic_year_id AND y.tenant_id=s.tenant_id
                                WHERE s.tenant_id=? AND s.id=?
                                """,
                                (result, row) -> scheduleSummary(result),
                                tenantId,
                                scheduleId)
                        .stream()
                        .findFirst()
                        .orElseThrow(() -> new TimetableNotFoundException("Planning introuvable"));
        List<PeriodView> periods =
                jdbc.query(
                        "SELECT id,code,name,sequence_number,starts_at,ends_at,teaching FROM"
                                + " timetable_period WHERE tenant_id=? AND schedule_id=? ORDER BY"
                                + " sequence_number",
                        (result, row) ->
                                new PeriodView(
                                        uuid(result, "id"),
                                        result.getString("code"),
                                        result.getString("name"),
                                        result.getInt("sequence_number"),
                                        result.getObject("starts_at", LocalTime.class),
                                        result.getObject("ends_at", LocalTime.class),
                                        result.getBoolean("teaching")),
                        tenantId,
                        scheduleId);
        List<TimeSlotView> slots =
                jdbc.query(
                        """
                        SELECT ts.id,ts.period_id,ts.day_of_week,p.sequence_number,p.starts_at,p.ends_at,p.teaching
                        FROM timetable_time_slot ts
                        JOIN timetable_period p ON p.id=ts.period_id AND p.tenant_id=ts.tenant_id
                        WHERE ts.tenant_id=? AND ts.schedule_id=?
                        ORDER BY ts.day_of_week,p.sequence_number
                        """,
                        (result, row) ->
                                new TimeSlotView(
                                        uuid(result, "id"),
                                        uuid(result, "period_id"),
                                        result.getInt("day_of_week"),
                                        result.getInt("sequence_number"),
                                        result.getObject("starts_at", LocalTime.class),
                                        result.getObject("ends_at", LocalTime.class),
                                        result.getBoolean("teaching")),
                        tenantId,
                        scheduleId);
        List<AssignmentView> assignments = assignments(tenantId, scheduleId);
        List<LessonView> lessons =
                jdbc.query(
                        lessonSql()
                                + " WHERE l.tenant_id=? AND l.schedule_id=? ORDER BY"
                                + " ts.day_of_week,p.sequence_number",
                        (result, row) -> lessonView(result),
                        tenantId,
                        scheduleId);
        List<AvailabilityView> availabilities = new ArrayList<>();
        availabilities.addAll(
                jdbc.query(
                        "SELECT teacher_staff_id AS target_id,time_slot_id,availability,note FROM"
                            + " timetable_teacher_availability WHERE tenant_id=? AND schedule_id=?",
                        (result, row) ->
                                new AvailabilityView(
                                        "TEACHER",
                                        uuid(result, "target_id"),
                                        uuid(result, "time_slot_id"),
                                        result.getString("availability"),
                                        result.getString("note")),
                        tenantId,
                        scheduleId));
        availabilities.addAll(
                jdbc.query(
                        "SELECT room_id AS target_id,time_slot_id,availability,note FROM"
                            + " timetable_room_availability WHERE tenant_id=? AND schedule_id=?",
                        (result, row) ->
                                new AvailabilityView(
                                        "ROOM",
                                        uuid(result, "target_id"),
                                        uuid(result, "time_slot_id"),
                                        result.getString("availability"),
                                        result.getString("note")),
                        tenantId,
                        scheduleId));
        List<ConstraintView> constraints =
                jdbc.query(
                        "SELECT"
                            + " id,constraint_type,scope_type,target_id,configuration,hard_constraint,weight,enabled"
                            + " FROM timetable_constraint WHERE tenant_id=? AND schedule_id=? ORDER"
                            + " BY constraint_type,scope_type",
                        (result, row) -> constraintView(result),
                        tenantId,
                        scheduleId);
        return new Board(
                schedule,
                subjects(tenantId),
                periods,
                slots,
                rooms(tenantId, schedule.campusId()),
                assignments,
                lessons,
                List.copyOf(availabilities),
                constraints);
    }

    private PlanningSnapshot snapshot(UUID tenantId, UUID scheduleId) {
        ScheduleScope scope = scheduleScope(tenantId, scheduleId);
        Map<UUID, Slot> slots =
                jdbc
                        .query(
                                """
                                SELECT ts.id,ts.day_of_week,p.sequence_number
                                FROM timetable_time_slot ts
                                JOIN timetable_period p ON p.id=ts.period_id AND p.tenant_id=ts.tenant_id
                                WHERE ts.tenant_id=? AND ts.schedule_id=? AND p.teaching=TRUE
                                """,
                                (result, row) ->
                                        new Slot(
                                                uuid(result, "id"),
                                                result.getInt("day_of_week"),
                                                result.getInt("sequence_number")),
                                tenantId,
                                scheduleId)
                        .stream()
                        .collect(Collectors.toMap(Slot::id, value -> value));
        Map<UUID, Room> rooms = new LinkedHashMap<>();
        for (RoomView room : rooms(tenantId, scope.campusId())) {
            rooms.put(
                    room.id(),
                    new Room(
                            room.id(),
                            room.name(),
                            room.roomType(),
                            room.capacity(),
                            room.equipment().stream()
                                    .map(RoomEquipmentView::code)
                                    .collect(Collectors.toSet())));
        }
        Map<UUID, Assignment> assignments =
                jdbc
                        .query(
                                """
                                SELECT a.id,a.classroom_id,a.subject_id,a.teacher_staff_id,
                                       a.weekly_periods,a.required_room_type,a.required_equipment,
                                       c.capacity AS classroom_capacity
                                FROM timetable_teacher_assignment a
                                JOIN classroom c ON c.id=a.classroom_id AND c.tenant_id=a.tenant_id
                                WHERE a.tenant_id=? AND a.schedule_id=? AND a.active=TRUE
                                """,
                                (result, row) ->
                                        new Assignment(
                                                uuid(result, "id"),
                                                uuid(result, "classroom_id"),
                                                uuid(result, "subject_id"),
                                                uuid(result, "teacher_staff_id"),
                                                result.getInt("classroom_capacity"),
                                                result.getInt("weekly_periods"),
                                                result.getString("required_room_type"),
                                                splitEquipment(
                                                        result.getString("required_equipment"))),
                                tenantId,
                                scheduleId)
                        .stream()
                        .collect(Collectors.toMap(Assignment::id, value -> value));
        List<Placement> placements =
                jdbc.query(
                        "SELECT id,assignment_id,classroom_id,subject_id,teacher_staff_id,room_id,"
                                + "time_slot_id FROM timetable_lesson WHERE tenant_id=? AND"
                                + " schedule_id=?",
                        (result, row) ->
                                new Placement(
                                        uuid(result, "id"),
                                        uuid(result, "assignment_id"),
                                        uuid(result, "classroom_id"),
                                        uuid(result, "subject_id"),
                                        uuid(result, "teacher_staff_id"),
                                        uuid(result, "room_id"),
                                        uuid(result, "time_slot_id")),
                        tenantId,
                        scheduleId);
        Map<UUID, Map<UUID, String>> teacherAvailability = new HashMap<>();
        jdbc.query(
                "SELECT teacher_staff_id,time_slot_id,availability FROM"
                        + " timetable_teacher_availability WHERE tenant_id=? AND schedule_id=?",
                result -> {
                    teacherAvailability
                            .computeIfAbsent(
                                    uuid(result, "teacher_staff_id"), ignored -> new HashMap<>())
                            .put(uuid(result, "time_slot_id"), result.getString("availability"));
                },
                tenantId,
                scheduleId);
        Map<UUID, Map<UUID, String>> roomAvailability = new HashMap<>();
        jdbc.query(
                "SELECT room_id,time_slot_id,availability FROM timetable_room_availability WHERE"
                        + " tenant_id=? AND schedule_id=?",
                result -> {
                    roomAvailability
                            .computeIfAbsent(uuid(result, "room_id"), ignored -> new HashMap<>())
                            .put(uuid(result, "time_slot_id"), result.getString("availability"));
                },
                tenantId,
                scheduleId);
        List<ConstraintInternal> constraintRows =
                jdbc.query(
                        "SELECT constraint_type,target_id,configuration,hard_constraint FROM"
                                + " timetable_constraint WHERE tenant_id=? AND schedule_id=? AND"
                                + " enabled=TRUE",
                        (result, row) ->
                                new ConstraintInternal(
                                        result.getString("constraint_type"),
                                        uuidNullable(result, "target_id"),
                                        result.getString("configuration"),
                                        result.getBoolean("hard_constraint")),
                        tenantId,
                        scheduleId);
        Map<UUID, Integer> maxDaily = new HashMap<>();
        Map<UUID, Integer> maxConsecutive = new HashMap<>();
        boolean maxDailyHard = true;
        boolean maxConsecutiveHard = true;
        for (ConstraintInternal constraint : constraintRows) {
            if (constraint.targetId() == null) continue;
            if (constraint.type().equals("MAX_HOURS_PER_DAY")) {
                maxDaily.put(constraint.targetId(), integerConfig(constraint.configuration(), 8));
                maxDailyHard &= constraint.hard();
            } else if (constraint.type().equals("MAX_CONSECUTIVE_HOURS")) {
                maxConsecutive.put(
                        constraint.targetId(), integerConfig(constraint.configuration(), 4));
                maxConsecutiveHard &= constraint.hard();
            }
        }
        return new PlanningSnapshot(
                slots,
                rooms,
                assignments,
                placements,
                teacherAvailability,
                roomAvailability,
                maxDaily,
                maxConsecutive,
                maxDailyHard,
                maxConsecutiveHard,
                true);
    }

    private void persistLesson(
            SchoolPrincipal actor,
            UUID scheduleId,
            Placement placement,
            String title,
            String notes,
            String source,
            String ipAddress) {
        Instant now = clock.instant();
        try {
            jdbc.update(
                    """
                    INSERT INTO timetable_lesson
                        (id,tenant_id,schedule_id,assignment_id,classroom_id,subject_id,
                         teacher_staff_id,room_id,time_slot_id,title,notes,source,
                         created_by_user_id,created_at,updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    placement.id(),
                    actor.tenantId(),
                    scheduleId,
                    placement.assignmentId(),
                    placement.classroomId(),
                    placement.subjectId(),
                    placement.teacherId(),
                    placement.roomId(),
                    placement.slotId(),
                    trim(title),
                    trim(notes),
                    source,
                    actor.accountId(),
                    timestamp(now),
                    timestamp(now));
        } catch (DuplicateKeyException exception) {
            throw new TimetableConflictException(
                    List.of(
                            new Conflict(
                                    "OCCUPANCY_CONFLICT",
                                    "HARD",
                                    "L'enseignant, la salle ou la classe est déjà occupé")));
        }
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "timetable.lesson.created",
                "Lesson",
                placement.id().toString(),
                ipAddress,
                Map.of(
                        "scheduleId", scheduleId,
                        "assignmentId", placement.assignmentId(),
                        "source", source),
                null,
                Map.of("roomId", placement.roomId(), "slotId", placement.slotId()));
    }

    private static String lessonSql() {
        return """
        SELECT l.id,l.schedule_id,l.assignment_id,l.classroom_id,c.name AS classroom_name,
               l.subject_id,s.name_fr AS subject_name,s.color,l.teacher_staff_id,
               CONCAT(person.first_name,' ',person.last_name) AS teacher_name,
               l.room_id,r.name AS room_name,l.time_slot_id,ts.day_of_week,
               p.sequence_number,p.starts_at,p.ends_at,l.title,l.notes,l.source
        FROM timetable_lesson l
        JOIN classroom c ON c.id=l.classroom_id AND c.tenant_id=l.tenant_id
        JOIN timetable_subject s ON s.id=l.subject_id AND s.tenant_id=l.tenant_id
        JOIN staff_profile staff ON staff.id=l.teacher_staff_id AND staff.tenant_id=l.tenant_id
        JOIN person ON person.id=staff.person_id AND person.tenant_id=staff.tenant_id
        JOIN physical_room r ON r.id=l.room_id AND r.tenant_id=l.tenant_id
        JOIN timetable_time_slot ts ON ts.id=l.time_slot_id AND ts.tenant_id=l.tenant_id
        JOIN timetable_period p ON p.id=ts.period_id AND p.tenant_id=ts.tenant_id
        """;
    }

    private LessonView lesson(UUID tenantId, UUID lessonId) {
        return jdbc
                .query(
                        lessonSql() + " WHERE l.tenant_id=? AND l.id=?",
                        (result, row) -> lessonView(result),
                        tenantId,
                        lessonId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new TimetableNotFoundException("Cours introuvable"));
    }

    private LessonIdentity lessonIdentity(UUID tenantId, UUID scheduleId, UUID lessonId) {
        return jdbc
                .query(
                        "SELECT assignment_id,room_id,time_slot_id FROM timetable_lesson WHERE"
                                + " tenant_id=? AND schedule_id=? AND id=?",
                        (result, row) ->
                                new LessonIdentity(
                                        uuid(result, "assignment_id"),
                                        uuid(result, "room_id"),
                                        uuid(result, "time_slot_id")),
                        tenantId,
                        scheduleId,
                        lessonId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new TimetableNotFoundException("Cours introuvable"));
    }

    private List<SubjectView> subjects(UUID tenantId) {
        return jdbc.query(
                "SELECT id,code,name_fr,name_ar,color,active FROM timetable_subject WHERE"
                        + " tenant_id=? ORDER BY name_fr",
                (result, row) -> subjectView(result),
                tenantId);
    }

    private SubjectView subject(UUID tenantId, UUID subjectId) {
        return jdbc
                .query(
                        "SELECT id,code,name_fr,name_ar,color,active FROM timetable_subject WHERE"
                                + " tenant_id=? AND id=?",
                        (result, row) -> subjectView(result),
                        tenantId,
                        subjectId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new TimetableNotFoundException("Matière introuvable"));
    }

    private List<RoomView> rooms(UUID tenantId, UUID campusId) {
        String sql =
                "SELECT id,campus_id,code,name,room_type,capacity,floor_label FROM physical_room"
                        + " WHERE tenant_id=? AND active=TRUE"
                        + (campusId == null ? "" : " AND campus_id=?")
                        + " ORDER BY name";
        Object[] arguments =
                campusId == null ? new Object[] {tenantId} : new Object[] {tenantId, campusId};
        List<RoomBase> bases =
                jdbc.query(
                        sql,
                        (result, row) ->
                                new RoomBase(
                                        uuid(result, "id"),
                                        uuid(result, "campus_id"),
                                        result.getString("code"),
                                        result.getString("name"),
                                        result.getString("room_type"),
                                        result.getInt("capacity"),
                                        result.getString("floor_label")),
                        arguments);
        Map<UUID, List<RoomEquipmentView>> equipment = new HashMap<>();
        jdbc.query(
                "SELECT room_id,equipment_code,quantity FROM timetable_room_equipment WHERE"
                        + " tenant_id=? ORDER BY equipment_code",
                result -> {
                    equipment
                            .computeIfAbsent(uuid(result, "room_id"), ignored -> new ArrayList<>())
                            .add(
                                    new RoomEquipmentView(
                                            result.getString("equipment_code"),
                                            result.getInt("quantity")));
                },
                tenantId);
        return bases.stream()
                .map(
                        room ->
                                new RoomView(
                                        room.id(),
                                        room.campusId(),
                                        room.code(),
                                        room.name(),
                                        room.roomType(),
                                        room.capacity(),
                                        room.floorLabel(),
                                        equipment.getOrDefault(room.id(), List.of())))
                .toList();
    }

    private List<AssignmentView> assignments(UUID tenantId, UUID scheduleId) {
        return jdbc.query(
                """
                SELECT a.id,a.schedule_id,a.classroom_id,c.name AS classroom_name,
                       a.subject_id,s.name_fr AS subject_name,s.color,a.teacher_staff_id,
                       CONCAT(p.first_name,' ',p.last_name) AS teacher_name,a.weekly_periods,
                       a.required_room_type,a.required_equipment,a.active
                FROM timetable_teacher_assignment a
                JOIN classroom c ON c.id=a.classroom_id AND c.tenant_id=a.tenant_id
                JOIN timetable_subject s ON s.id=a.subject_id AND s.tenant_id=a.tenant_id
                JOIN staff_profile sp ON sp.id=a.teacher_staff_id AND sp.tenant_id=a.tenant_id
                JOIN person p ON p.id=sp.person_id AND p.tenant_id=sp.tenant_id
                WHERE a.tenant_id=? AND a.schedule_id=?
                ORDER BY c.name,s.name_fr,p.last_name
                """,
                (result, row) -> assignmentView(result),
                tenantId,
                scheduleId);
    }

    private AssignmentView assignment(UUID tenantId, UUID assignmentId) {
        return jdbc
                .query(
                        """
                        SELECT a.id,a.schedule_id,a.classroom_id,c.name AS classroom_name,
                               a.subject_id,s.name_fr AS subject_name,s.color,a.teacher_staff_id,
                               CONCAT(p.first_name,' ',p.last_name) AS teacher_name,a.weekly_periods,
                               a.required_room_type,a.required_equipment,a.active
                        FROM timetable_teacher_assignment a
                        JOIN classroom c ON c.id=a.classroom_id AND c.tenant_id=a.tenant_id
                        JOIN timetable_subject s ON s.id=a.subject_id AND s.tenant_id=a.tenant_id
                        JOIN staff_profile sp ON sp.id=a.teacher_staff_id AND sp.tenant_id=a.tenant_id
                        JOIN person p ON p.id=sp.person_id AND p.tenant_id=sp.tenant_id
                        WHERE a.tenant_id=? AND a.id=?
                        """,
                        (result, row) -> assignmentView(result),
                        tenantId,
                        assignmentId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new TimetableNotFoundException("Affectation introuvable"));
    }

    private ConstraintView constraint(UUID tenantId, UUID constraintId) {
        return jdbc
                .query(
                        "SELECT"
                            + " id,constraint_type,scope_type,target_id,configuration,hard_constraint,weight,enabled"
                            + " FROM timetable_constraint WHERE tenant_id=? AND id=?",
                        (result, row) -> constraintView(result),
                        tenantId,
                        constraintId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new TimetableNotFoundException("Contrainte introuvable"));
    }

    private ScheduleScope scheduleScope(UUID tenantId, UUID scheduleId) {
        return jdbc
                .query(
                        "SELECT campus_id,academic_year_id,status FROM timetable_schedule WHERE"
                                + " tenant_id=? AND id=?",
                        (result, row) ->
                                new ScheduleScope(
                                        uuid(result, "campus_id"),
                                        uuid(result, "academic_year_id"),
                                        result.getString("status")),
                        tenantId,
                        scheduleId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new TimetableNotFoundException("Planning introuvable"));
    }

    private void requireDraft(UUID tenantId, UUID scheduleId) {
        ScheduleScope scope = scheduleScope(tenantId, scheduleId);
        if (!"DRAFT".equals(scope.status())) {
            throw new TimetableValidationException("Seul un planning brouillon est modifiable");
        }
    }

    private Scope requireScope(UUID tenantId, UUID campusId, UUID yearId) {
        requireTenantReference("school_campus", tenantId, campusId, "Campus introuvable");
        return jdbc
                .query(
                        "SELECT starts_on,ends_on FROM academic_year WHERE tenant_id=? AND id=?",
                        (result, row) ->
                                new Scope(
                                        result.getObject("starts_on", LocalDate.class),
                                        result.getObject("ends_on", LocalDate.class)),
                        tenantId,
                        yearId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new TimetableNotFoundException("Année scolaire introuvable"));
    }

    private void requireRoomForSchedule(UUID tenantId, UUID scheduleId, UUID roomId) {
        UUID campusId = scheduleScope(tenantId, scheduleId).campusId();
        Integer count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM physical_room WHERE tenant_id=? AND id=? AND"
                                + " campus_id=? AND active=TRUE",
                        Integer.class,
                        tenantId,
                        roomId,
                        campusId);
        if (count == null || count == 0) {
            throw new TimetableValidationException(
                    "La salle n'appartient pas au campus du planning");
        }
    }

    private void requireSlot(UUID tenantId, UUID scheduleId, UUID slotId) {
        Integer count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM timetable_time_slot WHERE tenant_id=? AND"
                                + " schedule_id=? AND id=?",
                        Integer.class,
                        tenantId,
                        scheduleId,
                        slotId);
        if (count == null || count == 0) {
            throw new TimetableNotFoundException("Créneau introuvable");
        }
    }

    private void requireTenantReference(
            String table, UUID tenantId, UUID id, String notFoundMessage) {
        Set<String> allowed =
                Set.of("physical_room", "timetable_subject", "staff_profile", "school_campus");
        if (!allowed.contains(table)) throw new IllegalArgumentException("Unsupported table");
        Integer count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM " + table + " WHERE tenant_id=? AND id=?",
                        Integer.class,
                        tenantId,
                        id);
        if (count == null || count == 0) throw new TimetableNotFoundException(notFoundMessage);
    }

    private void replaceAvailability(
            String table,
            String targetColumn,
            UUID tenantId,
            UUID scheduleId,
            UUID targetId,
            UUID slotId,
            AvailabilityCommand command) {
        Set<String> tables =
                Set.of("timetable_teacher_availability", "timetable_room_availability");
        Set<String> columns = Set.of("teacher_staff_id", "room_id");
        if (!tables.contains(table) || !columns.contains(targetColumn)) {
            throw new IllegalArgumentException("Unsupported availability target");
        }
        jdbc.update(
                "DELETE FROM "
                        + table
                        + " WHERE tenant_id=? AND schedule_id=? AND "
                        + targetColumn
                        + "=? AND time_slot_id=?",
                tenantId,
                scheduleId,
                targetId,
                slotId);
        jdbc.update(
                "INSERT INTO "
                        + table
                        + " (id,tenant_id,schedule_id,"
                        + targetColumn
                        + ",time_slot_id,availability,note) VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                tenantId,
                scheduleId,
                targetId,
                slotId,
                normalize(command.availability()),
                trim(command.note()));
    }

    private void validateScheduleCommand(ScheduleCommand command) {
        if (command == null
                || command.campusId() == null
                || command.academicYearId() == null
                || blank(command.code())
                || blank(command.name())
                || command.validFrom() == null
                || command.validTo() == null
                || command.validFrom().isAfter(command.validTo())
                || command.periods() == null
                || command.periods().isEmpty()
                || command.workingDays() == null
                || command.workingDays().isEmpty()
                || command.workingDays().stream()
                        .anyMatch(day -> day == null || day < 1 || day > 7)) {
            throw new TimetableValidationException("Les informations du planning sont incomplètes");
        }
    }

    private void validatePeriod(PeriodCommand period, Set<Integer> sequences) {
        if (period == null
                || blank(period.code())
                || blank(period.name())
                || period.sequenceNumber() < 1
                || period.startsAt() == null
                || period.endsAt() == null
                || !period.startsAt().isBefore(period.endsAt())
                || !sequences.add(period.sequenceNumber())) {
            throw new TimetableValidationException("Un créneau horaire est invalide ou dupliqué");
        }
    }

    private void validateAvailability(AvailabilityCommand command) {
        if (command == null || !AVAILABILITY.contains(normalize(command.availability()))) {
            throw new TimetableValidationException("La disponibilité est invalide");
        }
    }

    private void validateConstraint(ConstraintCommand command) {
        if (command == null
                || !CONSTRAINT_TYPES.contains(normalize(command.constraintType()))
                || !CONSTRAINT_SCOPES.contains(normalize(command.scopeType()))
                || command.weight() < 1
                || command.weight() > 1000) {
            throw new TimetableValidationException("La contrainte est invalide");
        }
        String type = normalize(command.constraintType());
        if ((type.equals("MAX_HOURS_PER_DAY") || type.equals("MAX_CONSECUTIVE_HOURS"))
                && (command.targetId() == null
                        || integerConfig(canonicalJson(command.configuration()), 0) < 1)) {
            throw new TimetableValidationException(
                    "Cette contrainte exige un enseignant et un nombre de périodes positif");
        }
    }

    private static void requirePlacementCommand(PlacementCommand command) {
        if (command == null
                || command.assignmentId() == null
                || command.roomId() == null
                || command.slotId() == null) {
            throw new TimetableValidationException(
                    "L'affectation, la salle et le créneau sont requis");
        }
    }

    private static Assignment requireAssignment(PlanningSnapshot snapshot, UUID assignmentId) {
        Assignment assignment = snapshot.assignments().get(assignmentId);
        if (assignment == null) {
            throw new TimetableNotFoundException("Affectation introuvable");
        }
        return assignment;
    }

    private static void requireNoHardConflict(List<Conflict> conflicts) {
        List<Conflict> hard = conflicts.stream().filter(Conflict::hard).toList();
        if (!hard.isEmpty()) throw new TimetableConflictException(hard);
    }

    private String canonicalJson(Map<String, Object> configuration) {
        try {
            return objectMapper.writeValueAsString(
                    configuration == null ? Map.of() : configuration);
        } catch (JsonProcessingException exception) {
            throw new TimetableValidationException("La configuration JSON est invalide");
        }
    }

    private static int integerConfig(String configuration, int fallback) {
        try {
            JsonNode node = new ObjectMapper().readTree(configuration);
            JsonNode value = node.get("maxPeriods");
            return value == null || !value.canConvertToInt() ? fallback : value.intValue();
        } catch (JsonProcessingException exception) {
            return fallback;
        }
    }

    private static ScheduleSummary scheduleSummary(ResultSet result) throws SQLException {
        return new ScheduleSummary(
                uuid(result, "id"),
                result.getString("code"),
                result.getString("name"),
                result.getString("status"),
                result.getObject("valid_from", LocalDate.class),
                result.getObject("valid_to", LocalDate.class),
                uuid(result, "campus_id"),
                result.getString("campus_name"),
                uuid(result, "academic_year_id"),
                result.getString("year_name"),
                result.getInt("lesson_count"));
    }

    private static SubjectView subjectView(ResultSet result) throws SQLException {
        return new SubjectView(
                uuid(result, "id"),
                result.getString("code"),
                result.getString("name_fr"),
                result.getString("name_ar"),
                result.getString("color"),
                result.getBoolean("active"));
    }

    private static AssignmentView assignmentView(ResultSet result) throws SQLException {
        return new AssignmentView(
                uuid(result, "id"),
                uuid(result, "schedule_id"),
                uuid(result, "classroom_id"),
                result.getString("classroom_name"),
                uuid(result, "subject_id"),
                result.getString("subject_name"),
                result.getString("color"),
                uuid(result, "teacher_staff_id"),
                result.getString("teacher_name"),
                result.getInt("weekly_periods"),
                result.getString("required_room_type"),
                splitEquipment(result.getString("required_equipment")),
                result.getBoolean("active"));
    }

    private static LessonView lessonView(ResultSet result) throws SQLException {
        return new LessonView(
                uuid(result, "id"),
                uuid(result, "schedule_id"),
                uuid(result, "assignment_id"),
                uuid(result, "classroom_id"),
                result.getString("classroom_name"),
                uuid(result, "subject_id"),
                result.getString("subject_name"),
                result.getString("color"),
                uuid(result, "teacher_staff_id"),
                result.getString("teacher_name"),
                uuid(result, "room_id"),
                result.getString("room_name"),
                uuid(result, "time_slot_id"),
                result.getInt("day_of_week"),
                result.getInt("sequence_number"),
                result.getObject("starts_at", LocalTime.class),
                result.getObject("ends_at", LocalTime.class),
                result.getString("title"),
                result.getString("notes"),
                result.getString("source"));
    }

    private static ConstraintView constraintView(ResultSet result) throws SQLException {
        return new ConstraintView(
                uuid(result, "id"),
                result.getString("constraint_type"),
                result.getString("scope_type"),
                uuidNullable(result, "target_id"),
                result.getString("configuration"),
                result.getBoolean("hard_constraint"),
                result.getInt("weight"),
                result.getBoolean("enabled"));
    }

    private static UUID uuid(ResultSet result, String column) throws SQLException {
        return result.getObject(column, UUID.class);
    }

    private static UUID uuidNullable(ResultSet result, String column) throws SQLException {
        return result.getObject(column, UUID.class);
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String trim(String value) {
        return blank(value) ? null : value.strip();
    }

    private static String normalize(String value) {
        return blank(value) ? "" : value.strip().toUpperCase(Locale.ROOT);
    }

    private static String upper(String value) {
        String normalized = normalize(value);
        return normalized.isEmpty() ? null : normalized;
    }

    private static String joinEquipment(Set<String> equipment) {
        if (equipment == null || equipment.isEmpty()) return "";
        return equipment.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.strip().toUpperCase(Locale.ROOT))
                .distinct()
                .sorted()
                .collect(Collectors.joining(","));
    }

    private static Set<String> splitEquipment(String equipment) {
        if (equipment == null || equipment.isBlank()) return Set.of();
        return Set.of(equipment.split(","));
    }

    public record CampusOption(UUID id, String code, String name) {}

    public record YearOption(
            UUID id,
            String code,
            String name,
            String status,
            LocalDate startsOn,
            LocalDate endsOn) {}

    public record ClassroomOption(
            UUID id, UUID campusId, UUID academicYearId, String code, String name, int capacity) {}

    public record TeacherOption(UUID id, String employeeNumber, String name) {}

    public record RoomEquipmentView(String code, int quantity) {}

    public record RoomView(
            UUID id,
            UUID campusId,
            String code,
            String name,
            String roomType,
            int capacity,
            String floorLabel,
            List<RoomEquipmentView> equipment) {}

    public record ReferenceData(
            List<CampusOption> campuses,
            List<YearOption> years,
            List<ClassroomOption> classrooms,
            List<TeacherOption> teachers,
            List<RoomView> rooms) {}

    public record SubjectView(
            UUID id, String code, String nameFr, String nameAr, String color, boolean active) {}

    public record SubjectCommand(String code, String nameFr, String nameAr, String color) {}

    public record ScheduleSummary(
            UUID id,
            String code,
            String name,
            String status,
            LocalDate validFrom,
            LocalDate validTo,
            UUID campusId,
            String campusName,
            UUID academicYearId,
            String academicYearName,
            int lessonCount) {}

    public record ScheduleCommand(
            UUID campusId,
            UUID academicYearId,
            String code,
            String name,
            LocalDate validFrom,
            LocalDate validTo,
            List<PeriodCommand> periods,
            Set<Integer> workingDays) {}

    public record PeriodCommand(
            String code,
            String name,
            int sequenceNumber,
            LocalTime startsAt,
            LocalTime endsAt,
            boolean teaching) {}

    public record PeriodView(
            UUID id,
            String code,
            String name,
            int sequenceNumber,
            LocalTime startsAt,
            LocalTime endsAt,
            boolean teaching) {}

    public record TimeSlotView(
            UUID id,
            UUID periodId,
            int dayOfWeek,
            int sequenceNumber,
            LocalTime startsAt,
            LocalTime endsAt,
            boolean teaching) {}

    public record RoomEquipmentCommand(String equipmentCode, int quantity) {}

    public record AssignmentCommand(
            UUID classroomId,
            UUID subjectId,
            UUID teacherStaffId,
            int weeklyPeriods,
            String requiredRoomType,
            Set<String> requiredEquipment) {}

    public record AssignmentView(
            UUID id,
            UUID scheduleId,
            UUID classroomId,
            String classroomName,
            UUID subjectId,
            String subjectName,
            String color,
            UUID teacherStaffId,
            String teacherName,
            int weeklyPeriods,
            String requiredRoomType,
            Set<String> requiredEquipment,
            boolean active) {}

    public record AvailabilityCommand(String availability, String note) {}

    public record AvailabilityView(
            String targetType, UUID targetId, UUID timeSlotId, String availability, String note) {}

    public record ConstraintCommand(
            String constraintType,
            String scopeType,
            UUID targetId,
            Map<String, Object> configuration,
            boolean hardConstraint,
            int weight) {}

    public record ConstraintView(
            UUID id,
            String constraintType,
            String scopeType,
            UUID targetId,
            String configuration,
            boolean hardConstraint,
            int weight,
            boolean enabled) {}

    public record PlacementCommand(
            UUID assignmentId, UUID roomId, UUID slotId, String title, String notes) {}

    public record MoveCommand(UUID roomId, UUID slotId) {}

    public record LessonView(
            UUID id,
            UUID scheduleId,
            UUID assignmentId,
            UUID classroomId,
            String classroomName,
            UUID subjectId,
            String subjectName,
            String color,
            UUID teacherStaffId,
            String teacherName,
            UUID roomId,
            String roomName,
            UUID timeSlotId,
            int dayOfWeek,
            int sequenceNumber,
            LocalTime startsAt,
            LocalTime endsAt,
            String title,
            String notes,
            String source) {}

    public record ValidationView(boolean valid, List<Conflict> conflicts) {}

    public record PlacementProposal(UUID assignmentId, UUID roomId, UUID slotId) {}

    public record OptimizationView(
            List<PlacementProposal> proposals, Map<UUID, Integer> unscheduled, int score) {}

    public record ApplyOptimizationCommand(List<PlacementProposal> proposals) {}

    public record Board(
            ScheduleSummary schedule,
            List<SubjectView> subjects,
            List<PeriodView> periods,
            List<TimeSlotView> timeSlots,
            List<RoomView> rooms,
            List<AssignmentView> assignments,
            List<LessonView> lessons,
            List<AvailabilityView> availabilities,
            List<ConstraintView> constraints) {}

    private record Scope(LocalDate yearStartsOn, LocalDate yearEndsOn) {}

    private record ScheduleScope(UUID campusId, UUID academicYearId, String status) {}

    private record RoomBase(
            UUID id,
            UUID campusId,
            String code,
            String name,
            String roomType,
            int capacity,
            String floorLabel) {}

    private record LessonIdentity(UUID assignmentId, UUID roomId, UUID slotId) {}

    private record ConstraintInternal(
            String type, UUID targetId, String configuration, boolean hard) {}
}
