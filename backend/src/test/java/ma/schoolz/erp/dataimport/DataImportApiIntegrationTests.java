package ma.schoolz.erp.dataimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class DataImportApiIntegrationTests {

    private static final String PASSWORD = "TestSchoolZ!2026";
    private static final Set<String> IMPORT_PERMISSIONS =
            Set.of(
                    "data-import.read",
                    "data-import.write",
                    "person.read",
                    "person.write",
                    "student.read",
                    "student.write",
                    "academic-structure.read",
                    "academic-structure.write");

    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private IdentityTenantRepository tenants;
    @Autowired private RoleRepository roles;
    @Autowired private UserAccountRepository accounts;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private Clock clock;
    @Autowired private ObjectMapper json;

    private MockMvc mvc;
    private UUID tenantId;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanDatabase();
        IdentityTenant tenant = createUser("imports", "School Imports", IMPORT_PERMISSIONS);
        tenantId = tenant.id();
        createAcademicScope(tenantId);
        token = login("imports", "admin@imports.ma");
    }

    @Test
    void xlsxTemplateCompletesUploadMappingValidationPreviewAndImport() throws Exception {
        byte[] workbook =
                new ClassPathResource("import-templates/schoolz-import-students.xlsx")
                        .getContentAsByteArray();
        UploadResult uploaded =
                upload(
                        "STUDENTS",
                        new MockMultipartFile(
                                "file",
                                "students.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                workbook));

        assertThat(uploaded.format()).isEqualTo("XLSX");
        map(uploaded);
        mvc.perform(
                        post("/api/v1/imports/" + uploaded.id() + "/validate")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job.status").value("VALIDATED"))
                .andExpect(jsonPath("$.job.invalidRows").value(0))
                .andExpect(jsonPath("$.rows[0].rowNumber").value(2));
        mvc.perform(
                        post("/api/v1/imports/" + uploaded.id() + "/execute")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IMPORTED"))
                .andExpect(jsonPath("$.importedRows").value(1));

        assertThat(count("SELECT count(*) FROM student_profile WHERE tenant_id=?", tenantId))
                .isEqualTo(1);
        assertThat(
                        count(
                                "SELECT count(*) FROM student_identifier WHERE tenant_id=? AND"
                                        + " identifier_value='ELV-2026-001'",
                                tenantId))
                .isEqualTo(1);
    }

    @Test
    void invalidCsvReportsTheExactSourceLineAndField() throws Exception {
        String source =
                "first_name;last_name;employee_number;job_title;hired_on\n"
                        + "Sara;Bennani;EMP-001;Enseignante;01/09/2024\n";
        UploadResult uploaded = uploadCsv("STAFF", "staff.csv", source);
        map(uploaded);

        mvc.perform(
                        post("/api/v1/imports/" + uploaded.id() + "/validate")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job.invalidRows").value(1))
                .andExpect(jsonPath("$.rows[0].rowNumber").value(2))
                .andExpect(jsonPath("$.rows[0].status").value("INVALID"))
                .andExpect(jsonPath("$.rows[0].errors[0].field").value("hired_on"))
                .andExpect(
                        jsonPath("$.rows[0].errors[0].message")
                                .value("Date attendue au format AAAA-MM-JJ"));
    }

    @Test
    void completeImportRollsBackEveryBusinessRowWhenTargetChangesAfterValidation()
            throws Exception {
        String source =
                "first_name,last_name,employee_number,job_title,hired_on\n"
                        + "Sara,Bennani,EMP-001,Enseignante,2024-09-01\n"
                        + "Amine,Idrissi,EMP-002,Surveillant,2024-09-01\n";
        UploadResult uploaded = uploadCsv("STAFF", "staff.csv", source);
        map(uploaded);
        mvc.perform(
                        post("/api/v1/imports/" + uploaded.id() + "/validate")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job.invalidRows").value(0));

        insertConcurrentStaff("EMP-002");

        mvc.perform(
                        post("/api/v1/imports/" + uploaded.id() + "/execute")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.importedRows").value(0))
                .andExpect(
                        jsonPath("$.errorSummary")
                                .value("Import annulé : aucune donnée métier n'a été conservée"));

        assertThat(
                        count(
                                "SELECT count(*) FROM staff_profile WHERE tenant_id=? AND"
                                        + " employee_number='EMP-001'",
                                tenantId))
                .isZero();
        assertThat(
                        count(
                                "SELECT count(*) FROM staff_profile WHERE tenant_id=? AND"
                                        + " employee_number='EMP-002'",
                                tenantId))
                .isEqualTo(1);
    }

    @Test
    void templatesRequireAuthenticationAndAreAvailableToAuthorizedUsers() throws Exception {
        mvc.perform(get("/api/v1/imports/templates/STUDENTS.xlsx"))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        get("/api/v1/imports/templates/STUDENTS.xlsx")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(
                        result ->
                                assertThat(result.getResponse().getContentType())
                                        .isEqualTo(
                                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @Test
    void allSixBusinessImportTypesPersistTheirExpectedTargets() throws Exception {
        byte[] workbook =
                new ClassPathResource("import-templates/schoolz-import-students.xlsx")
                        .getContentAsByteArray();
        complete(
                upload(
                        "STUDENTS",
                        new MockMultipartFile(
                                "file",
                                "students.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                workbook)));
        complete(
                uploadCsv(
                        "PARENTS",
                        "parents.csv",
                        "first_name,last_name,student_identifier,relationship,email,phone\n"
                            + "Omar,Alaoui,ELV-2026-001,FATHER,omar@example.ma,212612345679\n"));
        complete(
                uploadCsv(
                        "STAFF",
                        "staff.csv",
                        "first_name,last_name,employee_number,job_title,hired_on\n"
                                + "Sara,Bennani,EMP-010,Enseignante,2024-09-01\n"));
        complete(
                uploadCsv(
                        "CLASSES",
                        "classes.csv",
                        "code,name,campus_code,academic_year,grade_code,capacity\n"
                                + "1AP-B,1AP B,CASA,2026-2027,1AP,30\n"));
        complete(
                uploadCsv(
                        "PAYMENTS",
                        "payments.csv",
                        "student_identifier,amount,paid_on,payment_method,external_reference,currency\n"
                            + "ELV-2026-001,1250.00,2026-08-15,BANK_TRANSFER,VIR-001,MAD\n"));
        complete(
                uploadCsv(
                        "OPENING_BALANCES",
                        "balances.csv",
                        "student_identifier,amount,as_of_date,description,currency\n"
                                + "ELV-2026-001,2500.00,2026-08-31,Ancien solde,MAD\n"));

        assertThat(count("SELECT count(*) FROM guardian_profile WHERE tenant_id=?", tenantId))
                .isEqualTo(2);
        assertThat(count("SELECT count(*) FROM staff_profile WHERE tenant_id=?", tenantId))
                .isEqualTo(1);
        assertThat(
                        count(
                                "SELECT count(*) FROM classroom WHERE tenant_id=? AND code='1AP-B'",
                                tenantId))
                .isEqualTo(1);
        assertThat(
                        count(
                                "SELECT count(*) FROM imported_legacy_payment WHERE tenant_id=? AND"
                                        + " reconciliation_status='PENDING'",
                                tenantId))
                .isEqualTo(1);
        assertThat(
                        count(
                                "SELECT count(*) FROM imported_opening_balance WHERE tenant_id=?"
                                        + " AND reconciliation_status='PENDING'",
                                tenantId))
                .isEqualTo(1);
    }

    private UploadResult uploadCsv(String type, String name, String source) throws Exception {
        return upload(
                type,
                new MockMultipartFile(
                        "file", name, "text/csv", source.getBytes(StandardCharsets.UTF_8)));
    }

    private UploadResult upload(String type, MockMultipartFile file) throws Exception {
        MvcResult result =
                mvc.perform(
                                multipart("/api/v1/imports")
                                        .file(file)
                                        .param("type", type)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("UPLOADED"))
                        .andReturn();
        String content = result.getResponse().getContentAsString();
        return new UploadResult(
                JsonPath.read(content, "$.id"),
                JsonPath.read(content, "$.fileFormat"),
                JsonPath.read(content, "$.mapping"));
    }

    private void map(UploadResult uploaded) throws Exception {
        mvc.perform(
                        put("/api/v1/imports/" + uploaded.id() + "/mapping")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of("mapping", uploaded.mapping()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MAPPED"));
    }

    private void complete(UploadResult uploaded) throws Exception {
        map(uploaded);
        mvc.perform(
                        post("/api/v1/imports/" + uploaded.id() + "/validate")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job.invalidRows").value(0));
        mvc.perform(
                        post("/api/v1/imports/" + uploaded.id() + "/execute")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IMPORTED"))
                .andExpect(jsonPath("$.importedRows").value(1));
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

    private IdentityTenant createUser(String slug, String name, Set<String> permissions) {
        IdentityTenant tenant = tenants.save(IdentityTenant.create(slug, name, clock.instant()));
        Role role =
                roles.save(
                        Role.create(
                                tenant.id(),
                                "IMPORT_MANAGER",
                                "Import manager",
                                false,
                                permissions,
                                clock.instant()));
        UserAccount account =
                UserAccount.create(
                        tenant.id(),
                        "admin@imports.ma",
                        name + " User",
                        passwordEncoder.encode(PASSWORD),
                        "fr-MA",
                        true,
                        clock.instant());
        account.assignRoles(Set.of(role), clock.instant());
        accounts.save(account);
        return tenant;
    }

    private void createAcademicScope(UUID scopeTenantId) {
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
                    + " VALUES (?, ?, 'ORG', 'School Imports', 'MA', '{}', ?, ?)",
                organizationId,
                scopeTenantId,
                databaseTime(now),
                databaseTime(now));
        jdbc.update(
                "INSERT INTO school_campus"
                    + " (id,tenant_id,organization_id,code,name,country_code,locale,timezone,active,created_at,updated_at)"
                    + " VALUES (?, ?, ?, 'CASA', 'Campus Casa', 'MA', 'fr-MA', 'Africa/Casablanca',"
                    + " TRUE, ?, ?)",
                campusId,
                scopeTenantId,
                organizationId,
                databaseTime(now),
                databaseTime(now));
        jdbc.update(
                "INSERT INTO academic_year"
                    + " (id,tenant_id,organization_id,code,name,starts_on,ends_on,status,created_at,updated_at)"
                    + " VALUES (?, ?, ?, '2026-2027', 'Année 2026-2027', '2026-09-01',"
                    + " '2027-06-30', 'ACTIVE', ?, ?)",
                yearId,
                scopeTenantId,
                organizationId,
                databaseTime(now),
                databaseTime(now));
        jdbc.update(
                "INSERT INTO school_level"
                    + " (id,tenant_id,organization_id,code,name,sequence_number,created_at,updated_at)"
                    + " VALUES (?, ?, ?, 'PRIMARY', 'Primaire', 1, ?, ?)",
                levelId,
                scopeTenantId,
                organizationId,
                databaseTime(now),
                databaseTime(now));
        jdbc.update(
                "INSERT INTO grade_level"
                    + " (id,tenant_id,school_level_id,code,name,sequence_number,created_at,updated_at)"
                    + " VALUES (?, ?, ?, '1AP', '1AP', 1, ?, ?)",
                gradeId,
                scopeTenantId,
                levelId,
                databaseTime(now),
                databaseTime(now));
        jdbc.update(
                "INSERT INTO classroom"
                    + " (id,tenant_id,campus_id,academic_year_id,grade_level_id,code,name,capacity,active,created_at,updated_at)"
                    + " VALUES (?, ?, ?, ?, ?, '1AP-A', '1AP A', 28, TRUE, ?, ?)",
                classroomId,
                scopeTenantId,
                campusId,
                yearId,
                gradeId,
                databaseTime(now),
                databaseTime(now));
    }

    private void insertConcurrentStaff(String employeeNumber) {
        Instant now = clock.instant();
        UUID personId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO person"
                        + " (id,tenant_id,first_name,last_name,status,created_at,updated_at)"
                        + " VALUES (?, ?, 'Concurrent', 'User', 'ACTIVE', ?, ?)",
                personId,
                tenantId,
                databaseTime(now),
                databaseTime(now));
        jdbc.update(
                "INSERT INTO staff_profile"
                    + " (id,tenant_id,person_id,employee_number,job_title,employment_status,hired_on,created_at,updated_at)"
                    + " VALUES (?, ?, ?, ?, 'Test', 'ACTIVE', '2024-09-01', ?, ?)",
                UUID.randomUUID(),
                tenantId,
                personId,
                employeeNumber,
                databaseTime(now),
                databaseTime(now));
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
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

    private static Timestamp databaseTime(Instant instant) {
        return Timestamp.from(instant);
    }

    private static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private record UploadResult(String id, String format, Map<String, String> mapping) {}
}
