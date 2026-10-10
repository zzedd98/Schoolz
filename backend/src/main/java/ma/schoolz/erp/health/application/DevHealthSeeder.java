package ma.schoolz.erp.health.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
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
@Order(200)
public class DevHealthSeeder implements ApplicationRunner {
    private static final ZoneId SCHOOL_ZONE = ZoneId.of("Africa/Casablanca");
    private static final String NURSE_EMAIL = "infirmiere.demo@schoolz.ma";
    private static final String NURSE_PASSWORD = "SchoolZNurse2026!";

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final String tenantSlug;

    public DevHealthSeeder(
            JdbcTemplate jdbc,
            Clock clock,
            PasswordEncoder passwordEncoder,
            @Value("${schoolz.demo.seed-enabled:false}") boolean enabled,
            @Value("${schoolz.demo.tenant-slug:ecole-demo}") String tenantSlug) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.tenantSlug = tenantSlug;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (!enabled) return;
        UUID tenantId = id("SELECT id FROM identity_tenant WHERE slug=?", tenantSlug);
        seedNurse(tenantId);
        if (count("SELECT count(*) FROM health_profile WHERE tenant_id=?", tenantId) > 0) return;

        UUID studentId =
                id(
                        """
                        SELECT sp.id FROM student_profile sp JOIN person p ON p.id=sp.person_id
                        WHERE sp.tenant_id=? AND p.first_name='Yasmine' AND p.last_name='Alaoui'
                        """,
                        tenantId);
        UUID adminId =
                id(
                        "SELECT id FROM identity_user_account WHERE tenant_id=? AND"
                                + " normalized_email='admin@schoolz.ma'",
                        tenantId);
        Timestamp now = Timestamp.from(clock.instant());

        jdbc.update(
                """
                INSERT INTO health_policy(
                    id,tenant_id,vaccination_tracking_enabled,updated_by_user_id,created_at,updated_at)
                VALUES(?,?,TRUE,?,?,?)
                """,
                UUID.randomUUID(),
                tenantId,
                adminId,
                now,
                now);

        UUID profileId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO health_profile(
                    id,tenant_id,student_id,blood_group,insurance_provider,insurance_number,
                    physician_name,physician_phone,emergency_notes,created_at,updated_at,version)
                VALUES(?,?,?,'O+','Assurance scolaire Démo','AS-2026-0042',
                    'Dr Salma Benjelloun','+212 5 22 00 11 22',
                    'Contacter la famille avant toute prise en charge non urgente.',?,?,0)
                """,
                profileId,
                tenantId,
                studentId,
                now,
                now);

        jdbc.update(
                """
                INSERT INTO health_allergy(
                    id,tenant_id,health_profile_id,allergen,reaction_description,severity,
                    emergency_instructions,active,recorded_by_user_id,created_at,updated_at)
                VALUES(?,?,?,'Arachides','Urticaire signalée par la famille','HIGH',
                    'Éviter toute exposition et suivre le protocole familial transmis.',TRUE,?,?,?)
                """,
                UUID.randomUUID(),
                tenantId,
                profileId,
                adminId,
                now,
                now);

        jdbc.update(
                """
                INSERT INTO health_medication_instruction(
                    id,tenant_id,health_profile_id,medication_name,dosage_text,administration_route,
                    schedule_text,starts_on,ends_on,prescriber_name,authorization_reference,
                    instructions,active,recorded_by_user_id,created_at,updated_at)
                VALUES(?,?,?,'Traitement d’urgence fourni par la famille','Selon protocole joint',
                    'Selon protocole','Uniquement en situation prévue',?,?,
                    'Dr Salma Benjelloun','AUT-FAM-2026-001',
                    'Instruction transmise par la famille ; aucune adaptation par SchoolZ.',TRUE,?,?,?)
                """,
                UUID.randomUUID(),
                tenantId,
                profileId,
                LocalDate.now(clock.withZone(SCHOOL_ZONE)).minusMonths(1),
                LocalDate.now(clock.withZone(SCHOOL_ZONE)).plusMonths(10),
                adminId,
                now,
                now);

        UUID incidentId = UUID.randomUUID();
        Timestamp incidentAt = Timestamp.from(clock.instant().minus(Duration.ofDays(4)));
        jdbc.update(
                """
                INSERT INTO health_incident(
                    id,tenant_id,student_id,occurred_at,incident_type,severity,location,description,
                    action_taken,guardian_notified_at,status,recorded_by_user_id,created_at,updated_at)
                VALUES(?,?,?,?,'PLAYGROUND_FALL','LOW','Cour primaire',
                    'Chute pendant la récréation, éraflure superficielle observée au genou.',
                    'Nettoyage et protection de la zone ; surveillance en classe.',?,'CLOSED',?,?,?)
                """,
                incidentId,
                tenantId,
                studentId,
                incidentAt,
                Timestamp.from(clock.instant().minus(Duration.ofDays(4)).plusSeconds(600)),
                adminId,
                incidentAt,
                now);

        jdbc.update(
                """
                INSERT INTO health_nurse_visit(
                    id,tenant_id,student_id,incident_id,visited_at,reason,observations,
                    temperature_celsius,action_taken,return_to_class,referred_to,
                    recorded_by_user_id,created_at)
                VALUES(?,?,?,?,?,'Passage après chute dans la cour',
                    'Élève calme, mobilité conservée.',NULL,
                    'Nettoyage, pansement et surveillance.',TRUE,NULL,?,?)
                """,
                UUID.randomUUID(),
                tenantId,
                studentId,
                incidentId,
                incidentAt,
                adminId,
                incidentAt);

        jdbc.update(
                """
                INSERT INTO health_emergency_action(
                    id,tenant_id,incident_id,action_type,description,performed_at,
                    performed_by_user_id,guardian_contacted,external_reference,created_at)
                VALUES(?,?,?,'FIRST_AID','Nettoyage et pose d’un pansement.',?,?,TRUE,NULL,?)
                """,
                UUID.randomUUID(),
                tenantId,
                incidentId,
                incidentAt,
                adminId,
                incidentAt);

        jdbc.update(
                """
                INSERT INTO health_vaccination_record(
                    id,tenant_id,health_profile_id,vaccine_name,dose_label,administered_on,
                    provider_name,document_reference,next_due_on,status,recorded_by_user_id,created_at)
                VALUES(?,?,?,'Vaccin déclaré par la famille','Rappel',?,
                    'Centre de santé déclaré','DOC-SANTE-2026-014',NULL,'VERIFIED',?,?)
                """,
                UUID.randomUUID(),
                tenantId,
                profileId,
                LocalDate.now(clock.withZone(SCHOOL_ZONE)).minusYears(1),
                adminId,
                now);
    }

    private void seedNurse(UUID tenantId) {
        String normalized = NURSE_EMAIL.toLowerCase(Locale.ROOT);
        List<UUID> existing =
                jdbc.query(
                        "SELECT id FROM identity_user_account WHERE tenant_id=? AND"
                                + " normalized_email=?",
                        (result, row) -> result.getObject(1, UUID.class),
                        tenantId,
                        normalized);
        UUID accountId;
        if (existing.isEmpty()) {
            accountId = UUID.randomUUID();
            Timestamp now = Timestamp.from(clock.instant());
            jdbc.update(
                    """
                    INSERT INTO identity_user_account
                        (id,tenant_id,email,normalized_email,display_name,password_hash,locale,
                         status,email_verified_at,failed_login_attempts,mfa_enabled,created_at,
                         updated_at,version)
                    VALUES (?, ?, ?, ?, 'Infirmière Démo', ?, 'fr-MA', 'ACTIVE', ?, 0, FALSE,
                            ?, ?, 0)
                    """,
                    accountId,
                    tenantId,
                    NURSE_EMAIL,
                    normalized,
                    passwordEncoder.encode(NURSE_PASSWORD),
                    now,
                    now,
                    now);
        } else {
            accountId = existing.getFirst();
        }
        UUID nurseRoleId =
                id("SELECT id FROM identity_role WHERE tenant_id=? AND code='NURSE'", tenantId);
        jdbc.update(
                """
                INSERT INTO identity_user_role (user_id,role_id)
                SELECT ?, ?
                WHERE NOT EXISTS (
                    SELECT 1 FROM identity_user_role WHERE user_id=? AND role_id=?
                )
                """,
                accountId,
                nurseRoleId,
                accountId,
                nurseRoleId);
    }

    private UUID id(String query, Object... arguments) {
        List<UUID> values =
                jdbc.query(query, (result, row) -> result.getObject(1, UUID.class), arguments);
        if (values.isEmpty()) throw new IllegalStateException("Donnée de démonstration absente");
        return values.getFirst();
    }

    private int count(String query, Object... arguments) {
        Integer value = jdbc.queryForObject(query, Integer.class, arguments);
        return value == null ? 0 : value;
    }
}
