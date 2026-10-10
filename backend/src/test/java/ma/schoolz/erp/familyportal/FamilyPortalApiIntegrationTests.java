package ma.schoolz.erp.familyportal;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import ma.schoolz.erp.identity.domain.IdentityTenant;
import ma.schoolz.erp.identity.persistence.IdentityTenantRepository;
import org.junit.jupiter.api.AfterEach;
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
class FamilyPortalApiIntegrationTests {
    private static final String PASSWORD = "SchoolZParent2026!";

    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @Autowired IdentityTenantRepository tenants;

    private MockMvc mvc;
    private UUID tenantId;
    private UUID parentAccountId;
    private UUID enrolledStudentId;
    private UUID secondStudentId;
    private Cookie parentCookie;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanDatabase();
        IdentityTenant tenant =
                tenants.save(IdentityTenant.create("family-a", "Family A", clock.instant()));
        tenantId = tenant.id();
        createAcademicScope();
        parentCookie = registerParent();
        parentAccountId =
                jdbc.queryForObject(
                        "SELECT id FROM admission_portal_account WHERE tenant_id=? AND"
                                + " normalized_email='parent@family-a.ma'",
                        UUID.class,
                        tenantId);
        link(enrolledStudentId);
        link(secondStudentId);
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void familySwitchesBetweenLinkedChildrenAndKeepsItsPreference() throws Exception {
        mvc.perform(get("/api/v1/public/family-portal").cookie(parentCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.email").value("parent@family-a.ma"))
                .andExpect(jsonPath("$.children.length()").value(2))
                .andExpect(jsonPath("$.selectedStudentId").value(enrolledStudentId.toString()))
                .andExpect(jsonPath("$.overview.attendanceToday.status").value("NO_SESSION"))
                .andExpect(jsonPath("$.modules[?(@.code == 'FINANCE')].available").value(true))
                .andExpect(
                        jsonPath("$.modules[?(@.code == 'FINANCE')].targetPath").value("/finance"))
                .andExpect(jsonPath("$.modules[?(@.code == 'HOMEWORK')].available").value(true));

        mvc.perform(
                        put("/api/v1/public/family-portal/preference")
                                .cookie(parentCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of("studentId", secondStudentId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedStudentId").value(secondStudentId.toString()))
                .andExpect(jsonPath("$.modules[?(@.code == 'TIMETABLE')].available").value(false));

        mvc.perform(get("/api/v1/public/family-portal").cookie(parentCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedStudentId").value(secondStudentId.toString()));
    }

    @Test
    void familyCannotReadOrPreferAnUnlinkedStudent() throws Exception {
        UUID unlinkedStudentId = createStudent("Enfant", "Non lie", null, null, null);

        mvc.perform(
                        get("/api/v1/public/family-portal")
                                .cookie(parentCookie)
                                .param("studentId", unlinkedStudentId.toString()))
                .andExpect(status().isNotFound());
        mvc.perform(
                        put("/api/v1/public/family-portal/preference")
                                .cookie(parentCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of("studentId", unlinkedStudentId))))
                .andExpect(status().isNotFound());
        mvc.perform(
                        get(
                                        "/api/v1/public/family-portal/children/{studentId}/report-cards/{reportCardId}/pdf",
                                        unlinkedStudentId,
                                        UUID.randomUUID())
                                .cookie(parentCookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void portalRequiresAParentSessionAndRejectsDatesOutsideTheAllowedWindow() throws Exception {
        mvc.perform(get("/api/v1/public/family-portal")).andExpect(status().isUnauthorized());
        mvc.perform(
                        get("/api/v1/public/family-portal")
                                .cookie(parentCookie)
                                .param("date", LocalDate.now(clock).plusYears(2).toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        get(
                                        "/api/v1/public/family-portal/children/{studentId}/report-cards/{reportCardId}/pdf",
                                        enrolledStudentId,
                                        UUID.randomUUID())
                                .cookie(parentCookie))
                .andExpect(status().isNotFound());
    }

    private void createAcademicScope() {
        Instant now = clock.instant();
        UUID organizationId = UUID.randomUUID();
        UUID campusId = UUID.randomUUID();
        UUID yearId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();
        UUID gradeId = UUID.randomUUID();
        UUID classroomId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO school_organization"
                    + " (id,tenant_id,code,name,country_code,business_identifiers,created_at,updated_at)"
                    + " VALUES (?, ?, 'ORG-F', 'Ecole Famille', 'MA', '{}', ?, ?)",
                organizationId,
                tenantId,
                timestamp(now),
                timestamp(now));
        jdbc.update(
                "INSERT INTO school_campus"
                    + " (id,tenant_id,organization_id,code,name,city,country_code,locale,timezone,"
                    + " active,created_at,updated_at) VALUES (?, ?, ?, 'CAMP-F', 'Campus Rabat',"
                    + " 'Rabat', 'MA', 'fr-MA', 'Africa/Casablanca', TRUE, ?, ?)",
                campusId,
                tenantId,
                organizationId,
                timestamp(now),
                timestamp(now));
        jdbc.update(
                "INSERT INTO academic_year"
                    + " (id,tenant_id,organization_id,code,name,starts_on,ends_on,status,created_at,updated_at)"
                    + " VALUES (?, ?, ?, '2026', '2026-2027', '2026-09-01', '2027-07-15', 'ACTIVE',"
                    + " ?, ?)",
                yearId,
                tenantId,
                organizationId,
                timestamp(now),
                timestamp(now));
        jdbc.update(
                "INSERT INTO school_level"
                    + " (id,tenant_id,organization_id,code,name,sequence_number,active,created_at,updated_at)"
                    + " VALUES (?, ?, ?, 'PRIMARY', 'Primaire', 1, TRUE, ?, ?)",
                levelId,
                tenantId,
                organizationId,
                timestamp(now),
                timestamp(now));
        jdbc.update(
                "INSERT INTO grade_level"
                    + " (id,tenant_id,school_level_id,code,name,sequence_number,active,created_at,updated_at)"
                    + " VALUES (?, ?, ?, 'CE1', 'CE1', 1, TRUE, ?, ?)",
                gradeId,
                tenantId,
                levelId,
                timestamp(now),
                timestamp(now));
        jdbc.update(
                "INSERT INTO classroom"
                    + " (id,tenant_id,campus_id,academic_year_id,grade_level_id,code,name,capacity,"
                    + " active,created_at,updated_at) VALUES (?, ?, ?, ?, ?, 'CE1-A', 'CE1 A', 24,"
                    + " TRUE, ?, ?)",
                classroomId,
                tenantId,
                campusId,
                yearId,
                gradeId,
                timestamp(now),
                timestamp(now));
        enrolledStudentId = createStudent("Yasmine", "Alaoui", campusId, yearId, classroomId);
        secondStudentId = createStudent("Amine", "El Idrissi", null, null, null);
    }

    private UUID createStudent(
            String firstName, String lastName, UUID campusId, UUID yearId, UUID classroomId) {
        Instant now = clock.instant();
        UUID personId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO person"
                    + " (id,tenant_id,first_name,last_name,birth_date,status,created_at,updated_at)"
                    + " VALUES (?, ?, ?, ?, '2017-03-05', 'ACTIVE', ?, ?)",
                personId,
                tenantId,
                firstName,
                lastName,
                timestamp(now),
                timestamp(now));
        jdbc.update(
                "INSERT INTO student_profile"
                        + " (id,tenant_id,person_id,status,created_at,updated_at)"
                        + " VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                studentId,
                tenantId,
                personId,
                timestamp(now),
                timestamp(now));
        if (classroomId != null) {
            jdbc.update(
                    "INSERT INTO student_enrollment"
                        + " (id,tenant_id,student_id,campus_id,academic_year_id,classroom_id,status,"
                        + " enrolled_on,created_at,updated_at) VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE',"
                        + " '2026-09-01', ?, ?)",
                    UUID.randomUUID(),
                    tenantId,
                    studentId,
                    campusId,
                    yearId,
                    classroomId,
                    timestamp(now),
                    timestamp(now));
        }
        return studentId;
    }

    private Cookie registerParent() throws Exception {
        MvcResult result =
                mvc.perform(
                                post("/api/v1/public/admissions/accounts")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "tenantSlug",
                                                                "family-a",
                                                                "email",
                                                                "parent@family-a.ma",
                                                                "password",
                                                                PASSWORD,
                                                                "displayName",
                                                                "Famille Alaoui"))))
                        .andExpect(status().isOk())
                        .andReturn();
        return result.getResponse().getCookie("schoolz_admission");
    }

    private void link(UUID studentId) {
        jdbc.update(
                "INSERT INTO enrollment_portal_student_link"
                        + " (id,tenant_id,portal_account_id,student_id,created_at)"
                        + " VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                tenantId,
                parentAccountId,
                studentId,
                timestamp(clock.instant()));
    }

    private void cleanDatabase() {
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        try {
            for (String table :
                    jdbc.queryForList(
                            "SELECT table_name FROM information_schema.tables WHERE"
                                    + " table_schema='public' AND table_type='BASE TABLE'",
                            String.class)) {
                if (!"flyway_schema_history".equalsIgnoreCase(table)
                        && !"platform_metadata".equalsIgnoreCase(table)) {
                    jdbc.update("DELETE FROM " + table);
                }
            }
        } finally {
            jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }
}
