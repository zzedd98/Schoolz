package ma.schoolz.erp.calendar;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
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
            "spring.datasource.url=jdbc:h2:mem:schoolcalendar;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "schoolz.demo.seed-enabled=true",
            "schoolz.demo.tenant-slug=ecole-demo",
            "schoolz.demo.tenant-name=École Démo",
            "schoolz.demo.admin-email=admin@schoolz.ma",
            "schoolz.demo.admin-password=SchoolZDemo2026!",
            "schoolz.demo.teacher-email=prof.math@schoolz.ma",
            "schoolz.demo.teacher-password=SchoolZTeacher2026!",
            "schoolz.payments.sandbox.webhook-secret=schoolz-test-calendar-webhook-secret"
        })
class SchoolCalendarApiIntegrationTests {
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
    void calendarExposesEverySchoolEventTypeAndFilters() throws Exception {
        Instant now = Instant.now();
        mvc.perform(
                        get("/api/v1/calendar")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .param("from", now.minus(2, ChronoUnit.DAYS).toString())
                                .param("to", now.plus(60, ChronoUnit.DAYS).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events.length()").value(greaterThanOrEqualTo(6)))
                .andExpect(
                        jsonPath("$.events[*].eventType")
                                .value(
                                        hasItems(
                                                "HOLIDAY",
                                                "EXAM",
                                                "MEETING",
                                                "TRIP",
                                                "ACTIVITY",
                                                "PARENT_TEACHER")));
        mvc.perform(
                        get("/api/v1/calendar")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .param("from", now.minus(2, ChronoUnit.DAYS).toString())
                                .param("to", now.plus(60, ChronoUnit.DAYS).toString())
                                .param("type", "EXAM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events.length()").value(1))
                .andExpect(jsonPath("$.events[0].eventType").value("EXAM"));
    }

    @Test
    void managerCreatesAndSoftCancelsAnEvent() throws Exception {
        Instant start = Instant.now().plus(70, ChronoUnit.DAYS);
        MvcResult created =
                mvc.perform(
                                post("/api/v1/calendar")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "title",
                                                                "Conseil d’établissement",
                                                                "description",
                                                                "Ordre du jour annuel",
                                                                "eventType",
                                                                "MEETING",
                                                                "audienceScope",
                                                                "SCHOOL",
                                                                "startsAt",
                                                                start.toString(),
                                                                "endsAt",
                                                                start.plus(2, ChronoUnit.HOURS)
                                                                        .toString(),
                                                                "allDay",
                                                                false,
                                                                "location",
                                                                "Salle de conférence"))))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.syncStatus").value("LOCAL_ONLY"))
                        .andReturn();
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        mvc.perform(
                        put("/api/v1/calendar/{id}/cancel", id)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void teacherCanReadButCannotManageSchoolCalendar() throws Exception {
        String teacher = login("prof.math@schoolz.ma", "SchoolZTeacher2026!");
        Instant now = Instant.now();
        mvc.perform(
                        get("/api/v1/calendar")
                                .header(HttpHeaders.AUTHORIZATION, bearer(teacher))
                                .param("from", now.minus(1, ChronoUnit.DAYS).toString())
                                .param("to", now.plus(40, ChronoUnit.DAYS).toString()))
                .andExpect(status().isOk());
        mvc.perform(
                        post("/api/v1/calendar")
                                .header(HttpHeaders.AUTHORIZATION, bearer(teacher))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "title",
                                                        "Événement interdit",
                                                        "eventType",
                                                        "MEETING",
                                                        "audienceScope",
                                                        "SCHOOL",
                                                        "startsAt",
                                                        now.plus(3, ChronoUnit.DAYS).toString(),
                                                        "endsAt",
                                                        now.plus(3, ChronoUnit.DAYS)
                                                                .plus(1, ChronoUnit.HOURS)
                                                                .toString(),
                                                        "allDay",
                                                        false))))
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

    private String bearer(String value) {
        return "Bearer " + value;
    }
}
