package ma.schoolz.erp.organization.persistence;

import java.util.Optional;
import java.util.UUID;
import ma.schoolz.erp.organization.domain.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
    Optional<Organization> findByTenantId(UUID tenantId);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);
}
