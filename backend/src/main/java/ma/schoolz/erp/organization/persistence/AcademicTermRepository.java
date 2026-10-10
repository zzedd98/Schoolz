package ma.schoolz.erp.organization.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ma.schoolz.erp.organization.domain.AcademicTerm;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademicTermRepository extends JpaRepository<AcademicTerm, UUID> {
    List<AcademicTerm> findAllByTenantIdAndAcademicYearIdOrderBySequenceAsc(
            UUID tenantId, UUID yearId);

    boolean existsByTenantIdAndAcademicYearIdAndCode(UUID tenantId, UUID yearId, String code);

    boolean existsByTenantIdAndAcademicYearIdAndStartsOnLessThanEqualAndEndsOnGreaterThanEqual(
            UUID tenantId, UUID yearId, LocalDate endDate, LocalDate startDate);
}
