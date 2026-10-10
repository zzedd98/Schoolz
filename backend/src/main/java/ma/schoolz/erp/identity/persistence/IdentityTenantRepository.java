package ma.schoolz.erp.identity.persistence;

import java.util.Optional;
import java.util.UUID;
import ma.schoolz.erp.identity.domain.IdentityTenant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentityTenantRepository extends JpaRepository<IdentityTenant, UUID> {
    Optional<IdentityTenant> findBySlugAndActiveTrue(String slug);

    boolean existsBySlug(String slug);
}
