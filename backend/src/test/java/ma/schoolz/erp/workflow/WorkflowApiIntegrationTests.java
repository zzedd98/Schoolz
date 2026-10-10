package ma.schoolz.erp.workflow;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ActiveProfiles("dev")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:workflow;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "schoolz.demo.seed-enabled=true",
            "schoolz.demo.tenant-slug=ecole-demo",
            "schoolz.demo.tenant-name=École Démo",
            "schoolz.demo.admin-email=admin@schoolz.ma",
            "schoolz.demo.admin-password=SchoolZDemo2026!",
            "schoolz.demo.teacher-email=prof.math@schoolz.ma",
            "schoolz.demo.teacher-password=SchoolZTeacher2026!",
            "schoolz.demo.student-email=yasmine.eleve@schoolz.ma",
            "schoolz.demo.student-password=SchoolZStudent2026!",
            "schoolz.payments.sandbox.webhook-secret=schoolz-test-workflow-webhook-secret"
        })
class WorkflowApiIntegrationTests {
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
    void dashboardShowsConfigurableWorkflowsExecutionsAndLoopGuard() throws Exception {
        mvc.perform(
                        get("/api/v1/workflows/dashboard")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statistics.workflowCount").value(3))
                .andExpect(jsonPath("$.statistics.activeWorkflows").value(3))
                .andExpect(jsonPath("$.statistics.completedExecutions").value(3))
                .andExpect(jsonPath("$.statistics.loopBlocks").value(1))
                .andExpect(jsonPath("$.statistics.actionsExecuted").value(5))
                .andExpect(jsonPath("$.loopPolicy.ancestryGuard").value(true))
                .andExpect(jsonPath("$.workflows.length()").value(3));
    }

    @Test
    @Order(2)
    void matchingEventRunsThreeActionsAndDuplicateEventIsIdempotent() throws Exception {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> event = event(eventId, "invoice.overdue", Map.of("days", 10));
        mvc.perform(
                        post("/api/v1/workflows/events")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(event)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(1))
                .andExpect(jsonPath("$.executions[0].logs.length()").value(5))
                .andExpect(jsonPath("$.executions[0].logs[2].stepType").value("ACTION"))
                .andExpect(jsonPath("$.executions[0].logs[3].stepType").value("ACTION"))
                .andExpect(jsonPath("$.executions[0].logs[4].stepType").value("ACTION"));
        mvc.perform(
                        post("/api/v1/workflows/events")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(event)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executions.length()").value(1));
    }

    @Test
    @Order(3)
    void aConditionCanSkipTheExecutionWithItsLog() throws Exception {
        mvc.perform(
                        post("/api/v1/workflows/events")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                event(
                                                        UUID.randomUUID(),
                                                        "invoice.overdue",
                                                        Map.of("days", 3)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skipped").value(1))
                .andExpect(jsonPath("$.executions[0].status").value("SKIPPED"));
    }

    @Test
    @Order(4)
    void aManagerCanCreateAndDeactivateAWorkflow() throws Exception {
        Map<String, Object> command =
                Map.of(
                        "code",
                        "DOSSIER_COMPLET",
                        "name",
                        "Suivre un dossier complété",
                        "description",
                        "Crée une tâche administrative après réception des pièces.",
                        "active",
                        true,
                        "maxDepth",
                        5,
                        "trigger",
                        Map.of("eventType", "document.completed", "sourceModule", "documents"),
                        "conditions",
                        List.of(),
                        "actions",
                        List.of(
                                Map.of(
                                        "actionType",
                                        "CREATE_TASK",
                                        "label",
                                        "Créer la vérification",
                                        "configuration",
                                        Map.of())));
        MvcResult result =
                mvc.perform(
                                post("/api/v1/workflows")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json.writeValueAsString(command)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.code").value("DOSSIER_COMPLET"))
                        .andReturn();
        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        mvc.perform(
                        put("/api/v1/workflows/{id}/activation", id)
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @Order(5)
    void invalidEventAndWorkflowAreRejected() throws Exception {
        mvc.perform(
                        post("/api/v1/workflows/events")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                event(UUID.randomUUID(), "INVALID", Map.of()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(6)
    void studentCannotReadTheWorkflowEngine() throws Exception {
        mvc.perform(
                        get("/api/v1/workflows/dashboard")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        bearer(
                                                login(
                                                        "yasmine.eleve@schoolz.ma",
                                                        "SchoolZStudent2026!"))))
                .andExpect(status().isForbidden());
    }

    private Map<String, Object> event(UUID id, String type, Map<String, Object> payload) {
        return Map.of("eventId", id, "eventType", type, "sourceModule", "test", "payload", payload);
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
