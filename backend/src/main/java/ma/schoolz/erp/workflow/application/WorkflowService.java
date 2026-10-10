package ma.schoolz.erp.workflow.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.identity.IdentityAuditTrail;
import ma.schoolz.erp.workflow.application.WorkflowRuleEngine.Condition;
import ma.schoolz.erp.workflow.application.WorkflowRuleEngine.ConditionResult;
import ma.schoolz.erp.workflow.application.WorkflowRuleEngine.Operator;
import ma.schoolz.erp.workflow.application.WorkflowRuleEngine.ValueType;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowService {
    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_-]{2,79}");
    private static final Pattern EVENT = Pattern.compile("[a-z][a-z0-9_-]*(\\.[a-z0-9_-]+)+");
    private static final Set<String> ACTION_TYPES =
            Set.of("SEND_NOTIFICATION", "SEND_EMAIL", "CREATE_TASK", "NOTIFY_PARENT", "EMIT_EVENT");
    private static final int MAX_ROOT_EXECUTIONS = 50;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final IdentityAuditTrail audit;
    private final Clock clock;
    private final WorkflowRuleEngine engine = new WorkflowRuleEngine();

    public WorkflowService(
            JdbcTemplate jdbc, ObjectMapper json, IdentityAuditTrail audit, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('workflow.read')")
    public Dashboard dashboard(SchoolPrincipal actor) {
        return dashboard(actor.tenantId());
    }

    @Transactional
    @PreAuthorize("hasAuthority('workflow.manage')")
    public WorkflowView create(SchoolPrincipal actor, WorkflowCommand command, String ipAddress) {
        validate(command);
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                """
                INSERT INTO workflow_definition
                    (id,tenant_id,code,name,description,active,max_depth,created_by_user_id,
                     created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                """,
                id,
                actor.tenantId(),
                command.code().strip().toUpperCase(Locale.ROOT),
                command.name().strip(),
                command.description().strip(),
                command.active(),
                command.maxDepth(),
                actor.accountId(),
                timestamp(now),
                timestamp(now));
        insertTrigger(actor.tenantId(), id, command.trigger(), now);
        for (int index = 0; index < command.conditions().size(); index++) {
            insertCondition(actor.tenantId(), id, command.conditions().get(index), index, now);
        }
        for (int index = 0; index < command.actions().size(); index++) {
            insertAction(actor.tenantId(), id, command.actions().get(index), index, now);
        }
        WorkflowView created = workflow(actor.tenantId(), id);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "WORKFLOW_CREATED",
                "workflow",
                id.toString(),
                ipAddress,
                Map.of("module", "workflow"),
                null,
                created);
        return created;
    }

    @Transactional
    @PreAuthorize("hasAuthority('workflow.manage')")
    public WorkflowView activation(
            SchoolPrincipal actor, UUID workflowId, ActivationCommand command, String ipAddress) {
        WorkflowView before = workflow(actor.tenantId(), workflowId);
        jdbc.update(
                "UPDATE workflow_definition SET active=?,updated_at=? WHERE tenant_id=? AND id=?",
                command.active(),
                timestamp(clock.instant()),
                actor.tenantId(),
                workflowId);
        WorkflowView after = workflow(actor.tenantId(), workflowId);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "WORKFLOW_ACTIVATION_CHANGED",
                "workflow",
                workflowId.toString(),
                ipAddress,
                Map.of("module", "workflow"),
                before,
                after);
        return after;
    }

    @Transactional
    @PreAuthorize("hasAuthority('workflow.manage')")
    public DispatchView dispatch(SchoolPrincipal actor, EventCommand command, String ipAddress) {
        DispatchView result = dispatchForTenant(actor.tenantId(), actor.accountId(), command);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "WORKFLOW_EVENT_DISPATCHED",
                "workflow-event",
                result.rootEventId().toString(),
                ipAddress,
                Map.of("module", "workflow", "eventType", command.eventType()),
                null,
                result);
        return result;
    }

    DispatchView dispatchForTenant(UUID tenantId, UUID actorId, EventCommand command) {
        validate(command);
        DispatchAccumulator accumulator = new DispatchAccumulator(command.eventId());
        processEvent(
                tenantId,
                actorId,
                command,
                command.eventId(),
                null,
                0,
                new LinkedHashSet<>(),
                accumulator);
        return accumulator.view();
    }

    private void processEvent(
            UUID tenantId,
            UUID actorId,
            EventCommand event,
            UUID rootEventId,
            UUID parentExecutionId,
            int depth,
            Set<UUID> workflowPath,
            DispatchAccumulator accumulator) {
        List<UUID> workflowIds =
                jdbc.query(
                        """
                        SELECT w.id FROM workflow_definition w
                        JOIN workflow_trigger t ON t.workflow_id=w.id AND t.tenant_id=w.tenant_id
                        WHERE w.tenant_id=? AND w.active=TRUE AND t.event_type=?
                        ORDER BY w.created_at,w.id
                        """,
                        (rs, row) -> rs.getObject(1, UUID.class),
                        tenantId,
                        event.eventType());
        for (UUID workflowId : workflowIds) {
            ExecutionView existing = existingExecution(tenantId, workflowId, event.eventId());
            if (existing != null) {
                accumulator.add(existing);
                continue;
            }
            WorkflowView workflow = workflow(tenantId, workflowId);
            if (depth > workflow.maxDepth()
                    || workflowPath.contains(workflowId)
                    || rootExecutionCount(tenantId, rootEventId) >= MAX_ROOT_EXECUTIONS) {
                String reason =
                        workflowPath.contains(workflowId)
                                ? "Workflow déjà présent dans la chaîne d’exécution"
                                : "Profondeur ou volume maximal de la chaîne atteint";
                ExecutionView blocked =
                        createBlocked(
                                tenantId,
                                workflow,
                                event,
                                rootEventId,
                                parentExecutionId,
                                depth,
                                reason);
                accumulator.add(blocked);
                continue;
            }

            List<Condition> conditions =
                    workflow.conditions().stream()
                            .map(
                                    condition ->
                                            new Condition(
                                                    condition.fieldPath(),
                                                    Operator.valueOf(condition.operator()),
                                                    condition.expectedValue(),
                                                    ValueType.valueOf(condition.valueType())))
                            .toList();
            WorkflowRuleEngine.Evaluation evaluation = engine.evaluate(conditions, event.payload());
            UUID executionId = UUID.randomUUID();
            Instant started = clock.instant();
            String status = evaluation.matched() ? "COMPLETED" : "SKIPPED";
            insertExecution(
                    executionId,
                    tenantId,
                    workflow.id(),
                    event,
                    rootEventId,
                    parentExecutionId,
                    status,
                    depth,
                    null,
                    started);
            int sequence = 0;
            log(
                    tenantId,
                    executionId,
                    sequence++,
                    "INFO",
                    "TRIGGER",
                    "Événement " + event.eventType() + " reçu",
                    Map.of("sourceModule", event.sourceModule()));
            for (ConditionResult condition : evaluation.conditions()) {
                log(
                        tenantId,
                        executionId,
                        sequence++,
                        condition.matched() ? "INFO" : "WARN",
                        "CONDITION",
                        condition.fieldPath()
                                + (condition.matched()
                                        ? " satisfait la condition"
                                        : " ne satisfait pas la condition"),
                        condition);
            }
            if (evaluation.matched()) {
                Set<UUID> childPath = new LinkedHashSet<>(workflowPath);
                childPath.add(workflow.id());
                try {
                    for (ActionView action : workflow.actions()) {
                        if ("CREATE_TASK".equals(action.actionType())) {
                            createWorkflowTask(
                                    tenantId, actorId, executionId, action, event.payload());
                        }
                        log(
                                tenantId,
                                executionId,
                                sequence++,
                                "INFO",
                                "ACTION",
                                action.label() + " exécutée par l’adaptateur local",
                                Map.of("actionType", action.actionType(), "state", "COMPLETED"));
                        if ("EMIT_EVENT".equals(action.actionType())) {
                            UUID childEventId =
                                    UUID.nameUUIDFromBytes(
                                            (event.eventId() + ":" + action.id())
                                                    .getBytes(StandardCharsets.UTF_8));
                            processEvent(
                                    tenantId,
                                    actorId,
                                    new EventCommand(
                                            childEventId,
                                            action.emittedEventType(),
                                            "workflow",
                                            event.payload()),
                                    rootEventId,
                                    executionId,
                                    depth + 1,
                                    childPath,
                                    accumulator);
                        }
                    }
                } catch (RuntimeException exception) {
                    jdbc.update(
                            "UPDATE workflow_execution SET"
                                    + " status='FAILED',failure_reason=?,completed_at=? WHERE"
                                    + " tenant_id=? AND id=?",
                            limit(exception.getMessage(), 1000),
                            timestamp(clock.instant()),
                            tenantId,
                            executionId);
                    log(
                            tenantId,
                            executionId,
                            sequence,
                            "ERROR",
                            "EXECUTION",
                            "Exécution interrompue",
                            Map.of("reason", String.valueOf(exception.getMessage())));
                }
            }
            accumulator.add(execution(tenantId, executionId));
        }
    }

    private void createWorkflowTask(
            UUID tenantId,
            UUID actorId,
            UUID executionId,
            ActionView action,
            Map<String, Object> payload) {
        UUID assigneeId = uuid(payload.get("assigneeUserId"), actorId);
        UUID linkedEntityId = uuid(payload.get("linkedEntityId"), null);
        String linkedEntityType =
                linkedEntityId == null
                        ? null
                        : text(payload.get("linkedEntityType"), "WORKFLOW_EVENT");
        String priority = text(payload.get("priority"), "HIGH").toUpperCase(Locale.ROOT);
        if (!Set.of("LOW", "MEDIUM", "HIGH", "URGENT").contains(priority)) priority = "HIGH";
        LocalDate dueDate;
        try {
            dueDate = LocalDate.parse(text(payload.get("dueDate"), ""));
        } catch (RuntimeException exception) {
            dueDate = LocalDate.now(clock).plusDays(2);
        }
        jdbc.update(
                "INSERT INTO internal_task"
                    + " (id,tenant_id,title,description,assignee_user_id,created_by_user_id,"
                    + " due_date,priority,status,linked_entity_type,linked_entity_id,source,"
                    + " source_workflow_execution_id,source_workflow_action_id,created_at,updated_at)"
                    + " VALUES (?,?,?,?,?,?,?,?,'TODO',?,?,'WORKFLOW',?,?,?,?)",
                UUID.randomUUID(),
                tenantId,
                limit(text(payload.get("taskTitle"), action.label()), 220),
                limit(
                        "Créée automatiquement par le workflow pour "
                                + text(payload.get("reference"), "l’événement reçu"),
                        4000),
                assigneeId,
                actorId,
                dueDate,
                priority,
                linkedEntityType,
                linkedEntityId,
                executionId,
                action.id(),
                timestamp(clock.instant()),
                timestamp(clock.instant()));
    }

    private UUID uuid(Object value, UUID fallback) {
        if (value == null) return fallback;
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private String text(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    private ExecutionView createBlocked(
            UUID tenantId,
            WorkflowView workflow,
            EventCommand event,
            UUID rootEventId,
            UUID parentExecutionId,
            int depth,
            String reason) {
        UUID id = UUID.randomUUID();
        insertExecution(
                id,
                tenantId,
                workflow.id(),
                event,
                rootEventId,
                parentExecutionId,
                "LOOP_BLOCKED",
                Math.min(depth, 12),
                reason,
                clock.instant());
        log(tenantId, id, 0, "WARN", "LOOP_GUARD", reason, Map.of("depth", depth));
        return execution(tenantId, id);
    }

    private void insertExecution(
            UUID id,
            UUID tenantId,
            UUID workflowId,
            EventCommand event,
            UUID rootEventId,
            UUID parentExecutionId,
            String status,
            int depth,
            String failureReason,
            Instant now) {
        try {
            jdbc.update(
                    """
                    INSERT INTO workflow_execution
                        (id,tenant_id,workflow_id,event_id,root_event_id,parent_execution_id,
                         trigger_event_type,status,depth,payload_json,started_at,completed_at,
                         failure_reason)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """,
                    id,
                    tenantId,
                    workflowId,
                    event.eventId(),
                    rootEventId,
                    parentExecutionId,
                    event.eventType(),
                    status,
                    depth,
                    writeJson(event.payload()),
                    timestamp(now),
                    timestamp(now),
                    failureReason);
        } catch (DuplicateKeyException exception) {
            throw new IllegalStateException("Concurrent duplicate workflow execution", exception);
        }
    }

    private void log(
            UUID tenantId,
            UUID executionId,
            int sequence,
            String level,
            String step,
            String message,
            Object detail) {
        jdbc.update(
                """
                INSERT INTO workflow_execution_log
                    (id,tenant_id,execution_id,sequence_number,log_level,step_type,message,
                     detail_json,occurred_at)
                VALUES (?,?,?,?,?,?,?,?,?)
                """,
                UUID.randomUUID(),
                tenantId,
                executionId,
                sequence,
                level,
                step,
                limit(message, 600),
                writeJson(detail),
                timestamp(clock.instant()));
    }

    private Dashboard dashboard(UUID tenantId) {
        List<WorkflowView> workflows = workflows(tenantId);
        List<ExecutionView> executions = executions(tenantId);
        long completed =
                executions.stream().filter(item -> "COMPLETED".equals(item.status())).count();
        long skipped = executions.stream().filter(item -> "SKIPPED".equals(item.status())).count();
        long blocked =
                executions.stream().filter(item -> "LOOP_BLOCKED".equals(item.status())).count();
        long actions =
                executions.stream()
                        .flatMap(item -> item.logs().stream())
                        .filter(log -> "ACTION".equals(log.stepType()))
                        .count();
        return new Dashboard(
                clock.instant(),
                new Statistics(
                        workflows.size(),
                        workflows.stream().filter(WorkflowView::active).count(),
                        completed,
                        skipped,
                        blocked,
                        actions),
                workflows,
                executions,
                List.of(
                        "invoice.overdue",
                        "student.absence.created",
                        "enrollment.completed",
                        "document.missing",
                        "workflow.demo.loop"),
                ACTION_TYPES.stream().sorted().toList(),
                new LoopPolicy(12, MAX_ROOT_EXECUTIONS, true));
    }

    private List<WorkflowView> workflows(UUID tenantId) {
        return jdbc.query(
                "SELECT * FROM workflow_definition WHERE tenant_id=? ORDER BY created_at,code",
                (rs, row) -> mapWorkflow(rs, tenantId),
                tenantId);
    }

    private WorkflowView workflow(UUID tenantId, UUID id) {
        List<WorkflowView> values =
                jdbc.query(
                        "SELECT * FROM workflow_definition WHERE tenant_id=? AND id=?",
                        (rs, row) -> mapWorkflow(rs, tenantId),
                        tenantId,
                        id);
        if (values.isEmpty()) throw new IllegalArgumentException("Workflow introuvable");
        return values.getFirst();
    }

    private WorkflowView mapWorkflow(ResultSet rs, UUID tenantId) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        TriggerView trigger =
                jdbc.queryForObject(
                        "SELECT * FROM workflow_trigger WHERE tenant_id=? AND workflow_id=?",
                        (inner, row) ->
                                new TriggerView(
                                        inner.getObject("id", UUID.class),
                                        inner.getString("event_type"),
                                        inner.getString("source_module")),
                        tenantId,
                        id);
        List<ConditionView> conditions =
                jdbc.query(
                        "SELECT * FROM workflow_condition WHERE tenant_id=? AND workflow_id=?"
                                + " ORDER BY sort_order,id",
                        (inner, row) ->
                                new ConditionView(
                                        inner.getObject("id", UUID.class),
                                        inner.getString("field_path"),
                                        inner.getString("comparison_operator"),
                                        inner.getString("expected_value"),
                                        inner.getString("value_type"),
                                        inner.getInt("sort_order")),
                        tenantId,
                        id);
        List<ActionView> actions =
                jdbc.query(
                        "SELECT * FROM workflow_action WHERE tenant_id=? AND workflow_id=?"
                                + " ORDER BY sort_order,id",
                        (inner, row) ->
                                new ActionView(
                                        inner.getObject("id", UUID.class),
                                        inner.getString("action_type"),
                                        inner.getString("action_label"),
                                        readMap(inner.getString("configuration_json")),
                                        inner.getString("emitted_event_type"),
                                        inner.getInt("sort_order")),
                        tenantId,
                        id);
        return new WorkflowView(
                id,
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getBoolean("active"),
                rs.getInt("max_depth"),
                trigger,
                conditions,
                actions,
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private List<ExecutionView> executions(UUID tenantId) {
        return jdbc.query(
                "SELECT id FROM workflow_execution WHERE tenant_id=? ORDER BY started_at DESC"
                        + " FETCH FIRST 40 ROWS ONLY",
                (rs, row) -> execution(tenantId, rs.getObject(1, UUID.class)),
                tenantId);
    }

    private ExecutionView execution(UUID tenantId, UUID id) {
        return jdbc.queryForObject(
                """
                SELECT e.*,w.code,w.name FROM workflow_execution e
                JOIN workflow_definition w ON w.id=e.workflow_id AND w.tenant_id=e.tenant_id
                WHERE e.tenant_id=? AND e.id=?
                """,
                (rs, row) ->
                        new ExecutionView(
                                rs.getObject("id", UUID.class),
                                rs.getObject("workflow_id", UUID.class),
                                rs.getString("code"),
                                rs.getString("name"),
                                rs.getObject("event_id", UUID.class),
                                rs.getObject("root_event_id", UUID.class),
                                rs.getObject("parent_execution_id", UUID.class),
                                rs.getString("trigger_event_type"),
                                rs.getString("status"),
                                rs.getInt("depth"),
                                readMap(rs.getString("payload_json")),
                                instant(rs, "started_at"),
                                instant(rs, "completed_at"),
                                rs.getString("failure_reason"),
                                logs(tenantId, id)),
                tenantId,
                id);
    }

    private ExecutionView existingExecution(UUID tenantId, UUID workflowId, UUID eventId) {
        List<UUID> ids =
                jdbc.query(
                        "SELECT id FROM workflow_execution WHERE tenant_id=? AND workflow_id=?"
                                + " AND event_id=?",
                        (rs, row) -> rs.getObject(1, UUID.class),
                        tenantId,
                        workflowId,
                        eventId);
        return ids.isEmpty() ? null : execution(tenantId, ids.getFirst());
    }

    private List<ExecutionLogView> logs(UUID tenantId, UUID executionId) {
        return jdbc.query(
                "SELECT * FROM workflow_execution_log WHERE tenant_id=? AND execution_id=?"
                        + " ORDER BY sequence_number",
                (rs, row) ->
                        new ExecutionLogView(
                                rs.getObject("id", UUID.class),
                                rs.getInt("sequence_number"),
                                rs.getString("log_level"),
                                rs.getString("step_type"),
                                rs.getString("message"),
                                readMap(rs.getString("detail_json")),
                                instant(rs, "occurred_at")),
                tenantId,
                executionId);
    }

    private int rootExecutionCount(UUID tenantId, UUID rootEventId) {
        Integer count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM workflow_execution WHERE tenant_id=? AND"
                                + " root_event_id=?",
                        Integer.class,
                        tenantId,
                        rootEventId);
        return count == null ? 0 : count;
    }

    private void insertTrigger(
            UUID tenantId, UUID workflowId, TriggerCommand trigger, Instant now) {
        jdbc.update(
                "INSERT INTO workflow_trigger"
                        + " (id,tenant_id,workflow_id,event_type,source_module,created_at)"
                        + " VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(),
                tenantId,
                workflowId,
                trigger.eventType().strip(),
                trigger.sourceModule().strip(),
                timestamp(now));
    }

    private void insertCondition(
            UUID tenantId, UUID workflowId, ConditionCommand condition, int order, Instant now) {
        jdbc.update(
                "INSERT INTO workflow_condition"
                    + " (id,tenant_id,workflow_id,field_path,comparison_operator,expected_value,value_type,sort_order,created_at)"
                    + " VALUES (?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(),
                tenantId,
                workflowId,
                condition.fieldPath().strip(),
                condition.operator(),
                condition.expectedValue(),
                condition.valueType(),
                order,
                timestamp(now));
    }

    private void insertAction(
            UUID tenantId, UUID workflowId, ActionCommand action, int order, Instant now) {
        jdbc.update(
                "INSERT INTO workflow_action"
                        + " (id,tenant_id,workflow_id,action_type,action_label,configuration_json,"
                        + "emitted_event_type,sort_order,created_at) VALUES (?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(),
                tenantId,
                workflowId,
                action.actionType(),
                action.label().strip(),
                writeJson(action.configuration()),
                blankToNull(action.emittedEventType()),
                order,
                timestamp(now));
    }

    private void validate(WorkflowCommand command) {
        if (command == null
                || command.code() == null
                || !CODE.matcher(command.code().strip().toUpperCase(Locale.ROOT)).matches())
            throw new IllegalArgumentException("Le code du workflow est invalide");
        if (blank(command.name()) || blank(command.description()))
            throw new IllegalArgumentException("Le nom et la description sont obligatoires");
        if (command.maxDepth() < 1 || command.maxDepth() > 12)
            throw new IllegalArgumentException("La profondeur doit être comprise entre 1 et 12");
        if (command.trigger() == null || !validEvent(command.trigger().eventType()))
            throw new IllegalArgumentException("Le type d’événement déclencheur est invalide");
        if (blank(command.trigger().sourceModule()))
            throw new IllegalArgumentException("Le module source est obligatoire");
        if (command.conditions() == null
                || command.actions() == null
                || command.actions().isEmpty())
            throw new IllegalArgumentException("Au moins une action est obligatoire");
        for (ConditionCommand condition : command.conditions()) {
            if (blank(condition.fieldPath()))
                throw new IllegalArgumentException("Le chemin d’une condition est obligatoire");
            try {
                Operator.valueOf(condition.operator());
                ValueType.valueOf(condition.valueType());
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("Opérateur ou type de condition invalide");
            }
            if (!"EXISTS".equals(condition.operator()) && blank(condition.expectedValue()))
                throw new IllegalArgumentException("La valeur attendue est obligatoire");
        }
        for (ActionCommand action : command.actions()) {
            if (!ACTION_TYPES.contains(action.actionType()) || blank(action.label()))
                throw new IllegalArgumentException("Action de workflow invalide");
            if ("EMIT_EVENT".equals(action.actionType()) != validEvent(action.emittedEventType()))
                throw new IllegalArgumentException("Une émission exige un type d’événement valide");
        }
    }

    private void validate(EventCommand command) {
        if (command == null || command.eventId() == null || !validEvent(command.eventType()))
            throw new IllegalArgumentException(
                    "L’identifiant et le type de l’événement sont obligatoires");
        if (blank(command.sourceModule()) || command.payload() == null)
            throw new IllegalArgumentException("La source et le payload sont obligatoires");
    }

    private boolean validEvent(String value) {
        return value != null && EVENT.matcher(value.strip()).matches();
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Configuration JSON invalide", exception);
        }
    }

    private Map<String, Object> readMap(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            return json.readValue(value, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException exception) {
            return Map.of("raw", value);
        }
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static String blankToNull(String value) {
        return blank(value) ? null : value.strip();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String limit(String value, int maximum) {
        if (value == null) return null;
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    private static final class DispatchAccumulator {
        private final UUID rootEventId;
        private final List<ExecutionView> executions = new ArrayList<>();

        private DispatchAccumulator(UUID rootEventId) {
            this.rootEventId = rootEventId;
        }

        private void add(ExecutionView execution) {
            if (executions.stream().noneMatch(item -> item.id().equals(execution.id())))
                executions.add(execution);
        }

        private DispatchView view() {
            return new DispatchView(
                    rootEventId,
                    executions.stream().filter(item -> "COMPLETED".equals(item.status())).count(),
                    executions.stream().filter(item -> "SKIPPED".equals(item.status())).count(),
                    executions.stream()
                            .filter(item -> "LOOP_BLOCKED".equals(item.status()))
                            .count(),
                    executions);
        }
    }

    public record Dashboard(
            Instant generatedAt,
            Statistics statistics,
            List<WorkflowView> workflows,
            List<ExecutionView> executions,
            List<String> supportedEvents,
            List<String> supportedActions,
            LoopPolicy loopPolicy) {}

    public record Statistics(
            long workflowCount,
            long activeWorkflows,
            long completedExecutions,
            long skippedExecutions,
            long loopBlocks,
            long actionsExecuted) {}

    public record LoopPolicy(
            int maximumDepth, int maximumExecutionsPerRoot, boolean ancestryGuard) {}

    public record WorkflowView(
            UUID id,
            String code,
            String name,
            String description,
            boolean active,
            int maxDepth,
            TriggerView trigger,
            List<ConditionView> conditions,
            List<ActionView> actions,
            Instant createdAt,
            Instant updatedAt) {}

    public record TriggerView(UUID id, String eventType, String sourceModule) {}

    public record ConditionView(
            UUID id,
            String fieldPath,
            String operator,
            String expectedValue,
            String valueType,
            int order) {}

    public record ActionView(
            UUID id,
            String actionType,
            String label,
            Map<String, Object> configuration,
            String emittedEventType,
            int order) {}

    public record ExecutionView(
            UUID id,
            UUID workflowId,
            String workflowCode,
            String workflowName,
            UUID eventId,
            UUID rootEventId,
            UUID parentExecutionId,
            String eventType,
            String status,
            int depth,
            Map<String, Object> payload,
            Instant startedAt,
            Instant completedAt,
            String failureReason,
            List<ExecutionLogView> logs) {}

    public record ExecutionLogView(
            UUID id,
            int sequence,
            String level,
            String stepType,
            String message,
            Map<String, Object> detail,
            Instant occurredAt) {}

    public record WorkflowCommand(
            String code,
            String name,
            String description,
            boolean active,
            int maxDepth,
            TriggerCommand trigger,
            List<ConditionCommand> conditions,
            List<ActionCommand> actions) {}

    public record TriggerCommand(String eventType, String sourceModule) {}

    public record ConditionCommand(
            String fieldPath, String operator, String expectedValue, String valueType) {}

    public record ActionCommand(
            String actionType,
            String label,
            Map<String, Object> configuration,
            String emittedEventType) {}

    public record ActivationCommand(boolean active) {}

    public record EventCommand(
            UUID eventId, String eventType, String sourceModule, Map<String, Object> payload) {}

    public record DispatchView(
            UUID rootEventId,
            long completed,
            long skipped,
            long loopBlocked,
            List<ExecutionView> executions) {}
}
