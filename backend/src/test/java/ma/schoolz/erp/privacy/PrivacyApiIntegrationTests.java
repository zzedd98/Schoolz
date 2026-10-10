package ma.schoolz.erp.privacy;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.identity.domain.IdentityTenant;
import ma.schoolz.erp.identity.domain.Role;
import ma.schoolz.erp.identity.domain.UserAccount;
import ma.schoolz.erp.identity.persistence.IdentityTenantRepository;
import ma.schoolz.erp.identity.persistence.RoleRepository;
import ma.schoolz.erp.identity.persistence.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ActiveProfiles("dev")
@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:privacy;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "schoolz.demo.seed-enabled=true",
            "schoolz.demo.tenant-slug=ecole-demo",
            "schoolz.demo.tenant-name=École Démo",
            "schoolz.demo.admin-email=admin@schoolz.ma",
            "schoolz.demo.admin-password=SchoolZDemo2026!",
            "schoolz.demo.teacher-email=prof.math@schoolz.ma",
            "schoolz.demo.teacher-password=SchoolZTeacher2026!",
            "schoolz.payments.sandbox.webhook-secret=schoolz-test-privacy-webhook-secret"
        })
class PrivacyApiIntegrationTests {
    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired IdentityTenantRepository tenants;
    @Autowired RoleRepository roles;
    @Autowired UserAccountRepository accounts;
    @Autowired PasswordEncoder passwordEncoder;

    private MockMvc mvc;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        token = login("admin@schoolz.ma", "SchoolZDemo2026!");
    }

    @Test
    void dashboardExposesPurposesRetentionAndUnverifiedHosting() throws Exception {
        mvc.perform(get("/api/v1/privacy").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purposes.length()").value(greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.retention.length()").value(greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.hosting.countryCode").value("MA"))
                .andExpect(jsonPath("$.hosting.legalValidationStatus").value("PENDING"));
    }

    @Test
    void managerProcessesRightsConsentExportAndHostingTrace() throws Exception {
        MvcResult dashboard =
                mvc.perform(get("/api/v1/privacy").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                        .andExpect(status().isOk())
                        .andReturn();
        String purposeId =
                JsonPath.read(dashboard.getResponse().getContentAsString(), "$.purposes[0].id");
        mvc.perform(
                        post("/api/v1/privacy/consents")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "purposeId",
                                                        purposeId,
                                                        "subjectType",
                                                        "GUARDIAN",
                                                        "subjectReference",
                                                        "FAM-DEMO-001",
                                                        "decision",
                                                        "GRANTED",
                                                        "source",
                                                        "PORTAL"))))
                .andExpect(status().isCreated());

        MvcResult created =
                mvc.perform(
                                post("/api/v1/privacy/requests")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "requestType",
                                                                "EXPORT",
                                                                "subjectType",
                                                                "GUARDIAN",
                                                                "subjectReference",
                                                                "FAM-DEMO-001",
                                                                "notes",
                                                                "Identité à vérifier"))))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.status").value("RECEIVED"))
                        .andReturn();
        String requestId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        mvc.perform(
                        post("/api/v1/privacy/exports")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "requestId",
                                                        requestId,
                                                        "subjectType",
                                                        "GUARDIAN",
                                                        "subjectReference",
                                                        "FAM-DEMO-001",
                                                        "scopeSummary",
                                                        "Dossier famille et scolarité liée"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PREPARED"));
        mvc.perform(
                        put("/api/v1/privacy/requests/{id}/status", requestId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "status",
                                                        "FULFILLED",
                                                        "resolutionAction",
                                                        "EXPORTED",
                                                        "notes",
                                                        "Remis après vérification"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolutionAction").value("EXPORTED"));
        mvc.perform(
                        put("/api/v1/privacy/hosting")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "countryCode",
                                                        "MA",
                                                        "regionLabel",
                                                        "Casablanca",
                                                        "providerName",
                                                        "Hébergeur à contractualiser",
                                                        "encryptionAtRest",
                                                        true,
                                                        "encryptionInTransit",
                                                        true,
                                                        "crossBorderTransfer",
                                                        false,
                                                        "legalValidationStatus",
                                                        "PENDING"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encryptionInTransit").value(true));
    }

    @Test
    void teacherIsForbiddenAndForeignTenantRequestIsHidden() throws Exception {
        String teacher = login("prof.math@schoolz.ma", "SchoolZTeacher2026!");
        mvc.perform(get("/api/v1/privacy").header(HttpHeaders.AUTHORIZATION, bearer(teacher)))
                .andExpect(status().isForbidden());

        UUID foreignRequest = insertForeignRequest();
        mvc.perform(
                        put("/api/v1/privacy/requests/{id}/status", foreignRequest)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(Map.of("status", "IN_REVIEW"))))
                .andExpect(status().isBadRequest());
        Integer unchanged =
                jdbc.queryForObject(
                        "SELECT count(*) FROM privacy_data_subject_request WHERE id=? AND"
                                + " status='RECEIVED'",
                        Integer.class,
                        foreignRequest);
        org.assertj.core.api.Assertions.assertThat(unchanged).isEqualTo(1);
    }

    private UUID insertForeignRequest() {
        Instant now = Instant.now();
        IdentityTenant tenant =
                tenants.save(
                        IdentityTenant.create(
                                "privacy-" + UUID.randomUUID().toString().substring(0, 8),
                                "École étrangère",
                                now));
        Role role =
                roles.save(
                        Role.create(
                                tenant.id(),
                                "PRIVACY_TEST",
                                "Privacy test",
                                false,
                                Set.of("privacy.read", "privacy.manage", "privacy.export"),
                                now));
        UserAccount user =
                UserAccount.create(
                        tenant.id(),
                        "privacy@foreign.ma",
                        "Responsable étranger",
                        passwordEncoder.encode("ForeignPrivacy2026!"),
                        "fr-MA",
                        true,
                        now);
        user.assignRoles(Set.of(role), now);
        accounts.save(user);
        UUID request = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO privacy_data_subject_request"
                    + " (id,tenant_id,request_number,request_type,subject_type,subject_reference,status,received_at,due_at,created_by_user_id,updated_by_user_id,created_at,updated_at)"
                    + " VALUES (?,?,?,'ACCESS','STUDENT','FOREIGN-001','RECEIVED',?,?,?,?,?,?)",
                request,
                tenant.id(),
                "PRV-FOREIGN-" + request.toString().substring(0, 8),
                Timestamp.from(now),
                Timestamp.from(now.plus(30, ChronoUnit.DAYS)),
                user.id(),
                user.id(),
                Timestamp.from(now),
                Timestamp.from(now));
        return request;
    }

    private String login(String email, String password) throws Exception {
        MvcResult result =
                mvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "tenantSlug",
                                                                "ecole-demo",
                                                                "email",
                                                                email,
                                                                "password",
                                                                password))))
                        .andExpect(status().isOk())
                        .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private String bearer(String value) {
        return "Bearer " + value;
    }
}
