package ma.schoolz.erp.publicapi;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ActiveProfiles("dev")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:publicapi;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "schoolz.demo.seed-enabled=true",
            "schoolz.demo.tenant-slug=ecole-demo",
            "schoolz.demo.tenant-name=École Démo",
            "schoolz.demo.admin-email=admin@schoolz.ma",
            "schoolz.demo.admin-password=SchoolZDemo2026!",
            "schoolz.demo.teacher-email=prof.math@schoolz.ma",
            "schoolz.demo.teacher-password=SchoolZTeacher2026!",
            "schoolz.demo.student-email=yasmine.eleve@schoolz.ma",
            "schoolz.demo.student-password=SchoolZStudent2026!",
            "schoolz.payments.sandbox.webhook-secret=schoolz-test-public-api-secret"
        })
class PublicApiIntegrationTests {
    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper json;

    private MockMvc mvc;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        adminToken = login("admin@schoolz.ma", "SchoolZDemo2026!");
    }

    @Test
    @Order(1)
    void dashboardExposesClientsScopesAndDocumentedResourcesButNeverHashes() throws Exception {
        mvc.perform(
                        get("/api/v1/api-access/dashboard")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statistics.clientCount").value(1))
                .andExpect(jsonPath("$.statistics.activeCount").value(1))
                .andExpect(jsonPath("$.allowedScopes.length()").value(3))
                .andExpect(jsonPath("$.endpoints.length()").value(3))
                .andExpect(jsonPath("$.clients[0].keyPrefix").value("schoolz_demo_api"))
                .andExpect(jsonPath("$.clients[0].keyHash").doesNotExist())
                .andExpect(jsonPath("$.clients[0].apiKey").doesNotExist());
    }

    @Test
    @Order(2)
    void demoKeyReadsStablePaginatedBusinessResources() throws Exception {
        mvc.perform(
                        get("/api/public/v1/students?size=2")
                                .header("X-SchoolZ-Api-Key", "schoolz_demo_api_key_m37_2026"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit", "120"))
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.items[0].firstName").exists())
                .andExpect(jsonPath("$.items[0].personId").doesNotExist());

        mvc.perform(
                        get("/api/public/v1/invoices")
                                .header("X-SchoolZ-Api-Key", "schoolz_demo_api_key_m37_2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].invoiceNumber").value("FAC-DEMO-2026-0001"))
                .andExpect(jsonPath("$.items[0].balance").value(1500));

        mvc.perform(
                        get("/api/public/v1/classes")
                                .header("X-SchoolZ-Api-Key", "schoolz_demo_api_key_m37_2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()", org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.items[0].capacity").isNumber());
    }

    @Test
    @Order(3)
    void invalidKeysAndMissingScopesAreRejected() throws Exception {
        mvc.perform(get("/api/public/v1/students").header("X-SchoolZ-Api-Key", "not-a-schoolz-key"))
                .andExpect(status().isUnauthorized());

        String studentOnly = createKey("Student connector", List.of("students:read"), 20);
        mvc.perform(get("/api/public/v1/invoices").header("X-SchoolZ-Api-Key", studentOnly))
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.detail")
                                .value(org.hamcrest.Matchers.containsString("invoices:read")));
    }

    @Test
    @Order(4)
    void rateLimitIsEnforcedAndLogged() throws Exception {
        String limited = createKey("Low quota", List.of("students:read"), 2);
        for (int index = 0; index < 2; index++) {
            mvc.perform(get("/api/public/v1/students?size=1").header("X-SchoolZ-Api-Key", limited))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/public/v1/students?size=1").header("X-SchoolZ-Api-Key", limited))
                .andExpect(status().isTooManyRequests());

        mvc.perform(
                        get("/api/v1/api-access/dashboard")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statistics.limitedCount").value(1))
                .andExpect(
                        jsonPath("$.requestLogs[*].outcome")
                                .value(org.hamcrest.Matchers.hasItem("RATE_LIMITED")));
    }

    @Test
    @Order(5)
    void aGeneratedKeyIsShownOnceAndRevocationIsImmediate() throws Exception {
        MvcResult created =
                mvc.perform(
                                post("/api/v1/api-access/clients")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        command(
                                                                "Temporary connector",
                                                                List.of("classes:read"),
                                                                10))))
                        .andExpect(status().isCreated())
                        .andExpect(
                                jsonPath("$.apiKey")
                                        .value(org.hamcrest.Matchers.startsWith("sz_live_")))
                        .andReturn();
        String apiKey = JsonPath.read(created.getResponse().getContentAsString(), "$.apiKey");
        String clientId = JsonPath.read(created.getResponse().getContentAsString(), "$.client.id");

        mvc.perform(
                        post("/api/v1/api-access/clients/{id}/revoke", clientId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"Fin du test de révocation\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/public/v1/classes").header("X-SchoolZ-Api-Key", apiKey))
                .andExpect(status().isUnauthorized());

        mvc.perform(
                        get("/api/v1/api-access/dashboard")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clients[*].apiKey").doesNotExist());
    }

    @Test
    @Order(6)
    void studentCannotManageServerCredentials() throws Exception {
        mvc.perform(
                        get("/api/v1/api-access/dashboard")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        bearer(
                                                login(
                                                        "yasmine.eleve@schoolz.ma",
                                                        "SchoolZStudent2026!"))))
                .andExpect(status().isForbidden());
    }

    private String createKey(String name, List<String> scopes, int limit) throws Exception {
        MvcResult result =
                mvc.perform(
                                post("/api/v1/api-access/clients")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        command(name, scopes, limit))))
                        .andExpect(status().isCreated())
                        .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.apiKey");
    }

    private Map<String, Object> command(String name, List<String> scopes, int limit) {
        return Map.of(
                "name",
                name,
                "description",
                "Client créé par la recette automatisée.",
                "scopes",
                scopes,
                "rateLimitPerMinute",
                limit);
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

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
