package ma.schoolz.erp.humanresources;

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
            "spring.datasource.url=jdbc:h2:mem:human-resources;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "schoolz.demo.seed-enabled=true",
            "schoolz.demo.tenant-slug=ecole-demo",
            "schoolz.demo.tenant-name=École Démo",
            "schoolz.demo.admin-email=admin@schoolz.ma",
            "schoolz.demo.admin-password=SchoolZDemo2026!",
            "schoolz.demo.teacher-email=prof.math@schoolz.ma",
            "schoolz.demo.teacher-password=SchoolZTeacher2026!",
            "schoolz.demo.student-email=yasmine.eleve@schoolz.ma",
            "schoolz.demo.student-password=SchoolZStudent2026!",
            "schoolz.payments.sandbox.webhook-secret=schoolz-test-hr-webhook-secret"
        })
class HumanResourcesApiIntegrationTests {
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
    void dashboardExtendsExistingPeopleWithoutDuplicatingThem() throws Exception {
        mvc.perform(
                        get("/api/v1/hr/dashboard")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employees.length()").value(4))
                .andExpect(jsonPath("$.departments.length()").value(3))
                .andExpect(jsonPath("$.positions.length()").value(3))
                .andExpect(jsonPath("$.leaveRequests.length()").value(3))
                .andExpect(jsonPath("$.attendance.length()").value(4))
                .andExpect(jsonPath("$.expiringDocuments").value(1));

        assertThat(
                        jdbc.queryForObject(
                                """
                                SELECT count(*) FROM hr_employee e
                                JOIN staff_profile s ON s.id=e.staff_profile_id
                                    AND s.tenant_id=e.tenant_id
                                WHERE e.person_id=s.person_id
                                """,
                                Integer.class))
                .isEqualTo(4);
    }

    @Test
    void employeeRecordContainsContractScheduleDocumentAndQualification() throws Exception {
        MvcResult dashboard =
                mvc.perform(
                                get("/api/v1/hr/dashboard")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                        .andReturn();
        java.util.List<String> teacherIds =
                JsonPath.read(
                        dashboard.getResponse().getContentAsString(),
                        "$.employees[?(@.employeeNumber == 'DEMO-TEACH-001')].id");
        String teacherId = teacherIds.getFirst();

        mvc.perform(
                        get("/api/v1/hr/employees/" + teacherId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employee.teacherPosition").value(true))
                .andExpect(jsonPath("$.contracts.length()").value(1))
                .andExpect(jsonPath("$.schedules.length()").value(5))
                .andExpect(jsonPath("$.documents.length()").value(1))
                .andExpect(jsonPath("$.qualifications.length()").value(1));
    }

    @Test
    void leaveWorkflowRequiresManagerReviewBeforeDecision() throws Exception {
        MvcResult dashboard =
                mvc.perform(
                                get("/api/v1/hr/dashboard")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                        .andReturn();
        String employeeId =
                JsonPath.read(dashboard.getResponse().getContentAsString(), "$.employees[0].id");

        MvcResult created =
                mvc.perform(
                                post("/api/v1/hr/leave-requests")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "employeeId",
                                                                employeeId,
                                                                "leaveType",
                                                                "ANNUAL",
                                                                "startsOn",
                                                                LocalDate.now().plusDays(45),
                                                                "endsOn",
                                                                LocalDate.now().plusDays(46),
                                                                "workingDays",
                                                                2,
                                                                "reason",
                                                                "Repos planifié"))))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.status").value("REQUESTED"))
                        .andReturn();
        String requestId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mvc.perform(
                        post("/api/v1/hr/leave-requests/" + requestId + "/decision")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(
                        post("/api/v1/hr/leave-requests/" + requestId + "/manager-review")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MANAGER_REVIEW"));

        mvc.perform(
                        post("/api/v1/hr/leave-requests/" + requestId + "/decision")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"decision\":\"APPROVED\",\"comment\":\"Planning"
                                                + " compatible\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM identity_audit_log WHERE action LIKE"
                                        + " 'hr.leave.%'",
                                Integer.class))
                .isGreaterThanOrEqualTo(3);
    }

    @Test
    void attendanceIsUpsertedAndStudentCannotAccessHr() throws Exception {
        MvcResult dashboard =
                mvc.perform(
                                get("/api/v1/hr/dashboard")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                        .andExpect(jsonPath("$.attendance.length()").value(greaterThanOrEqualTo(1)))
                        .andReturn();
        String employeeId =
                JsonPath.read(dashboard.getResponse().getContentAsString(), "$.employees[0].id");

        mvc.perform(
                        post("/api/v1/hr/attendance")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "employeeId",
                                                        employeeId,
                                                        "workDate",
                                                        LocalDate.now(),
                                                        "status",
                                                        "REMOTE",
                                                        "source",
                                                        "ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REMOTE"));

        mvc.perform(
                        get("/api/v1/hr/dashboard")
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
