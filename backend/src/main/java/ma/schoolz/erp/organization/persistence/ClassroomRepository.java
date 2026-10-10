package ma.schoolz.erp.organization.persistence;

import java.util.List;
import java.util.UUID;
import ma.schoolz.erp.organization.domain.Classroom;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassroomRepository extends JpaRepository<Classroom, UUID> {
    List<Classroom> findAllByTenantIdOrderByNameAsc(UUID tenantId);

    boolean existsByTenantIdAndAcademicYearIdAndCampusIdAndCode(
            UUID tenantId, UUID yearId, UUID campusId, String code);
}
