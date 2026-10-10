package ma.schoolz.erp.businessconfiguration;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
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
            "spring.datasource.url=jdbc:h2:mem:businessconfiguration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "schoolz.demo.seed-enabled=true",
            "schoolz.demo.tenant-slug=ecole-demo",
            "schoolz.demo.tenant-name=École Démo",
            "schoolz.demo.admin-email=admin@schoolz.ma",
            "schoolz.demo.admin-password=SchoolZDemo2026!",
            "schoolz.demo.teacher-email=prof.math@schoolz.ma",
            "schoolz.demo.teacher-password=SchoolZTeacher2026!",
            "schoolz.demo.student-email=yasmine.eleve@schoolz.ma",
            "schoolz.demo.student-password=SchoolZStudent2026!",
            "schoolz.payments.sandbox.webhook-secret=schoolz-test-business-configuration-secret"
        })
class BusinessConfigurationApiIntegrationTests {
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
    void dashboardCoversEveryRequestedBusinessDomain() throws Exception {
        mvc.perform(
                        get("/api/v1/business-configuration/dashboard")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statistics.itemCount").value(14))
                .andExpect(jsonPath("$.statistics.categoryCount").value(10))
                .andExpect(jsonPath("$.statistics.customizedCount").value(0))
                .andExpect(jsonPath("$.categories.length()").value(10))
                .andExpect(
                        jsonPath("$.categories[*].key")
                                .value(
                                        org.hamcrest.Matchers.hasItems(
                                                "ACADEMIC",
                                                "FINANCE",
                                                "ATTENDANCE",
                                                "DISCIPLINE",
                                                "DOCUMENTS",
                                                "NOTIFICATIONS",
                                                "GRADING",
                                                "SECURITY")))
                .andExpect(jsonPath("$.recentRevisions.length()").value(14));
    }

    @Test
    @Order(2)
    void aTypedSettingIsVersionedAndCanBeReset() throws Exception {
        Item lesson = item("schedule.lesson_minutes");
        mvc.perform(
                        put("/api/v1/business-configuration/items/{id}", lesson.id())
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "value",
                                                        75,
                                                        "expectedVersion",
                                                        lesson.version(),
                                                        "reason",
                                                        "Allonger les ateliers du matin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(75))
                .andExpect(jsonPath("$.version").value(2));

        mvc.perform(
                        post("/api/v1/business-configuration/items/{id}/reset", lesson.id())
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "expectedVersion",
                                                        2,
                                                        "reason",
                                                        "Retour au standard de démonstration"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(60))
                .andExpect(jsonPath("$.version").value(3));
    }

    @Test
    @Order(3)
    void constraintsAndOptionsAreEnforcedServerSide() throws Exception {
        Item lesson = item("schedule.lesson_minutes");
        mvc.perform(
                        put("/api/v1/business-configuration/items/{id}", lesson.id())
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "value",
                                                        5,
                                                        "expectedVersion",
                                                        lesson.version(),
                                                        "reason",
                                                        "Valeur volontairement invalide"))))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.detail")
                                .value(org.hamcrest.Matchers.containsString("minimum")));

        Item plans = item("finance.payment_plans");
        mvc.perform(
                        put("/api/v1/business-configuration/items/{id}", plans.id())
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "value",
                                                        List.of("WEEKLY"),
                                                        "expectedVersion",
                                                        plans.version(),
                                                        "reason",
                                                        "Option volontairement invalide"))))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("option")));
    }

    @Test
    @Order(4)
    void optimisticConcurrencyPreventsSilentOverwrites() throws Exception {
        Item channels = item("notifications.channels");
        Map<String, Object> command =
                Map.of(
                        "value",
                        List.of("IN_APP", "EMAIL", "PUSH"),
                        "expectedVersion",
                        channels.version(),
                        "reason",
                        "Activer le push pour la démonstration");
        mvc.perform(
                        put("/api/v1/business-configuration/items/{id}", channels.id())
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(command)))
                .andExpect(status().isOk());
        mvc.perform(
                        put("/api/v1/business-configuration/items/{id}", channels.id())
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(command)))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(5)
    void categoryFilteringReturnsAConsistentSubset() throws Exception {
        mvc.perform(
                        get("/api/v1/business-configuration/dashboard?category=GRADING")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedCategory").value("GRADING"))
                .andExpect(jsonPath("$.statistics.itemCount").value(2))
                .andExpect(jsonPath("$.categories.length()").value(1));
    }

    @Test
    @Order(6)
    void studentAndTeacherCannotEnterTheConfigurationCenter() throws Exception {
        for (String token :
                List.of(
                        login("prof.math@schoolz.ma", "SchoolZTeacher2026!"),
                        login("yasmine.eleve@schoolz.ma", "SchoolZStudent2026!"))) {
            mvc.perform(
                            get("/api/v1/business-configuration/dashboard")
                                    .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isForbidden());
        }
    }

    private Item item(String key) throws Exception {
        MvcResult result =
                mvc.perform(
                                get("/api/v1/business-configuration/dashboard")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                        .andExpect(status().isOk())
                        .andReturn();
        Map<String, Object> body =
                json.readValue(result.getResponse().getContentAsString(), new TypeReference<>() {});
        List<Map<String, Object>> categories = cast(body.get("categories"));
        return categories.stream()
                .flatMap(category -> this.<Map<String, Object>>cast(category.get("items")).stream())
                .filter(candidate -> key.equals(candidate.get("key")))
                .findFirst()
                .map(
                        candidate ->
                                new Item(
                                        String.valueOf(candidate.get("id")),
                                        ((Number) candidate.get("version")).intValue()))
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> cast(Object value) {
        return (List<T>) value;
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

    private record Item(String id, int version) {}
}
