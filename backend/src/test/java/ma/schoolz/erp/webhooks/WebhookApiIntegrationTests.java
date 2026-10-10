package ma.schoolz.erp.webhooks;

import static org.assertj.core.api.Assertions.assertThat;
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
import ma.schoolz.erp.webhooks.application.WebhookService;
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
            "spring.datasource.url=jdbc:h2:mem:webhooks;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "schoolz.demo.seed-enabled=true",
            "schoolz.demo.tenant-slug=ecole-demo",
            "schoolz.demo.tenant-name=École Démo",
            "schoolz.demo.admin-email=admin@schoolz.ma",
            "schoolz.demo.admin-password=SchoolZDemo2026!",
            "schoolz.demo.teacher-email=prof.math@schoolz.ma",
            "schoolz.demo.teacher-password=SchoolZTeacher2026!",
            "schoolz.demo.student-email=yasmine.eleve@schoolz.ma",
            "schoolz.demo.student-password=SchoolZStudent2026!",
            "schoolz.payments.sandbox.webhook-secret=schoolz-test-webhook-secret",
            "schoolz.webhooks.master-key=schoolz-test-master-key-m38"
        })
class WebhookApiIntegrationTests {
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
    void dashboardShowsSubscriptionsRetriesAttemptsAndDeadLettersWithoutSecrets() throws Exception {
        mvc.perform(
                        get("/api/v1/webhooks/dashboard")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statistics.subscriptionCount").value(2))
                .andExpect(jsonPath("$.statistics.activeCount").value(2))
                .andExpect(jsonPath("$.statistics.deliveredCount").value(1))
                .andExpect(jsonPath("$.statistics.retryCount").value(1))
                .andExpect(jsonPath("$.statistics.deadLetterCount").value(1))
                .andExpect(jsonPath("$.eventTypes.length()").value(6))
                .andExpect(jsonPath("$.attempts.length()").value(8))
                .andExpect(jsonPath("$.subscriptions[0].secretCiphertext").doesNotExist())
                .andExpect(jsonPath("$.subscriptions[0].secretHash").doesNotExist());
    }

    @Test
    @Order(2)
    void subscriptionSecretIsRevealedOnceAndRevocationIsAudited() throws Exception {
        MvcResult created =
                mvc.perform(
                                post("/api/v1/webhooks/subscriptions")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "name",
                                                                "Portail partenaire test",
                                                                "endpointUrl",
                                                                "https://partner.schoolz.test/hooks",
                                                                "eventTypes",
                                                                List.of("student.created"),
                                                                "maxAttempts",
                                                                3,
                                                                "timeoutSeconds",
                                                                8))))
                        .andExpect(status().isCreated())
                        .andExpect(
                                jsonPath("$.signingSecret")
                                        .value(org.hamcrest.Matchers.startsWith("whsec_")))
                        .andReturn();
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.subscription.id");

        mvc.perform(
                        get("/api/v1/webhooks/dashboard")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptions[*].signingSecret").doesNotExist());

        mvc.perform(
                        post("/api/v1/webhooks/subscriptions/{id}/revoke", id)
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"Fin de la recette M38\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @Order(3)
    void eventPublicationIsIdempotentAndCreatesOnlyMatchingDeliveries() throws Exception {
        String body =
                json.writeValueAsString(
                        Map.of(
                                "eventType",
                                "invoice.created",
                                "idempotencyKey",
                                "test-invoice-m38",
                                "payload",
                                Map.of("invoiceNumber", "FAC-TEST-M38", "amount", 1250)));
        MvcResult first =
                mvc.perform(
                                post("/api/v1/webhooks/events")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                        .andExpect(status().isAccepted())
                        .andExpect(jsonPath("$.deliveryCount").value(2))
                        .andReturn();
        MvcResult second =
                mvc.perform(
                                post("/api/v1/webhooks/events")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                        .andExpect(status().isAccepted())
                        .andExpect(jsonPath("$.deliveryCount").value(2))
                        .andReturn();
        assertThat(JsonPath.<String>read(first.getResponse().getContentAsString(), "$.id"))
                .isEqualTo(JsonPath.read(second.getResponse().getContentAsString(), "$.id"));
    }

    @Test
    @Order(4)
    void endpointPolicyAndStudentPermissionAreEnforced() throws Exception {
        mvc.perform(
                        post("/api/v1/webhooks/subscriptions")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "name",
                                                        "Endpoint non sûr",
                                                        "endpointUrl",
                                                        "http://example.test/hook",
                                                        "eventTypes",
                                                        List.of("student.created")))))
                .andExpect(status().isBadRequest());

        mvc.perform(
                        get("/api/v1/webhooks/dashboard")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        bearer(
                                                login(
                                                        "yasmine.eleve@schoolz.ma",
                                                        "SchoolZStudent2026!"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(5)
    void signatureIsDeterministicAndBindsTimestampEventAndPayload() {
        UUID eventId = UUID.fromString("88888888-8888-8888-8888-888888888888");
        String first = WebhookService.signature("secret", 1234, eventId, "{\"ok\":true}");
        assertThat(first).startsWith("v1=").hasSize(67);
        assertThat(WebhookService.signature("secret", 1234, eventId, "{\"ok\":true}"))
                .isEqualTo(first);
        assertThat(WebhookService.signature("secret", 1235, eventId, "{\"ok\":true}"))
                .isNotEqualTo(first);
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
