package ma.schoolz.erp.organization;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.util.Set;
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
class OrganizationApiIntegrationTests {

    private static final String PASSWORD = "TestSchoolZ!2026";
    private static final Set<String> MANAGEMENT_PERMISSIONS =
            Set.of(
                    "organization.read",
                    "organization.write",
                    "campus.read",
                    "campus.write",
                    "academic-structure.read",
                    "academic-structure.write");

    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private IdentityTenantRepository tenants;
    @Autowired private RoleRepository roles;
    @Autowired private UserAccountRepository accounts;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private Clock clock;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanDatabase();
        createUser("group-a", "Group A", "admin@group-a.ma", MANAGEMENT_PERMISSIONS);
        createUser("group-b", "Group B", "admin@group-b.ma", MANAGEMENT_PERMISSIONS);
        createUser("group-view", "Viewer Group", "viewer@group-view.ma", Set.of());
    }

    @Test
    void oneOrganizationManagesMultipleCampusesFromOneAccount() throws Exception {
        String token = login("group-a", "admin@group-a.ma");
        configureOrganization(token, "GROUP-A", "Groupe Scolaire Atlas");
        configureSettings(token);
        createCampus(token, "CASA", "Campus Casablanca", "Casablanca");
        createCampus(token, "RABAT", "Campus Rabat", "Rabat");

        mvc.perform(get("/api/v1/campuses").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").exists())
                .andExpect(jsonPath("$[1].name").exists());
        mvc.perform(
                        get("/api/v1/organization/settings")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currencyCode").value("MAD"))
                .andExpect(jsonPath("$.timezone").value("Africa/Casablanca"));
    }

    @Test
    void academicStructureCreatesYearTermsLevelsOfferingsRoomsAndClassrooms() throws Exception {
        String token = login("group-a", "admin@group-a.ma");
        configureOrganization(token, "GROUP-A", "Groupe Scolaire Atlas");
        String campusId = createCampus(token, "CASA", "Campus Casablanca", "Casablanca");

        MvcResult year =
                mvc.perform(
                                post("/api/v1/academics/years")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"code":"2026-2027","name":"2026–2027",\
                                                "startsOn":"2026-09-01","endsOn":"2027-06-30"}
                                                """))
                        .andExpect(status().isCreated())
                        .andReturn();
        String yearId = JsonPath.read(year.getResponse().getContentAsString(), "$.id");
        mvc.perform(
                        post("/api/v1/academics/years/" + yearId + "/terms")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"code":"T1","name":"Trimestre 1","type":"TRIMESTER",\
                                        "sequence":1,"startsOn":"2026-09-01",\
                                        "endsOn":"2026-12-19"}
                                        """))
                .andExpect(status().isCreated());
        mvc.perform(
                        patch("/api/v1/academics/years/" + yearId + "/status")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        MvcResult level =
                mvc.perform(
                                post("/api/v1/academics/levels")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"code\":\"PRIMARY\",\"name\":\"Primaire\",\"sequence\":1}"))
                        .andExpect(status().isCreated())
                        .andReturn();
        String levelId = JsonPath.read(level.getResponse().getContentAsString(), "$.id");
        MvcResult grade =
                mvc.perform(
                                post("/api/v1/academics/levels/" + levelId + "/grades")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"code\":\"1AP\",\"name\":\"1re année"
                                                        + " primaire\",\"sequence\":1}"))
                        .andExpect(status().isCreated())
                        .andReturn();
        String gradeId = JsonPath.read(grade.getResponse().getContentAsString(), "$.id");
        mvc.perform(
                        put("/api/v1/campuses/" + campusId + "/grade-levels")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"gradeLevelIds\":[\"" + gradeId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offeredGradeLevelIds[0]").value(gradeId));

        MvcResult room =
                mvc.perform(
                                post("/api/v1/academics/rooms")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"campusId":"%s","code":"A101","name":"Salle A101",\
                                                "roomType":"CLASSROOM","capacity":28,"floorLabel":"1"}
                                                """
                                                        .formatted(campusId)))
                        .andExpect(status().isCreated())
                        .andReturn();
        String roomId = JsonPath.read(room.getResponse().getContentAsString(), "$.id");
        mvc.perform(
                        post("/api/v1/academics/classrooms")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"campusId":"%s","academicYearId":"%s",\
                                        "gradeLevelId":"%s","physicalRoomId":"%s",\
                                        "code":"1AP-A","name":"1AP Groupe A","capacity":26}
                                        """
                                                .formatted(campusId, yearId, gradeId, roomId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.campusId").value(campusId));
    }

    @Test
    void tenantScopeBlocksUpdatingAnotherGroupsCampus() throws Exception {
        String firstToken = login("group-a", "admin@group-a.ma");
        String secondToken = login("group-b", "admin@group-b.ma");
        configureOrganization(firstToken, "GROUP-A", "Group A");
        configureOrganization(secondToken, "GROUP-B", "Group B");
        String firstCampus = createCampus(firstToken, "A", "Campus A", "Casablanca");

        mvc.perform(
                        put("/api/v1/campuses/" + firstCampus)
                                .header(HttpHeaders.AUTHORIZATION, bearer(secondToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(campusJson("STOLEN", "Stolen Campus", "Rabat")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/campuses").header(HttpHeaders.AUTHORIZATION, bearer(secondToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void missingManagementPermissionReturnsForbidden() throws Exception {
        String token = login("group-view", "viewer@group-view.ma");
        mvc.perform(get("/api/v1/campuses").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
    }

    private void configureOrganization(String token, String code, String name) throws Exception {
        mvc.perform(
                        put("/api/v1/organization")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"code":"%s","name":"%s","legalName":"%s SARL",\
                                        "addressLine1":"1 avenue Atlas","city":"Casablanca",\
                                        "region":"Casablanca-Settat","postalCode":"20000",\
                                        "countryCode":"MA","phone":"+212522000000",\
                                        "email":"contact@example.ma",\
                                        "businessIdentifiers":{"ICE":"001122334455667"}}
                                        """
                                                .formatted(code, name, name)))
                .andExpect(status().isOk());
    }

    private void configureSettings(String token) throws Exception {
        mvc.perform(
                        put("/api/v1/organization/settings")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"defaultLocale":"fr-MA",\
                                        "supportedLocales":["fr-MA","ar-MA"],\
                                        "currencyCode":"MAD","timezone":"Africa/Casablanca",\
                                        "weekStartsOn":1,"workingDays":["MONDAY","TUESDAY",\
                                        "WEDNESDAY","THURSDAY","FRIDAY"],\
                                        "dayStartsAt":"08:00:00","dayEndsAt":"17:30:00"}
                                        """))
                .andExpect(status().isOk());
    }

    private String createCampus(String token, String code, String name, String city)
            throws Exception {
        MvcResult result =
                mvc.perform(
                                post("/api/v1/campuses")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(campusJson(code, name, city)))
                        .andExpect(status().isCreated())
                        .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private static String campusJson(String code, String name, String city) {
        return """
        {"code":"%s","name":"%s","addressLine1":"2 avenue Atlas",\
        "city":"%s","region":"Casablanca-Settat","postalCode":"20000",\
        "countryCode":"MA","phone":"+212522000000",\
        "email":"campus@example.ma","locale":"fr-MA",\
        "timezone":"Africa/Casablanca"}
        """
                .formatted(code, name, city);
    }

    private String login(String tenant, String email) throws Exception {
        MvcResult result =
                mvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"tenantSlug":"%s","email":"%s",\
                                                "password":"%s"}
                                                """
                                                        .formatted(tenant, email, PASSWORD)))
                        .andExpect(status().isOk())
                        .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private void createUser(String slug, String tenantName, String email, Set<String> permissions) {
        IdentityTenant tenant =
                tenants.save(IdentityTenant.create(slug, tenantName, clock.instant()));
        Role role =
                roles.save(
                        Role.create(
                                tenant.id(),
                                "MANAGER",
                                "Manager",
                                false,
                                permissions,
                                clock.instant()));
        UserAccount account =
                UserAccount.create(
                        tenant.id(),
                        email,
                        tenantName + " User",
                        passwordEncoder.encode(PASSWORD),
                        "fr-MA",
                        true,
                        clock.instant());
        account.assignRoles(Set.of(role), clock.instant());
        accounts.save(account);
    }

    private void cleanDatabase() {
        jdbc.update("DELETE FROM imported_legacy_payment");
        jdbc.update("DELETE FROM imported_opening_balance");
        jdbc.update("DELETE FROM data_import_row");
        jdbc.update("DELETE FROM data_import_job");
        jdbc.update("DELETE FROM student_history");
        jdbc.update("DELETE FROM person_document");
        jdbc.update("DELETE FROM person_custom_field");
        jdbc.update("DELETE FROM student_identifier");
        jdbc.update("DELETE FROM student_enrollment");
        jdbc.update("DELETE FROM emergency_contact");
        jdbc.update("DELETE FROM household_relationship");
        jdbc.update("DELETE FROM guardian_profile");
        jdbc.update("DELETE FROM staff_profile");
        jdbc.update("DELETE FROM student_profile");
        jdbc.update("DELETE FROM person_contact_method");
        jdbc.update("DELETE FROM person_address");
        jdbc.update("DELETE FROM household");
        jdbc.update("DELETE FROM person");
        jdbc.update("DELETE FROM classroom");
        jdbc.update("DELETE FROM physical_room");
        jdbc.update("DELETE FROM campus_grade_offering");
        jdbc.update("DELETE FROM grade_level");
        jdbc.update("DELETE FROM school_level");
        jdbc.update("DELETE FROM academic_term");
        jdbc.update("DELETE FROM academic_year");
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

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
