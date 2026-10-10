package ma.schoolz.erp.enrollment.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.admissions.AdmissionPortalAccess.PortalIdentity;
import ma.schoolz.erp.foundation.security.FileSecurityScanner;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.people.StudentImportOperations;
import ma.schoolz.erp.people.StudentImportOperations.ImportedStudent;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class EnrollmentWorkflowService {
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final Set<String> CONTENT_TYPES =
            Set.of("application/pdf", "image/jpeg", "image/png");

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final StudentImportOperations students;
    private final FileSecurityScanner fileSecurity;
    private final Clock clock;

    public EnrollmentWorkflowService(
            JdbcTemplate jdbc,
            ObjectMapper json,
            StudentImportOperations students,
            FileSecurityScanner fileSecurity,
            Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.students = students;
        this.fileSecurity = fileSecurity;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<WorkflowView> portalWorkflows(PortalIdentity parent) {
        return workflowIds(
                        "SELECT id FROM enrollment_workflow WHERE tenant_id=? AND"
                                + " portal_account_id=? ORDER BY updated_at DESC",
                        parent.tenantId(),
                        parent.accountId())
                .stream()
                .map(id -> workflow(parent.tenantId(), id, parent.accountId()))
                .toList();
    }

    @Transactional
    public WorkflowView startInitial(PortalIdentity parent, UUID applicationId) {
        InitialOrigin origin =
                jdbc
                        .query(
                                """
                                SELECT a.id,a.reference_number,a.current_status,a.desired_campus_id,
                                       c.academic_year_id,p.first_name,p.last_name,p.birth_date,
                                       p.gender,p.nationality_code,pa.email,pa.phone
                                FROM admission_application a
                                JOIN admission_campaign c ON c.id=a.campaign_id AND c.tenant_id=a.tenant_id
                                JOIN admission_applicant p ON p.id=a.applicant_id AND p.tenant_id=a.tenant_id
                                JOIN admission_portal_account pa ON pa.id=p.portal_account_id AND pa.tenant_id=p.tenant_id
                                JOIN admission_enrollment_offer o ON o.application_id=a.id AND o.tenant_id=a.tenant_id
                                WHERE a.tenant_id=? AND a.id=? AND p.portal_account_id=?
                                  AND a.current_status='ENROLLED' AND o.status='ACCEPTED'
                                """,
                                (result, row) ->
                                        new InitialOrigin(
                                                result.getObject("id", UUID.class),
                                                result.getString("reference_number"),
                                                result.getObject("desired_campus_id", UUID.class),
                                                result.getObject("academic_year_id", UUID.class),
                                                result.getString("first_name"),
                                                result.getString("last_name"),
                                                result.getObject("birth_date", LocalDate.class),
                                                result.getString("gender"),
                                                result.getString("nationality_code"),
                                                result.getString("email"),
                                                result.getString("phone")),
                                parent.tenantId(),
                                applicationId,
                                parent.accountId())
                        .stream()
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new EnrollmentValidationException(
                                                "Une offre acceptée est nécessaire pour démarrer"
                                                        + " l'inscription"));
        UUID workflowId = UUID.randomUUID();
        Instant now = clock.instant();
        try {
            insertWorkflow(
                    workflowId,
                    parent,
                    applicationId,
                    null,
                    origin.academicYearId(),
                    origin.campusId(),
                    "INITIAL",
                    now);
        } catch (DuplicateKeyException exception) {
            throw new EnrollmentValidationException("Un parcours existe déjà pour cette admission");
        }
        insertContract(workflowId, parent.tenantId(), origin.reference(), now);
        Map<String, Object> family =
                Map.of(
                        "guardianFirstName", parent.displayName(),
                        "guardianLastName", "",
                        "email", origin.email(),
                        "phone", origin.phone() == null ? "" : origin.phone(),
                        "relationship", "PARENT",
                        "addressLine1", "",
                        "city", "");
        insertFamilyAndServices(workflowId, parent.tenantId(), family, now);
        return workflow(parent.tenantId(), workflowId, parent.accountId());
    }

    @Transactional
    public WorkflowView startRenewal(PortalIdentity parent, RenewalCommand command) {
        Integer linked =
                jdbc.queryForObject(
                        """
                        SELECT count(*) FROM enrollment_portal_student_link
                        WHERE tenant_id=? AND portal_account_id=? AND student_id=?
                        """,
                        Integer.class,
                        parent.tenantId(),
                        parent.accountId(),
                        command.studentId());
        if (linked == null || linked == 0) {
            throw new EnrollmentNotFoundException("Élève introuvable dans votre famille");
        }
        requireReference("academic_year", parent.tenantId(), command.academicYearId());
        requireReference("school_campus", parent.tenantId(), command.campusId());
        UUID workflowId = UUID.randomUUID();
        Instant now = clock.instant();
        try {
            insertWorkflow(
                    workflowId,
                    parent,
                    null,
                    command.studentId(),
                    command.academicYearId(),
                    command.campusId(),
                    "RE_ENROLLMENT",
                    now);
        } catch (DuplicateKeyException exception) {
            throw new EnrollmentValidationException("La réinscription de cette année existe déjà");
        }
        insertContract(workflowId, parent.tenantId(), "RÉINSCRIPTION", now);
        Map<String, Object> family = existingFamily(parent.tenantId(), command.studentId());
        insertFamilyAndServices(workflowId, parent.tenantId(), family, now);
        return workflow(parent.tenantId(), workflowId, parent.accountId());
    }

    @Transactional
    public WorkflowView acceptContract(PortalIdentity parent, UUID workflowId) {
        requireOwned(parent, workflowId);
        Instant now = clock.instant();
        jdbc.update(
                "UPDATE enrollment_contract SET status='ACCEPTED',accepted_at=? WHERE tenant_id=?"
                        + " AND workflow_id=?",
                timestamp(now),
                parent.tenantId(),
                workflowId);
        step(parent.tenantId(), workflowId, "FAMILY_INFORMATION", now);
        return workflow(parent.tenantId(), workflowId, parent.accountId());
    }

    @Transactional
    public WorkflowView confirmFamily(
            PortalIdentity parent, UUID workflowId, FamilyCommand command) {
        requireOwned(parent, workflowId);
        if (command.data() == null
                || blank(value(command.data(), "guardianFirstName"))
                || blank(value(command.data(), "guardianLastName"))
                || blank(value(command.data(), "addressLine1"))
                || blank(value(command.data(), "city"))) {
            throw new EnrollmentValidationException(
                    "Les informations famille obligatoires sont incomplètes");
        }
        Instant now = clock.instant();
        jdbc.update(
                """
                UPDATE enrollment_family_confirmation
                SET family_data=?,confirmed=TRUE,confirmed_at=?,updated_at=?
                WHERE tenant_id=? AND workflow_id=?
                """,
                writeJson(command.data()),
                timestamp(now),
                timestamp(now),
                parent.tenantId(),
                workflowId);
        step(parent.tenantId(), workflowId, "SERVICES", now);
        return workflow(parent.tenantId(), workflowId, parent.accountId());
    }

    @Transactional
    public WorkflowView confirmServices(
            PortalIdentity parent, UUID workflowId, ServicesCommand command) {
        requireOwned(parent, workflowId);
        if (!Set.of("ANNUAL", "QUARTERLY", "MONTHLY").contains(command.paymentPlan())) {
            throw new EnrollmentValidationException("Plan de paiement invalide");
        }
        Instant now = clock.instant();
        jdbc.update(
                """
                UPDATE enrollment_service_selection
                SET transport_selected=?,transport_route=?,cafeteria_selected=?,activities=?,
                    payment_plan=?,confirmed=TRUE,confirmed_at=?,updated_at=?
                WHERE tenant_id=? AND workflow_id=?
                """,
                command.transportSelected(),
                blankToNull(command.transportRoute()),
                command.cafeteriaSelected(),
                writeJson(command.activities() == null ? List.of() : command.activities()),
                command.paymentPlan(),
                timestamp(now),
                timestamp(now),
                parent.tenantId(),
                workflowId);
        step(parent.tenantId(), workflowId, "DOCUMENTS", now);
        return workflow(parent.tenantId(), workflowId, parent.accountId());
    }

    @Transactional
    public WorkflowView upload(
            PortalIdentity parent, UUID workflowId, String documentType, MultipartFile file) {
        requireOwned(parent, workflowId);
        if (file == null || file.isEmpty() || file.getSize() > 5L * 1024 * 1024) {
            throw new EnrollmentValidationException("Le document doit peser entre 1 octet et 5 Mo");
        }
        String contentType = file.getContentType();
        if (contentType == null || !CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new EnrollmentValidationException(
                    "Seuls les fichiers PDF, JPEG et PNG sont acceptés");
        }
        try {
            byte[] bytes = file.getBytes();
            fileSecurity.inspect(
                    file.getOriginalFilename(),
                    contentType,
                    bytes,
                    CONTENT_TYPES,
                    5L * 1024 * 1024);
            jdbc.update(
                    """
                    INSERT INTO enrollment_document
                        (id,tenant_id,workflow_id,document_type,original_file_name,content_type,
                         size_bytes,sha256,binary_data,created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    UUID.randomUUID(),
                    parent.tenantId(),
                    workflowId,
                    documentType.strip().toUpperCase(Locale.ROOT),
                    safeName(file.getOriginalFilename()),
                    contentType,
                    bytes.length,
                    sha256(bytes),
                    bytes,
                    timestamp(clock.instant()));
        } catch (java.io.IOException exception) {
            throw new EnrollmentValidationException("Document illisible");
        }
        if (missingDocuments(parent.tenantId(), workflowId).isEmpty()) {
            step(parent.tenantId(), workflowId, "SIGNATURE", clock.instant());
        }
        return workflow(parent.tenantId(), workflowId, parent.accountId());
    }

    @Transactional
    public WorkflowView sign(
            PortalIdentity parent, UUID workflowId, SignatureCommand command, String ipAddress) {
        requireOwned(parent, workflowId);
        if (!command.accepted()
                || blank(command.signerName())
                || !missingDocuments(parent.tenantId(), workflowId).isEmpty()) {
            throw new EnrollmentValidationException(
                    "La signature et les documents sont obligatoires");
        }
        Instant now = clock.instant();
        try {
            jdbc.update(
                    """
                    INSERT INTO enrollment_signature
                        (id,tenant_id,workflow_id,signer_name,consent_text,signature_hash,
                         ip_address,signed_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    UUID.randomUUID(),
                    parent.tenantId(),
                    workflowId,
                    command.signerName().strip(),
                    "Je confirme les informations et accepte le contrat d'inscription SchoolZ.",
                    sha256(
                            (workflowId + command.signerName() + now)
                                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    ipAddress,
                    timestamp(now));
        } catch (DuplicateKeyException exception) {
            throw new EnrollmentValidationException("Ce contrat est déjà signé");
        }
        step(parent.tenantId(), workflowId, "REGISTRATION_FEE", now);
        return workflow(parent.tenantId(), workflowId, parent.accountId());
    }

    @Transactional
    public WorkflowView pay(PortalIdentity parent, UUID workflowId, PaymentCommand command) {
        WorkflowView workflow = workflow(parent.tenantId(), workflowId, parent.accountId());
        if (command.cardLast4() == null || !command.cardLast4().matches("[0-9]{4}")) {
            throw new EnrollmentValidationException("Quatre chiffres de carte sont requis");
        }
        if (workflow.feeStatus().equals("PAID")) {
            throw new EnrollmentValidationException("Les frais sont déjà réglés");
        }
        Instant now = clock.instant();
        jdbc.update(
                """
                INSERT INTO enrollment_fee_payment
                    (id,tenant_id,workflow_id,amount,currency_code,payment_method,
                     payment_reference,status,paid_at,created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'PAID', ?, ?)
                """,
                UUID.randomUUID(),
                parent.tenantId(),
                workflowId,
                workflow.registrationFee(),
                workflow.currencyCode(),
                "DEMO_CARD_" + command.cardLast4(),
                "ENR-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT),
                timestamp(now),
                timestamp(now));
        step(parent.tenantId(), workflowId, "CLASS_ASSIGNMENT", now);
        return workflow(parent.tenantId(), workflowId, parent.accountId());
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('enrollment.read')")
    public List<WorkflowView> adminWorkflows(SchoolPrincipal actor) {
        return workflowIds(
                        "SELECT id FROM enrollment_workflow WHERE tenant_id=? ORDER BY updated_at"
                                + " DESC",
                        actor.tenantId())
                .stream()
                .map(id -> workflow(actor.tenantId(), id, null))
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('enrollment.read')")
    public List<ClassOption> classes(SchoolPrincipal actor, UUID workflowId) {
        BaseRow workflow = base(actor.tenantId(), workflowId, null);
        return jdbc.query(
                """
                SELECT id,code,name,capacity FROM classroom
                WHERE tenant_id=? AND academic_year_id=? AND campus_id=? AND active=TRUE
                ORDER BY name
                """,
                (result, row) ->
                        new ClassOption(
                                result.getObject("id", UUID.class),
                                result.getString("code"),
                                result.getString("name"),
                                result.getInt("capacity")),
                actor.tenantId(),
                workflow.academicYearId(),
                workflow.campusId());
    }

    @Transactional
    @PreAuthorize("hasAuthority('enrollment.write')")
    public WorkflowView assignClass(SchoolPrincipal actor, UUID workflowId, UUID classroomId) {
        BaseRow workflow = base(actor.tenantId(), workflowId, null);
        Integer valid =
                jdbc.queryForObject(
                        """
                        SELECT count(*) FROM classroom WHERE tenant_id=? AND id=?
                          AND academic_year_id=? AND campus_id=? AND active=TRUE
                        """,
                        Integer.class,
                        actor.tenantId(),
                        classroomId,
                        workflow.academicYearId(),
                        workflow.campusId());
        if (valid == null || valid == 0) {
            throw new EnrollmentValidationException(
                    "Classe incompatible avec le campus ou l'année");
        }
        jdbc.update(
                """
                UPDATE enrollment_workflow SET classroom_id=?,current_step='READY',
                    status='READY',updated_at=?,version=version+1 WHERE tenant_id=? AND id=?
                """,
                classroomId,
                timestamp(clock.instant()),
                actor.tenantId(),
                workflowId);
        return workflow(actor.tenantId(), workflowId, null);
    }

    @Transactional
    @PreAuthorize("hasAuthority('enrollment.write')")
    public WorkflowView activate(SchoolPrincipal actor, UUID workflowId, String ipAddress) {
        WorkflowView workflow = workflow(actor.tenantId(), workflowId, null);
        if (!"READY".equals(workflow.status())
                || workflow.classroomId() == null
                || !"ACCEPTED".equals(workflow.contract().status())
                || !workflow.familyConfirmed()
                || !workflow.services().confirmed()
                || !workflow.signed()
                || !workflow.feeStatus().equals("PAID")
                || !workflow.missingDocuments().isEmpty()) {
            throw new EnrollmentValidationException("Le parcours n'est pas prêt à être activé");
        }
        UUID studentId;
        if ("INITIAL".equals(workflow.workflowType())) {
            studentId = createInitialStudent(actor, workflow, ipAddress);
            jdbc.update(
                    """
                    INSERT INTO enrollment_portal_student_link
                        (id,tenant_id,portal_account_id,student_id,created_at)
                    VALUES (?, ?, ?, ?, ?)
                    """,
                    UUID.randomUUID(),
                    actor.tenantId(),
                    workflow.portalAccountId(),
                    studentId,
                    timestamp(clock.instant()));
        } else {
            studentId = workflow.studentId();
            try {
                jdbc.update(
                        """
                        INSERT INTO student_enrollment
                            (id,tenant_id,student_id,campus_id,academic_year_id,classroom_id,
                             status,enrolled_on,created_at,updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
                        """,
                        UUID.randomUUID(),
                        actor.tenantId(),
                        studentId,
                        workflow.campusId(),
                        workflow.academicYearId(),
                        workflow.classroomId(),
                        LocalDate.now(clock),
                        timestamp(clock.instant()),
                        timestamp(clock.instant()));
            } catch (DuplicateKeyException exception) {
                throw new EnrollmentValidationException(
                        "L'élève est déjà inscrit pour cette année");
            }
        }
        Instant now = clock.instant();
        jdbc.update(
                """
                UPDATE enrollment_workflow SET activated_student_id=?,current_step='ACTIVE',
                    status='ACTIVE',completed_at=?,updated_at=?,version=version+1
                WHERE tenant_id=? AND id=?
                """,
                studentId,
                timestamp(now),
                timestamp(now),
                actor.tenantId(),
                workflowId);
        return workflow(actor.tenantId(), workflowId, null);
    }

    private UUID createInitialStudent(
            SchoolPrincipal actor, WorkflowView workflow, String ipAddress) {
        InitialOrigin origin = initialOrigin(actor.tenantId(), workflow.applicationId());
        Map<String, Object> family = workflow.familyData();
        String identifier =
                "ELV-"
                        + LocalDate.now(clock).getYear()
                        + "-"
                        + workflow.id().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        return students.create(
                actor,
                new ImportedStudent(
                        origin.firstName(),
                        origin.lastName(),
                        null,
                        origin.birthDate(),
                        origin.gender(),
                        origin.nationalityCode(),
                        identifier,
                        "Famille " + origin.lastName(),
                        value(family, "addressLine1"),
                        value(family, "addressLine2"),
                        value(family, "city"),
                        value(family, "region"),
                        value(family, "postalCode"),
                        "MA",
                        value(family, "guardianFirstName"),
                        value(family, "guardianLastName"),
                        value(family, "relationship"),
                        value(family, "email"),
                        value(family, "phone"),
                        value(family, "occupation"),
                        "MA",
                        true,
                        workflow.campusId(),
                        workflow.academicYearId(),
                        workflow.classroomId(),
                        LocalDate.now(clock)),
                ipAddress);
    }

    private WorkflowView workflow(UUID tenantId, UUID workflowId, UUID owner) {
        BaseRow base = base(tenantId, workflowId, owner);
        Contract contract =
                jdbc.query(
                                "SELECT title,body,status FROM enrollment_contract WHERE"
                                        + " tenant_id=? AND workflow_id=?",
                                (result, row) ->
                                        new Contract(
                                                result.getString("title"),
                                                result.getString("body"),
                                                result.getString("status")),
                                tenantId,
                                workflowId)
                        .getFirst();
        FamilyRow family =
                jdbc.query(
                                "SELECT family_data,confirmed FROM enrollment_family_confirmation"
                                        + " WHERE tenant_id=? AND workflow_id=?",
                                (result, row) ->
                                        new FamilyRow(
                                                readMap(result.getString("family_data")),
                                                result.getBoolean("confirmed")),
                                tenantId,
                                workflowId)
                        .getFirst();
        ServicesRow services =
                jdbc.query(
                                "SELECT * FROM enrollment_service_selection WHERE tenant_id=? AND"
                                        + " workflow_id=?",
                                (result, row) ->
                                        new ServicesRow(
                                                result.getBoolean("transport_selected"),
                                                result.getString("transport_route"),
                                                result.getBoolean("cafeteria_selected"),
                                                readStrings(result.getString("activities")),
                                                result.getString("payment_plan"),
                                                result.getBoolean("confirmed")),
                                tenantId,
                                workflowId)
                        .getFirst();
        List<DocumentView> documents =
                jdbc.query(
                        "SELECT id,document_type,original_file_name,created_at FROM"
                            + " enrollment_document WHERE tenant_id=? AND workflow_id=? ORDER BY"
                            + " created_at",
                        (result, row) ->
                                new DocumentView(
                                        result.getObject("id", UUID.class),
                                        result.getString("document_type"),
                                        result.getString("original_file_name"),
                                        result.getTimestamp("created_at").toInstant()),
                        tenantId,
                        workflowId);
        boolean signed =
                scalar(
                                "SELECT count(*) FROM enrollment_signature WHERE tenant_id=? AND"
                                        + " workflow_id=?",
                                tenantId,
                                workflowId)
                        > 0;
        String feeStatus =
                scalar(
                                        "SELECT count(*) FROM enrollment_fee_payment WHERE"
                                            + " tenant_id=? AND workflow_id=? AND status='PAID'",
                                        tenantId,
                                        workflowId)
                                > 0
                        ? "PAID"
                        : "DUE";
        return new WorkflowView(
                base.id(),
                base.portalAccountId(),
                base.applicationId(),
                base.studentId(),
                base.academicYearId(),
                base.campusId(),
                base.classroomId(),
                base.workflowType(),
                base.currentStep(),
                base.status(),
                base.registrationFee(),
                base.currencyCode(),
                contract,
                family.data(),
                family.confirmed(),
                services,
                documents,
                missingDocuments(tenantId, workflowId),
                signed,
                feeStatus,
                base.activatedStudentId(),
                base.updatedAt());
    }

    private BaseRow base(UUID tenantId, UUID workflowId, UUID owner) {
        String sql =
                "SELECT * FROM enrollment_workflow WHERE tenant_id=? AND id=?"
                        + (owner == null ? "" : " AND portal_account_id=?");
        Object[] args =
                owner == null
                        ? new Object[] {tenantId, workflowId}
                        : new Object[] {tenantId, workflowId, owner};
        return jdbc.query(sql, (result, row) -> baseRow(result), args).stream()
                .findFirst()
                .orElseThrow(
                        () ->
                                new EnrollmentNotFoundException(
                                        "Parcours d'inscription introuvable"));
    }

    private BaseRow baseRow(ResultSet result) throws SQLException {
        return new BaseRow(
                result.getObject("id", UUID.class),
                result.getObject("portal_account_id", UUID.class),
                result.getObject("application_id", UUID.class),
                result.getObject("student_id", UUID.class),
                result.getObject("academic_year_id", UUID.class),
                result.getObject("campus_id", UUID.class),
                result.getObject("classroom_id", UUID.class),
                result.getString("workflow_type"),
                result.getString("current_step"),
                result.getString("status"),
                result.getBigDecimal("registration_fee"),
                result.getString("currency_code"),
                readStrings(result.getString("required_document_types")),
                result.getObject("activated_student_id", UUID.class),
                result.getTimestamp("updated_at").toInstant());
    }

    private void insertWorkflow(
            UUID id,
            PortalIdentity parent,
            UUID applicationId,
            UUID studentId,
            UUID yearId,
            UUID campusId,
            String type,
            Instant now) {
        jdbc.update(
                """
                INSERT INTO enrollment_workflow
                    (id,tenant_id,portal_account_id,application_id,student_id,academic_year_id,
                     campus_id,workflow_type,current_step,status,registration_fee,currency_code,
                     required_document_types,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'CONTRACT', 'IN_PROGRESS', 1000.00, 'MAD',
                        '["IDENTITY_COPY"]', ?, ?)
                """,
                id,
                parent.tenantId(),
                parent.accountId(),
                applicationId,
                studentId,
                yearId,
                campusId,
                type,
                timestamp(now),
                timestamp(now));
    }

    private void insertContract(UUID workflowId, UUID tenantId, String reference, Instant now) {
        jdbc.update(
                """
                INSERT INTO enrollment_contract
                    (id,tenant_id,workflow_id,contract_version,title,body,status,issued_at)
                VALUES (?, ?, ?, '2026.1', ?, ?, 'ISSUED', ?)
                """,
                UUID.randomUUID(),
                tenantId,
                workflowId,
                "Contrat d'inscription SchoolZ — " + reference,
                "Le responsable confirme les informations, les services choisis et le règlement"
                        + " intérieur de l'établissement.",
                timestamp(now));
    }

    private void insertFamilyAndServices(
            UUID workflowId, UUID tenantId, Map<String, Object> family, Instant now) {
        jdbc.update(
                """
                INSERT INTO enrollment_family_confirmation
                    (id,tenant_id,workflow_id,family_data,confirmed,updated_at)
                VALUES (?, ?, ?, ?, FALSE, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                workflowId,
                writeJson(family),
                timestamp(now));
        jdbc.update(
                """
                INSERT INTO enrollment_service_selection
                    (id,tenant_id,workflow_id,activities,payment_plan,confirmed,updated_at)
                VALUES (?, ?, ?, '[]', 'MONTHLY', FALSE, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                workflowId,
                timestamp(now));
    }

    private InitialOrigin initialOrigin(UUID tenantId, UUID applicationId) {
        return jdbc.query(
                        """
                        SELECT a.id,a.reference_number,a.desired_campus_id,c.academic_year_id,
                               p.first_name,p.last_name,p.birth_date,p.gender,p.nationality_code,
                               pa.email,pa.phone
                        FROM admission_application a
                        JOIN admission_campaign c ON c.id=a.campaign_id AND c.tenant_id=a.tenant_id
                        JOIN admission_applicant p ON p.id=a.applicant_id AND p.tenant_id=a.tenant_id
                        JOIN admission_portal_account pa ON pa.id=p.portal_account_id AND pa.tenant_id=p.tenant_id
                        WHERE a.tenant_id=? AND a.id=?
                        """,
                        (result, row) ->
                                new InitialOrigin(
                                        result.getObject("id", UUID.class),
                                        result.getString("reference_number"),
                                        result.getObject("desired_campus_id", UUID.class),
                                        result.getObject("academic_year_id", UUID.class),
                                        result.getString("first_name"),
                                        result.getString("last_name"),
                                        result.getObject("birth_date", LocalDate.class),
                                        result.getString("gender"),
                                        result.getString("nationality_code"),
                                        result.getString("email"),
                                        result.getString("phone")),
                        tenantId,
                        applicationId)
                .getFirst();
    }

    private Map<String, Object> existingFamily(UUID tenantId, UUID studentId) {
        return jdbc
                .query(
                        """
                        SELECT gp.first_name,gp.last_name,cm.contact_value AS email,
                               pa.line1,pa.line2,pa.city,pa.region,pa.postal_code
                        FROM student_profile s
                        JOIN person sp ON sp.id=s.person_id AND sp.tenant_id=s.tenant_id
                        JOIN household_relationship sr ON sr.person_id=sp.id AND sr.tenant_id=sp.tenant_id
                        JOIN household_relationship gr ON gr.household_id=sr.household_id AND gr.tenant_id=sr.tenant_id AND gr.legal_guardian=TRUE
                        JOIN person gp ON gp.id=gr.person_id AND gp.tenant_id=gr.tenant_id
                        LEFT JOIN person_contact_method cm ON cm.person_id=gp.id AND cm.tenant_id=gp.tenant_id AND cm.contact_type='EMAIL'
                        LEFT JOIN person_address pa ON pa.person_id=sp.id AND pa.tenant_id=sp.tenant_id AND pa.primary_address=TRUE
                        WHERE s.tenant_id=? AND s.id=? FETCH FIRST 1 ROWS ONLY
                        """,
                        (result, row) ->
                                Map.<String, Object>ofEntries(
                                        Map.entry(
                                                "guardianFirstName", string(result, "first_name")),
                                        Map.entry("guardianLastName", string(result, "last_name")),
                                        Map.entry("email", string(result, "email")),
                                        Map.entry("relationship", "PARENT"),
                                        Map.entry("addressLine1", string(result, "line1")),
                                        Map.entry("addressLine2", string(result, "line2")),
                                        Map.entry("city", string(result, "city")),
                                        Map.entry("region", string(result, "region")),
                                        Map.entry("postalCode", string(result, "postal_code")),
                                        Map.entry("phone", "")),
                        tenantId,
                        studentId)
                .stream()
                .findFirst()
                .orElse(Map.of());
    }

    private List<String> missingDocuments(UUID tenantId, UUID workflowId) {
        BaseRow base = base(tenantId, workflowId, null);
        List<String> uploaded =
                jdbc.query(
                        "SELECT DISTINCT document_type FROM enrollment_document WHERE tenant_id=?"
                                + " AND workflow_id=?",
                        (result, row) -> result.getString(1),
                        tenantId,
                        workflowId);
        return base.requiredDocuments().stream()
                .filter(required -> !uploaded.contains(required.toUpperCase(Locale.ROOT)))
                .toList();
    }

    private void requireOwned(PortalIdentity parent, UUID workflowId) {
        base(parent.tenantId(), workflowId, parent.accountId());
    }

    private void requireReference(String table, UUID tenantId, UUID id) {
        if (id == null
                || scalar(
                                "SELECT count(*) FROM " + table + " WHERE tenant_id=? AND id=?",
                                tenantId,
                                id)
                        == 0) {
            throw new EnrollmentNotFoundException("Référence académique introuvable");
        }
    }

    private void step(UUID tenantId, UUID workflowId, String next, Instant now) {
        jdbc.update(
                "UPDATE enrollment_workflow SET current_step=?,updated_at=?,version=version+1 WHERE"
                        + " tenant_id=? AND id=? AND status='IN_PROGRESS'",
                next,
                timestamp(now),
                tenantId,
                workflowId);
    }

    private List<UUID> workflowIds(String sql, Object... args) {
        return jdbc.query(sql, (result, row) -> result.getObject(1, UUID.class), args);
    }

    private int scalar(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private Map<String, Object> readMap(String source) {
        try {
            return json.readValue(source, OBJECT_MAP);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private List<String> readStrings(String source) {
        try {
            return json.readValue(source, STRING_LIST);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new EnrollmentValidationException("Données invalides");
        }
    }

    private static String value(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? "" : value.toString().strip();
    }

    private static String string(ResultSet result, String column) throws SQLException {
        String value = result.getString(column);
        return value == null ? "" : value;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return blank(value) ? null : value.strip();
    }

    private static String safeName(String value) {
        String name = value == null ? "document" : value.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._ -]", "_");
        return name.isBlank() ? "document" : name.substring(0, Math.min(255, name.length()));
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    public record RenewalCommand(UUID studentId, UUID academicYearId, UUID campusId) {}

    public record FamilyCommand(Map<String, Object> data) {}

    public record ServicesCommand(
            boolean transportSelected,
            String transportRoute,
            boolean cafeteriaSelected,
            List<String> activities,
            String paymentPlan) {}

    public record SignatureCommand(String signerName, boolean accepted) {}

    public record PaymentCommand(String cardLast4) {}

    public record Contract(String title, String body, String status) {}

    public record ServicesRow(
            boolean transportSelected,
            String transportRoute,
            boolean cafeteriaSelected,
            List<String> activities,
            String paymentPlan,
            boolean confirmed) {}

    public record DocumentView(UUID id, String documentType, String fileName, Instant createdAt) {}

    public record ClassOption(UUID id, String code, String name, int capacity) {}

    public record WorkflowView(
            UUID id,
            UUID portalAccountId,
            UUID applicationId,
            UUID studentId,
            UUID academicYearId,
            UUID campusId,
            UUID classroomId,
            String workflowType,
            String currentStep,
            String status,
            BigDecimal registrationFee,
            String currencyCode,
            Contract contract,
            Map<String, Object> familyData,
            boolean familyConfirmed,
            ServicesRow services,
            List<DocumentView> documents,
            List<String> missingDocuments,
            boolean signed,
            String feeStatus,
            UUID activatedStudentId,
            Instant updatedAt) {}

    private record FamilyRow(Map<String, Object> data, boolean confirmed) {}

    private record BaseRow(
            UUID id,
            UUID portalAccountId,
            UUID applicationId,
            UUID studentId,
            UUID academicYearId,
            UUID campusId,
            UUID classroomId,
            String workflowType,
            String currentStep,
            String status,
            BigDecimal registrationFee,
            String currencyCode,
            List<String> requiredDocuments,
            UUID activatedStudentId,
            Instant updatedAt) {}

    private record InitialOrigin(
            UUID applicationId,
            String reference,
            UUID campusId,
            UUID academicYearId,
            String firstName,
            String lastName,
            LocalDate birthDate,
            String gender,
            String nationalityCode,
            String email,
            String phone) {}
}
