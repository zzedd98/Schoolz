package ma.schoolz.erp.workflow.application;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ma.schoolz.erp.identity.TenantDirectory;
import ma.schoolz.erp.workflow.application.WorkflowService.ActionCommand;
import ma.schoolz.erp.workflow.application.WorkflowService.ConditionCommand;
import ma.schoolz.erp.workflow.application.WorkflowService.EventCommand;
import ma.schoolz.erp.workflow.application.WorkflowService.TriggerCommand;
import ma.schoolz.erp.workflow.application.WorkflowService.WorkflowCommand;
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
@Order(290)
public class DevWorkflowSeeder implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final TenantDirectory tenants;
    private final WorkflowService service;
    private final boolean enabled;
    private final String tenantSlug;

    public DevWorkflowSeeder(
            JdbcTemplate jdbc,
            TenantDirectory tenants,
            WorkflowService service,
            @Value("${schoolz.demo.seed-enabled:false}") boolean enabled,
            @Value("${schoolz.demo.tenant-slug:}") String tenantSlug) {
        this.jdbc = jdbc;
        this.tenants = tenants;
        this.service = service;
        this.enabled = enabled;
        this.tenantSlug = tenantSlug;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (!enabled || tenantSlug.isBlank()) return;
        UUID tenantId =
                tenants.findActiveBySlug(tenantSlug)
                        .map(TenantDirectory.TenantSummary::id)
                        .orElse(null);
        if (tenantId == null) return;
        UUID actorId =
                id(
                        "SELECT id FROM identity_user_account WHERE tenant_id=? ORDER BY created_at"
                                + " FETCH FIRST 1 ROW ONLY",
                        tenantId);
        if (actorId == null) return;

        seed(
                tenantId,
                actorId,
                new WorkflowCommand(
                        "RELANCE_FACTURE_J7",
                        "Relancer une facture après 7 jours",
                        "Quand une facture est échue depuis plus de sept jours, prévenir et créer"
                                + " un suivi.",
                        true,
                        6,
                        new TriggerCommand("invoice.overdue", "billing"),
                        List.of(new ConditionCommand("days", "GT", "7", "NUMBER")),
                        List.of(
                                action("SEND_NOTIFICATION", "Notifier le gestionnaire"),
                                action("SEND_EMAIL", "Préparer l’e-mail de relance"),
                                action("CREATE_TASK", "Créer une tâche de recouvrement"))));
        seed(
                tenantId,
                actorId,
                new WorkflowCommand(
                        "ABSENCE_NON_JUSTIFIEE",
                        "Prévenir le parent d’une absence",
                        "Déclenche une information parent uniquement lorsque l’absence est non"
                                + " justifiée.",
                        true,
                        5,
                        new TriggerCommand("student.absence.created", "attendance"),
                        List.of(new ConditionCommand("unjustified", "EQ", "true", "BOOLEAN")),
                        List.of(action("NOTIFY_PARENT", "Notifier le responsable légal"))));
        seed(
                tenantId,
                actorId,
                new WorkflowCommand(
                        "DEMO_PROTECTION_BOUCLE",
                        "Démonstration de la protection de boucle",
                        "Réémet volontairement son événement pour démontrer l’arrêt par"
                                + " ascendance.",
                        true,
                        4,
                        new TriggerCommand("workflow.demo.loop", "workflow"),
                        List.of(),
                        List.of(
                                new ActionCommand(
                                        "EMIT_EVENT",
                                        "Réémettre l’événement de démonstration",
                                        Map.of("purpose", "loop-guard-proof"),
                                        "workflow.demo.loop"))));

        if (count("SELECT count(*) FROM workflow_execution WHERE tenant_id=?", tenantId) == 0) {
            service.dispatchForTenant(
                    tenantId,
                    actorId,
                    new EventCommand(
                            UUID.nameUUIDFromBytes("schoolz-demo-invoice-overdue".getBytes()),
                            "invoice.overdue",
                            "billing",
                            Map.of("invoiceId", "FAC-2026-0042", "days", 12, "amount", 1500)));
            service.dispatchForTenant(
                    tenantId,
                    actorId,
                    new EventCommand(
                            UUID.nameUUIDFromBytes("schoolz-demo-absence".getBytes()),
                            "student.absence.created",
                            "attendance",
                            Map.of("student", "Yasmine Alaoui", "unjustified", true)));
            service.dispatchForTenant(
                    tenantId,
                    actorId,
                    new EventCommand(
                            UUID.nameUUIDFromBytes("schoolz-demo-loop".getBytes()),
                            "workflow.demo.loop",
                            "workflow",
                            Map.of("demo", true)));
        }
    }

    private void seed(UUID tenantId, UUID actorId, WorkflowCommand command) {
        if (count(
                        "SELECT count(*) FROM workflow_definition WHERE tenant_id=? AND code=?",
                        tenantId,
                        command.code())
                > 0) return;
        UUID workflowId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
                """
                INSERT INTO workflow_definition
                    (id,tenant_id,code,name,description,active,max_depth,created_by_user_id,
                     created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?)
                """,
                workflowId,
                tenantId,
                command.code(),
                command.name(),
                command.description(),
                command.active(),
                command.maxDepth(),
                actorId,
                Timestamp.from(now),
                Timestamp.from(now));
        jdbc.update(
                "INSERT INTO workflow_trigger"
                        + " (id,tenant_id,workflow_id,event_type,source_module,created_at)"
                        + " VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(),
                tenantId,
                workflowId,
                command.trigger().eventType(),
                command.trigger().sourceModule(),
                Timestamp.from(now));
        for (int index = 0; index < command.conditions().size(); index++) {
            ConditionCommand condition = command.conditions().get(index);
            jdbc.update(
                    "INSERT INTO workflow_condition"
                            + " (id,tenant_id,workflow_id,field_path,comparison_operator,"
                            + "expected_value,value_type,sort_order,created_at)"
                            + " VALUES (?,?,?,?,?,?,?,?,?)",
                    UUID.randomUUID(),
                    tenantId,
                    workflowId,
                    condition.fieldPath(),
                    condition.operator(),
                    condition.expectedValue(),
                    condition.valueType(),
                    index,
                    Timestamp.from(now));
        }
        for (int index = 0; index < command.actions().size(); index++) {
            ActionCommand action = command.actions().get(index);
            jdbc.update(
                    "INSERT INTO workflow_action"
                        + " (id,tenant_id,workflow_id,action_type,action_label,configuration_json,emitted_event_type,sort_order,created_at)"
                        + " VALUES (?,?,?,?,?,?,?,?,?)",
                    UUID.randomUUID(),
                    tenantId,
                    workflowId,
                    action.actionType(),
                    action.label(),
                    "{}",
                    action.emittedEventType(),
                    index,
                    Timestamp.from(now));
        }
    }

    private static ActionCommand action(String type, String label) {
        return new ActionCommand(type, label, Map.of("adapter", "local"), null);
    }

    private UUID id(String sql, Object... arguments) {
        List<UUID> values = jdbc.query(sql, (rs, row) -> rs.getObject(1, UUID.class), arguments);
        return values.isEmpty() ? null : values.getFirst();
    }

    private int count(String sql, Object... arguments) {
        Integer value = jdbc.queryForObject(sql, Integer.class, arguments);
        return value == null ? 0 : value;
    }
}
