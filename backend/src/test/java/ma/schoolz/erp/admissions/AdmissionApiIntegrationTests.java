package ma.schoolz.erp.admissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class AdmissionApiIntegrationTests {

    private static final String PASSWORD = "SchoolZTest2026!";
    private static final String PORTAL_PASSWORD = "SchoolZParent2026!";

    MockMvc mvc;
    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired IdentityTenantRepository tenants;
    @Autowired RoleRepository roles;
    @Autowired UserAccountRepository accounts;

    private UUID tenantId;
    private UUID campaignId;
    private UUID campusId;
    private UUID gradeId;
    private UUID classroomId;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanDatabase();
        IdentityTenant tenant =
                createUser(
                        "admissions-a",
                        "Admissions A",
                        "admin@admissions-a.ma",
                        Set.of(
                                "admissions.read",
                                "admissions.write",
                                "enrollment.read",
                                "enrollment.write",
                                "student.write",
                                "person.write"));
        tenantId = tenant.id();
        AcademicScope scope = createAcademicScope(tenantId, "A");
        campusId = scope.campusId();
        gradeId = scope.gradeId();
        classroomId = scope.classroomId();
        campaignId = createCampaign(tenantId, scope.yearId(), "ADM-A");
        token = login("admissions-a", "admin@admissions-a.ma");
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void parentJourneyFeedsKanbanDecisionOfferAndDashboard() throws Exception {
        mvc.perform(get("/api/v1/public/admissions/admissions-a/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaigns[0].id").value(campaignId.toString()))
                .andExpect(jsonPath("$.campuses[0].id").value(campusId.toString()));

        Cookie portalCookie =
                registerPortal("admissions-a", "parent@admissions-a.ma", "Famille Alaoui");
        String applicantId = createApplicant(portalCookie, "Yasmine", "Alaoui");
        String applicationId = createApplication(portalCookie, applicantId);

        mvc.perform(
                        put("/api/v1/public/admissions/applications/" + applicationId)
                                .cookie(portalCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"formData":{"motivation":"Projet bilingue",
                                         "languages":"Français, arabe"}}
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completenessPercent").value(60));

        uploadDocument(portalCookie, applicationId, "BIRTH_CERTIFICATE", "birth.pdf");
        String documentId =
                uploadDocument(portalCookie, applicationId, "REPORT_CARD", "report.pdf");

        mvc.perform(
                        post("/api/v1/public/admissions/applications/"
                                        + applicationId
                                        + "/fee-payments")
                                .cookie(portalCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"cardLast4\":\"4242\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));

        mvc.perform(
                        post("/api/v1/public/admissions/applications/" + applicationId + "/submit")
                                .cookie(portalCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLICATION_COMPLETE"))
                .andExpect(jsonPath("$.completenessPercent").value(100));

        mvc.perform(
                        patch("/api/v1/admissions/documents/" + documentId + "/review")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        move(applicationId, "TEST_SCHEDULED");
        move(applicationId, "REVIEW");
        MvcResult decision =
                mvc.perform(
                                post("/api/v1/admissions/applications/"
                                                + applicationId
                                                + "/decision")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"decision":"ACCEPTED","reason":"Avis favorable",
                                                 "offerConditions":"Dossier administratif complet"}
                                                """))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.decision").value("ACCEPTED"))
                        .andReturn();
        String offerId = JsonPath.read(decision.getResponse().getContentAsString(), "$.offerId");

        mvc.perform(
                        post("/api/v1/public/admissions/offers/" + offerId + "/accept")
                                .cookie(portalCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENROLLED"))
                .andExpect(jsonPath("$.offer.status").value("ACCEPTED"));

        mvc.perform(
                        get("/api/v1/admissions/board")
                                .param("campaignId", campaignId.toString())
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns[10].status").value("ENROLLED"))
                .andExpect(jsonPath("$.columns[10].cards[0].id").value(applicationId));
        mvc.perform(
                        get("/api/v1/admissions/dashboard")
                                .param("campaignId", campaignId.toString())
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applications").value(1))
                .andExpect(jsonPath("$.acceptances").value(1))
                .andExpect(jsonPath("$.enrollments").value(1));
        mvc.perform(get("/api/v1/public/admissions/notifications").cookie(portalCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("STATUS_CHANGED"));

        MvcResult enrollment =
                mvc.perform(
                                post("/api/v1/public/enrollments")
                                        .cookie(portalCookie)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"applicationId\":\"" + applicationId + "\"}"))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.currentStep").value("CONTRACT"))
                        .andReturn();
        String workflowId = JsonPath.read(enrollment.getResponse().getContentAsString(), "$.id");

        mvc.perform(
                        post("/api/v1/public/enrollments/" + workflowId + "/contract/accept")
                                .cookie(portalCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStep").value("FAMILY_INFORMATION"));
        mvc.perform(
                        put("/api/v1/public/enrollments/" + workflowId + "/family")
                                .cookie(portalCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"data":{"guardianFirstName":"Sara","guardianLastName":"Alaoui",
                                         "email":"parent@admissions-a.ma","phone":"+212600000000",
                                         "relationship":"MOTHER","addressLine1":"12 rue des Ã‰coles",
                                         "city":"Casablanca"}}
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.familyConfirmed").value(true));
        mvc.perform(
                        put("/api/v1/public/enrollments/" + workflowId + "/services")
                                .cookie(portalCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"transportSelected":true,"transportRoute":"Casa Centre",
                                         "cafeteriaSelected":true,"activities":["ROBOTICS"],
                                         "paymentPlan":"MONTHLY"}
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStep").value("DOCUMENTS"));
        MockMultipartFile identityDocument =
                new MockMultipartFile(
                        "file",
                        "identity.pdf",
                        "application/pdf",
                        "%PDF-1.7 identity".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(
                        multipart("/api/v1/public/enrollments/" + workflowId + "/documents")
                                .file(identityDocument)
                                .param("documentType", "IDENTITY_COPY")
                                .cookie(portalCookie))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentStep").value("SIGNATURE"));
        mvc.perform(
                        post("/api/v1/public/enrollments/" + workflowId + "/signature")
                                .cookie(portalCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"signerName\":\"Sara Alaoui\",\"accepted\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signed").value(true));
        mvc.perform(
                        post("/api/v1/public/enrollments/" + workflowId + "/fee-payments")
                                .cookie(portalCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"cardLast4\":\"4242\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feeStatus").value("PAID"));
        mvc.perform(
                        get("/api/v1/enrollments/" + workflowId + "/classes")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(classroomId.toString()));
        mvc.perform(
                        patch("/api/v1/enrollments/" + workflowId + "/class")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"classroomId\":\"" + classroomId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"));
        MvcResult activation =
                mvc.perform(
                                post("/api/v1/enrollments/" + workflowId + "/activate")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("ACTIVE"))
                        .andExpect(jsonPath("$.activatedStudentId").isNotEmpty())
                        .andReturn();
        String studentId =
                JsonPath.read(
                        activation.getResponse().getContentAsString(), "$.activatedStudentId");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM student_enrollment WHERE tenant_id=? AND"
                                        + " classroom_id=? AND status='ACTIVE'",
                                Integer.class,
                                tenantId,
                                classroomId))
                .isEqualTo(1);

        UUID renewalYearId = createRenewalYear(tenantId, campusId);
        mvc.perform(
                        post("/api/v1/public/enrollments/re-enrollments")
                                .cookie(portalCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "studentId",
                                                        studentId,
                                                        "academicYearId",
                                                        renewalYearId,
                                                        "campusId",
                                                        campusId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workflowType").value("RE_ENROLLMENT"))
                .andExpect(jsonPath("$.familyData.guardianFirstName").value("Sara"))
                .andExpect(jsonPath("$.familyData.addressLine1").value("12 rue des Ã‰coles"));
    }

    @Test
    void submissionReportsMissingMandatoryDocuments() throws Exception {
        Cookie portalCookie =
                registerPortal("admissions-a", "missing@admissions-a.ma", "Famille Pièces");
        String applicantId = createApplicant(portalCookie, "Adam", "Bennani");
        String applicationId = createApplication(portalCookie, applicantId);
        mvc.perform(
                        put("/api/v1/public/admissions/applications/" + applicationId)
                                .cookie(portalCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"formData\":{\"motivation\":\"SchoolZ\"}}"))
                .andExpect(status().isOk());

        mvc.perform(
                        post("/api/v1/public/admissions/applications/" + applicationId + "/submit")
                                .cookie(portalCookie))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.detail")
                                .value(org.hamcrest.Matchers.containsString("BIRTH_CERTIFICATE")))
                .andExpect(
                        jsonPath("$.detail")
                                .value(org.hamcrest.Matchers.containsString("REPORT_CARD")));
    }

    @Test
    void portalDocumentIsIsolatedBetweenTenants() throws Exception {
        Cookie owner = registerPortal("admissions-a", "owner@admissions-a.ma", "Famille A");
        String applicantId = createApplicant(owner, "Aya", "Owner");
        String applicationId = createApplication(owner, applicantId);
        String documentId = uploadDocument(owner, applicationId, "REPORT_CARD", "private.pdf");

        IdentityTenant tenantB =
                createUser(
                        "admissions-b",
                        "Admissions B",
                        "admin@admissions-b.ma",
                        Set.of("admissions.read", "admissions.write"));
        AcademicScope scopeB = createAcademicScope(tenantB.id(), "B");
        createCampaign(tenantB.id(), scopeB.yearId(), "ADM-B");
        Cookie outsider = registerPortal("admissions-b", "parent@admissions-b.ma", "Famille B");

        mvc.perform(get("/api/v1/public/admissions/documents/" + documentId).cookie(outsider))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/public/admissions/documents/" + documentId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(
                        result ->
                                assertThat(result.getResponse().getContentAsByteArray())
                                        .startsWith(
                                                "%PDF"
                                                        .getBytes(
                                                                java.nio.charset.StandardCharsets
                                                                        .UTF_8)));
    }

    @Test
    void portalRejectsWeakAndDuplicateAccountsAndRevokesLogout() throws Exception {
        mvc.perform(
                        post("/api/v1/public/admissions/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"tenantSlug":"admissions-a","email":"weak@example.ma",
                                         "password":"password","displayName":"Weak"}
                                        """))
                .andExpect(status().isBadRequest());
        Cookie cookie = registerPortal("admissions-a", "unique@example.ma", "Famille Unique");
        mvc.perform(
                        post("/api/v1/public/admissions/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "tenantSlug", "admissions-a",
                                                        "email", "unique@example.ma",
                                                        "password", PORTAL_PASSWORD,
                                                        "displayName", "Duplicate"))))
                .andExpect(status().isConflict());
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                                        "/api/v1/public/admissions/session")
                                .cookie(cookie))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("schoolz_admission", 0));
        mvc.perform(get("/api/v1/public/admissions/me").cookie(cookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void admissionsPermissionsProtectInternalWrites() throws Exception {
        IdentityTenant readonly =
                createUser(
                        "admissions-read",
                        "Admissions Read",
                        "reader@admissions.ma",
                        Set.of("admissions.read"));
        createAcademicScope(readonly.id(), "R");
        String readToken = login("admissions-read", "reader@admissions.ma");
        mvc.perform(
                        post("/api/v1/admissions/prospects")
                                .header(HttpHeaders.AUTHORIZATION, bearer(readToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"firstName":"No","lastName":"Write",
                                         "email":"no-write@example.ma","source":"WEBSITE"}
                                        """))
                .andExpect(status().isForbidden());
    }

    private Cookie registerPortal(String slug, String email, String displayName) throws Exception {
        MvcResult result =
                mvc.perform(
                                post("/api/v1/public/admissions/accounts")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "tenantSlug", slug,
                                                                "email", email,
                                                                "password", PORTAL_PASSWORD,
                                                                "displayName", displayName,
                                                                "phone", "+212600000000"))))
                        .andExpect(status().isOk())
                        .andExpect(cookie().httpOnly("schoolz_admission", true))
                        .andReturn();
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotBlank();
        String value = setCookie.split(";", 2)[0].split("=", 2)[1];
        return new Cookie("schoolz_admission", value);
    }

    private String createApplicant(Cookie cookie, String firstName, String lastName)
            throws Exception {
        MvcResult result =
                mvc.perform(
                                post("/api/v1/public/admissions/applicants")
                                        .cookie(cookie)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "firstName", firstName,
                                                                "lastName", lastName,
                                                                "birthDate", "2019-05-12",
                                                                "gender", "FEMALE",
                                                                "nationalityCode", "MA",
                                                                "currentSchool", "École Horizon"))))
                        .andExpect(status().isCreated())
                        .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createApplication(Cookie cookie, String applicantId) throws Exception {
        MvcResult result =
                mvc.perform(
                                post("/api/v1/public/admissions/applications")
                                        .cookie(cookie)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "campaignId", campaignId,
                                                                "applicantId", applicantId,
                                                                "desiredCampusId", campusId,
                                                                "desiredGradeLevelId", gradeId))))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.status").value("APPLICATION_STARTED"))
                        .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String uploadDocument(
            Cookie cookie, String applicationId, String documentType, String fileName)
            throws Exception {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        fileName,
                        "application/pdf",
                        "%PDF-1.7 SchoolZ test".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        MvcResult result =
                mvc.perform(
                                multipart(
                                                "/api/v1/public/admissions/applications/"
                                                        + applicationId
                                                        + "/documents")
                                        .file(file)
                                        .param("documentType", documentType)
                                        .cookie(cookie))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.documentType").value(documentType))
                        .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private void move(String applicationId, String statusName) throws Exception {
        mvc.perform(
                        patch("/api/v1/admissions/pipeline")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "cardType",
                                                        "APPLICATION",
                                                        "cardId",
                                                        applicationId,
                                                        "targetStatus",
                                                        statusName,
                                                        "comment",
                                                        "Test integration"))))
                .andExpect(status().isNoContent());
    }

    private IdentityTenant createUser(
            String slug, String name, String email, Set<String> permissions) {
        IdentityTenant tenant = tenants.save(IdentityTenant.create(slug, name, clock.instant()));
        Role role =
                roles.save(
                        Role.create(
                                tenant.id(),
                                "ADMISSION_TEST",
                                "Admissions test",
                                false,
                                permissions,
                                clock.instant()));
        UserAccount account =
                UserAccount.create(
                        tenant.id(),
                        email,
                        name + " User",
                        passwordEncoder.encode(PASSWORD),
                        "fr-MA",
                        true,
                        clock.instant());
        account.assignRoles(Set.of(role), clock.instant());
        accounts.save(account);
        return tenant;
    }

    private AcademicScope createAcademicScope(UUID scopeTenantId, String suffix) {
        Instant now = clock.instant();
        UUID organizationId = UUID.randomUUID();
        UUID yearId = UUID.randomUUID();
        UUID campus = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();
        UUID grade = UUID.randomUUID();
        UUID classroom = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO school_organization
                    (id,tenant_id,code,name,country_code,business_identifiers,created_at,updated_at)
                VALUES (?, ?, ?, ?, 'MA', '{}', ?, ?)
                """,
                organizationId,
                scopeTenantId,
                "ORG-" + suffix,
                "School " + suffix,
                timestamp(now),
                timestamp(now));
        jdbc.update(
                """
                INSERT INTO academic_year
                    (id,tenant_id,organization_id,code,name,starts_on,ends_on,status,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
                """,
                yearId,
                scopeTenantId,
                organizationId,
                "2026-2027-" + suffix,
                "Année " + suffix,
                LocalDate.now(clock).minusMonths(3),
                LocalDate.now(clock).plusYears(1),
                timestamp(now),
                timestamp(now));
        jdbc.update(
                """
                INSERT INTO school_campus
                    (id,tenant_id,organization_id,code,name,country_code,locale,timezone,
                     active,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, 'MA', 'fr-MA', 'Africa/Casablanca', TRUE, ?, ?)
                """,
                campus,
                scopeTenantId,
                organizationId,
                "CAMPUS-" + suffix,
                "Campus " + suffix,
                timestamp(now),
                timestamp(now));
        jdbc.update(
                """
                INSERT INTO school_level
                    (id,tenant_id,organization_id,code,name,sequence_number,created_at,updated_at)
                VALUES (?, ?, ?, ?, 'Primaire', 1, ?, ?)
                """,
                levelId,
                scopeTenantId,
                organizationId,
                "PRIMARY-" + suffix,
                timestamp(now),
                timestamp(now));
        jdbc.update(
                """
                INSERT INTO grade_level
                    (id,tenant_id,school_level_id,code,name,sequence_number,created_at,updated_at)
                VALUES (?, ?, ?, ?, '1AP', 1, ?, ?)
                """,
                grade,
                scopeTenantId,
                levelId,
                "1AP-" + suffix,
                timestamp(now),
                timestamp(now));
        jdbc.update(
                """
                INSERT INTO classroom
                    (id,tenant_id,campus_id,academic_year_id,grade_level_id,code,name,
                     capacity,active,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 26, TRUE, ?, ?)
                """,
                classroom,
                scopeTenantId,
                campus,
                yearId,
                grade,
                "1AP-" + suffix,
                "1AP " + suffix,
                timestamp(now),
                timestamp(now));
        return new AcademicScope(yearId, campus, grade, classroom);
    }

    private UUID createCampaign(UUID scopeTenantId, UUID yearId, String code) {
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                """
                INSERT INTO admission_campaign
                    (id,tenant_id,academic_year_id,code,name,opens_on,closes_on,
                     application_fee,currency_code,required_document_types,status,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 500.00, 'MAD',
                        '["BIRTH_CERTIFICATE","REPORT_CARD"]', 'OPEN', ?, ?)
                """,
                id,
                scopeTenantId,
                yearId,
                code,
                "Admissions " + code,
                LocalDate.now(clock).minusDays(10),
                LocalDate.now(clock).plusMonths(6),
                timestamp(now),
                timestamp(now));
        return id;
    }

    private UUID createRenewalYear(UUID scopeTenantId, UUID scopeCampusId) {
        Instant now = clock.instant();
        UUID organizationId =
                jdbc.queryForObject(
                        "SELECT organization_id FROM school_campus WHERE tenant_id=? AND id=?",
                        UUID.class,
                        scopeTenantId,
                        scopeCampusId);
        UUID yearId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO academic_year
                    (id,tenant_id,organization_id,code,name,starts_on,ends_on,status,created_at,updated_at)
                VALUES (?, ?, ?, '2027-2028-A', 'AnnÃ©e de rÃ©inscription', ?, ?, 'PLANNED', ?, ?)
                """,
                yearId,
                scopeTenantId,
                organizationId,
                LocalDate.now(clock).plusYears(1),
                LocalDate.now(clock).plusYears(2),
                timestamp(now),
                timestamp(now));
        return yearId;
    }

    private String login(String tenant, String email) throws Exception {
        MvcResult result =
                mvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "tenantSlug", tenant,
                                                                "email", email,
                                                                "password", PASSWORD))))
                        .andExpect(status().isOk())
                        .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private void cleanDatabase() {
        for (String table :
                new String[] {
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

    private static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private record AcademicScope(UUID yearId, UUID campusId, UUID gradeId, UUID classroomId) {}
}
