package ma.schoolz.erp.organization.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ma.schoolz.erp.organization.domain.AcademicYear;
import ma.schoolz.erp.organization.domain.AcademicYearStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademicYearRepository extends JpaRepository<AcademicYear, UUID> {
    List<AcademicYear> findAllByTenantIdOrderByStartsOnDesc(UUID tenantId);

    Optional<AcademicYear> findByIdAndTenantId(UUID id, UUID tenantId);

    List<AcademicYear> findAllByTenantIdAndStatus(UUID tenantId, AcademicYearStatus status);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);
}
