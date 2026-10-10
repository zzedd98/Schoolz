package ma.schoolz.erp.identity;

import java.util.Map;
import java.util.UUID;

public interface IdentityAuditTrail {

    void record(
            UUID tenantId,
            UUID actorUserId,
            String action,
            String resourceType,
            String resourceId,
            String ipAddress,
            Map<String, ?> metadata,
            Object before,
            Object after);
}
