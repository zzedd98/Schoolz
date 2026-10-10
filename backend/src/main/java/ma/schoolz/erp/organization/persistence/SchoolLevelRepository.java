package ma.schoolz.erp.organization.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ma.schoolz.erp.organization.domain.SchoolLevel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolLevelRepository extends JpaRepository<SchoolLevel, UUID> {
    List<SchoolLevel> findAllByTenantIdOrderBySequenceAsc(UUID tenantId);

    Optional<SchoolLevel> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);
}
