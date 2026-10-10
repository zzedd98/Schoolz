package ma.schoolz.erp.services;

import static org.assertj.core.api.Assertions.assertThat;
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
import jakarta.servlet.http.Cookie;
import java.util.List;
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
            "spring.datasource.url=jdbc:h2:mem:school-services;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "schoolz.demo.seed-enabled=true",
            "schoolz.demo.tenant-slug=ecole-demo",
            "schoolz.demo.tenant-name=École Démo",
            "schoolz.demo.admin-email=admin@schoolz.ma",
            "schoolz.demo.admin-password=SchoolZDemo2026!",
            "schoolz.demo.teacher-email=prof.math@schoolz.ma",
            "schoolz.demo.teacher-password=SchoolZTeacher2026!",
            "schoolz.demo.student-email=yasmine.eleve@schoolz.ma",
            "schoolz.demo.student-password=SchoolZStudent2026!",
            "schoolz.payments.sandbox.webhook-secret=schoolz-test-services-webhook-secret"
        })
class SchoolServicesApiIntegrationTests {
    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    private MockMvc mvc;
    private String adminToken;
    private Cookie parentCookie;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        adminToken = loginAdmin();
        parentCookie = loginParent();
    }

    @Test
    void transportExposesOperationalTripAndParentTracking() throws Exception {
        mvc.perform(
                        get("/api/v1/transport/dashboard")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicles.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.routes.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.subscriptions.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.trips.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.gpsProviders").value(hasItem("local-demo")));

        mvc.perform(get("/api/v1/public/transport").cookie(parentCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentName").isNotEmpty())
                .andExpect(jsonPath("$.children.length()").value(1))
                .andExpect(jsonPath("$.children[0].latestTrip").exists())
                .andExpect(jsonPath("$.children[0].position.providerKey").value("local-demo"))
                .andExpect(jsonPath("$.children[0].eta.minutes").isNumber());
    }

    @Test
    void cafeteriaShowsOnlyTheAuthenticatedFamilyHealthData() throws Exception {
        mvc.perform(
                        get("/api/v1/cafeteria/dashboard")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plans.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.menus.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.subscriptions.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.attendances.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.restrictions.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.healthDataVisible").value(true));

        mvc.perform(get("/api/v1/public/cafeteria").cookie(parentCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptions.length()").value(1))
                .andExpect(jsonPath("$.healthDataVisible").value(true))
                .andExpect(jsonPath("$.restrictions.length()").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void parentCanEnrollInAnAvailableActivity() throws Exception {
        MvcResult dashboard =
                mvc.perform(get("/api/v1/public/activities").cookie(parentCookie))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.activities.length()").value(5))
                        .andExpect(jsonPath("$.students.length()").value(greaterThanOrEqualTo(1)))
                        .andExpect(jsonPath("$.enrollments.length()").value(1))
                        .andReturn();
        String payload = dashboard.getResponse().getContentAsString();
        List<String> footballIds =
                JsonPath.read(payload, "$.activities[?(@.code == 'FOOTBALL')].id");
        String studentId = JsonPath.read(payload, "$.students[0].id");

        mvc.perform(
                        post("/api/v1/public/activities/enrollments")
                                .cookie(parentCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "activityId",
                                                        footballIds.getFirst(),
                                                        "studentId",
                                                        studentId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ENROLLED"))
                .andExpect(jsonPath("$.enrollmentId").isNotEmpty());

        mvc.perform(get("/api/v1/public/activities").cookie(parentCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrollments.length()").value(2));
    }

    @Test
    void studentHasAnIndependentAcademicPortalAndCanCompleteHomework() throws Exception {
        String studentToken = loginStudent();
        MvcResult dashboard =
                mvc.perform(
                                get("/api/v1/student-portal/dashboard")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(studentToken)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.student.firstName").value("Yasmine"))
                        .andExpect(jsonPath("$.student.studentNumber").value("SZ-2026-0001"))
                        .andExpect(jsonPath("$.lessons.length()").value(greaterThanOrEqualTo(1)))
                        .andExpect(jsonPath("$.homework.length()").value(greaterThanOrEqualTo(1)))
                        .andExpect(
                                jsonPath("$.assessments.length()").value(greaterThanOrEqualTo(1)))
                        .andExpect(jsonPath("$.grades.length()").value(greaterThanOrEqualTo(1)))
                        .andExpect(jsonPath("$.courses.length()").value(greaterThanOrEqualTo(1)))
                        .andReturn();
        String homeworkId =
                JsonPath.read(dashboard.getResponse().getContentAsString(), "$.homework[0].id");

        mvc.perform(
                        get("/api/v1/student-portal/all")
                                .header(HttpHeaders.AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.student.firstName").value("Yasmine"))
                .andExpect(jsonPath("$.homework.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.assessments.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.grades.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.courses.length()").value(greaterThanOrEqualTo(1)));

        mvc.perform(
                        put("/api/v1/student-portal/homework/" + homeworkId + "/status")
                                .header(HttpHeaders.AUTHORIZATION, bearer(studentToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.student.firstName").value("Yasmine"));
    }

    @Test
    void healthModuleRestrictsAndAuditsSensitiveStudentRecords() throws Exception {
        MvcResult dashboard =
                mvc.perform(
                                get("/api/v1/health/dashboard")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                        .andExpect(status().isOk())
                        .andExpect(
                                jsonPath("$.safetyNotice").value(hasItemText("aucun diagnostic")))
                        .andExpect(jsonPath("$.policy.vaccinationTrackingEnabled").value(true))
                        .andExpect(jsonPath("$.students.length()").value(greaterThanOrEqualTo(1)))
                        .andExpect(
                                jsonPath("$.recentIncidents.length()")
                                        .value(greaterThanOrEqualTo(1)))
                        .andReturn();
        List<String> profileStudents =
                JsonPath.read(
                        dashboard.getResponse().getContentAsString(),
                        "$.students[?(@.profileReady == true)].studentId");
        String studentId = profileStudents.getFirst();

        mvc.perform(
                        get("/api/v1/health/students/" + studentId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.id").isNotEmpty())
                .andExpect(jsonPath("$.allergies.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.medications.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.visits.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.vaccinations.length()").value(greaterThanOrEqualTo(1)));

        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM identity_audit_log WHERE action = ?",
                                Integer.class,
                                "health.profile.viewed"))
                .isGreaterThanOrEqualTo(1);

        mvc.perform(
                        get("/api/v1/health/dashboard")
                                .header(HttpHeaders.AUTHORIZATION, bearer(loginStudent())))
                .andExpect(status().isForbidden());
    }

    @Test
    void nurseCanRecordFactsWithoutAnyDiagnosticField() throws Exception {
        String nurseToken = loginNurse();
        MvcResult dashboard =
                mvc.perform(
                                get("/api/v1/health/dashboard")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(nurseToken)))
                        .andExpect(status().isOk())
                        .andReturn();
        String studentId =
                JsonPath.read(
                        dashboard.getResponse().getContentAsString(), "$.students[0].studentId");

        mvc.perform(
                        post("/api/v1/health/students/" + studentId + "/incidents")
                                .header(HttpHeaders.AUTHORIZATION, bearer(nurseToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "incidentType",
                                                        "HEADACHE_REPORTED",
                                                        "severity",
                                                        "LOW",
                                                        "description",
                                                        "L'élève signale une gêne ; aucun autre"
                                                                + " fait observé.",
                                                        "actionTaken",
                                                        "Repos et eau proposés.",
                                                        "status",
                                                        "MONITORING"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.incidentType").value("HEADACHE_REPORTED"))
                .andExpect(jsonPath("$.status").value("MONITORING"));

        mvc.perform(
                        post("/api/v1/health/students/" + studentId + "/visits")
                                .header(HttpHeaders.AUTHORIZATION, bearer(nurseToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "reason",
                                                        "Passage spontané à l'infirmerie",
                                                        "observations",
                                                        "Élève calme et communicant.",
                                                        "temperatureCelsius",
                                                        "36.8",
                                                        "actionTaken",
                                                        "Repos dix minutes.",
                                                        "returnToClass",
                                                        true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.returnToClass").value(true));
    }

    private String loginAdmin() throws Exception {
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
                                                                "admin@schoolz.ma",
                                                                "password",
                                                                "SchoolZDemo2026!"))))
                        .andExpect(status().isOk())
                        .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private Cookie loginParent() throws Exception {
        MvcResult result =
                mvc.perform(
                                post("/api/v1/public/admissions/sessions")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "tenantSlug",
                                                                "ecole-demo",
                                                                "email",
                                                                "parent.demo@schoolz.ma",
                                                                "password",
                                                                "SchoolZParent2026!"))))
                        .andExpect(status().isOk())
                        .andReturn();
        return result.getResponse().getCookie("schoolz_admission");
    }

    private String loginStudent() throws Exception {
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
                                                                "yasmine.eleve@schoolz.ma",
                                                                "password",
                                                                "SchoolZStudent2026!"))))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.user.roles").value(hasItem("STUDENT")))
                        .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private String loginNurse() throws Exception {
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
                                                                "infirmiere.demo@schoolz.ma",
                                                                "password",
                                                                "SchoolZNurse2026!"))))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.user.roles").value(hasItem("NURSE")))
                        .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private static org.hamcrest.Matcher<String> hasItemText(String text) {
        return org.hamcrest.Matchers.containsString(text);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
