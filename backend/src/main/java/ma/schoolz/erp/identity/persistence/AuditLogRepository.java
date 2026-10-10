package ma.schoolz.erp.identity.persistence;

import java.util.List;
import java.util.UUID;
import ma.schoolz.erp.identity.domain.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findAllByTenantIdOrderByOccurredAtDesc(UUID tenantId, Pageable pageable);
}
