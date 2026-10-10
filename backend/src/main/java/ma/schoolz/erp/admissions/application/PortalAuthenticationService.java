package ma.schoolz.erp.admissions.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import ma.schoolz.erp.foundation.security.SecureTokenService;
import ma.schoolz.erp.identity.TenantDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortalAuthenticationService {

    public static final String COOKIE_NAME = "schoolz_admission";
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final JdbcTemplate jdbc;
    private final TenantDirectory tenants;
    private final PasswordEncoder passwordEncoder;
    private final SecureTokenService tokens;
    private final Clock clock;
    private final Duration sessionTtl;

    public PortalAuthenticationService(
            JdbcTemplate jdbc,
            TenantDirectory tenants,
            PasswordEncoder passwordEncoder,
            SecureTokenService tokens,
            Clock clock,
            @Value("${schoolz.admissions.portal-session-ttl:7d}") Duration sessionTtl) {
        this.jdbc = jdbc;
        this.tenants = tenants;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.clock = clock;
        this.sessionTtl = sessionTtl;
    }

    @Transactional
    public PortalSession register(RegisterCommand command, String ipAddress, String userAgent) {
        UUID tenantId =
                tenants.findActiveBySlug(command.tenantSlug())
                        .orElseThrow(() -> new AdmissionNotFoundException("École introuvable"))
                        .id();
        String email = normalizeEmail(command.email());
        validateAccount(command.displayName(), email, command.password());
        UUID accountId = UUID.randomUUID();
        Instant now = clock.instant();
        try {
            jdbc.update(
                    """
                    INSERT INTO admission_portal_account
                        (id,tenant_id,email,normalized_email,display_name,phone,password_hash,
                         locale,status,created_at,updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'fr-MA', 'ACTIVE', ?, ?)
                    """,
                    accountId,
                    tenantId,
                    email,
                    email,
                    command.displayName().strip(),
                    blankToNull(command.phone()),
                    passwordEncoder.encode(command.password()),
                    timestamp(now),
                    timestamp(now));
        } catch (DuplicateKeyException exception) {
            throw new AdmissionConflictException("Un compte existe déjà avec cette adresse e-mail");
        }
        return createSession(
                new PortalPrincipal(
                        tenantId, accountId, email, command.displayName().strip(), "fr-MA"),
                ipAddress,
                userAgent);
    }

    @Transactional
    public PortalSession login(LoginCommand command, String ipAddress, String userAgent) {
        UUID tenantId =
                tenants.findActiveBySlug(command.tenantSlug())
                        .orElseThrow(PortalAuthenticationException::new)
                        .id();
        String email = normalizeEmail(command.email());
        AccountRow account =
                jdbc
                        .query(
                                """
                                SELECT id,email,display_name,locale,password_hash,status
                                FROM admission_portal_account
                                WHERE tenant_id=? AND normalized_email=?
                                """,
                                (result, row) ->
                                        new AccountRow(
                                                result.getObject("id", UUID.class),
                                                result.getString("email"),
                                                result.getString("display_name"),
                                                result.getString("locale"),
                                                result.getString("password_hash"),
                                                result.getString("status")),
                                tenantId,
                                email)
                        .stream()
                        .findFirst()
                        .orElseThrow(PortalAuthenticationException::new);
        if (!"ACTIVE".equals(account.status())
                || !passwordEncoder.matches(command.password(), account.passwordHash())) {
            throw new PortalAuthenticationException();
        }
        return createSession(
                new PortalPrincipal(
                        tenantId,
                        account.id(),
                        account.email(),
                        account.displayName(),
                        account.locale()),
                ipAddress,
                userAgent);
    }

    @Transactional
    public PortalPrincipal require(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new PortalAuthenticationException();
        }
        Instant now = clock.instant();
        PortalPrincipal principal =
                jdbc
                        .query(
                                """
                                SELECT a.tenant_id,a.id,a.email,a.display_name,a.locale
                                FROM admission_portal_session s
                                JOIN admission_portal_account a
                                  ON a.id=s.account_id AND a.tenant_id=s.tenant_id
                                WHERE s.token_hash=? AND s.revoked_at IS NULL AND s.expires_at>?
                                  AND a.status='ACTIVE'
                                """,
                                (result, row) ->
                                        new PortalPrincipal(
                                                result.getObject("tenant_id", UUID.class),
                                                result.getObject("id", UUID.class),
                                                result.getString("email"),
                                                result.getString("display_name"),
                                                result.getString("locale")),
                                tokens.hash(rawToken),
                                timestamp(now))
                        .stream()
                        .findFirst()
                        .orElseThrow(PortalAuthenticationException::new);
        jdbc.update(
                "UPDATE admission_portal_session SET last_used_at=? WHERE token_hash=?",
                timestamp(now),
                tokens.hash(rawToken));
        return principal;
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken != null && !rawToken.isBlank()) {
            jdbc.update(
                    "UPDATE admission_portal_session SET revoked_at=? WHERE token_hash=? AND"
                            + " revoked_at IS NULL",
                    timestamp(clock.instant()),
                    tokens.hash(rawToken));
        }
    }

    public Duration sessionTtl() {
        return sessionTtl;
    }

    private PortalSession createSession(
            PortalPrincipal principal, String ipAddress, String userAgent) {
        String rawToken = tokens.generate();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(sessionTtl);
        jdbc.update(
                """
                INSERT INTO admission_portal_session
                    (id,tenant_id,account_id,token_hash,expires_at,created_at,ip_address,user_agent)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                principal.tenantId(),
                principal.accountId(),
                tokens.hash(rawToken),
                timestamp(expiresAt),
                timestamp(now),
                ipAddress,
                truncate(userAgent, 500));
        return new PortalSession(rawToken, expiresAt, principal);
    }

    private static void validateAccount(String displayName, String email, String password) {
        if (displayName == null || displayName.isBlank() || displayName.strip().length() > 180) {
            throw new AdmissionValidationException("Le nom du parent est obligatoire");
        }
        if (email.length() > 254 || !EMAIL.matcher(email).matches()) {
            throw new AdmissionValidationException("Adresse e-mail invalide");
        }
        if (password == null
                || password.length() < 12
                || !password.matches(".*[A-Z].*")
                || !password.matches(".*[a-z].*")
                || !password.matches(".*[0-9].*")) {
            throw new AdmissionValidationException(
                    "Le mot de passe doit contenir 12 caractères, une majuscule, une minuscule et"
                            + " un chiffre");
        }
    }

    private static String normalizeEmail(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String truncate(String value, int length) {
        if (value == null) return null;
        return value.length() <= length ? value : value.substring(0, length);
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private record AccountRow(
            UUID id,
            String email,
            String displayName,
            String locale,
            String passwordHash,
            String status) {}

    public record RegisterCommand(
            String tenantSlug, String email, String password, String displayName, String phone) {}

    public record LoginCommand(String tenantSlug, String email, String password) {}

    public record PortalPrincipal(
            UUID tenantId, UUID accountId, String email, String displayName, String locale) {}

    public record PortalSession(String rawToken, Instant expiresAt, PortalPrincipal principal) {}
}
