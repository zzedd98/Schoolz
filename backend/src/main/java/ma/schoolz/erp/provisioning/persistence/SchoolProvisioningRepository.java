package ma.schoolz.erp.provisioning.persistence;

import java.util.Optional;
import java.util.UUID;
import ma.schoolz.erp.provisioning.domain.SchoolProvisioning;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolProvisioningRepository extends JpaRepository<SchoolProvisioning, UUID> {
    Optional<SchoolProvisioning> findByIdempotencyKey(String idempotencyKey);
}
