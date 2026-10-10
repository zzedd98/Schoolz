package ma.schoolz.erp.teacherportal;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
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
class TeacherPortalApiIntegrationTests {
    private static final String PASSWORD = "SchoolZTeacherTest2026!";
    private static final ZoneId SCHOOL_ZONE = ZoneId.of("Africa/Casablanca");

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
    private UserAccount teacher;
    private UserAccount unlinked;
    private UUID teacherStaffId;
    private UUID classroomId;
    private LocalDate teachingDate;
    private String teacherToken;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanDatabase();
        IdentityTenant tenant =
                tenants.save(IdentityTenant.create("teacher-a", "Teacher A", clock.instant()));
        tenantId = tenant.id();
        Role role =
                roles.save(
                        Role.create(
                                tenantId,
                                "TEACHER_TEST",
                                "Teacher test",
                                false,
                                Set.of(
                                        "teacher-portal.read",
                                        "attendance.write",
                                        "grade.write",
                                        "homework.write",
                                        "discipline.write",
                                        "communication.write"),
                                clock.instant()));
        teacher = account("fouad@teacher-a.ma", "Fouad Mellouli", role);
        unlinked = account("unlinked@teacher-a.ma", "Sans profil", role);
        teachingDate = LocalDate.now(clock.withZone(SCHOOL_ZONE)).plusDays(2);
        createAcademicScope();
        teacherToken = login(teacher.email());
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void teacherSeesOnlyOwnPublishedLessonsAndOneClickActions() throws Exception {
        mvc.perform(
                        get("/api/v1/teacher-portal/today")
                                .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken))
                                .param("date", teachingDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacher.staffProfileId").value(teacherStaffId.toString()))
                .andExpect(jsonPath("$.teacher.firstName").value("Fouad"))
                .andExpect(jsonPath("$.lessons.length()").value(2))
                .andExpect(jsonPath("$.lessons[0].startsAt").value("08:00:00"))
                .andExpect(jsonPath("$.lessons[1].startsAt").value("09:00:00"))
                .andExpect(jsonPath("$.lessons[0].attendanceStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$.summary.lessonCount").value(2))
                .andExpect(jsonPath("$.summary.attendanceSubmittedCount").value(1))
                .andExpect(jsonPath("$.quickActions.length()").value(5))
                .andExpect(jsonPath("$.quickActions[0].code").value("TAKE_ATTENDANCE"))
                .andExpect(jsonPath("$.quickActions[0].enabled").value(true))
                .andExpect(
                        jsonPath("$.quickActions[0].targetPath")
                                .value(
                                        org.hamcrest.Matchers.containsString(
                                                "classroomId=" + classroomId)))
                .andExpect(jsonPath("$.quickActions[4].code").value("MESSAGE"))
                .andExpect(jsonPath("$.quickActions[4].enabled").value(true))
                .andExpect(
                        jsonPath("$.quickActions[4].targetPath")
                                .value(
                                        org.hamcrest.Matchers.containsString(
                                                "classroomId=" + classroomId)));
    }

    @Test
    void teacherCanOpenPortalOnADayWithoutLessons() throws Exception {
        LocalDate dayWithoutLessons = teachingDate.minusDays(1);

        mvc.perform(
                        get("/api/v1/teacher-portal/today")
                                .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken))
                                .param("date", dayWithoutLessons.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(dayWithoutLessons.toString()))
                .andExpect(jsonPath("$.lessons.length()").value(0))
                .andExpect(jsonPath("$.summary.lessonCount").value(0))
                .andExpect(jsonPath("$.nextTeachingDate").value(teachingDate.toString()))
                .andExpect(jsonPath("$.quickActions.length()").value(5))
                .andExpect(jsonPath("$.quickActions[0].enabled").value(false))
                .andExpect(jsonPath("$.quickActions[4].code").value("MESSAGE"))
                .andExpect(jsonPath("$.quickActions[4].enabled").value(false))
                .andExpect(jsonPath("$.quickActions[4].targetPath").isEmpty());
    }

    @Test
    void accountWithoutMappedStaffProfileIsRejected() throws Exception {
        String token = login(unlinked.email());
        mvc.perform(
                        get("/api/v1/teacher-portal/today")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .param("date", teachingDate.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Profil professeur introuvable"));
    }

    @Test
    void dateOutsideGuardWindowIsRejected() throws Exception {
        mvc.perform(
                        get("/api/v1/teacher-portal/today")
                                .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken))
                                .param(
                                        "date",
                                        LocalDate.now(clock.withZone(SCHOOL_ZONE))
                                                .plusDays(401)
                                                .toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Date du portail invalide"));
    }

    private UserAccount account(String email, String displayName, Role role) {
        UserAccount account =
                UserAccount.create(
                        tenantId,
                        email,
                        displayName,
                        passwordEncoder.encode(PASSWORD),
                        "fr-MA",
                        true,
                        clock.instant());
        account.assignRoles(Set.of(role), clock.instant());
        return accounts.save(account);
    }

    private void createAcademicScope() {
        Instant now = clock.instant();
        UUID organizationId = UUID.randomUUID();
        UUID campusId = UUID.randomUUID();
        UUID yearId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();
        UUID gradeId = UUID.randomUUID();
        classroomId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();
        teacherStaffId = staff("Fouad", "Mellouli", "EMP-TEACH-1", now);
        UUID otherStaffId = staff("Nadia", "El Fassi", "EMP-TEACH-2", now);

        jdbc.update(
                "INSERT INTO school_organization"
                    + " (id,tenant_id,code,name,country_code,business_identifiers,created_at,updated_at)"
                    + " VALUES (?, ?, 'ORG-T', 'Teacher', 'MA', '{}', ?, ?)",
                organizationId,
                tenantId,
                timestamp(now),
                timestamp(now));
        jdbc.update(
                "INSERT INTO school_campus"
                    + " (id,tenant_id,organization_id,code,name,city,country_code,locale,timezone,"
                    + " active,created_at,updated_at) VALUES (?, ?, ?, 'CAMP-T', 'Campus', 'Rabat',"
                    + " 'MA', 'fr-MA', 'Africa/Casablanca', TRUE, ?, ?)",
                campusId,
                tenantId,
                organizationId,
                timestamp(now),
                timestamp(now));
        jdbc.update(
                "INSERT INTO academic_year"
                    + " (id,tenant_id,organization_id,code,name,starts_on,ends_on,status,created_at,updated_at)"
                    + " VALUES (?, ?, ?, 'CURRENT', 'Année', ?, ?, 'ACTIVE', ?, ?)",
                yearId,
                tenantId,
                organizationId,
                teachingDate.minusMonths(1),
                teachingDate.plusMonths(10),
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
                    + " VALUES (?, ?, ?, 'CE2', 'CE2', 1, TRUE, ?, ?)",
                gradeId,
                tenantId,
                levelId,
                timestamp(now),
                timestamp(now));
        jdbc.update(
                "INSERT INTO classroom"
                    + " (id,tenant_id,campus_id,academic_year_id,grade_level_id,code,name,capacity,"
                    + " active,created_at,updated_at) VALUES (?, ?, ?, ?, ?, 'CE2-A', 'CE2 A', 25,"
                    + " TRUE, ?, ?)",
                classroomId,
                tenantId,
                campusId,
                yearId,
                gradeId,
                timestamp(now),
                timestamp(now));
        jdbc.update(
                "INSERT INTO physical_room"
                    + " (id,tenant_id,campus_id,code,name,room_type,capacity,active,created_at,updated_at)"
                    + " VALUES (?, ?, ?, 'A-1', 'Salle A1', 'CLASSROOM', 30, TRUE, ?, ?)",
                roomId,
                tenantId,
                campusId,
                timestamp(now),
                timestamp(now));
        jdbc.update(
                "INSERT INTO timetable_subject"
                    + " (id,tenant_id,code,name_fr,name_ar,color,active,created_at,updated_at)"
                    + " VALUES (?, ?, 'MATH', 'Mathématiques', 'الرياضيات', '#2563EB', TRUE, ?, ?)",
                subjectId,
                tenantId,
                timestamp(now),
                timestamp(now));
        jdbc.update(
                "INSERT INTO timetable_schedule"
                    + " (id,tenant_id,campus_id,academic_year_id,code,name,status,valid_from,valid_to,"
                    + " created_by_user_id,created_at,updated_at) VALUES (?, ?, ?, ?, 'MAIN',"
                    + " 'Principal', 'PUBLISHED', ?, ?, ?, ?, ?)",
                scheduleId,
                tenantId,
                campusId,
                yearId,
                teachingDate.minusDays(7),
                teachingDate.plusDays(30),
                teacher.id(),
                timestamp(now),
                timestamp(now));
        UUID teacherAssignment = assignment(scheduleId, subjectId, teacherStaffId, "MATH-T", now);
        UUID otherAssignment = assignment(scheduleId, subjectId, otherStaffId, "MATH-O", now);
        UUID period1 = period(scheduleId, "P1", 1, LocalTime.of(8, 0), now);
        UUID period2 = period(scheduleId, "P2", 2, LocalTime.of(9, 0), now);
        UUID period3 = period(scheduleId, "P3", 3, LocalTime.of(10, 0), now);
        UUID slot1 = slot(scheduleId, period1);
        UUID slot2 = slot(scheduleId, period2);
        UUID slot3 = slot(scheduleId, period3);
        lesson(scheduleId, teacherAssignment, subjectId, teacherStaffId, roomId, slot1, now);
        lesson(scheduleId, teacherAssignment, subjectId, teacherStaffId, roomId, slot2, now);
        lesson(scheduleId, otherAssignment, subjectId, otherStaffId, roomId, slot3, now);
        jdbc.update(
                "INSERT INTO teacher_portal_profile"
                    + " (id,tenant_id,user_account_id,staff_profile_id,active,created_at,updated_at)"
                    + " VALUES (?, ?, ?, ?, TRUE, ?, ?)",
                UUID.randomUUID(),
                tenantId,
                teacher.id(),
                teacherStaffId,
                timestamp(now),
                timestamp(now));
        jdbc.update(
                "INSERT INTO attendance_session"
                    + " (id,tenant_id,campus_id,academic_year_id,classroom_id,teacher_staff_id,"
                    + " session_date,subject_code,subject_name,starts_at,ends_at,status,submitted_at,"
                    + " submitted_by_user_id,created_by_user_id,created_at,updated_at,version)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, 'MATH', 'Mathématiques', '08:00', '09:00',"
                    + " 'SUBMITTED', ?, ?, ?, ?, ?, 0)",
                UUID.randomUUID(),
                tenantId,
                campusId,
                yearId,
                classroomId,
                teacherStaffId,
                teachingDate,
                timestamp(now),
                teacher.id(),
                teacher.id(),
                timestamp(now),
                timestamp(now));
    }

    private UUID staff(String firstName, String lastName, String employeeNumber, Instant now) {
        UUID personId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO person"
                        + " (id,tenant_id,first_name,last_name,status,created_at,updated_at)"
                        + " VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)",
                personId,
                tenantId,
                firstName,
                lastName,
                timestamp(now),
                timestamp(now));
        jdbc.update(
                "INSERT INTO staff_profile"
                    + " (id,tenant_id,person_id,employee_number,job_title,employment_status,hired_on,"
                    + " created_at,updated_at) VALUES (?, ?, ?, ?, 'Professeur', 'ACTIVE', ?, ?,"
                    + " ?)",
                staffId,
                tenantId,
                personId,
                employeeNumber,
                LocalDate.now(clock).minusYears(2),
                timestamp(now),
                timestamp(now));
        return staffId;
    }

    private UUID assignment(
            UUID scheduleId, UUID subjectId, UUID staffId, String marker, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO timetable_teacher_assignment"
                    + " (id,tenant_id,schedule_id,classroom_id,subject_id,teacher_staff_id,weekly_periods,"
                    + " required_equipment,active,created_at,updated_at) VALUES (?, ?, ?, ?, ?, ?,"
                    + " 3, ?, TRUE, ?, ?)",
                id,
                tenantId,
                scheduleId,
                classroomId,
                subjectId,
                staffId,
                marker,
                timestamp(now),
                timestamp(now));
        return id;
    }

    private UUID period(
            UUID scheduleId, String code, int sequence, LocalTime startsAt, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO timetable_period"
                    + " (id,tenant_id,schedule_id,code,name,sequence_number,starts_at,ends_at,teaching)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE)",
                id,
                tenantId,
                scheduleId,
                code,
                code,
                sequence,
                startsAt,
                startsAt.plusHours(1));
        return id;
    }

    private UUID slot(UUID scheduleId, UUID periodId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO timetable_time_slot (id,tenant_id,schedule_id,period_id,day_of_week)"
                        + " VALUES (?, ?, ?, ?, ?)",
                id,
                tenantId,
                scheduleId,
                periodId,
                teachingDate.getDayOfWeek().getValue());
        return id;
    }

    private void lesson(
            UUID scheduleId,
            UUID assignmentId,
            UUID subjectId,
            UUID staffId,
            UUID roomId,
            UUID slotId,
            Instant now) {
        jdbc.update(
                "INSERT INTO timetable_lesson"
                    + " (id,tenant_id,schedule_id,assignment_id,classroom_id,subject_id,teacher_staff_id,"
                    + " room_id,time_slot_id,source,created_by_user_id,created_at,updated_at)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'MANUAL', ?, ?, ?)",
                UUID.randomUUID(),
                tenantId,
                scheduleId,
                assignmentId,
                classroomId,
                subjectId,
                staffId,
                roomId,
                slotId,
                teacher.id(),
                timestamp(now),
                timestamp(now));
    }

    private String login(String email) throws Exception {
        MvcResult result =
                mvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "tenantSlug",
                                                                "teacher-a",
                                                                "email",
                                                                email,
                                                                "password",
                                                                PASSWORD))))
                        .andExpect(status().isOk())
                        .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
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
