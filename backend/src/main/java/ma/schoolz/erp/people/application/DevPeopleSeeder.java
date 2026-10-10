package ma.schoolz.erp.people.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
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
@Order(30)
public class DevPeopleSeeder implements ApplicationRunner {

    private static final String DEMO_IDENTIFIER = "SZ-2026-0001";

    private final TenantDirectory tenants;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final boolean enabled;
    private final String tenantSlug;

    public DevPeopleSeeder(
            TenantDirectory tenants,
            JdbcTemplate jdbc,
            Clock clock,
            @Value("${schoolz.demo.seed-enabled:false}") boolean enabled,
            @Value("${schoolz.demo.tenant-slug:}") String tenantSlug) {
        this.tenants = tenants;
        this.jdbc = jdbc;
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
        Integer existing =
                jdbc.queryForObject(
                        "SELECT count(*) FROM student_identifier WHERE tenant_id=? AND"
                                + " identifier_value=?",
                        Integer.class,
                        tenantId,
                        DEMO_IDENTIFIER);
        if (existing != null && existing > 0) return;

        UUID campusId =
                id(
                        "SELECT id FROM school_campus WHERE tenant_id=? AND code='CASA-CENTRE'",
                        tenantId);
        UUID yearId =
                id("SELECT id FROM academic_year WHERE tenant_id=? AND code='2026-2027'", tenantId);
        UUID classroomId =
                id("SELECT id FROM classroom WHERE tenant_id=? AND code='1AP-A'", tenantId);
        Timestamp now = Timestamp.from(clock.instant());
        UUID studentPersonId = UUID.randomUUID();
        UUID guardianPersonId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID householdId = UUID.randomUUID();

        person(tenantId, studentPersonId, "Yasmine", "Alaoui", LocalDate.of(2020, 3, 14), now);
        person(tenantId, guardianPersonId, "Nadia", "Alaoui", LocalDate.of(1986, 7, 9), now);
        jdbc.update(
                "INSERT INTO student_profile (id,tenant_id,person_id,status,created_at,updated_at)"
                        + " VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                studentId,
                tenantId,
                studentPersonId,
                now,
                now);
        jdbc.update(
                "INSERT INTO guardian_profile"
                    + " (id,tenant_id,person_id,occupation,status,created_at,updated_at) VALUES (?,"
                    + " ?, ?, 'Architecte', 'ACTIVE', ?, ?)",
                UUID.randomUUID(),
                tenantId,
                guardianPersonId,
                now,
                now);
        jdbc.update(
                "INSERT INTO household (id,tenant_id,name,status,created_at,updated_at) VALUES (?,"
                        + " ?, 'Famille Alaoui', 'ACTIVE', ?, ?)",
                householdId,
                tenantId,
                now,
                now);
        jdbc.update(
                "INSERT INTO person_address"
                    + " (id,tenant_id,person_id,address_type,line1,city,region,postal_code,country_code,primary_address,created_at)"
                    + " VALUES (?, ?, ?, 'HOME', '24 rue Al Massira', 'Casablanca',"
                    + " 'Casablanca-Settat', '20250', 'MA', TRUE, ?)",
                UUID.randomUUID(),
                tenantId,
                studentPersonId,
                now);
        contact(tenantId, guardianPersonId, "EMAIL", "nadia.alaoui@example.ma", now);
        contact(tenantId, guardianPersonId, "PHONE", "+212612345678", now);
        relationship(tenantId, householdId, studentPersonId, "CHILD", false, false, now);
        relationship(tenantId, householdId, guardianPersonId, "MOTHER", true, true, now);
        jdbc.update(
                "INSERT INTO emergency_contact"
                    + " (id,tenant_id,student_id,person_id,display_name,relationship_type,phone,priority,authorized_pickup,created_at)"
                    + " VALUES (?, ?, ?, ?, 'Nadia Alaoui', 'MOTHER', '+212612345678', 1, TRUE, ?)",
                UUID.randomUUID(),
                tenantId,
                studentId,
                guardianPersonId,
                now);
        jdbc.update(
                "INSERT INTO student_identifier"
                    + " (id,tenant_id,student_id,identifier_type,identifier_value,issuer,primary_identifier,created_at)"
                    + " VALUES (?, ?, ?, 'SCHOOL', ?, 'SchoolZ', TRUE, ?)",
                UUID.randomUUID(),
                tenantId,
                studentId,
                DEMO_IDENTIFIER,
                now);
        jdbc.update(
                "INSERT INTO student_enrollment"
                    + " (id,tenant_id,student_id,campus_id,academic_year_id,classroom_id,status,enrolled_on,created_at,updated_at)"
                    + " VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)",
                UUID.randomUUID(),
                tenantId,
                studentId,
                campusId,
                yearId,
                classroomId,
                LocalDate.of(2026, 9, 1),
                now,
                now);
        jdbc.update(
                "INSERT INTO person_custom_field"
                    + " (id,tenant_id,owner_type,owner_id,field_key,field_value,updated_at) VALUES"
                    + " (?, ?, 'STUDENT', ?, 'langue_maternelle', 'Arabe', ?)",
                UUID.randomUUID(),
                tenantId,
                studentId,
                now);
        jdbc.update(
                "INSERT INTO person_document"
                    + " (id,tenant_id,person_id,document_type,title,object_key,status,created_at)"
                    + " VALUES (?, ?, ?, 'BIRTH_CERTIFICATE', 'Acte de naissance',"
                    + " 'demo/yasmine/acte-naissance.pdf', 'VALID', ?)",
                UUID.randomUUID(),
                tenantId,
                studentPersonId,
                now);
        jdbc.update(
                "INSERT INTO student_history"
                    + " (id,tenant_id,student_id,event_type,summary,details,occurred_at) VALUES (?,"
                    + " ?, ?, 'STUDENT_CREATED', 'Dossier élève créé', 'Donnée de démonstration',"
                    + " ?)",
                UUID.randomUUID(),
                tenantId,
                studentId,
                now);
    }

    private UUID id(String sql, UUID tenantId) {
        return jdbc.queryForObject(sql, UUID.class, tenantId);
    }

    private void person(
            UUID tenantId,
            UUID id,
            String firstName,
            String lastName,
            LocalDate birthDate,
            Timestamp now) {
        jdbc.update(
                "INSERT INTO person"
                    + " (id,tenant_id,first_name,last_name,birth_date,nationality_code,status,created_at,updated_at)"
                    + " VALUES (?, ?, ?, ?, ?, 'MA', 'ACTIVE', ?, ?)",
                id,
                tenantId,
                firstName,
                lastName,
                birthDate,
                now,
                now);
    }

    private void contact(UUID tenantId, UUID personId, String type, String value, Timestamp now) {
        jdbc.update(
                "INSERT INTO person_contact_method"
                    + " (id,tenant_id,person_id,contact_type,contact_value,normalized_value,primary_contact,created_at)"
                    + " VALUES (?, ?, ?, ?, ?, ?, TRUE, ?)",
                UUID.randomUUID(),
                tenantId,
                personId,
                type,
                value,
                value.toLowerCase(),
                now);
    }

    private void relationship(
            UUID tenantId,
            UUID householdId,
            UUID personId,
            String type,
            boolean legalGuardian,
            boolean financialResponsible,
            Timestamp now) {
        jdbc.update(
                "INSERT INTO household_relationship"
                    + " (id,tenant_id,household_id,person_id,relationship_type,legal_guardian,financial_responsible,lives_with_student,created_at)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, TRUE, ?)",
                UUID.randomUUID(),
                tenantId,
                householdId,
                personId,
                type,
                legalGuardian,
                financialResponsible,
                now);
    }
}
