package ma.schoolz.erp.enrollment.application;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import ma.schoolz.erp.admissions.AdmissionPortalAccess.PortalIdentity;
import ma.schoolz.erp.enrollment.PortalStudentAccess;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
class PortalStudentAccessAdapter implements PortalStudentAccess {
    private final JdbcTemplate jdbc;

    PortalStudentAccessAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<UUID> linkedStudentIds(PortalIdentity parent) {
        return jdbc
                .query(
                        "SELECT student_id FROM enrollment_portal_student_link WHERE tenant_id=?"
                                + " AND portal_account_id=?",
                        (result, row) -> result.getObject(1, UUID.class),
                        parent.tenantId(),
                        parent.accountId())
                .stream()
                .collect(Collectors.toUnmodifiableSet());
    }
}
