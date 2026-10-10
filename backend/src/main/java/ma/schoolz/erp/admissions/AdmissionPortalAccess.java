package ma.schoolz.erp.admissions;

import java.util.UUID;

public interface AdmissionPortalAccess {
    String COOKIE_NAME = "schoolz_admission";

    PortalIdentity require(String rawToken);

    record PortalIdentity(
            UUID tenantId, UUID accountId, String email, String displayName, String locale) {}
}
