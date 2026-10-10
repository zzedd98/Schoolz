package ma.schoolz.erp.calendar.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.identity.IdentityAuditTrail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchoolCalendarService {
    private static final Set<String> TYPES =
            Set.of("HOLIDAY", "EXAM", "MEETING", "TRIP", "ACTIVITY", "PARENT_TEACHER");
    private static final Set<String> SCOPES = Set.of("SCHOOL", "CAMPUS", "CLASS");

    private final JdbcTemplate jdbc;
    private final IdentityAuditTrail audit;
    private final Clock clock;

    public SchoolCalendarService(JdbcTemplate jdbc, IdentityAuditTrail audit, Clock clock) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('calendar.read')")
    public CalendarView calendar(
            SchoolPrincipal actor, Instant from, Instant to, String requestedType, UUID campusId) {
        if (from == null
                || to == null
                || !to.isAfter(from)
                || Duration.between(from, to).toDays() > 370)
            throw new IllegalArgumentException("Période de calendrier invalide");
        String type = optionalType(requestedType);
        StringBuilder sql =
                new StringBuilder(
                        "SELECT * FROM school_calendar_event WHERE tenant_id=? AND"
                                + " status='SCHEDULED' AND starts_at<? AND ends_at>?");
        List<Object> parameters =
                new ArrayList<>(
                        List.of(actor.tenantId(), Timestamp.from(to), Timestamp.from(from)));
        if (type != null) {
            sql.append(" AND event_type=?");
            parameters.add(type);
        }
        if (campusId != null) {
            sql.append(" AND (audience_scope='SCHOOL' OR campus_id=?)");
            parameters.add(campusId);
        }
        sql.append(" ORDER BY starts_at,event_type,title");
        List<EventView> events = jdbc.query(sql.toString(), this::event, parameters.toArray());
        Map<String, Long> counts =
                TYPES.stream()
                        .sorted()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        value -> value,
                                        value ->
                                                events.stream()
                                                        .filter(
                                                                item ->
                                                                        value.equals(
                                                                                item.eventType()))
                                                        .count(),
                                        (left, right) -> left,
                                        java.util.LinkedHashMap::new));
        return new CalendarView(
                clock.instant(),
                from,
                to,
                TYPES.stream().sorted().toList(),
                counts,
                campuses(actor.tenantId()),
                events);
    }

    @Transactional
    @PreAuthorize("hasAuthority('calendar.manage')")
    public EventView create(SchoolPrincipal actor, EventCommand command, String ipAddress) {
        validate(command);
        Instant now = clock.instant();
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO school_calendar_event"
                        + " (id,tenant_id,title,description,event_type,audience_scope,campus_id,"
                        + " classroom_id,starts_at,ends_at,all_day,location,status,sync_status,"
                        + " created_by_user_id,created_at,updated_at)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,'SCHEDULED','LOCAL_ONLY',?,?,?)",
                id,
                actor.tenantId(),
                required(command.title(), 220),
                nullable(command.description(), 4000),
                upper(command.eventType()),
                upper(command.audienceScope()),
                command.campusId(),
                command.classroomId(),
                Timestamp.from(command.startsAt()),
                Timestamp.from(command.endsAt()),
                command.allDay(),
                nullable(command.location(), 220),
                actor.accountId(),
                Timestamp.from(now),
                Timestamp.from(now));
        EventView created = one(actor.tenantId(), id);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "CALENDAR_EVENT_CREATED",
                "calendar-event",
                id.toString(),
                ipAddress,
                Map.of("module", "calendar"),
                null,
                created);
        return created;
    }

    @Transactional
    @PreAuthorize("hasAuthority('calendar.manage')")
    public EventView cancel(SchoolPrincipal actor, UUID eventId, String ipAddress) {
        EventView before = one(actor.tenantId(), eventId);
        int changed =
                jdbc.update(
                        "UPDATE school_calendar_event SET status='CANCELLED',updated_at=? WHERE"
                                + " tenant_id=? AND id=? AND status='SCHEDULED'",
                        Timestamp.from(clock.instant()),
                        actor.tenantId(),
                        eventId);
        if (changed == 0)
            throw new IllegalArgumentException("Événement introuvable ou déjà annulé");
        EventView after = one(actor.tenantId(), eventId);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "CALENDAR_EVENT_CANCELLED",
                "calendar-event",
                eventId.toString(),
                ipAddress,
                Map.of("module", "calendar"),
                before,
                after);
        return after;
    }

    private List<CampusView> campuses(UUID tenantId) {
        return jdbc.query(
                "SELECT id,name FROM school_campus WHERE tenant_id=? AND active=TRUE ORDER BY name",
                (result, row) ->
                        new CampusView(
                                result.getObject("id", UUID.class), result.getString("name")),
                tenantId);
    }

    private EventView one(UUID tenantId, UUID id) {
        List<EventView> values =
                jdbc.query(
                        "SELECT * FROM school_calendar_event WHERE tenant_id=? AND id=?",
                        this::event,
                        tenantId,
                        id);
        if (values.isEmpty()) throw new IllegalArgumentException("Événement introuvable");
        return values.getFirst();
    }

    private EventView event(ResultSet result, int row) throws SQLException {
        UUID campusId = result.getObject("campus_id", UUID.class);
        String campusName =
                campusId == null
                        ? null
                        : jdbc.queryForObject(
                                "SELECT name FROM school_campus WHERE tenant_id=? AND id=?",
                                String.class,
                                result.getObject("tenant_id", UUID.class),
                                campusId);
        return new EventView(
                result.getObject("id", UUID.class),
                result.getString("title"),
                result.getString("description"),
                result.getString("event_type"),
                result.getString("audience_scope"),
                campusId,
                campusName,
                result.getObject("classroom_id", UUID.class),
                result.getTimestamp("starts_at").toInstant(),
                result.getTimestamp("ends_at").toInstant(),
                result.getBoolean("all_day"),
                result.getString("location"),
                result.getString("status"),
                result.getString("sync_status"));
    }

    private void validate(EventCommand command) {
        if (command == null
                || command.startsAt() == null
                || command.endsAt() == null
                || !command.endsAt().isAfter(command.startsAt()))
            throw new IllegalArgumentException("Les dates de l’événement sont invalides");
        if (!TYPES.contains(upper(command.eventType())))
            throw new IllegalArgumentException("Type d’événement inconnu");
        String scope = upper(command.audienceScope());
        if (!SCOPES.contains(scope)) throw new IllegalArgumentException("Audience inconnue");
        if (("SCHOOL".equals(scope)
                        && (command.campusId() != null || command.classroomId() != null))
                || ("CAMPUS".equals(scope)
                        && (command.campusId() == null || command.classroomId() != null))
                || ("CLASS".equals(scope)
                        && (command.classroomId() == null || command.campusId() != null)))
            throw new IllegalArgumentException("Le périmètre de l’événement est incohérent");
        required(command.title(), 220);
    }

    private String optionalType(String value) {
        if (value == null || value.isBlank()) return null;
        String result = upper(value);
        if (!TYPES.contains(result)) throw new IllegalArgumentException("Type d’événement inconnu");
        return result;
    }

    private String required(String value, int max) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("Le titre est obligatoire");
        String result = value.trim();
        if (result.length() > max) throw new IllegalArgumentException("Le titre est trop long");
        return result;
    }

    private String nullable(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim();
        if (result.length() > max) throw new IllegalArgumentException("Valeur trop longue");
        return result;
    }

    private String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    public record CalendarView(
            Instant generatedAt,
            Instant from,
            Instant to,
            List<String> eventTypes,
            Map<String, Long> counts,
            List<CampusView> campuses,
            List<EventView> events) {}

    public record CampusView(UUID id, String name) {}

    public record EventView(
            UUID id,
            String title,
            String description,
            String eventType,
            String audienceScope,
            UUID campusId,
            String campusName,
            UUID classroomId,
            Instant startsAt,
            Instant endsAt,
            boolean allDay,
            String location,
            String status,
            String syncStatus) {}

    public record EventCommand(
            String title,
            String description,
            String eventType,
            String audienceScope,
            UUID campusId,
            UUID classroomId,
            Instant startsAt,
            Instant endsAt,
            boolean allDay,
            String location) {}
}
