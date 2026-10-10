package ma.schoolz.erp.communications.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import ma.schoolz.erp.communications.application.NotificationChannelAdapter.NotificationEnvelope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
class NotificationDeliveryService {
    private static final List<String> CHANNELS =
            List.of("IN_APP", "PUSH", "EMAIL", "SMS", "WHATSAPP");

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final List<NotificationChannelAdapter> adapters;

    NotificationDeliveryService(
            JdbcTemplate jdbc, Clock clock, List<NotificationChannelAdapter> adapters) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.adapters = adapters;
    }

    UUID notifyParent(
            UUID tenantId,
            UUID portalAccountId,
            UUID studentId,
            String category,
            String title,
            String body,
            String resourceType,
            UUID resourceId) {
        return create(
                tenantId,
                "PARENT",
                null,
                portalAccountId,
                studentId,
                category,
                title,
                body,
                resourceType,
                resourceId);
    }

    UUID notifyInternal(
            UUID tenantId,
            UUID userId,
            String category,
            String title,
            String body,
            String resourceType,
            UUID resourceId) {
        return create(
                tenantId,
                "INTERNAL",
                userId,
                null,
                null,
                category,
                title,
                body,
                resourceType,
                resourceId);
    }

    private UUID create(
            UUID tenantId,
            String recipientKind,
            UUID userId,
            UUID portalAccountId,
            UUID studentId,
            String category,
            String title,
            String body,
            String resourceType,
            UUID resourceId) {
        UUID notificationId = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                "INSERT INTO communication_notification"
                        + " (id,tenant_id,category,recipient_kind,recipient_user_id,"
                        + " recipient_portal_account_id,student_id,title,body,resource_type,"
                        + " resource_id,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                notificationId,
                tenantId,
                category,
                recipientKind,
                userId,
                portalAccountId,
                studentId,
                title,
                body,
                resourceType,
                resourceId,
                timestamp(now));

        Recipient recipient = recipient(tenantId, recipientKind, userId, portalAccountId);
        for (String channel : CHANNELS) {
            dispatch(notificationId, tenantId, recipient, category, channel, title, body, now);
        }
        return notificationId;
    }

    private void dispatch(
            UUID notificationId,
            UUID tenantId,
            Recipient recipient,
            String category,
            String channel,
            String title,
            String body,
            Instant now) {
        String idempotencyKey = "notification:" + notificationId + ":" + channel;
        if (!preferenceEnabled(tenantId, recipient, category, channel)) {
            insertDelivery(
                    notificationId,
                    tenantId,
                    channel,
                    "SKIPPED",
                    "preference",
                    null,
                    idempotencyKey,
                    now,
                    null,
                    "Canal désactivé par préférence");
            return;
        }
        if ("IN_APP".equals(channel)) {
            insertDelivery(
                    notificationId,
                    tenantId,
                    channel,
                    "DELIVERED",
                    "in-app",
                    recipient.kind().toLowerCase(Locale.ROOT),
                    idempotencyKey,
                    now,
                    now,
                    null);
            return;
        }
        String endpoint = recipient.endpoint(channel);
        if (endpoint == null || endpoint.isBlank()) {
            insertDelivery(
                    notificationId,
                    tenantId,
                    channel,
                    "SKIPPED",
                    "unavailable",
                    null,
                    idempotencyKey,
                    now,
                    null,
                    "Aucun destinataire pour ce canal");
            return;
        }
        NotificationChannelAdapter adapter =
                adapters.stream()
                        .filter(candidate -> candidate.supports(channel))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new CommunicationValidationException(
                                                "Aucun adaptateur pour " + channel));
        try {
            NotificationChannelAdapter.DispatchResult result =
                    adapter.dispatch(
                            new NotificationEnvelope(
                                    tenantId,
                                    notificationId,
                                    channel,
                                    endpoint,
                                    title,
                                    body,
                                    idempotencyKey));
            insertDelivery(
                    notificationId,
                    tenantId,
                    channel,
                    result.status(),
                    result.adapterKey(),
                    mask(endpoint),
                    idempotencyKey,
                    now,
                    "DELIVERED".equals(result.status()) ? now : null,
                    null);
        } catch (RuntimeException exception) {
            insertDelivery(
                    notificationId,
                    tenantId,
                    channel,
                    "FAILED",
                    "error",
                    mask(endpoint),
                    idempotencyKey,
                    now,
                    null,
                    trim(exception.getMessage(), 500));
        }
    }

    private boolean preferenceEnabled(
            UUID tenantId, Recipient recipient, String category, String channel) {
        List<Boolean> values =
                jdbc.query(
                        "SELECT enabled FROM communication_notification_preference"
                                + " WHERE tenant_id=? AND recipient_kind=? AND recipient_key=?"
                                + " AND category=? AND channel=?",
                        (result, row) -> result.getBoolean("enabled"),
                        tenantId,
                        recipient.kind(),
                        recipient.id().toString(),
                        category,
                        channel);
        return values.isEmpty() ? "IN_APP".equals(channel) : values.getFirst();
    }

    private Recipient recipient(UUID tenantId, String kind, UUID userId, UUID portalAccountId) {
        if ("PARENT".equals(kind)) {
            return jdbc.queryForObject(
                    "SELECT id,email,phone FROM admission_portal_account"
                            + " WHERE tenant_id=? AND id=? AND status='ACTIVE'",
                    (result, row) ->
                            new Recipient(
                                    "PARENT",
                                    result.getObject("id", UUID.class),
                                    result.getString("email"),
                                    result.getString("phone")),
                    tenantId,
                    portalAccountId);
        }
        return jdbc.queryForObject(
                "SELECT id,email FROM identity_user_account"
                        + " WHERE tenant_id=? AND id=? AND status='ACTIVE'",
                (result, row) ->
                        new Recipient(
                                "INTERNAL",
                                result.getObject("id", UUID.class),
                                result.getString("email"),
                                null),
                tenantId,
                userId);
    }

    private void insertDelivery(
            UUID notificationId,
            UUID tenantId,
            String channel,
            String status,
            String adapterKey,
            String recipientMasked,
            String idempotencyKey,
            Instant attemptedAt,
            Instant deliveredAt,
            String error) {
        jdbc.update(
                "INSERT INTO communication_notification_delivery"
                    + " (id,tenant_id,notification_id,channel,status,adapter_key,"
                    + " recipient_masked,idempotency_key,attempted_at,delivered_at,error_message)"
                    + " VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(),
                tenantId,
                notificationId,
                channel,
                status,
                adapterKey,
                recipientMasked,
                idempotencyKey,
                timestamp(attemptedAt),
                deliveredAt == null ? null : timestamp(deliveredAt),
                error);
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private static String mask(String value) {
        if (value == null || value.length() < 5) return "***";
        int at = value.indexOf('@');
        if (at > 1) return value.substring(0, 2) + "***" + value.substring(at);
        return "***" + value.substring(value.length() - 3);
    }

    private static String trim(String value, int max) {
        if (value == null) return "Erreur adaptateur";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private record Recipient(String kind, UUID id, String email, String phone) {
        String endpoint(String channel) {
            return switch (channel) {
                case "EMAIL" -> email;
                case "SMS", "WHATSAPP" -> phone;
                case "PUSH" -> kind.toLowerCase(Locale.ROOT) + ":" + id;
                default -> null;
            };
        }
    }
}
