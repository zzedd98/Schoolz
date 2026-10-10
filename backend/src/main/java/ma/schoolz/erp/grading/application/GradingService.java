package ma.schoolz.erp.grading.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.grading.engine.GradingCalculationEngine;
import ma.schoolz.erp.grading.engine.GradingCalculationEngine.CalculationModel;
import ma.schoolz.erp.grading.engine.GradingCalculationEngine.CalculationResult;
import ma.schoolz.erp.grading.engine.GradingCalculationEngine.MissingCategoryMode;
import ma.schoolz.erp.grading.engine.GradingCalculationEngine.ScoredItem;
import ma.schoolz.erp.grading.engine.GradingCalculationEngine.Strategy;
import ma.schoolz.erp.identity.IdentityAuditTrail;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GradingService {
    private static final Set<String> SCALE_TYPES = Set.of("NUMERIC", "COMPETENCY");
    private static final Set<String> MODES = Set.of("NONE", "COMPETENCY_ONLY", "HYBRID", "NUMERIC");
    private static final Set<String> STRATEGIES = Set.of("WEIGHTED_MEAN", "CATEGORY_WEIGHTED");
    private static final Set<String> GRADE_STATUSES =
            Set.of("GRADED", "ABSENT", "EXEMPT", "NOT_GRADED");
    private static final Set<String> COMMENT_TYPES =
            Set.of("GENERAL", "SUBJECT", "COMPETENCY", "BEHAVIOR");

    private final JdbcTemplate jdbc;
    private final GradingCalculationEngine engine;
    private final IdentityAuditTrail audit;
    private final ObjectMapper json;
    private final Clock clock;

    public GradingService(
            JdbcTemplate jdbc,
            GradingCalculationEngine engine,
            IdentityAuditTrail audit,
            ObjectMapper json,
            Clock clock) {
        this.jdbc = jdbc;
        this.engine = engine;
        this.audit = audit;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('grade.read')")
    public ReferenceData references(SchoolPrincipal actor) {
        UUID tenantId = actor.tenantId();
        List<YearOption> years =
                jdbc.query(
                        "SELECT id,code,name,status,starts_on,ends_on FROM academic_year WHERE"
                                + " tenant_id=? ORDER BY starts_on DESC",
                        (rs, row) ->
                                new YearOption(
                                        uuid(rs, "id"),
                                        rs.getString("code"),
                                        rs.getString("name"),
                                        rs.getString("status"),
                                        rs.getObject("starts_on", LocalDate.class),
                                        rs.getObject("ends_on", LocalDate.class)),
                        tenantId);
        List<TermOption> terms =
                jdbc.query(
                        "SELECT id,academic_year_id,code,name,sequence_number,starts_on,ends_on"
                                + " FROM academic_term WHERE tenant_id=? ORDER BY starts_on",
                        (rs, row) ->
                                new TermOption(
                                        uuid(rs, "id"),
                                        uuid(rs, "academic_year_id"),
                                        rs.getString("code"),
                                        rs.getString("name"),
                                        rs.getInt("sequence_number"),
                                        rs.getObject("starts_on", LocalDate.class),
                                        rs.getObject("ends_on", LocalDate.class)),
                        tenantId);
        List<SchoolLevelOption> schoolLevels =
                jdbc.query(
                        "SELECT id,code,name,sequence_number FROM school_level WHERE tenant_id=?"
                                + " AND active=TRUE ORDER BY sequence_number",
                        (rs, row) ->
                                new SchoolLevelOption(
                                        uuid(rs, "id"),
                                        rs.getString("code"),
                                        rs.getString("name"),
                                        rs.getInt("sequence_number")),
                        tenantId);
        List<GradeLevelOption> gradeLevels =
                jdbc.query(
                        "SELECT id,school_level_id,code,name,sequence_number FROM grade_level WHERE"
                                + " tenant_id=? AND active=TRUE ORDER BY sequence_number",
                        (rs, row) ->
                                new GradeLevelOption(
                                        uuid(rs, "id"),
                                        uuid(rs, "school_level_id"),
                                        rs.getString("code"),
                                        rs.getString("name"),
                                        rs.getInt("sequence_number")),
                        tenantId);
        List<ClassroomOption> classrooms =
                jdbc.query(
                        """
                        SELECT c.id,c.academic_year_id,c.grade_level_id,c.code,c.name,
                               gl.school_level_id,sl.code AS school_level_code,sl.name AS school_level_name
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
                                        uuid(rs, "grade_level_id"),
                                        uuid(rs, "school_level_id"),
                                        rs.getString("school_level_code"),
                                        rs.getString("school_level_name"),
                                        rs.getString("code"),
                                        rs.getString("name")),
                        tenantId);
        List<TeacherOption> teachers =
                jdbc.query(
                        """
                        SELECT sp.id,sp.employee_number,CONCAT(p.first_name,' ',p.last_name) AS name
                        FROM staff_profile sp
                        JOIN person p ON p.id=sp.person_id AND p.tenant_id=sp.tenant_id
                        WHERE sp.tenant_id=? AND sp.employment_status='ACTIVE'
                        ORDER BY p.last_name,p.first_name
                        """,
                        (rs, row) ->
                                new TeacherOption(
                                        uuid(rs, "id"),
                                        rs.getString("employee_number"),
                                        rs.getString("name")),
                        tenantId);
        return new ReferenceData(
                years,
                terms,
                schoolLevels,
                gradeLevels,
                classrooms,
                teachers,
                subjects(tenantId),
                scales(tenantId),
                policies(tenantId),
                assessmentTypes(tenantId),
                coefficients(tenantId),
                competencies(tenantId),
                templates(tenantId));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('grade.read')")
    public GradingDashboard dashboard(
            SchoolPrincipal actor, UUID classroomId, UUID academicTermId) {
        ClassScope scope = classScope(actor.tenantId(), classroomId, academicTermId);
        PolicyView policy = schoolPolicy(actor.tenantId(), scope.schoolLevelId());
        List<StudentView> students =
                students(actor.tenantId(), classroomId).stream()
                        .filter(student -> !student.childcare())
                        .toList();
        List<AssessmentView> assessments =
                assessments(actor.tenantId(), classroomId, academicTermId);
        List<GradeView> grades = grades(actor.tenantId(), classroomId, academicTermId);
        List<CompetencyEvaluationView> evaluations =
                competencyEvaluations(actor.tenantId(), classroomId, academicTermId);
        List<TeacherCommentView> comments =
                teacherComments(actor.tenantId(), classroomId, academicTermId);
        List<StudentSummary> summaries =
                summaries(actor.tenantId(), scope, policy, students, grades);
        List<ReportCardView> reportCards =
                reportCards(actor.tenantId(), classroomId, academicTermId);
        return new GradingDashboard(
                new ClassContext(
                        scope.classroomId(),
                        scope.classroomName(),
                        scope.academicYearId(),
                        scope.academicYearName(),
                        scope.termId(),
                        scope.termName(),
                        scope.gradeLevelId(),
                        scope.gradeLevelName(),
                        scope.schoolLevelId(),
                        scope.schoolLevelCode(),
                        scope.schoolLevelName()),
                policy,
                students,
                assessments,
                grades,
                competencies(actor.tenantId()).stream()
                        .filter(value -> value.schoolLevelId().equals(scope.schoolLevelId()))
                        .toList(),
                evaluations,
                comments,
                summaries,
                reportCards);
    }

    @Transactional
    @PreAuthorize("hasAuthority('grade.configure')")
    public GradeScaleView createScale(
            SchoolPrincipal actor, GradeScaleCommand command, String ipAddress) {
        validateScale(command);
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        String type = normalize(command.scaleType());
        try {
            jdbc.update(
                    """
                    INSERT INTO grading_grade_scale
                        (id,tenant_id,code,name,scale_type,minimum_score,maximum_score,
                         passing_score,rounding_scale,competency_levels,active,created_at,updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?)
                    """,
                    id,
                    actor.tenantId(),
                    normalize(command.code()),
                    command.name().strip(),
                    type,
                    type.equals("NUMERIC") ? command.minimumScore() : null,
                    type.equals("NUMERIC") ? command.maximumScore() : null,
                    type.equals("NUMERIC") ? command.passingScore() : null,
                    command.roundingScale(),
                    writeJson(
                            command.competencyLevels() == null
                                    ? List.of()
                                    : command.competencyLevels()),
                    timestamp(now),
                    timestamp(now));
        } catch (DuplicateKeyException exception) {
            throw new GradingValidationException("Ce code d'échelle existe déjà");
        }
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "grading.scale.created",
                "GradeScale",
                id.toString(),
                ipAddress,
                Map.of("code", normalize(command.code()), "type", type),
                null,
                Map.of("active", true));
        return scale(actor.tenantId(), id);
    }

    @Transactional
    @PreAuthorize("hasAuthority('grade.configure')")
    public PolicyView savePolicy(SchoolPrincipal actor, PolicyCommand command, String ipAddress) {
        if (command == null || blank(command.name()) || blank(command.code())) {
            throw new GradingValidationException("Le code et le nom de la politique sont requis");
        }
        String scopeType = normalize(command.scopeType());
        if (!Set.of("SCHOOL_LEVEL", "CHILDCARE").contains(scopeType)) {
            throw new GradingValidationException("Portée de politique invalide");
        }
        if (scopeType.equals("SCHOOL_LEVEL") && command.schoolLevelId() == null) {
            throw new GradingValidationException("Le cycle scolaire est requis");
        }
        if (scopeType.equals("CHILDCARE") && command.schoolLevelId() != null) {
            throw new GradingValidationException(
                    "La politique crèche ne cible pas un cycle scolaire");
        }
        String mode = normalize(command.evaluationMode());
        String strategy = normalize(command.calculationStrategy());
        if (!MODES.contains(mode) || !STRATEGIES.contains(strategy)) {
            throw new GradingValidationException("Mode ou stratégie de calcul invalide");
        }
        String scopeKey =
                scopeType.equals("CHILDCARE") ? "CHILDCARE" : command.schoolLevelId().toString();
        if (command.schoolLevelId() != null) {
            requireExists(
                    "school_level", actor.tenantId(), command.schoolLevelId(), "Cycle introuvable");
        }
        validatePolicyScales(
                actor.tenantId(), mode, command.gradeScaleId(), command.competencyScaleId());
        String config =
                writeJson(
                        command.calculationConfig() == null
                                ? Map.of()
                                : command.calculationConfig());
        validateCalculationModel(strategy, config);
        PolicyView before = policyByScope(actor.tenantId(), scopeType, scopeKey, false);
        UUID id = before == null ? UUID.randomUUID() : before.id();
        Instant now = clock.instant();
        if (before == null) {
            jdbc.update(
                    """
                    INSERT INTO grading_policy
                        (id,tenant_id,scope_type,scope_key,school_level_id,code,name,evaluation_mode,
                         grade_scale_id,competency_scale_id,calculation_strategy,calculation_config,
                         rankings_enabled,report_cards_enabled,active,created_at,updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?)
                    """,
                    id,
                    actor.tenantId(),
                    scopeType,
                    scopeKey,
                    command.schoolLevelId(),
                    normalize(command.code()),
                    command.name().strip(),
                    mode,
                    command.gradeScaleId(),
                    command.competencyScaleId(),
                    strategy,
                    config,
                    command.rankingsEnabled(),
                    command.reportCardsEnabled(),
                    timestamp(now),
                    timestamp(now));
        } else {
            jdbc.update(
                    """
                    UPDATE grading_policy
                    SET code=?,name=?,evaluation_mode=?,grade_scale_id=?,competency_scale_id=?,
                        calculation_strategy=?,calculation_config=?,rankings_enabled=?,
                        report_cards_enabled=?,active=TRUE,updated_at=?
                    WHERE id=? AND tenant_id=?
                    """,
                    normalize(command.code()),
                    command.name().strip(),
                    mode,
                    command.gradeScaleId(),
                    command.competencyScaleId(),
                    strategy,
                    config,
                    command.rankingsEnabled(),
                    command.reportCardsEnabled(),
                    timestamp(now),
                    id,
                    actor.tenantId());
        }
        PolicyView after = policy(actor.tenantId(), id);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                before == null ? "grading.policy.created" : "grading.policy.updated",
                "GradingPolicy",
                id.toString(),
                ipAddress,
                Map.of("scope", scopeType, "scopeKey", scopeKey),
                before,
                after);
        return after;
    }

    @Transactional
    @PreAuthorize("hasAuthority('grade.configure')")
    public AssessmentTypeView createAssessmentType(
            SchoolPrincipal actor, AssessmentTypeCommand command, String ipAddress) {
        if (command == null
                || blank(command.code())
                || blank(command.nameFr())
                || blank(command.calculationCategory())
                || (!command.numericEnabled() && !command.competencyEnabled())) {
            throw new GradingValidationException("Type d'évaluation incomplet");
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        try {
            jdbc.update(
                    """
                    INSERT INTO grading_assessment_type
                        (id,tenant_id,code,name_fr,name_ar,calculation_category,numeric_enabled,
                         competency_enabled,active,created_at,updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?)
                    """,
                    id,
                    actor.tenantId(),
                    normalize(command.code()),
                    command.nameFr().strip(),
                    trim(command.nameAr()),
                    normalize(command.calculationCategory()),
                    command.numericEnabled(),
                    command.competencyEnabled(),
                    timestamp(now),
                    timestamp(now));
        } catch (DuplicateKeyException exception) {
            throw new GradingValidationException("Ce type d'évaluation existe déjà");
        }
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "grading.assessment-type.created",
                "AssessmentType",
                id.toString(),
                ipAddress,
                Map.of("code", normalize(command.code())),
                null,
                Map.of("active", true));
        return assessmentType(actor.tenantId(), id);
    }

    @Transactional
    @PreAuthorize("hasAuthority('grade.configure')")
    public CoefficientView saveCoefficient(
            SchoolPrincipal actor, CoefficientCommand command, String ipAddress) {
        if (command == null
                || command.academicYearId() == null
                || command.gradeLevelId() == null
                || command.subjectId() == null
                || command.coefficient() == null
                || command.coefficient().signum() <= 0) {
            throw new GradingValidationException("Coefficient incomplet ou invalide");
        }
        requireExists(
                "academic_year", actor.tenantId(), command.academicYearId(), "Année introuvable");
        requireExists(
                "grade_level", actor.tenantId(), command.gradeLevelId(), "Niveau introuvable");
        requireExists(
                "timetable_subject", actor.tenantId(), command.subjectId(), "Matière introuvable");
        CoefficientView before = coefficient(actor.tenantId(), command, false);
        UUID id = before == null ? UUID.randomUUID() : before.id();
        Instant now = clock.instant();
        if (before == null) {
            jdbc.update(
                    """
                    INSERT INTO grading_coefficient
                        (id,tenant_id,academic_year_id,grade_level_id,subject_id,coefficient,
                         created_at,updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    id,
                    actor.tenantId(),
                    command.academicYearId(),
                    command.gradeLevelId(),
                    command.subjectId(),
                    command.coefficient(),
                    timestamp(now),
                    timestamp(now));
        } else {
            jdbc.update(
                    "UPDATE grading_coefficient SET coefficient=?,updated_at=? WHERE id=? AND"
                            + " tenant_id=?",
                    command.coefficient(),
                    timestamp(now),
                    id,
                    actor.tenantId());
        }
        CoefficientView after = coefficient(actor.tenantId(), id);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "grading.coefficient.saved",
                "Coefficient",
                id.toString(),
                ipAddress,
                Map.of("subjectId", command.subjectId()),
                before,
                after);
        return after;
    }

    @Transactional
    @PreAuthorize("hasAuthority('grade.configure')")
    public CompetencyView createCompetency(
            SchoolPrincipal actor, CompetencyCommand command, String ipAddress) {
        if (command == null
                || command.schoolLevelId() == null
                || blank(command.code())
                || blank(command.nameFr())
                || command.sequenceNumber() <= 0) {
            throw new GradingValidationException("Compétence incomplète");
        }
        requireExists(
                "school_level", actor.tenantId(), command.schoolLevelId(), "Cycle introuvable");
        if (command.subjectId() != null) {
            requireExists(
                    "timetable_subject",
                    actor.tenantId(),
                    command.subjectId(),
                    "Matière introuvable");
        }
        if (command.parentCompetencyId() != null) {
            requireExists(
                    "grading_competency",
                    actor.tenantId(),
                    command.parentCompetencyId(),
                    "Compétence parente introuvable");
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        try {
            jdbc.update(
                    """
                    INSERT INTO grading_competency
                        (id,tenant_id,school_level_id,subject_id,parent_competency_id,code,name_fr,
                         name_ar,description,sequence_number,active,created_at,updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?)
                    """,
                    id,
                    actor.tenantId(),
                    command.schoolLevelId(),
                    command.subjectId(),
                    command.parentCompetencyId(),
                    normalize(command.code()),
                    command.nameFr().strip(),
                    trim(command.nameAr()),
                    trim(command.description()),
                    command.sequenceNumber(),
                    timestamp(now),
                    timestamp(now));
        } catch (DuplicateKeyException exception) {
            throw new GradingValidationException("Ce code de compétence existe déjà pour ce cycle");
        }
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "grading.competency.created",
                "Competency",
                id.toString(),
                ipAddress,
                Map.of("code", normalize(command.code())),
                null,
                Map.of("active", true));
        return competency(actor.tenantId(), id);
    }

    @Transactional
    @PreAuthorize("hasAuthority('grade.configure')")
    public ReportCardTemplateView createTemplate(
            SchoolPrincipal actor, ReportCardTemplateCommand command, String ipAddress) {
        if (command == null
                || command.schoolLevelId() == null
                || blank(command.code())
                || blank(command.name())
                || !Set.of("FR", "AR", "BILINGUAL").contains(normalize(command.languageMode()))) {
            throw new GradingValidationException("Modèle de bulletin incomplet");
        }
        requireExists(
                "school_level", actor.tenantId(), command.schoolLevelId(), "Cycle introuvable");
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        int version = command.versionNumber() <= 0 ? 1 : command.versionNumber();
        try {
            jdbc.update(
                    """
                    INSERT INTO grading_report_card_template
                        (id,tenant_id,school_level_id,code,name,language_mode,layout_config,
                         version_number,active,created_at,updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?)
                    """,
                    id,
                    actor.tenantId(),
                    command.schoolLevelId(),
                    normalize(command.code()),
                    command.name().strip(),
                    normalize(command.languageMode()),
                    writeJson(command.layoutConfig() == null ? Map.of() : command.layoutConfig()),
                    version,
                    timestamp(now),
                    timestamp(now));
        } catch (DuplicateKeyException exception) {
            throw new GradingValidationException(
                    "Ce code et cette version de modèle existent déjà");
        }
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "grading.report-template.created",
                "ReportCardTemplate",
                id.toString(),
                ipAddress,
                Map.of("code", normalize(command.code()), "version", version),
                null,
                Map.of("active", true));
        return template(actor.tenantId(), id);
    }

    @Transactional
    @PreAuthorize("hasAuthority('grade.write')")
    public AssessmentView createAssessment(
            SchoolPrincipal actor, AssessmentCommand command, String ipAddress) {
        if (command == null
                || command.classroomId() == null
                || command.academicTermId() == null
                || command.subjectId() == null
                || command.assessmentTypeId() == null
                || command.teacherStaffId() == null
                || blank(command.title())
                || command.assessmentDate() == null
                || command.coefficient() == null
                || command.coefficient().signum() <= 0) {
            throw new GradingValidationException("Évaluation incomplète");
        }
        ClassScope scope =
                classScope(actor.tenantId(), command.classroomId(), command.academicTermId());
        PolicyView policy = schoolPolicy(actor.tenantId(), scope.schoolLevelId());
        if (policy.evaluationMode().equals("NONE")) {
            throw new GradingConflictException("Ce cycle n'autorise aucune évaluation");
        }
        AssessmentTypeView type = assessmentType(actor.tenantId(), command.assessmentTypeId());
        boolean numericAllowed =
                policy.evaluationMode().equals("NUMERIC")
                        || policy.evaluationMode().equals("HYBRID");
        boolean competencyAllowed =
                policy.evaluationMode().equals("COMPETENCY_ONLY")
                        || policy.evaluationMode().equals("HYBRID");
        if (type.numericEnabled() && !numericAllowed && !type.competencyEnabled()) {
            throw new GradingConflictException(
                    "Les notes numériques sont désactivées pour ce cycle");
        }
        if (type.competencyEnabled() && !competencyAllowed && !type.numericEnabled()) {
            throw new GradingConflictException("Les compétences sont désactivées pour ce cycle");
        }
        if (type.numericEnabled()
                && numericAllowed
                && (command.maximumScore() == null || command.maximumScore().signum() <= 0)) {
            throw new GradingValidationException("Le barème maximum est requis");
        }
        requireExists(
                "timetable_subject", actor.tenantId(), command.subjectId(), "Matière introuvable");
        requireExists(
                "staff_profile",
                actor.tenantId(),
                command.teacherStaffId(),
                "Enseignant introuvable");
        if (command.assessmentDate().isBefore(scope.termStartsOn())
                || command.assessmentDate().isAfter(scope.termEndsOn())) {
            throw new GradingValidationException(
                    "La date doit appartenir à la période sélectionnée");
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                """
                INSERT INTO grading_assessment
                    (id,tenant_id,academic_year_id,academic_term_id,classroom_id,subject_id,
                     assessment_type_id,teacher_staff_id,title,description,assessment_date,
                     maximum_score,coefficient,status,created_by_user_id,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?)
                """,
                id,
                actor.tenantId(),
                scope.academicYearId(),
                scope.termId(),
                scope.classroomId(),
                command.subjectId(),
                command.assessmentTypeId(),
                command.teacherStaffId(),
                command.title().strip(),
                trim(command.description()),
                command.assessmentDate(),
                command.maximumScore(),
                command.coefficient(),
                actor.accountId(),
                timestamp(now),
                timestamp(now));
        AssessmentView after = assessment(actor.tenantId(), id);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "grading.assessment.created",
                "Assessment",
                id.toString(),
                ipAddress,
                Map.of("classroomId", scope.classroomId(), "termId", scope.termId()),
                null,
                after);
        return after;
    }

    @Transactional
    @PreAuthorize("hasAuthority('grade.write')")
    public AssessmentView publishAssessment(
            SchoolPrincipal actor, UUID assessmentId, String ipAddress) {
        AssessmentView before = assessment(actor.tenantId(), assessmentId);
        if (before.status().equals("CANCELLED")) {
            throw new GradingConflictException("Une évaluation annulée ne peut pas être publiée");
        }
        if (!before.status().equals("PUBLISHED")) {
            jdbc.update(
                    "UPDATE grading_assessment SET status='PUBLISHED',updated_at=? WHERE id=? AND"
                            + " tenant_id=?",
                    timestamp(clock.instant()),
                    assessmentId,
                    actor.tenantId());
        }
        AssessmentView after = assessment(actor.tenantId(), assessmentId);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "grading.assessment.published",
                "Assessment",
                assessmentId.toString(),
                ipAddress,
                Map.of(),
                before,
                after);
        return after;
    }

    @Transactional
    @PreAuthorize("hasAuthority('grade.write')")
    public GradeView saveGrade(
            SchoolPrincipal actor,
            UUID assessmentId,
            UUID studentId,
            GradeCommand command,
            String ipAddress) {
        if (command == null || !GRADE_STATUSES.contains(normalize(command.status()))) {
            throw new GradingValidationException("Statut de note invalide");
        }
        AssessmentInternal assessment = assessmentInternal(actor.tenantId(), assessmentId);
        if (assessment.status().equals("CANCELLED")) {
            throw new GradingConflictException("Cette évaluation est annulée");
        }
        if (isChildcareStudent(actor.tenantId(), studentId)) {
            throw new GradingConflictException(
                    "Un enfant rattaché à la crèche ne peut recevoir de note");
        }
        if (!studentInClass(actor.tenantId(), studentId, assessment.classroomId())) {
            throw new GradingNotFoundException("Élève absent de cette classe");
        }
        PolicyView policy = schoolPolicy(actor.tenantId(), assessment.schoolLevelId());
        if (!Set.of("NUMERIC", "HYBRID").contains(policy.evaluationMode())
                || !assessment.numericEnabled()) {
            throw new GradingConflictException(
                    "Les notes numériques sont désactivées pour ce modèle");
        }
        String status = normalize(command.status());
        BigDecimal score = status.equals("GRADED") ? command.rawScore() : null;
        if (status.equals("GRADED")
                && (score == null
                        || score.signum() < 0
                        || assessment.maximumScore() == null
                        || score.compareTo(assessment.maximumScore()) > 0)) {
            throw new GradingValidationException(
                    "La note doit être comprise entre 0 et " + assessment.maximumScore());
        }
        GradeView before = grade(actor.tenantId(), assessmentId, studentId, false);
        if (before != null
                && (blank(command.reason()) || command.reason().strip().length() < 3)
                && (!Objects.equals(before.rawScore(), score) || !before.status().equals(status))) {
            throw new GradingValidationException("Un motif est requis pour modifier une note");
        }
        if (before != null
                && Objects.equals(before.rawScore(), score)
                && before.status().equals(status)
                && Objects.equals(before.teacherComment(), trim(command.teacherComment()))) {
            return before;
        }
        UUID gradeId = before == null ? UUID.randomUUID() : before.id();
        Instant now = clock.instant();
        if (before == null) {
            jdbc.update(
                    """
                    INSERT INTO grading_grade
                        (id,tenant_id,assessment_id,student_id,raw_score,grade_status,teacher_comment,
                         version,created_by_user_id,updated_by_user_id,created_at,updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?)
                    """,
                    gradeId,
                    actor.tenantId(),
                    assessmentId,
                    studentId,
                    score,
                    status,
                    trim(command.teacherComment()),
                    actor.accountId(),
                    actor.accountId(),
                    timestamp(now),
                    timestamp(now));
        } else {
            jdbc.update(
                    """
                    UPDATE grading_grade
                    SET raw_score=?,grade_status=?,teacher_comment=?,version=version+1,
                        updated_by_user_id=?,updated_at=?
                    WHERE id=? AND tenant_id=?
                    """,
                    score,
                    status,
                    trim(command.teacherComment()),
                    actor.accountId(),
                    timestamp(now),
                    gradeId,
                    actor.tenantId());
        }
        GradeView after = grade(actor.tenantId(), assessmentId, studentId, true);
        String changeType =
                before == null
                        ? "CREATED"
                        : before.status().equals(after.status()) ? "UPDATED" : "STATUS_CHANGED";
        jdbc.update(
                """
                INSERT INTO grading_grade_history
                    (id,tenant_id,grade_id,assessment_id,student_id,change_type,previous_score,
                     new_score,previous_status,new_status,reason,actor_user_id,changed_at,
                     before_snapshot,after_snapshot)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                actor.tenantId(),
                gradeId,
                assessmentId,
                studentId,
                changeType,
                before == null ? null : before.rawScore(),
                after.rawScore(),
                before == null ? null : before.status(),
                after.status(),
                before == null ? trim(command.reason()) : command.reason().strip(),
                actor.accountId(),
                timestamp(now),
                before == null ? null : writeJson(before),
                writeJson(after));
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                before == null ? "grading.grade.created" : "grading.grade.updated",
                "Grade",
                gradeId.toString(),
                ipAddress,
                Map.of(
                        "assessmentId",
                        assessmentId,
                        "studentId",
                        studentId,
                        "reason",
                        trim(command.reason()) == null ? "initial-entry" : trim(command.reason())),
                before,
                after);
        return after;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('grade.read')")
    public List<GradeHistoryView> gradeHistory(SchoolPrincipal actor, UUID gradeId) {
        requireExists("grading_grade", actor.tenantId(), gradeId, "Note introuvable");
        return jdbc.query(
                """
                SELECT h.id,h.change_type,h.previous_score,h.new_score,h.previous_status,
                       h.new_status,h.reason,h.changed_at,h.actor_user_id,u.display_name AS actor_name
                FROM grading_grade_history h
                JOIN identity_user_account u ON u.id=h.actor_user_id AND u.tenant_id=h.tenant_id
                WHERE h.tenant_id=? AND h.grade_id=?
                ORDER BY h.changed_at,h.id
                """,
                (rs, row) ->
                        new GradeHistoryView(
                                uuid(rs, "id"),
                                rs.getString("change_type"),
                                decimal(rs, "previous_score"),
                                decimal(rs, "new_score"),
                                rs.getString("previous_status"),
                                rs.getString("new_status"),
                                rs.getString("reason"),
                                rs.getTimestamp("changed_at").toInstant(),
                                uuid(rs, "actor_user_id"),
                                rs.getString("actor_name")),
                actor.tenantId(),
                gradeId);
    }

    @Transactional
    @PreAuthorize("hasAuthority('grade.write')")
    public CompetencyEvaluationView evaluateCompetency(
            SchoolPrincipal actor, CompetencyEvaluationCommand command, String ipAddress) {
        if (command == null
                || command.competencyId() == null
                || command.studentId() == null
                || command.academicTermId() == null
                || command.teacherStaffId() == null
                || blank(command.levelCode())) {
            throw new GradingValidationException("Évaluation de compétence incomplète");
        }
        StudentScope scope =
                studentScope(actor.tenantId(), command.studentId(), command.academicTermId());
        PolicyView policy =
                isChildcareStudent(actor.tenantId(), command.studentId())
                        ? childcarePolicy(actor.tenantId())
                        : schoolPolicy(actor.tenantId(), scope.schoolLevelId());
        if (!Set.of("COMPETENCY_ONLY", "HYBRID").contains(policy.evaluationMode())) {
            throw new GradingConflictException("Les compétences sont désactivées pour ce modèle");
        }
        CompetencyView competency = competency(actor.tenantId(), command.competencyId());
        if (!competency.schoolLevelId().equals(scope.schoolLevelId())) {
            throw new GradingConflictException(
                    "Cette compétence n'appartient pas au cycle de l'élève");
        }
        Set<String> allowedLevels =
                competencyLevelCodes(actor.tenantId(), policy.competencyScaleId());
        String level = normalize(command.levelCode());
        if (!allowedLevels.contains(level)) {
            throw new GradingValidationException("Niveau de compétence inconnu : " + level);
        }
        requireExists(
                "staff_profile",
                actor.tenantId(),
                command.teacherStaffId(),
                "Enseignant introuvable");
        if (command.assessmentId() != null) {
            AssessmentInternal assessment =
                    assessmentInternal(actor.tenantId(), command.assessmentId());
            if (!assessment.classroomId().equals(scope.classroomId())
                    || !assessment.termId().equals(command.academicTermId())) {
                throw new GradingConflictException(
                        "L'évaluation ne correspond pas à la classe et période");
            }
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                """
                INSERT INTO grading_competency_evaluation
                    (id,tenant_id,competency_id,student_id,academic_term_id,assessment_id,
                     teacher_staff_id,level_code,observation,evaluated_at,created_by_user_id,
                     created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                actor.tenantId(),
                command.competencyId(),
                command.studentId(),
                command.academicTermId(),
                command.assessmentId(),
                command.teacherStaffId(),
                level,
                trim(command.observation()),
                timestamp(now),
                actor.accountId(),
                timestamp(now),
                timestamp(now));
        CompetencyEvaluationView after = competencyEvaluation(actor.tenantId(), id);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "grading.competency-evaluated",
                "CompetencyEvaluation",
                id.toString(),
                ipAddress,
                Map.of("studentId", command.studentId(), "level", level),
                null,
                after);
        return after;
    }

    @Transactional
    @PreAuthorize("hasAuthority('grade.write')")
    public TeacherCommentView createComment(
            SchoolPrincipal actor, TeacherCommentCommand command, String ipAddress) {
        if (command == null
                || command.studentId() == null
                || command.academicTermId() == null
                || command.teacherStaffId() == null
                || blank(command.body())
                || !COMMENT_TYPES.contains(normalize(command.commentType()))) {
            throw new GradingValidationException("Commentaire enseignant incomplet");
        }
        studentScope(actor.tenantId(), command.studentId(), command.academicTermId());
        requireExists(
                "staff_profile",
                actor.tenantId(),
                command.teacherStaffId(),
                "Enseignant introuvable");
        if (command.subjectId() != null) {
            requireExists(
                    "timetable_subject",
                    actor.tenantId(),
                    command.subjectId(),
                    "Matière introuvable");
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                """
                INSERT INTO grading_teacher_comment
                    (id,tenant_id,student_id,academic_term_id,subject_id,teacher_staff_id,
                     comment_type,body,visible_to_family,created_by_user_id,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                actor.tenantId(),
                command.studentId(),
                command.academicTermId(),
                command.subjectId(),
                command.teacherStaffId(),
                normalize(command.commentType()),
                command.body().strip(),
                command.visibleToFamily(),
                actor.accountId(),
                timestamp(now),
                timestamp(now));
        TeacherCommentView after = teacherComment(actor.tenantId(), id);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "grading.teacher-comment.created",
                "TeacherComment",
                id.toString(),
                ipAddress,
                Map.of("studentId", command.studentId(), "type", normalize(command.commentType())),
                null,
                Map.of("visibleToFamily", command.visibleToFamily()));
        return after;
    }

    @Transactional
    @PreAuthorize("hasAuthority('report-card.calculate')")
    public List<ReportCardView> calculateReportCards(
            SchoolPrincipal actor,
            UUID classroomId,
            UUID academicTermId,
            UUID templateId,
            String ipAddress) {
        ClassScope scope = classScope(actor.tenantId(), classroomId, academicTermId);
        PolicyView policy = schoolPolicy(actor.tenantId(), scope.schoolLevelId());
        if (!policy.reportCardsEnabled() || policy.evaluationMode().equals("NONE")) {
            throw new GradingConflictException("Les bulletins sont désactivés pour ce modèle");
        }
        if (templateId != null) {
            ReportCardTemplateView template = template(actor.tenantId(), templateId);
            if (!template.schoolLevelId().equals(scope.schoolLevelId())) {
                throw new GradingConflictException(
                        "Le modèle de bulletin ne correspond pas au cycle");
            }
        }
        List<StudentView> students =
                students(actor.tenantId(), classroomId).stream()
                        .filter(student -> !student.childcare())
                        .toList();
        List<GradeView> grades = grades(actor.tenantId(), classroomId, academicTermId);
        List<StudentSummary> summaries =
                summaries(actor.tenantId(), scope, policy, students, grades);
        Map<UUID, StudentSummary> byStudent =
                summaries.stream()
                        .collect(Collectors.toMap(StudentSummary::studentId, value -> value));
        Instant now = clock.instant();
        for (StudentView student : students) {
            StudentSummary summary = byStudent.get(student.id());
            LatestReport latest = latestReport(actor.tenantId(), student.id(), academicTermId);
            boolean createVersion = latest == null || latest.status().equals("PUBLISHED");
            UUID reportId = createVersion ? UUID.randomUUID() : latest.id();
            int versionNumber =
                    latest == null
                            ? 1
                            : createVersion ? latest.versionNumber() + 1 : latest.versionNumber();
            UUID supersedesId =
                    latest != null && latest.status().equals("PUBLISHED") ? latest.id() : null;
            Map<String, Object> snapshotValues = new LinkedHashMap<>();
            snapshotValues.put("policyId", policy.id());
            snapshotValues.put("strategy", policy.calculationStrategy());
            snapshotValues.put("configuration", policy.calculationConfig());
            snapshotValues.put("calculatedAt", now.toString());
            snapshotValues.put("versionNumber", versionNumber);
            snapshotValues.put("supersedesReportCardId", supersedesId);
            String snapshot = writeJson(snapshotValues);
            if (createVersion) {
                jdbc.update(
                        """
                        INSERT INTO grading_report_card
                            (id,tenant_id,student_id,academic_year_id,academic_term_id,classroom_id,
                             policy_id,template_id,status,overall_average,class_rank,class_size,
                             calculation_snapshot,version_number,calculated_by_user_id,calculated_at,
                             created_at,updated_at,supersedes_report_card_id)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'CALCULATED', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        reportId,
                        actor.tenantId(),
                        student.id(),
                        scope.academicYearId(),
                        academicTermId,
                        classroomId,
                        policy.id(),
                        templateId,
                        summary == null ? null : summary.overallAverage(),
                        summary == null ? null : summary.rank(),
                        summary == null ? null : summary.classSize(),
                        snapshot,
                        versionNumber,
                        actor.accountId(),
                        timestamp(now),
                        timestamp(now),
                        timestamp(now),
                        supersedesId);
            } else {
                jdbc.update(
                        """
                        UPDATE grading_report_card
                        SET policy_id=?,template_id=?,status='CALCULATED',overall_average=?,
                            class_rank=?,class_size=?,calculation_snapshot=?,calculated_by_user_id=?,
                            calculated_at=?,updated_at=?
                        WHERE id=? AND tenant_id=? AND status<>'PUBLISHED'
                        """,
                        policy.id(),
                        templateId,
                        summary == null ? null : summary.overallAverage(),
                        summary == null ? null : summary.rank(),
                        summary == null ? null : summary.classSize(),
                        snapshot,
                        actor.accountId(),
                        timestamp(now),
                        timestamp(now),
                        reportId,
                        actor.tenantId());
            }
            jdbc.update(
                    "DELETE FROM grading_report_card_subject_result WHERE report_card_id=? AND"
                            + " tenant_id=?",
                    reportId,
                    actor.tenantId());
            if (summary != null) {
                for (SubjectSummary subject : summary.subjects()) {
                    jdbc.update(
                            """
                            INSERT INTO grading_report_card_subject_result
                                (id,tenant_id,report_card_id,subject_id,average,coefficient,
                                 category_breakdown,teacher_comment)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                            UUID.randomUUID(),
                            actor.tenantId(),
                            reportId,
                            subject.subjectId(),
                            subject.average(),
                            subject.coefficient(),
                            writeJson(subject.categoryAverages()),
                            latestSubjectComment(
                                    actor.tenantId(),
                                    student.id(),
                                    academicTermId,
                                    subject.subjectId()));
                }
            }
        }
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "grading.report-cards.calculated",
                "ReportCardBatch",
                classroomId + ":" + academicTermId,
                ipAddress,
                Map.of("classroomId", classroomId, "termId", academicTermId),
                null,
                Map.of("count", students.size(), "rankings", policy.rankingsEnabled()));
        return reportCards(actor.tenantId(), classroomId, academicTermId);
    }

    private List<SubjectView> subjects(UUID tenantId) {
        return jdbc.query(
                "SELECT id,code,name_fr,name_ar,color FROM timetable_subject WHERE tenant_id=? AND"
                        + " active=TRUE ORDER BY name_fr",
                (rs, row) ->
                        new SubjectView(
                                uuid(rs, "id"),
                                rs.getString("code"),
                                rs.getString("name_fr"),
                                rs.getString("name_ar"),
                                rs.getString("color")),
                tenantId);
    }

    private List<GradeScaleView> scales(UUID tenantId) {
        return jdbc.query(
                """
                SELECT id,code,name,scale_type,minimum_score,maximum_score,passing_score,
                       rounding_scale,competency_levels,active
                FROM grading_grade_scale WHERE tenant_id=? ORDER BY scale_type,name
                """,
                (rs, row) -> scaleView(rs),
                tenantId);
    }

    private GradeScaleView scale(UUID tenantId, UUID id) {
        List<GradeScaleView> rows =
                jdbc.query(
                        """
                        SELECT id,code,name,scale_type,minimum_score,maximum_score,passing_score,
                               rounding_scale,competency_levels,active
                        FROM grading_grade_scale WHERE tenant_id=? AND id=?
                        """,
                        (rs, row) -> scaleView(rs),
                        tenantId,
                        id);
        if (rows.isEmpty()) throw new GradingNotFoundException("Échelle introuvable");
        return rows.getFirst();
    }

    private GradeScaleView scaleView(ResultSet rs) throws SQLException {
        return new GradeScaleView(
                uuid(rs, "id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("scale_type"),
                decimal(rs, "minimum_score"),
                decimal(rs, "maximum_score"),
                decimal(rs, "passing_score"),
                rs.getInt("rounding_scale"),
                readLevels(rs.getString("competency_levels")),
                rs.getBoolean("active"));
    }

    private List<PolicyView> policies(UUID tenantId) {
        return jdbc.query(
                """
                SELECT p.id,p.scope_type,p.scope_key,p.school_level_id,p.code,p.name,
                       p.evaluation_mode,p.grade_scale_id,p.competency_scale_id,
                       p.calculation_strategy,p.calculation_config,p.rankings_enabled,
                       p.report_cards_enabled,p.active,sl.code AS school_level_code,
                       sl.name AS school_level_name
                FROM grading_policy p
                LEFT JOIN school_level sl ON sl.id=p.school_level_id AND sl.tenant_id=p.tenant_id
                WHERE p.tenant_id=? ORDER BY p.scope_type,sl.sequence_number,p.name
                """,
                (rs, row) -> policyView(rs),
                tenantId);
    }

    private PolicyView policy(UUID tenantId, UUID id) {
        List<PolicyView> rows =
                jdbc.query(
                        """
                        SELECT p.id,p.scope_type,p.scope_key,p.school_level_id,p.code,p.name,
                               p.evaluation_mode,p.grade_scale_id,p.competency_scale_id,
                               p.calculation_strategy,p.calculation_config,p.rankings_enabled,
                               p.report_cards_enabled,p.active,sl.code AS school_level_code,
                               sl.name AS school_level_name
                        FROM grading_policy p
                        LEFT JOIN school_level sl ON sl.id=p.school_level_id AND sl.tenant_id=p.tenant_id
                        WHERE p.tenant_id=? AND p.id=?
                        """,
                        (rs, row) -> policyView(rs),
                        tenantId,
                        id);
        if (rows.isEmpty())
            throw new GradingNotFoundException("Politique d'évaluation introuvable");
        return rows.getFirst();
    }

    private PolicyView schoolPolicy(UUID tenantId, UUID schoolLevelId) {
        PolicyView policy =
                policyByScope(tenantId, "SCHOOL_LEVEL", schoolLevelId.toString(), false);
        if (policy == null || !policy.active()) {
            throw new GradingConflictException(
                    "Aucune politique d'évaluation active pour ce cycle");
        }
        return policy;
    }

    private PolicyView childcarePolicy(UUID tenantId) {
        PolicyView policy = policyByScope(tenantId, "CHILDCARE", "CHILDCARE", false);
        if (policy == null || !policy.active()) {
            throw new GradingConflictException("Aucune politique d'évaluation crèche active");
        }
        return policy;
    }

    private PolicyView policyByScope(
            UUID tenantId, String scopeType, String scopeKey, boolean required) {
        List<PolicyView> rows =
                jdbc.query(
                        """
                        SELECT p.id,p.scope_type,p.scope_key,p.school_level_id,p.code,p.name,
                               p.evaluation_mode,p.grade_scale_id,p.competency_scale_id,
                               p.calculation_strategy,p.calculation_config,p.rankings_enabled,
                               p.report_cards_enabled,p.active,sl.code AS school_level_code,
                               sl.name AS school_level_name
                        FROM grading_policy p
                        LEFT JOIN school_level sl ON sl.id=p.school_level_id AND sl.tenant_id=p.tenant_id
                        WHERE p.tenant_id=? AND p.scope_type=? AND p.scope_key=?
                        """,
                        (rs, row) -> policyView(rs),
                        tenantId,
                        scopeType,
                        scopeKey);
        if (rows.isEmpty()) {
            if (required) throw new GradingNotFoundException("Politique introuvable");
            return null;
        }
        return rows.getFirst();
    }

    private PolicyView policyView(ResultSet rs) throws SQLException {
        return new PolicyView(
                uuid(rs, "id"),
                rs.getString("scope_type"),
                rs.getString("scope_key"),
                uuidNullable(rs, "school_level_id"),
                rs.getString("school_level_code"),
                rs.getString("school_level_name"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("evaluation_mode"),
                uuidNullable(rs, "grade_scale_id"),
                uuidNullable(rs, "competency_scale_id"),
                rs.getString("calculation_strategy"),
                readMap(rs.getString("calculation_config")),
                rs.getBoolean("rankings_enabled"),
                rs.getBoolean("report_cards_enabled"),
                rs.getBoolean("active"));
    }

    private List<AssessmentTypeView> assessmentTypes(UUID tenantId) {
        return jdbc.query(
                """
                SELECT id,code,name_fr,name_ar,calculation_category,numeric_enabled,
                       competency_enabled,active
                FROM grading_assessment_type WHERE tenant_id=? ORDER BY name_fr
                """,
                (rs, row) -> assessmentTypeView(rs),
                tenantId);
    }

    private AssessmentTypeView assessmentType(UUID tenantId, UUID id) {
        List<AssessmentTypeView> rows =
                jdbc.query(
                        """
                        SELECT id,code,name_fr,name_ar,calculation_category,numeric_enabled,
                               competency_enabled,active
                        FROM grading_assessment_type WHERE tenant_id=? AND id=?
                        """,
                        (rs, row) -> assessmentTypeView(rs),
                        tenantId,
                        id);
        if (rows.isEmpty()) throw new GradingNotFoundException("Type d'évaluation introuvable");
        return rows.getFirst();
    }

    private static AssessmentTypeView assessmentTypeView(ResultSet rs) throws SQLException {
        return new AssessmentTypeView(
                uuid(rs, "id"),
                rs.getString("code"),
                rs.getString("name_fr"),
                rs.getString("name_ar"),
                rs.getString("calculation_category"),
                rs.getBoolean("numeric_enabled"),
                rs.getBoolean("competency_enabled"),
                rs.getBoolean("active"));
    }

    private List<CoefficientView> coefficients(UUID tenantId) {
        return jdbc.query(
                """
                SELECT c.id,c.academic_year_id,c.grade_level_id,c.subject_id,c.coefficient,
                       gl.code AS grade_level_code,gl.name AS grade_level_name,
                       s.code AS subject_code,s.name_fr AS subject_name
                FROM grading_coefficient c
                JOIN grade_level gl ON gl.id=c.grade_level_id AND gl.tenant_id=c.tenant_id
                JOIN timetable_subject s ON s.id=c.subject_id AND s.tenant_id=c.tenant_id
                WHERE c.tenant_id=? ORDER BY gl.sequence_number,s.name_fr
                """,
                (rs, row) -> coefficientView(rs),
                tenantId);
    }

    private CoefficientView coefficient(UUID tenantId, UUID id) {
        List<CoefficientView> rows =
                jdbc.query(
                        """
                        SELECT c.id,c.academic_year_id,c.grade_level_id,c.subject_id,c.coefficient,
                               gl.code AS grade_level_code,gl.name AS grade_level_name,
                               s.code AS subject_code,s.name_fr AS subject_name
                        FROM grading_coefficient c
                        JOIN grade_level gl ON gl.id=c.grade_level_id AND gl.tenant_id=c.tenant_id
                        JOIN timetable_subject s ON s.id=c.subject_id AND s.tenant_id=c.tenant_id
                        WHERE c.tenant_id=? AND c.id=?
                        """,
                        (rs, row) -> coefficientView(rs),
                        tenantId,
                        id);
        if (rows.isEmpty()) throw new GradingNotFoundException("Coefficient introuvable");
        return rows.getFirst();
    }

    private CoefficientView coefficient(
            UUID tenantId, CoefficientCommand command, boolean required) {
        List<CoefficientView> rows =
                jdbc.query(
                        """
                        SELECT c.id,c.academic_year_id,c.grade_level_id,c.subject_id,c.coefficient,
                               gl.code AS grade_level_code,gl.name AS grade_level_name,
                               s.code AS subject_code,s.name_fr AS subject_name
                        FROM grading_coefficient c
                        JOIN grade_level gl ON gl.id=c.grade_level_id AND gl.tenant_id=c.tenant_id
                        JOIN timetable_subject s ON s.id=c.subject_id AND s.tenant_id=c.tenant_id
                        WHERE c.tenant_id=? AND c.academic_year_id=? AND c.grade_level_id=?
                          AND c.subject_id=?
                        """,
                        (rs, row) -> coefficientView(rs),
                        tenantId,
                        command.academicYearId(),
                        command.gradeLevelId(),
                        command.subjectId());
        if (rows.isEmpty()) {
            if (required) throw new GradingNotFoundException("Coefficient introuvable");
            return null;
        }
        return rows.getFirst();
    }

    private static CoefficientView coefficientView(ResultSet rs) throws SQLException {
        return new CoefficientView(
                uuid(rs, "id"),
                uuid(rs, "academic_year_id"),
                uuid(rs, "grade_level_id"),
                rs.getString("grade_level_code"),
                rs.getString("grade_level_name"),
                uuid(rs, "subject_id"),
                rs.getString("subject_code"),
                rs.getString("subject_name"),
                rs.getBigDecimal("coefficient"));
    }

    private List<CompetencyView> competencies(UUID tenantId) {
        return jdbc.query(
                """
                SELECT c.id,c.school_level_id,c.subject_id,c.parent_competency_id,c.code,
                       c.name_fr,c.name_ar,c.description,c.sequence_number,c.active,
                       sl.code AS school_level_code,sl.name AS school_level_name,
                       s.code AS subject_code,s.name_fr AS subject_name
                FROM grading_competency c
                JOIN school_level sl ON sl.id=c.school_level_id AND sl.tenant_id=c.tenant_id
                LEFT JOIN timetable_subject s ON s.id=c.subject_id AND s.tenant_id=c.tenant_id
                WHERE c.tenant_id=? ORDER BY sl.sequence_number,c.sequence_number,c.name_fr
                """,
                (rs, row) -> competencyView(rs),
                tenantId);
    }

    private CompetencyView competency(UUID tenantId, UUID id) {
        List<CompetencyView> rows =
                jdbc.query(
                        """
                        SELECT c.id,c.school_level_id,c.subject_id,c.parent_competency_id,c.code,
                               c.name_fr,c.name_ar,c.description,c.sequence_number,c.active,
                               sl.code AS school_level_code,sl.name AS school_level_name,
                               s.code AS subject_code,s.name_fr AS subject_name
                        FROM grading_competency c
                        JOIN school_level sl ON sl.id=c.school_level_id AND sl.tenant_id=c.tenant_id
                        LEFT JOIN timetable_subject s ON s.id=c.subject_id AND s.tenant_id=c.tenant_id
                        WHERE c.tenant_id=? AND c.id=?
                        """,
                        (rs, row) -> competencyView(rs),
                        tenantId,
                        id);
        if (rows.isEmpty()) throw new GradingNotFoundException("Compétence introuvable");
        return rows.getFirst();
    }

    private static CompetencyView competencyView(ResultSet rs) throws SQLException {
        return new CompetencyView(
                uuid(rs, "id"),
                uuid(rs, "school_level_id"),
                rs.getString("school_level_code"),
                rs.getString("school_level_name"),
                uuidNullable(rs, "subject_id"),
                rs.getString("subject_code"),
                rs.getString("subject_name"),
                uuidNullable(rs, "parent_competency_id"),
                rs.getString("code"),
                rs.getString("name_fr"),
                rs.getString("name_ar"),
                rs.getString("description"),
                rs.getInt("sequence_number"),
                rs.getBoolean("active"));
    }

    private List<ReportCardTemplateView> templates(UUID tenantId) {
        return jdbc.query(
                """
                SELECT t.id,t.school_level_id,t.code,t.name,t.language_mode,t.layout_config,
                       t.version_number,t.active,sl.code AS school_level_code,sl.name AS school_level_name
                FROM grading_report_card_template t
                JOIN school_level sl ON sl.id=t.school_level_id AND sl.tenant_id=t.tenant_id
                WHERE t.tenant_id=? ORDER BY sl.sequence_number,t.code,t.version_number DESC
                """,
                (rs, row) -> templateView(rs),
                tenantId);
    }

    private ReportCardTemplateView template(UUID tenantId, UUID id) {
        List<ReportCardTemplateView> rows =
                jdbc.query(
                        """
                        SELECT t.id,t.school_level_id,t.code,t.name,t.language_mode,t.layout_config,
                               t.version_number,t.active,sl.code AS school_level_code,
                               sl.name AS school_level_name
                        FROM grading_report_card_template t
                        JOIN school_level sl ON sl.id=t.school_level_id AND sl.tenant_id=t.tenant_id
                        WHERE t.tenant_id=? AND t.id=?
                        """,
                        (rs, row) -> templateView(rs),
                        tenantId,
                        id);
        if (rows.isEmpty()) throw new GradingNotFoundException("Modèle de bulletin introuvable");
        return rows.getFirst();
    }

    private ReportCardTemplateView templateView(ResultSet rs) throws SQLException {
        return new ReportCardTemplateView(
                uuid(rs, "id"),
                uuid(rs, "school_level_id"),
                rs.getString("school_level_code"),
                rs.getString("school_level_name"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("language_mode"),
                readMap(rs.getString("layout_config")),
                rs.getInt("version_number"),
                rs.getBoolean("active"));
    }

    private ClassScope classScope(UUID tenantId, UUID classroomId, UUID termId) {
        if (classroomId == null || termId == null) {
            throw new GradingValidationException("La classe et la période sont requises");
        }
        List<ClassScope> rows =
                jdbc.query(
                        """
                        SELECT c.id AS classroom_id,c.name AS classroom_name,c.academic_year_id,
                               ay.name AS academic_year_name,t.id AS term_id,t.name AS term_name,
                               t.starts_on AS term_starts_on,t.ends_on AS term_ends_on,
                               gl.id AS grade_level_id,gl.name AS grade_level_name,
                               sl.id AS school_level_id,sl.code AS school_level_code,
                               sl.name AS school_level_name
                        FROM classroom c
                        JOIN academic_year ay ON ay.id=c.academic_year_id AND ay.tenant_id=c.tenant_id
                        JOIN academic_term t ON t.academic_year_id=c.academic_year_id
                            AND t.tenant_id=c.tenant_id
                        JOIN grade_level gl ON gl.id=c.grade_level_id AND gl.tenant_id=c.tenant_id
                        JOIN school_level sl ON sl.id=gl.school_level_id AND sl.tenant_id=gl.tenant_id
                        WHERE c.tenant_id=? AND c.id=? AND t.id=? AND c.active=TRUE
                        """,
                        (rs, row) ->
                                new ClassScope(
                                        uuid(rs, "classroom_id"),
                                        rs.getString("classroom_name"),
                                        uuid(rs, "academic_year_id"),
                                        rs.getString("academic_year_name"),
                                        uuid(rs, "term_id"),
                                        rs.getString("term_name"),
                                        rs.getObject("term_starts_on", LocalDate.class),
                                        rs.getObject("term_ends_on", LocalDate.class),
                                        uuid(rs, "grade_level_id"),
                                        rs.getString("grade_level_name"),
                                        uuid(rs, "school_level_id"),
                                        rs.getString("school_level_code"),
                                        rs.getString("school_level_name")),
                        tenantId,
                        classroomId,
                        termId);
        if (rows.isEmpty()) {
            throw new GradingNotFoundException("Classe ou période introuvable dans ce tenant");
        }
        return rows.getFirst();
    }

    private StudentScope studentScope(UUID tenantId, UUID studentId, UUID termId) {
        List<StudentScope> rows =
                jdbc.query(
                        """
                        SELECT se.classroom_id,c.grade_level_id,gl.school_level_id
                        FROM student_enrollment se
                        JOIN academic_term t ON t.academic_year_id=se.academic_year_id
                            AND t.tenant_id=se.tenant_id
                        JOIN classroom c ON c.id=se.classroom_id AND c.tenant_id=se.tenant_id
                        JOIN grade_level gl ON gl.id=c.grade_level_id AND gl.tenant_id=c.tenant_id
                        WHERE se.tenant_id=? AND se.student_id=? AND t.id=?
                          AND se.status='ACTIVE' AND se.classroom_id IS NOT NULL
                        """,
                        (rs, row) ->
                                new StudentScope(
                                        uuid(rs, "classroom_id"),
                                        uuid(rs, "grade_level_id"),
                                        uuid(rs, "school_level_id")),
                        tenantId,
                        studentId,
                        termId);
        if (rows.isEmpty())
            throw new GradingNotFoundException("Élève non inscrit pour cette période");
        return rows.getFirst();
    }

    private List<StudentView> students(UUID tenantId, UUID classroomId) {
        return jdbc.query(
                """
                SELECT sp.id,p.first_name,p.last_name,si.identifier_value,
                       CASE WHEN cp.id IS NULL THEN FALSE ELSE TRUE END AS childcare
                FROM student_enrollment se
                JOIN student_profile sp ON sp.id=se.student_id AND sp.tenant_id=se.tenant_id
                JOIN person p ON p.id=sp.person_id AND p.tenant_id=sp.tenant_id
                LEFT JOIN student_identifier si ON si.student_id=sp.id AND si.tenant_id=sp.tenant_id
                    AND si.primary_identifier=TRUE
                LEFT JOIN childcare_profile cp ON cp.student_id=sp.id AND cp.tenant_id=sp.tenant_id
                WHERE se.tenant_id=? AND se.classroom_id=? AND se.status='ACTIVE'
                  AND sp.status='ACTIVE'
                ORDER BY p.last_name,p.first_name
                """,
                (rs, row) ->
                        new StudentView(
                                uuid(rs, "id"),
                                rs.getString("first_name"),
                                rs.getString("last_name"),
                                rs.getString("identifier_value"),
                                rs.getBoolean("childcare")),
                tenantId,
                classroomId);
    }

    private List<AssessmentView> assessments(UUID tenantId, UUID classroomId, UUID termId) {
        return jdbc.query(
                assessmentSelect()
                        + " WHERE a.tenant_id=? AND a.classroom_id=? AND a.academic_term_id=?"
                        + " ORDER BY a.assessment_date DESC,a.created_at DESC",
                (rs, row) -> assessmentView(rs),
                tenantId,
                classroomId,
                termId);
    }

    private AssessmentView assessment(UUID tenantId, UUID id) {
        List<AssessmentView> rows =
                jdbc.query(
                        assessmentSelect() + " WHERE a.tenant_id=? AND a.id=?",
                        (rs, row) -> assessmentView(rs),
                        tenantId,
                        id);
        if (rows.isEmpty()) throw new GradingNotFoundException("Évaluation introuvable");
        return rows.getFirst();
    }

    private static String assessmentSelect() {
        return """
        SELECT a.id,a.academic_year_id,a.academic_term_id,a.classroom_id,a.subject_id,
               s.code AS subject_code,s.name_fr AS subject_name,s.color,
               a.assessment_type_id,at.code AS type_code,at.name_fr AS type_name,
               at.calculation_category,at.numeric_enabled,at.competency_enabled,
               a.teacher_staff_id,CONCAT(p.first_name,' ',p.last_name) AS teacher_name,
               a.title,a.description,a.assessment_date,a.maximum_score,a.coefficient,
               a.status,a.created_at,a.updated_at,
               (SELECT COUNT(*) FROM grading_grade g
                WHERE g.tenant_id=a.tenant_id AND g.assessment_id=a.id) AS grade_count
        FROM grading_assessment a
        JOIN timetable_subject s ON s.id=a.subject_id AND s.tenant_id=a.tenant_id
        JOIN grading_assessment_type at
            ON at.id=a.assessment_type_id AND at.tenant_id=a.tenant_id
        JOIN staff_profile sp ON sp.id=a.teacher_staff_id AND sp.tenant_id=a.tenant_id
        JOIN person p ON p.id=sp.person_id AND p.tenant_id=sp.tenant_id
        """;
    }

    private static AssessmentView assessmentView(ResultSet rs) throws SQLException {
        return new AssessmentView(
                uuid(rs, "id"),
                uuid(rs, "academic_year_id"),
                uuid(rs, "academic_term_id"),
                uuid(rs, "classroom_id"),
                uuid(rs, "subject_id"),
                rs.getString("subject_code"),
                rs.getString("subject_name"),
                rs.getString("color"),
                uuid(rs, "assessment_type_id"),
                rs.getString("type_code"),
                rs.getString("type_name"),
                rs.getString("calculation_category"),
                rs.getBoolean("numeric_enabled"),
                rs.getBoolean("competency_enabled"),
                uuid(rs, "teacher_staff_id"),
                rs.getString("teacher_name"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getObject("assessment_date", LocalDate.class),
                decimal(rs, "maximum_score"),
                rs.getBigDecimal("coefficient"),
                rs.getString("status"),
                rs.getInt("grade_count"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private AssessmentInternal assessmentInternal(UUID tenantId, UUID id) {
        List<AssessmentInternal> rows =
                jdbc.query(
                        """
                        SELECT a.id,a.classroom_id,a.academic_term_id,a.maximum_score,a.status,
                               at.numeric_enabled,gl.school_level_id
                        FROM grading_assessment a
                        JOIN grading_assessment_type at
                          ON at.id=a.assessment_type_id AND at.tenant_id=a.tenant_id
                        JOIN classroom c ON c.id=a.classroom_id AND c.tenant_id=a.tenant_id
                        JOIN grade_level gl ON gl.id=c.grade_level_id AND gl.tenant_id=c.tenant_id
                        WHERE a.tenant_id=? AND a.id=?
                        """,
                        (rs, row) ->
                                new AssessmentInternal(
                                        uuid(rs, "id"),
                                        uuid(rs, "classroom_id"),
                                        uuid(rs, "academic_term_id"),
                                        uuid(rs, "school_level_id"),
                                        decimal(rs, "maximum_score"),
                                        rs.getString("status"),
                                        rs.getBoolean("numeric_enabled")),
                        tenantId,
                        id);
        if (rows.isEmpty()) throw new GradingNotFoundException("Évaluation introuvable");
        return rows.getFirst();
    }

    private List<GradeView> grades(UUID tenantId, UUID classroomId, UUID termId) {
        return jdbc.query(
                gradeSelect()
                        + " WHERE g.tenant_id=? AND a.classroom_id=? AND a.academic_term_id=?"
                        + " ORDER BY p.last_name,p.first_name,a.assessment_date,a.id",
                (rs, row) -> gradeView(rs),
                tenantId,
                classroomId,
                termId);
    }

    private GradeView grade(UUID tenantId, UUID assessmentId, UUID studentId, boolean required) {
        List<GradeView> rows =
                jdbc.query(
                        gradeSelect()
                                + " WHERE g.tenant_id=? AND g.assessment_id=? AND g.student_id=?",
                        (rs, row) -> gradeView(rs),
                        tenantId,
                        assessmentId,
                        studentId);
        if (rows.isEmpty()) {
            if (required) throw new GradingNotFoundException("Note introuvable");
            return null;
        }
        return rows.getFirst();
    }

    private static String gradeSelect() {
        return """
        SELECT g.id,g.assessment_id,g.student_id,g.raw_score,g.grade_status,
               g.teacher_comment,g.version,g.updated_at,
               a.subject_id,s.code AS subject_code,s.name_fr AS subject_name,s.color,
               a.title AS assessment_title,a.maximum_score,a.coefficient,
               at.calculation_category,p.first_name,p.last_name
        FROM grading_grade g
        JOIN grading_assessment a ON a.id=g.assessment_id AND a.tenant_id=g.tenant_id
        JOIN grading_assessment_type at
            ON at.id=a.assessment_type_id AND at.tenant_id=a.tenant_id
        JOIN timetable_subject s ON s.id=a.subject_id AND s.tenant_id=a.tenant_id
        JOIN student_profile sp ON sp.id=g.student_id AND sp.tenant_id=g.tenant_id
        JOIN person p ON p.id=sp.person_id AND p.tenant_id=sp.tenant_id
        """;
    }

    private static GradeView gradeView(ResultSet rs) throws SQLException {
        return new GradeView(
                uuid(rs, "id"),
                uuid(rs, "assessment_id"),
                rs.getString("assessment_title"),
                uuid(rs, "student_id"),
                rs.getString("first_name") + " " + rs.getString("last_name"),
                uuid(rs, "subject_id"),
                rs.getString("subject_code"),
                rs.getString("subject_name"),
                rs.getString("color"),
                decimal(rs, "raw_score"),
                decimal(rs, "maximum_score"),
                rs.getString("grade_status"),
                rs.getString("teacher_comment"),
                rs.getLong("version"),
                rs.getBigDecimal("coefficient"),
                rs.getString("calculation_category"),
                rs.getTimestamp("updated_at").toInstant());
    }

    private List<CompetencyEvaluationView> competencyEvaluations(
            UUID tenantId, UUID classroomId, UUID termId) {
        return jdbc.query(
                competencyEvaluationSelect()
                        + " JOIN student_enrollment se ON se.student_id=ce.student_id"
                        + " AND se.tenant_id=ce.tenant_id"
                        + " WHERE ce.tenant_id=? AND se.classroom_id=?"
                        + " AND ce.academic_term_id=? AND se.status='ACTIVE'"
                        + " ORDER BY ce.evaluated_at DESC",
                (rs, row) -> competencyEvaluationView(rs),
                tenantId,
                classroomId,
                termId);
    }

    private CompetencyEvaluationView competencyEvaluation(UUID tenantId, UUID id) {
        List<CompetencyEvaluationView> rows =
                jdbc.query(
                        competencyEvaluationSelect() + " WHERE ce.tenant_id=? AND ce.id=?",
                        (rs, row) -> competencyEvaluationView(rs),
                        tenantId,
                        id);
        if (rows.isEmpty())
            throw new GradingNotFoundException("Évaluation de compétence introuvable");
        return rows.getFirst();
    }

    private static String competencyEvaluationSelect() {
        return """
        SELECT ce.id,ce.competency_id,ce.student_id,ce.academic_term_id,ce.assessment_id,
               ce.teacher_staff_id,ce.level_code,ce.observation,ce.evaluated_at,
               c.code AS competency_code,c.name_fr AS competency_name,
               p.first_name,p.last_name,
               CONCAT(tp.first_name,' ',tp.last_name) AS teacher_name
        FROM grading_competency_evaluation ce
        JOIN grading_competency c ON c.id=ce.competency_id AND c.tenant_id=ce.tenant_id
        JOIN student_profile sp ON sp.id=ce.student_id AND sp.tenant_id=ce.tenant_id
        JOIN person p ON p.id=sp.person_id AND p.tenant_id=sp.tenant_id
        JOIN staff_profile ts ON ts.id=ce.teacher_staff_id AND ts.tenant_id=ce.tenant_id
        JOIN person tp ON tp.id=ts.person_id AND tp.tenant_id=ts.tenant_id
        """;
    }

    private static CompetencyEvaluationView competencyEvaluationView(ResultSet rs)
            throws SQLException {
        return new CompetencyEvaluationView(
                uuid(rs, "id"),
                uuid(rs, "competency_id"),
                rs.getString("competency_code"),
                rs.getString("competency_name"),
                uuid(rs, "student_id"),
                rs.getString("first_name") + " " + rs.getString("last_name"),
                uuid(rs, "academic_term_id"),
                uuidNullable(rs, "assessment_id"),
                uuid(rs, "teacher_staff_id"),
                rs.getString("teacher_name"),
                rs.getString("level_code"),
                rs.getString("observation"),
                rs.getTimestamp("evaluated_at").toInstant());
    }

    private List<TeacherCommentView> teacherComments(UUID tenantId, UUID classroomId, UUID termId) {
        return jdbc.query(
                teacherCommentSelect()
                        + " JOIN student_enrollment se ON se.student_id=tc.student_id"
                        + " AND se.tenant_id=tc.tenant_id"
                        + " WHERE tc.tenant_id=? AND se.classroom_id=?"
                        + " AND tc.academic_term_id=? AND se.status='ACTIVE'"
                        + " ORDER BY tc.created_at DESC",
                (rs, row) -> teacherCommentView(rs),
                tenantId,
                classroomId,
                termId);
    }

    private TeacherCommentView teacherComment(UUID tenantId, UUID id) {
        List<TeacherCommentView> rows =
                jdbc.query(
                        teacherCommentSelect() + " WHERE tc.tenant_id=? AND tc.id=?",
                        (rs, row) -> teacherCommentView(rs),
                        tenantId,
                        id);
        if (rows.isEmpty()) throw new GradingNotFoundException("Commentaire introuvable");
        return rows.getFirst();
    }

    private static String teacherCommentSelect() {
        return """
        SELECT tc.id,tc.student_id,tc.academic_term_id,tc.subject_id,tc.teacher_staff_id,
               tc.comment_type,tc.body,tc.visible_to_family,tc.created_at,
               p.first_name,p.last_name,
               CONCAT(tp.first_name,' ',tp.last_name) AS teacher_name,
               s.code AS subject_code,s.name_fr AS subject_name
        FROM grading_teacher_comment tc
        JOIN student_profile sp ON sp.id=tc.student_id AND sp.tenant_id=tc.tenant_id
        JOIN person p ON p.id=sp.person_id AND p.tenant_id=sp.tenant_id
        JOIN staff_profile ts ON ts.id=tc.teacher_staff_id AND ts.tenant_id=tc.tenant_id
        JOIN person tp ON tp.id=ts.person_id AND tp.tenant_id=ts.tenant_id
        LEFT JOIN timetable_subject s ON s.id=tc.subject_id AND s.tenant_id=tc.tenant_id
        """;
    }

    private static TeacherCommentView teacherCommentView(ResultSet rs) throws SQLException {
        return new TeacherCommentView(
                uuid(rs, "id"),
                uuid(rs, "student_id"),
                rs.getString("first_name") + " " + rs.getString("last_name"),
                uuid(rs, "academic_term_id"),
                uuidNullable(rs, "subject_id"),
                rs.getString("subject_code"),
                rs.getString("subject_name"),
                uuid(rs, "teacher_staff_id"),
                rs.getString("teacher_name"),
                rs.getString("comment_type"),
                rs.getString("body"),
                rs.getBoolean("visible_to_family"),
                rs.getTimestamp("created_at").toInstant());
    }

    private List<ReportCardView> reportCards(UUID tenantId, UUID classroomId, UUID termId) {
        return jdbc.query(
                """
                SELECT rc.id,rc.student_id,CONCAT(p.first_name,' ',p.last_name) AS student_name,
                       rc.academic_year_id,rc.academic_term_id,rc.classroom_id,rc.policy_id,
                       rc.template_id,rc.status,rc.overall_average,rc.class_rank,rc.class_size,
                       rc.version_number,rc.calculated_at,t.name AS template_name,
                       rc.supersedes_report_card_id,rc.language_mode,rc.file_name,rc.pdf_sha256,
                       rc.pdf_size,rc.signed_by_name,rc.signed_by_title,rc.published_at,
                       (SELECT COUNT(*) FROM grading_report_card_subject_result r
                        WHERE r.tenant_id=rc.tenant_id AND r.report_card_id=rc.id) AS subject_count
                FROM grading_report_card rc
                JOIN student_profile sp ON sp.id=rc.student_id AND sp.tenant_id=rc.tenant_id
                JOIN person p ON p.id=sp.person_id AND p.tenant_id=sp.tenant_id
                LEFT JOIN grading_report_card_template t
                    ON t.id=rc.template_id AND t.tenant_id=rc.tenant_id
                WHERE rc.tenant_id=? AND rc.classroom_id=? AND rc.academic_term_id=?
                ORDER BY p.last_name,p.first_name,rc.version_number DESC
                """,
                (rs, row) ->
                        new ReportCardView(
                                uuid(rs, "id"),
                                uuid(rs, "student_id"),
                                rs.getString("student_name"),
                                uuid(rs, "academic_year_id"),
                                uuid(rs, "academic_term_id"),
                                uuid(rs, "classroom_id"),
                                uuid(rs, "policy_id"),
                                uuidNullable(rs, "template_id"),
                                rs.getString("template_name"),
                                rs.getString("status"),
                                decimal(rs, "overall_average"),
                                integerNullable(rs, "class_rank"),
                                integerNullable(rs, "class_size"),
                                rs.getInt("version_number"),
                                rs.getInt("subject_count"),
                                rs.getTimestamp("calculated_at").toInstant(),
                                uuidNullable(rs, "supersedes_report_card_id"),
                                rs.getString("language_mode"),
                                rs.getString("file_name"),
                                rs.getString("pdf_sha256"),
                                rs.getObject("pdf_size") == null ? null : rs.getLong("pdf_size"),
                                rs.getString("signed_by_name"),
                                rs.getString("signed_by_title"),
                                instantNullable(rs, "published_at")),
                tenantId,
                classroomId,
                termId);
    }

    private List<StudentSummary> summaries(
            UUID tenantId,
            ClassScope scope,
            PolicyView policy,
            List<StudentView> students,
            List<GradeView> grades) {
        if (!Set.of("NUMERIC", "HYBRID").contains(policy.evaluationMode())) {
            return students.stream()
                    .map(
                            student ->
                                    new StudentSummary(
                                            student.id(),
                                            student.displayName(),
                                            List.of(),
                                            null,
                                            null,
                                            null))
                    .toList();
        }
        GradeScaleView scale = scale(tenantId, policy.gradeScaleId());
        CalculationModel model =
                calculationModel(policy.calculationStrategy(), policy.calculationConfig());
        Map<UUID, BigDecimal> subjectCoefficients =
                jdbc
                        .query(
                                """
                                SELECT subject_id,coefficient FROM grading_coefficient
                                WHERE tenant_id=? AND academic_year_id=? AND grade_level_id=?
                                """,
                                (rs, row) ->
                                        Map.entry(
                                                uuid(rs, "subject_id"),
                                                rs.getBigDecimal("coefficient")),
                                tenantId,
                                scope.academicYearId(),
                                scope.gradeLevelId())
                        .stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Map<UUID, Map<UUID, List<GradeView>>> grouped = new LinkedHashMap<>();
        for (GradeView grade : grades) {
            if (!grade.status().equals("GRADED")
                    || grade.rawScore() == null
                    || grade.maximumScore() == null) {
                continue;
            }
            grouped.computeIfAbsent(grade.studentId(), ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(grade.subjectId(), ignored -> new ArrayList<>())
                    .add(grade);
        }
        List<StudentSummary> preliminary = new ArrayList<>();
        for (StudentView student : students) {
            List<SubjectSummary> subjects = new ArrayList<>();
            Map<UUID, List<GradeView>> studentGrades = grouped.getOrDefault(student.id(), Map.of());
            for (Map.Entry<UUID, List<GradeView>> entry : studentGrades.entrySet()) {
                List<GradeView> values = entry.getValue();
                GradeView first = values.getFirst();
                List<ScoredItem> items =
                        values.stream()
                                .map(
                                        grade ->
                                                new ScoredItem(
                                                        grade.calculationCategory(),
                                                        normalizeScore(
                                                                grade.rawScore(),
                                                                grade.maximumScore(),
                                                                scale.maximumScore(),
                                                                scale.roundingScale() + 4),
                                                        grade.assessmentCoefficient()))
                                .toList();
                CalculationResult result = engine.calculate(items, model);
                subjects.add(
                        new SubjectSummary(
                                first.subjectId(),
                                first.subjectCode(),
                                first.subjectName(),
                                first.color(),
                                result.average(),
                                subjectCoefficients.getOrDefault(first.subjectId(), BigDecimal.ONE),
                                result.categoryAverages(),
                                result.includedItems()));
            }
            subjects.sort(Comparator.comparing(SubjectSummary::subjectName));
            boolean useCoefficients =
                    booleanConfig(policy.calculationConfig(), "overallUseCoefficients", true);
            List<ScoredItem> overallItems =
                    subjects.stream()
                            .filter(value -> value.average() != null)
                            .map(
                                    value ->
                                            new ScoredItem(
                                                    "SUBJECT",
                                                    value.average(),
                                                    useCoefficients
                                                            ? value.coefficient()
                                                            : BigDecimal.ONE))
                            .toList();
            BigDecimal overall =
                    overallItems.isEmpty()
                            ? null
                            : engine.calculate(
                                            overallItems,
                                            new CalculationModel(
                                                    Strategy.WEIGHTED_MEAN,
                                                    Map.of(),
                                                    MissingCategoryMode.RENORMALIZE,
                                                    0,
                                                    scale.roundingScale()))
                                    .average();
            preliminary.add(
                    new StudentSummary(
                            student.id(),
                            student.displayName(),
                            List.copyOf(subjects),
                            overall,
                            null,
                            null));
        }
        if (!policy.rankingsEnabled()) return List.copyOf(preliminary);
        List<StudentSummary> ranked =
                preliminary.stream()
                        .filter(value -> value.overallAverage() != null)
                        .sorted(Comparator.comparing(StudentSummary::overallAverage).reversed())
                        .toList();
        Map<UUID, Integer> ranks = new HashMap<>();
        String rankingMethod =
                stringConfig(policy.calculationConfig(), "rankingMethod", "COMPETITION");
        int rank = 0;
        int denseRank = 0;
        BigDecimal previous = null;
        for (int index = 0; index < ranked.size(); index++) {
            StudentSummary current = ranked.get(index);
            if (previous == null || current.overallAverage().compareTo(previous) != 0) {
                denseRank++;
                rank = rankingMethod.equals("DENSE") ? denseRank : index + 1;
                previous = current.overallAverage();
            }
            ranks.put(current.studentId(), rank);
        }
        return preliminary.stream()
                .map(
                        value ->
                                new StudentSummary(
                                        value.studentId(),
                                        value.studentName(),
                                        value.subjects(),
                                        value.overallAverage(),
                                        ranks.get(value.studentId()),
                                        ranked.size()))
                .toList();
    }

    private void validateScale(GradeScaleCommand command) {
        if (command == null
                || blank(command.code())
                || blank(command.name())
                || !SCALE_TYPES.contains(normalize(command.scaleType()))
                || command.roundingScale() < 0
                || command.roundingScale() > 4) {
            throw new GradingValidationException("Échelle de notation incomplète ou invalide");
        }
        String type = normalize(command.scaleType());
        if (type.equals("NUMERIC")) {
            if (command.minimumScore() == null
                    || command.maximumScore() == null
                    || command.minimumScore().compareTo(command.maximumScore()) >= 0
                    || (command.passingScore() != null
                            && (command.passingScore().compareTo(command.minimumScore()) < 0
                                    || command.passingScore().compareTo(command.maximumScore())
                                            > 0))) {
                throw new GradingValidationException("Bornes de l'échelle numérique invalides");
            }
        } else {
            List<CompetencyLevelCommand> levels = command.competencyLevels();
            if (levels == null || levels.size() < 2) {
                throw new GradingValidationException(
                        "Une échelle de compétences exige au moins deux niveaux");
            }
            Set<String> codes = new LinkedHashSet<>();
            for (CompetencyLevelCommand level : levels) {
                if (level == null
                        || blank(level.code())
                        || blank(level.labelFr())
                        || level.sequenceNumber() <= 0
                        || !codes.add(normalize(level.code()))) {
                    throw new GradingValidationException(
                            "Niveaux de compétence invalides ou dupliqués");
                }
            }
        }
    }

    private void validatePolicyScales(
            UUID tenantId, String mode, UUID gradeScaleId, UUID competencyScaleId) {
        if (Set.of("NUMERIC", "HYBRID").contains(mode)) {
            if (gradeScaleId == null
                    || !scale(tenantId, gradeScaleId).scaleType().equals("NUMERIC")) {
                throw new GradingValidationException("Une échelle numérique est requise");
            }
        }
        if (Set.of("COMPETENCY_ONLY", "HYBRID").contains(mode)) {
            if (competencyScaleId == null
                    || !scale(tenantId, competencyScaleId).scaleType().equals("COMPETENCY")) {
                throw new GradingValidationException("Une échelle de compétences est requise");
            }
        }
    }

    private void validateCalculationModel(String strategy, String config) {
        try {
            engine.calculate(
                    List.of(new ScoredItem("VALIDATION", BigDecimal.TEN, BigDecimal.ONE)),
                    calculationModel(strategy, readMap(config)));
        } catch (IllegalArgumentException exception) {
            throw new GradingValidationException(exception.getMessage());
        }
    }

    private CalculationModel calculationModel(String strategy, Map<String, Object> config) {
        Strategy selected;
        try {
            selected = Strategy.valueOf(normalize(strategy));
        } catch (IllegalArgumentException exception) {
            throw new GradingValidationException("Stratégie de calcul inconnue");
        }
        Map<String, BigDecimal> categoryWeights = new LinkedHashMap<>();
        Object configuredWeights = config.get("categoryWeights");
        if (configuredWeights instanceof Map<?, ?> weights) {
            weights.forEach(
                    (key, value) -> {
                        if (key != null && value != null) {
                            try {
                                categoryWeights.put(
                                        normalize(key.toString()),
                                        new BigDecimal(value.toString()));
                            } catch (NumberFormatException exception) {
                                throw new GradingValidationException("Poids de catégorie invalide");
                            }
                        }
                    });
        }
        MissingCategoryMode missing;
        try {
            missing =
                    MissingCategoryMode.valueOf(
                            stringConfig(config, "missingCategoryMode", "RENORMALIZE"));
        } catch (IllegalArgumentException exception) {
            throw new GradingValidationException("Gestion des catégories manquantes invalide");
        }
        return new CalculationModel(
                selected,
                categoryWeights,
                missing,
                integerConfig(config, "dropLowest", 0),
                integerConfig(config, "roundingScale", 2));
    }

    private Set<String> competencyLevelCodes(UUID tenantId, UUID scaleId) {
        if (scaleId == null) return Set.of();
        return scale(tenantId, scaleId).competencyLevels().stream()
                .map(CompetencyLevelView::code)
                .map(GradingService::normalize)
                .collect(Collectors.toUnmodifiableSet());
    }

    private String latestSubjectComment(
            UUID tenantId, UUID studentId, UUID termId, UUID subjectId) {
        List<String> rows =
                jdbc.query(
                        """
                        SELECT body FROM grading_teacher_comment
                        WHERE tenant_id=? AND student_id=? AND academic_term_id=? AND subject_id=?
                        ORDER BY created_at DESC
                        """,
                        (rs, row) -> rs.getString("body"),
                        tenantId,
                        studentId,
                        termId,
                        subjectId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private LatestReport latestReport(UUID tenantId, UUID studentId, UUID termId) {
        List<LatestReport> rows =
                jdbc.query(
                        """
                        SELECT id,status,version_number FROM grading_report_card
                        WHERE tenant_id=? AND student_id=? AND academic_term_id=?
                        ORDER BY version_number DESC
                        """,
                        (rs, row) ->
                                new LatestReport(
                                        uuid(rs, "id"),
                                        rs.getString("status"),
                                        rs.getInt("version_number")),
                        tenantId,
                        studentId,
                        termId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private boolean isChildcareStudent(UUID tenantId, UUID studentId) {
        Integer count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM childcare_profile WHERE tenant_id=? AND student_id=?",
                        Integer.class,
                        tenantId,
                        studentId);
        return count != null && count > 0;
    }

    private boolean studentInClass(UUID tenantId, UUID studentId, UUID classroomId) {
        Integer count =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM student_enrollment
                        WHERE tenant_id=? AND student_id=? AND classroom_id=? AND status='ACTIVE'
                        """,
                        Integer.class,
                        tenantId,
                        studentId,
                        classroomId);
        return count != null && count > 0;
    }

    private void requireExists(String table, UUID tenantId, UUID id, String message) {
        Set<String> allowed =
                Set.of(
                        "academic_year",
                        "school_level",
                        "grade_level",
                        "timetable_subject",
                        "staff_profile",
                        "grading_competency",
                        "grading_grade");
        if (!allowed.contains(table)) throw new IllegalArgumentException("Table non autorisée");
        Integer count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM " + table + " WHERE tenant_id=? AND id=?",
                        Integer.class,
                        tenantId,
                        id);
        if (count == null || count == 0) throw new GradingNotFoundException(message);
    }

    private static BigDecimal normalizeScore(
            BigDecimal score, BigDecimal maximum, BigDecimal targetMaximum, int scale) {
        if (maximum == null || maximum.signum() <= 0 || targetMaximum == null) {
            throw new GradingValidationException("Barème numérique invalide");
        }
        return score.multiply(targetMaximum).divide(maximum, scale, RoundingMode.HALF_UP);
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new GradingValidationException("Configuration JSON invalide");
        }
    }

    private Map<String, Object> readMap(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            return json.readValue(value, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new GradingValidationException("Configuration JSON invalide");
        }
    }

    private List<CompetencyLevelView> readLevels(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            JsonNode root = json.readTree(value);
            if (!root.isArray())
                throw new GradingValidationException("Échelle de compétences invalide");
            List<CompetencyLevelView> levels = new ArrayList<>();
            for (JsonNode node : root) {
                levels.add(
                        new CompetencyLevelView(
                                normalize(node.path("code").asText()),
                                node.path("labelFr").asText(),
                                node.path("labelAr").asText(null),
                                node.path("sequenceNumber").asInt(),
                                node.path("color").asText(null)));
            }
            return List.copyOf(levels);
        } catch (JsonProcessingException exception) {
            throw new GradingValidationException("Échelle de compétences invalide");
        }
    }

    private static boolean booleanConfig(Map<String, Object> config, String key, boolean fallback) {
        Object value = config.get(key);
        return value == null ? fallback : Boolean.parseBoolean(value.toString());
    }

    private static int integerConfig(Map<String, Object> config, String key, int fallback) {
        Object value = config.get(key);
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException exception) {
            throw new GradingValidationException("Valeur entière invalide pour " + key);
        }
    }

    private static String stringConfig(Map<String, Object> config, String key, String fallback) {
        Object value = config.get(key);
        return normalize(value == null ? fallback : value.toString());
    }

    private static UUID uuid(ResultSet result, String column) throws SQLException {
        return result.getObject(column, UUID.class);
    }

    private static UUID uuidNullable(ResultSet result, String column) throws SQLException {
        return result.getObject(column, UUID.class);
    }

    private static BigDecimal decimal(ResultSet result, String column) throws SQLException {
        return result.getBigDecimal(column);
    }

    private static Integer integerNullable(ResultSet result, String column) throws SQLException {
        int value = result.getInt(column);
        return result.wasNull() ? null : value;
    }

    private static Instant instantNullable(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
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

    public record YearOption(
            UUID id,
            String code,
            String name,
            String status,
            LocalDate startsOn,
            LocalDate endsOn) {}

    public record TermOption(
            UUID id,
            UUID academicYearId,
            String code,
            String name,
            int sequenceNumber,
            LocalDate startsOn,
            LocalDate endsOn) {}

    public record SchoolLevelOption(UUID id, String code, String name, int sequenceNumber) {}

    public record GradeLevelOption(
            UUID id, UUID schoolLevelId, String code, String name, int sequenceNumber) {}

    public record ClassroomOption(
            UUID id,
            UUID academicYearId,
            UUID gradeLevelId,
            UUID schoolLevelId,
            String schoolLevelCode,
            String schoolLevelName,
            String code,
            String name) {}

    public record TeacherOption(UUID id, String employeeNumber, String name) {}

    public record SubjectView(UUID id, String code, String nameFr, String nameAr, String color) {}

    public record CompetencyLevelCommand(
            String code, String labelFr, String labelAr, int sequenceNumber, String color) {}

    public record CompetencyLevelView(
            String code, String labelFr, String labelAr, int sequenceNumber, String color) {}

    public record GradeScaleCommand(
            String code,
            String name,
            String scaleType,
            BigDecimal minimumScore,
            BigDecimal maximumScore,
            BigDecimal passingScore,
            int roundingScale,
            List<CompetencyLevelCommand> competencyLevels) {}

    public record GradeScaleView(
            UUID id,
            String code,
            String name,
            String scaleType,
            BigDecimal minimumScore,
            BigDecimal maximumScore,
            BigDecimal passingScore,
            int roundingScale,
            List<CompetencyLevelView> competencyLevels,
            boolean active) {}

    public record PolicyCommand(
            String scopeType,
            UUID schoolLevelId,
            String code,
            String name,
            String evaluationMode,
            UUID gradeScaleId,
            UUID competencyScaleId,
            String calculationStrategy,
            Map<String, Object> calculationConfig,
            boolean rankingsEnabled,
            boolean reportCardsEnabled) {}

    public record PolicyView(
            UUID id,
            String scopeType,
            String scopeKey,
            UUID schoolLevelId,
            String schoolLevelCode,
            String schoolLevelName,
            String code,
            String name,
            String evaluationMode,
            UUID gradeScaleId,
            UUID competencyScaleId,
            String calculationStrategy,
            Map<String, Object> calculationConfig,
            boolean rankingsEnabled,
            boolean reportCardsEnabled,
            boolean active) {}

    public record AssessmentTypeCommand(
            String code,
            String nameFr,
            String nameAr,
            String calculationCategory,
            boolean numericEnabled,
            boolean competencyEnabled) {}

    public record AssessmentTypeView(
            UUID id,
            String code,
            String nameFr,
            String nameAr,
            String calculationCategory,
            boolean numericEnabled,
            boolean competencyEnabled,
            boolean active) {}

    public record CoefficientCommand(
            UUID academicYearId, UUID gradeLevelId, UUID subjectId, BigDecimal coefficient) {}

    public record CoefficientView(
            UUID id,
            UUID academicYearId,
            UUID gradeLevelId,
            String gradeLevelCode,
            String gradeLevelName,
            UUID subjectId,
            String subjectCode,
            String subjectName,
            BigDecimal coefficient) {}

    public record CompetencyCommand(
            UUID schoolLevelId,
            UUID subjectId,
            UUID parentCompetencyId,
            String code,
            String nameFr,
            String nameAr,
            String description,
            int sequenceNumber) {}

    public record CompetencyView(
            UUID id,
            UUID schoolLevelId,
            String schoolLevelCode,
            String schoolLevelName,
            UUID subjectId,
            String subjectCode,
            String subjectName,
            UUID parentCompetencyId,
            String code,
            String nameFr,
            String nameAr,
            String description,
            int sequenceNumber,
            boolean active) {}

    public record ReportCardTemplateCommand(
            UUID schoolLevelId,
            String code,
            String name,
            String languageMode,
            Map<String, Object> layoutConfig,
            int versionNumber) {}

    public record ReportCardTemplateView(
            UUID id,
            UUID schoolLevelId,
            String schoolLevelCode,
            String schoolLevelName,
            String code,
            String name,
            String languageMode,
            Map<String, Object> layoutConfig,
            int versionNumber,
            boolean active) {}

    public record ReferenceData(
            List<YearOption> years,
            List<TermOption> terms,
            List<SchoolLevelOption> schoolLevels,
            List<GradeLevelOption> gradeLevels,
            List<ClassroomOption> classrooms,
            List<TeacherOption> teachers,
            List<SubjectView> subjects,
            List<GradeScaleView> scales,
            List<PolicyView> policies,
            List<AssessmentTypeView> assessmentTypes,
            List<CoefficientView> coefficients,
            List<CompetencyView> competencies,
            List<ReportCardTemplateView> templates) {}

    public record AssessmentCommand(
            UUID classroomId,
            UUID academicTermId,
            UUID subjectId,
            UUID assessmentTypeId,
            UUID teacherStaffId,
            String title,
            String description,
            LocalDate assessmentDate,
            BigDecimal maximumScore,
            BigDecimal coefficient) {}

    public record AssessmentView(
            UUID id,
            UUID academicYearId,
            UUID academicTermId,
            UUID classroomId,
            UUID subjectId,
            String subjectCode,
            String subjectName,
            String color,
            UUID assessmentTypeId,
            String assessmentTypeCode,
            String assessmentTypeName,
            String calculationCategory,
            boolean numericEnabled,
            boolean competencyEnabled,
            UUID teacherStaffId,
            String teacherName,
            String title,
            String description,
            LocalDate assessmentDate,
            BigDecimal maximumScore,
            BigDecimal coefficient,
            String status,
            int gradeCount,
            Instant createdAt,
            Instant updatedAt) {}

    public record GradeCommand(
            BigDecimal rawScore, String status, String teacherComment, String reason) {}

    public record GradeView(
            UUID id,
            UUID assessmentId,
            String assessmentTitle,
            UUID studentId,
            String studentName,
            UUID subjectId,
            String subjectCode,
            String subjectName,
            String color,
            BigDecimal rawScore,
            BigDecimal maximumScore,
            String status,
            String teacherComment,
            long version,
            BigDecimal assessmentCoefficient,
            String calculationCategory,
            Instant updatedAt) {}

    public record GradeHistoryView(
            UUID id,
            String changeType,
            BigDecimal previousScore,
            BigDecimal newScore,
            String previousStatus,
            String newStatus,
            String reason,
            Instant changedAt,
            UUID actorUserId,
            String actorName) {}

    public record CompetencyEvaluationCommand(
            UUID competencyId,
            UUID studentId,
            UUID academicTermId,
            UUID assessmentId,
            UUID teacherStaffId,
            String levelCode,
            String observation) {}

    public record CompetencyEvaluationView(
            UUID id,
            UUID competencyId,
            String competencyCode,
            String competencyName,
            UUID studentId,
            String studentName,
            UUID academicTermId,
            UUID assessmentId,
            UUID teacherStaffId,
            String teacherName,
            String levelCode,
            String observation,
            Instant evaluatedAt) {}

    public record TeacherCommentCommand(
            UUID studentId,
            UUID academicTermId,
            UUID subjectId,
            UUID teacherStaffId,
            String commentType,
            String body,
            boolean visibleToFamily) {}

    public record TeacherCommentView(
            UUID id,
            UUID studentId,
            String studentName,
            UUID academicTermId,
            UUID subjectId,
            String subjectCode,
            String subjectName,
            UUID teacherStaffId,
            String teacherName,
            String commentType,
            String body,
            boolean visibleToFamily,
            Instant createdAt) {}

    public record StudentView(
            UUID id, String firstName, String lastName, String identifier, boolean childcare) {
        public String displayName() {
            return firstName + " " + lastName;
        }
    }

    public record SubjectSummary(
            UUID subjectId,
            String subjectCode,
            String subjectName,
            String color,
            BigDecimal average,
            BigDecimal coefficient,
            Map<String, BigDecimal> categoryAverages,
            int includedGrades) {}

    public record StudentSummary(
            UUID studentId,
            String studentName,
            List<SubjectSummary> subjects,
            BigDecimal overallAverage,
            Integer rank,
            Integer classSize) {}

    public record ReportCardView(
            UUID id,
            UUID studentId,
            String studentName,
            UUID academicYearId,
            UUID academicTermId,
            UUID classroomId,
            UUID policyId,
            UUID templateId,
            String templateName,
            String status,
            BigDecimal overallAverage,
            Integer classRank,
            Integer classSize,
            int versionNumber,
            int subjectCount,
            Instant calculatedAt,
            UUID supersedesReportCardId,
            String languageMode,
            String fileName,
            String pdfSha256,
            Long pdfSize,
            String signedByName,
            String signedByTitle,
            Instant publishedAt) {}

    public record ClassContext(
            UUID classroomId,
            String classroomName,
            UUID academicYearId,
            String academicYearName,
            UUID academicTermId,
            String academicTermName,
            UUID gradeLevelId,
            String gradeLevelName,
            UUID schoolLevelId,
            String schoolLevelCode,
            String schoolLevelName) {}

    public record GradingDashboard(
            ClassContext context,
            PolicyView policy,
            List<StudentView> students,
            List<AssessmentView> assessments,
            List<GradeView> grades,
            List<CompetencyView> competencies,
            List<CompetencyEvaluationView> competencyEvaluations,
            List<TeacherCommentView> teacherComments,
            List<StudentSummary> summaries,
            List<ReportCardView> reportCards) {}

    private record ClassScope(
            UUID classroomId,
            String classroomName,
            UUID academicYearId,
            String academicYearName,
            UUID termId,
            String termName,
            LocalDate termStartsOn,
            LocalDate termEndsOn,
            UUID gradeLevelId,
            String gradeLevelName,
            UUID schoolLevelId,
            String schoolLevelCode,
            String schoolLevelName) {}

    private record StudentScope(UUID classroomId, UUID gradeLevelId, UUID schoolLevelId) {}

    private record AssessmentInternal(
            UUID id,
            UUID classroomId,
            UUID termId,
            UUID schoolLevelId,
            BigDecimal maximumScore,
            String status,
            boolean numericEnabled) {}

    private record LatestReport(UUID id, String status, int versionNumber) {}
}
