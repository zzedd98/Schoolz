package ma.schoolz.erp.documents;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
            "spring.datasource.url=jdbc:h2:mem:signatures;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "schoolz.demo.seed-enabled=true",
            "schoolz.demo.tenant-slug=ecole-demo",
            "schoolz.demo.tenant-name=École Démo",
            "schoolz.demo.admin-email=admin@schoolz.ma",
            "schoolz.demo.admin-password=SchoolZDemo2026!",
            "schoolz.demo.teacher-email=prof.math@schoolz.ma",
            "schoolz.demo.teacher-password=SchoolZTeacher2026!",
            "schoolz.payments.sandbox.webhook-secret=schoolz-test-signatures-webhook-secret"
        })
class DocumentSignaturesApiIntegrationTests {
    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper json;

    private MockMvc mvc;
    private String adminToken;
    private Cookie parentCookie;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        adminToken = loginInternal();
        parentCookie = loginParent();
    }

    @Test
    void demoShowsSentAndSignedEvidenceWithoutQualifiedClaim() throws Exception {
        mvc.perform(
                        get("/api/v1/document-signatures/dashboard")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflows[*].status").value(hasItems("SENT", "SIGNED")))
                .andExpect(jsonPath("$.providers[0].key").value("local-simple"))
                .andExpect(jsonPath("$.providers[0].qualified").value(false))
                .andExpect(
                        jsonPath("$.legalNotice.assurance").value("SIMPLE_ELECTRONIC_ACCEPTANCE"));
    }

    @Test
    void familyProgressesStrictlyFromSentToSignedAndKeepsVersionEvidence() throws Exception {
        MvcResult admin =
                mvc.perform(
                                get("/api/v1/document-signatures/dashboard")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                        .andExpect(status().isOk())
                        .andReturn();
        String payload = admin.getResponse().getContentAsString();
        List<String> documentIds =
                JsonPath.read(payload, "$.documents[?(@.documentType == 'INVOICE')].id");
        String documentId = documentIds.getFirst();
        String parentId = JsonPath.read(payload, "$.parents[0].id");

        MvcResult created =
                mvc.perform(
                                post("/api/v1/document-signatures")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "documentId",
                                                                documentId,
                                                                "portalAccountId",
                                                                parentId))))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.status").value("GENERATED"))
                        .andReturn();
        String workflowId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        int version = JsonPath.read(created.getResponse().getContentAsString(), "$.versionNumber");

        mvc.perform(
                        post("/api/v1/document-signatures/{id}/send", workflowId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"));

        mvc.perform(
                        post("/api/v1/public/document-signatures/{id}/accept", workflowId)
                                .cookie(parentCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"consent\":true}"))
                .andExpect(status().isBadRequest());

        mvc.perform(
                        post("/api/v1/public/document-signatures/{id}/view", workflowId)
                                .cookie(parentCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VIEWED"));

        mvc.perform(
                        post("/api/v1/public/document-signatures/{id}/accept", workflowId)
                                .cookie(parentCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"consent\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        mvc.perform(
                        post("/api/v1/public/document-signatures/{id}/sign", workflowId)
                                .cookie(parentCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "signatoryName",
                                                        "Amina El Mansouri",
                                                        "providerKey",
                                                        "local-simple",
                                                        "confirmSimpleSignature",
                                                        true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGNED"))
                .andExpect(jsonPath("$.versionNumber").value(version))
                .andExpect(jsonPath("$.providerKey").value("local-simple"))
                .andExpect(jsonPath("$.assuranceLevel").value("SIMPLE_ELECTRONIC_ACCEPTANCE"))
                .andExpect(jsonPath("$.signatoryIp").isNotEmpty())
                .andExpect(jsonPath("$.evidenceSha256").isNotEmpty())
                .andExpect(
                        jsonPath("$.events[*].toStatus")
                                .value(
                                        hasItems(
                                                "GENERATED",
                                                "SENT",
                                                "VIEWED",
                                                "ACCEPTED",
                                                "SIGNED")));
    }

    private String loginInternal() throws Exception {
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

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
