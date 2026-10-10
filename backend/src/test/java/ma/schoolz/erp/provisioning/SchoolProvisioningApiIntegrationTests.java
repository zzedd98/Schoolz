package ma.schoolz.erp.provisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class SchoolProvisioningApiIntegrationTests {

    private static final String KEY =
            "schoolz-test-provisioning-key-at-least-thirty-two-characters";

    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.update("DELETE FROM platform_school_provisioning");
        jdbc.update("DELETE FROM school_campus");
        jdbc.update("DELETE FROM school_settings");
        jdbc.update("DELETE FROM school_organization");
        jdbc.update("DELETE FROM identity_audit_log");
        jdbc.update("DELETE FROM identity_one_time_token");
        jdbc.update("DELETE FROM identity_refresh_session");
        jdbc.update("DELETE FROM identity_user_role");
        jdbc.update("DELETE FROM identity_role_permission");
        jdbc.update("DELETE FROM identity_role");
        jdbc.update("DELETE FROM identity_user_account");
        jdbc.update("DELETE FROM identity_tenant");
    }

    @Test
    void provisionsAnIdempotentReadySchoolAndActivatesItsOwner() throws Exception {
        MvcResult first =
                mvc.perform(
                                post("/api/v1/platform/provisioning/schools")
                                        .header("X-SchoolZ-Provisioning-Key", KEY)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(requestJson()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.created").value(true))
                        .andExpect(jsonPath("$.tenantSlug").value("ecole-atlas"))
                        .andExpect(jsonPath("$.ownerStatus").value("PENDING_VERIFICATION"))
                        .andExpect(jsonPath("$.activationUrl").isNotEmpty())
                        .andReturn();

        mvc.perform(
                        post("/api/v1/platform/provisioning/schools")
                                .header("X-SchoolZ-Provisioning-Key", KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity_tenant", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM school_organization", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM school_campus", Integer.class))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM identity_role WHERE tenant_id=(SELECT id FROM"
                                        + " identity_tenant)",
                                Integer.class))
                .isGreaterThan(10);

        String activationUrl =
                JsonPath.read(first.getResponse().getContentAsString(), "$.activationUrl");
        String token = queryParameter(activationUrl, "token");
        mvc.perform(
                        post("/api/v1/auth/activate-account")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"token":"%s","newPassword":"AtlasSchoolZ!2026"}
                                        """
                                                .formatted(token)))
                .andExpect(status().isNoContent());

        mvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "tenantSlug":"ecole-atlas",
                                          "email":"direction@atlas.ma",
                                          "password":"AtlasSchoolZ!2026"
                                        }
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.roles[0]").value("ORGANIZATION_OWNER"));
    }

    @Test
    void hidesProvisioningEndpointWhenTheOperatorKeyIsInvalid() throws Exception {
        mvc.perform(
                        post("/api/v1/platform/provisioning/schools")
                                .header("X-SchoolZ-Provisioning-Key", "invalid")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson()))
                .andExpect(status().isNotFound());
    }

    private static String queryParameter(String url, String name) {
        String query = URI.create(url).getRawQuery();
        for (String part : query.split("&")) {
            String[] value = part.split("=", 2);
            if (value[0].equals(name)) {
                return URLDecoder.decode(value[1], StandardCharsets.UTF_8);
            }
        }
        throw new IllegalArgumentException("Missing query parameter " + name);
    }

    private static String requestJson() {
        return """
        {
          "idempotencyKey":"school:ecole-atlas:v1",
          "tenantSlug":"ecole-atlas",
          "schoolName":"École Atlas",
          "ownerName":"Nadia El Amrani",
          "ownerEmail":"direction@atlas.ma",
          "locale":"fr-MA",
          "campusName":"Campus principal",
          "city":"Casablanca",
          "countryCode":"MA",
          "timezone":"Africa/Casablanca"
        }
        """;
    }
}
