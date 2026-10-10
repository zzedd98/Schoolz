package ma.schoolz.erp.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "identity_user_account")
public class UserAccount {

    private static final int LOCK_THRESHOLD = 5;

    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "normalized_email", nullable = false, length = 254)
    private String normalizedEmail;

    @Column(name = "display_name", nullable = false, length = 180)
    private String displayName;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 10)
    private String locale;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AccountStatus status;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "mfa_secret_ciphertext", length = 700)
    private String mfaSecretCiphertext;

    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "identity_user_role",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    protected UserAccount() {}

    private UserAccount(
            UUID tenantId,
            String email,
            String displayName,
            String passwordHash,
            String locale,
            boolean emailVerified,
            Instant now) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.email = email.strip();
        this.normalizedEmail = normalizeEmail(email);
        this.displayName = displayName.strip();
        this.passwordHash = passwordHash;
        this.locale = locale;
        this.status = emailVerified ? AccountStatus.ACTIVE : AccountStatus.PENDING_VERIFICATION;
        this.emailVerifiedAt = emailVerified ? now : null;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static UserAccount create(
            UUID tenantId,
            String email,
            String displayName,
            String passwordHash,
            String locale,
            boolean emailVerified,
            Instant now) {
        return new UserAccount(
                tenantId, email, displayName, passwordHash, locale, emailVerified, now);
    }

    public void assignRoles(Set<Role> roles, Instant now) {
        if (roles.stream().anyMatch(role -> !tenantId.equals(role.tenantId()))) {
            throw new IllegalArgumentException("Roles must belong to the same tenant");
        }
        this.roles.clear();
        this.roles.addAll(roles);
        this.updatedAt = now;
    }

    public void recordLoginSuccess(Instant now) {
        failedLoginAttempts = 0;
        lockedUntil = null;
        updatedAt = now;
    }

    public void recordLoginFailure(Instant now) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= LOCK_THRESHOLD) {
            int exponent = Math.min(failedLoginAttempts - LOCK_THRESHOLD, 6);
            long minutes = Math.min(15L << exponent, Duration.ofHours(24).toMinutes());
            lockedUntil = now.plus(Duration.ofMinutes(minutes));
        }
        updatedAt = now;
    }

    public void verifyEmail(Instant now) {
        emailVerifiedAt = now;
        if (status == AccountStatus.PENDING_VERIFICATION) {
            status = AccountStatus.ACTIVE;
        }
        updatedAt = now;
    }

    public void changePassword(String passwordHash, Instant now) {
        this.passwordHash = passwordHash;
        failedLoginAttempts = 0;
        lockedUntil = null;
        updatedAt = now;
    }

    public void changeStatus(AccountStatus status, Instant now) {
        this.status = status;
        updatedAt = now;
    }

    public boolean canAuthenticate(Instant now) {
        return status == AccountStatus.ACTIVE
                && emailVerifiedAt != null
                && (lockedUntil == null || !lockedUntil.isAfter(now));
    }

    public boolean locked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public Set<String> permissions() {
        return roles.stream()
                .flatMap(role -> role.permissions().stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    public Set<String> roleCodes() {
        return roles.stream().map(Role::code).collect(Collectors.toUnmodifiableSet());
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public String email() {
        return email;
    }

    public String normalizedEmail() {
        return normalizedEmail;
    }

    public String displayName() {
        return displayName;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String locale() {
        return locale;
    }

    public AccountStatus status() {
        return status;
    }

    public Instant emailVerifiedAt() {
        return emailVerifiedAt;
    }

    public int failedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant lockedUntil() {
        return lockedUntil;
    }

    public boolean mfaEnabled() {
        return mfaEnabled;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }
}
