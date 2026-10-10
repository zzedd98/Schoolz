package ma.schoolz.erp.identity;

import java.util.Optional;
import java.util.UUID;

public interface TenantDirectory {

    Optional<TenantSummary> findActiveBySlug(String slug);

    record TenantSummary(UUID id, String slug, String displayName) {}
}
