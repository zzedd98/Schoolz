package ma.schoolz.erp.privacy.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
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
@Order(390)
public class DevPrivacySeeder implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final boolean enabled;
    private final String tenantSlug;

    public DevPrivacySeeder(
            JdbcTemplate jdbc,
            Clock clock,
            @Value("${schoolz.demo.seed-enabled:false}") boolean enabled,
            @Value("${schoolz.demo.tenant-slug:ecole-demo}") String tenantSlug) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.enabled = enabled;
        this.tenantSlug = tenantSlug;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (!enabled) return;
        UUID tenant = id("SELECT id FROM identity_tenant WHERE slug=?", tenantSlug);
        if (count("SELECT count(*) FROM privacy_processing_purpose WHERE tenant_id=?", tenant) > 0)
            return;
        UUID admin =
                id(
                        "SELECT id FROM identity_user_account WHERE tenant_id=? AND"
                                + " normalized_email='admin@schoolz.ma'",
                        tenant);
        purpose(
                tenant,
                admin,
                "SCHOOLING",
                "Gestion de la scolarité",
                "Inscription, suivi pédagogique, évaluations et vie scolaire.",
                "CONTRACT_OR_PUBLIC_TASK",
                "identité, scolarité, notes, assiduité",
                "personnel habilité, famille et élève",
                3650,
                false);
        purpose(
                tenant,
                admin,
                "HEALTH_SUPPORT",
                "Protection et accompagnement sanitaire",
                "Prise en charge des urgences, allergies et besoins de santé déclarés.",
                "LEGAL_OBLIGATION_OR_VITAL_INTEREST",
                "santé, allergies, incidents",
                "personnel médical et personnes strictement habilitées",
                1825,
                true);
        purpose(
                tenant,
                admin,
                "SCHOOL_COMMUNICATION",
                "Communication de l’établissement",
                "Diffusion des annonces et messages nécessaires au fonctionnement scolaire.",
                "LEGITIMATE_INTEREST_OR_CONSENT",
                "coordonnées, préférences, messages",
                "établissement et prestataires contractuels validés",
                730,
                false);
        retention(tenant, admin, "Dossiers élèves", 3650, "REVIEW", true);
        retention(tenant, admin, "Pièces d’admission non retenues", 365, "DELETE", false);
        retention(tenant, admin, "Journaux techniques", 90, "DELETE", false);
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update(
                "INSERT INTO privacy_hosting_configuration"
                    + " (tenant_id,country_code,region_label,provider_name,encryption_at_rest,encryption_in_transit,cross_border_transfer,legal_validation_status,updated_by_user_id,updated_at)"
                    + " VALUES (?,'MA','Configuration locale de démonstration','Docker"
                    + " local',TRUE,FALSE,FALSE,'PENDING',?,?)",
                tenant,
                admin,
                now);
    }

    private void purpose(
            UUID tenant,
            UUID admin,
            String code,
            String name,
            String description,
            String basis,
            String categories,
            String recipients,
            int retentionDays,
            boolean sensitive) {
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update(
                "INSERT INTO privacy_processing_purpose"
                    + " (id,tenant_id,purpose_code,name,description,lawful_basis,data_categories,recipients,retention_days,sensitive,active,created_by_user_id,created_at,updated_at)"
                    + " VALUES (?,?,?,?,?,?,?,?,?,?,TRUE,?,?,?)",
                UUID.randomUUID(),
                tenant,
                code,
                name,
                description,
                basis,
                categories,
                recipients,
                retentionDays,
                sensitive,
                admin,
                now,
                now);
    }

    private void retention(
            UUID tenant, UUID admin, String category, int days, String action, boolean legalHold) {
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update(
                "INSERT INTO privacy_retention_policy"
                    + " (id,tenant_id,data_category,retention_days,expiry_action,legal_hold,last_reviewed_at,updated_by_user_id,updated_at)"
                    + " VALUES (?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(),
                tenant,
                category,
                days,
                action,
                legalHold,
                now,
                admin,
                now);
    }

    private UUID id(String sql, Object... args) {
        List<UUID> values = jdbc.query(sql, (result, row) -> result.getObject(1, UUID.class), args);
        if (values.isEmpty())
            throw new IllegalStateException("Gouvernance de données démo incomplète");
        return values.getFirst();
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }
}
