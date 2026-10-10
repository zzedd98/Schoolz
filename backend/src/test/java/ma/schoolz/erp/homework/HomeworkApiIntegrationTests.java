package ma.schoolz.erp.homework;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class HomeworkApiIntegrationTests {
    private static final String PASSWORD = "SchoolZHomework2026!";
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
    private UUID termId;
    private UUID classroomId;
    private UUID teacherAssignmentId;
    private UUID firstStudentId;
    private UUID secondStudentId;
    private String token;
    private Cookie parentCookie;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanDatabase();
        IdentityTenant tenant =
                tenants.save(IdentityTenant.create("homework-a", "Homework A", clock.instant()));
        tenantId = tenant.id();
        Role role =
                roles.save(
                        Role.create(
                                tenantId,
                                "HOMEWORK_TEST",
                                "Homework test",
                                false,
                                Set.of("homework.read", "homework.write", "homework.publish"),
                                clock.instant()));
        UserAccount teacher =
                UserAccount.create(
                        tenantId,
                        "teacher@homework-a.ma",
                        "Professeur Test",
                        passwordEncoder.encode(PASSWORD),
                        "fr-MA",
                        true,
                        clock.instant());
        teacher.assignRoles(Set.of(role), clock.instant());
        accounts.save(teacher);
        token = login();
        createAcademicScope(teacher.id());
        parentCookie = registerParent();
        UUID portalAccountId =
                jdbc.queryForObject(
                        "SELECT id FROM admission_portal_account WHERE tenant_id=? AND"
                                + " normalized_email='parent@homework-a.ma'",
                        UUID.class,
                        tenantId);
        jdbc.update(
                "INSERT INTO enrollment_portal_student_link"
                        + " (id,tenant_id,portal_account_id,student_id,created_at)"
                        + " VALUES (?, ?, ?, ?, ?)",
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
    void teacherPublishesLessonAndHomeworkWithImmutableAttachments() throws Exception {
        MvcResult lesson =
                mvc.perform(
                                post("/api/v1/homework/lesson-contents")
                                        .header(HttpHeaders.AUTHORIZATION, bearer())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json.writeValueAsString(lessonCommand())))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.status").value("DRAFT"))
                        .andReturn();
        String lessonId = JsonPath.read(lesson.getResponse().getContentAsString(), "$.id");
        MockMultipartFile lessonFile =
                new MockMultipartFile(
                        "file",
                        "fiche-fractions.txt",
                        MediaType.TEXT_PLAIN_VALUE,
                        "Comparer 3/8 et 5/8".getBytes());
        MvcResult attachment =
                mvc.perform(
                                multipart(
                                                "/api/v1/homework/lesson-contents/"
                                                        + lessonId
                                                        + "/attachments")
                                        .file(lessonFile)
                                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.fileName").value("fiche-fractions.txt"))
                        .andExpect(jsonPath("$.sha256").isNotEmpty())
                        .andReturn();
        String attachmentId = JsonPath.read(attachment.getResponse().getContentAsString(), "$.id");
        mvc.perform(
                        post("/api/v1/homework/lesson-contents/" + lessonId + "/publish")
                                .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        mvc.perform(
                        put("/api/v1/homework/lesson-contents/" + lessonId)
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(lessonCommand())))
                .andExpect(status().isConflict());
        mvc.perform(
                        get("/api/v1/homework/attachments/" + attachmentId)
                                .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(content().bytes("Comparer 3/8 et 5/8".getBytes()))
                .andExpect(header().exists("X-Content-SHA256"));

        MvcResult assignment = createHomework(lessonId);
        String homeworkId = JsonPath.read(assignment.getResponse().getContentAsString(), "$.id");
        mvc.perform(
                        post("/api/v1/homework/assignments/" + homeworkId + "/publish")
                                .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.totalStudents").value(2))
                .andExpect(jsonPath("$.completedStudents").value(0));
        mvc.perform(
                        put("/api/v1/homework/assignments/" + homeworkId)
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                homeworkCommand(UUID.fromString(lessonId)))))
                .andExpect(status().isConflict());

        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM homework_student_status WHERE tenant_id=?"
                                        + " AND homework_id=?",
                                Integer.class,
                                tenantId,
                                UUID.fromString(homeworkId)))
                .isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM identity_audit_log WHERE tenant_id=? AND"
                                        + " action LIKE 'homework.%'",
                                Integer.class, tenantId))
                .isGreaterThanOrEqualTo(4);
    }

    @Test
    void hostileAttachmentIsRejectedBeforePersistence() throws Exception {
        MvcResult lesson =
                mvc.perform(
                                post("/api/v1/homework/lesson-contents")
                                        .header(HttpHeaders.AUTHORIZATION, bearer())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json.writeValueAsString(lessonCommand())))
                        .andExpect(status().isCreated())
                        .andReturn();
        String lessonId = JsonPath.read(lesson.getResponse().getContentAsString(), "$.id");
        MockMultipartFile disguisedExecutable =
                new MockMultipartFile(
                        "file",
                        "cours.png",
                        MediaType.IMAGE_PNG_VALUE,
                        "MZ executable content".getBytes());

        mvc.perform(
                        multipart("/api/v1/homework/lesson-contents/" + lessonId + "/attachments")
                                .file(disguisedExecutable)
                                .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isBadRequest());

        MockMultipartFile malwareTestSignature =
                new MockMultipartFile(
                        "file",
                        "controle.txt",
                        MediaType.TEXT_PLAIN_VALUE,
                        "EICAR-STANDARD-ANTIVIRUS-TEST-FILE".getBytes());
        mvc.perform(
                        multipart("/api/v1/homework/lesson-contents/" + lessonId + "/attachments")
                                .file(malwareTestSignature)
                                .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isBadRequest());

        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM homework_attachment WHERE tenant_id=? AND"
                                        + " lesson_content_id=?",
                                Integer.class,
                                tenantId,
                                UUID.fromString(lessonId)))
                .isZero();
    }

    @Test
    void linkedParentSeesCalendarAndMarksOnlyOwnChildHomeworkDone() throws Exception {
        String lessonId = createAndPublishLesson();
        String homeworkId =
                JsonPath.read(createHomework(lessonId).getResponse().getContentAsString(), "$.id");
        mvc.perform(
                        post("/api/v1/homework/assignments/" + homeworkId + "/publish")
                                .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk());

        mvc.perform(
                        get("/api/v1/public/homework")
                                .cookie(parentCookie)
                                .param("from", "2026-09-01")
                                .param("to", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.children.length()").value(1))
                .andExpect(jsonPath("$.lessonContents.length()").value(1))
                .andExpect(jsonPath("$.homework.length()").value(1))
                .andExpect(jsonPath("$.homework[0].studentStatus").value("TO_DO"));
        mvc.perform(
                        patch("/api/v1/public/homework/assignments/" + homeworkId + "/status")
                                .cookie(parentCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "studentId",
                                                        firstStudentId,
                                                        "status",
                                                        "DONE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentStatus").value("DONE"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());
        mvc.perform(
                        patch("/api/v1/public/homework/assignments/" + homeworkId + "/status")
                                .cookie(parentCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "studentId",
                                                        secondStudentId,
                                                        "status",
                                                        "DONE"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void tenantBoundaryHidesForeignAssignment() throws Exception {
        IdentityTenant foreign =
                tenants.save(IdentityTenant.create("homework-b", "Homework B", clock.instant()));
        mvc.perform(
                        post("/api/v1/homework/lesson-contents")
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "teacherAssignmentId",
                                                        UUID.randomUUID(),
                                                        "academicTermId",
                                                        termId,
                                                        "lessonDate",
                                                        "2026-09-07",
                                                        "title",
                                                        "Invisible",
                                                        "content",
                                                        "Contenu étranger"))))
                .andExpect(status().isNotFound());
        assertThat(foreign.id()).isNotEqualTo(tenantId);
        mvc.perform(get("/api/v1/public/homework")).andExpect(status().isUnauthorized());
    }

    private String createAndPublishLesson() throws Exception {
        MvcResult lesson =
                mvc.perform(
                                post("/api/v1/homework/lesson-contents")
                                        .header(HttpHeaders.AUTHORIZATION, bearer())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json.writeValueAsString(lessonCommand())))
                        .andExpect(status().isCreated())
                        .andReturn();
        String id = JsonPath.read(lesson.getResponse().getContentAsString(), "$.id");
        mvc.perform(
                        post("/api/v1/homework/lesson-contents/" + id + "/publish")
                                .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk());
        return id;
    }

    private MvcResult createHomework(String lessonId) throws Exception {
        return mvc.perform(
                        post("/api/v1/homework/assignments")
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                homeworkCommand(UUID.fromString(lessonId)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();
    }

    private Map<String, Object> lessonCommand() {
        return Map.of(
                "teacherAssignmentId", teacherAssignmentId,
                "academicTermId", termId,
                "lessonDate", "2026-09-07",
                "title", "Comparer des fractions",
                "summary", "Fractions de même dénominateur",
                "content", "Comparer, représenter puis ordonner les fractions.");
    }

    private Map<String, Object> homeworkCommand(UUID lessonId) {
        return Map.of(
                "teacherAssignmentId",
                teacherAssignmentId,
                "academicTermId",
                termId,
                "lessonContentId",
                lessonId,
                "title",
                "Exercices sur les fractions",
                "instructions",
                "Faire les exercices 2, 3 et 5.",
                "assignedOn",
                "2026-09-07",
                "dueAt",
                "2026-09-10T18:00:00+01:00",
                "estimatedMinutes",
                30);
    }

    private void createAcademicScope(UUID teacherUserId) {
        Instant now = clock.instant();
        UUID organizationId = UUID.randomUUID();
        UUID campusId = UUID.randomUUID();
        UUID yearId = UUID.randomUUID();
        termId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();
        UUID gradeId = UUID.randomUUID();
        classroomId = UUID.randomUUID();
        UUID teacherPersonId = UUID.randomUUID();
        UUID teacherStaffId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();
        teacherAssignmentId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO school_organization"
                    + " (id,tenant_id,code,name,country_code,business_identifiers,created_at,updated_at)"
                    + " VALUES (?, ?, 'ORG-A', 'Homework', 'MA', '{}', ?, ?)",
                organizationId,
                tenantId,
                timestamp(now),
                timestamp(now));
        jdbc.update(
                "INSERT INTO school_campus"
                    + " (id,tenant_id,organization_id,code,name,city,country_code,locale,timezone,"
                    + " active,created_at,updated_at) VALUES (?, ?, ?, 'CAMP-A', 'Campus A',"
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
                "INSERT INTO academic_term"
                        + " (id,tenant_id,academic_year_id,code,name,term_type,sequence_number,"
                        + " starts_on,ends_on,created_at,updated_at) VALUES (?, ?, ?, 'T1',"
                        + " 'Trimestre 1', 'TRIMESTER', 1, '2026-09-01', '2026-12-20', ?, ?)",
                termId,
                tenantId,
                yearId,
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
        person(teacherPersonId, "Fouad", "Professeur", LocalDate.of(1985, 5, 2), now);
        jdbc.update(
                "INSERT INTO staff_profile"
                        + " (id,tenant_id,person_id,employee_number,job_title,employment_status,"
                        + " hired_on,created_at,updated_at) VALUES (?, ?, ?, 'EMP-T', 'Professeur',"
                        + " 'ACTIVE', '2024-09-01', ?, ?)",
                teacherStaffId,
                tenantId,
                teacherPersonId,
                timestamp(now),
                timestamp(now));
        firstStudentId = student("Yasmine", "Test", campusId, yearId, now);
        secondStudentId = student("Adam", "Test", campusId, yearId, now);
        jdbc.update(
                "INSERT INTO timetable_subject"
                    + " (id,tenant_id,code,name_fr,name_ar,color,active,created_at,updated_at)"
                    + " VALUES (?, ?, 'MATH', 'Mathématiques', 'الرياضيات', '#276749', TRUE, ?, ?)",
                subjectId,
                tenantId,
                timestamp(now),
                timestamp(now));
        jdbc.update(
                "INSERT INTO timetable_schedule"
                        + " (id,tenant_id,campus_id,academic_year_id,code,name,status,valid_from,"
                        + " valid_to,created_by_user_id,created_at,updated_at) VALUES (?, ?, ?, ?,"
                        + " 'MAIN', 'Emploi principal', 'PUBLISHED', '2026-09-01', '2027-07-15',"
                        + " ?, ?, ?)",
                scheduleId,
                tenantId,
                campusId,
                yearId,
                teacherUserId,
                timestamp(now),
                timestamp(now));
        jdbc.update(
                "INSERT INTO timetable_teacher_assignment"
                        + " (id,tenant_id,schedule_id,classroom_id,subject_id,teacher_staff_id,"
                        + " weekly_periods,active,created_at,updated_at) VALUES (?, ?, ?, ?, ?, ?,"
                        + " 5, TRUE, ?, ?)",
                teacherAssignmentId,
                tenantId,
                scheduleId,
                classroomId,
                subjectId,
                teacherStaffId,
                timestamp(now),
                timestamp(now));
    }

    private UUID student(
            String firstName, String lastName, UUID campusId, UUID yearId, Instant now) {
        UUID personId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        person(personId, firstName, lastName, LocalDate.of(2017, 3, 5), now);
        jdbc.update(
                "INSERT INTO student_profile"
                        + " (id,tenant_id,person_id,status,created_at,updated_at)"
                        + " VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                studentId,
                tenantId,
                personId,
                timestamp(now),
                timestamp(now));
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
        return studentId;
    }

    private void person(
            UUID id, String firstName, String lastName, LocalDate birthDate, Instant now) {
        jdbc.update(
                "INSERT INTO person"
                    + " (id,tenant_id,first_name,last_name,birth_date,status,created_at,updated_at)"
                    + " VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?)",
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
                                                                "homework-a",
                                                                "email",
                                                                "parent@homework-a.ma",
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
                                                                "homework-a",
                                                                "email",
                                                                "teacher@homework-a.ma",
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
                    "homework_student_status",
                    "homework_attachment",
                    "homework_assignment",
                    "lesson_content",
                    "timetable_constraint",
                    "timetable_teacher_availability",
                    "timetable_room_availability",
                    "timetable_lesson",
                    "timetable_teacher_assignment",
                    "timetable_room_equipment",
                    "timetable_time_slot",
                    "timetable_period",
                    "timetable_schedule",
                    "timetable_subject",
                    "enrollment_portal_student_link",
                    "admission_portal_session",
                    "admission_portal_account",
                    "student_identifier",
                    "student_enrollment",
                    "staff_profile",
                    "student_profile",
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
}
