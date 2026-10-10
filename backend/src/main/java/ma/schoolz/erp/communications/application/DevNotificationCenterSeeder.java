package ma.schoolz.erp.communications.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
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
@Order(360)
public class DevNotificationCenterSeeder implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final boolean enabled;
    private final String tenantSlug;

    public DevNotificationCenterSeeder(
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
        UUID tenantId = id("SELECT id FROM identity_tenant WHERE slug=?", tenantSlug);
        UUID adminId =
                id(
                        "SELECT id FROM identity_user_account WHERE tenant_id=?"
                                + " AND normalized_email='admin@schoolz.ma'",
                        tenantId);
        seedPolicy(tenantId);
        seedPreferences(tenantId, adminId);
        if (count(
                        "SELECT count(*) FROM communication_notification WHERE tenant_id=?"
                                + " AND recipient_user_id=? AND alert_key IS NOT NULL",
                        tenantId,
                        adminId)
                > 0) return;
        notification(
                tenantId,
                adminId,
                "Sécurité · nouvelle connexion",
                "Une connexion à la console a été détectée depuis Casablanca. Vérifiez-la si elle"
                        + " ne vous appartient pas.",
                "SECURITY_LOGIN",
                true,
                "USER",
                null,
                8,
                false);
        notification(
                tenantId,
                adminId,
                "3 dossiers d’admission à valider",
                "Les pièces sont complètes et attendent la décision de la direction.",
                "ADMISSION_REVIEW",
                false,
                "ADMISSION",
                null,
                70,
                false);
        notification(
                tenantId,
                adminId,
                "Échéance de règlement demain",
                "La facture SZ-FAC-2026-0001 arrive à échéance. La famille a reçu son rappel.",
                "PAYMENT_DUE",
                false,
                "INVOICE",
                null,
                190,
                true);
        notification(
                tenantId,
                adminId,
                "Le rapport hebdomadaire est prêt",
                "Les indicateurs de présence et de scolarité sont disponibles dans le cockpit.",
                "REPORT_READY",
                false,
                "DOCUMENT",
                null,
                1440,
                true);
    }

    private void seedPolicy(UUID tenantId) {
        if (count(
                        "SELECT count(*) FROM communication_notification_policy WHERE tenant_id=?"
                                + " AND alert_key='SECURITY_LOGIN' AND channel='EMAIL'",
                        tenantId)
                > 0) return;
        jdbc.update(
                "INSERT INTO communication_notification_policy"
                        + " (id,tenant_id,alert_key,channel,mandatory,active,updated_at)"
                        + " VALUES (?,?,'SECURITY_LOGIN','EMAIL',TRUE,TRUE,?)",
                UUID.randomUUID(),
                tenantId,
                Timestamp.from(clock.instant()));
    }

    private void seedPreferences(UUID tenantId, UUID userId) {
        for (String channel : List.of("PUSH", "EMAIL", "SMS")) {
            if (count(
                            "SELECT count(*) FROM communication_center_preference"
                                    + " WHERE tenant_id=? AND user_id=? AND channel=?",
                            tenantId,
                            userId,
                            channel)
                    > 0) continue;
            jdbc.update(
                    "INSERT INTO communication_center_preference"
                        + " (id,tenant_id,user_id,channel,enabled,updated_at) VALUES (?,?,?,?,?,?)",
                    UUID.randomUUID(),
                    tenantId,
                    userId,
                    channel,
                    !"SMS".equals(channel),
                    Timestamp.from(clock.instant()));
        }
    }

    private void notification(
            UUID tenantId,
            UUID userId,
            String title,
            String body,
            String alertKey,
            boolean critical,
            String resourceType,
            UUID resourceId,
            long minutesAgo,
            boolean read) {
        Timestamp created = Timestamp.from(clock.instant().minus(minutesAgo, ChronoUnit.MINUTES));
        jdbc.update(
                "INSERT INTO communication_notification"
                        + " (id,tenant_id,category,recipient_kind,recipient_user_id,title,body,"
                        + " resource_type,resource_id,created_at,read_at,critical,alert_key)"
                        + " VALUES (?,?,'AUTOMATED_NOTIFICATION','INTERNAL',?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(),
                tenantId,
                userId,
                title,
                body,
                resourceType,
                resourceId,
                created,
                read ? created : null,
                critical,
                alertKey);
    }

    private UUID id(String sql, Object... arguments) {
        List<UUID> values =
                jdbc.query(sql, (result, row) -> result.getObject(1, UUID.class), arguments);
        if (values.isEmpty())
            throw new IllegalStateException("Compte de démonstration introuvable");
        return values.getFirst();
    }

    private int count(String sql, Object... arguments) {
        Integer value = jdbc.queryForObject(sql, Integer.class, arguments);
        return value == null ? 0 : value;
    }
}
