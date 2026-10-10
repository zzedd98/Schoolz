package ma.schoolz.erp.organization.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ma.schoolz.erp.organization.domain.Campus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampusRepository extends JpaRepository<Campus, UUID> {
    List<Campus> findAllByTenantIdOrderByNameAsc(UUID tenantId);

    Optional<Campus> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);
}
