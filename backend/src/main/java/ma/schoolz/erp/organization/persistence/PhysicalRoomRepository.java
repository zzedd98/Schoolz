package ma.schoolz.erp.organization.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ma.schoolz.erp.organization.domain.PhysicalRoom;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhysicalRoomRepository extends JpaRepository<PhysicalRoom, UUID> {
    List<PhysicalRoom> findAllByTenantIdOrderByNameAsc(UUID tenantId);

    Optional<PhysicalRoom> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndCampusIdAndCode(UUID tenantId, UUID campusId, String code);
}
