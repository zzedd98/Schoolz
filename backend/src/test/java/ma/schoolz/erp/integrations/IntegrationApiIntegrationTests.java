package ma.schoolz.erp.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ActiveProfiles("dev")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:integrations;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "schoolz.demo.seed-enabled=true",
            "schoolz.demo.tenant-slug=ecole-demo",
            "schoolz.demo.tenant-name=École Démo",
            "schoolz.demo.admin-email=admin@schoolz.ma",
            "schoolz.demo.admin-password=SchoolZDemo2026!",
            "schoolz.demo.teacher-email=prof.math@schoolz.ma",
            "schoolz.demo.teacher-password=SchoolZTeacher2026!",
            "schoolz.demo.student-email=yasmine.eleve@schoolz.ma",
            "schoolz.demo.student-password=SchoolZStudent2026!",
            "schoolz.payments.sandbox.webhook-secret=schoolz-test-integration-webhook-secret"
        })
class IntegrationApiIntegrationTests {
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
    void dashboardExposesFiveAdaptersAndAnExplicitMassarCompliancePolicy() throws Exception {
        mvc.perform(
                        get("/api/v1/integrations/dashboard")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statistics.adapterCount").value(5))
                .andExpect(jsonPath("$.statistics.readyAdapters").value(5))
                .andExpect(jsonPath("$.adapters.length()").value(5))
                .andExpect(jsonPath("$.compliance.massarScraping").value(false))
                .andExpect(jsonPath("$.compliance.officialMassarApi").value(false))
                .andExpect(jsonPath("$.jobs[0].fileAvailable").value(true));
    }

    @Test
    @Order(2)
    void aCsvStudentExportCanBeDownloadedWithItsIntegrityHash() throws Exception {
        String jobId = createExport("CSV");

        MvcResult result =
                mvc.perform(
                                get("/api/v1/integrations/jobs/{jobId}/file", jobId)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                        .andExpect(status().isOk())
                        .andExpect(header().exists("X-Content-SHA256"))
                        .andExpect(
                                header().string(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8"))
                        .andReturn();
        byte[] content = result.getResponse().getContentAsByteArray();
        assertThat(content).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        assertThat(new String(content, StandardCharsets.UTF_8))
                .contains("student_identifier;last_name;first_name")
                .contains("Yasmine");
    }

    @Test
    @Order(3)
    void anXlsxStudentExportProducesARealWorkbook() throws Exception {
        String jobId = createExport("XLSX");

        MvcResult result =
                mvc.perform(
                                get("/api/v1/integrations/jobs/{jobId}/file", jobId)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                        .andExpect(status().isOk())
                        .andExpect(
                                header().string(
                                                HttpHeaders.CONTENT_TYPE,
                                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                        .andReturn();
        assertThat(result.getResponse().getContentAsByteArray()).startsWith((byte) 'P', (byte) 'K');
    }

    @Test
    @Order(4)
    void aWellFormedCsvImportIsValidatedButDoesNotMutateStudentData() throws Exception {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "eleves.csv",
                        "text/csv",
                        ("student_identifier;last_name;first_name;birth_date;gender;class_code;academic_year\n"
                             + "SZ-NEW;Benali;Nora;2015-04-12;F;1AP-A;2026-2027\n")
                                .getBytes(StandardCharsets.UTF_8));

        mvc.perform(
                        multipart("/api/v1/integrations/massar-file/imports")
                                .file(file)
                                .param("dataset", "STUDENTS")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDATED"))
                .andExpect(jsonPath("$.rowCount").value(1))
                .andExpect(jsonPath("$.successCount").value(1))
                .andExpect(jsonPath("$.fileAvailable").value(false));
    }

    @Test
    @Order(5)
    void anImportWithAnUnknownSchemaIsRejected() throws Exception {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "eleves.csv",
                        "text/csv",
                        "nom;prenom\nAlaoui;Yasmine\n".getBytes(StandardCharsets.UTF_8));

        mvc.perform(
                        multipart("/api/v1/integrations/massar-file/imports")
                                .file(file)
                                .param("dataset", "STUDENTS")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.detail")
                                .value(org.hamcrest.Matchers.containsString("Colonnes")));
    }

    @Test
    @Order(6)
    void studentsCannotAccessAdministrativeIntegrations() throws Exception {
        mvc.perform(
                        get("/api/v1/integrations/dashboard")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        bearer(
                                                login(
                                                        "yasmine.eleve@schoolz.ma",
                                                        "SchoolZStudent2026!"))))
                .andExpect(status().isForbidden());
    }

    private String createExport(String format) throws Exception {
        MvcResult result =
                mvc.perform(
                                post("/api/v1/integrations/massar-file/exports")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "dataset",
                                                                "STUDENTS",
                                                                "format",
                                                                format))))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("COMPLETED"))
                        .andExpect(jsonPath("$.fileAvailable").value(true))
                        .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
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
