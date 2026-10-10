package ma.schoolz.erp.people;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.identity.domain.IdentityTenant;
import ma.schoolz.erp.identity.domain.Role;
import ma.schoolz.erp.identity.domain.UserAccount;
import ma.schoolz.erp.identity.persistence.IdentityTenantRepository;
import ma.schoolz.erp.identity.persistence.RoleRepository;
import ma.schoolz.erp.identity.persistence.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class StudentApiIntegrationTests {

    private static final String PASSWORD = "TestSchoolZ!2026";
    private static final Set<String> PEOPLE_PERMISSIONS =
            Set.of("person.read", "person.write", "student.read", "student.write");

    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private IdentityTenantRepository tenants;
    @Autowired private RoleRepository roles;
    @Autowired private UserAccountRepository accounts;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private Clock clock;

    private MockMvc mvc;
    private AcademicScope firstScope;
    private AcademicScope secondScope;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanDatabase();
        IdentityTenant first =
                createUser("people-a", "School A", "admin@people-a.ma", PEOPLE_PERMISSIONS);
        IdentityTenant second =
                createUser("people-b", "School B", "admin@people-b.ma", PEOPLE_PERMISSIONS);
        createUser("people-view", "School View", "viewer@people-view.ma", Set.of());
        firstScope = academicScope(first.id(), "A");
        secondScope = academicScope(second.id(), "B");
    }

    @Test
    void completeStudentJourneyBuildsThe360RecordAndArchivesInsteadOfDeleting() throws Exception {
        String token = login("people-a", "admin@people-a.ma");
        String studentId = createStudent(token, firstScope, "A-0001");

        mvc.perform(
                        get("/api/v1/students")
                                .param("search", "Yasmine")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].schoolIdentifier").value("A-0001"));
        mvc.perform(
                        get("/api/v1/students/page")
                                .param("search", "Yasmine")
                                .param("size", "1")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].schoolIdentifier").value("A-0001"))
                .andExpect(jsonPath("$.page").value(0));
        mvc.perform(
                        get("/api/v1/students/" + studentId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identity.firstName").value("Yasmine"))
                .andExpect(jsonPath("$.guardians[0].relationship").value("MOTHER"))
                .andExpect(jsonPath("$.enrollments[0].classroomName").value("1AP-A"))
                .andExpect(jsonPath("$.attendance.length()").value(0))
                .andExpect(jsonPath("$.grades.length()").value(0))
                .andExpect(jsonPath("$.finance.status").value("PENDING_MODULE"));

        mvc.perform(
                        put("/api/v1/students/" + studentId + "/custom-fields")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fields\":{\"langue_maternelle\":\"Arabe\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customFields.langue_maternelle").value("Arabe"));
        mvc.perform(
                        post("/api/v1/students/" + studentId + "/documents")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"documentType\":\"BIRTH_CERTIFICATE\",\"title\":\"Acte"
                                            + " de naissance\",\"objectKey\":\"students/a/acte.pdf\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("VALID"));
        mvc.perform(
                        patch("/api/v1/students/" + studentId + "/archive")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"Départ vers un autre établissement\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identity.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.enrollments[0].status").value("ENDED"));
        mvc.perform(
                        get("/api/v1/students/" + studentId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history.length()").value(3));
    }

    @Test
    void tenantIsolationHidesAnotherSchoolsStudent() throws Exception {
        String firstToken = login("people-a", "admin@people-a.ma");
        String secondToken = login("people-b", "admin@people-b.ma");
        String studentId = createStudent(firstToken, firstScope, "A-0002");

        mvc.perform(
                        get("/api/v1/students/" + studentId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(secondToken)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/students").header(HttpHeaders.AUTHORIZATION, bearer(secondToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void missingStudentPermissionReturnsForbidden() throws Exception {
        String token = login("people-view", "viewer@people-view.ma");
        mvc.perform(get("/api/v1/students").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
    }

    private String createStudent(String token, AcademicScope scope, String identifier)
            throws Exception {
        MvcResult result =
                mvc.perform(
                                post("/api/v1/students")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(studentJson(scope, identifier)))
                        .andExpect(status().isCreated())
                        .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.identity.id");
    }

    private static String studentJson(AcademicScope scope, String identifier) {
        return """
        {"studentFirstName":"Yasmine","studentLastName":"Alaoui",\
        "preferredName":"Yas","birthDate":"2020-03-14","gender":"FEMALE",\
        "nationalityCode":"MA","schoolIdentifier":"%s",\
        "householdName":"Famille Alaoui","addressLine1":"24 rue Al Massira",\
        "city":"Casablanca","region":"Casablanca-Settat","postalCode":"20250",\
        "countryCode":"MA","guardianFirstName":"Nadia",\
        "guardianLastName":"Alaoui","guardianRelationship":"MOTHER",\
        "guardianEmail":"nadia.%s@example.ma","guardianPhone":"+212612345678",\
        "guardianOccupation":"Architecte","guardianNationalityCode":"MA",\
        "financialResponsible":true,"campusId":"%s","academicYearId":"%s",\
        "classroomId":"%s","enrolledOn":"2026-09-01"}
        """
                .formatted(
                        identifier,
                        identifier.toLowerCase(),
                        scope.campusId(),
                        scope.yearId(),
                        scope.classroomId());
    }

    private String login(String tenant, String email) throws Exception {
        MvcResult result =
                mvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"tenantSlug\":\""
                                                        + tenant
                                                        + "\",\"email\":\""
                                                        + email
                                                        + "\",\"password\":\""
                                                        + PASSWORD
                                                        + "\"}"))
                        .andExpect(status().isOk())
                        .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private IdentityTenant createUser(
            String slug, String name, String email, Set<String> permissions) {
        IdentityTenant tenant = tenants.save(IdentityTenant.create(slug, name, clock.instant()));
        Role role =
                roles.save(
                        Role.create(
                                tenant.id(),
                                "PEOPLE",
                                "People",
                                false,
                                permissions,
                                clock.instant()));
        UserAccount account =
                UserAccount.create(
                        tenant.id(),
                        email,
                        name + " User",
                        passwordEncoder.encode(PASSWORD),
                        "fr-MA",
                        true,
                        clock.instant());
        account.assignRoles(Set.of(role), clock.instant());
        accounts.save(account);
        return tenant;
    }

    private AcademicScope academicScope(UUID tenantId, String suffix) {
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
                    + " VALUES (?, ?, ?, ?, 'MA', '{}', ?, ?)",
                organizationId,
                tenantId,
                "ORG-" + suffix,
                "School " + suffix,
                now,
                now);
        jdbc.update(
                "INSERT INTO school_campus"
                    + " (id,tenant_id,organization_id,code,name,country_code,locale,timezone,active,created_at,updated_at)"
                    + " VALUES (?, ?, ?, ?, ?, 'MA', 'fr-MA', 'Africa/Casablanca', TRUE, ?, ?)",
                campusId,
                tenantId,
                organizationId,
                "CAMPUS-" + suffix,
                "Campus " + suffix,
                now,
                now);
        jdbc.update(
                "INSERT INTO academic_year"
                    + " (id,tenant_id,organization_id,code,name,starts_on,ends_on,status,created_at,updated_at)"
                    + " VALUES (?, ?, ?, '2026-2027', 'Année 2026-2027', '2026-09-01',"
                    + " '2027-06-30', 'ACTIVE', ?, ?)",
                yearId,
                tenantId,
                organizationId,
                now,
                now);
        jdbc.update(
                "INSERT INTO school_level"
                    + " (id,tenant_id,organization_id,code,name,sequence_number,active,created_at,updated_at)"
                    + " VALUES (?, ?, ?, 'PRIMARY', 'Primaire', 1, TRUE, ?, ?)",
                levelId,
                tenantId,
                organizationId,
                now,
                now);
        jdbc.update(
                "INSERT INTO grade_level"
                    + " (id,tenant_id,school_level_id,code,name,sequence_number,active,created_at,updated_at)"
                    + " VALUES (?, ?, ?, '1AP', '1re année primaire', 1, TRUE, ?, ?)",
                gradeId,
                tenantId,
                levelId,
                now,
                now);
        jdbc.update(
                "INSERT INTO classroom"
                    + " (id,tenant_id,campus_id,academic_year_id,grade_level_id,code,name,capacity,active,created_at,updated_at)"
                    + " VALUES (?, ?, ?, ?, ?, ?, '1AP-A', 26, TRUE, ?, ?)",
                classroomId,
                tenantId,
                campusId,
                yearId,
                gradeId,
                "1AP-" + suffix,
                now,
                now);
        return new AcademicScope(campusId, yearId, classroomId);
    }

    private void cleanDatabase() {
        for (String table :
                new String[] {
                    "imported_legacy_payment",
                    "imported_opening_balance",
                    "data_import_row",
                    "data_import_job",
                    "student_history",
                    "person_document",
                    "person_custom_field",
                    "student_identifier",
                    "student_enrollment",
                    "emergency_contact",
                    "household_relationship",
                    "guardian_profile",
                    "staff_profile",
                    "student_profile",
                    "person_contact_method",
                    "person_address",
                    "household",
                    "person",
                    "classroom",
                    "physical_room",
                    "campus_grade_offering",
                    "grade_level",
                    "school_level",
                    "academic_term",
                    "academic_year",
                    "school_campus",
                    "school_settings",
                    "school_organization",
                    "identity_audit_log",
                    "identity_one_time_token",
                    "identity_refresh_session",
                    "identity_user_role",
                    "identity_role_permission",
                    "identity_role",
                    "identity_user_account",
                    "identity_tenant"
                }) {
            jdbc.update("DELETE FROM " + table);
        }
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private record AcademicScope(UUID campusId, UUID yearId, UUID classroomId) {}
}
