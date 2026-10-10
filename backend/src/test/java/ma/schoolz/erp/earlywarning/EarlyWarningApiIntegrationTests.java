package ma.schoolz.erp.earlywarning;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:early-warning;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "schoolz.demo.seed-enabled=true",
            "schoolz.demo.tenant-slug=ecole-demo",
            "schoolz.demo.tenant-name=École Démo",
            "schoolz.demo.admin-email=admin@schoolz.ma",
            "schoolz.demo.admin-password=SchoolZDemo2026!",
            "schoolz.demo.teacher-email=prof.math@schoolz.ma",
            "schoolz.demo.teacher-password=SchoolZTeacher2026!",
            "schoolz.demo.student-email=yasmine.eleve@schoolz.ma",
            "schoolz.demo.student-password=SchoolZStudent2026!",
            "schoolz.payments.sandbox.webhook-secret=schoolz-test-early-warning-webhook-secret"
        })
class EarlyWarningApiIntegrationTests {
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
    @Order(1)
    void dashboardExposesRulesSignalsAlertsAndEthicalBoundary() throws Exception {
        mvc.perform(
                        get("/api/v1/early-warning/dashboard")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .param("asOf", "2026-08-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ethicalPolicy.factualOnly").value(true))
                .andExpect(jsonPath("$.statistics.activeRules").value(6))
                .andExpect(jsonPath("$.statistics.studentsObserved").value(3))
                .andExpect(jsonPath("$.statistics.activeAlerts").value(9))
                .andExpect(jsonPath("$.statistics.highAlerts").value(3))
                .andExpect(jsonPath("$.signalDefinitions.length()").value(7))
                .andExpect(jsonPath("$.rules.length()").value(6))
                .andExpect(jsonPath("$.students.length()").value(3))
                .andExpect(jsonPath("$.alerts.length()").value(9));
    }

    @Test
    @Order(2)
    void combinedRuleKeepsBothFactualPiecesOfEvidence() throws Exception {
        mvc.perform(
                        get("/api/v1/early-warning/dashboard")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .param("asOf", "2026-08-10"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                        "$.alerts[?(@.ruleCode == 'ASSIDUITE_ET_NOTES' &&"
                                                + " @.studentName == 'Yasmine"
                                                + " Alaoui')].evidence.length()")
                                .value(2))
                .andExpect(
                        jsonPath("$.alerts[?(@.ruleCode == 'ASSIDUITE_ET_NOTES')].factualSummary")
                                .exists());
    }

    @Test
    @Order(3)
    void reevaluationIsIdempotentAndRefreshesExistingAlerts() throws Exception {
        mvc.perform(
                        post("/api/v1/early-warning/evaluate")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .param("asOf", "2026-08-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statistics.activeAlerts").value(9))
                .andExpect(jsonPath("$.evaluations[0].alertsCreated").value(0))
                .andExpect(jsonPath("$.evaluations[0].alertsRefreshed").value(9));
    }

    @Test
    @Order(4)
    void anAlertCanBeAcknowledgedAndResolvedWithoutDeletingItsEvidence() throws Exception {
        UUID alertId =
                jdbc.queryForObject(
                        """
                        SELECT a.id FROM early_warning_alert a
                        JOIN early_warning_rule r ON r.tenant_id=a.tenant_id AND r.id=a.rule_id
                        WHERE r.code='RETARDS_REPETES' AND a.status='OPEN'
                        ORDER BY a.created_at FETCH FIRST 1 ROW ONLY
                        """,
                        UUID.class);
        mvc.perform(
                        post("/api/v1/early-warning/alerts/{id}/acknowledge", alertId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of("note", "Suivi planifié avec la famille."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.evidence.length()").value(1));
        mvc.perform(
                        post("/api/v1/early-warning/alerts/{id}/resolve", alertId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "note",
                                                        "Les éléments attendus ont été reçus."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(
                        jsonPath("$.resolutionNote").value("Les éléments attendus ont été reçus."));
    }

    @Test
    @Order(5)
    void aManagerCanCreateAFactualRule() throws Exception {
        Map<String, Object> command =
                Map.of(
                        "code",
                        "ABSENCE_15",
                        "name",
                        "Absence au-dessus de quinze pour cent",
                        "description",
                        "Signale un taux d’absence strictement supérieur au seuil.",
                        "severity",
                        "MEDIUM",
                        "matchMode",
                        "ALL",
                        "active",
                        true,
                        "conditions",
                        List.of(
                                Map.of(
                                        "signalType",
                                        "ABSENCE_RATE",
                                        "operator",
                                        "GT",
                                        "threshold",
                                        15)));
        mvc.perform(
                        post("/api/v1/early-warning/rules")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(command)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("ABSENCE_15"))
                .andExpect(jsonPath("$.conditions.length()").value(1));
    }

    @Test
    @Order(6)
    void psychologicalDiagnosticWordingIsRejected() throws Exception {
        Map<String, Object> command =
                Map.of(
                        "code",
                        "INVALID_DIAG",
                        "name",
                        "Diagnostic psychologique",
                        "description",
                        "Détecte un trouble mental.",
                        "severity",
                        "HIGH",
                        "matchMode",
                        "ALL",
                        "active",
                        true,
                        "conditions",
                        List.of(
                                Map.of(
                                        "signalType",
                                        "LATE_COUNT",
                                        "operator",
                                        "GT",
                                        "threshold",
                                        2)));
        mvc.perform(
                        post("/api/v1/early-warning/rules")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(command)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(7)
    void studentCannotReadEarlyWarningData() throws Exception {
        mvc.perform(
                        get("/api/v1/early-warning/dashboard")
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
