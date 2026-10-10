package ma.schoolz.erp.tasks;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:tasks;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "schoolz.demo.seed-enabled=true",
            "schoolz.demo.tenant-slug=ecole-demo",
            "schoolz.demo.tenant-name=École Démo",
            "schoolz.demo.admin-email=admin@schoolz.ma",
            "schoolz.demo.admin-password=SchoolZDemo2026!",
            "schoolz.demo.teacher-email=prof.math@schoolz.ma",
            "schoolz.demo.teacher-password=SchoolZTeacher2026!",
            "schoolz.payments.sandbox.webhook-secret=schoolz-test-tasks-webhook-secret"
        })
class TaskApiIntegrationTests {
    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper json;

    private MockMvc mvc;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        token = login("admin@schoolz.ma", "SchoolZDemo2026!");
    }

    @Test
    void createsAssignsFiltersAndCompletesManualTask() throws Exception {
        MvcResult dashboard =
                mvc.perform(get("/api/v1/tasks").header(HttpHeaders.AUTHORIZATION, bearer()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.counts.todo").value(greaterThanOrEqualTo(3)))
                        .andExpect(
                                jsonPath("$.tasks[*].title")
                                        .value(hasItem("Relancer la famille Alaoui")))
                        .andReturn();
        String assignee =
                JsonPath.read(dashboard.getResponse().getContentAsString(), "$.assignees[0].id");
        MvcResult created =
                mvc.perform(
                                post("/api/v1/tasks")
                                        .header(HttpHeaders.AUTHORIZATION, bearer())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "title",
                                                                "Préparer le conseil de classe",
                                                                "description",
                                                                "Rassembler les indicateurs.",
                                                                "assigneeUserId",
                                                                assignee,
                                                                "dueDate",
                                                                LocalDate.now()
                                                                        .plusDays(3)
                                                                        .toString(),
                                                                "priority",
                                                                "HIGH"))))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.source").value("MANUAL"))
                        .andReturn();
        String taskId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        mvc.perform(
                        put("/api/v1/tasks/{id}/status", taskId)
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));
        mvc.perform(get("/api/v1/tasks?status=DONE").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks[*].id").value(hasItem(taskId)));
    }

    @Test
    void workflowCreateTaskActionProducesIdempotentOperationalTask() throws Exception {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> payload =
                Map.of(
                        "eventId",
                        eventId,
                        "eventType",
                        "invoice.overdue",
                        "sourceModule",
                        "billing",
                        "payload",
                        Map.of(
                                "days",
                                14,
                                "taskTitle",
                                "Relancer automatiquement la facture test",
                                "priority",
                                "URGENT"));
        mvc.perform(
                        post("/api/v1/workflows/events")
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(greaterThanOrEqualTo(1)));
        mvc.perform(get("/api/v1/tasks").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.tasks[*].title")
                                .value(hasItem("Relancer automatiquement la facture test")))
                .andExpect(jsonPath("$.tasks[*].source").value(hasItem("WORKFLOW")));
    }

    @Test
    void teacherOnlySeesAssignedTasks() throws Exception {
        String teacher = login("prof.math@schoolz.ma", "SchoolZTeacher2026!");
        mvc.perform(get("/api/v1/tasks").header(HttpHeaders.AUTHORIZATION, "Bearer " + teacher))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks.length()").value(1))
                .andExpect(jsonPath("$.tasks[0].title").value("Vérifier le dossier de Yassine"));
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

    private String bearer() {
        return "Bearer " + token;
    }
}
