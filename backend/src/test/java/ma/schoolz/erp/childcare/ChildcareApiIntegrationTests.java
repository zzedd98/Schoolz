package ma.schoolz.erp.childcare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.identity.domain.IdentityTenant;
import ma.schoolz.erp.identity.domain.Role;
import ma.schoolz.erp.identity.domain.UserAccount;
import ma.schoolz.erp.identity.persistence.IdentityTenantRepository;
import ma.schoolz.erp.identity.persistence.RoleRepository;
import ma.schoolz.erp.identity.persistence.UserAccountRepository;
import org.junit.jupiter.api.AfterEach;
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
class ChildcareApiIntegrationTests {
    private static final String PASSWORD = "SchoolZChildcare2026!";
    private static final String PARENT_PASSWORD = "SchoolZParent2026!";

    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired IdentityTenantRepository tenants;
    @Autowired RoleRepository roles;
    @Autowired UserAccountRepository accounts;

    private MockMvc mvc;
    private UUID tenantId;
    private UUID studentId;
    private UUID authorizedPickupId;
    private UUID prohibitedPickupId;
    private String token;
    private Cookie parentCookie;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanDatabase();
        IdentityTenant tenant =
                tenants.save(IdentityTenant.create("childcare-a", "Childcare A", clock.instant()));
        tenantId = tenant.id();
        Role role =
                roles.save(
                        Role.create(
                                tenantId,
                                "CHILDCARE_TEST",
                                "Childcare test",
                                false,
                                Set.of(
                                        "childcare.read",
                                        "childcare.write",
                                        "childcare.publish",
                                        "childcare.medication"),
                                clock.instant()));
        UserAccount educator =
                UserAccount.create(
                        tenantId,
                        "educator@childcare-a.ma",
                        "Éducatrice Test",
                        passwordEncoder.encode(PASSWORD),
                        "fr-MA",
                        true,
                        clock.instant());
        educator.assignRoles(Set.of(role), clock.instant());
        accounts.save(educator);
        token = login();

        Scope scope = createScope(educator.id());
        studentId = scope.studentId();
        authorizedPickupId = scope.authorizedPickupId();
        prohibitedPickupId = scope.prohibitedPickupId();
        parentCookie = registerParent();
        UUID accountId =
                jdbc.queryForObject(
                        "SELECT id FROM admission_portal_account WHERE tenant_id=? AND"
                                + " normalized_email='parent@childcare-a.ma'",
                        UUID.class,
                        tenantId);
        jdbc.update(
                """
                INSERT INTO enrollment_portal_student_link
                    (id,tenant_id,portal_account_id,student_id,created_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                accountId,
                studentId,
                timestamp(clock.instant()));
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void educatorRecordsDayPublishesAndParentReceivesDailyReport() throws Exception {
        Instant arrival = clock.instant().minusSeconds(3600);
        Instant departure = clock.instant();
        mvc.perform(
                        post("/api/v1/childcare/children/" + studentId + "/arrival")
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(Map.of("at", arrival))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyLog.arrivalAt").isNotEmpty());
        event("MEAL", Map.of("label", "Déjeuner", "outcome", "ALL"));
        event(
                "SLEEP",
                Map.of(
                        "label",
                        "Sieste",
                        "occurredAt",
                        arrival.plusSeconds(1200),
                        "endedAt",
                        arrival.plusSeconds(3000)));
        event("DIAPER", Map.of("label", "Change", "outcome", "CLEAN"));
        event("ACTIVITY", Map.of("label", "Peinture", "outcome", "PARTICIPATED"));
        event("MOOD", Map.of("label", "Humeur", "outcome", "HAPPY"));
        mvc.perform(
                        post("/api/v1/childcare/children/" + studentId + "/departure")
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "at",
                                                        departure,
                                                        "pickupPersonId",
                                                        authorizedPickupId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyLog.pickupPerson").value("Nadia Test"));
        mvc.perform(
                        post("/api/v1/childcare/children/" + studentId + "/publish")
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "date",
                                                        LocalDate.now(clock),
                                                        "summary",
                                                        "Très bonne journée."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyLog.status").value("PUBLISHED"));

        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM identity_audit_log WHERE tenant_id=? AND"
                                        + " action LIKE 'childcare.%'",
                                Integer.class, tenantId))
                .isEqualTo(8);

        mvc.perform(
                        get("/api/v1/public/childcare/reports")
                                .param("date", LocalDate.now(clock).toString())
                                .cookie(parentCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentName").value("Amine Test"))
                .andExpect(jsonPath("$[0].dailyLog.summary").value("Très bonne journée."))
                .andExpect(jsonPath("$[0].dailyLog.events.length()").value(5));
    }

    @Test
    void prohibitedPersonCannotCollectChild() throws Exception {
        mvc.perform(
                        post("/api/v1/childcare/children/" + studentId + "/departure")
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of("pickupPersonId", prohibitedPickupId))))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.detail")
                                .value(org.hamcrest.Matchers.containsString("pas autorisée")));
    }

    @Test
    void medicationIsRejectedWhenCampusPolicyDisablesRecording() throws Exception {
        mvc.perform(
                        post("/api/v1/childcare/children/" + studentId + "/events")
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"eventType":"MEDICATION","medicationName":"Test",
                                         "medicationDose":"1 ml","authorizationReference":"AUTH-1"}
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.detail")
                                .value(org.hamcrest.Matchers.containsString("politique")));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM childcare_daily_event WHERE tenant_id=?",
                                Integer.class,
                                tenantId))
                .isZero();
    }

    private void event(String eventType, Map<String, Object> values) throws Exception {
        java.util.HashMap<String, Object> body = new java.util.HashMap<>(values);
        body.put("eventType", eventType);
        mvc.perform(
                        post("/api/v1/childcare/children/" + studentId + "/events")
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    private Scope createScope(UUID educatorUserId) {
        Instant now = clock.instant();
        UUID organizationId = UUID.randomUUID();
        UUID campusId = UUID.randomUUID();
        UUID educatorPersonId = UUID.randomUUID();
        UUID educatorId = UUID.randomUUID();
        UUID childPersonId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID authorized = UUID.randomUUID();
        UUID prohibited = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO school_organization"
                    + " (id,tenant_id,code,name,country_code,business_identifiers,created_at,updated_at)"
                    + " VALUES (?, ?, 'ORG-C', 'Childcare', 'MA', '{}', ?, ?)",
                organizationId,
                tenantId,
                timestamp(now),
                timestamp(now));
        jdbc.update(
                """
                INSERT INTO school_campus
                    (id,tenant_id,organization_id,code,name,country_code,locale,timezone,
                     active,created_at,updated_at)
                VALUES (?, ?, ?, 'CAMP-C', 'Campus Crèche', 'MA', 'fr-MA',
                        'Africa/Casablanca', TRUE, ?, ?)
                """,
                campusId,
                tenantId,
                organizationId,
                timestamp(now),
                timestamp(now));
        person(educatorPersonId, "Salma", "Test", LocalDate.of(1990, 1, 1), now);
        jdbc.update(
                """
                INSERT INTO staff_profile
                    (id,tenant_id,person_id,employee_number,job_title,employment_status,
                     hired_on,created_at,updated_at)
                VALUES (?, ?, ?, 'EDU-1', 'Éducatrice', 'ACTIVE', '2024-09-01', ?, ?)
                """,
                educatorId,
                tenantId,
                educatorPersonId,
                timestamp(now),
                timestamp(now));
        person(childPersonId, "Amine", "Test", LocalDate.now(clock).minusYears(2), now);
        jdbc.update(
                "INSERT INTO student_profile (id,tenant_id,person_id,status,created_at,updated_at)"
                        + " VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                childId,
                tenantId,
                childPersonId,
                timestamp(now),
                timestamp(now));
        jdbc.update(
                """
                INSERT INTO childcare_policy
                    (id,tenant_id,campus_id,medication_recording_enabled,
                     medication_authorization_required,created_at,updated_at)
                VALUES (?, ?, ?, FALSE, TRUE, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                campusId,
                timestamp(now),
                timestamp(now));
        jdbc.update(
                """
                INSERT INTO childcare_group
                    (id,tenant_id,campus_id,reference_educator_id,code,name,min_age_months,
                     max_age_months,capacity,active,created_at,updated_at)
                VALUES (?, ?, ?, ?, 'PAPILLONS', 'Les Papillons', 18, 36, 14, TRUE, ?, ?)
                """,
                groupId,
                tenantId,
                campusId,
                educatorId,
                timestamp(now),
                timestamp(now));
        jdbc.update(
                """
                INSERT INTO childcare_profile
                    (id,tenant_id,student_id,group_id,expected_arrival_time,
                     expected_departure_time,toilet_stage,active,created_at,updated_at)
                VALUES (?, ?, ?, ?, '08:00', '17:30', 'COUCHES', TRUE, ?, ?)
                """,
                profileId,
                tenantId,
                childId,
                groupId,
                timestamp(now),
                timestamp(now));
        pickup(profileId, authorized, "Nadia Test", "AUTHORIZED", now);
        pickup(profileId, prohibited, "Personne Interdite", "PROHIBITED", now);
        return new Scope(childId, authorized, prohibited, educatorUserId);
    }

    private void person(
            UUID id, String firstName, String lastName, LocalDate birthDate, Instant now) {
        jdbc.update(
                """
                INSERT INTO person
                    (id,tenant_id,first_name,last_name,birth_date,nationality_code,status,
                     created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, 'MA', 'ACTIVE', ?, ?)
                """,
                id,
                tenantId,
                firstName,
                lastName,
                birthDate,
                timestamp(now),
                timestamp(now));
    }

    private void pickup(UUID profileId, UUID id, String name, String status, Instant now) {
        jdbc.update(
                """
                INSERT INTO childcare_pickup_person
                    (id,tenant_id,childcare_profile_id,display_name,relationship_type,
                     authorization_status,created_at,updated_at)
                VALUES (?, ?, ?, ?, 'PARENT', ?, ?, ?)
                """,
                id,
                tenantId,
                profileId,
                name,
                status,
                timestamp(now),
                timestamp(now));
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
                                                                "childcare-a",
                                                                "email",
                                                                "parent@childcare-a.ma",
                                                                "password",
                                                                PARENT_PASSWORD,
                                                                "displayName",
                                                                "Famille Test"))))
                        .andExpect(status().isOk())
                        .andReturn();
        return result.getResponse().getCookie("schoolz_admission");
    }

    private String login() throws Exception {
        MvcResult result =
                mvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "tenantSlug",
                                                                "childcare-a",
                                                                "email",
                                                                "educator@childcare-a.ma",
                                                                "password",
                                                                PASSWORD))))
                        .andExpect(status().isOk())
                        .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private String bearer() {
        return "Bearer " + token;
    }

    private void cleanDatabase() {
        for (String table :
                new String[] {
                    "childcare_daily_event",
                    "childcare_daily_log",
                    "childcare_pickup_person",
                    "childcare_profile",
                    "childcare_group",
                    "childcare_policy",
                    "enrollment_signature",
                    "enrollment_fee_payment",
                    "enrollment_document",
                    "enrollment_service_selection",
                    "enrollment_family_confirmation",
                    "enrollment_contract",
                    "enrollment_portal_student_link",
                    "enrollment_workflow",
                    "admission_fee_payment",
                    "admission_notification",
                    "admission_enrollment_offer",
                    "admission_decision",
                    "admission_test",
                    "admission_interview",
                    "admission_visit",
                    "admission_document",
                    "admission_application_status",
                    "admission_application",
                    "admission_applicant",
                    "admission_prospect",
                    "admission_step",
                    "admission_campaign",
                    "admission_portal_session",
                    "admission_portal_account",
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

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    private record Scope(
            UUID studentId,
            UUID authorizedPickupId,
            UUID prohibitedPickupId,
            UUID educatorUserId) {}
}
