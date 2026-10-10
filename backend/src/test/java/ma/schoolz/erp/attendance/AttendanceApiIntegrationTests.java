package ma.schoolz.erp.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
class AttendanceApiIntegrationTests {
    private static final String PASSWORD = "SchoolZAttendance2026!";
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
    private UUID classroomId;
    private UUID teacherStaffId;
    private UUID firstStudentId;
    private UUID secondStudentId;
    private UUID reasonId;
    private String token;
    private Cookie parentCookie;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanDatabase();
        IdentityTenant tenant =
                tenants.save(
                        IdentityTenant.create("attendance-a", "Attendance A", clock.instant()));
        tenantId = tenant.id();
        Role role =
                roles.save(
                        Role.create(
                                tenantId,
                                "ATTENDANCE_TEST",
                                "Attendance test",
                                false,
                                Set.of(
                                        "attendance.read",
                                        "attendance.write",
                                        "attendance.manage",
                                        "discipline.write"),
                                clock.instant()));
        UserAccount teacher =
                UserAccount.create(
                        tenantId,
                        "teacher@attendance-a.ma",
                        "Professeur Test",
                        passwordEncoder.encode(PASSWORD),
                        "fr-MA",
                        true,
                        clock.instant());
        teacher.assignRoles(Set.of(role), clock.instant());
        accounts.save(teacher);
        token = login();

        Scope scope = createScope(teacher.id());
        classroomId = scope.classroomId();
        teacherStaffId = scope.teacherStaffId();
        firstStudentId = scope.firstStudentId();
        secondStudentId = scope.secondStudentId();
        reasonId = scope.reasonId();
        parentCookie = registerParent();
        UUID portalAccountId =
                jdbc.queryForObject(
                        "SELECT id FROM admission_portal_account WHERE tenant_id=? AND"
                                + " normalized_email='parent@attendance-a.ma'",
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
                portalAccountId,
                firstStudentId,
                timestamp(clock.instant()));
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void teacherSubmitsCallParentJustifiesAndAdministrationApproves() throws Exception {
        String sessionId = createSession();
        MvcResult initial =
                mvc.perform(
                                get("/api/v1/attendance/sessions/" + sessionId)
                                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.records.length()").value(2))
                        .andExpect(jsonPath("$.records[0].status").value("PRESENT"))
                        .andExpect(jsonPath("$.records[1].status").value("PRESENT"))
                        .andReturn();
        List<Map<String, Object>> records =
                JsonPath.read(initial.getResponse().getContentAsString(), "$.records");
        String firstRecord =
                records.stream()
                        .filter(record -> firstStudentId.toString().equals(record.get("studentId")))
                        .findFirst()
                        .orElseThrow()
                        .get("id")
                        .toString();
        String secondRecord =
                records.stream()
                        .filter(
                                record ->
                                        secondStudentId.toString().equals(record.get("studentId")))
                        .findFirst()
                        .orElseThrow()
                        .get("id")
                        .toString();
        mvc.perform(
                        put("/api/v1/attendance/sessions/" + sessionId + "/records")
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "records",
                                                        List.of(
                                                                Map.of(
                                                                        "recordId",
                                                                        firstRecord,
                                                                        "status",
                                                                        "ABSENT",
                                                                        "notes",
                                                                        "Non présent à l'appel"),
                                                                Map.of(
                                                                        "recordId",
                                                                        secondRecord,
                                                                        "status",
                                                                        "LATE",
                                                                        "arrivedAt",
                                                                        "08:10"))))))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.records[?(@.status == 'ABSENT')]")
                                .value(org.hamcrest.Matchers.hasSize(1)))
                .andExpect(
                        jsonPath("$.records[?(@.status == 'LATE')]")
                                .value(org.hamcrest.Matchers.hasSize(1)));
        mvc.perform(
                        post("/api/v1/attendance/sessions/" + sessionId + "/submit")
                                .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        MvcResult parent =
                mvc.perform(
                                get("/api/v1/public/attendance")
                                        .cookie(parentCookie)
                                        .param("from", LocalDate.now(clock).minusDays(1).toString())
                                        .param("to", LocalDate.now(clock).plusDays(1).toString()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.absences.length()").value(1))
                        .andExpect(jsonPath("$.absences[0].resolutionStatus").value("UNEXCUSED"))
                        .andExpect(jsonPath("$.notifications.length()").value(1))
                        .andReturn();
        String absenceId =
                JsonPath.read(parent.getResponse().getContentAsString(), "$.absences[0].absenceId");
        mvc.perform(
                        post("/api/v1/public/attendance/absences/" + absenceId + "/justifications")
                                .cookie(parentCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "reasonId",
                                                        reasonId,
                                                        "statement",
                                                        "Consultation médicale programmée."))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resolutionStatus").value("PENDING"));

        MvcResult pending =
                mvc.perform(
                                get("/api/v1/attendance/justifications")
                                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$[0].reviewStatus").value("PENDING"))
                        .andReturn();
        String justificationId =
                JsonPath.read(pending.getResponse().getContentAsString(), "$[0].id");
        mvc.perform(
                        patch("/api/v1/attendance/justifications/" + justificationId)
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"decision\":\"APPROVED\",\"notes\":\"Pièce vérifiée\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("APPROVED"));

        mvc.perform(
                        get("/api/v1/public/attendance")
                                .cookie(parentCookie)
                                .param("from", LocalDate.now(clock).minusDays(1).toString())
                                .param("to", LocalDate.now(clock).plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.absences[0].resolutionStatus").value("EXCUSED"))
                .andExpect(jsonPath("$.notifications.length()").value(2));
        mvc.perform(
                        get("/api/v1/attendance/statistics")
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .param("from", LocalDate.now(clock).minusDays(1).toString())
                                .param("to", LocalDate.now(clock).plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRecords").value(2))
                .andExpect(jsonPath("$.absentCount").value(1))
                .andExpect(jsonPath("$.lateCount").value(1))
                .andExpect(jsonPath("$.unexcusedCount").value(0));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM identity_audit_log WHERE tenant_id=? AND"
                                        + " action LIKE 'attendance.%'",
                                Integer.class, tenantId))
                .isEqualTo(5);
    }

    @Test
    void parentCannotJustifyAnotherStudentsAbsence() throws Exception {
        UUID recordId = UUID.randomUUID();
        UUID sessionId = seedSubmittedSessionFor(secondStudentId, recordId);
        UUID absenceId = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                """
                INSERT INTO attendance_absence
                    (id,tenant_id,attendance_record_id,student_id,occurred_on,
                     resolution_status,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, 'UNEXCUSED', ?, ?)
                """,
                absenceId,
                tenantId,
                recordId,
                secondStudentId,
                LocalDate.now(clock),
                timestamp(now),
                timestamp(now));
        assertThat(sessionId).isNotNull();
        mvc.perform(
                        post("/api/v1/public/attendance/absences/" + absenceId + "/justifications")
                                .cookie(parentCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "reasonId",
                                                        reasonId,
                                                        "statement",
                                                        "Tentative hors périmètre."))))
                .andExpect(status().isNotFound());
    }

    @Test
    void disciplineAndTeacherObservationArePersistedAndAudited() throws Exception {
        MvcResult incident =
                mvc.perform(
                                post("/api/v1/attendance/incidents")
                                        .header(HttpHeaders.AUTHORIZATION, bearer())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "studentId",
                                                                firstStudentId,
                                                                "classroomId",
                                                                classroomId,
                                                                "category",
                                                                "BEHAVIOUR",
                                                                "severity",
                                                                "LOW",
                                                                "description",
                                                                "Interruption répétée du cours."))))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.status").value("OPEN"))
                        .andReturn();
        String incidentId = JsonPath.read(incident.getResponse().getContentAsString(), "$.id");
        mvc.perform(
                        post("/api/v1/attendance/incidents/" + incidentId + "/sanctions")
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "sanctionType",
                                                        "WARNING",
                                                        "description",
                                                        "Rappel formel du règlement.",
                                                        "startsOn",
                                                        LocalDate.now(clock)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(
                        post("/api/v1/attendance/observations")
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "studentId",
                                                        firstStudentId,
                                                        "classroomId",
                                                        classroomId,
                                                        "teacherStaffId",
                                                        teacherStaffId,
                                                        "category",
                                                        "PROGRESS",
                                                        "observation",
                                                        "Participation en nette progression.",
                                                        "visibleToParent",
                                                        true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.visibleToParent").value(true));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM disciplinary_incident WHERE tenant_id=?",
                                Integer.class,
                                tenantId))
                .isOne();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM attendance_sanction WHERE tenant_id=?",
                                Integer.class,
                                tenantId))
                .isOne();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM teacher_observation WHERE tenant_id=?",
                                Integer.class,
                                tenantId))
                .isOne();
    }

    private String createSession() throws Exception {
        MvcResult result =
                mvc.perform(
                                post("/api/v1/attendance/sessions")
                                        .header(HttpHeaders.AUTHORIZATION, bearer())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "classroomId",
                                                                classroomId,
                                                                "teacherStaffId",
                                                                teacherStaffId,
                                                                "sessionDate",
                                                                LocalDate.now(clock),
                                                                "subjectCode",
                                                                "MATH",
                                                                "subjectName",
                                                                "Mathématiques",
                                                                "startsAt",
                                                                "08:00",
                                                                "endsAt",
                                                                "09:00"))))
                        .andExpect(status().isCreated())
                        .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private UUID seedSubmittedSessionFor(UUID studentId, UUID recordId) {
        UUID sessionId = UUID.randomUUID();
        UUID enrollmentId =
                jdbc.queryForObject(
                        "SELECT id FROM student_enrollment WHERE tenant_id=? AND student_id=?",
                        UUID.class,
                        tenantId,
                        studentId);
        UUID campusId =
                jdbc.queryForObject(
                        "SELECT campus_id FROM classroom WHERE tenant_id=? AND id=?",
                        UUID.class,
                        tenantId,
                        classroomId);
        UUID yearId =
                jdbc.queryForObject(
                        "SELECT academic_year_id FROM classroom WHERE tenant_id=? AND id=?",
                        UUID.class,
                        tenantId,
                        classroomId);
        UUID accountId =
                jdbc.queryForObject(
                        "SELECT id FROM identity_user_account WHERE tenant_id=?",
                        UUID.class,
                        tenantId);
        Instant now = clock.instant();
        jdbc.update(
                """
                INSERT INTO attendance_session
                    (id,tenant_id,campus_id,academic_year_id,classroom_id,teacher_staff_id,
                     session_date,subject_code,subject_name,starts_at,ends_at,status,
                     submitted_at,submitted_by_user_id,created_by_user_id,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'FR', 'Français', '10:00', '11:00',
                        'SUBMITTED', ?, ?, ?, ?, ?)
                """,
                sessionId,
                tenantId,
                campusId,
                yearId,
                classroomId,
                teacherStaffId,
                LocalDate.now(clock),
                timestamp(now),
                accountId,
                accountId,
                timestamp(now),
                timestamp(now));
        jdbc.update(
                """
                INSERT INTO attendance_record
                    (id,tenant_id,session_id,student_enrollment_id,student_id,status,
                     marked_at,marked_by_user_id,updated_at)
                VALUES (?, ?, ?, ?, ?, 'ABSENT', ?, ?, ?)
                """,
                recordId,
                tenantId,
                sessionId,
                enrollmentId,
                studentId,
                timestamp(now),
                accountId,
                timestamp(now));
        return sessionId;
    }

    private Scope createScope(UUID teacherUserId) {
        Instant now = clock.instant();
        UUID organizationId = UUID.randomUUID();
        UUID campusId = UUID.randomUUID();
        UUID yearId = UUID.randomUUID();
        UUID schoolLevelId = UUID.randomUUID();
        UUID gradeId = UUID.randomUUID();
        classroomId = UUID.randomUUID();
        teacherStaffId = UUID.randomUUID();
        UUID teacherPersonId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO school_organization"
                    + " (id,tenant_id,code,name,country_code,business_identifiers,created_at,updated_at)"
                    + " VALUES (?, ?, 'ORG-A', 'Attendance', 'MA', '{}', ?, ?)",
                organizationId,
                tenantId,
                timestamp(now),
                timestamp(now));
        jdbc.update(
                """
                INSERT INTO school_campus
                    (id,tenant_id,organization_id,code,name,city,country_code,locale,timezone,
                     active,created_at,updated_at)
                VALUES (?, ?, ?, 'CAMP-A', 'Campus A', 'Rabat', 'MA', 'fr-MA', 'Africa/Casablanca',
                        TRUE, ?, ?)
                """,
                campusId,
                tenantId,
                organizationId,
                timestamp(now),
                timestamp(now));
        jdbc.update(
                """
                INSERT INTO academic_year
                    (id,tenant_id,organization_id,code,name,starts_on,ends_on,status,created_at,updated_at)
                VALUES (?, ?, ?, '2026', '2026-2027', ?, ?, 'ACTIVE', ?, ?)
                """,
                yearId,
                tenantId,
                organizationId,
                LocalDate.now(clock).minusMonths(1),
                LocalDate.now(clock).plusMonths(10),
                timestamp(now),
                timestamp(now));
        jdbc.update(
                """
                INSERT INTO school_level
                    (id,tenant_id,organization_id,code,name,sequence_number,active,created_at,updated_at)
                VALUES (?, ?, ?, 'PRIMARY', 'Primaire', 1, TRUE, ?, ?)
                """,
                schoolLevelId,
                tenantId,
                organizationId,
                timestamp(now),
                timestamp(now));
        jdbc.update(
                """
                INSERT INTO grade_level
                    (id,tenant_id,school_level_id,code,name,sequence_number,active,created_at,updated_at)
                VALUES (?, ?, ?, 'CE1', 'CE1', 1, TRUE, ?, ?)
                """,
                gradeId,
                tenantId,
                schoolLevelId,
                timestamp(now),
                timestamp(now));
        jdbc.update(
                """
                INSERT INTO classroom
                    (id,tenant_id,campus_id,academic_year_id,grade_level_id,code,name,capacity,
                     active,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, 'CE1-A', 'CE1 A', 24, TRUE, ?, ?)
                """,
                classroomId,
                tenantId,
                campusId,
                yearId,
                gradeId,
                timestamp(now),
                timestamp(now));
        person(teacherPersonId, "Fouad", "Professeur", LocalDate.of(1985, 5, 2), now);
        jdbc.update(
                """
                INSERT INTO staff_profile
                    (id,tenant_id,person_id,employee_number,job_title,employment_status,
                     hired_on,created_at,updated_at)
                VALUES (?, ?, ?, 'EMP-T', 'Professeur', 'ACTIVE', ?, ?, ?)
                """,
                teacherStaffId,
                tenantId,
                teacherPersonId,
                LocalDate.now(clock).minusYears(2),
                timestamp(now),
                timestamp(now));
        firstStudentId = student("Yasmine", "Test", "STU-1", classroomId, campusId, yearId, now);
        secondStudentId = student("Adam", "Test", "STU-2", classroomId, campusId, yearId, now);
        reasonId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO attendance_absence_reason
                    (id,tenant_id,code,name_fr,name_ar,category,requires_document,
                     active,created_at,updated_at)
                VALUES (?, ?, 'MEDICAL', 'Raison médicale', 'سبب طبي', 'MEDICAL',
                        TRUE, TRUE, ?, ?)
                """,
                reasonId,
                tenantId,
                timestamp(now),
                timestamp(now));
        return new Scope(classroomId, teacherStaffId, firstStudentId, secondStudentId, reasonId);
    }

    private UUID student(
            String firstName,
            String lastName,
            String number,
            UUID classId,
            UUID campusId,
            UUID yearId,
            Instant now) {
        UUID personId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        person(personId, firstName, lastName, LocalDate.of(2017, 3, 5), now);
        jdbc.update(
                """
                INSERT INTO student_profile
                    (id,tenant_id,person_id,status,created_at,updated_at)
                VALUES (?, ?, ?, 'ACTIVE', ?, ?)
                """,
                studentId,
                tenantId,
                personId,
                timestamp(now),
                timestamp(now));
        jdbc.update(
                """
                INSERT INTO student_identifier
                    (id,tenant_id,student_id,identifier_type,identifier_value,issuer,
                     primary_identifier,created_at)
                VALUES (?, ?, ?, 'INTERNAL', ?, 'SchoolZ', TRUE, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                studentId,
                number,
                timestamp(now));
        jdbc.update(
                """
                INSERT INTO student_enrollment
                    (id,tenant_id,student_id,campus_id,academic_year_id,classroom_id,status,
                     enrolled_on,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                studentId,
                campusId,
                yearId,
                classId,
                LocalDate.now(clock).minusMonths(1),
                timestamp(now),
                timestamp(now));
        return studentId;
    }

    private void person(
            UUID id, String firstName, String lastName, LocalDate birthDate, Instant now) {
        jdbc.update(
                """
                INSERT INTO person
                    (id,tenant_id,first_name,last_name,birth_date,status,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
                """,
                id,
                tenantId,
                firstName,
                lastName,
                birthDate,
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
                                                                "attendance-a",
                                                                "email",
                                                                "parent@attendance-a.ma",
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
                                                                "attendance-a",
                                                                "email",
                                                                "teacher@attendance-a.ma",
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
                    "attendance_parent_notification",
                    "attendance_excused_absence",
                    "attendance_late_arrival",
                    "attendance_early_departure",
                    "attendance_absence",
                    "attendance_record",
                    "attendance_session",
                    "attendance_sanction",
                    "disciplinary_incident",
                    "teacher_observation",
                    "attendance_absence_reason",
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
            UUID classroomId,
            UUID teacherStaffId,
            UUID firstStudentId,
            UUID secondStudentId,
            UUID reasonId) {}
}
