package ma.schoolz.erp.organization.persistence;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.organization.domain.GradeLevel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GradeLevelRepository extends JpaRepository<GradeLevel, UUID> {
    List<GradeLevel> findAllByTenantIdOrderBySequenceAsc(UUID tenantId);

    List<GradeLevel> findAllByTenantIdAndSchoolLevelIdOrderBySequenceAsc(
            UUID tenantId, UUID levelId);

    Optional<GradeLevel> findByIdAndTenantId(UUID id, UUID tenantId);

    List<GradeLevel> findAllByTenantIdAndIdIn(UUID tenantId, Set<UUID> ids);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);
}
