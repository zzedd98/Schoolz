package ma.schoolz.erp.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import ma.schoolz.erp.foundation.security.IdentitySecurityProperties;
import ma.schoolz.erp.foundation.security.JwtService;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.foundation.security.SecureTokenService;
import ma.schoolz.erp.identity.domain.IdentityTenant;
import ma.schoolz.erp.identity.domain.OneTimeToken;
import ma.schoolz.erp.identity.domain.RefreshSession;
import ma.schoolz.erp.identity.domain.TokenPurpose;
import ma.schoolz.erp.identity.domain.UserAccount;
import ma.schoolz.erp.identity.persistence.IdentityTenantRepository;
import ma.schoolz.erp.identity.persistence.OneTimeTokenRepository;
import ma.schoolz.erp.identity.persistence.RefreshSessionRepository;
import ma.schoolz.erp.identity.persistence.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService {

    private static final String DUMMY_BCRYPT_HASH =
            "$2a$12$7EqJtq98hPqEX7fNZaFWoO5JjK7P3iWwM5LQe9l5p2JpYzZy4vM8e";

    private final IdentityTenantRepository tenants;
    private final UserAccountRepository accounts;
    private final RefreshSessionRepository sessions;
    private final OneTimeTokenRepository oneTimeTokens;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final SecureTokenService tokenService;
    private final JwtService jwtService;
    private final IdentitySecurityProperties properties;
    private final AuditLogService audit;
    private final Clock clock;

    public AuthenticationService(
            IdentityTenantRepository tenants,
            UserAccountRepository accounts,
            RefreshSessionRepository sessions,
            OneTimeTokenRepository oneTimeTokens,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy,
            SecureTokenService tokenService,
            JwtService jwtService,
            IdentitySecurityProperties properties,
            AuditLogService audit,
            Clock clock) {
        this.tenants = tenants;
        this.accounts = accounts;
        this.sessions = sessions;
        this.oneTimeTokens = oneTimeTokens;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.tokenService = tokenService;
        this.jwtService = jwtService;
        this.properties = properties;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public AuthenticationResult login(
            String tenantSlug, String email, String password, ClientContext clientContext) {
        Instant now = clock.instant();
        Optional<IdentityTenant> tenant =
                tenants.findBySlugAndActiveTrue(IdentityTenant.normalizeSlug(tenantSlug));
        Optional<UserAccount> account =
                tenant.flatMap(
                        value ->
                                accounts.findByTenantIdAndNormalizedEmail(
                                        value.id(), UserAccount.normalizeEmail(email)));

        if (account.isEmpty()) {
            passwordEncoder.matches(password == null ? "" : password, DUMMY_BCRYPT_HASH);
            throw new AuthenticationFailedException();
        }

        UserAccount user = account.orElseThrow();
        if (user.locked(now)
                || !user.canAuthenticate(now)
                || !passwordEncoder.matches(password, user.passwordHash())) {
            if (!user.locked(now)) {
                user.recordLoginFailure(now);
                accounts.save(user);
            }
            audit.record(
                    user.tenantId(),
                    user.id(),
                    "authentication.login_failed",
                    "UserAccount",
                    user.id().toString(),
                    clientContext.ipAddress(),
                    Map.of("reason", user.locked(now) ? "locked" : "invalid_credentials"),
                    null,
                    null);
            throw new AuthenticationFailedException();
        }

        user.recordLoginSuccess(now);
        accounts.save(user);
        AuthenticationResult result = createSession(user, UUID.randomUUID(), clientContext, now);
        audit.record(
                user.tenantId(),
                user.id(),
                "authentication.login_succeeded",
                "RefreshSession",
                result.sessionId().toString(),
                clientContext.ipAddress(),
                Map.of(),
                null,
                null);
        return result;
    }

    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public AuthenticationResult refresh(String rawRefreshToken, ClientContext clientContext) {
        Instant now = clock.instant();
        RefreshSession current =
                sessions.findByTokenHash(tokenService.hash(rawRefreshToken))
                        .orElseThrow(AuthenticationFailedException::new);
        if (!current.usable(now)) {
            sessions.revokeFamily(current.familyId(), now);
            throw new AuthenticationFailedException();
        }
        UserAccount account =
                accounts.findByIdAndTenantId(current.userId(), current.tenantId())
                        .filter(user -> user.canAuthenticate(now))
                        .orElseThrow(AuthenticationFailedException::new);

        AuthenticationResult next = createSession(account, current.familyId(), clientContext, now);
        current.rotateTo(next.sessionId(), now);
        sessions.save(current);
        audit.record(
                account.tenantId(),
                account.id(),
                "authentication.session_rotated",
                "RefreshSession",
                current.id().toString(),
                clientContext.ipAddress(),
                Map.of("rotatedTo", next.sessionId()),
                null,
                null);
        return next;
    }

    @Transactional
    public void logout(String rawRefreshToken, ClientContext clientContext) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        sessions.findByTokenHash(tokenService.hash(rawRefreshToken))
                .ifPresent(
                        session -> {
                            session.revoke(clock.instant());
                            sessions.save(session);
                            audit.record(
                                    session.tenantId(),
                                    session.userId(),
                                    "authentication.logout",
                                    "RefreshSession",
                                    session.id().toString(),
                                    clientContext.ipAddress(),
                                    Map.of(),
                                    null,
                                    null);
                        });
    }

    @Transactional
    public Optional<String> requestPasswordReset(String tenantSlug, String email) {
        return createOneTimeToken(
                tenantSlug, email, TokenPurpose.PASSWORD_RESET, properties.passwordResetTtl());
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        Instant now = clock.instant();
        OneTimeToken token =
                oneTimeTokens
                        .findByTokenHashAndPurpose(
                                tokenService.hash(rawToken), TokenPurpose.PASSWORD_RESET)
                        .filter(candidate -> candidate.usable(now))
                        .orElseThrow(AuthenticationFailedException::new);
        UserAccount account =
                accounts.findByIdAndTenantId(token.userId(), token.tenantId())
                        .orElseThrow(AuthenticationFailedException::new);
        passwordPolicy.validate(newPassword, account.email());
        account.changePassword(passwordEncoder.encode(newPassword), now);
        token.consume(now);
        sessions.revokeAllForUser(account.tenantId(), account.id(), now);
        accounts.save(account);
        oneTimeTokens.save(token);
        audit.record(
                account.tenantId(),
                account.id(),
                "authentication.password_reset",
                "UserAccount",
                account.id().toString(),
                null,
                Map.of(),
                null,
                Map.of("passwordChanged", true));
    }

    @Transactional
    public void activateAccount(String rawToken, String newPassword) {
        Instant now = clock.instant();
        OneTimeToken token =
                oneTimeTokens
                        .findByTokenHashAndPurpose(
                                tokenService.hash(rawToken), TokenPurpose.ACCOUNT_ACTIVATION)
                        .filter(candidate -> candidate.usable(now))
                        .orElseThrow(AuthenticationFailedException::new);
        UserAccount account =
                accounts.findByIdAndTenantId(token.userId(), token.tenantId())
                        .filter(
                                user ->
                                        user.status()
                                                == ma.schoolz.erp.identity.domain.AccountStatus
                                                        .PENDING_VERIFICATION)
                        .orElseThrow(AuthenticationFailedException::new);
        passwordPolicy.validate(newPassword, account.email());
        account.changePassword(passwordEncoder.encode(newPassword), now);
        account.verifyEmail(now);
        token.consume(now);
        accounts.save(account);
        oneTimeTokens.save(token);
        audit.record(
                account.tenantId(),
                account.id(),
                "authentication.account_activated",
                "UserAccount",
                account.id().toString(),
                null,
                Map.of(),
                null,
                Map.of("activated", true));
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        Instant now = clock.instant();
        OneTimeToken token =
                oneTimeTokens
                        .findByTokenHashAndPurpose(
                                tokenService.hash(rawToken), TokenPurpose.EMAIL_VERIFICATION)
                        .filter(candidate -> candidate.usable(now))
                        .orElseThrow(AuthenticationFailedException::new);
        UserAccount account =
                accounts.findByIdAndTenantId(token.userId(), token.tenantId())
                        .orElseThrow(AuthenticationFailedException::new);
        account.verifyEmail(now);
        token.consume(now);
        accounts.save(account);
        oneTimeTokens.save(token);
        audit.record(
                account.tenantId(),
                account.id(),
                "authentication.email_verified",
                "UserAccount",
                account.id().toString(),
                null,
                Map.of(),
                null,
                Map.of("verified", true));
    }

    @Transactional
    public String createEmailVerificationToken(UserAccount account) {
        String rawToken = tokenService.generate();
        oneTimeTokens.save(
                OneTimeToken.create(
                        account.tenantId(),
                        account.id(),
                        TokenPurpose.EMAIL_VERIFICATION,
                        tokenService.hash(rawToken),
                        clock.instant().plus(properties.emailVerificationTtl()),
                        clock.instant()));
        return rawToken;
    }

    @Transactional
    public String createAccountActivationToken(UserAccount account) {
        String rawToken = tokenService.generate();
        oneTimeTokens.save(
                OneTimeToken.create(
                        account.tenantId(),
                        account.id(),
                        TokenPurpose.ACCOUNT_ACTIVATION,
                        tokenService.hash(rawToken),
                        clock.instant().plus(properties.emailVerificationTtl()),
                        clock.instant()));
        return rawToken;
    }

    private Optional<String> createOneTimeToken(
            String tenantSlug, String email, TokenPurpose purpose, java.time.Duration timeToLive) {
        return tenants.findBySlugAndActiveTrue(IdentityTenant.normalizeSlug(tenantSlug))
                .flatMap(
                        tenant ->
                                accounts.findByTenantIdAndNormalizedEmail(
                                        tenant.id(), UserAccount.normalizeEmail(email)))
                .map(
                        account -> {
                            String rawToken = tokenService.generate();
                            oneTimeTokens.save(
                                    OneTimeToken.create(
                                            account.tenantId(),
                                            account.id(),
                                            purpose,
                                            tokenService.hash(rawToken),
                                            clock.instant().plus(timeToLive),
                                            clock.instant()));
                            audit.record(
                                    account.tenantId(),
                                    account.id(),
                                    "authentication.token_requested",
                                    "UserAccount",
                                    account.id().toString(),
                                    null,
                                    Map.of("purpose", purpose.name()),
                                    null,
                                    null);
                            return rawToken;
                        });
    }

    private AuthenticationResult createSession(
            UserAccount account, UUID familyId, ClientContext clientContext, Instant now) {
        String rawRefreshToken = tokenService.generate();
        RefreshSession session =
                RefreshSession.create(
                        account.tenantId(),
                        account.id(),
                        familyId,
                        tokenService.hash(rawRefreshToken),
                        now.plus(properties.refreshTokenTtl()),
                        now,
                        clientContext.ipAddress(),
                        clientContext.userAgent());
        sessions.save(session);
        SchoolPrincipal principal = principal(account);
        JwtService.IssuedAccessToken accessToken = jwtService.issue(principal);
        return new AuthenticationResult(
                session.id(),
                accessToken.value(),
                accessToken.expiresAt(),
                rawRefreshToken,
                session.expiresAt(),
                principal);
    }

    private SchoolPrincipal principal(UserAccount account) {
        return new SchoolPrincipal(
                account.id(),
                account.tenantId(),
                account.email(),
                account.displayName(),
                account.locale(),
                account.roleCodes(),
                account.permissions());
    }

    public record ClientContext(String ipAddress, String userAgent) {}

    public record AuthenticationResult(
            UUID sessionId,
            String accessToken,
            Instant accessExpiresAt,
            String refreshToken,
            Instant refreshExpiresAt,
            SchoolPrincipal principal) {}
}
