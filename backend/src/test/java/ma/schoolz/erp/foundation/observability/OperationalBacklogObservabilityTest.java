package ma.schoolz.erp.foundation.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;
import org.springframework.jdbc.core.JdbcTemplate;

class OperationalBacklogObservabilityTest {
    @Test
    void exposesLowCardinalityBacklogMetricsAndHealth() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(0);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(0);
        OperationalBacklogObservability observability =
                new OperationalBacklogObservability(
                        jdbc, Clock.fixed(Instant.parse("2026-09-01T08:00:00Z"), ZoneOffset.UTC));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        observability.bindTo(registry);

        assertThat(registry.find("schoolz.backlog.webhook.dead_letters").gauge()).isNotNull();
        assertThat(registry.find("schoolz.backlog.workflow.failed").gauge()).isNotNull();
        assertThat(registry.find("schoolz.backlog.notification.failed").gauge()).isNotNull();
        assertThat(registry.find("schoolz.backlog.privacy.overdue").gauge()).isNotNull();
        assertThat(registry.find("schoolz.backlog.task.overdue").gauge()).isNotNull();
        assertThat(observability.health().getStatus()).isEqualTo(Status.UP);
    }
}
