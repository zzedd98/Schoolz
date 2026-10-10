package ma.schoolz.erp.organization.persistence;

import java.util.Optional;
import java.util.UUID;
import ma.schoolz.erp.organization.domain.SchoolSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolSettingsRepository extends JpaRepository<SchoolSettings, UUID> {
    Optional<SchoolSettings> findByTenantId(UUID tenantId);
}
