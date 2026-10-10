package ma.schoolz.erp.customfields;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
            "spring.datasource.url=jdbc:h2:mem:customfields;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "schoolz.demo.seed-enabled=true",
            "schoolz.demo.tenant-slug=ecole-demo",
            "schoolz.demo.tenant-name=École Démo",
            "schoolz.demo.admin-email=admin@schoolz.ma",
            "schoolz.demo.admin-password=SchoolZDemo2026!",
            "schoolz.demo.teacher-email=prof.math@schoolz.ma",
            "schoolz.demo.teacher-password=SchoolZTeacher2026!",
            "schoolz.demo.student-email=yasmine.eleve@schoolz.ma",
            "schoolz.demo.student-password=SchoolZStudent2026!",
            "schoolz.payments.sandbox.webhook-secret=schoolz-test-custom-fields-secret"
        })
class CustomFieldApiIntegrationTests {
    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper json;

    private MockMvc mvc;
    private String adminToken;
    private String studentId;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        adminToken = login("admin@schoolz.ma", "SchoolZDemo2026!");
        MvcResult result =
                mvc.perform(
                                get("/api/v1/custom-fields/dashboard")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                        .andExpect(status().isOk())
                        .andReturn();
        studentId = JsonPath.read(result.getResponse().getContentAsString(), "$.selectedEntityId");
    }

    @Test
    @Order(1)
    void dashboardShowsTypedDefinitionsValuesAndAvailablePermissions() throws Exception {
        mvc.perform(
                        get("/api/v1/custom-fields/dashboard")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entityType").value("STUDENT"))
                .andExpect(jsonPath("$.statistics.definitionCount").value(4))
                .andExpect(jsonPath("$.statistics.populatedCount").value(4))
                .andExpect(jsonPath("$.definitions.length()").value(4))
                .andExpect(jsonPath("$.values.length()").value(4))
                .andExpect(jsonPath("$.supportedFieldTypes.length()").value(6))
                .andExpect(
                        jsonPath("$.availablePermissions")
                                .value(org.hamcrest.Matchers.hasItem("health.sensitive.read")));
    }

    @Test
    @Order(2)
    void aNumberDefinitionCanBeCreatedAndItsValueRemainsTyped() throws Exception {
        String definitionId =
                createDefinition("locker_number", "Casier", "NUMBER", List.of(), false);

        mvc.perform(
                        put(
                                        "/api/v1/custom-fields/definitions/{id}/entities/{entityId}",
                                        definitionId,
                                        studentId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"value\":42.5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("locker_number"))
                .andExpect(jsonPath("$.value").value(42.5));
    }

    @Test
    @Order(3)
    void selectOptionsAreValidatedServerSide() throws Exception {
        String definitionId =
                createDefinition(
                        "preferred_club",
                        "Club préféré",
                        "SELECT",
                        List.of("Robotique", "Théâtre"),
                        false);

        mvc.perform(
                        put(
                                        "/api/v1/custom-fields/definitions/{id}/entities/{entityId}",
                                        definitionId,
                                        studentId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"value\":\"Football\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Option")));
        mvc.perform(
                        put(
                                        "/api/v1/custom-fields/definitions/{id}/entities/{entityId}",
                                        definitionId,
                                        studentId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"value\":\"Robotique\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("Robotique"));
    }

    @Test
    @Order(4)
    void malformedDefinitionsAndDuplicateKeysAreRejected() throws Exception {
        Map<String, Object> invalid =
                definition("bad_select", "Sélection", "SELECT", List.of("Seule"), false);
        mvc.perform(
                        post("/api/v1/custom-fields/definitions")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());

        Map<String, Object> duplicate =
                definition("custom_reference", "Doublon", "TEXT", List.of(), false);
        mvc.perform(
                        post("/api/v1/custom-fields/definitions")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(duplicate)))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(5)
    void optionalValuesCanBeClearedButRequiredValuesCannot() throws Exception {
        String optional = createDefinition("locker_color", "Couleur", "TEXT", List.of(), false);
        setValue(optional, "Bleu");
        mvc.perform(
                        delete(
                                        "/api/v1/custom-fields/definitions/{id}/entities/{entityId}",
                                        optional,
                                        studentId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNoContent());

        String required = createDefinition("badge_name", "Badge", "TEXT", List.of(), true);
        setValue(required, "Yasmine");
        mvc.perform(
                        delete(
                                        "/api/v1/custom-fields/definitions/{id}/entities/{entityId}",
                                        required,
                                        studentId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(6)
    void aTeacherOnlySeesFieldsAllowedByTheirSpecificReadPermissions() throws Exception {
        String teacher = login("prof.math@schoolz.ma", "SchoolZTeacher2026!");
        mvc.perform(
                        get("/api/v1/custom-fields/dashboard")
                                .header(HttpHeaders.AUTHORIZATION, bearer(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statistics.definitionCount").value(6))
                .andExpect(
                        jsonPath("$.definitions[*].key")
                                .value(
                                        org.hamcrest.Matchers.not(
                                                org.hamcrest.Matchers.hasItem("blood_group"))))
                .andExpect(
                        jsonPath("$.definitions[*].key")
                                .value(
                                        org.hamcrest.Matchers.not(
                                                org.hamcrest.Matchers.hasItem("bus_code"))));
    }

    @Test
    @Order(7)
    void aStudentCannotEnterTheAdministrativeCustomFieldModule() throws Exception {
        mvc.perform(
                        get("/api/v1/custom-fields/dashboard")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        bearer(
                                                login(
                                                        "yasmine.eleve@schoolz.ma",
                                                        "SchoolZStudent2026!"))))
                .andExpect(status().isForbidden());
    }

    private String createDefinition(
            String key, String label, String type, List<String> options, boolean required)
            throws Exception {
        MvcResult result =
                mvc.perform(
                                post("/api/v1/custom-fields/definitions")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        definition(
                                                                key, label, type, options,
                                                                required))))
                        .andExpect(status().isCreated())
                        .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private Map<String, Object> definition(
            String key, String label, String type, List<String> options, boolean required) {
        return Map.ofEntries(
                Map.entry("entityType", "STUDENT"),
                Map.entry("key", key),
                Map.entry("label", label),
                Map.entry("description", "Champ créé par un test métier."),
                Map.entry("type", type),
                Map.entry("options", options),
                Map.entry("required", required),
                Map.entry("displayOrder", 80),
                Map.entry("active", true));
    }

    private void setValue(String definitionId, Object value) throws Exception {
        mvc.perform(
                        put(
                                        "/api/v1/custom-fields/definitions/{id}/entities/{entityId}",
                                        definitionId,
                                        studentId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(Map.of("value", value))))
                .andExpect(status().isOk());
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
