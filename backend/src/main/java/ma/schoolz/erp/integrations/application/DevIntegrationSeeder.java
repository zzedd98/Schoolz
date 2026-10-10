package ma.schoolz.erp.integrations.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ma.schoolz.erp.identity.TenantDirectory;
import ma.schoolz.erp.integrations.application.IntegrationAdapter.AdapterDescriptor;
import ma.schoolz.erp.integrations.application.IntegrationAdapter.DatasetSchema;
import ma.schoolz.erp.integrations.application.IntegrationService.ExportCommand;
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
@Order(300)
public class DevIntegrationSeeder implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final TenantDirectory tenants;
    private final IntegrationService service;
    private final List<IntegrationAdapter> adapters;
    private final ObjectMapper json;
    private final Clock clock;
    private final boolean enabled;
    private final String tenantSlug;

    public DevIntegrationSeeder(
            JdbcTemplate jdbc,
            TenantDirectory tenants,
            IntegrationService service,
            List<IntegrationAdapter> adapters,
            ObjectMapper json,
            Clock clock,
            @Value("${schoolz.demo.seed-enabled:false}") boolean enabled,
            @Value("${schoolz.demo.tenant-slug:}") String tenantSlug) {
        this.jdbc = jdbc;
        this.tenants = tenants;
        this.service = service;
        this.adapters = adapters;
        this.json = json;
        this.clock = clock;
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
        for (IntegrationAdapter adapter : adapters) seedAdapter(tenantId, adapter);
        if (count("SELECT count(*) FROM integration_transfer_job WHERE tenant_id=?", tenantId)
                == 0) {
            service.exportForTenant(
                    tenantId, actorId, "massar-file", new ExportCommand("STUDENTS", "CSV"));
        }
    }

    private void seedAdapter(UUID tenantId, IntegrationAdapter adapter) {
        AdapterDescriptor descriptor = adapter.descriptor();
        UUID adapterId =
                id(
                        "SELECT id FROM integration_adapter WHERE tenant_id=? AND adapter_key=?",
                        tenantId,
                        descriptor.key());
        if (adapterId == null) {
            adapterId = UUID.randomUUID();
            Instant now = clock.instant();
            jdbc.update(
                    """
                    INSERT INTO integration_adapter
                        (id,tenant_id,adapter_key,display_name,category,status,transport_mode,
                         capabilities_json,disclaimer,created_at,updated_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?)
                    """,
                    adapterId,
                    tenantId,
                    descriptor.key(),
                    descriptor.name(),
                    descriptor.category(),
                    descriptor.status(),
                    descriptor.transportMode(),
                    write(descriptor.capabilities()),
                    descriptor.disclaimer(),
                    Timestamp.from(now),
                    Timestamp.from(now));
        }
        for (DatasetSchema schema : adapter.schemas()) {
            for (String format : List.of("CSV", "XLSX")) {
                String direction =
                        schema.importable() && schema.exportable()
                                ? "BOTH"
                                : schema.importable() ? "IMPORT" : "EXPORT";
                String code =
                        (descriptor.key() + "_" + schema.dataset() + "_" + format)
                                .toUpperCase()
                                .replace('-', '_');
                if (count(
                                "SELECT count(*) FROM integration_mapping_profile WHERE tenant_id=?"
                                        + " AND code=?",
                                tenantId,
                                code)
                        > 0) continue;
                Map<String, String> mapping = new LinkedHashMap<>();
                schema.headers().forEach(header -> mapping.put(header, header));
                Instant now = clock.instant();
                jdbc.update(
                        """
                        INSERT INTO integration_mapping_profile
                            (id,tenant_id,adapter_id,code,name,dataset_type,direction,file_format,
                             field_mapping_json,active,created_at,updated_at)
                        VALUES (?,?,?,?,?,?,?,?,?,TRUE,?,?)
                        """,
                        UUID.randomUUID(),
                        tenantId,
                        adapterId,
                        code,
                        schema.label() + " · " + format,
                        schema.dataset(),
                        direction,
                        format,
                        write(mapping),
                        Timestamp.from(now),
                        Timestamp.from(now));
            }
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private UUID id(String sql, Object... arguments) {
        List<UUID> ids = jdbc.query(sql, (rs, row) -> rs.getObject(1, UUID.class), arguments);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private int count(String sql, Object... arguments) {
        Integer value = jdbc.queryForObject(sql, Integer.class, arguments);
        return value == null ? 0 : value;
    }
}
