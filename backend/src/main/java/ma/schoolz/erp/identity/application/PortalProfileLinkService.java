package ma.schoolz.erp.identity.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PortalProfileLinkService {

    private final JdbcTemplate jdbc;

    public PortalProfileLinkService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Options options(UUID tenantId) {
        List<ProfileOption> staff =
                jdbc.query(
                        """
                        SELECT sp.id, concat(p.first_name, ' ', p.last_name), sp.employee_number,
                               tpp.user_account_id
                        FROM staff_profile sp
                        JOIN person p ON p.id=sp.person_id AND p.tenant_id=sp.tenant_id
                        LEFT JOIN teacher_portal_profile tpp
                          ON tpp.staff_profile_id=sp.id AND tpp.tenant_id=sp.tenant_id
                         AND tpp.active=TRUE
                        WHERE sp.tenant_id=? AND sp.employment_status <> 'TERMINATED'
                        ORDER BY p.last_name, p.first_name
                        """,
                        PortalProfileLinkService::option,
                        tenantId);
        List<ProfileOption> students =
                jdbc.query(
                        """
                        SELECT sp.id, concat(p.first_name, ' ', p.last_name), NULL,
                               spp.user_account_id
                        FROM student_profile sp
                        JOIN person p ON p.id=sp.person_id AND p.tenant_id=sp.tenant_id
                        LEFT JOIN student_portal_profile spp
                          ON spp.student_id=sp.id AND spp.tenant_id=sp.tenant_id
                         AND spp.active=TRUE
                        WHERE sp.tenant_id=? AND sp.status <> 'ARCHIVED'
                        ORDER BY p.last_name, p.first_name
                        """,
                        PortalProfileLinkService::option,
                        tenantId);
        return new Options(staff, students);
    }

    public void link(
            UUID tenantId,
            UUID accountId,
            Set<String> roleCodes,
            UUID staffProfileId,
            UUID studentId,
            Instant now) {
        if (roleCodes.contains("TEACHER") && staffProfileId == null) {
            throw new IdentityConflictException("A teacher profile must be selected");
        }
        if (roleCodes.contains("STUDENT") && studentId == null) {
            throw new IdentityConflictException("A student profile must be selected");
        }
        if (staffProfileId != null && !roleCodes.contains("TEACHER")) {
            throw new IdentityConflictException("A staff profile requires the teacher role");
        }
        if (studentId != null && !roleCodes.contains("STUDENT")) {
            throw new IdentityConflictException("A student profile requires the student role");
        }
        try {
            if (staffProfileId != null) {
                requireAvailable("staff_profile", staffProfileId, tenantId);
                jdbc.update(
                        """
                        INSERT INTO teacher_portal_profile
                          (id, tenant_id, user_account_id, staff_profile_id, active, created_at, updated_at)
                        VALUES (?, ?, ?, ?, TRUE, ?, ?)
                        """,
                        UUID.randomUUID(),
                        tenantId,
                        accountId,
                        staffProfileId,
                        now,
                        now);
            }
            if (studentId != null) {
                requireAvailable("student_profile", studentId, tenantId);
                jdbc.update(
                        """
                        INSERT INTO student_portal_profile
                          (id, tenant_id, user_account_id, student_id, active, created_at, updated_at)
                        VALUES (?, ?, ?, ?, TRUE, ?, ?)
                        """,
                        UUID.randomUUID(),
                        tenantId,
                        accountId,
                        studentId,
                        now,
                        now);
            }
        } catch (DataIntegrityViolationException exception) {
            throw new IdentityConflictException("The selected profile is already linked");
        }
    }

    private void requireAvailable(String table, UUID profileId, UUID tenantId) {
        if (!table.equals("staff_profile") && !table.equals("student_profile")) {
            throw new IllegalArgumentException("Unsupported profile table");
        }
        Integer count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM " + table + " WHERE id=? AND tenant_id=?",
                        Integer.class,
                        profileId,
                        tenantId);
        if (count == null || count != 1) {
            throw new IdentityNotFoundException();
        }
    }

    private static ProfileOption option(ResultSet result, int row) throws SQLException {
        return new ProfileOption(
                result.getObject(1, UUID.class),
                result.getString(2),
                result.getString(3),
                result.getObject(4, UUID.class));
    }

    public record Options(List<ProfileOption> staff, List<ProfileOption> students) {}

    public record ProfileOption(
            UUID id, String displayName, String reference, UUID linkedAccountId) {}
}
