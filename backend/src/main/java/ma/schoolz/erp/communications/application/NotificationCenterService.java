package ma.schoolz.erp.communications.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationCenterService {
    private static final Set<String> STATUSES = Set.of("ALL", "UNREAD", "READ", "ARCHIVED");
    private static final List<String> CHANNELS = List.of("PUSH", "EMAIL", "SMS");

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public NotificationCenterService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('communication.read')")
    public CenterResponse center(
            SchoolPrincipal actor, String requestedStatus, int page, int size) {
        if (page < 0 || size < 1 || size > 50)
            throw new CommunicationValidationException("Pagination invalide");
        String status = status(requestedStatus);
        StringBuilder sql =
                new StringBuilder(
                        "SELECT * FROM communication_notification WHERE tenant_id=?"
                                + " AND recipient_kind='INTERNAL' AND recipient_user_id=?");
        List<Object> params = new ArrayList<>(List.of(actor.tenantId(), actor.accountId()));
        switch (status) {
            case "UNREAD" -> sql.append(" AND archived_at IS NULL AND read_at IS NULL");
            case "READ" -> sql.append(" AND archived_at IS NULL AND read_at IS NOT NULL");
            case "ARCHIVED" -> sql.append(" AND archived_at IS NOT NULL");
            default -> sql.append(" AND archived_at IS NULL");
        }
        sql.append(" ORDER BY critical DESC,created_at DESC LIMIT ? OFFSET ?");
        params.add(size + 1);
        params.add(page * size);
        List<NotificationItem> rows = jdbc.query(sql.toString(), this::item, params.toArray());
        boolean hasNext = rows.size() > size;
        List<NotificationItem> items = hasNext ? rows.subList(0, size) : rows;
        Counts counts =
                new Counts(
                        count(actor, "archived_at IS NULL AND read_at IS NULL"),
                        count(actor, "archived_at IS NULL AND read_at IS NOT NULL"),
                        count(actor, "archived_at IS NOT NULL"));
        return new CenterResponse(
                clock.instant(), status, page, size, hasNext, counts, preferences(actor), items);
    }

    @Transactional
    @PreAuthorize("hasAuthority('communication.read')")
    public NotificationItem markRead(SchoolPrincipal actor, UUID notificationId) {
        int changed =
                jdbc.update(
                        "UPDATE communication_notification SET read_at=COALESCE(read_at,?)"
                                + " WHERE tenant_id=? AND id=? AND recipient_kind='INTERNAL'"
                                + " AND recipient_user_id=? AND archived_at IS NULL",
                        Timestamp.from(clock.instant()),
                        actor.tenantId(),
                        notificationId,
                        actor.accountId());
        if (changed == 0) throw new CommunicationNotFoundException("Notification introuvable");
        return one(actor, notificationId);
    }

    @Transactional
    @PreAuthorize("hasAuthority('communication.read')")
    public NotificationItem archive(SchoolPrincipal actor, UUID notificationId) {
        Instant now = clock.instant();
        int changed =
                jdbc.update(
                        "UPDATE communication_notification SET"
                                + " archived_at=?,read_at=COALESCE(read_at,?) WHERE tenant_id=? AND"
                                + " id=? AND recipient_kind='INTERNAL' AND recipient_user_id=? AND"
                                + " archived_at IS NULL",
                        Timestamp.from(now),
                        Timestamp.from(now),
                        actor.tenantId(),
                        notificationId,
                        actor.accountId());
        if (changed == 0) throw new CommunicationNotFoundException("Notification introuvable");
        return one(actor, notificationId);
    }

    @Transactional
    @PreAuthorize("hasAuthority('communication.read')")
    public List<PreferenceView> savePreferences(SchoolPrincipal actor, PreferenceCommand command) {
        if (command == null || command.channels() == null || command.channels().isEmpty())
            throw new CommunicationValidationException("Au moins une préférence est requise");
        Map<String, Boolean> requested = command.channels();
        for (String channel : requested.keySet()) {
            if (!CHANNELS.contains(channel))
                throw new CommunicationValidationException("Canal de notification inconnu");
            if (Boolean.FALSE.equals(requested.get(channel))
                    && mandatory(actor.tenantId(), channel))
                throw new CommunicationValidationException(
                        "Le canal " + channel + " est obligatoire pour les alertes critiques");
        }
        Instant now = clock.instant();
        requested.forEach(
                (channel, enabled) -> {
                    int changed =
                            jdbc.update(
                                    "UPDATE communication_center_preference SET"
                                        + " enabled=?,updated_at=? WHERE tenant_id=? AND user_id=?"
                                        + " AND channel=?",
                                    enabled,
                                    Timestamp.from(now),
                                    actor.tenantId(),
                                    actor.accountId(),
                                    channel);
                    if (changed == 0)
                        jdbc.update(
                                "INSERT INTO communication_center_preference"
                                        + " (id,tenant_id,user_id,channel,enabled,updated_at)"
                                        + " VALUES (?,?,?,?,?,?)",
                                UUID.randomUUID(),
                                actor.tenantId(),
                                actor.accountId(),
                                channel,
                                enabled,
                                Timestamp.from(now));
                });
        return preferences(actor);
    }

    private NotificationItem one(SchoolPrincipal actor, UUID id) {
        List<NotificationItem> rows =
                jdbc.query(
                        "SELECT * FROM communication_notification WHERE tenant_id=? AND id=?"
                                + " AND recipient_kind='INTERNAL' AND recipient_user_id=?",
                        this::item,
                        actor.tenantId(),
                        id,
                        actor.accountId());
        if (rows.isEmpty()) throw new CommunicationNotFoundException("Notification introuvable");
        return rows.getFirst();
    }

    private NotificationItem item(ResultSet result, int row) throws SQLException {
        Timestamp readAt = result.getTimestamp("read_at");
        Timestamp archivedAt = result.getTimestamp("archived_at");
        String state = archivedAt != null ? "ARCHIVED" : readAt == null ? "UNREAD" : "READ";
        return new NotificationItem(
                result.getObject("id", UUID.class),
                result.getString("category"),
                result.getString("title"),
                result.getString("body"),
                result.getBoolean("critical"),
                result.getString("alert_key"),
                state,
                result.getString("resource_type"),
                result.getObject("resource_id", UUID.class),
                route(
                        result.getString("resource_type"),
                        result.getObject("resource_id", UUID.class)),
                result.getTimestamp("created_at").toInstant(),
                readAt == null ? null : readAt.toInstant(),
                archivedAt == null ? null : archivedAt.toInstant());
    }

    private List<PreferenceView> preferences(SchoolPrincipal actor) {
        return CHANNELS.stream()
                .map(
                        channel -> {
                            List<Boolean> values =
                                    jdbc.query(
                                            "SELECT enabled FROM communication_center_preference"
                                                + " WHERE tenant_id=? AND user_id=? AND channel=?",
                                            (result, row) -> result.getBoolean(1),
                                            actor.tenantId(),
                                            actor.accountId(),
                                            channel);
                            return new PreferenceView(
                                    channel,
                                    values.isEmpty() || values.getFirst(),
                                    mandatory(actor.tenantId(), channel));
                        })
                .toList();
    }

    private boolean mandatory(UUID tenantId, String channel) {
        Integer value =
                jdbc.queryForObject(
                        "SELECT count(*) FROM communication_notification_policy WHERE tenant_id=?"
                                + " AND channel=? AND mandatory=TRUE AND active=TRUE",
                        Integer.class,
                        tenantId,
                        channel);
        return value != null && value > 0;
    }

    private int count(SchoolPrincipal actor, String predicate) {
        Integer value =
                jdbc.queryForObject(
                        "SELECT count(*) FROM communication_notification WHERE tenant_id=?"
                                + " AND recipient_kind='INTERNAL' AND recipient_user_id=? AND "
                                + predicate,
                        Integer.class,
                        actor.tenantId(),
                        actor.accountId());
        return value == null ? 0 : value;
    }

    private String status(String value) {
        String result = value == null || value.isBlank() ? "ALL" : value.toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(result))
            throw new CommunicationValidationException("Statut de notification inconnu");
        return result;
    }

    private String route(String resourceType, UUID resourceId) {
        if (resourceType == null) return "/app/communications";
        return switch (resourceType) {
            case "STUDENT" -> resourceId == null ? "/app/students" : "/app/students/" + resourceId;
            case "INVOICE" -> "/app/finance";
            case "ADMISSION" -> "/app/admissions";
            case "DOCUMENT" -> "/app/documents";
            default -> "/app/communications";
        };
    }

    public record CenterResponse(
            Instant generatedAt,
            String status,
            int page,
            int size,
            boolean hasNext,
            Counts counts,
            List<PreferenceView> preferences,
            List<NotificationItem> items) {}

    public record Counts(int unread, int read, int archived) {}

    public record NotificationItem(
            UUID id,
            String category,
            String title,
            String body,
            boolean critical,
            String alertKey,
            String state,
            String resourceType,
            UUID resourceId,
            String routePath,
            Instant createdAt,
            Instant readAt,
            Instant archivedAt) {}

    public record PreferenceView(String channel, boolean enabled, boolean mandatory) {}

    public record PreferenceCommand(Map<String, Boolean> channels) {}
}
