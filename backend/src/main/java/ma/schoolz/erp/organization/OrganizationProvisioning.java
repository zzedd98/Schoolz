package ma.schoolz.erp.organization;

import java.util.UUID;

public interface OrganizationProvisioning {

    SchoolStructure createInitialStructure(
            UUID tenantId,
            String tenantSlug,
            String schoolName,
            String ownerEmail,
            String locale,
            String campusName,
            String city,
            String countryCode,
            String timezone);

    record SchoolStructure(UUID organizationId, UUID campusId) {}
}
