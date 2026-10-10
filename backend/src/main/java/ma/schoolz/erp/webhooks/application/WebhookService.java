package ma.schoolz.erp.webhooks.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.identity.IdentityAuditTrail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookService {
    public static final Set<String> EVENT_TYPES =
            Set.of(
                    "student.created",
                    "student.enrolled",
                    "invoice.created",
                    "invoice.paid",
                    "absence.created",
                    "grade.published");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final IdentityAuditTrail audit;
    private final Clock clock;
    private final WebhookSecretCipher cipher;
    private final WebhookTransport transport;

    public WebhookService(
            JdbcTemplate jdbc,
            ObjectMapper json,
            IdentityAuditTrail audit,
            Clock clock,
            WebhookSecretCipher cipher,
            WebhookTransport transport) {
        this.jdbc = jdbc;
        this.json = json;
        this.audit = audit;
        this.clock = clock;
        this.cipher = cipher;
        this.transport = transport;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('webhook.read')")
    public Dashboard dashboard(SchoolPrincipal actor) {
        List<SubscriptionView> subscriptions = subscriptions(actor.tenantId());
        List<DeliveryView> deliveries = deliveries(actor.tenantId());
        List<AttemptView> attempts = attempts(actor.tenantId());
        List<DeadLetterView> deadLetters = deadLetters(actor.tenantId());
        return new Dashboard(
                clock.instant(),
                new Statistics(
                        subscriptions.size(),
                        subscriptions.stream().filter(SubscriptionView::active).count(),
                        deliveries.stream()
                                .filter(delivery -> "DELIVERED".equals(delivery.status()))
                                .count(),
                        deliveries.stream()
                                .filter(delivery -> "RETRY_SCHEDULED".equals(delivery.status()))
                                .count(),
                        deadLetters.stream().filter(letter -> letter.resolvedAt() == null).count()),
                EVENT_TYPES.stream().sorted().toList(),
                subscriptions,
                deliveries,
                attempts,
                deadLetters);
    }

    @Transactional
    @PreAuthorize("hasAuthority('webhook.manage')")
    public CreatedSubscription create(
            SchoolPrincipal actor, SubscriptionCommand command, String ipAddress) {
        String name = required(command == null ? null : command.name(), "Le nom", 160);
        URI endpoint = endpoint(command.endpointUrl());
        Set<String> eventTypes = eventTypes(command.eventTypes());
        int maxAttempts = bounded(command.maxAttempts(), 5, 1, 10, "Tentatives");
        int timeout = bounded(command.timeoutSeconds(), 10, 1, 30, "Délai");
        String secret = generateSecret();
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                """
                INSERT INTO webhook_subscription
                    (id,tenant_id,name,endpoint_url,event_types_json,secret_prefix,secret_hash,
                     secret_ciphertext,max_attempts,timeout_seconds,active,created_by_user_id,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,TRUE,?,?)
                """,
                id,
                actor.tenantId(),
                name,
                endpoint.toString(),
                write(eventTypes.stream().sorted().toList()),
                prefix(secret),
                sha256(secret),
                cipher.encrypt(secret),
                maxAttempts,
                timeout,
                actor.accountId(),
                Timestamp.from(now));
        SubscriptionView created = subscription(actor.tenantId(), id);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "WEBHOOK_SUBSCRIPTION_CREATED",
                "webhook-subscription",
                id.toString(),
                ipAddress,
                Map.of("eventTypes", eventTypes, "endpointHost", endpoint.getHost()),
                null,
                created);
        return new CreatedSubscription(created, secret);
    }

    @Transactional
    @PreAuthorize("hasAuthority('webhook.manage')")
    public SubscriptionView revoke(
            SchoolPrincipal actor, UUID subscriptionId, ReasonCommand command, String ipAddress) {
        SubscriptionView before = subscription(actor.tenantId(), subscriptionId);
        if (!before.active()) return before;
        String reason = required(command == null ? null : command.reason(), "Le motif", 500);
        jdbc.update(
                """
                UPDATE webhook_subscription SET active=FALSE,revoked_by_user_id=?,revoked_at=?
                WHERE tenant_id=? AND id=? AND active=TRUE
                """,
                actor.accountId(),
                Timestamp.from(clock.instant()),
                actor.tenantId(),
                subscriptionId);
        SubscriptionView after = subscription(actor.tenantId(), subscriptionId);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "WEBHOOK_SUBSCRIPTION_REVOKED",
                "webhook-subscription",
                subscriptionId.toString(),
                ipAddress,
                Map.of("reason", reason),
                before,
                after);
        return after;
    }

    @Transactional
    @PreAuthorize("hasAuthority('webhook.manage')")
    public EventView publish(SchoolPrincipal actor, EventCommand command, String ipAddress) {
        String type = normalizeEventType(command == null ? null : command.eventType());
        String idempotency =
                required(command == null ? null : command.idempotencyKey(), "Idempotency-Key", 160);
        Map<String, Object> payload = command.payload() == null ? Map.of() : command.payload();
        String payloadJson = write(payload);
        if (payloadJson.length() > 20000)
            throw new IllegalArgumentException("Payload trop volumineux");
        List<EventView> duplicate =
                jdbc.query(
                        "SELECT * FROM webhook_event WHERE tenant_id=? AND idempotency_key=?",
                        this::mapEvent,
                        actor.tenantId(),
                        idempotency);
        if (!duplicate.isEmpty()) return duplicate.getFirst();

        UUID eventId = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                """
                INSERT INTO webhook_event
                    (id,tenant_id,event_type,idempotency_key,payload_json,occurred_at,
                     created_by_user_id,created_at)
                VALUES (?,?,?,?,?,?,?,?)
                """,
                eventId,
                actor.tenantId(),
                type,
                idempotency,
                payloadJson,
                Timestamp.from(now),
                actor.accountId(),
                Timestamp.from(now));
        List<SubscriptionView> matching =
                subscriptions(actor.tenantId()).stream()
                        .filter(SubscriptionView::active)
                        .filter(subscription -> subscription.eventTypes().contains(type))
                        .toList();
        for (SubscriptionView subscription : matching) {
            jdbc.update(
                    """
                    INSERT INTO webhook_delivery
                        (id,tenant_id,subscription_id,event_id,status,attempt_count,
                         next_attempt_at,created_at,updated_at)
                    VALUES (?,?,?,?, 'PENDING',0,?,?,?)
                    """,
                    UUID.randomUUID(),
                    actor.tenantId(),
                    subscription.id(),
                    eventId,
                    Timestamp.from(now),
                    Timestamp.from(now),
                    Timestamp.from(now));
        }
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "WEBHOOK_EVENT_PUBLISHED",
                "webhook-event",
                eventId.toString(),
                ipAddress,
                Map.of("eventType", type, "deliveryCount", matching.size()),
                null,
                Map.of("idempotencyKey", idempotency));
        return event(actor.tenantId(), eventId);
    }

    @Transactional
    @PreAuthorize("hasAuthority('webhook.manage')")
    public ProcessResult processDue(SchoolPrincipal actor) {
        List<UUID> due =
                jdbc.query(
                        """
                        SELECT id FROM webhook_delivery
                        WHERE tenant_id=? AND status IN ('PENDING','RETRY_SCHEDULED')
                          AND (next_attempt_at IS NULL OR next_attempt_at<=?)
                        ORDER BY created_at FETCH FIRST 20 ROWS ONLY
                        """,
                        (rs, row) -> rs.getObject(1, UUID.class),
                        actor.tenantId(),
                        Timestamp.from(clock.instant()));
        int delivered = 0;
        int deferred = 0;
        int dead = 0;
        for (UUID id : due) {
            DeliveryView result = deliver(actor.tenantId(), id, false);
            if ("DELIVERED".equals(result.status())) delivered++;
            else if ("DEAD_LETTER".equals(result.status())) dead++;
            else deferred++;
        }
        return new ProcessResult(due.size(), delivered, deferred, dead);
    }

    @Transactional
    @PreAuthorize("hasAuthority('webhook.manage')")
    public DeliveryView retry(
            SchoolPrincipal actor, UUID deliveryId, ReasonCommand command, String ipAddress) {
        DeliveryView before = delivery(actor.tenantId(), deliveryId);
        String reason = required(command == null ? null : command.reason(), "Le motif", 500);
        if (!"DEAD_LETTER".equals(before.status()) && !"RETRY_SCHEDULED".equals(before.status())) {
            throw new IllegalArgumentException("Cette livraison ne nécessite pas de reprise");
        }
        if ("DEAD_LETTER".equals(before.status())) {
            jdbc.update(
                    """
                    UPDATE webhook_dead_letter
                    SET resolved_at=?,resolved_by_user_id=?,resolution_note=?
                    WHERE tenant_id=? AND delivery_id=? AND resolved_at IS NULL
                    """,
                    Timestamp.from(clock.instant()),
                    actor.accountId(),
                    reason,
                    actor.tenantId(),
                    deliveryId);
        }
        jdbc.update(
                "UPDATE webhook_delivery SET status='PENDING',next_attempt_at=?,updated_at=?"
                        + " WHERE tenant_id=? AND id=?",
                Timestamp.from(clock.instant()),
                Timestamp.from(clock.instant()),
                actor.tenantId(),
                deliveryId);
        DeliveryView after = deliver(actor.tenantId(), deliveryId, true);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "WEBHOOK_DELIVERY_RETRIED",
                "webhook-delivery",
                deliveryId.toString(),
                ipAddress,
                Map.of("reason", reason),
                before,
                after);
        return after;
    }

    private DeliveryView deliver(UUID tenantId, UUID deliveryId, boolean forced) {
        DeliveryContext context = context(tenantId, deliveryId);
        if (!context.subscriptionActive()) {
            return failWithoutTransport(context, "Abonnement révoqué", forced);
        }
        int attemptNumber = context.attemptCount() + 1;
        if (!forced && attemptNumber > context.maxAttempts()) {
            return deadLetter(context, "Nombre maximal de tentatives atteint");
        }
        Instant started = clock.instant();
        long startedNanos = System.nanoTime();
        long signatureTimestamp = started.getEpochSecond();
        String envelope =
                write(
                        Map.of(
                                "id", context.eventId(),
                                "type", context.eventType(),
                                "occurredAt", context.occurredAt(),
                                "data", readMap(context.payloadJson())));
        String signature =
                signature(
                        cipher.decrypt(context.secretCiphertext()),
                        signatureTimestamp,
                        context.eventId(),
                        envelope);
        UUID requestId = UUID.randomUUID();
        WebhookTransport.Result result =
                transport.send(
                        URI.create(context.endpointUrl()),
                        envelope,
                        Map.of(
                                "Content-Type", "application/json",
                                "User-Agent", "SchoolZ-Webhooks/1.0",
                                "X-SchoolZ-Event-Id", context.eventId().toString(),
                                "X-SchoolZ-Delivery-Id", context.deliveryId().toString(),
                                "X-SchoolZ-Request-Id", requestId.toString(),
                                "X-SchoolZ-Timestamp", String.valueOf(signatureTimestamp),
                                "X-SchoolZ-Signature", signature),
                        Duration.ofSeconds(context.timeoutSeconds()));
        long duration = Math.max(0, Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());
        String error = result.delivered() ? null : safeError(result);
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
                attemptNumber,
                requestId,
                signatureTimestamp,
                signature,
                result.statusCode(),
                result.delivered() ? "DELIVERED" : "FAILED",
                error,
                duration,
                Timestamp.from(started));
        if (result.delivered()) {
            jdbc.update(
                    """
                    UPDATE webhook_delivery
                    SET status='DELIVERED',attempt_count=?,next_attempt_at=NULL,delivered_at=?,
                        last_http_status=?,last_error=NULL,updated_at=?
                    WHERE tenant_id=? AND id=?
                    """,
                    attemptNumber,
                    Timestamp.from(clock.instant()),
                    result.statusCode(),
                    Timestamp.from(clock.instant()),
                    tenantId,
                    deliveryId);
            return delivery(tenantId, deliveryId);
        }
        if (forced || attemptNumber >= context.maxAttempts()) {
            jdbc.update(
                    """
                    UPDATE webhook_delivery
                    SET status='DEAD_LETTER',attempt_count=?,next_attempt_at=NULL,
                        last_http_status=?,last_error=?,updated_at=? WHERE tenant_id=? AND id=?
                    """,
                    attemptNumber,
                    result.statusCode(),
                    error,
                    Timestamp.from(clock.instant()),
                    tenantId,
                    deliveryId);
            return deadLetter(context, error);
        }
        Instant next = clock.instant().plusSeconds(60L << Math.min(attemptNumber - 1, 6));
        jdbc.update(
                """
                UPDATE webhook_delivery
                SET status='RETRY_SCHEDULED',attempt_count=?,next_attempt_at=?,
                    last_http_status=?,last_error=?,updated_at=? WHERE tenant_id=? AND id=?
                """,
                attemptNumber,
                Timestamp.from(next),
                result.statusCode(),
                error,
                Timestamp.from(clock.instant()),
                tenantId,
                deliveryId);
        return delivery(tenantId, deliveryId);
    }

    private DeliveryView failWithoutTransport(
            DeliveryContext context, String error, boolean forced) {
        if (forced || context.attemptCount() + 1 >= context.maxAttempts()) {
            jdbc.update(
                    "UPDATE webhook_delivery SET status='DEAD_LETTER',last_error=?,updated_at=?"
                            + " WHERE tenant_id=? AND id=?",
                    error,
                    Timestamp.from(clock.instant()),
                    context.tenantId(),
                    context.deliveryId());
            return deadLetter(context, error);
        }
        jdbc.update(
                "UPDATE webhook_delivery SET status='RETRY_SCHEDULED',next_attempt_at=?,"
                        + "last_error=?,updated_at=? WHERE tenant_id=? AND id=?",
                Timestamp.from(clock.instant().plusSeconds(60)),
                error,
                Timestamp.from(clock.instant()),
                context.tenantId(),
                context.deliveryId());
        return delivery(context.tenantId(), context.deliveryId());
    }

    private DeliveryView deadLetter(DeliveryContext context, String reason) {
        jdbc.update(
                "UPDATE webhook_delivery SET status='DEAD_LETTER',next_attempt_at=NULL,"
                        + "last_error=?,updated_at=? WHERE tenant_id=? AND id=?",
                reason,
                Timestamp.from(clock.instant()),
                context.tenantId(),
                context.deliveryId());
        Integer count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM webhook_dead_letter WHERE tenant_id=? AND"
                                + " delivery_id=?",
                        Integer.class,
                        context.tenantId(),
                        context.deliveryId());
        if (count != null && count > 0) {
            jdbc.update(
                    """
                    UPDATE webhook_dead_letter
                    SET reason=?,failed_at=?,resolved_at=NULL,resolved_by_user_id=NULL,resolution_note=NULL
                    WHERE tenant_id=? AND delivery_id=?
                    """,
                    reason,
                    Timestamp.from(clock.instant()),
                    context.tenantId(),
                    context.deliveryId());
        } else {
            jdbc.update(
                    "INSERT INTO webhook_dead_letter"
                            + " (id,tenant_id,delivery_id,reason,failed_at) VALUES (?,?,?,?,?)",
                    UUID.randomUUID(),
                    context.tenantId(),
                    context.deliveryId(),
                    reason,
                    Timestamp.from(clock.instant()));
        }
        return delivery(context.tenantId(), context.deliveryId());
    }

    private List<SubscriptionView> subscriptions(UUID tenantId) {
        return jdbc.query(
                "SELECT * FROM webhook_subscription WHERE tenant_id=? ORDER BY created_at DESC",
                this::mapSubscription,
                tenantId);
    }

    private SubscriptionView subscription(UUID tenantId, UUID id) {
        List<SubscriptionView> rows =
                jdbc.query(
                        "SELECT * FROM webhook_subscription WHERE tenant_id=? AND id=?",
                        this::mapSubscription,
                        tenantId,
                        id);
        if (rows.isEmpty()) throw new IllegalArgumentException("Abonnement webhook introuvable");
        return rows.getFirst();
    }

    private SubscriptionView mapSubscription(ResultSet rs, int row) throws SQLException {
        return new SubscriptionView(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("endpoint_url"),
                readStrings(rs.getString("event_types_json")),
                rs.getString("secret_prefix"),
                rs.getInt("max_attempts"),
                rs.getInt("timeout_seconds"),
                rs.getBoolean("active"),
                instant(rs, "created_at"),
                instant(rs, "revoked_at"));
    }

    private EventView event(UUID tenantId, UUID id) {
        List<EventView> rows =
                jdbc.query(
                        "SELECT * FROM webhook_event WHERE tenant_id=? AND id=?",
                        this::mapEvent,
                        tenantId,
                        id);
        if (rows.isEmpty()) throw new IllegalArgumentException("Événement webhook introuvable");
        return rows.getFirst();
    }

    private EventView mapEvent(ResultSet rs, int row) throws SQLException {
        UUID tenantId = rs.getObject("tenant_id", UUID.class);
        UUID id = rs.getObject("id", UUID.class);
        Integer count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM webhook_delivery WHERE tenant_id=? AND event_id=?",
                        Integer.class,
                        tenantId,
                        id);
        return new EventView(
                id,
                rs.getString("event_type"),
                rs.getString("idempotency_key"),
                readMap(rs.getString("payload_json")),
                instant(rs, "occurred_at"),
                count == null ? 0 : count);
    }

    private List<DeliveryView> deliveries(UUID tenantId) {
        return jdbc.query(
                """
                SELECT d.*,s.name subscription_name,s.endpoint_url,e.event_type,e.idempotency_key
                FROM webhook_delivery d
                JOIN webhook_subscription s ON s.id=d.subscription_id AND s.tenant_id=d.tenant_id
                JOIN webhook_event e ON e.id=d.event_id AND e.tenant_id=d.tenant_id
                WHERE d.tenant_id=? ORDER BY d.created_at DESC FETCH FIRST 50 ROWS ONLY
                """,
                this::mapDelivery,
                tenantId);
    }

    private DeliveryView delivery(UUID tenantId, UUID id) {
        List<DeliveryView> rows =
                jdbc.query(
                        """
                        SELECT d.*,s.name subscription_name,s.endpoint_url,e.event_type,e.idempotency_key
                        FROM webhook_delivery d
                        JOIN webhook_subscription s ON s.id=d.subscription_id AND s.tenant_id=d.tenant_id
                        JOIN webhook_event e ON e.id=d.event_id AND e.tenant_id=d.tenant_id
                        WHERE d.tenant_id=? AND d.id=?
                        """,
                        this::mapDelivery,
                        tenantId,
                        id);
        if (rows.isEmpty()) throw new IllegalArgumentException("Livraison webhook introuvable");
        return rows.getFirst();
    }

    private DeliveryView mapDelivery(ResultSet rs, int row) throws SQLException {
        return new DeliveryView(
                rs.getObject("id", UUID.class),
                rs.getObject("event_id", UUID.class),
                rs.getObject("subscription_id", UUID.class),
                rs.getString("subscription_name"),
                rs.getString("endpoint_url"),
                rs.getString("event_type"),
                rs.getString("idempotency_key"),
                rs.getString("status"),
                rs.getInt("attempt_count"),
                instant(rs, "next_attempt_at"),
                instant(rs, "delivered_at"),
                (Integer) rs.getObject("last_http_status"),
                rs.getString("last_error"),
                instant(rs, "updated_at"));
    }

    private DeliveryContext context(UUID tenantId, UUID id) {
        List<DeliveryContext> rows =
                jdbc.query(
                        """
                        SELECT d.id delivery_id,d.tenant_id,d.attempt_count,s.active subscription_active,
                               s.endpoint_url,s.secret_ciphertext,s.max_attempts,s.timeout_seconds,
                               e.id event_id,e.event_type,e.payload_json,e.occurred_at
                        FROM webhook_delivery d
                        JOIN webhook_subscription s ON s.id=d.subscription_id AND s.tenant_id=d.tenant_id
                        JOIN webhook_event e ON e.id=d.event_id AND e.tenant_id=d.tenant_id
                        WHERE d.tenant_id=? AND d.id=?
                        """,
                        (rs, row) ->
                                new DeliveryContext(
                                        rs.getObject("delivery_id", UUID.class),
                                        rs.getObject("tenant_id", UUID.class),
                                        rs.getBoolean("subscription_active"),
                                        rs.getString("endpoint_url"),
                                        rs.getString("secret_ciphertext"),
                                        rs.getInt("max_attempts"),
                                        rs.getInt("timeout_seconds"),
                                        rs.getInt("attempt_count"),
                                        rs.getObject("event_id", UUID.class),
                                        rs.getString("event_type"),
                                        rs.getString("payload_json"),
                                        instant(rs, "occurred_at")),
                        tenantId,
                        id);
        if (rows.isEmpty()) throw new IllegalArgumentException("Livraison webhook introuvable");
        return rows.getFirst();
    }

    private List<AttemptView> attempts(UUID tenantId) {
        return jdbc.query(
                """
                SELECT a.*,e.event_type,s.name subscription_name
                FROM webhook_delivery_attempt a
                JOIN webhook_delivery d ON d.id=a.delivery_id AND d.tenant_id=a.tenant_id
                JOIN webhook_event e ON e.id=d.event_id AND e.tenant_id=d.tenant_id
                JOIN webhook_subscription s ON s.id=d.subscription_id AND s.tenant_id=d.tenant_id
                WHERE a.tenant_id=? ORDER BY a.attempted_at DESC FETCH FIRST 50 ROWS ONLY
                """,
                (rs, row) ->
                        new AttemptView(
                                rs.getObject("id", UUID.class),
                                rs.getObject("delivery_id", UUID.class),
                                rs.getString("event_type"),
                                rs.getString("subscription_name"),
                                rs.getInt("attempt_number"),
                                rs.getObject("request_id", UUID.class),
                                rs.getLong("signature_timestamp"),
                                rs.getString("signature_value"),
                                (Integer) rs.getObject("http_status"),
                                rs.getString("outcome"),
                                rs.getString("error_message"),
                                rs.getLong("duration_ms"),
                                instant(rs, "attempted_at")),
                tenantId);
    }

    private List<DeadLetterView> deadLetters(UUID tenantId) {
        return jdbc.query(
                """
                SELECT dl.*,e.event_type,s.name subscription_name
                FROM webhook_dead_letter dl
                JOIN webhook_delivery d ON d.id=dl.delivery_id AND d.tenant_id=dl.tenant_id
                JOIN webhook_event e ON e.id=d.event_id AND e.tenant_id=d.tenant_id
                JOIN webhook_subscription s ON s.id=d.subscription_id AND s.tenant_id=d.tenant_id
                WHERE dl.tenant_id=? ORDER BY dl.failed_at DESC FETCH FIRST 30 ROWS ONLY
                """,
                (rs, row) ->
                        new DeadLetterView(
                                rs.getObject("id", UUID.class),
                                rs.getObject("delivery_id", UUID.class),
                                rs.getString("event_type"),
                                rs.getString("subscription_name"),
                                rs.getString("reason"),
                                instant(rs, "failed_at"),
                                instant(rs, "resolved_at"),
                                rs.getString("resolution_note")),
                tenantId);
    }

    private URI endpoint(String value) {
        String required = required(value, "L'URL", 1000);
        URI uri;
        try {
            uri = URI.create(required);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("URL webhook invalide");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalArgumentException("Une URL HTTPS absolue est requise");
        }
        if (uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException(
                    "Identifiants et fragments sont interdits dans l'URL");
        }
        return uri;
    }

    private Set<String> eventTypes(List<String> values) {
        if (values == null || values.isEmpty())
            throw new IllegalArgumentException("Au moins un événement est requis");
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) normalized.add(normalizeEventType(value));
        return Set.copyOf(normalized);
    }

    private String normalizeEventType(String value) {
        String normalized = required(value, "Le type d'événement", 100).toLowerCase(Locale.ROOT);
        if (!EVENT_TYPES.contains(normalized))
            throw new IllegalArgumentException("Type d'événement inconnu");
        return normalized;
    }

    private int bounded(Integer value, int fallback, int minimum, int maximum, String label) {
        int result = value == null ? fallback : value;
        if (result < minimum || result > maximum)
            throw new IllegalArgumentException(label + " hors limites");
        return result;
    }

    private String required(String value, String label, int maximum) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(label + " est requis");
        String result = value.trim();
        if (result.length() > maximum) throw new IllegalArgumentException(label + " est trop long");
        return result;
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("JSON webhook invalide", exception);
        }
    }

    private List<String> readStrings(String value) {
        try {
            return json.readValue(value, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Événements webhook persistés invalides", exception);
        }
    }

    private Map<String, Object> readMap(String value) {
        try {
            return json.readValue(value, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Payload webhook persisté invalide", exception);
        }
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    public static String sha256(String value) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static String signature(String secret, long timestamp, UUID eventId, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String signed = timestamp + "." + eventId + "." + payload;
            return "v1="
                    + java.util.HexFormat.of()
                            .formatHex(mac.doFinal(signed.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Impossible de signer le webhook", exception);
        }
    }

    private String generateSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String prefix(String secret) {
        return secret.substring(0, Math.min(18, secret.length()));
    }

    private String safeError(WebhookTransport.Result result) {
        String value =
                result.error() != null
                        ? result.error()
                        : "Réponse HTTP " + String.valueOf(result.statusCode());
        return value.substring(0, Math.min(value.length(), 1000));
    }

    public record SubscriptionCommand(
            String name,
            String endpointUrl,
            List<String> eventTypes,
            Integer maxAttempts,
            Integer timeoutSeconds) {}

    public record EventCommand(
            String eventType, String idempotencyKey, Map<String, Object> payload) {}

    public record ReasonCommand(String reason) {}

    public record Dashboard(
            Instant generatedAt,
            Statistics statistics,
            List<String> eventTypes,
            List<SubscriptionView> subscriptions,
            List<DeliveryView> deliveries,
            List<AttemptView> attempts,
            List<DeadLetterView> deadLetters) {}

    public record Statistics(
            int subscriptionCount,
            long activeCount,
            long deliveredCount,
            long retryCount,
            long deadLetterCount) {}

    public record CreatedSubscription(SubscriptionView subscription, String signingSecret) {}

    public record SubscriptionView(
            UUID id,
            String name,
            String endpointUrl,
            List<String> eventTypes,
            String secretPrefix,
            int maxAttempts,
            int timeoutSeconds,
            boolean active,
            Instant createdAt,
            Instant revokedAt) {}

    public record EventView(
            UUID id,
            String eventType,
            String idempotencyKey,
            Map<String, Object> payload,
            Instant occurredAt,
            int deliveryCount) {}

    public record DeliveryView(
            UUID id,
            UUID eventId,
            UUID subscriptionId,
            String subscriptionName,
            String endpointUrl,
            String eventType,
            String idempotencyKey,
            String status,
            int attemptCount,
            Instant nextAttemptAt,
            Instant deliveredAt,
            Integer lastHttpStatus,
            String lastError,
            Instant updatedAt) {}

    public record AttemptView(
            UUID id,
            UUID deliveryId,
            String eventType,
            String subscriptionName,
            int attemptNumber,
            UUID requestId,
            long signatureTimestamp,
            String signature,
            Integer httpStatus,
            String outcome,
            String error,
            long durationMs,
            Instant attemptedAt) {}

    public record DeadLetterView(
            UUID id,
            UUID deliveryId,
            String eventType,
            String subscriptionName,
            String reason,
            Instant failedAt,
            Instant resolvedAt,
            String resolutionNote) {}

    public record ProcessResult(int processed, int delivered, int deferred, int deadLettered) {}

    private record DeliveryContext(
            UUID deliveryId,
            UUID tenantId,
            boolean subscriptionActive,
            String endpointUrl,
            String secretCiphertext,
            int maxAttempts,
            int timeoutSeconds,
            int attemptCount,
            UUID eventId,
            String eventType,
            String payloadJson,
            Instant occurredAt) {}
}
