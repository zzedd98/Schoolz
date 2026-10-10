package ma.schoolz.erp.organization.persistence;

import java.util.List;
import java.util.UUID;
import ma.schoolz.erp.organization.domain.CampusGradeOffering;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampusGradeOfferingRepository extends JpaRepository<CampusGradeOffering, UUID> {
    List<CampusGradeOffering> findAllByTenantIdAndCampusId(UUID tenantId, UUID campusId);

    boolean existsByTenantIdAndCampusIdAndGradeLevelId(
            UUID tenantId, UUID campusId, UUID gradeLevelId);

    void deleteAllByTenantIdAndCampusId(UUID tenantId, UUID campusId);
}
