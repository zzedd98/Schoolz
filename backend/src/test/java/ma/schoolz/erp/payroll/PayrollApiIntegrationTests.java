package ma.schoolz.erp.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.util.Map;
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
            "spring.datasource.url=jdbc:h2:mem:payroll;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "schoolz.demo.seed-enabled=true",
            "schoolz.demo.tenant-slug=ecole-demo",
            "schoolz.demo.tenant-name=École Démo",
            "schoolz.demo.admin-email=admin@schoolz.ma",
            "schoolz.demo.admin-password=SchoolZDemo2026!",
            "schoolz.demo.teacher-email=prof.math@schoolz.ma",
            "schoolz.demo.teacher-password=SchoolZTeacher2026!",
            "schoolz.demo.student-email=yasmine.eleve@schoolz.ma",
            "schoolz.demo.student-password=SchoolZStudent2026!",
            "schoolz.payments.sandbox.webhook-secret=schoolz-test-payroll-webhook-secret"
        })
class PayrollApiIntegrationTests {
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
    void dashboardExposesConfigurableDemoWithoutLegalClaim() throws Exception {
        mvc.perform(
                        get("/api/v1/payroll/dashboard")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periods.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.components.length()").value(6))
                .andExpect(jsonPath("$.rules.length()").value(3))
                .andExpect(jsonPath("$.profiles.length()").value(4))
                .andExpect(jsonPath("$.runs.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(
                        jsonPath("$.complianceNotice")
                                .value(
                                        org.hamcrest.Matchers.containsString(
                                                "non validée juridiquement")));

        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM payroll_salary_rule WHERE"
                                        + " legally_validated=TRUE",
                                Integer.class))
                .isZero();
    }

    @Test
    void calculationIsReproducibleAndProducesDetailedPayslips() throws Exception {
        MvcResult periodResult =
                mvc.perform(
                                post("/api/v1/payroll/periods")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "code",
                                                                "2026-09",
                                                                "label",
                                                                "Paie septembre 2026",
                                                                "startsOn",
                                                                LocalDate.of(2026, 9, 1),
                                                                "endsOn",
                                                                LocalDate.of(2026, 9, 30),
                                                                "paymentDate",
                                                                LocalDate.of(2026, 9, 30),
                                                                "currency",
                                                                "MAD",
                                                                "status",
                                                                "OPEN"))))
                        .andExpect(status().isCreated())
                        .andReturn();
        String periodId = JsonPath.read(periodResult.getResponse().getContentAsString(), "$.id");
        MvcResult runResult =
                mvc.perform(
                                post("/api/v1/payroll/runs")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of("periodId", periodId))))
                        .andExpect(status().isCreated())
                        .andReturn();
        String runId = JsonPath.read(runResult.getResponse().getContentAsString(), "$.id");

        mvc.perform(
                        post("/api/v1/payroll/runs/" + runId + "/calculate")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeCount").value(4))
                .andExpect(jsonPath("$.totalGross").value(43000.00));
        mvc.perform(
                        post("/api/v1/payroll/runs/" + runId + "/calculate")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeCount").value(4));

        MvcResult payslips =
                mvc.perform(
                                get("/api/v1/payroll/runs/" + runId + "/payslips")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.length()").value(4))
                        .andReturn();
        String payslipId = JsonPath.read(payslips.getResponse().getContentAsString(), "$[0].id");
        mvc.perform(
                        get("/api/v1/payroll/payslips/" + payslipId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.payslip.netAmount").isNumber());
    }

    @Test
    void legallyValidatedRuleRequiresASourceAndStudentIsForbidden() throws Exception {
        MvcResult dashboard =
                mvc.perform(
                                get("/api/v1/payroll/dashboard")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                        .andReturn();
        String componentId =
                JsonPath.read(dashboard.getResponse().getContentAsString(), "$.components[1].id");

        mvc.perform(
                        post("/api/v1/payroll/rules")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "componentId",
                                                        componentId,
                                                        "code",
                                                        "UNSOURCED",
                                                        "name",
                                                        "Règle sans source",
                                                        "fixedAmount",
                                                        100,
                                                        "legallyValidated",
                                                        true,
                                                        "effectiveFrom",
                                                        LocalDate.of(2026, 1, 1)))))
                .andExpect(status().isBadRequest());

        mvc.perform(
                        get("/api/v1/payroll/dashboard")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        bearer(
                                                login(
                                                        "yasmine.eleve@schoolz.ma",
                                                        "SchoolZStudent2026!"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void finalizedRunProducesIdempotentAccountingExport() throws Exception {
        MvcResult dashboard =
                mvc.perform(
                                get("/api/v1/payroll/dashboard")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                        .andReturn();
        java.util.List<String> runIds =
                JsonPath.read(
                        dashboard.getResponse().getContentAsString(),
                        "$.runs[?(@.periodCode == '2026-08')].id");
        String runId = runIds.getFirst();

        mvc.perform(
                        post("/api/v1/payroll/runs/" + runId + "/finalize")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALIZED"));
        mvc.perform(
                        post("/api/v1/payroll/runs/" + runId + "/accounting-export?format=CSV")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.format").value("CSV"))
                .andExpect(
                        jsonPath("$.sha256")
                                .value(org.hamcrest.Matchers.matchesPattern("[0-9a-f]{64}")))
                .andExpect(
                        jsonPath("$.content")
                                .value(org.hamcrest.Matchers.containsString("compte_debit")));
        mvc.perform(
                        post("/api/v1/payroll/runs/" + runId + "/accounting-export?format=CSV")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());

        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM payroll_accounting_export", Integer.class))
                .isEqualTo(1);
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
                        .andExpect(
                                jsonPath("$.user.roles")
                                        .value(hasItem(org.hamcrest.Matchers.anything())))
                        .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
