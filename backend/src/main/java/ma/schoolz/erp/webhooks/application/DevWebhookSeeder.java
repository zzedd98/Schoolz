package ma.schoolz.erp.webhooks.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
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
@Order(340)
public class DevWebhookSeeder implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final TenantDirectory tenants;
    private final ObjectMapper json;
    private final Clock clock;
    private final WebhookSecretCipher cipher;
    private final boolean enabled;
    private final String tenantSlug;

    public DevWebhookSeeder(
            JdbcTemplate jdbc,
            TenantDirectory tenants,
            ObjectMapper json,
            Clock clock,
            WebhookSecretCipher cipher,
            @Value("${schoolz.demo.seed-enabled:false}") boolean enabled,
            @Value("${schoolz.demo.tenant-slug:}") String tenantSlug) {
        this.jdbc = jdbc;
        this.tenants = tenants;
        this.json = json;
        this.clock = clock;
        this.cipher = cipher;
        this.enabled = enabled;
        this.tenantSlug = tenantSlug;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) throws Exception {
        if (!enabled || tenantSlug.isBlank()) return;
        UUID tenantId =
                tenants.findActiveBySlug(tenantSlug)
                        .map(TenantDirectory.TenantSummary::id)
                        .orElse(null);
        if (tenantId == null) return;
        Integer existing =
                jdbc.queryForObject(
                        "SELECT count(*) FROM webhook_subscription WHERE tenant_id=?",
                        Integer.class,
                        tenantId);
        if (existing != null && existing > 0) return;
        List<UUID> actors =
                jdbc.query(
                        "SELECT id FROM identity_user_account WHERE tenant_id=? ORDER BY created_at"
                                + " FETCH FIRST 1 ROW ONLY",
                        (rs, row) -> rs.getObject(1, UUID.class),
                        tenantId);
        if (actors.isEmpty()) return;
        UUID actor = actors.getFirst();
        Instant now = clock.instant();
        UUID sis = UUID.randomUUID();
        UUID accounting = UUID.randomUUID();
        insertSubscription(
                tenantId,
                actor,
                sis,
                "SIS pédagogique",
                "https://sis-demo.schoolz.invalid/events",
                List.copyOf(WebhookService.EVENT_TYPES),
                "whsec_demo_m38_sis_schoolz",
                5,
                now.minusSeconds(86400));
        insertSubscription(
                tenantId,
                actor,
                accounting,
                "Comptabilité école",
                "https://accounting-demo.schoolz.invalid/hooks",
                List.of("invoice.created", "invoice.paid"),
                "whsec_demo_m38_accounting",
                4,
                now.minusSeconds(43200));

        seedDelivery(
                tenantId,
                actor,
                sis,
                "invoice.paid",
                "m38-demo-paid",
                "{\"invoiceNumber\":\"FAC-2026-0042\",\"amount\":38000}",
                "DELIVERED",
                1,
                204,
                null,
                now.minusSeconds(5400));
        seedDelivery(
                tenantId,
                actor,
                sis,
                "absence.created",
                "m38-demo-absence",
                "{\"studentNumber\":\"SZ-2026-0001\",\"justified\":false}",
                "RETRY_SCHEDULED",
                2,
                503,
                "Récepteur temporairement indisponible",
                now.minusSeconds(1200));
        seedDelivery(
                tenantId,
                actor,
                sis,
                "grade.published",
                "m38-demo-grade",
                "{\"studentNumber\":\"SZ-2026-0001\",\"assessment\":\"Synthèse\"}",
                "DEAD_LETTER",
                5,
                422,
                "Payload refusé après cinq tentatives",
                now.minusSeconds(300));
    }

    private void insertSubscription(
            UUID tenantId,
            UUID actor,
            UUID id,
            String name,
            String endpoint,
            List<String> events,
            String secret,
            int attempts,
            Instant createdAt)
            throws Exception {
        jdbc.update(
                """
                INSERT INTO webhook_subscription
                    (id,tenant_id,name,endpoint_url,event_types_json,secret_prefix,secret_hash,
                     secret_ciphertext,max_attempts,timeout_seconds,active,created_by_user_id,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,10,TRUE,?,?)
                """,
                id,
                tenantId,
                name,
                endpoint,
                json.writeValueAsString(events.stream().sorted().toList()),
                secret.substring(0, Math.min(18, secret.length())),
                WebhookService.sha256(secret),
                cipher.encrypt(secret),
                attempts,
                actor,
                Timestamp.from(createdAt));
    }

    private void seedDelivery(
            UUID tenantId,
            UUID actor,
            UUID subscriptionId,
            String eventType,
            String idempotency,
            String payload,
            String status,
            int attemptCount,
            Integer httpStatus,
            String error,
            Instant occurredAt) {
        UUID eventId = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO webhook_event
                    (id,tenant_id,event_type,idempotency_key,payload_json,occurred_at,
                     created_by_user_id,created_at) VALUES (?,?,?,?,?,?,?,?)
                """,
                eventId,
                tenantId,
                eventType,
                idempotency,
                payload,
                Timestamp.from(occurredAt),
                actor,
                Timestamp.from(occurredAt));
        Instant next = "RETRY_SCHEDULED".equals(status) ? occurredAt.plusSeconds(3600) : null;
        Instant delivered = "DELIVERED".equals(status) ? occurredAt.plusSeconds(2) : null;
        jdbc.update(
                """
                INSERT INTO webhook_delivery
                    (id,tenant_id,subscription_id,event_id,status,attempt_count,next_attempt_at,
                     delivered_at,last_http_status,last_error,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                deliveryId,
                tenantId,
                subscriptionId,
                eventId,
                status,
                attemptCount,
                next == null ? null : Timestamp.from(next),
                delivered == null ? null : Timestamp.from(delivered),
                httpStatus,
                error,
                Timestamp.from(occurredAt),
                Timestamp.from(occurredAt.plusSeconds(2)));
        for (int attempt = 1; attempt <= attemptCount; attempt++) {
            boolean last = attempt == attemptCount;
            String outcome = last && "DELIVERED".equals(status) ? "DELIVERED" : "FAILED";
            jdbc.update(
                    """
                    INSERT INTO webhook_delivery_attempt
                        (id,tenant_id,delivery_id,attempt_number,request_id,signature_timestamp,
                         signature_value,http_status,outcome,error_message,duration_ms,attempted_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                    """,
                    UUID.randomUUID(),
                    tenantId,
                    deliveryId,
                    attempt,
                    UUID.randomUUID(),
                    occurredAt.plusSeconds(attempt).getEpochSecond(),
                    "v1=" + "a".repeat(64),
                    last ? httpStatus : 503,
                    outcome,
                    "DELIVERED".equals(outcome) ? null : error,
                    80L + attempt * 15L,
                    Timestamp.from(occurredAt.plusSeconds(attempt)));
        }
        if ("DEAD_LETTER".equals(status)) {
            jdbc.update(
                    "INSERT INTO webhook_dead_letter"
                            + " (id,tenant_id,delivery_id,reason,failed_at) VALUES (?,?,?,?,?)",
                    UUID.randomUUID(),
                    tenantId,
                    deliveryId,
                    error,
                    Timestamp.from(occurredAt.plusSeconds(5)));
        }
    }
}
