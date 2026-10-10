package ma.schoolz.erp.identity.application;

import java.util.Optional;
import ma.schoolz.erp.identity.TenantDirectory;
import ma.schoolz.erp.identity.domain.IdentityTenant;
import ma.schoolz.erp.identity.persistence.IdentityTenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class IdentityTenantDirectory implements TenantDirectory {

    private final IdentityTenantRepository tenants;

    IdentityTenantDirectory(IdentityTenantRepository tenants) {
        this.tenants = tenants;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TenantSummary> findActiveBySlug(String slug) {
        return tenants.findBySlugAndActiveTrue(IdentityTenant.normalizeSlug(slug))
                .map(tenant -> new TenantSummary(tenant.id(), tenant.slug(), tenant.displayName()));
    }
}
