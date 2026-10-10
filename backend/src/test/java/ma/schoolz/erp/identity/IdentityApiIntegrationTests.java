package ma.schoolz.erp.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.util.Set;
import ma.schoolz.erp.identity.application.AuthenticationService;
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
class IdentityApiIntegrationTests {

    private static final String PASSWORD = "TestSchoolZ!2026";

    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private IdentityTenantRepository tenants;
    @Autowired private RoleRepository roles;
    @Autowired private UserAccountRepository accounts;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private Clock clock;
    @Autowired private AuthenticationService authentication;

    private MockMvc mvc;
    private UserAccount admin;
    private UserAccount outsider;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.update("DELETE FROM imported_legacy_payment");
        jdbc.update("DELETE FROM imported_opening_balance");
        jdbc.update("DELETE FROM teacher_portal_profile");
        jdbc.update("DELETE FROM student_portal_profile");
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

        IdentityTenant first =
                tenants.save(IdentityTenant.create("tenant-a", "Tenant A", clock.instant()));
        IdentityTenant second =
                tenants.save(IdentityTenant.create("tenant-b", "Tenant B", clock.instant()));
        Role administrator =
                roles.save(
                        Role.create(
                                first.id(),
                                "ADMIN",
                                "Administrator",
                                false,
                                Set.of(
                                        "user.read",
                                        "user.write",
                                        "user.disable",
                                        "role.read",
                                        "role.write",
                                        "audit.read"),
                                clock.instant()));
        Role viewer =
                roles.save(
                        Role.create(
                                second.id(), "VIEWER", "Viewer", false, Set.of(), clock.instant()));
        admin =
                UserAccount.create(
                        first.id(),
                        "admin@tenant-a.ma",
                        "Tenant A Admin",
                        passwordEncoder.encode(PASSWORD),
                        "fr-MA",
                        true,
                        clock.instant());
        admin.assignRoles(Set.of(administrator), clock.instant());
        admin = accounts.save(admin);
        outsider =
                UserAccount.create(
                        second.id(),
                        "viewer@tenant-b.ma",
                        "Tenant B Viewer",
                        passwordEncoder.encode(PASSWORD),
                        "fr-MA",
                        true,
                        clock.instant());
        outsider.assignRoles(Set.of(viewer), clock.instant());
        outsider = accounts.save(outsider);
    }

    @Test
    void loginRefreshAndLogoutRotateAndRevokeTheSession() throws Exception {
        MvcResult login =
                mvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(loginJson("tenant-a", "admin@tenant-a.ma")))
                        .andExpect(status().isOk())
                        .andExpect(cookie().httpOnly("schoolz_refresh", true))
                        .andExpect(jsonPath("$.user.tenantId").value(admin.tenantId().toString()))
                        .andReturn();
        String accessToken =
                com.jayway.jsonpath.JsonPath.read(
                        login.getResponse().getContentAsString(), "$.accessToken");
        Cookie refreshCookie = login.getResponse().getCookie("schoolz_refresh");

        mvc.perform(
                        get("/api/v1/auth/me")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@tenant-a.ma"));

        MvcResult refreshed =
                mvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie))
                        .andExpect(status().isOk())
                        .andExpect(cookie().exists("schoolz_refresh"))
                        .andReturn();
        Cookie rotatedCookie = refreshed.getResponse().getCookie("schoolz_refresh");
        assertThat(rotatedCookie.getValue()).isNotEqualTo(refreshCookie.getValue());

        mvc.perform(post("/api/v1/auth/logout").cookie(rotatedCookie))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("schoolz_refresh", 0));
        mvc.perform(post("/api/v1/auth/refresh").cookie(rotatedCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anAuthenticatedUserWithoutPermissionReceivesForbidden() throws Exception {
        String token = login("tenant-b", "viewer@tenant-b.ma");

        mvc.perform(get("/api/v1/admin/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void apiResponsesCarryNoStoreAndBrowserIsolationHeaders() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Cross-Origin-Opener-Policy", "same-origin"))
                .andExpect(header().string("Cross-Origin-Resource-Policy", "same-origin"))
                .andExpect(header().string("X-Permitted-Cross-Domain-Policies", "none"));
    }

    @Test
    void aTenantCannotReadAnAccountFromAnotherTenant() throws Exception {
        String token = login("tenant-a", "admin@tenant-a.ma");

        mvc.perform(
                        get("/api/v1/admin/users/" + outsider.id())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void passwordsArePersistedOnlyAsBcryptHashes() {
        UserAccount stored =
                accounts.findByIdAndTenantId(admin.id(), admin.tenantId()).orElseThrow();

        assertThat(stored.passwordHash()).startsWith("$2");
        assertThat(stored.passwordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, stored.passwordHash())).isTrue();
    }

    @Test
    void anAdministratorCanCreateAndActivateATenantScopedUser() throws Exception {
        String accessToken = login("tenant-a", "admin@tenant-a.ma");
        MvcResult created =
                mvc.perform(
                                post("/api/v1/admin/users")
                                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {
                                                  "email": "new.user@tenant-a.ma",
                                                  "displayName": "New User",
                                                  "locale": "fr-MA",
                                                  "roleCodes": ["ADMIN"]
                                                }
                                                """))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.user.status").value("PENDING_VERIFICATION"))
                        .andReturn();
        String activationPath =
                com.jayway.jsonpath.JsonPath.read(
                        created.getResponse().getContentAsString(), "$.activationPath");
        String activationToken = activationPath.substring(activationPath.indexOf("token=") + 6);

        mvc.perform(
                        post("/api/v1/auth/activate-account")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"token":"%s","newPassword":"Invite!Secure2026"}
                                        """
                                                .formatted(activationToken)))
                .andExpect(status().isNoContent());
        login("tenant-a", "new.user@tenant-a.ma", "Invite!Secure2026");
    }

    @Test
    void teacherAndStudentAccountsAreLinkedToTheirSelectedProfiles() throws Exception {
        Role teacherRole =
                roles.save(
                        Role.create(
                                admin.tenantId(),
                                "TEACHER",
                                "Teacher",
                                true,
                                Set.of("teacher-portal.read"),
                                clock.instant()));
        Role studentRole =
                roles.save(
                        Role.create(
                                admin.tenantId(),
                                "STUDENT",
                                "Student",
                                true,
                                Set.of("student-portal.read"),
                                clock.instant()));
        assertThat(teacherRole).isNotNull();
        assertThat(studentRole).isNotNull();

        java.util.UUID teacherPersonId = java.util.UUID.randomUUID();
        java.util.UUID studentPersonId = java.util.UUID.randomUUID();
        java.util.UUID staffProfileId = java.util.UUID.randomUUID();
        java.util.UUID studentProfileId = java.util.UUID.randomUUID();
        jdbc.update(
                "INSERT INTO person"
                    + " (id,tenant_id,first_name,last_name,status,created_at,updated_at,version)"
                    + " VALUES (?,?,?,?,?,?,?,0)",
                teacherPersonId,
                admin.tenantId(),
                "Samira",
                "Bennani",
                "ACTIVE",
                clock.instant(),
                clock.instant());
        jdbc.update(
                "INSERT INTO person"
                    + " (id,tenant_id,first_name,last_name,status,created_at,updated_at,version)"
                    + " VALUES (?,?,?,?,?,?,?,0)",
                studentPersonId,
                admin.tenantId(),
                "Youssef",
                "Alaoui",
                "ACTIVE",
                clock.instant(),
                clock.instant());
        jdbc.update(
                "INSERT INTO staff_profile"
                    + " (id,tenant_id,person_id,employee_number,job_title,employment_status,hired_on,created_at,updated_at,version)"
                    + " VALUES (?,?,?,?,?,?,?,?,?,0)",
                staffProfileId,
                admin.tenantId(),
                teacherPersonId,
                "ENS-TEST",
                "Professeur",
                "ACTIVE",
                java.time.LocalDate.now(),
                clock.instant(),
                clock.instant());
        jdbc.update(
                "INSERT INTO student_profile"
                        + " (id,tenant_id,person_id,status,created_at,updated_at,version) VALUES"
                        + " (?,?,?,?,?,?,0)",
                studentProfileId,
                admin.tenantId(),
                studentPersonId,
                "ACTIVE",
                clock.instant(),
                clock.instant());

        String accessToken = login("tenant-a", "admin@tenant-a.ma");
        mvc.perform(
                        post("/api/v1/admin/users")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "email":"teacher@tenant-a.ma",
                                          "displayName":"Samira Bennani",
                                          "locale":"fr-MA",
                                          "roleCodes":["TEACHER"],
                                          "staffProfileId":"%s"
                                        }
                                        """
                                                .formatted(staffProfileId)))
                .andExpect(status().isCreated());
        mvc.perform(
                        post("/api/v1/admin/users")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "email":"student@tenant-a.ma",
                                          "displayName":"Youssef Alaoui",
                                          "locale":"fr-MA",
                                          "roleCodes":["STUDENT"],
                                          "studentId":"%s"
                                        }
                                        """
                                                .formatted(studentProfileId)))
                .andExpect(status().isCreated());

        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM teacher_portal_profile WHERE"
                                        + " staff_profile_id=?",
                                Integer.class,
                                staffProfileId))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM student_portal_profile WHERE student_id=?",
                                Integer.class,
                                studentProfileId))
                .isEqualTo(1);
    }

    @Test
    void passwordResetRevokesSessionsAndRejectsThePreviousPassword() throws Exception {
        String resetToken =
                authentication.requestPasswordReset("tenant-a", "admin@tenant-a.ma").orElseThrow();

        mvc.perform(
                        post("/api/v1/auth/reset-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "token": "%s",
                                          "newPassword": "Replacement!2026"
                                        }
                                        """
                                                .formatted(resetToken)))
                .andExpect(status().isNoContent());
        mvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginJson("tenant-a", "admin@tenant-a.ma", PASSWORD)))
                .andExpect(status().isUnauthorized());
        login("tenant-a", "admin@tenant-a.ma", "Replacement!2026");
    }

    @Test
    void replayingARotatedRefreshTokenRevokesItsWholeFamily() throws Exception {
        MvcResult login =
                mvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(loginJson("tenant-a", "admin@tenant-a.ma")))
                        .andExpect(status().isOk())
                        .andReturn();
        Cookie original = login.getResponse().getCookie("schoolz_refresh");
        MvcResult rotated =
                mvc.perform(post("/api/v1/auth/refresh").cookie(original))
                        .andExpect(status().isOk())
                        .andReturn();
        Cookie current = rotated.getResponse().getCookie("schoolz_refresh");

        mvc.perform(post("/api/v1/auth/refresh").cookie(original))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").cookie(current))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void fiveInvalidPasswordsPersistAProgressiveAccountLock() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            mvc.perform(
                            post("/api/v1/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            loginJson(
                                                    "tenant-a",
                                                    "admin@tenant-a.ma",
                                                    "WrongPassword!2026")))
                    .andExpect(status().isUnauthorized());
        }

        UserAccount locked =
                accounts.findByIdAndTenantId(admin.id(), admin.tenantId()).orElseThrow();
        assertThat(locked.failedLoginAttempts()).isEqualTo(5);
        assertThat(locked.locked(clock.instant())).isTrue();
        mvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginJson("tenant-a", "admin@tenant-a.ma")))
                .andExpect(status().isUnauthorized());
    }

    private String login(String tenant, String email) throws Exception {
        return login(tenant, email, PASSWORD);
    }

    private String login(String tenant, String email, String password) throws Exception {
        MvcResult result =
                mvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(loginJson(tenant, email, password)))
                        .andExpect(status().isOk())
                        .andReturn();
        return com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.accessToken");
    }

    private static String loginJson(String tenant, String email) {
        return loginJson(tenant, email, PASSWORD);
    }

    private static String loginJson(String tenant, String email, String password) {
        return """
        {
          "tenantSlug": "%s",
          "email": "%s",
          "password": "%s"
        }
        """
                .formatted(tenant, email, password);
    }
}
