package ma.schoolz.erp.communications;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
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
            "spring.datasource.url=jdbc:h2:mem:notificationcenter;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "schoolz.demo.seed-enabled=true",
            "schoolz.demo.tenant-slug=ecole-demo",
            "schoolz.demo.tenant-name=École Démo",
            "schoolz.demo.admin-email=admin@schoolz.ma",
            "schoolz.demo.admin-password=SchoolZDemo2026!",
            "schoolz.demo.teacher-email=prof.math@schoolz.ma",
            "schoolz.demo.teacher-password=SchoolZTeacher2026!",
            "schoolz.payments.sandbox.webhook-secret=schoolz-test-notifications-webhook-secret"
        })
class NotificationCenterApiIntegrationTests {
    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper json;

    private MockMvc mvc;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        token = login();
    }

    @Test
    void dashboardSupportsReadArchiveFiltersAndMandatoryPreferences() throws Exception {
        MvcResult dashboard =
                mvc.perform(
                                get("/api/v1/notification-center")
                                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.counts.unread").value(2))
                        .andExpect(jsonPath("$.counts.read").value(2))
                        .andExpect(jsonPath("$.items[0].critical").value(true))
                        .andExpect(jsonPath("$.preferences[1].channel").value("EMAIL"))
                        .andExpect(jsonPath("$.preferences[1].mandatory").value(true))
                        .andReturn();
        String notificationId =
                JsonPath.read(dashboard.getResponse().getContentAsString(), "$.items[0].id");

        mvc.perform(
                        patch("/api/v1/notification-center/{id}/read", notificationId)
                                .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("READ"));
        mvc.perform(
                        patch("/api/v1/notification-center/{id}/archive", notificationId)
                                .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ARCHIVED"));
        mvc.perform(
                        get("/api/v1/notification-center?status=ARCHIVED")
                                .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(notificationId));

        mvc.perform(
                        put("/api/v1/notification-center/preferences")
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of("channels", Map.of("EMAIL", false)))))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        put("/api/v1/notification-center/preferences")
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of("channels", Map.of("PUSH", false)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].enabled").value(false));
    }

    @Test
    void validatesPaginationAndStatus() throws Exception {
        mvc.perform(
                        get("/api/v1/notification-center?status=UNKNOWN")
                                .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        get("/api/v1/notification-center?size=51")
                                .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isBadRequest());
    }

    private String login() throws Exception {
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

    private String bearer() {
        return "Bearer " + token;
    }
}
