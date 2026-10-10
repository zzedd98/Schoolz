package ma.schoolz.erp.humanresources.application;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.identity.IdentityAuditTrail;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HumanResourcesService {
    private static final Set<String> EMPLOYEE_STATUSES =
            Set.of("ACTIVE", "ON_LEAVE", "SUSPENDED", "ENDED");
    private static final Set<String> CONTRACT_TYPES =
            Set.of("PERMANENT", "FIXED_TERM", "PART_TIME", "TEMPORARY", "INTERNSHIP", "OTHER");
    private static final Set<String> CONTRACT_STATUSES =
            Set.of("DRAFT", "ACTIVE", "EXPIRED", "TERMINATED");
    private static final Set<String> ATTENDANCE_STATUSES =
            Set.of("PRESENT", "REMOTE", "LEAVE", "ABSENT", "REST_DAY");
    private static final Set<String> LEAVE_DECISIONS = Set.of("APPROVED", "REJECTED");

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final IdentityAuditTrail audit;

    public HumanResourcesService(JdbcTemplate jdbc, Clock clock, IdentityAuditTrail audit) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.audit = audit;
    }

    @Transactional
    public Dashboard dashboard(SchoolPrincipal actor, LocalDate attendanceDate, String ipAddress) {
        require(actor, "hr.read");
        LocalDate selectedDate = attendanceDate == null ? LocalDate.now(clock) : attendanceDate;
        List<DepartmentView> departments = departments(actor.tenantId());
        List<PositionView> positions = positions(actor.tenantId());
        List<EmployeeSummary> employees = employees(actor.tenantId());
        List<LeaveRequestView> leaveRequests = leaveRequests(actor.tenantId(), null);
        List<AttendanceView> attendance = attendance(actor.tenantId(), selectedDate);
        int expiringDocuments =
                count(
                        "SELECT count(*) FROM hr_staff_document WHERE tenant_id=? AND status IN"
                                + " ('EXPIRING','EXPIRED')",
                        actor.tenantId());
        audit(
                actor,
                "hr.dashboard.viewed",
                "HumanResourcesDashboard",
                actor.tenantId(),
                ipAddress,
                Map.of("employeeCount", employees.size(), "attendanceDate", selectedDate));
        return new Dashboard(
                clock.instant(),
                selectedDate,
                departments,
                positions,
                employees,
                leaveRequests,
                attendance,
                expiringDocuments);
    }

    @Transactional
    public EmployeeRecord employee(SchoolPrincipal actor, UUID employeeId, String ipAddress) {
        require(actor, "hr.read");
        EmployeeSummary summary = employeeSummary(actor.tenantId(), employeeId);
        EmployeeRecord result =
                new EmployeeRecord(
                        summary,
                        contracts(actor.tenantId(), employeeId),
                        schedules(actor.tenantId(), employeeId),
                        leaveRequests(actor.tenantId(), employeeId),
                        employeeAttendance(actor.tenantId(), employeeId),
                        documents(actor.tenantId(), employeeId),
                        qualifications(actor.tenantId(), employeeId));
        audit(
                actor,
                "hr.employee.viewed",
                "Employee",
                employeeId,
                ipAddress,
                Map.of("employeeNumber", summary.employeeNumber()));
        return result;
    }

    @Transactional
    public DepartmentView createDepartment(
            SchoolPrincipal actor, DepartmentCommand command, String ipAddress) {
        require(actor, "hr.write");
        if (command == null) throw new HrValidationException("Département requis");
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        try {
            jdbc.update(
                    """
                    INSERT INTO hr_department(
                        id,tenant_id,campus_id,code,name,description,active,created_at,updated_at)
                    VALUES(?,?,?,?,?,?,TRUE,?,?)
                    """,
                    id,
                    actor.tenantId(),
                    command.campusId(),
                    code(command.code()),
                    required(command.name(), "Nom du département requis"),
                    nullable(command.description()),
                    timestamp(now),
                    timestamp(now));
        } catch (DuplicateKeyException exception) {
            throw new HrValidationException("Ce code de département existe déjà");
        }
        audit(actor, "hr.department.created", "Department", id, ipAddress, Map.of());
        return department(actor.tenantId(), id);
    }

    @Transactional
    public PositionView createPosition(
            SchoolPrincipal actor, PositionCommand command, String ipAddress) {
        require(actor, "hr.write");
        if (command == null) throw new HrValidationException("Poste requis");
        if (command.departmentId() != null) department(actor.tenantId(), command.departmentId());
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        try {
            jdbc.update(
                    """
                    INSERT INTO hr_position(
                        id,tenant_id,department_id,code,title,description,teacher_position,
                        active,created_at,updated_at)
                    VALUES(?,?,?,?,?,?,?,TRUE,?,?)
                    """,
                    id,
                    actor.tenantId(),
                    command.departmentId(),
                    code(command.code()),
                    required(command.title(), "Intitulé du poste requis"),
                    nullable(command.description()),
                    command.teacherPosition(),
                    timestamp(now),
                    timestamp(now));
        } catch (DuplicateKeyException exception) {
            throw new HrValidationException("Ce code de poste existe déjà");
        }
        audit(actor, "hr.position.created", "Position", id, ipAddress, Map.of());
        return position(actor.tenantId(), id);
    }

    @Transactional
    public EmployeeRecord createEmployee(
            SchoolPrincipal actor, EmployeeCommand command, String ipAddress) {
        require(actor, "hr.write");
        if (command == null) throw new HrValidationException("Employé requis");
        StaffIdentity staff = staff(actor.tenantId(), command.staffProfileId());
        if (command.departmentId() != null) department(actor.tenantId(), command.departmentId());
        if (command.positionId() != null) position(actor.tenantId(), command.positionId());
        if (command.managerEmployeeId() != null) {
            employeeSummary(actor.tenantId(), command.managerEmployeeId());
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        try {
            jdbc.update(
                    """
                    INSERT INTO hr_employee(
                        id,tenant_id,person_id,staff_profile_id,department_id,position_id,
                        manager_employee_id,work_email,work_phone,status,joined_on,created_at,
                        updated_at,version)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,0)
                    """,
                    id,
                    actor.tenantId(),
                    staff.personId(),
                    command.staffProfileId(),
                    command.departmentId(),
                    command.positionId(),
                    command.managerEmployeeId(),
                    nullable(command.workEmail()),
                    nullable(command.workPhone()),
                    enumValue(command.status(), EMPLOYEE_STATUSES, "Statut employé invalide"),
                    command.joinedOn() == null ? staff.hiredOn() : command.joinedOn(),
                    timestamp(now),
                    timestamp(now));
        } catch (DuplicateKeyException exception) {
            throw new HrValidationException(
                    "Cette personne ou ce profil personnel possède déjà un dossier RH");
        }
        audit(
                actor,
                "hr.employee.created",
                "Employee",
                id,
                ipAddress,
                Map.of("staffProfileId", command.staffProfileId()));
        return employee(actor, id, ipAddress);
    }

    @Transactional
    public ContractView addContract(
            SchoolPrincipal actor, UUID employeeId, ContractCommand command, String ipAddress) {
        require(actor, "hr.write");
        employeeSummary(actor.tenantId(), employeeId);
        if (command == null) throw new HrValidationException("Contrat requis");
        if (command.startsOn() == null) throw new HrValidationException("Date de début requise");
        if (command.endsOn() != null && command.endsOn().isBefore(command.startsOn())) {
            throw new HrValidationException("La fin du contrat précède son début");
        }
        if (command.weeklyHours() != null
                && (command.weeklyHours().signum() <= 0
                        || command.weeklyHours().compareTo(new BigDecimal("80")) > 0)) {
            throw new HrValidationException("Volume hebdomadaire invalide");
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                """
                INSERT INTO hr_employment_contract(
                    id,tenant_id,employee_id,contract_number,contract_type,starts_on,ends_on,
                    weekly_hours,status,notes,created_by_user_id,created_at,updated_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                id,
                actor.tenantId(),
                employeeId,
                required(command.contractNumber(), "Numéro de contrat requis"),
                enumValue(command.contractType(), CONTRACT_TYPES, "Type de contrat invalide"),
                command.startsOn(),
                command.endsOn(),
                command.weeklyHours(),
                enumValue(command.status(), CONTRACT_STATUSES, "Statut de contrat invalide"),
                nullable(command.notes()),
                actor.accountId(),
                timestamp(now),
                timestamp(now));
        audit(actor, "hr.contract.created", "EmploymentContract", id, ipAddress, Map.of());
        return contract(actor.tenantId(), id);
    }

    @Transactional
    public WorkScheduleView addSchedule(
            SchoolPrincipal actor, UUID employeeId, WorkScheduleCommand command, String ipAddress) {
        require(actor, "hr.write");
        employeeSummary(actor.tenantId(), employeeId);
        if (command == null
                || command.dayOfWeek() == null
                || command.startsAt() == null
                || command.endsAt() == null
                || command.effectiveFrom() == null) {
            throw new HrValidationException("Planning de travail incomplet");
        }
        if (command.dayOfWeek() < 1 || command.dayOfWeek() > 7) {
            throw new HrValidationException("Jour de semaine invalide");
        }
        if (!command.endsAt().isAfter(command.startsAt())) {
            throw new HrValidationException("L'heure de fin doit suivre l'heure de début");
        }
        int breakMinutes = command.breakMinutes() == null ? 0 : command.breakMinutes();
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO hr_work_schedule(
                    id,tenant_id,employee_id,day_of_week,starts_at,ends_at,break_minutes,
                    effective_from,effective_to,created_at)
                VALUES(?,?,?,?,?,?,?,?,?,?)
                """,
                id,
                actor.tenantId(),
                employeeId,
                command.dayOfWeek(),
                Time.valueOf(command.startsAt()),
                Time.valueOf(command.endsAt()),
                breakMinutes,
                command.effectiveFrom(),
                command.effectiveTo(),
                timestamp(clock.instant()));
        audit(actor, "hr.schedule.created", "WorkSchedule", id, ipAddress, Map.of());
        return schedule(actor.tenantId(), id);
    }

    @Transactional
    public LeaveRequestView requestLeave(
            SchoolPrincipal actor, LeaveRequestCommand command, String ipAddress) {
        require(actor, "hr.write");
        if (command == null || command.employeeId() == null) {
            throw new HrValidationException("Employé requis");
        }
        employeeSummary(actor.tenantId(), command.employeeId());
        if (command.startsOn() == null
                || command.endsOn() == null
                || command.endsOn().isBefore(command.startsOn())) {
            throw new HrValidationException("Période de congé invalide");
        }
        if (command.workingDays() == null || command.workingDays().signum() <= 0) {
            throw new HrValidationException("Nombre de jours ouvrés invalide");
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                """
                INSERT INTO hr_leave_request(
                    id,tenant_id,employee_id,leave_type,starts_on,ends_on,working_days,reason,
                    status,requested_at,updated_at,version)
                VALUES(?,?,?,?,?,?,?,?, 'REQUESTED',?,?,0)
                """,
                id,
                actor.tenantId(),
                command.employeeId(),
                required(command.leaveType(), "Type de congé requis").toUpperCase(Locale.ROOT),
                command.startsOn(),
                command.endsOn(),
                command.workingDays(),
                nullable(command.reason()),
                timestamp(now),
                timestamp(now));
        audit(actor, "hr.leave.requested", "LeaveRequest", id, ipAddress, Map.of());
        return leaveRequest(actor.tenantId(), id);
    }

    @Transactional
    public LeaveRequestView sendToManagerReview(
            SchoolPrincipal actor, UUID leaveRequestId, String ipAddress) {
        require(actor, "hr.leave.review");
        LeaveRequestView current = leaveRequest(actor.tenantId(), leaveRequestId);
        if (!"REQUESTED".equals(current.status())) {
            throw new HrValidationException("Seule une demande reçue peut passer en revue manager");
        }
        Instant now = clock.instant();
        jdbc.update(
                """
                UPDATE hr_leave_request
                SET status='MANAGER_REVIEW',manager_reviewed_by_user_id=?,manager_reviewed_at=?,
                    updated_at=?,version=version+1
                WHERE tenant_id=? AND id=? AND status='REQUESTED'
                """,
                actor.accountId(),
                timestamp(now),
                timestamp(now),
                actor.tenantId(),
                leaveRequestId);
        audit(
                actor,
                "hr.leave.manager-review",
                "LeaveRequest",
                leaveRequestId,
                ipAddress,
                Map.of());
        return leaveRequest(actor.tenantId(), leaveRequestId);
    }

    @Transactional
    public LeaveRequestView decideLeave(
            SchoolPrincipal actor,
            UUID leaveRequestId,
            LeaveDecisionCommand command,
            String ipAddress) {
        require(actor, "hr.leave.review");
        LeaveRequestView current = leaveRequest(actor.tenantId(), leaveRequestId);
        if (!"MANAGER_REVIEW".equals(current.status())) {
            throw new HrValidationException("La décision exige une revue manager préalable");
        }
        String decision =
                enumValue(
                        command == null ? null : command.decision(),
                        LEAVE_DECISIONS,
                        "Décision de congé invalide");
        Instant now = clock.instant();
        jdbc.update(
                """
                UPDATE hr_leave_request
                SET status=?,decided_by_user_id=?,decided_at=?,decision_comment=?,updated_at=?,
                    version=version+1
                WHERE tenant_id=? AND id=? AND status='MANAGER_REVIEW'
                """,
                decision,
                actor.accountId(),
                timestamp(now),
                nullable(command.comment()),
                timestamp(now),
                actor.tenantId(),
                leaveRequestId);
        audit(
                actor,
                "hr.leave.decided",
                "LeaveRequest",
                leaveRequestId,
                ipAddress,
                Map.of("decision", decision));
        return leaveRequest(actor.tenantId(), leaveRequestId);
    }

    @Transactional
    public AttendanceView recordAttendance(
            SchoolPrincipal actor, AttendanceCommand command, String ipAddress) {
        require(actor, "hr.attendance.manage");
        if (command == null || command.employeeId() == null || command.workDate() == null) {
            throw new HrValidationException("Présence incomplète");
        }
        employeeSummary(actor.tenantId(), command.employeeId());
        String status =
                enumValue(command.status(), ATTENDANCE_STATUSES, "Statut de présence invalide");
        Instant now = clock.instant();
        List<UUID> existing =
                jdbc.query(
                        "SELECT id FROM hr_staff_attendance WHERE tenant_id=? AND employee_id=?"
                                + " AND work_date=?",
                        (result, row) -> result.getObject(1, UUID.class),
                        actor.tenantId(),
                        command.employeeId(),
                        command.workDate());
        UUID id = existing.isEmpty() ? UUID.randomUUID() : existing.getFirst();
        if (existing.isEmpty()) {
            jdbc.update(
                    """
                    INSERT INTO hr_staff_attendance(
                        id,tenant_id,employee_id,work_date,checked_in_at,checked_out_at,status,
                        source,note,recorded_by_user_id,created_at,updated_at)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
                    """,
                    id,
                    actor.tenantId(),
                    command.employeeId(),
                    command.workDate(),
                    timestamp(command.checkedInAt()),
                    timestamp(command.checkedOutAt()),
                    status,
                    required(command.source(), "Source de présence requise")
                            .toUpperCase(Locale.ROOT),
                    nullable(command.note()),
                    actor.accountId(),
                    timestamp(now),
                    timestamp(now));
        } else {
            jdbc.update(
                    """
                    UPDATE hr_staff_attendance
                    SET checked_in_at=?,checked_out_at=?,status=?,source=?,note=?,
                        recorded_by_user_id=?,updated_at=?
                    WHERE tenant_id=? AND id=?
                    """,
                    timestamp(command.checkedInAt()),
                    timestamp(command.checkedOutAt()),
                    status,
                    required(command.source(), "Source de présence requise")
                            .toUpperCase(Locale.ROOT),
                    nullable(command.note()),
                    actor.accountId(),
                    timestamp(now),
                    actor.tenantId(),
                    id);
        }
        audit(actor, "hr.attendance.recorded", "StaffAttendance", id, ipAddress, Map.of());
        return attendanceById(actor.tenantId(), id);
    }

    @Transactional
    public StaffDocumentView addDocument(
            SchoolPrincipal actor,
            UUID employeeId,
            StaffDocumentCommand command,
            String ipAddress) {
        require(actor, "hr.write");
        employeeSummary(actor.tenantId(), employeeId);
        if (command == null) throw new HrValidationException("Document requis");
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO hr_staff_document(
                    id,tenant_id,employee_id,document_type,file_name,object_key,issued_on,
                    expires_on,status,uploaded_by_user_id,created_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?)
                """,
                id,
                actor.tenantId(),
                employeeId,
                required(command.documentType(), "Type de document requis")
                        .toUpperCase(Locale.ROOT),
                required(command.fileName(), "Nom de fichier requis"),
                required(command.objectKey(), "Clé de stockage requise"),
                command.issuedOn(),
                command.expiresOn(),
                enumValue(
                        command.status(),
                        Set.of("VALID", "EXPIRING", "EXPIRED", "ARCHIVED"),
                        "Statut de document invalide"),
                actor.accountId(),
                timestamp(clock.instant()));
        audit(actor, "hr.document.created", "StaffDocument", id, ipAddress, Map.of());
        return document(actor.tenantId(), id);
    }

    @Transactional
    public QualificationView addQualification(
            SchoolPrincipal actor,
            UUID employeeId,
            QualificationCommand command,
            String ipAddress) {
        require(actor, "hr.write");
        EmployeeSummary employee = employeeSummary(actor.tenantId(), employeeId);
        if (command == null) throw new HrValidationException("Qualification requise");
        if (!employee.teacherPosition()) {
            throw new HrValidationException(
                    "Les qualifications enseignantes exigent un poste enseignant");
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                """
                INSERT INTO hr_teacher_qualification(
                    id,tenant_id,employee_id,qualification_type,title,institution,subject_area,
                    obtained_on,expires_on,verified,verified_by_user_id,verified_at,created_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                id,
                actor.tenantId(),
                employeeId,
                required(command.qualificationType(), "Type de qualification requis")
                        .toUpperCase(Locale.ROOT),
                required(command.title(), "Titre de qualification requis"),
                nullable(command.institution()),
                nullable(command.subjectArea()),
                command.obtainedOn(),
                command.expiresOn(),
                command.verified(),
                command.verified() ? actor.accountId() : null,
                command.verified() ? timestamp(now) : null,
                timestamp(now));
        audit(actor, "hr.qualification.created", "TeacherQualification", id, ipAddress, Map.of());
        return qualification(actor.tenantId(), id);
    }

    private List<DepartmentView> departments(UUID tenantId) {
        return jdbc.query(
                """
                SELECT d.id,d.code,d.name,d.description,d.campus_id,d.active,
                    (SELECT count(*) FROM hr_employee e WHERE e.tenant_id=d.tenant_id
                        AND e.department_id=d.id AND e.status<>'ENDED') AS employee_count
                FROM hr_department d WHERE d.tenant_id=? ORDER BY d.name
                """,
                (result, row) -> department(result),
                tenantId);
    }

    private DepartmentView department(UUID tenantId, UUID id) {
        List<DepartmentView> values =
                jdbc.query(
                        """
                        SELECT d.id,d.code,d.name,d.description,d.campus_id,d.active,
                            (SELECT count(*) FROM hr_employee e WHERE e.tenant_id=d.tenant_id
                                AND e.department_id=d.id AND e.status<>'ENDED') AS employee_count
                        FROM hr_department d WHERE d.tenant_id=? AND d.id=?
                        """,
                        (result, row) -> department(result),
                        tenantId,
                        id);
        if (values.isEmpty()) throw new HrNotFoundException("Département introuvable");
        return values.getFirst();
    }

    private static DepartmentView department(ResultSet result) throws SQLException {
        return new DepartmentView(
                result.getObject("id", UUID.class),
                result.getString("code"),
                result.getString("name"),
                result.getString("description"),
                result.getObject("campus_id", UUID.class),
                result.getBoolean("active"),
                result.getInt("employee_count"));
    }

    private List<PositionView> positions(UUID tenantId) {
        return jdbc.query(
                """
                SELECT p.id,p.code,p.title,p.description,p.department_id,d.name department_name,
                    p.teacher_position,p.active
                FROM hr_position p LEFT JOIN hr_department d ON d.id=p.department_id
                    AND d.tenant_id=p.tenant_id
                WHERE p.tenant_id=? ORDER BY p.title
                """,
                (result, row) -> position(result),
                tenantId);
    }

    private PositionView position(UUID tenantId, UUID id) {
        List<PositionView> values =
                jdbc.query(
                        """
                        SELECT p.id,p.code,p.title,p.description,p.department_id,
                            d.name department_name,p.teacher_position,p.active
                        FROM hr_position p LEFT JOIN hr_department d ON d.id=p.department_id
                            AND d.tenant_id=p.tenant_id
                        WHERE p.tenant_id=? AND p.id=?
                        """,
                        (result, row) -> position(result),
                        tenantId,
                        id);
        if (values.isEmpty()) throw new HrNotFoundException("Poste introuvable");
        return values.getFirst();
    }

    private static PositionView position(ResultSet result) throws SQLException {
        return new PositionView(
                result.getObject("id", UUID.class),
                result.getString("code"),
                result.getString("title"),
                result.getString("description"),
                result.getObject("department_id", UUID.class),
                result.getString("department_name"),
                result.getBoolean("teacher_position"),
                result.getBoolean("active"));
    }

    private List<EmployeeSummary> employees(UUID tenantId) {
        return jdbc.query(
                employeeSql() + " WHERE e.tenant_id=? ORDER BY p.last_name,p.first_name",
                this::employee,
                tenantId);
    }

    private EmployeeSummary employeeSummary(UUID tenantId, UUID id) {
        List<EmployeeSummary> values =
                jdbc.query(
                        employeeSql() + " WHERE e.tenant_id=? AND e.id=?",
                        this::employee,
                        tenantId,
                        id);
        if (values.isEmpty()) throw new HrNotFoundException("Employé introuvable");
        return values.getFirst();
    }

    private static String employeeSql() {
        return """
        SELECT e.id,e.person_id,e.staff_profile_id,s.employee_number,
            CONCAT(p.first_name,' ',p.last_name) display_name,s.job_title,
            e.department_id,d.name department_name,e.position_id,pos.title position_title,
            pos.teacher_position,e.manager_employee_id,
            CONCAT(mp.first_name,' ',mp.last_name) manager_name,e.work_email,e.work_phone,
            e.status,e.joined_on,e.ended_on
        FROM hr_employee e
        JOIN staff_profile s ON s.id=e.staff_profile_id AND s.tenant_id=e.tenant_id
        JOIN person p ON p.id=e.person_id AND p.tenant_id=e.tenant_id
        LEFT JOIN hr_department d ON d.id=e.department_id AND d.tenant_id=e.tenant_id
        LEFT JOIN hr_position pos ON pos.id=e.position_id AND pos.tenant_id=e.tenant_id
        LEFT JOIN hr_employee me ON me.id=e.manager_employee_id AND me.tenant_id=e.tenant_id
        LEFT JOIN person mp ON mp.id=me.person_id AND mp.tenant_id=me.tenant_id
        """;
    }

    private EmployeeSummary employee(ResultSet result, int row) throws SQLException {
        return new EmployeeSummary(
                result.getObject("id", UUID.class),
                result.getObject("person_id", UUID.class),
                result.getObject("staff_profile_id", UUID.class),
                result.getString("employee_number"),
                result.getString("display_name"),
                result.getString("job_title"),
                result.getObject("department_id", UUID.class),
                result.getString("department_name"),
                result.getObject("position_id", UUID.class),
                result.getString("position_title"),
                result.getBoolean("teacher_position"),
                result.getObject("manager_employee_id", UUID.class),
                result.getString("manager_name"),
                result.getString("work_email"),
                result.getString("work_phone"),
                result.getString("status"),
                result.getObject("joined_on", LocalDate.class),
                result.getObject("ended_on", LocalDate.class));
    }

    private StaffIdentity staff(UUID tenantId, UUID staffProfileId) {
        if (staffProfileId == null) throw new HrValidationException("Profil personnel requis");
        List<StaffIdentity> values =
                jdbc.query(
                        "SELECT person_id,hired_on FROM staff_profile WHERE tenant_id=? AND id=?",
                        (result, row) ->
                                new StaffIdentity(
                                        result.getObject("person_id", UUID.class),
                                        result.getObject("hired_on", LocalDate.class)),
                        tenantId,
                        staffProfileId);
        if (values.isEmpty()) throw new HrNotFoundException("Profil personnel introuvable");
        return values.getFirst();
    }

    private List<ContractView> contracts(UUID tenantId, UUID employeeId) {
        return jdbc.query(
                """
                SELECT id,contract_number,contract_type,starts_on,ends_on,weekly_hours,status,notes
                FROM hr_employment_contract WHERE tenant_id=? AND employee_id=?
                ORDER BY starts_on DESC
                """,
                (result, row) -> contract(result),
                tenantId,
                employeeId);
    }

    private ContractView contract(UUID tenantId, UUID id) {
        List<ContractView> values =
                jdbc.query(
                        """
                        SELECT id,contract_number,contract_type,starts_on,ends_on,weekly_hours,
                            status,notes
                        FROM hr_employment_contract WHERE tenant_id=? AND id=?
                        """,
                        (result, row) -> contract(result),
                        tenantId,
                        id);
        return values.getFirst();
    }

    private static ContractView contract(ResultSet result) throws SQLException {
        return new ContractView(
                result.getObject("id", UUID.class),
                result.getString("contract_number"),
                result.getString("contract_type"),
                result.getObject("starts_on", LocalDate.class),
                result.getObject("ends_on", LocalDate.class),
                result.getBigDecimal("weekly_hours"),
                result.getString("status"),
                result.getString("notes"));
    }

    private List<WorkScheduleView> schedules(UUID tenantId, UUID employeeId) {
        return jdbc.query(
                """
                SELECT id,day_of_week,starts_at,ends_at,break_minutes,effective_from,effective_to
                FROM hr_work_schedule WHERE tenant_id=? AND employee_id=?
                ORDER BY effective_from DESC,day_of_week
                """,
                (result, row) -> schedule(result),
                tenantId,
                employeeId);
    }

    private WorkScheduleView schedule(UUID tenantId, UUID id) {
        return jdbc.query(
                        """
                        SELECT id,day_of_week,starts_at,ends_at,break_minutes,effective_from,
                            effective_to FROM hr_work_schedule WHERE tenant_id=? AND id=?
                        """,
                        (result, row) -> schedule(result),
                        tenantId,
                        id)
                .getFirst();
    }

    private static WorkScheduleView schedule(ResultSet result) throws SQLException {
        return new WorkScheduleView(
                result.getObject("id", UUID.class),
                result.getInt("day_of_week"),
                result.getObject("starts_at", LocalTime.class),
                result.getObject("ends_at", LocalTime.class),
                result.getInt("break_minutes"),
                result.getObject("effective_from", LocalDate.class),
                result.getObject("effective_to", LocalDate.class));
    }

    private List<LeaveRequestView> leaveRequests(UUID tenantId, UUID employeeId) {
        String sql =
                """
                SELECT l.id,l.employee_id,CONCAT(p.first_name,' ',p.last_name) employee_name,
                    l.leave_type,l.starts_on,l.ends_on,l.working_days,l.reason,l.status,
                    l.requested_at,l.manager_reviewed_at,l.decided_at,l.decision_comment
                FROM hr_leave_request l
                JOIN hr_employee e ON e.id=l.employee_id AND e.tenant_id=l.tenant_id
                JOIN person p ON p.id=e.person_id AND p.tenant_id=e.tenant_id
                WHERE l.tenant_id=?
                """;
        if (employeeId == null) {
            return jdbc.query(sql + " ORDER BY l.requested_at DESC", this::leaveRequest, tenantId);
        }
        return jdbc.query(
                sql + " AND l.employee_id=? ORDER BY l.requested_at DESC",
                this::leaveRequest,
                tenantId,
                employeeId);
    }

    private LeaveRequestView leaveRequest(UUID tenantId, UUID id) {
        List<LeaveRequestView> values =
                jdbc.query(
                        """
                        SELECT l.id,l.employee_id,CONCAT(p.first_name,' ',p.last_name) employee_name,
                            l.leave_type,l.starts_on,l.ends_on,l.working_days,l.reason,l.status,
                            l.requested_at,l.manager_reviewed_at,l.decided_at,l.decision_comment
                        FROM hr_leave_request l
                        JOIN hr_employee e ON e.id=l.employee_id AND e.tenant_id=l.tenant_id
                        JOIN person p ON p.id=e.person_id AND p.tenant_id=e.tenant_id
                        WHERE l.tenant_id=? AND l.id=?
                        """,
                        this::leaveRequest,
                        tenantId,
                        id);
        if (values.isEmpty()) throw new HrNotFoundException("Demande de congé introuvable");
        return values.getFirst();
    }

    private LeaveRequestView leaveRequest(ResultSet result, int row) throws SQLException {
        return new LeaveRequestView(
                result.getObject("id", UUID.class),
                result.getObject("employee_id", UUID.class),
                result.getString("employee_name"),
                result.getString("leave_type"),
                result.getObject("starts_on", LocalDate.class),
                result.getObject("ends_on", LocalDate.class),
                result.getBigDecimal("working_days"),
                result.getString("reason"),
                result.getString("status"),
                instant(result, "requested_at"),
                instant(result, "manager_reviewed_at"),
                instant(result, "decided_at"),
                result.getString("decision_comment"));
    }

    private List<AttendanceView> attendance(UUID tenantId, LocalDate date) {
        return jdbc.query(
                attendanceSql() + " WHERE a.tenant_id=? AND a.work_date=? ORDER BY p.last_name",
                this::attendance,
                tenantId,
                date);
    }

    private List<AttendanceView> employeeAttendance(UUID tenantId, UUID employeeId) {
        return jdbc.query(
                attendanceSql()
                        + " WHERE a.tenant_id=? AND a.employee_id=? ORDER BY a.work_date DESC",
                this::attendance,
                tenantId,
                employeeId);
    }

    private AttendanceView attendanceById(UUID tenantId, UUID id) {
        return jdbc.query(
                        attendanceSql() + " WHERE a.tenant_id=? AND a.id=?",
                        this::attendance,
                        tenantId,
                        id)
                .getFirst();
    }

    private static String attendanceSql() {
        return """
        SELECT a.id,a.employee_id,CONCAT(p.first_name,' ',p.last_name) employee_name,
            a.work_date,a.checked_in_at,a.checked_out_at,a.status,a.source,a.note
        FROM hr_staff_attendance a
        JOIN hr_employee e ON e.id=a.employee_id AND e.tenant_id=a.tenant_id
        JOIN person p ON p.id=e.person_id AND p.tenant_id=e.tenant_id
        """;
    }

    private AttendanceView attendance(ResultSet result, int row) throws SQLException {
        return new AttendanceView(
                result.getObject("id", UUID.class),
                result.getObject("employee_id", UUID.class),
                result.getString("employee_name"),
                result.getObject("work_date", LocalDate.class),
                instant(result, "checked_in_at"),
                instant(result, "checked_out_at"),
                result.getString("status"),
                result.getString("source"),
                result.getString("note"));
    }

    private List<StaffDocumentView> documents(UUID tenantId, UUID employeeId) {
        return jdbc.query(
                """
                SELECT id,document_type,file_name,object_key,issued_on,expires_on,status,created_at
                FROM hr_staff_document WHERE tenant_id=? AND employee_id=? ORDER BY created_at DESC
                """,
                (result, row) -> document(result),
                tenantId,
                employeeId);
    }

    private StaffDocumentView document(UUID tenantId, UUID id) {
        return jdbc.query(
                        """
                        SELECT id,document_type,file_name,object_key,issued_on,expires_on,status,
                            created_at FROM hr_staff_document WHERE tenant_id=? AND id=?
                        """,
                        (result, row) -> document(result),
                        tenantId,
                        id)
                .getFirst();
    }

    private static StaffDocumentView document(ResultSet result) throws SQLException {
        return new StaffDocumentView(
                result.getObject("id", UUID.class),
                result.getString("document_type"),
                result.getString("file_name"),
                result.getString("object_key"),
                result.getObject("issued_on", LocalDate.class),
                result.getObject("expires_on", LocalDate.class),
                result.getString("status"),
                instant(result, "created_at"));
    }

    private List<QualificationView> qualifications(UUID tenantId, UUID employeeId) {
        return jdbc.query(
                """
                SELECT id,qualification_type,title,institution,subject_area,obtained_on,expires_on,
                    verified,verified_at FROM hr_teacher_qualification
                WHERE tenant_id=? AND employee_id=? ORDER BY obtained_on DESC
                """,
                (result, row) -> qualification(result),
                tenantId,
                employeeId);
    }

    private QualificationView qualification(UUID tenantId, UUID id) {
        return jdbc.query(
                        """
                        SELECT id,qualification_type,title,institution,subject_area,obtained_on,
                            expires_on,verified,verified_at FROM hr_teacher_qualification
                        WHERE tenant_id=? AND id=?
                        """,
                        (result, row) -> qualification(result),
                        tenantId,
                        id)
                .getFirst();
    }

    private static QualificationView qualification(ResultSet result) throws SQLException {
        return new QualificationView(
                result.getObject("id", UUID.class),
                result.getString("qualification_type"),
                result.getString("title"),
                result.getString("institution"),
                result.getString("subject_area"),
                result.getObject("obtained_on", LocalDate.class),
                result.getObject("expires_on", LocalDate.class),
                result.getBoolean("verified"),
                instant(result, "verified_at"));
    }

    private void audit(
            SchoolPrincipal actor,
            String action,
            String resourceType,
            UUID resourceId,
            String ipAddress,
            Map<String, ?> metadata) {
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                action,
                resourceType,
                resourceId.toString(),
                ipAddress,
                metadata,
                null,
                null);
    }

    private int count(String sql, Object... arguments) {
        Integer value = jdbc.queryForObject(sql, Integer.class, arguments);
        return value == null ? 0 : value;
    }

    private static void require(SchoolPrincipal actor, String permission) {
        if (actor == null || !actor.permissions().contains(permission)) {
            throw new AccessDeniedException("Permission requise : " + permission);
        }
    }

    private static String enumValue(String value, Set<String> allowed, String message) {
        String normalized = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw new HrValidationException(message);
        return normalized;
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) throw new HrValidationException(message);
        return value.strip();
    }

    private static String nullable(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String code(String value) {
        return required(value, "Code requis")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9_-]", "-");
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private record StaffIdentity(UUID personId, LocalDate hiredOn) {}

    public record Dashboard(
            Instant generatedAt,
            LocalDate attendanceDate,
            List<DepartmentView> departments,
            List<PositionView> positions,
            List<EmployeeSummary> employees,
            List<LeaveRequestView> leaveRequests,
            List<AttendanceView> attendance,
            int expiringDocuments) {}

    public record DepartmentView(
            UUID id,
            String code,
            String name,
            String description,
            UUID campusId,
            boolean active,
            int employeeCount) {}

    public record PositionView(
            UUID id,
            String code,
            String title,
            String description,
            UUID departmentId,
            String departmentName,
            boolean teacherPosition,
            boolean active) {}

    public record EmployeeSummary(
            UUID id,
            UUID personId,
            UUID staffProfileId,
            String employeeNumber,
            String displayName,
            String jobTitle,
            UUID departmentId,
            String departmentName,
            UUID positionId,
            String positionTitle,
            boolean teacherPosition,
            UUID managerEmployeeId,
            String managerName,
            String workEmail,
            String workPhone,
            String status,
            LocalDate joinedOn,
            LocalDate endedOn) {}

    public record EmployeeRecord(
            EmployeeSummary employee,
            List<ContractView> contracts,
            List<WorkScheduleView> schedules,
            List<LeaveRequestView> leaveRequests,
            List<AttendanceView> attendance,
            List<StaffDocumentView> documents,
            List<QualificationView> qualifications) {}

    public record ContractView(
            UUID id,
            String contractNumber,
            String contractType,
            LocalDate startsOn,
            LocalDate endsOn,
            BigDecimal weeklyHours,
            String status,
            String notes) {}

    public record WorkScheduleView(
            UUID id,
            int dayOfWeek,
            LocalTime startsAt,
            LocalTime endsAt,
            int breakMinutes,
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {}

    public record LeaveRequestView(
            UUID id,
            UUID employeeId,
            String employeeName,
            String leaveType,
            LocalDate startsOn,
            LocalDate endsOn,
            BigDecimal workingDays,
            String reason,
            String status,
            Instant requestedAt,
            Instant managerReviewedAt,
            Instant decidedAt,
            String decisionComment) {}

    public record AttendanceView(
            UUID id,
            UUID employeeId,
            String employeeName,
            LocalDate workDate,
            Instant checkedInAt,
            Instant checkedOutAt,
            String status,
            String source,
            String note) {}

    public record StaffDocumentView(
            UUID id,
            String documentType,
            String fileName,
            String objectKey,
            LocalDate issuedOn,
            LocalDate expiresOn,
            String status,
            Instant createdAt) {}

    public record QualificationView(
            UUID id,
            String qualificationType,
            String title,
            String institution,
            String subjectArea,
            LocalDate obtainedOn,
            LocalDate expiresOn,
            boolean verified,
            Instant verifiedAt) {}

    public record DepartmentCommand(UUID campusId, String code, String name, String description) {}

    public record PositionCommand(
            UUID departmentId,
            String code,
            String title,
            String description,
            boolean teacherPosition) {}

    public record EmployeeCommand(
            UUID staffProfileId,
            UUID departmentId,
            UUID positionId,
            UUID managerEmployeeId,
            String workEmail,
            String workPhone,
            String status,
            LocalDate joinedOn) {}

    public record ContractCommand(
            String contractNumber,
            String contractType,
            LocalDate startsOn,
            LocalDate endsOn,
            BigDecimal weeklyHours,
            String status,
            String notes) {}

    public record WorkScheduleCommand(
            Integer dayOfWeek,
            LocalTime startsAt,
            LocalTime endsAt,
            Integer breakMinutes,
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {}

    public record LeaveRequestCommand(
            UUID employeeId,
            String leaveType,
            LocalDate startsOn,
            LocalDate endsOn,
            BigDecimal workingDays,
            String reason) {}

    public record LeaveDecisionCommand(String decision, String comment) {}

    public record AttendanceCommand(
            UUID employeeId,
            LocalDate workDate,
            Instant checkedInAt,
            Instant checkedOutAt,
            String status,
            String source,
            String note) {}

    public record StaffDocumentCommand(
            String documentType,
            String fileName,
            String objectKey,
            LocalDate issuedOn,
            LocalDate expiresOn,
            String status) {}

    public record QualificationCommand(
            String qualificationType,
            String title,
            String institution,
            String subjectArea,
            LocalDate obtainedOn,
            LocalDate expiresOn,
            boolean verified) {}
}
