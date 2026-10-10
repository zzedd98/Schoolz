package ma.schoolz.erp.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "identity_refresh_session")
public class RefreshSession {

    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "rotated_to")
    private UUID rotatedTo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    protected RefreshSession() {}

    private RefreshSession(
            UUID id,
            UUID tenantId,
            UUID userId,
            UUID familyId,
            String tokenHash,
            Instant expiresAt,
            Instant createdAt,
            String ipAddress,
            String userAgent) {
        this.id = id;
        this.tenantId = tenantId;
        this.userId = userId;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }

    public static RefreshSession create(
            UUID tenantId,
            UUID userId,
            UUID familyId,
            String tokenHash,
            Instant expiresAt,
            Instant now,
            String ipAddress,
            String userAgent) {
        return new RefreshSession(
                UUID.randomUUID(),
                tenantId,
                userId,
                familyId,
                tokenHash,
                expiresAt,
                now,
                ipAddress,
                userAgent);
    }

    public void rotateTo(UUID nextSessionId, Instant now) {
        revokedAt = now;
        rotatedTo = nextSessionId;
        lastUsedAt = now;
    }

    public void revoke(Instant now) {
        revokedAt = now;
        lastUsedAt = now;
    }

    public boolean usable(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID userId() {
        return userId;
    }

    public UUID familyId() {
        return familyId;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }
}
