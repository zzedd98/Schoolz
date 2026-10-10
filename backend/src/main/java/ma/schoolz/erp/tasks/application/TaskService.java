package ma.schoolz.erp.tasks.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
public class TaskService {
    private static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "URGENT");
    private static final Set<String> STATUSES = Set.of("TODO", "IN_PROGRESS", "DONE", "CANCELLED");

    private final JdbcTemplate jdbc;
    private final IdentityAuditTrail audit;
    private final Clock clock;

    public TaskService(JdbcTemplate jdbc, IdentityAuditTrail audit, Clock clock) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('task.read')")
    public Dashboard dashboard(
            SchoolPrincipal actor,
            String requestedStatus,
            String requestedPriority,
            int page,
            int size) {
        if (page < 0 || size < 1 || size > 50)
            throw new IllegalArgumentException("Pagination invalide");
        String status = optional(requestedStatus, STATUSES, "Statut de tâche inconnu");
        String priority = optional(requestedPriority, PRIORITIES, "Priorité inconnue");
        boolean manageAll = actor.permissions().contains("task.manage");
        StringBuilder sql = new StringBuilder("SELECT * FROM internal_task WHERE tenant_id=?");
        List<Object> parameters = new ArrayList<>(List.of(actor.tenantId()));
        if (!manageAll) {
            sql.append(" AND assignee_user_id=?");
            parameters.add(actor.accountId());
        }
        if (status != null) {
            sql.append(" AND status=?");
            parameters.add(status);
        } else {
            sql.append(" AND status<>'CANCELLED'");
        }
        if (priority != null) {
            sql.append(" AND priority=?");
            parameters.add(priority);
        }
        sql.append(
                " ORDER BY CASE priority WHEN 'URGENT' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN"
                        + " 2 ELSE 3 END,due_date,created_at DESC LIMIT ? OFFSET ?");
        parameters.add(size + 1);
        parameters.add(page * size);
        List<TaskView> rows = jdbc.query(sql.toString(), this::task, parameters.toArray());
        boolean hasNext = rows.size() > size;
        List<TaskView> tasks = hasNext ? rows.subList(0, size) : rows;
        return new Dashboard(
                clock.instant(),
                page,
                size,
                hasNext,
                counts(actor, manageAll),
                assignees(actor.tenantId()),
                tasks);
    }

    @Transactional
    @PreAuthorize("hasAuthority('task.manage')")
    public TaskView create(SchoolPrincipal actor, TaskCommand command, String ipAddress) {
        validate(command);
        requireUser(actor.tenantId(), command.assigneeUserId());
        Instant now = clock.instant();
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO internal_task"
                    + " (id,tenant_id,title,description,assignee_user_id,created_by_user_id,"
                    + " due_date,priority,status,linked_entity_type,linked_entity_id,source,"
                    + " created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,'TODO',?,?,'MANUAL',?,?)",
                id,
                actor.tenantId(),
                required(command.title(), "Le titre est obligatoire", 220),
                nullable(command.description(), 4000),
                command.assigneeUserId(),
                actor.accountId(),
                command.dueDate(),
                upper(command.priority()),
                nullable(command.linkedEntityType(), 50),
                command.linkedEntityId(),
                Timestamp.from(now),
                Timestamp.from(now));
        TaskView created = one(actor.tenantId(), id);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "TASK_CREATED",
                "task",
                id.toString(),
                ipAddress,
                Map.of("module", "tasks"),
                null,
                created);
        return created;
    }

    @Transactional
    @PreAuthorize("hasAuthority('task.manage')")
    public TaskView updateStatus(
            SchoolPrincipal actor, UUID taskId, StatusCommand command, String ipAddress) {
        String status =
                optional(command == null ? null : command.status(), STATUSES, "Statut invalide");
        if (status == null) throw new IllegalArgumentException("Le statut est obligatoire");
        TaskView before = one(actor.tenantId(), taskId);
        Instant now = clock.instant();
        jdbc.update(
                "UPDATE internal_task SET status=?,completed_at=?,updated_at=? WHERE tenant_id=?"
                        + " AND id=?",
                status,
                "DONE".equals(status) ? Timestamp.from(now) : null,
                Timestamp.from(now),
                actor.tenantId(),
                taskId);
        TaskView after = one(actor.tenantId(), taskId);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "TASK_STATUS_CHANGED",
                "task",
                taskId.toString(),
                ipAddress,
                Map.of("module", "tasks"),
                before,
                after);
        return after;
    }

    @Transactional
    @PreAuthorize("hasAuthority('task.manage')")
    public TaskView assign(
            SchoolPrincipal actor, UUID taskId, AssignCommand command, String ipAddress) {
        if (command == null || command.assigneeUserId() == null)
            throw new IllegalArgumentException("Le responsable est obligatoire");
        requireUser(actor.tenantId(), command.assigneeUserId());
        TaskView before = one(actor.tenantId(), taskId);
        jdbc.update(
                "UPDATE internal_task SET assignee_user_id=?,updated_at=? WHERE tenant_id=? AND"
                        + " id=?",
                command.assigneeUserId(),
                Timestamp.from(clock.instant()),
                actor.tenantId(),
                taskId);
        TaskView after = one(actor.tenantId(), taskId);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "TASK_ASSIGNED",
                "task",
                taskId.toString(),
                ipAddress,
                Map.of("module", "tasks"),
                before,
                after);
        return after;
    }

    private Counts counts(SchoolPrincipal actor, boolean manageAll) {
        LocalDate today = LocalDate.now(clock);
        String sql =
                "SELECT COALESCE(SUM(CASE WHEN status='TODO' THEN 1 ELSE 0 END),0) todo,"
                        + " COALESCE(SUM(CASE WHEN status='IN_PROGRESS' THEN 1 ELSE 0 END),0)"
                        + " in_progress, COALESCE(SUM(CASE WHEN status='DONE' THEN 1 ELSE 0 END),0)"
                        + " done, COALESCE(SUM(CASE WHEN status IN ('TODO','IN_PROGRESS') AND"
                        + " due_date<? THEN 1 ELSE 0 END),0) overdue FROM internal_task WHERE"
                        + " tenant_id=?";
        List<Object> parameters = new ArrayList<>(List.of(today, actor.tenantId()));
        if (!manageAll) {
            sql += " AND assignee_user_id=?";
            parameters.add(actor.accountId());
        }
        return jdbc.queryForObject(
                sql,
                (result, row) ->
                        new Counts(
                                result.getInt("todo"),
                                result.getInt("in_progress"),
                                result.getInt("done"),
                                result.getInt("overdue")),
                parameters.toArray());
    }

    private List<AssigneeView> assignees(UUID tenantId) {
        return jdbc.query(
                "SELECT id,display_name,email FROM identity_user_account WHERE tenant_id=?"
                        + " AND status='ACTIVE' ORDER BY display_name",
                (result, row) ->
                        new AssigneeView(
                                result.getObject("id", UUID.class),
                                result.getString("display_name"),
                                result.getString("email")),
                tenantId);
    }

    private TaskView one(UUID tenantId, UUID id) {
        List<TaskView> values =
                jdbc.query(
                        "SELECT * FROM internal_task WHERE tenant_id=? AND id=?",
                        this::task,
                        tenantId,
                        id);
        if (values.isEmpty()) throw new IllegalArgumentException("Tâche introuvable");
        return values.getFirst();
    }

    private TaskView task(ResultSet result, int row) throws SQLException {
        UUID assigneeId = result.getObject("assignee_user_id", UUID.class);
        String assignee =
                jdbc.queryForObject(
                        "SELECT display_name FROM identity_user_account WHERE tenant_id=? AND id=?",
                        String.class,
                        result.getObject("tenant_id", UUID.class),
                        assigneeId);
        Timestamp completed = result.getTimestamp("completed_at");
        return new TaskView(
                result.getObject("id", UUID.class),
                result.getString("title"),
                result.getString("description"),
                assigneeId,
                assignee,
                result.getObject("due_date", LocalDate.class),
                result.getString("priority"),
                result.getString("status"),
                result.getString("linked_entity_type"),
                result.getObject("linked_entity_id", UUID.class),
                route(
                        result.getString("linked_entity_type"),
                        result.getObject("linked_entity_id", UUID.class)),
                result.getString("source"),
                result.getTimestamp("created_at").toInstant(),
                completed == null ? null : completed.toInstant());
    }

    private String route(String type, UUID id) {
        if (type == null) return "/app/tasks";
        return switch (type) {
            case "STUDENT" -> id == null ? "/app/students" : "/app/students/" + id;
            case "ADMISSION" -> "/app/admissions";
            case "INVOICE", "FAMILY" -> "/app/finance";
            case "SUPPLIER" -> "/app/procurement";
            default -> "/app/tasks";
        };
    }

    private void validate(TaskCommand command) {
        if (command == null || command.assigneeUserId() == null || command.dueDate() == null)
            throw new IllegalArgumentException("Responsable et échéance sont obligatoires");
        String priority = upper(command.priority());
        if (!PRIORITIES.contains(priority)) throw new IllegalArgumentException("Priorité inconnue");
        boolean hasType =
                command.linkedEntityType() != null && !command.linkedEntityType().isBlank();
        if (hasType != (command.linkedEntityId() != null))
            throw new IllegalArgumentException("L’entité liée doit être complète");
    }

    private void requireUser(UUID tenantId, UUID userId) {
        Integer count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM identity_user_account WHERE tenant_id=? AND id=? AND"
                                + " status='ACTIVE'",
                        Integer.class,
                        tenantId,
                        userId);
        if (count == null || count == 0)
            throw new IllegalArgumentException("Responsable introuvable");
    }

    private String optional(String value, Set<String> values, String message) {
        if (value == null || value.isBlank()) return null;
        String result = upper(value);
        if (!values.contains(result)) throw new IllegalArgumentException(message);
        return result;
    }

    private String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String required(String value, String message, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        String result = value.trim();
        if (result.length() > max) throw new IllegalArgumentException(message);
        return result;
    }

    private String nullable(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim();
        if (result.length() > max) throw new IllegalArgumentException("Valeur trop longue");
        return result;
    }

    public record Dashboard(
            Instant generatedAt,
            int page,
            int size,
            boolean hasNext,
            Counts counts,
            List<AssigneeView> assignees,
            List<TaskView> tasks) {}

    public record Counts(int todo, int inProgress, int done, int overdue) {}

    public record AssigneeView(UUID id, String displayName, String email) {}

    public record TaskView(
            UUID id,
            String title,
            String description,
            UUID assigneeUserId,
            String assigneeName,
            LocalDate dueDate,
            String priority,
            String status,
            String linkedEntityType,
            UUID linkedEntityId,
            String routePath,
            String source,
            Instant createdAt,
            Instant completedAt) {}

    public record TaskCommand(
            String title,
            String description,
            UUID assigneeUserId,
            LocalDate dueDate,
            String priority,
            String linkedEntityType,
            UUID linkedEntityId) {}

    public record StatusCommand(String status) {}

    public record AssignCommand(UUID assigneeUserId) {}
}
