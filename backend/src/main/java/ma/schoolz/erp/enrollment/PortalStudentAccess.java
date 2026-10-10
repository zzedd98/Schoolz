package ma.schoolz.erp.enrollment;

import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.admissions.AdmissionPortalAccess.PortalIdentity;

public interface PortalStudentAccess {
    Set<UUID> linkedStudentIds(PortalIdentity parent);
}
