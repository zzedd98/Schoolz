package ma.schoolz.erp.admissions.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ma.schoolz.erp.identity.TenantDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@Order(40)
public class DevAdmissionsSeeder implements ApplicationRunner {

    public static final String PARENT_EMAIL = "parent.demo@schoolz.ma";
    public static final String PARENT_PASSWORD = "SchoolZParent2026!";

    private final JdbcTemplate jdbc;
    private final TenantDirectory tenants;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final boolean enabled;
    private final String tenantSlug;

    public DevAdmissionsSeeder(
            JdbcTemplate jdbc,
            TenantDirectory tenants,
            PasswordEncoder passwords,
            Clock clock,
            @Value("${schoolz.demo.seed-enabled:false}") boolean enabled,
            @Value("${schoolz.demo.tenant-slug:}") String tenantSlug) {
        this.jdbc = jdbc;
        this.tenants = tenants;
        this.passwords = passwords;
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
                        "SELECT count(*) FROM admission_campaign WHERE tenant_id=? AND"
                                + " code='ADM-2026'",
                        Integer.class,
                        tenantId);
        if (existing != null && existing > 0) return;

        Instant now = clock.instant();
        UUID yearId =
                jdbc.queryForObject(
                        "SELECT id FROM academic_year WHERE tenant_id=? AND code='2026-2027'",
                        UUID.class,
                        tenantId);
        UUID campusId =
                jdbc.queryForObject(
                        "SELECT id FROM school_campus WHERE tenant_id=? AND code='CASA-CENTRE'",
                        UUID.class,
                        tenantId);
        UUID gradeId =
                jdbc.queryForObject(
                        "SELECT id FROM grade_level WHERE tenant_id=? AND code='1AP'",
                        UUID.class,
                        tenantId);
        UUID campaignId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO admission_campaign
                    (id,tenant_id,academic_year_id,code,name,description,opens_on,closes_on,
                     application_fee,currency_code,required_document_types,status,created_at,updated_at)
                VALUES (?, ?, ?, 'ADM-2026', 'Admissions 2026–2027',
                        'Rejoignez SchoolZ : un parcours bilingue, humain et exigeant.',
                        '2026-01-01', '2027-08-31', 500.00, 'MAD',
                        '["BIRTH_CERTIFICATE","REPORT_CARD"]', 'OPEN', ?, ?)
                """,
                campaignId,
                tenantId,
                yearId,
                timestamp(now),
                timestamp(now));
        seedSteps(tenantId, campaignId, now);

        seedProspect(
                tenantId,
                campaignId,
                "Nadia",
                "Amrani",
                "nadia.amrani@example.ma",
                "Meryem Amrani",
                "INSTAGRAM",
                AdmissionStage.NEW_LEAD,
                now.minusSeconds(86_400));
        seedProspect(
                tenantId,
                campaignId,
                "Omar",
                "Benali",
                "omar.benali@example.ma",
                "Adam Benali",
                "REFERRAL",
                AdmissionStage.CONTACTED,
                now.minusSeconds(172_800));
        seedProspect(
                tenantId,
                campaignId,
                "Salma",
                "Tahiri",
                "salma.tahiri@example.ma",
                "Lina Tahiri",
                "WEBSITE",
                AdmissionStage.SCHOOL_VISIT,
                now.minusSeconds(259_200));

        UUID accountId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO admission_portal_account
                    (id,tenant_id,email,normalized_email,display_name,phone,password_hash,
                     locale,status,created_at,updated_at)
                VALUES (?, ?, ?, ?, 'Famille Démo', '+212600000099', ?, 'fr-MA', 'ACTIVE', ?, ?)
                """,
                accountId,
                tenantId,
                PARENT_EMAIL,
                PARENT_EMAIL,
                passwords.encode(PARENT_PASSWORD),
                timestamp(now),
                timestamp(now));

        List<SeedApplication> applications =
                List.of(
                        new SeedApplication(
                                "Yasmine", "Alaoui", AdmissionStage.APPLICATION_STARTED, 60),
                        new SeedApplication(
                                "Ilyas", "Alaoui", AdmissionStage.APPLICATION_COMPLETE, 100),
                        new SeedApplication("Aya", "Alaoui", AdmissionStage.TEST_SCHEDULED, 100),
                        new SeedApplication("Rayan", "Alaoui", AdmissionStage.REVIEW, 100),
                        new SeedApplication("Maya", "Alaoui", AdmissionStage.ACCEPTED, 100));
        for (int index = 0; index < applications.size(); index++) {
            seedApplication(
                    tenantId,
                    accountId,
                    campaignId,
                    campusId,
                    gradeId,
                    applications.get(index),
                    index + 1,
                    now.minusSeconds((long) (index + 1) * 43_200));
        }
    }

    private void seedSteps(UUID tenantId, UUID campaignId, Instant now) {
        String[][] steps = {
            {"CHILD", "Votre enfant", "Renseignez son identité et sa scolarité actuelle."},
            {"FORM", "Le dossier", "Complétez les informations pédagogiques et familiales."},
            {"DOCUMENTS", "Les pièces", "Ajoutez l'acte de naissance et le dernier bulletin."},
            {"FOLLOW_UP", "Le suivi", "Suivez les rendez-vous et la décision en temps réel."}
        };
        for (int index = 0; index < steps.length; index++) {
            jdbc.update(
                    """
                    INSERT INTO admission_step
                        (id,tenant_id,campaign_id,code,name,instructions,sequence_number,required,created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, TRUE, ?)
                    """,
                    UUID.randomUUID(),
                    tenantId,
                    campaignId,
                    steps[index][0],
                    steps[index][1],
                    steps[index][2],
                    index + 1,
                    timestamp(now));
        }
    }

    private void seedProspect(
            UUID tenantId,
            UUID campaignId,
            String firstName,
            String lastName,
            String email,
            String childName,
            String source,
            AdmissionStage status,
            Instant createdAt) {
        jdbc.update(
                """
                INSERT INTO admission_prospect
                    (id,tenant_id,campaign_id,first_name,last_name,email,phone,child_name,
                     desired_grade_code,source,status,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, '+212600000001', ?, '1AP', ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                campaignId,
                firstName,
                lastName,
                email,
                childName,
                source,
                status.name(),
                timestamp(createdAt),
                timestamp(createdAt));
    }

    private void seedApplication(
            UUID tenantId,
            UUID accountId,
            UUID campaignId,
            UUID campusId,
            UUID gradeId,
            SeedApplication seed,
            int sequence,
            Instant createdAt) {
        UUID applicantId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO admission_applicant
                    (id,tenant_id,portal_account_id,first_name,last_name,birth_date,gender,
                     nationality_code,current_school,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'FEMALE', 'MA', 'École Horizon', ?, ?)
                """,
                applicantId,
                tenantId,
                accountId,
                seed.firstName(),
                seed.lastName(),
                LocalDate.of(2019, Math.min(sequence + 1, 12), 10),
                timestamp(createdAt),
                timestamp(createdAt));
        UUID applicationId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO admission_application
                    (id,tenant_id,campaign_id,applicant_id,desired_campus_id,
                     desired_grade_level_id,reference_number,current_status,form_data,
                     completeness_percent,submitted_at,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                applicationId,
                tenantId,
                campaignId,
                applicantId,
                campusId,
                gradeId,
                "ADM-2026-DEMO-" + sequence,
                seed.status().name(),
                "{\"motivation\":\"Projet éducatif bilingue\",\"needs\":\"Aucun\"}",
                seed.completeness(),
                seed.status() == AdmissionStage.APPLICATION_STARTED ? null : timestamp(createdAt),
                timestamp(createdAt),
                timestamp(createdAt));
        jdbc.update(
                """
                INSERT INTO admission_application_status
                    (id,tenant_id,application_id,to_status,comment,
                     changed_by_portal_account_id,occurred_at)
                VALUES (?, ?, ?, ?, 'Donnée de démonstration', ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                applicationId,
                seed.status().name(),
                accountId,
                timestamp(createdAt));
        if (seed.status() == AdmissionStage.ACCEPTED) {
            jdbc.update(
                    """
                    INSERT INTO admission_decision
                        (id,tenant_id,application_id,decision,reason,decided_at)
                    VALUES (?, ?, ?, 'ACCEPTED', 'Avis favorable', ?)
                    """,
                    UUID.randomUUID(),
                    tenantId,
                    applicationId,
                    timestamp(createdAt));
            jdbc.update(
                    """
                    INSERT INTO admission_enrollment_offer
                        (id,tenant_id,application_id,offer_reference,offered_on,expires_on,
                         status,conditions,created_at,updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, 'OFFERED', 'Sous réserve du dossier d’inscription', ?, ?)
                    """,
                    UUID.randomUUID(),
                    tenantId,
                    applicationId,
                    "OFF-DEMO-" + sequence,
                    LocalDate.now(clock),
                    LocalDate.now(clock).plusDays(30),
                    timestamp(createdAt),
                    timestamp(createdAt));
        }
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private record SeedApplication(
            String firstName, String lastName, AdmissionStage status, int completeness) {}
}
