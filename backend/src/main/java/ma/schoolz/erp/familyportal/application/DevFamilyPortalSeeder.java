package ma.schoolz.erp.familyportal.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@Order(120)
public class DevFamilyPortalSeeder implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final boolean enabled;
    private final String tenantSlug;
    private final String parentEmail;

    public DevFamilyPortalSeeder(
            JdbcTemplate jdbc,
            Clock clock,
            @Value("${schoolz.demo.seed-enabled:false}") boolean enabled,
            @Value("${schoolz.demo.tenant-slug:}") String tenantSlug,
            @Value("${schoolz.demo.parent-email:parent.demo@schoolz.ma}") String parentEmail) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.enabled = enabled;
        this.tenantSlug = tenantSlug;
        this.parentEmail = parentEmail;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (!enabled || tenantSlug.isBlank() || parentEmail.isBlank()) return;
        List<UUID> tenants =
                jdbc.query(
                        "SELECT id FROM identity_tenant WHERE slug=? AND active=TRUE",
                        (result, row) -> result.getObject(1, UUID.class),
                        tenantSlug);
        if (tenants.isEmpty()) return;
        UUID tenantId = tenants.getFirst();
        List<UUID> accounts =
                jdbc.query(
                        "SELECT id FROM admission_portal_account WHERE tenant_id=? AND"
                                + " normalized_email=? AND status='ACTIVE'",
                        (result, row) -> result.getObject(1, UUID.class),
                        tenantId,
                        parentEmail.strip().toLowerCase(java.util.Locale.ROOT));
        if (accounts.isEmpty()) return;
        UUID accountId = accounts.getFirst();
        List<UUID> students =
                jdbc.query(
                        """
                        SELECT link.student_id
                        FROM enrollment_portal_student_link link
                        LEFT JOIN student_enrollment e
                          ON e.student_id=link.student_id AND e.tenant_id=link.tenant_id
                          AND e.status='ACTIVE'
                        JOIN student_profile sp
                          ON sp.id=link.student_id AND sp.tenant_id=link.tenant_id
                        JOIN person p ON p.id=sp.person_id AND p.tenant_id=sp.tenant_id
                        WHERE link.tenant_id=? AND link.portal_account_id=?
                        ORDER BY CASE WHEN e.id IS NULL THEN 1 ELSE 0 END,p.first_name,p.last_name
                        LIMIT 1
                        """,
                        (result, row) -> result.getObject(1, UUID.class),
                        tenantId,
                        accountId);
        if (students.isEmpty()) return;
        Instant now = clock.instant();
        int updated =
                jdbc.update(
                        "UPDATE family_portal_preference SET preferred_student_id=?,updated_at=?"
                                + " WHERE tenant_id=? AND portal_account_id=?",
                        students.getFirst(),
                        Timestamp.from(now),
                        tenantId,
                        accountId);
        if (updated == 0) {
            jdbc.update(
                    "INSERT INTO family_portal_preference"
                        + " (id,tenant_id,portal_account_id,preferred_student_id,created_at,updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(),
                    tenantId,
                    accountId,
                    students.getFirst(),
                    Timestamp.from(now),
                    Timestamp.from(now));
        }
    }
}
