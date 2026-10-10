package ma.schoolz.erp.timetable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
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
class TimetableApiIntegrationTests {
    private static final String PASSWORD = "SchoolZTimetable2026!";

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
    private UUID actorId;
    private UUID campusId;
    private UUID yearId;
    private UUID classroomId;
    private UUID otherClassroomId;
    private UUID teacherId;
    private UUID labRoomId;
    private UUID smallRoomId;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanDatabase();
        IdentityTenant tenant =
                tenants.save(IdentityTenant.create("timetable-a", "Timetable A", clock.instant()));
        tenantId = tenant.id();
        Role role =
                roles.save(
                        Role.create(
                                tenantId,
                                "TIMETABLE_TEST",
                                "Timetable test",
                                false,
                                Set.of(
                                        "timetable.read",
                                        "timetable.write",
                                        "timetable.optimize",
                                        "timetable.publish"),
                                clock.instant()));
        UserAccount actor =
                UserAccount.create(
                        tenantId,
                        "planner@timetable-a.ma",
                        "Responsable planning",
                        passwordEncoder.encode(PASSWORD),
                        "fr-MA",
                        true,
                        clock.instant());
        actor.assignRoles(Set.of(role), clock.instant());
        accounts.save(actor);
        actorId = actor.id();
        token = login("timetable-a", "planner@timetable-a.ma", PASSWORD);
        createAcademicScope();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void manualPlacementDetectsEveryHardConflictAndSupportsDragDrop() throws Exception {
        UUID scheduleId = createSchedule("MANUAL");
        List<Map<String, Object>> slots = board(scheduleId, "timeSlots");
        UUID mondayFirst = slot(slots, 1, 1);
        UUID mondaySecond = slot(slots, 1, 2);
        UUID mondayThird = slot(slots, 1, 3);
        UUID mathId = createSubject("MATH", "Mathématiques", "#2563EB");
        UUID assignmentId =
                createAssignment(
                        scheduleId, classroomId, mathId, teacherId, 3, "LAB", Set.of("PROJECTOR"));
        addEquipment(labRoomId, "PROJECTOR");
        mvc.perform(
                        put("/api/v1/timetable/schedules/"
                                        + scheduleId
                                        + "/teachers/"
                                        + teacherId
                                        + "/slots/"
                                        + mondayFirst
                                        + "/availability")
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"availability\":\"UNAVAILABLE\"}"))
                .andExpect(status().isOk());

        mvc.perform(
                        post("/api/v1/timetable/schedules/" + scheduleId + "/lessons/validate")
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        placement(
                                                assignmentId,
                                                labRoomId,
                                                mondayFirst,
                                                "Mathématiques")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.conflicts[0].code").value("TEACHER_UNAVAILABLE"));
        mvc.perform(
                        post("/api/v1/timetable/schedules/" + scheduleId + "/lessons/validate")
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        placement(
                                                assignmentId,
                                                smallRoomId,
                                                mondaySecond,
                                                "Mathématiques")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(
                        jsonPath("$.conflicts[*].code")
                                .value(
                                        org.hamcrest.Matchers.hasItems(
                                                "ROOM_CAPACITY", "ROOM_TYPE", "ROOM_EQUIPMENT")));

        MvcResult created =
                mvc.perform(
                                post("/api/v1/timetable/schedules/" + scheduleId + "/lessons")
                                        .header(HttpHeaders.AUTHORIZATION, bearer())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                placement(
                                                        assignmentId,
                                                        labRoomId,
                                                        mondaySecond,
                                                        "Mathématiques")))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.source").value("MANUAL"))
                        .andReturn();
        String lessonId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        UUID scienceId = createSubject("SCI", "Sciences", "#059669");
        UUID conflictingAssignment =
                createAssignment(
                        scheduleId,
                        classroomId,
                        scienceId,
                        teacherId,
                        1,
                        "LAB",
                        Set.of("PROJECTOR"));
        mvc.perform(
                        post("/api/v1/timetable/schedules/" + scheduleId + "/lessons/validate")
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        placement(
                                                conflictingAssignment,
                                                labRoomId,
                                                mondaySecond,
                                                "Sciences")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(
                        jsonPath("$.conflicts[*].code")
                                .value(
                                        org.hamcrest.Matchers.hasItems(
                                                "TEACHER_BUSY", "ROOM_BUSY", "CLASSROOM_BUSY")));

        mvc.perform(
                        patch("/api/v1/timetable/schedules/" + scheduleId + "/lessons/" + lessonId)
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "roomId",
                                                        labRoomId,
                                                        "slotId",
                                                        mondayThird))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeSlotId").value(mondayThird.toString()));
        mvc.perform(
                        delete("/api/v1/timetable/schedules/" + scheduleId + "/lessons/" + lessonId)
                                .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isNoContent());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM identity_audit_log WHERE tenant_id=? AND"
                                        + " action LIKE 'timetable.%'",
                                Integer.class, tenantId))
                .isGreaterThanOrEqualTo(9);
    }

    @Test
    void optimizerProposesAppliesAndPublishesACompleteSchedule() throws Exception {
        UUID scheduleId = createSchedule("AUTO");
        UUID frenchId = createSubject("FR", "Français", "#7C3AED");
        UUID assignmentId =
                createAssignment(scheduleId, classroomId, frenchId, teacherId, 2, null, Set.of());
        mvc.perform(
                        post("/api/v1/timetable/schedules/" + scheduleId + "/constraints")
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "constraintType",
                                                        "MAX_HOURS_PER_DAY",
                                                        "scopeType",
                                                        "TEACHER",
                                                        "targetId",
                                                        teacherId,
                                                        "configuration",
                                                        Map.of("maxPeriods", 1),
                                                        "hardConstraint",
                                                        true,
                                                        "weight",
                                                        100))))
                .andExpect(status().isCreated());
        MvcResult optimization =
                mvc.perform(
                                post("/api/v1/timetable/schedules/"
                                                + scheduleId
                                                + "/optimizer/proposals")
                                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.proposals.length()").value(2))
                        .andExpect(jsonPath("$.unscheduled").isEmpty())
                        .andReturn();
        List<Map<String, Object>> proposals =
                JsonPath.read(optimization.getResponse().getContentAsString(), "$.proposals");
        assertThat(proposals)
                .extracting(value -> value.get("assignmentId"))
                .containsOnly(assignmentId.toString());
        MvcResult applied =
                mvc.perform(
                                post("/api/v1/timetable/schedules/"
                                                + scheduleId
                                                + "/optimizer/apply")
                                        .header(HttpHeaders.AUTHORIZATION, bearer())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of("proposals", proposals))))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.lessons.length()").value(2))
                        .andExpect(
                                jsonPath("$.lessons[*].source")
                                        .value(
                                                org.hamcrest.Matchers.everyItem(
                                                        org.hamcrest.Matchers.is("OPTIMIZER"))))
                        .andReturn();
        List<Integer> days =
                JsonPath.read(applied.getResponse().getContentAsString(), "$.lessons[*].dayOfWeek");
        assertThat(days).doesNotHaveDuplicates();

        mvc.perform(
                        post("/api/v1/timetable/schedules/" + scheduleId + "/publish")
                                .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schedule.status").value("PUBLISHED"));
        mvc.perform(
                        post("/api/v1/timetable/schedules/" + scheduleId + "/lessons")
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        placement(
                                                assignmentId,
                                                labRoomId,
                                                UUID.fromString(
                                                        proposals
                                                                .getFirst()
                                                                .get("slotId")
                                                                .toString()),
                                                "Interdit")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void tenantCannotReadOrMutateAnotherTenantSchedule() throws Exception {
        UUID scheduleId = createSchedule("TENANT");
        IdentityTenant other =
                tenants.save(IdentityTenant.create("timetable-b", "Timetable B", clock.instant()));
        Role otherRole =
                roles.save(
                        Role.create(
                                other.id(),
                                "OTHER_PLANNER",
                                "Other planner",
                                false,
                                Set.of("timetable.read", "timetable.write"),
                                clock.instant()));
        UserAccount otherUser =
                UserAccount.create(
                        other.id(),
                        "planner@timetable-b.ma",
                        "Autre responsable",
                        passwordEncoder.encode(PASSWORD),
                        "fr-MA",
                        true,
                        clock.instant());
        otherUser.assignRoles(Set.of(otherRole), clock.instant());
        accounts.save(otherUser);
        String otherToken = login("timetable-b", "planner@timetable-b.ma", PASSWORD);
        mvc.perform(
                        get("/api/v1/timetable/schedules/" + scheduleId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isNotFound());
        mvc.perform(
                        post("/api/v1/timetable/schedules/" + scheduleId + "/assignments")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "classroomId",
                                                        classroomId,
                                                        "subjectId",
                                                        UUID.randomUUID(),
                                                        "teacherStaffId",
                                                        teacherId,
                                                        "weeklyPeriods",
                                                        1,
                                                        "requiredEquipment",
                                                        List.of()))))
                .andExpect(status().isNotFound());
    }

    private UUID createSchedule(String code) throws Exception {
        MvcResult result =
                mvc.perform(
                                post("/api/v1/timetable/schedules")
                                        .header(HttpHeaders.AUTHORIZATION, bearer())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "campusId",
                                                                campusId,
                                                                "academicYearId",
                                                                yearId,
                                                                "code",
                                                                code,
                                                                "name",
                                                                "Planning " + code,
                                                                "validFrom",
                                                                "2026-09-01",
                                                                "validTo",
                                                                "2027-06-30",
                                                                "workingDays",
                                                                List.of(1, 2, 3, 4, 5),
                                                                "periods",
                                                                List.of(
                                                                        period(
                                                                                "P1", 1, "08:00",
                                                                                "09:00"),
                                                                        period(
                                                                                "P2", 2, "09:00",
                                                                                "10:00"),
                                                                        period(
                                                                                "P3", 3, "10:15",
                                                                                "11:15"))))))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.timeSlots.length()").value(15))
                        .andReturn();
        return UUID.fromString(
                JsonPath.read(result.getResponse().getContentAsString(), "$.schedule.id"));
    }

    private Map<String, Object> period(String code, int sequence, String start, String end) {
        return Map.of(
                "code",
                code,
                "name",
                "Période " + sequence,
                "sequenceNumber",
                sequence,
                "startsAt",
                start,
                "endsAt",
                end,
                "teaching",
                true);
    }

    private UUID createSubject(String code, String name, String color) throws Exception {
        MvcResult result =
                mvc.perform(
                                post("/api/v1/timetable/subjects")
                                        .header(HttpHeaders.AUTHORIZATION, bearer())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "code", code,
                                                                "nameFr", name,
                                                                "color", color))))
                        .andExpect(status().isCreated())
                        .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    private UUID createAssignment(
            UUID scheduleId,
            UUID classId,
            UUID subjectId,
            UUID staffId,
            int weekly,
            String roomType,
            Set<String> equipment)
            throws Exception {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("classroomId", classId);
        body.put("subjectId", subjectId);
        body.put("teacherStaffId", staffId);
        body.put("weeklyPeriods", weekly);
        body.put("requiredRoomType", roomType);
        body.put("requiredEquipment", equipment);
        MvcResult result =
                mvc.perform(
                                post("/api/v1/timetable/schedules/" + scheduleId + "/assignments")
                                        .header(HttpHeaders.AUTHORIZATION, bearer())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json.writeValueAsString(body)))
                        .andExpect(status().isCreated())
                        .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    private void addEquipment(UUID roomId, String equipment) throws Exception {
        mvc.perform(
                        post("/api/v1/timetable/rooms/" + roomId + "/equipment")
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of("equipmentCode", equipment, "quantity", 1))))
                .andExpect(status().isOk());
    }

    private String placement(UUID assignment, UUID room, UUID slot, String title) throws Exception {
        return json.writeValueAsString(
                Map.of(
                        "assignmentId", assignment,
                        "roomId", room,
                        "slotId", slot,
                        "title", title));
    }

    private List<Map<String, Object>> board(UUID scheduleId, String field) throws Exception {
        String content =
                mvc.perform(
                                get("/api/v1/timetable/schedules/" + scheduleId)
                                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        Map<String, Object> value = json.readValue(content, new TypeReference<>() {});
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = (List<Map<String, Object>>) value.get(field);
        return result;
    }

    private static UUID slot(List<Map<String, Object>> slots, int day, int sequence) {
        return slots.stream()
                .filter(value -> ((Number) value.get("dayOfWeek")).intValue() == day)
                .filter(value -> ((Number) value.get("sequenceNumber")).intValue() == sequence)
                .map(value -> UUID.fromString(value.get("id").toString()))
                .findFirst()
                .orElseThrow();
    }

    private String login(String tenant, String email, String password) throws Exception {
        MvcResult result =
                mvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "tenantSlug", tenant,
                                                                "email", email,
                                                                "password", password))))
                        .andExpect(status().isOk())
                        .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private String bearer() {
        return "Bearer " + token;
    }

    private void createAcademicScope() {
        Instant now = clock.instant();
        UUID organizationId = UUID.randomUUID();
        campusId = UUID.randomUUID();
        yearId = UUID.randomUUID();
        UUID schoolLevelId = UUID.randomUUID();
        UUID gradeId = UUID.randomUUID();
        classroomId = UUID.randomUUID();
        otherClassroomId = UUID.randomUUID();
        labRoomId = UUID.randomUUID();
        smallRoomId = UUID.randomUUID();
        UUID teacherPersonId = UUID.randomUUID();
        teacherId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO school_organization
                    (id,tenant_id,code,name,country_code,created_at,updated_at)
                VALUES (?, ?, 'ORG', 'École Test', 'MA', ?, ?)
                """,
                organizationId,
                tenantId,
                timestamp(now),
                timestamp(now));
        jdbc.update(
                """
                INSERT INTO school_campus
                    (id,tenant_id,organization_id,code,name,country_code,locale,timezone,active,
                     created_at,updated_at)
                VALUES (?, ?, ?, 'MAIN', 'Campus principal', 'MA', 'fr-MA',
                        'Africa/Casablanca', TRUE, ?, ?)
                """,
                campusId,
                tenantId,
                organizationId,
                timestamp(now),
                timestamp(now));
        jdbc.update(
                """
                INSERT INTO academic_year
                    (id,tenant_id,organization_id,code,name,starts_on,ends_on,status,
                     created_at,updated_at)
                VALUES (?, ?, ?, '2026', '2026-2027', ?, ?, 'ACTIVE', ?, ?)
                """,
                yearId,
                tenantId,
                organizationId,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2027, 6, 30),
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
        room(labRoomId, "LAB", "Laboratoire", "LAB", 30, now);
        room(smallRoomId, "SMALL", "Petite salle", "CLASSROOM", 10, now);
        classroom(classroomId, labRoomId, "CE1-A", "CE1 A", gradeId, 24, now);
        classroom(otherClassroomId, labRoomId, "CE1-B", "CE1 B", gradeId, 24, now);
        jdbc.update(
                """
                INSERT INTO person
                    (id,tenant_id,first_name,last_name,birth_date,status,created_at,updated_at)
                VALUES (?, ?, 'Fouad', 'Mellouli', ?, 'ACTIVE', ?, ?)
                """,
                teacherPersonId,
                tenantId,
                LocalDate.of(1985, 5, 2),
                timestamp(now),
                timestamp(now));
        jdbc.update(
                """
                INSERT INTO staff_profile
                    (id,tenant_id,person_id,employee_number,job_title,employment_status,
                     hired_on,created_at,updated_at)
                VALUES (?, ?, ?, 'EMP-T', 'Professeur', 'ACTIVE', ?, ?, ?)
                """,
                teacherId,
                tenantId,
                teacherPersonId,
                LocalDate.of(2020, 9, 1),
                timestamp(now),
                timestamp(now));
    }

    private void room(UUID id, String code, String name, String type, int capacity, Instant now) {
        jdbc.update(
                """
                INSERT INTO physical_room
                    (id,tenant_id,campus_id,code,name,room_type,capacity,active,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?)
                """,
                id,
                tenantId,
                campusId,
                code,
                name,
                type,
                capacity,
                timestamp(now),
                timestamp(now));
    }

    private void classroom(
            UUID id,
            UUID roomId,
            String code,
            String name,
            UUID gradeId,
            int capacity,
            Instant now) {
        jdbc.update(
                """
                INSERT INTO classroom
                    (id,tenant_id,campus_id,academic_year_id,grade_level_id,physical_room_id,
                     code,name,capacity,active,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?)
                """,
                id,
                tenantId,
                campusId,
                yearId,
                gradeId,
                roomId,
                code,
                name,
                capacity,
                timestamp(now),
                timestamp(now));
    }

    private void cleanDatabase() {
        for (String table :
                new String[] {
                    "timetable_lesson",
                    "timetable_teacher_availability",
                    "timetable_room_availability",
                    "timetable_constraint",
                    "timetable_teacher_assignment",
                    "timetable_room_equipment",
                    "timetable_time_slot",
                    "timetable_period",
                    "timetable_schedule",
                    "timetable_subject",
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
}
