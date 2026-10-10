package ma.schoolz.erp.foundation.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component("schoolzBacklog")
public class OperationalBacklogObservability implements MeterBinder, HealthIndicator {
    private static final Status DEGRADED = new Status("DEGRADED");

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public OperationalBacklogObservability(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        gauge(registry, "schoolz.backlog.webhook.dead_letters", Backlog.WEBHOOK_DEAD_LETTERS);
        gauge(registry, "schoolz.backlog.workflow.failed", Backlog.WORKFLOW_FAILED);
        gauge(registry, "schoolz.backlog.notification.failed", Backlog.NOTIFICATION_FAILED);
        gauge(registry, "schoolz.backlog.privacy.overdue", Backlog.PRIVACY_OVERDUE);
        gauge(registry, "schoolz.backlog.task.overdue", Backlog.TASK_OVERDUE);
    }

    @Override
    public Health health() {
        Map<String, Integer> values = new LinkedHashMap<>();
        values.put("webhookDeadLetters", count(Backlog.WEBHOOK_DEAD_LETTERS));
        values.put("workflowFailed", count(Backlog.WORKFLOW_FAILED));
        values.put("notificationFailed", count(Backlog.NOTIFICATION_FAILED));
        values.put("privacyOverdue", count(Backlog.PRIVACY_OVERDUE));
        values.put("taskOverdue", count(Backlog.TASK_OVERDUE));
        int total = values.values().stream().mapToInt(Integer::intValue).sum();
        Health.Builder builder = total == 0 ? Health.up() : Health.status(DEGRADED);
        return builder.withDetail("total", total).withDetails(values).build();
    }

    private void gauge(MeterRegistry registry, String name, Backlog backlog) {
        Gauge.builder(name, this, ignored -> count(backlog))
                .description(backlog.description)
                .strongReference(true)
                .register(registry);
    }

    private int count(Backlog backlog) {
        Integer value;
        if (backlog == Backlog.PRIVACY_OVERDUE) {
            value =
                    jdbc.queryForObject(
                            backlog.sql, Integer.class, java.sql.Timestamp.from(clock.instant()));
        } else if (backlog == Backlog.TASK_OVERDUE) {
            value = jdbc.queryForObject(backlog.sql, Integer.class, LocalDate.now(clock));
        } else {
            value = jdbc.queryForObject(backlog.sql, Integer.class);
        }
        return value == null ? 0 : value;
    }

    private enum Backlog {
        WEBHOOK_DEAD_LETTERS(
                "SELECT count(*) FROM webhook_delivery WHERE status='DEAD_LETTER'",
                "Livraisons webhook en dead letter"),
        WORKFLOW_FAILED(
                "SELECT count(*) FROM workflow_execution WHERE status='FAILED'",
                "Exécutions de workflow en échec"),
        NOTIFICATION_FAILED(
                "SELECT count(*) FROM communication_notification_delivery WHERE status='FAILED'",
                "Livraisons de notification en échec"),
        PRIVACY_OVERDUE(
                "SELECT count(*) FROM privacy_data_subject_request WHERE status NOT IN"
                        + " ('FULFILLED','REJECTED','CANCELLED') AND due_at<?",
                "Demandes de droits arrivées à échéance"),
        TASK_OVERDUE(
                "SELECT count(*) FROM internal_task WHERE status IN ('TODO','IN_PROGRESS') AND"
                        + " due_date<?",
                "Tâches internes en retard");

        private final String sql;
        private final String description;

        Backlog(String sql, String description) {
            this.sql = sql;
            this.description = description;
        }
    }
}
