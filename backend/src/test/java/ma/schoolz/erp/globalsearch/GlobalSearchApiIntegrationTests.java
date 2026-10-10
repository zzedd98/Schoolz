package ma.schoolz.erp.globalsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import ma.schoolz.erp.globalsearch.application.GlobalSearchService;
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
            "spring.datasource.url=jdbc:h2:mem:globalsearch;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "schoolz.demo.seed-enabled=true",
            "schoolz.demo.tenant-slug=ecole-demo",
            "schoolz.demo.tenant-name=École Démo",
            "schoolz.demo.admin-email=admin@schoolz.ma",
            "schoolz.demo.admin-password=SchoolZDemo2026!",
            "schoolz.demo.teacher-email=prof.math@schoolz.ma",
            "schoolz.demo.teacher-password=SchoolZTeacher2026!",
            "schoolz.demo.student-email=yasmine.eleve@schoolz.ma",
            "schoolz.demo.student-password=SchoolZStudent2026!",
            "schoolz.payments.sandbox.webhook-secret=schoolz-search-test-secret"
        })
class GlobalSearchApiIntegrationTests {
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
    void latinSearchIsAccentTolerantAndReturnsNavigableTypedResults() throws Exception {
        mvc.perform(
                        get("/api/v1/search?q=Yásmine")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.normalizedQuery").value("yasmine"))
                .andExpect(jsonPath("$.items[0].type").value("STUDENT"))
                .andExpect(jsonPath("$.items[0].displayName").value("Yasmine Alaoui"))
                .andExpect(
                        jsonPath("$.items[0].routePath")
                                .value(org.hamcrest.Matchers.startsWith("/app/students/")));
    }

    @Test
    @Order(2)
    void arabicAliasesAndTypeFilterUseTheSamePostgresIndex() throws Exception {
        mvc.perform(
                        get("/api/v1/search?q=فاتورة&type=invoice")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()", org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.items[0].type").value("INVOICE"))
                .andExpect(jsonPath("$.items[0].displayName").value("FAC-DEMO-2026-0001"));
    }

    @Test
    @Order(3)
    void refreshIsExplicitAndSearchIsPaginatedAndBounded() throws Exception {
        mvc.perform(
                        post("/api/v1/search/refresh")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentCount", org.hamcrest.Matchers.greaterThan(5)))
                .andExpect(jsonPath("$.counts.STUDENT").value(4));

        mvc.perform(
                        get("/api/v1/search?q=élève&page=0&size=2")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()", org.hamcrest.Matchers.lessThanOrEqualTo(2)))
                .andExpect(jsonPath("$.size").value(2));
        mvc.perform(
                        get("/api/v1/search?q=ab&size=51")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(4)
    void studentCannotEscapePermissionsAndNormalizationCoversArabicVariants() throws Exception {
        mvc.perform(
                        get("/api/v1/search?q=Yasmine")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        bearer(
                                                login(
                                                        "yasmine.eleve@schoolz.ma",
                                                        "SchoolZStudent2026!"))))
                .andExpect(status().isForbidden());
        assertThat(GlobalSearchService.normalize("إدَارَةُ الـمَدْرَسَة"))
                .isEqualTo("ادارة المدرسة");
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
