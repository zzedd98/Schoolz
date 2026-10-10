package ma.schoolz.erp.grading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.identity.domain.IdentityTenant;
import ma.schoolz.erp.identity.domain.Role;
import ma.schoolz.erp.identity.domain.UserAccount;
import ma.schoolz.erp.identity.persistence.IdentityTenantRepository;
import ma.schoolz.erp.identity.persistence.RoleRepository;
import ma.schoolz.erp.identity.persistence.UserAccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class GradingApiIntegrationTests {
    private static final String PASSWORD = "SchoolZGrading2026!";

    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired IdentityTenantRepository tenants;
    @Autowired RoleRepository roles;
    @Autowired UserAccountRepository accounts;

    private MockMvc mvc;
    private UUID tenantId;
    private UUID actorId;
    private UUID organizationId;
    private UUID campusId;
    private UUID yearId;
    private UUID termId;
    private UUID primaryLevelId;
    private UUID preschoolLevelId;
    private UUID gradeLevelId;
    private UUID preschoolGradeLevelId;
    private UUID classroomId;
    private UUID teacherId;
    private UUID mathId;
    private UUID studentOne;
    private UUID studentTwo;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanDatabase();
        IdentityTenant tenant =
                tenants.save(IdentityTenant.create("grading-a", "Grading A", clock.instant()));
        tenantId = tenant.id();
        Role role =
                roles.save(
                        Role.create(
                                tenantId,
                                "GRADING_TEST",
                                "Grading test",
                                false,
                                Set.of(
                                        "grade.read",
                                        "grade.write",
                                        "grade.configure",
                                        "report-card.calculate",
                                        "report-card.publish"),
                                clock.instant()));
        UserAccount actor =
                UserAccount.create(
                        tenantId,
                        "teacher@grading-a.ma",
                        "Responsable évaluation",
                        passwordEncoder.encode(PASSWORD),
                        "fr-MA",
                        true,
                        clock.instant());
        actor.assignRoles(Set.of(role), clock.instant());
        accounts.save(actor);
        actorId = actor.id();
        token = login("grading-a", "teacher@grading-a.ma", PASSWORD);
        createAcademicScope();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void hybridModelHistoriesGradeChangesAndCalculatesRankedDrafts() throws Exception {
        UUID numericScale = createNumericScale();
        UUID competencyScale = createCompetencyScale();
        savePolicy(
                primaryLevelId,
                "HYBRID",
                numericScale,
                competencyScale,
                true,
                Map.of(
                        "categoryWeights",
                        Map.of("CONTINUOUS", 60, "EXAM", 40),
                        "missingCategoryMode",
                        "RENORMALIZE",
                        "roundingScale",
                        2,
                        "overallUseCoefficients",
                        true,
                        "rankingMethod",
                        "COMPETITION"));
        UUID continuousType = createType("CONTROLE", "CONTINUOUS", true, false);
        UUID examType = createType("EXAMEN", "EXAM", true, false);
        UUID competency = createCompetency();
        saveCoefficient(new BigDecimal("3"));
        UUID continuous = createAssessment(continuousType, "Contrôle", LocalDate.of(2026, 9, 15));
        UUID exam = createAssessment(examType, "Examen", LocalDate.of(2026, 10, 15));

        UUID gradeOne = saveGrade(continuous, studentOne, "10", "GRADED", null);
        saveGrade(continuous, studentOne, "12", "GRADED", "Copie revérifiée");
        saveGrade(exam, studentOne, "18", "GRADED", null);
        saveGrade(continuous, studentTwo, "16", "GRADED", null);
        saveGrade(exam, studentTwo, "14", "GRADED", null);
        createCompetencyEvaluation(competency, studentOne, "ACQUIRED");
        createTeacherComment(studentOne);

        mvc.perform(
                        get("/api/v1/grading/grades/" + gradeOne + "/history")
                                .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].changeType").value("CREATED"))
                .andExpect(jsonPath("$[1].changeType").value("UPDATED"))
                .andExpect(jsonPath("$[1].previousScore").value(10))
                .andExpect(jsonPath("$[1].newScore").value(12))
                .andExpect(jsonPath("$[1].reason").value("Copie revérifiée"));

        mvc.perform(
                        get("/api/v1/grading/dashboard")
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .param("classroomId", classroomId.toString())
                                .param("academicTermId", termId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policy.evaluationMode").value("HYBRID"))
                .andExpect(jsonPath("$.summaries.length()").value(2))
                .andExpect(jsonPath("$.competencyEvaluations.length()").value(1))
                .andExpect(jsonPath("$.teacherComments.length()").value(1))
                .andExpect(jsonPath("$.summaries[0].overallAverage").value(14.4))
                .andExpect(jsonPath("$.summaries[0].rank").value(2))
                .andExpect(jsonPath("$.summaries[1].overallAverage").value(15.2))
                .andExpect(jsonPath("$.summaries[1].rank").value(1));

        UUID templateId = createTemplate();
        mvc.perform(
                        post("/api/v1/grading/report-cards/calculate")
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .param("classroomId", classroomId.toString())
                                .param("academicTermId", termId.toString())
                                .param("templateId", templateId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("CALCULATED"))
                .andExpect(jsonPath("$[0].subjectCount").value(1));

        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM identity_audit_log WHERE tenant_id=? AND"
                                        + " action='grading.grade.updated'",
                                Integer.class,
                                tenantId))
                .isEqualTo(1);
    }

    @Test
    void publishedPdfIsImmutableAndCorrectionCreatesTraceableVersion() throws Exception {
        UUID numericScale = createNumericScale();
        savePolicy(
                primaryLevelId,
                "NUMERIC",
                numericScale,
                null,
                true,
                Map.of("categoryWeights", Map.of("CONTINUOUS", 100), "roundingScale", 2));
        UUID type = createType("CONTROLE", "CONTINUOUS", true, false);
        UUID assessment = createAssessment(type, "Contrôle bilingue", LocalDate.of(2026, 10, 3));
        saveGrade(assessment, studentOne, "15", "GRADED", null);
        saveGrade(assessment, studentTwo, "13", "GRADED", null);
        UUID templateId = createTemplate();

        mvc.perform(
                        post("/api/v1/grading/report-cards/calculate")
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .param("classroomId", classroomId.toString())
                                .param("academicTermId", termId.toString())
                                .param("templateId", templateId.toString()))
                .andExpect(status().isOk());
        UUID versionOne =
                jdbc.queryForObject(
                        "SELECT id FROM grading_report_card WHERE tenant_id=? AND student_id=? AND"
                                + " academic_term_id=? AND version_number=1",
                        UUID.class,
                        tenantId,
                        studentOne,
                        termId);
        Map<String, Object> publication =
                Map.of(
                        "languageMode",
                        "BILINGUAL",
                        "signerName",
                        "Nadia El Mansouri",
                        "signerTitle",
                        "Directrice",
                        "publicationNote",
                        "Version remise à la famille");

        MvcResult preview =
                mvc.perform(
                                post("/api/v1/grading/report-cards/" + versionOne + "/preview")
                                        .header(HttpHeaders.AUTHORIZATION, bearer())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json.writeValueAsString(publication)))
                        .andExpect(status().isOk())
                        .andExpect(
                                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                        .content()
                                        .contentType(MediaType.APPLICATION_PDF))
                        .andReturn();
        assertThat(preview.getResponse().getContentAsByteArray()).startsWith('%', 'P', 'D', 'F');

        MvcResult published =
                mvc.perform(
                                post("/api/v1/grading/report-cards/" + versionOne + "/publish")
                                        .header(HttpHeaders.AUTHORIZATION, bearer())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json.writeValueAsString(publication)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("PUBLISHED"))
                        .andExpect(jsonPath("$.languageMode").value("BILINGUAL"))
                        .andExpect(jsonPath("$.versionNumber").value(1))
                        .andExpect(jsonPath("$.pdfSha256").isNotEmpty())
                        .andReturn();
        String digest = JsonPath.read(published.getResponse().getContentAsString(), "$.pdfSha256");

        MvcResult firstDownload =
                mvc.perform(
                                get("/api/v1/grading/report-cards/" + versionOne + "/pdf")
                                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                        .andExpect(status().isOk())
                        .andExpect(
                                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                        .header()
                                        .string("X-Content-SHA256", digest))
                        .andReturn();

        saveGrade(assessment, studentOne, "17", "GRADED", "Correction après publication");
        mvc.perform(
                        post("/api/v1/grading/report-cards/calculate")
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .param("classroomId", classroomId.toString())
                                .param("academicTermId", termId.toString())
                                .param("templateId", templateId.toString()))
                .andExpect(status().isOk());

        UUID versionTwo =
                jdbc.queryForObject(
                        "SELECT id FROM grading_report_card WHERE tenant_id=? AND student_id=? AND"
                                + " academic_term_id=? AND version_number=2",
                        UUID.class,
                        tenantId,
                        studentOne,
                        termId);
        assertThat(versionTwo).isNotEqualTo(versionOne);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT supersedes_report_card_id FROM grading_report_card WHERE"
                                        + " id=?",
                                UUID.class,
                                versionTwo))
                .isEqualTo(versionOne);

        mvc.perform(
                        get("/api/v1/grading/report-cards/" + versionTwo + "/versions")
                                .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].versionNumber").value(2))
                .andExpect(jsonPath("$[0].status").value("CALCULATED"))
                .andExpect(jsonPath("$[1].versionNumber").value(1))
                .andExpect(jsonPath("$[1].status").value("PUBLISHED"));

        MvcResult unchanged =
                mvc.perform(
                                get("/api/v1/grading/report-cards/" + versionOne + "/pdf")
                                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                        .andExpect(status().isOk())
                        .andReturn();
        assertThat(unchanged.getResponse().getContentAsByteArray())
                .isEqualTo(firstDownload.getResponse().getContentAsByteArray());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM identity_audit_log WHERE tenant_id=? AND"
                                        + " action='grading.report-card.published'",
                                Integer.class,
                                tenantId))
                .isEqualTo(1);
    }

    @Test
    void anotherTenantCannotReadOrPublishAReportCard() throws Exception {
        UUID numericScale = createNumericScale();
        savePolicy(
                primaryLevelId,
                "NUMERIC",
                numericScale,
                null,
                false,
                Map.of("categoryWeights", Map.of("CONTINUOUS", 100), "roundingScale", 2));
        UUID type = createType("CONTROLE", "CONTINUOUS", true, false);
        UUID assessment = createAssessment(type, "Contrôle", LocalDate.of(2026, 10, 3));
        saveGrade(assessment, studentOne, "15", "GRADED", null);
        mvc.perform(
                        post("/api/v1/grading/report-cards/calculate")
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .param("classroomId", classroomId.toString())
                                .param("academicTermId", termId.toString()))
                .andExpect(status().isOk());
        UUID reportId =
                jdbc.queryForObject(
                        "SELECT id FROM grading_report_card WHERE tenant_id=? AND student_id=?",
                        UUID.class,
                        tenantId,
                        studentOne);

        IdentityTenant other =
                tenants.save(
                        IdentityTenant.create(
                                "grading-private", "Grading Private", clock.instant()));
        Role role =
                roles.save(
                        Role.create(
                                other.id(),
                                "PUBLISHER",
                                "Publisher",
                                false,
                                Set.of("grade.read", "report-card.publish"),
                                clock.instant()));
        UserAccount user =
                UserAccount.create(
                        other.id(),
                        "publisher@private.ma",
                        "Publisher",
                        passwordEncoder.encode(PASSWORD),
                        "fr-MA",
                        true,
                        clock.instant());
        user.assignRoles(Set.of(role), clock.instant());
        accounts.save(user);
        String otherToken = login("grading-private", "publisher@private.ma", PASSWORD);
        String payload =
                json.writeValueAsString(Map.of("languageMode", "FR", "signerName", "Direction"));

        mvc.perform(
                        get("/api/v1/grading/report-cards/" + reportId + "/pdf")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isNotFound());
        mvc.perform(
                        post("/api/v1/grading/report-cards/" + reportId + "/publish")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                .andExpect(status().isNotFound());
    }

    @Test
    void cyclePoliciesAndChildcareRuleBlockNumericGradesAtTheServer() throws Exception {
        UUID numericScale = createNumericScale();
        UUID competencyScale = createCompetencyScale();
        savePolicy(
                primaryLevelId,
                "HYBRID",
                numericScale,
                competencyScale,
                false,
                Map.of("categoryWeights", Map.of("CONTINUOUS", 100), "roundingScale", 2));
        savePolicy(
                preschoolLevelId,
                "COMPETENCY_ONLY",
                null,
                competencyScale,
                false,
                Map.of("roundingScale", 2));
        UUID numericType = createType("NOTE", "CONTINUOUS", true, false);
        UUID assessment = createAssessment(numericType, "Note primaire", LocalDate.of(2026, 9, 20));
        markAsChildcare(studentTwo);

        mvc.perform(
                        put("/api/v1/grading/assessments/" + assessment + "/grades/" + studentTwo)
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of("rawScore", 15, "status", "GRADED"))))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.message")
                                .value(org.hamcrest.Matchers.containsString("crèche")));

        UUID preschoolClass = createPreschoolClass();
        mvc.perform(
                        post("/api/v1/grading/assessments")
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                assessmentPayload(
                                                        preschoolClass,
                                                        numericType,
                                                        "Note interdite",
                                                        LocalDate.of(2026, 9, 22)))))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.message")
                                .value(org.hamcrest.Matchers.containsString("notes numériques")));
    }

    @Test
    void dashboardNeverResolvesAClassFromAnotherTenant() throws Exception {
        IdentityTenant other =
                tenants.save(IdentityTenant.create("grading-b", "Grading B", clock.instant()));
        Role otherRole =
                roles.save(
                        Role.create(
                                other.id(),
                                "READER",
                                "Reader",
                                false,
                                Set.of("grade.read"),
                                clock.instant()));
        UserAccount otherUser =
                UserAccount.create(
                        other.id(),
                        "reader@grading-b.ma",
                        "Reader B",
                        passwordEncoder.encode(PASSWORD),
                        "fr-MA",
                        true,
                        clock.instant());
        otherUser.assignRoles(Set.of(otherRole), clock.instant());
        accounts.save(otherUser);
        String otherToken = login("grading-b", "reader@grading-b.ma", PASSWORD);

        mvc.perform(
                        get("/api/v1/grading/dashboard")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                                .param("classroomId", classroomId.toString())
                                .param("academicTermId", termId.toString()))
                .andExpect(status().isNotFound());
    }

    private UUID createNumericScale() throws Exception {
        return id(
                mvc.perform(
                                post("/api/v1/grading/scales")
                                        .header(HttpHeaders.AUTHORIZATION, bearer())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "code",
                                                                "NOTE-20",
                                                                "name",
                                                                "Sur 20",
                                                                "scaleType",
                                                                "NUMERIC",
                                                                "minimumScore",
                                                                0,
                                                                "maximumScore",
                                                                20,
                                                                "passingScore",
                                                                10,
                                                                "roundingScale",
                                                                2,
                                                                "competencyLevels",
                                                                List.of()))))
                        .andExpect(status().isCreated())
                        .andReturn());
    }

    private UUID createCompetencyScale() throws Exception {
        return id(
                mvc.perform(
                                post("/api/v1/grading/scales")
                                        .header(HttpHeaders.AUTHORIZATION, bearer())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "code",
                                                                "COMP-3",
                                                                "name",
                                                                "Compétences",
                                                                "scaleType",
                                                                "COMPETENCY",
                                                                "roundingScale",
                                                                0,
                                                                "competencyLevels",
                                                                List.of(
                                                                        Map.of(
                                                                                "code",
                                                                                        "DEVELOPING",
                                                                                "labelFr",
                                                                                        "En développement",
                                                                                "sequenceNumber", 1,
                                                                                "color", "#D5A22F"),
                                                                        Map.of(
                                                                                "code", "ACQUIRED",
                                                                                "labelFr", "Acquis",
                                                                                "sequenceNumber", 2,
                                                                                "color", "#2F8F68"),
                                                                        Map.of(
                                                                                "code", "MASTERED",
                                                                                "labelFr",
                                                                                        "Maîtrisé",
                                                                                "sequenceNumber", 3,
                                                                                "color",
                                                                                        "#236B53"))))))
                        .andExpect(status().isCreated())
                        .andReturn());
    }

    private void savePolicy(
            UUID schoolLevelId,
            String mode,
            UUID numericScale,
            UUID competencyScale,
            boolean rankings,
            Map<String, Object> config)
            throws Exception {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("scopeType", "SCHOOL_LEVEL");
        payload.put("schoolLevelId", schoolLevelId);
        payload.put("code", "POLICY-" + schoolLevelId);
        payload.put("name", "Politique test");
        payload.put("evaluationMode", mode);
        payload.put("gradeScaleId", numericScale);
        payload.put("competencyScaleId", competencyScale);
        payload.put(
                "calculationStrategy",
                mode.equals("COMPETENCY_ONLY") ? "WEIGHTED_MEAN" : "CATEGORY_WEIGHTED");
        payload.put("calculationConfig", config);
        payload.put("rankingsEnabled", rankings);
        payload.put("reportCardsEnabled", true);
        mvc.perform(
                        put("/api/v1/grading/policies")
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(payload)))
                .andExpect(status().isOk());
    }

    private UUID createType(String code, String category, boolean numeric, boolean competency)
            throws Exception {
        return id(
                mvc.perform(
                                post("/api/v1/grading/assessment-types")
                                        .header(HttpHeaders.AUTHORIZATION, bearer())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "code", code,
                                                                "nameFr", code,
                                                                "calculationCategory", category,
                                                                "numericEnabled", numeric,
                                                                "competencyEnabled", competency))))
                        .andExpect(status().isCreated())
                        .andReturn());
    }

    private void saveCoefficient(BigDecimal coefficient) throws Exception {
        mvc.perform(
                        put("/api/v1/grading/coefficients")
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "academicYearId", yearId,
                                                        "gradeLevelId", gradeLevelId,
                                                        "subjectId", mathId,
                                                        "coefficient", coefficient))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coefficient").value(3));
    }

    private UUID createCompetency() throws Exception {
        return id(
                mvc.perform(
                                post("/api/v1/grading/competencies")
                                        .header(HttpHeaders.AUTHORIZATION, bearer())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "schoolLevelId",
                                                                primaryLevelId,
                                                                "subjectId",
                                                                mathId,
                                                                "code",
                                                                "MATH-PROBLEM",
                                                                "nameFr",
                                                                "Résoudre un problème",
                                                                "sequenceNumber",
                                                                1))))
                        .andExpect(status().isCreated())
                        .andReturn());
    }

    private UUID createTemplate() throws Exception {
        return id(
                mvc.perform(
                                post("/api/v1/grading/report-card-templates")
                                        .header(HttpHeaders.AUTHORIZATION, bearer())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "schoolLevelId",
                                                                primaryLevelId,
                                                                "code",
                                                                "PRIMARY",
                                                                "name",
                                                                "Bulletin primaire",
                                                                "languageMode",
                                                                "BILINGUAL",
                                                                "layoutConfig",
                                                                Map.of("showRank", true),
                                                                "versionNumber",
                                                                1))))
                        .andExpect(status().isCreated())
                        .andReturn());
    }

    private UUID createAssessment(UUID typeId, String title, LocalDate date) throws Exception {
        return id(
                mvc.perform(
                                post("/api/v1/grading/assessments")
                                        .header(HttpHeaders.AUTHORIZATION, bearer())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        assessmentPayload(
                                                                classroomId, typeId, title, date))))
                        .andExpect(status().isCreated())
                        .andReturn());
    }

    private Map<String, Object> assessmentPayload(
            UUID targetClassroom, UUID typeId, String title, LocalDate date) {
        return Map.of(
                "classroomId", targetClassroom,
                "academicTermId", termId,
                "subjectId", mathId,
                "assessmentTypeId", typeId,
                "teacherStaffId", teacherId,
                "title", title,
                "assessmentDate", date.toString(),
                "maximumScore", 20,
                "coefficient", 1);
    }

    private UUID saveGrade(
            UUID assessmentId, UUID studentId, String score, String gradeStatus, String reason)
            throws Exception {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("rawScore", new BigDecimal(score));
        payload.put("status", gradeStatus);
        if (reason != null) payload.put("reason", reason);
        return id(
                mvc.perform(
                                put("/api/v1/grading/assessments/"
                                                + assessmentId
                                                + "/grades/"
                                                + studentId)
                                        .header(HttpHeaders.AUTHORIZATION, bearer())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json.writeValueAsString(payload)))
                        .andExpect(status().isOk())
                        .andReturn());
    }

    private void createCompetencyEvaluation(UUID competencyId, UUID studentId, String level)
            throws Exception {
        mvc.perform(
                        post("/api/v1/grading/competency-evaluations")
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "competencyId", competencyId,
                                                        "studentId", studentId,
                                                        "academicTermId", termId,
                                                        "teacherStaffId", teacherId,
                                                        "levelCode", level,
                                                        "observation", "Démarche claire"))))
                .andExpect(status().isCreated());
    }

    private void createTeacherComment(UUID studentId) throws Exception {
        mvc.perform(
                        post("/api/v1/grading/teacher-comments")
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "studentId", studentId,
                                                        "academicTermId", termId,
                                                        "subjectId", mathId,
                                                        "teacherStaffId", teacherId,
                                                        "commentType", "SUBJECT",
                                                        "body", "Très bonne progression.",
                                                        "visibleToFamily", true))))
                .andExpect(status().isCreated());
    }

    private void createAcademicScope() {
        Instant now = clock.instant();
        organizationId = UUID.randomUUID();
        campusId = UUID.randomUUID();
        yearId = UUID.randomUUID();
        termId = UUID.randomUUID();
        primaryLevelId = UUID.randomUUID();
        preschoolLevelId = UUID.randomUUID();
        gradeLevelId = UUID.randomUUID();
        preschoolGradeLevelId = UUID.randomUUID();
        classroomId = UUID.randomUUID();
        teacherId = UUID.randomUUID();
        mathId = UUID.randomUUID();
        studentOne = UUID.randomUUID();
        studentTwo = UUID.randomUUID();

        jdbc.update(
                """
                INSERT INTO school_organization
                    (id,tenant_id,code,name,country_code,created_at,updated_at)
                VALUES (?, ?, 'ORG', 'Ecole Grading', 'MA', ?, ?)
                """,
                organizationId,
                tenantId,
                timestamp(now),
                timestamp(now));
        jdbc.update(
                """
                INSERT INTO school_campus
                    (id,tenant_id,organization_id,code,name,country_code,locale,timezone,active,
                     created_at,updated_at)
                VALUES (?, ?, ?, 'MAIN', 'Campus principal', 'MA', 'fr-MA',
                        'Africa/Casablanca', TRUE, ?, ?)
                """,
                campusId,
                tenantId,
                organizationId,
                timestamp(now),
                timestamp(now));
        jdbc.update(
                """
                INSERT INTO academic_year
                    (id,tenant_id,organization_id,code,name,starts_on,ends_on,status,
                     created_at,updated_at)
                VALUES (?, ?, ?, '2026', '2026-2027', ?, ?, 'ACTIVE', ?, ?)
                """,
                yearId,
                tenantId,
                organizationId,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2027, 6, 30),
                timestamp(now),
                timestamp(now));
        jdbc.update(
                """
                INSERT INTO academic_term
                    (id,tenant_id,academic_year_id,code,name,term_type,sequence_number,
                     starts_on,ends_on,created_at,updated_at)
                VALUES (?, ?, ?, 'T1', 'Trimestre 1', 'TRIMESTER', 1, ?, ?, ?, ?)
                """,
                termId,
                tenantId,
                yearId,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 12, 20),
                timestamp(now),
                timestamp(now));
        schoolLevel(primaryLevelId, "PRIMARY", "Primaire", 2, now);
        schoolLevel(preschoolLevelId, "PRESCHOOL", "Maternelle", 1, now);
        gradeLevel(gradeLevelId, primaryLevelId, "CE1", "CE1", 2, now);
        gradeLevel(preschoolGradeLevelId, preschoolLevelId, "MS", "Moyenne section", 1, now);
        classroom(classroomId, gradeLevelId, "CE1-A", "CE1 A", now);

        UUID teacherPerson = UUID.randomUUID();
        person(teacherPerson, "Fouad", "Mellouli", LocalDate.of(1985, 5, 2), now);
        jdbc.update(
                """
                INSERT INTO staff_profile
                    (id,tenant_id,person_id,employee_number,job_title,employment_status,
                     hired_on,created_at,updated_at)
                VALUES (?, ?, ?, 'EMP-GRADE', 'Professeur', 'ACTIVE', ?, ?, ?)
                """,
                teacherId,
                tenantId,
                teacherPerson,
                LocalDate.of(2020, 9, 1),
                timestamp(now),
                timestamp(now));
        jdbc.update(
                """
                INSERT INTO timetable_subject
                    (id,tenant_id,code,name_fr,name_ar,color,active,created_at,updated_at)
                VALUES (?, ?, 'MATH', 'Mathematiques', NULL, '#315E8A', TRUE, ?, ?)
                """,
                mathId,
                tenantId,
                timestamp(now),
                timestamp(now));

        createStudent(studentOne, "Amine", "Alaoui", "SZ-001", LocalDate.of(2018, 3, 4), now);
        createStudent(studentTwo, "Salma", "Bennis", "SZ-002", LocalDate.of(2018, 7, 12), now);
    }

    private void schoolLevel(UUID id, String code, String name, int sequence, Instant now) {
        jdbc.update(
                """
                INSERT INTO school_level
                    (id,tenant_id,organization_id,code,name,sequence_number,active,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, TRUE, ?, ?)
                """,
                id,
                tenantId,
                organizationId,
                code,
                name,
                sequence,
                timestamp(now),
                timestamp(now));
    }

    private void gradeLevel(
            UUID id, UUID schoolLevelId, String code, String name, int sequence, Instant now) {
        jdbc.update(
                """
                INSERT INTO grade_level
                    (id,tenant_id,school_level_id,code,name,sequence_number,active,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, TRUE, ?, ?)
                """,
                id,
                tenantId,
                schoolLevelId,
                code,
                name,
                sequence,
                timestamp(now),
                timestamp(now));
    }

    private void classroom(UUID id, UUID gradeId, String code, String name, Instant now) {
        jdbc.update(
                """
                INSERT INTO classroom
                    (id,tenant_id,campus_id,academic_year_id,grade_level_id,code,name,capacity,
                     active,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 24, TRUE, ?, ?)
                """,
                id,
                tenantId,
                campusId,
                yearId,
                gradeId,
                code,
                name,
                timestamp(now),
                timestamp(now));
    }

    private void person(
            UUID id, String firstName, String lastName, LocalDate birthDate, Instant now) {
        jdbc.update(
                """
                INSERT INTO person
                    (id,tenant_id,first_name,last_name,birth_date,status,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
                """,
                id,
                tenantId,
                firstName,
                lastName,
                birthDate,
                timestamp(now),
                timestamp(now));
    }

    private void createStudent(
            UUID studentId,
            String firstName,
            String lastName,
            String identifier,
            LocalDate birthDate,
            Instant now) {
        UUID personId = UUID.randomUUID();
        person(personId, firstName, lastName, birthDate, now);
        jdbc.update(
                """
                INSERT INTO student_profile
                    (id,tenant_id,person_id,status,created_at,updated_at)
                VALUES (?, ?, ?, 'ACTIVE', ?, ?)
                """,
                studentId,
                tenantId,
                personId,
                timestamp(now),
                timestamp(now));
        jdbc.update(
                """
                INSERT INTO student_identifier
                    (id,tenant_id,student_id,identifier_type,identifier_value,issuer,
                     primary_identifier,created_at)
                VALUES (?, ?, ?, 'INTERNAL', ?, 'SCHOOLZ', TRUE, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                studentId,
                identifier,
                timestamp(now));
        jdbc.update(
                """
                INSERT INTO student_enrollment
                    (id,tenant_id,student_id,campus_id,academic_year_id,classroom_id,status,
                     enrolled_on,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                studentId,
                campusId,
                yearId,
                classroomId,
                LocalDate.of(2026, 9, 1),
                timestamp(now),
                timestamp(now));
    }

    private UUID createPreschoolClass() {
        UUID id = UUID.randomUUID();
        classroom(id, preschoolGradeLevelId, "MS-A", "Moyenne section A", clock.instant());
        return id;
    }

    private void markAsChildcare(UUID studentId) {
        Instant now = clock.instant();
        UUID groupId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO childcare_group
                    (id,tenant_id,campus_id,reference_educator_id,code,name,min_age_months,
                     max_age_months,capacity,active,created_at,updated_at)
                VALUES (?, ?, ?, ?, 'CRECHE-TEST', 'Groupe test', 0, 48, 12, TRUE, ?, ?)
                """,
                groupId,
                tenantId,
                campusId,
                teacherId,
                timestamp(now),
                timestamp(now));
        jdbc.update(
                """
                INSERT INTO childcare_profile
                    (id,tenant_id,student_id,group_id,expected_arrival_time,
                     expected_departure_time,active,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, TRUE, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                studentId,
                groupId,
                LocalTime.of(8, 0),
                LocalTime.of(17, 0),
                timestamp(now),
                timestamp(now));
    }

    private UUID id(MvcResult result) throws Exception {
        return UUID.fromString(
                JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString());
    }

    private String login(String tenant, String email, String password) throws Exception {
        MvcResult result =
                mvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "tenantSlug", tenant,
                                                                "email", email,
                                                                "password", password))))
                        .andExpect(status().isOk())
                        .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private String bearer() {
        return "Bearer " + token;
    }

    private void cleanDatabase() {
        for (String table :
                new String[] {
                    "grading_report_card_subject_result",
                    "grading_report_card",
                    "grading_teacher_comment",
                    "grading_competency_evaluation",
                    "grading_competency",
                    "grading_grade_history",
                    "grading_grade",
                    "grading_assessment",
                    "grading_coefficient",
                    "grading_report_card_template",
                    "grading_assessment_type",
                    "grading_policy",
                    "grading_grade_scale",
                    "timetable_lesson",
                    "timetable_teacher_availability",
                    "timetable_room_availability",
                    "timetable_constraint",
                    "timetable_teacher_assignment",
                    "timetable_room_equipment",
                    "timetable_time_slot",
                    "timetable_period",
                    "timetable_schedule",
                    "timetable_subject",
                    "attendance_parent_notification",
                    "attendance_excused_absence",
                    "attendance_late_arrival",
                    "attendance_early_departure",
                    "attendance_absence",
                    "attendance_record",
                    "attendance_session",
                    "attendance_sanction",
                    "disciplinary_incident",
                    "teacher_observation",
                    "attendance_absence_reason",
                    "childcare_daily_event",
                    "childcare_daily_log",
                    "childcare_pickup_person",
                    "childcare_profile",
                    "childcare_group",
                    "childcare_policy",
                    "enrollment_signature",
                    "enrollment_fee_payment",
                    "enrollment_document",
                    "enrollment_service_selection",
                    "enrollment_family_confirmation",
                    "enrollment_contract",
                    "enrollment_portal_student_link",
                    "enrollment_workflow",
                    "admission_fee_payment",
                    "admission_notification",
                    "admission_enrollment_offer",
                    "admission_decision",
                    "admission_test",
                    "admission_interview",
                    "admission_visit",
                    "admission_document",
                    "admission_application_status",
                    "admission_application",
                    "admission_applicant",
                    "admission_prospect",
                    "admission_step",
                    "admission_campaign",
                    "admission_portal_session",
                    "admission_portal_account",
                    "imported_legacy_payment",
                    "imported_opening_balance",
                    "data_import_row",
                    "data_import_job",
                    "student_history",
                    "person_document",
                    "person_custom_field",
                    "student_identifier",
                    "student_enrollment",
                    "emergency_contact",
                    "household_relationship",
                    "guardian_profile",
                    "staff_profile",
                    "student_profile",
                    "person_contact_method",
                    "person_address",
                    "household",
                    "person",
                    "classroom",
                    "physical_room",
                    "campus_grade_offering",
                    "grade_level",
                    "school_level",
                    "academic_term",
                    "academic_year",
                    "school_campus",
                    "school_settings",
                    "school_organization",
                    "identity_audit_log",
                    "identity_one_time_token",
                    "identity_refresh_session",
                    "identity_user_role",
                    "identity_role_permission",
                    "identity_role",
                    "identity_user_account",
                    "identity_tenant"
                }) {
            jdbc.update("DELETE FROM " + table);
        }
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }
}
