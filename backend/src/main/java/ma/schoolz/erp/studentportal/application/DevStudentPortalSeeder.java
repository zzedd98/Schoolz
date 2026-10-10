package ma.schoolz.erp.studentportal.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
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
@Order(125)
public class DevStudentPortalSeeder implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final boolean enabled;
    private final String tenantSlug;
    private final String email;
    private final String password;

    public DevStudentPortalSeeder(
            JdbcTemplate jdbc,
            PasswordEncoder passwordEncoder,
            Clock clock,
            @Value("${schoolz.demo.seed-enabled:false}") boolean enabled,
            @Value("${schoolz.demo.tenant-slug:}") String tenantSlug,
            @Value("${schoolz.demo.student-email:yasmine.eleve@schoolz.ma}") String email,
            @Value("${schoolz.demo.student-password:}") String password) {
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
        if (!enabled || tenantSlug.isBlank() || email.isBlank() || password.isBlank()) return;
        UUID tenantId =
                id("SELECT id FROM identity_tenant WHERE slug=? AND active=TRUE", tenantSlug);
        UUID roleId =
                id("SELECT id FROM identity_role WHERE tenant_id=? AND code='STUDENT'", tenantId);
        UUID studentId =
                id(
                        """
                        SELECT sp.id FROM student_profile sp
                        JOIN student_identifier si
                          ON si.student_id=sp.id AND si.tenant_id=sp.tenant_id
                        WHERE sp.tenant_id=? AND si.identifier_value='SZ-2026-0001'
                          AND sp.status='ACTIVE'
                        """,
                        tenantId);
        Instant now = clock.instant();
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        List<UUID> existing =
                jdbc.query(
                        "SELECT id FROM identity_user_account WHERE tenant_id=? AND"
                                + " normalized_email=?",
                        (result, row) -> result.getObject(1, UUID.class),
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
                    VALUES (?, ?, ?, ?, 'Yasmine Alaoui', ?, 'fr-MA', 'ACTIVE', ?, 0, FALSE,
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
                roleId,
                accountId,
                roleId);
        int updated =
                jdbc.update(
                        """
                        UPDATE student_portal_profile
                        SET student_id=?,active=TRUE,updated_at=?
                        WHERE tenant_id=? AND user_account_id=?
                        """,
                        studentId,
                        timestamp(now),
                        tenantId,
                        accountId);
        if (updated == 0) {
            jdbc.update(
                    """
                    INSERT INTO student_portal_profile
                        (id,tenant_id,user_account_id,student_id,active,created_at,updated_at)
                    VALUES (?, ?, ?, ?, TRUE, ?, ?)
                    """,
                    UUID.randomUUID(),
                    tenantId,
                    accountId,
                    studentId,
                    timestamp(now),
                    timestamp(now));
        }
    }

    private UUID id(String sql, Object... parameters) {
        return jdbc.queryForObject(sql, UUID.class, parameters);
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }
}
