package ma.schoolz.erp.communications;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ActiveProfiles("dev")
@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:communications;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "schoolz.demo.seed-enabled=true",
            "schoolz.demo.tenant-slug=ecole-demo",
            "schoolz.demo.tenant-name=École Démo",
            "schoolz.demo.admin-email=admin@schoolz.ma",
            "schoolz.demo.admin-password=SchoolZDemo2026!",
            "schoolz.demo.teacher-email=prof.math@schoolz.ma",
            "schoolz.demo.teacher-password=SchoolZTeacher2026!",
            "schoolz.payments.sandbox.webhook-secret=schoolz-test-communications-webhook-secret"
        })
class CommunicationsApiIntegrationTests {
    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper json;

    private MockMvc mvc;
    private String adminToken;
    private String teacherToken;
    private Cookie parentCookie;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        adminToken = loginInternal("admin@schoolz.ma", "SchoolZDemo2026!");
        teacherToken = loginInternal("prof.math@schoolz.ma", "SchoolZTeacher2026!");
        parentCookie = loginParent();
    }

    @Test
    void seededCockpitsExposeEveryCommunicationTypeAndPreviewDelivery() throws Exception {
        mvc.perform(
                        get("/api/v1/communications/dashboard")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherView").value(false))
                .andExpect(jsonPath("$.announcements.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.conversations.length()").value(1))
                .andExpect(jsonPath("$.templates.length()").value(10));

        mvc.perform(get("/api/v1/public/communications").cookie(parentCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.announcements.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.conversations.length()").value(1))
                .andExpect(
                        jsonPath("$.notifications[*].category")
                                .value(
                                        hasItems(
                                                "ANNOUNCEMENT",
                                                "DIRECT_MESSAGE",
                                                "AUTOMATED_NOTIFICATION")))
                .andExpect(
                        jsonPath("$.notifications[*].deliveries[*].channel")
                                .value(hasItems("IN_APP", "PUSH", "EMAIL", "SMS", "WHATSAPP")))
                .andExpect(
                        jsonPath("$.notifications[*].deliveries[*].status")
                                .value(hasItems("DELIVERED", "PREVIEW", "SKIPPED")));
    }

    @Test
    void teacherCanAddressAssignedClassButNotTheWholeSchool() throws Exception {
        MvcResult dashboard =
                mvc.perform(
                                get("/api/v1/communications/dashboard")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.teacherView").value(true))
                        .andExpect(jsonPath("$.campuses.length()").value(0))
                        .andExpect(jsonPath("$.classrooms.length()").value(1))
                        .andReturn();
        String classroomId =
                JsonPath.read(dashboard.getResponse().getContentAsString(), "$.classrooms[0].id");

        mvc.perform(
                        post("/api/v1/communications/announcements")
                                .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "title", "Annonce école interdite",
                                                        "body",
                                                                "Ce périmètre est réservé à la"
                                                                        + " direction.",
                                                        "audienceScope", "SCHOOL"))))
                .andExpect(status().isBadRequest());

        mvc.perform(
                        post("/api/v1/communications/announcements")
                                .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "title",
                                                        "Travail de la semaine",
                                                        "body",
                                                        "Le plan de travail est" + " disponible.",
                                                        "audienceScope",
                                                        "CLASS",
                                                        "classroomId",
                                                        classroomId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.audienceScope").value("CLASS"))
                .andExpect(jsonPath("$.recipientCount").value(1));
    }

    @Test
    void familyRepliesReadsNotificationsAndControlsWhatsAppPreference() throws Exception {
        MvcResult family =
                mvc.perform(get("/api/v1/public/communications").cookie(parentCookie))
                        .andExpect(status().isOk())
                        .andReturn();
        String payload = family.getResponse().getContentAsString();
        String conversationId = JsonPath.read(payload, "$.conversations[0].id");
        String notificationId = JsonPath.read(payload, "$.notifications[0].id");

        mvc.perform(
                        post(
                                        "/api/v1/public/communications/conversations/{id}/messages",
                                        conversationId)
                                .cookie(parentCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of("body", "Merci, message reçu."))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.messages[-1].senderKind").value("PARENT"));

        mvc.perform(
                        patch(
                                        "/api/v1/public/communications/notifications/{id}/read",
                                        notificationId)
                                .cookie(parentCookie))
                .andExpect(status().isNoContent());

        mvc.perform(
                        put("/api/v1/public/communications/preferences")
                                .cookie(parentCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "preferences",
                                                        List.of(
                                                                Map.of(
                                                                        "category",
                                                                        "DIRECT_MESSAGE",
                                                                        "channel",
                                                                        "WHATSAPP",
                                                                        "enabled",
                                                                        true))))))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                        "$[?(@.category == 'DIRECT_MESSAGE' && @.channel =="
                                                + " 'WHATSAPP')].enabled")
                                .value(true));

        mvc.perform(
                        get("/api/v1/communications/dashboard")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications[0].category").value("DIRECT_MESSAGE"));
    }

    private String loginInternal(String email, String password) throws Exception {
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

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
