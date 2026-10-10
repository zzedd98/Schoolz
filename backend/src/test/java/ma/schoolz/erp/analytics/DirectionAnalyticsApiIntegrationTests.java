package ma.schoolz.erp.analytics;

import static org.hamcrest.Matchers.closeTo;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ActiveProfiles("dev")
@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:direction-analytics;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "schoolz.demo.seed-enabled=true",
            "schoolz.demo.tenant-slug=ecole-demo",
            "schoolz.demo.tenant-name=École Démo",
            "schoolz.demo.admin-email=admin@schoolz.ma",
            "schoolz.demo.admin-password=SchoolZDemo2026!",
            "schoolz.demo.teacher-email=prof.math@schoolz.ma",
            "schoolz.demo.teacher-password=SchoolZTeacher2026!",
            "schoolz.demo.student-email=yasmine.eleve@schoolz.ma",
            "schoolz.demo.student-password=SchoolZStudent2026!",
            "schoolz.payments.sandbox.webhook-secret=schoolz-test-analytics-webhook-secret"
        })
class DirectionAnalyticsApiIntegrationTests {
    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    private MockMvc mvc;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        adminToken = login("admin@schoolz.ma", "SchoolZDemo2026!");
    }

    @Test
    void directionDashboardExposesTheExpectedExecutiveKpis() throws Exception {
        mvc.perform(
                        get("/api/v1/analytics/direction")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .param("asOf", "2026-08-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalStudents").value(1284))
                .andExpect(jsonPath("$.summary.presentStudents").value(1216))
                .andExpect(jsonPath("$.summary.absentStudents").value(68))
                .andExpect(jsonPath("$.summary.teachersAbsent").value(3))
                .andExpect(jsonPath("$.summary.expectedRevenue").value(1420000.0))
                .andExpect(jsonPath("$.summary.collectedRevenue").value(1180000.0))
                .andExpect(jsonPath("$.summary.outstandingRevenue").value(240000.0))
                .andExpect(jsonPath("$.summary.newApplications").value(14))
                .andExpect(jsonPath("$.summary.collectionRate").value(closeTo(83.10, 0.01)));
    }

    @Test
    void trendsFunnelsCapacityAndCampusComparisonAreComplete() throws Exception {
        mvc.perform(
                        get("/api/v1/analytics/direction")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .param("asOf", "2026-08-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrollmentTrend.length()").value(12))
                .andExpect(jsonPath("$.collectionTrend.length()").value(12))
                .andExpect(jsonPath("$.absenteeismTrend.length()").value(12))
                .andExpect(jsonPath("$.classDistribution.length()").value(4))
                .andExpect(jsonPath("$.admissionsFunnel.length()").value(5))
                .andExpect(jsonPath("$.revenueByService.length()").value(6))
                .andExpect(jsonPath("$.unpaidAging.length()").value(5))
                .andExpect(jsonPath("$.campusComparison.length()").value(3))
                .andExpect(jsonPath("$.campusComparison[?(@.city == 'Casablanca')]").exists())
                .andExpect(jsonPath("$.campusComparison[?(@.city == 'Rabat')]").exists())
                .andExpect(jsonPath("$.campusComparison[?(@.city == 'Marrakech')]").exists());
    }

    @Test
    void aCampusFilterRecalculatesKpisAndBreakdowns() throws Exception {
        UUID campusId =
                jdbc.queryForObject(
                        "SELECT id FROM school_campus WHERE code='CASA-CENTRE'", UUID.class);
        mvc.perform(
                        get("/api/v1/analytics/direction")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .param("asOf", "2026-08-10")
                                .param("campusId", campusId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedCampusId").value(campusId.toString()))
                .andExpect(jsonPath("$.summary.totalStudents").value(552))
                .andExpect(jsonPath("$.summary.expectedRevenue").value(610000.0))
                .andExpect(jsonPath("$.enrollmentTrend[11].totalStudents").value(552))
                .andExpect(jsonPath("$.classDistribution[0].value").value(123));
    }

    @Test
    void studentCannotOpenDirectionAnalytics() throws Exception {
        mvc.perform(
                        get("/api/v1/analytics/direction")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        bearer(
                                                login(
                                                        "yasmine.eleve@schoolz.ma",
                                                        "SchoolZStudent2026!"))))
                .andExpect(status().isForbidden());
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
