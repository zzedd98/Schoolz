package ma.schoolz.erp.library;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ActiveProfiles("dev")
@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:library;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "schoolz.demo.seed-enabled=true",
            "schoolz.demo.tenant-slug=ecole-demo",
            "schoolz.demo.tenant-name=École Démo",
            "schoolz.demo.admin-email=admin@schoolz.ma",
            "schoolz.demo.admin-password=SchoolZDemo2026!",
            "schoolz.demo.teacher-email=prof.math@schoolz.ma",
            "schoolz.demo.teacher-password=SchoolZTeacher2026!",
            "schoolz.demo.student-email=yasmine.eleve@schoolz.ma",
            "schoolz.demo.student-password=SchoolZStudent2026!",
            "schoolz.payments.sandbox.webhook-secret=schoolz-test-library-webhook-secret"
        })
class LibraryApiIntegrationTests {
    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    private MockMvc mvc;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        adminToken = login("admin@schoolz.ma", "SchoolZDemo2026!");
    }

    @Test
    void dashboardExposesCatalogCirculationAndOptionalFines() throws Exception {
        mvc.perform(
                        get("/api/v1/library/dashboard")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configuration.fineEnabled").value(true))
                .andExpect(jsonPath("$.configuration.finePerOverdueDay").value(2.0))
                .andExpect(jsonPath("$.authors.length()").value(greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.categories.length()").value(greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.statistics.books").value(greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.statistics.copies").value(greaterThanOrEqualTo(4)))
                .andExpect(jsonPath("$.statistics.activeLoans").value(greaterThanOrEqualTo(1)))
                .andExpect(
                        jsonPath("$.statistics.activeReservations").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.statistics.openFines").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.fines[?(@.status == 'OPEN')].amount").value(10.0));
    }

    @Test
    void scanFindsTheSameCopyByBarcodeAndQr() throws Exception {
        String copyId = scan("9780156012195-001", "$.id");
        String qrCopyId = scan("SZ-LIB-QR-0001", "$.id");

        org.assertj.core.api.Assertions.assertThat(qrCopyId).isEqualTo(copyId);
    }

    @Test
    void cancellingAReadyReservationPromotesTheOldestWaitingStudent() throws Exception {
        UUID tenantId =
                jdbc.queryForObject(
                        "SELECT id FROM identity_tenant WHERE slug='ecole-demo'", UUID.class);
        List<UUID> students =
                List.of(
                        createStudent(tenantId, "Lecteur", "Un"),
                        createStudent(tenantId, "Lecteur", "Deux"));
        UUID categoryId =
                jdbc.queryForObject(
                        "SELECT id FROM library_category WHERE tenant_id=? ORDER BY code LIMIT 1",
                        UUID.class,
                        tenantId);
        UUID locationId =
                jdbc.queryForObject(
                        "SELECT id FROM inventory_location WHERE tenant_id=? AND code='BIB-01'",
                        UUID.class,
                        tenantId);
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        MvcResult book =
                mvc.perform(
                                post("/api/v1/library/books")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "categoryId",
                                                                categoryId,
                                                                "isbn",
                                                                "TEST-" + suffix,
                                                                "title",
                                                                "Ouvrage de recette " + suffix,
                                                                "languageCode",
                                                                "fr",
                                                                "authorIds",
                                                                List.of()))))
                        .andExpect(status().isCreated())
                        .andReturn();
        String bookId = JsonPath.read(book.getResponse().getContentAsString(), "$.id");
        mvc.perform(
                        post("/api/v1/library/books/" + bookId + "/copies")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "locationId",
                                                        locationId,
                                                        "accessionNumber",
                                                        "REC-" + suffix,
                                                        "barcode",
                                                        "BAR-" + suffix,
                                                        "condition",
                                                        "NEW"))))
                .andExpect(status().isCreated());

        MvcResult ready = reserve(bookId, students.get(0), "READY");
        MvcResult waiting = reserve(bookId, students.get(1), "WAITING");
        String readyReservationId = JsonPath.read(ready.getResponse().getContentAsString(), "$.id");
        String waitingReservationId =
                JsonPath.read(waiting.getResponse().getContentAsString(), "$.id");

        mvc.perform(
                        post("/api/v1/library/reservations/" + readyReservationId + "/cancel")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mvc.perform(
                        get("/api/v1/library/dashboard")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.reservations[?(@.id == '" + waitingReservationId + "')].status")
                                .value("READY"))
                .andExpect(
                        jsonPath(
                                        "$.reservations[?(@.id == '"
                                                + waitingReservationId
                                                + "')].accessionNumber")
                                .value("REC-" + suffix));
    }

    @Test
    void studentAndLinkedParentSeeTheLibraryWithoutInternalPrivileges() throws Exception {
        String studentToken = login("yasmine.eleve@schoolz.ma", "SchoolZStudent2026!");

        mvc.perform(
                        get("/api/v1/student-portal/library")
                                .header(HttpHeaders.AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.borrowers.length()").value(1))
                .andExpect(jsonPath("$.borrowers[0].student.displayName").value("Yasmine Alaoui"))
                .andExpect(
                        jsonPath("$.borrowers[0].loans[?(@.status == 'ACTIVE')].bookTitle")
                                .value("Le Petit Prince"))
                .andExpect(
                        jsonPath("$.borrowers[0].reservations[?(@.status == 'READY')].bookTitle")
                                .value("Mathématiques 1AP — Manipuler et comprendre"));

        mvc.perform(
                        get("/api/v1/library/dashboard")
                                .header(HttpHeaders.AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isForbidden());

        Cookie parentCookie = parentLogin();
        mvc.perform(get("/api/v1/public/library").cookie(parentCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.borrowers.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(
                        jsonPath("$.borrowers[?(@.student.displayName == 'Yasmine Alaoui')].loans")
                                .exists());
    }

    private MvcResult reserve(String bookId, UUID studentId, String expectedStatus)
            throws Exception {
        return mvc.perform(
                        post("/api/v1/library/reservations")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of("bookId", bookId, "studentId", studentId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andReturn();
    }

    private String scan(String code, String path) throws Exception {
        MvcResult result =
                mvc.perform(
                                get("/api/v1/library/copies/scan")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                        .param("code", code))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.bookTitle").value("Le Petit Prince"))
                        .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private UUID createStudent(UUID tenantId, String firstName, String lastName) {
        Instant now = Instant.now();
        UUID personId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO person"
                    + " (id,tenant_id,first_name,last_name,birth_date,status,created_at,updated_at)"
                    + " VALUES (?, ?, ?, ?, '2017-01-01', 'ACTIVE', ?, ?)",
                personId,
                tenantId,
                firstName,
                lastName,
                Timestamp.from(now),
                Timestamp.from(now));
        jdbc.update(
                "INSERT INTO student_profile"
                        + " (id,tenant_id,person_id,status,created_at,updated_at)"
                        + " VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                studentId,
                tenantId,
                personId,
                Timestamp.from(now),
                Timestamp.from(now));
        return studentId;
    }

    private Cookie parentLogin() throws Exception {
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
