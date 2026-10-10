package ma.schoolz.erp.globalsearch.application;

import java.util.UUID;
import ma.schoolz.erp.identity.TenantDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
@Order(350)
public class DevGlobalSearchSeeder implements ApplicationRunner {
    private final GlobalSearchService search;
    private final TenantDirectory tenants;
    private final boolean enabled;
    private final String tenantSlug;

    public DevGlobalSearchSeeder(
            GlobalSearchService search,
            TenantDirectory tenants,
            @Value("${schoolz.demo.seed-enabled:false}") boolean enabled,
            @Value("${schoolz.demo.tenant-slug:}") String tenantSlug) {
        this.search = search;
        this.tenants = tenants;
        this.enabled = enabled;
        this.tenantSlug = tenantSlug;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (!enabled || tenantSlug.isBlank()) return;
        UUID tenantId =
                tenants.findActiveBySlug(tenantSlug)
                        .map(TenantDirectory.TenantSummary::id)
                        .orElse(null);
        if (tenantId != null) search.refreshTenant(tenantId);
    }
}
