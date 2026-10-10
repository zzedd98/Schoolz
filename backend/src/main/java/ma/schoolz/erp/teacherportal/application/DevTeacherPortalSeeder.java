package ma.schoolz.erp.teacherportal.application;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@Order(110)
public class DevTeacherPortalSeeder implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final boolean enabled;
    private final String tenantSlug;
    private final String email;
    private final String password;

    public DevTeacherPortalSeeder(
            JdbcTemplate jdbc,
            PasswordEncoder passwordEncoder,
            Clock clock,
            @Value("${schoolz.demo.seed-enabled:false}") boolean enabled,
            @Value("${schoolz.demo.tenant-slug:}") String tenantSlug,
            @Value("${schoolz.demo.teacher-email:prof.math@schoolz.ma}") String email,
            @Value("${schoolz.demo.teacher-password:}") String password) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.enabled = enabled;
        this.tenantSlug = tenantSlug;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (!enabled || password == null || password.isBlank()) return;
        UUID tenantId =
                id("SELECT id FROM identity_tenant WHERE slug=? AND active=TRUE", tenantSlug);
        UUID teacherRoleId =
                id("SELECT id FROM identity_role WHERE tenant_id=? AND code='TEACHER'", tenantId);
        UUID staffId =
                id(
                        "SELECT id FROM staff_profile WHERE tenant_id=? AND"
                                + " employee_number='DEMO-TEACH-001'",
                        tenantId);
        Instant now = clock.instant();
        String normalized = email.strip().toLowerCase(java.util.Locale.ROOT);
        List<UUID> existing =
                jdbc.query(
                        "SELECT id FROM identity_user_account WHERE tenant_id=? AND"
                                + " normalized_email=?",
                        (rs, row) -> rs.getObject("id", UUID.class),
                        tenantId,
                        normalized);
        UUID accountId;
        if (existing.isEmpty()) {
            accountId = UUID.randomUUID();
            jdbc.update(
                    """
                    INSERT INTO identity_user_account
                        (id,tenant_id,email,normalized_email,display_name,password_hash,locale,
                         status,email_verified_at,failed_login_attempts,mfa_enabled,created_at,
                         updated_at,version)
                    VALUES (?, ?, ?, ?, 'Fouad Mellouli', ?, 'fr-MA', 'ACTIVE', ?, 0, FALSE,
                            ?, ?, 0)
                    """,
                    accountId,
                    tenantId,
                    email.strip(),
                    normalized,
                    passwordEncoder.encode(password),
                    timestamp(now),
                    timestamp(now),
                    timestamp(now));
        } else {
            accountId = existing.getFirst();
        }
        jdbc.update(
                """
                INSERT INTO identity_user_role (user_id,role_id)
                SELECT ?, ?
                WHERE NOT EXISTS (
                    SELECT 1 FROM identity_user_role WHERE user_id=? AND role_id=?
                )
                """,
                accountId,
                teacherRoleId,
                accountId,
                teacherRoleId);
        int updated =
                jdbc.update(
                        """
                        UPDATE teacher_portal_profile
                        SET staff_profile_id=?, active=TRUE, updated_at=?
                        WHERE tenant_id=? AND user_account_id=?
                        """,
                        staffId,
                        timestamp(now),
                        tenantId,
                        accountId);
        if (updated == 0) {
            jdbc.update(
                    """
                    INSERT INTO teacher_portal_profile
                        (id,tenant_id,user_account_id,staff_profile_id,active,created_at,updated_at)
                    VALUES (?, ?, ?, ?, TRUE, ?, ?)
                    """,
                    UUID.randomUUID(),
                    tenantId,
                    accountId,
                    staffId,
                    timestamp(now),
                    timestamp(now));
        }
        jdbc.update(
                "UPDATE timetable_schedule SET status='PUBLISHED',updated_at=? WHERE tenant_id=?"
                        + " AND code='CENTRE-2026' AND status='DRAFT'",
                timestamp(now),
                tenantId);
    }

    private UUID id(String sql, Object... parameters) {
        return jdbc.queryForObject(sql, UUID.class, parameters);
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }
}
