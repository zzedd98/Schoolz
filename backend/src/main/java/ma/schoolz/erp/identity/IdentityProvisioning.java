package ma.schoolz.erp.identity;

import java.util.UUID;

public interface IdentityProvisioning {

    SchoolIdentity createSchoolIdentity(
            String tenantSlug,
            String schoolName,
            String ownerName,
            String ownerEmail,
            String locale);

    SchoolIdentity schoolIdentity(UUID tenantId, UUID ownerUserId);

    final class ProvisioningException extends RuntimeException {
        public ProvisioningException(String message) {
            super(message);
        }
    }

    record SchoolIdentity(
            UUID tenantId,
            String tenantSlug,
            String schoolName,
            UUID ownerUserId,
            String ownerEmail,
            String ownerStatus,
            String activationToken) {}
}
