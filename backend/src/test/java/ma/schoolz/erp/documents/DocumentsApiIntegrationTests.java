package ma.schoolz.erp.documents;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
            "spring.datasource.url=jdbc:h2:mem:documents;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "schoolz.demo.seed-enabled=true",
            "schoolz.demo.tenant-slug=ecole-demo",
            "schoolz.demo.tenant-name=École Démo",
            "schoolz.demo.admin-email=admin@schoolz.ma",
            "schoolz.demo.admin-password=SchoolZDemo2026!",
            "schoolz.demo.teacher-email=prof.math@schoolz.ma",
            "schoolz.demo.teacher-password=SchoolZTeacher2026!",
            "schoolz.payments.sandbox.webhook-secret=schoolz-test-documents-webhook-secret"
        })
class DocumentsApiIntegrationTests {
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
    void seededDmsExposesCategoriesVersionsAndEveryPdfTemplate() throws Exception {
        mvc.perform(
                        get("/api/v1/documents/dashboard")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories.length()").value(10))
                .andExpect(jsonPath("$.documents.length()").value(greaterThanOrEqualTo(6)))
                .andExpect(
                        jsonPath("$.documents[*].versions[0].pdfTemplate")
                                .value(
                                        hasItems(
                                                "SCHOOL_CERTIFICATE",
                                                "RECEIPT",
                                                "INVOICE",
                                                "CERTIFICATE",
                                                "REPORT_CARD",
                                                "CONTRACT")))
                .andExpect(jsonPath("$.storage.contract").value("S3_COMPATIBLE_OBJECT_KEYS"))
                .andExpect(jsonPath("$.storage.download").value("TEMPORARY_SIGNED_URL"));
    }

    @Test
    void familyCanOnlyDownloadSharedDocumentThroughExpiringSignedUrl() throws Exception {
        MvcResult dashboard =
                mvc.perform(get("/api/v1/public/documents").cookie(parentCookie))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.documents.length()").value(greaterThanOrEqualTo(6)))
                        .andReturn();
        String documentId =
                JsonPath.read(dashboard.getResponse().getContentAsString(), "$.documents[0].id");
        MvcResult signed =
                mvc.perform(
                                post("/api/v1/public/documents/{id}/signed-url", documentId)
                                        .cookie(parentCookie))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.maxDownloads").value(3))
                        .andExpect(jsonPath("$.sha256").isNotEmpty())
                        .andReturn();
        String url = JsonPath.read(signed.getResponse().getContentAsString(), "$.url");
        String token = url.substring(url.lastIndexOf('/') + 1);

        for (int attempt = 0; attempt < 3; attempt++) {
            mvc.perform(get("/api/v1/public/documents/downloads/{token}", token))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                    .andExpect(header().exists("X-Content-SHA256"))
                    .andExpect(header().string("Cache-Control", "no-store"));
        }
        mvc.perform(get("/api/v1/public/documents/downloads/{token}", token))
                .andExpect(status().isNotFound());
    }

    @Test
    void administratorGeneratesAndSharesANewVersionedPdf() throws Exception {
        MvcResult admin =
                mvc.perform(
                                get("/api/v1/documents/dashboard")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                        .andExpect(status().isOk())
                        .andReturn();
        String payload = admin.getResponse().getContentAsString();
        List<String> categoryIds =
                JsonPath.read(payload, "$.categories[?(@.code == 'ATTESTATION')].id");
        String categoryId = categoryIds.getFirst();
        String parentId = JsonPath.read(payload, "$.parents[0].id");

        MvcResult generated =
                mvc.perform(
                                post("/api/v1/documents/generated")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.ofEntries(
                                                                Map.entry("categoryId", categoryId),
                                                                Map.entry(
                                                                        "template",
                                                                        "SCHOOL_CERTIFICATE"),
                                                                Map.entry(
                                                                        "title",
                                                                        "Attestation test"),
                                                                Map.entry(
                                                                        "recipient",
                                                                        "Yasmine El Mansouri"),
                                                                Map.entry("reference", "TEST-19"),
                                                                Map.entry("ownerKind", "PARENT"),
                                                                Map.entry(
                                                                        "ownerReferenceId",
                                                                        parentId),
                                                                Map.entry("visibility", "PRIVATE"),
                                                                Map.entry(
                                                                        "fields",
                                                                        Map.of(
                                                                                "Objet",
                                                                                "Test intégration"))))))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.currentVersion").value(1))
                        .andExpect(jsonPath("$.versions[0].sourceKind").value("GENERATED"))
                        .andReturn();
        String documentId = JsonPath.read(generated.getResponse().getContentAsString(), "$.id");

        mvc.perform(
                        put("/api/v1/documents/{id}/family-share", documentId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "portalAccountId",
                                                        parentId,
                                                        "canDownload",
                                                        true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("FAMILY"));

        mvc.perform(get("/api/v1/public/documents").cookie(parentCookie))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.documents[?(@.id == '" + documentId + "')].title")
                                .value("Attestation test"));
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
