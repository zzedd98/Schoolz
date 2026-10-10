package ma.schoolz.erp.childcare.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.UUID;
import ma.schoolz.erp.identity.TenantDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@Order(60)
public class DevChildcareSeeder implements ApplicationRunner {
    private static final String GROUP_CODE = "CRECHE-PAPILLONS";
    private static final String STUDENT_IDENTIFIER = "SZ-CRECHE-0001";
    private static final String PARENT_EMAIL = "parent.demo@schoolz.ma";

    private final JdbcTemplate jdbc;
    private final TenantDirectory tenants;
    private final Clock clock;
    private final boolean enabled;
    private final String tenantSlug;

    public DevChildcareSeeder(
            JdbcTemplate jdbc,
            TenantDirectory tenants,
            Clock clock,
            @Value("${schoolz.demo.seed-enabled:false}") boolean enabled,
            @Value("${schoolz.demo.tenant-slug:}") String tenantSlug) {
        this.jdbc = jdbc;
        this.tenants = tenants;
        this.clock = clock;
        this.enabled = enabled;
        this.tenantSlug = tenantSlug;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (!enabled) return;
        UUID tenantId =
                tenants.findActiveBySlug(tenantSlug)
                        .orElseThrow(() -> new IllegalStateException("Demo tenant was not seeded"))
                        .id();
        if (count(
                        "SELECT count(*) FROM childcare_group WHERE tenant_id=? AND code=?",
                        tenantId,
                        GROUP_CODE)
                > 0) return;

        Instant now = clock.instant();
        UUID campusId =
                id(
                        "SELECT id FROM school_campus WHERE tenant_id=? AND code='CASA-CENTRE'",
                        tenantId);
        UUID actorId =
                id(
                        "SELECT id FROM identity_user_account WHERE tenant_id=? ORDER BY created_at"
                                + " FETCH FIRST 1 ROWS ONLY",
                        tenantId);
        UUID parentId =
                jdbc
                        .query(
                                "SELECT id FROM admission_portal_account WHERE tenant_id=? AND"
                                        + " normalized_email=?",
                                (result, row) -> result.getObject(1, UUID.class),
                                tenantId,
                                PARENT_EMAIL)
                        .stream()
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Demo parent was not seeded"));

        UUID educatorPersonId = UUID.randomUUID();
        UUID educatorId = UUID.randomUUID();
        insertPerson(
                tenantId, educatorPersonId, "Salma", "Bennani", LocalDate.of(1991, 6, 18), now);
        jdbc.update(
                """
                INSERT INTO staff_profile
                    (id,tenant_id,person_id,employee_number,job_title,employment_status,
                     hired_on,created_at,updated_at)
                VALUES (?, ?, ?, 'EDU-CRECHE-001', 'Éducatrice petite enfance',
                        'ACTIVE', '2024-09-01', ?, ?)
                """,
                educatorId,
                tenantId,
                educatorPersonId,
                timestamp(now),
                timestamp(now));

        UUID studentPersonId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        insertPerson(
                tenantId,
                studentPersonId,
                "Amine",
                "El Idrissi",
                LocalDate.now(clock).minusYears(2).minusMonths(4),
                now);
        jdbc.update(
                "INSERT INTO student_profile (id,tenant_id,person_id,status,created_at,updated_at)"
                        + " VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                studentId,
                tenantId,
                studentPersonId,
                timestamp(now),
                timestamp(now));
        jdbc.update(
                """
                INSERT INTO student_identifier
                    (id,tenant_id,student_id,identifier_type,identifier_value,issuer,
                     primary_identifier,created_at)
                VALUES (?, ?, ?, 'SCHOOL', ?, 'SchoolZ', TRUE, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                studentId,
                STUDENT_IDENTIFIER,
                timestamp(now));

        UUID groupId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO childcare_policy
                    (id,tenant_id,campus_id,medication_recording_enabled,
                     medication_authorization_required,instructions,created_at,updated_at)
                VALUES (?, ?, ?, FALSE, TRUE,
                        'Les médicaments ne sont pas saisis dans la démonstration.', ?, ?)
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
                VALUES (?, ?, ?, ?, ?, 'Les Papillons', 18, 36, 14, TRUE, ?, ?)
                """,
                groupId,
                tenantId,
                campusId,
                educatorId,
                GROUP_CODE,
                timestamp(now),
                timestamp(now));

        UUID profileId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO childcare_profile
                    (id,tenant_id,student_id,group_id,expected_arrival_time,
                     expected_departure_time,meal_instructions,bottle_instructions,
                     sleep_instructions,toilet_stage,active,created_at,updated_at)
                VALUES (?, ?, ?, ?, '08:00', '17:30',
                        'Sans porc · morceaux adaptés à l’âge',
                        'Biberon de lait fourni par la famille à 10:00',
                        'Doudou bleu pour la sieste', 'COUCHES', TRUE, ?, ?)
                """,
                profileId,
                tenantId,
                studentId,
                groupId,
                timestamp(now),
                timestamp(now));
        UUID authorizedPickupId = UUID.randomUUID();
        insertPickup(
                tenantId,
                profileId,
                authorizedPickupId,
                "Nadia El Idrissi",
                "MOTHER",
                "+212600000099",
                "AUTHORIZED",
                "CIN-DEMO-01",
                null,
                now);
        insertPickup(
                tenantId,
                profileId,
                UUID.randomUUID(),
                "Personne non autorisée",
                "OTHER",
                null,
                "PROHIBITED",
                null,
                "Interdiction explicite enregistrée par la direction",
                now);
        jdbc.update(
                """
                INSERT INTO enrollment_portal_student_link
                    (id,tenant_id,portal_account_id,student_id,created_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                parentId,
                studentId,
                timestamp(now));

        LocalDate today = LocalDate.now(clock);
        UUID logId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO childcare_daily_log
                    (id,tenant_id,childcare_profile_id,log_date,arrival_at,mood,summary,status,
                     published_at,published_by_user_id,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, 'HAPPY', 'Très bonne journée.', 'PUBLISHED', ?, ?, ?, ?)
                """,
                logId,
                tenantId,
                profileId,
                today,
                time(today, 8, 12),
                timestamp(now),
                actorId,
                timestamp(now),
                timestamp(now));
        event(
                tenantId,
                logId,
                actorId,
                "MEAL",
                time(today, 12, 5),
                null,
                "Déjeuner",
                null,
                "ALL",
                "Repas terminé",
                now);
        event(
                tenantId,
                logId,
                actorId,
                "SLEEP",
                time(today, 13, 5),
                time(today, 14, 20),
                "Sieste",
                null,
                null,
                "Sommeil calme",
                now);
        event(
                tenantId,
                logId,
                actorId,
                "DIAPER",
                time(today, 11, 10),
                null,
                "Change",
                null,
                "CLEAN",
                null,
                now);
        event(
                tenantId,
                logId,
                actorId,
                "DIAPER",
                time(today, 15, 5),
                null,
                "Change",
                null,
                "CLEAN",
                null,
                now);
        event(
                tenantId,
                logId,
                actorId,
                "ACTIVITY",
                time(today, 15, 30),
                null,
                "Peinture",
                null,
                "PARTICIPATED",
                "Très concentré",
                now);
        event(
                tenantId,
                logId,
                actorId,
                "MOOD",
                time(today, 16, 0),
                null,
                "Humeur",
                null,
                "HAPPY",
                null,
                now);
        event(
                tenantId,
                logId,
                actorId,
                "OBSERVATION",
                time(today, 16, 10),
                null,
                "Observation",
                null,
                null,
                "Très bonne journée.",
                now);
    }

    private void insertPerson(
            UUID tenantId,
            UUID personId,
            String firstName,
            String lastName,
            LocalDate birthDate,
            Instant now) {
        jdbc.update(
                """
                INSERT INTO person
                    (id,tenant_id,first_name,last_name,birth_date,nationality_code,status,
                     created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, 'MA', 'ACTIVE', ?, ?)
                """,
                personId,
                tenantId,
                firstName,
                lastName,
                birthDate,
                timestamp(now),
                timestamp(now));
    }

    private void insertPickup(
            UUID tenantId,
            UUID profileId,
            UUID id,
            String name,
            String relationship,
            String phone,
            String status,
            String identityReference,
            String notes,
            Instant now) {
        jdbc.update(
                """
                INSERT INTO childcare_pickup_person
                    (id,tenant_id,childcare_profile_id,display_name,relationship_type,phone,
                     authorization_status,identity_reference,notes,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                tenantId,
                profileId,
                name,
                relationship,
                phone,
                status,
                identityReference,
                notes,
                timestamp(now),
                timestamp(now));
    }

    private void event(
            UUID tenantId,
            UUID logId,
            UUID actorId,
            String type,
            Timestamp occurredAt,
            Timestamp endedAt,
            String label,
            String unit,
            String outcome,
            String notes,
            Instant now) {
        jdbc.update(
                """
                INSERT INTO childcare_daily_event
                    (id,tenant_id,daily_log_id,event_type,occurred_at,ended_at,label,unit,
                     outcome,notes,created_by_user_id,created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                logId,
                type,
                occurredAt,
                endedAt,
                label,
                unit,
                outcome,
                notes,
                actorId,
                timestamp(now));
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private UUID id(String sql, UUID tenantId) {
        return jdbc.queryForObject(sql, UUID.class, tenantId);
    }

    private static Timestamp time(LocalDate date, int hour, int minute) {
        return timestamp(date.atTime(LocalTime.of(hour, minute)).toInstant(ZoneOffset.UTC));
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }
}
