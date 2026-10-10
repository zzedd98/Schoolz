package ma.schoolz.erp.publicapi.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ma.schoolz.erp.identity.TenantDirectory;
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
@Order(330)
public class DevPublicApiSeeder implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final TenantDirectory tenants;
    private final ObjectMapper json;
    private final Clock clock;
    private final boolean enabled;
    private final String tenantSlug;

    public DevPublicApiSeeder(
            JdbcTemplate jdbc,
            TenantDirectory tenants,
            ObjectMapper json,
            Clock clock,
            @Value("${schoolz.demo.seed-enabled:false}") boolean enabled,
            @Value("${schoolz.demo.tenant-slug:}") String tenantSlug) {
        this.jdbc = jdbc;
        this.tenants = tenants;
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
        Integer count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM public_api_client WHERE tenant_id=? AND key_hash=?",
                        Integer.class,
                        tenantId,
                        PublicApiService.hashKey(PublicApiService.DEMO_KEY));
        if (count != null && count > 0) return;
        List<UUID> actors =
                jdbc.query(
                        "SELECT id FROM identity_user_account WHERE tenant_id=? ORDER BY created_at"
                                + " FETCH FIRST 1 ROW ONLY",
                        (rs, row) -> rs.getObject(1, UUID.class),
                        tenantId);
        if (actors.isEmpty()) return;
        Instant now = clock.instant();
        jdbc.update(
                """
                INSERT INTO public_api_client
                    (id,tenant_id,name,description,key_prefix,key_hash,scopes_json,
                     rate_limit_per_minute,active,created_by_user_id,created_at)
                VALUES (?,?,?,?,?,?,?,?,TRUE,?,?)
                """,
                UUID.randomUUID(),
                tenantId,
                "SIS de démonstration",
                "Client serveur local couvrant les trois ressources publiques M37.",
                PublicApiService.prefix(PublicApiService.DEMO_KEY),
                PublicApiService.hashKey(PublicApiService.DEMO_KEY),
                write(List.of("students:read", "invoices:read", "classes:read")),
                120,
                actors.getFirst(),
                Timestamp.from(now));
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
