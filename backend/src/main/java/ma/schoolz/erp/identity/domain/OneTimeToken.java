package ma.schoolz.erp.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "identity_one_time_token")
public class OneTimeToken {

    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TokenPurpose purpose;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OneTimeToken() {}

    private OneTimeToken(
            UUID tenantId,
            UUID userId,
            TokenPurpose purpose,
            String tokenHash,
            Instant expiresAt,
            Instant createdAt) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.userId = userId;
        this.purpose = purpose;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public static OneTimeToken create(
            UUID tenantId,
            UUID userId,
            TokenPurpose purpose,
            String tokenHash,
            Instant expiresAt,
            Instant now) {
        return new OneTimeToken(tenantId, userId, purpose, tokenHash, expiresAt, now);
    }

    public boolean usable(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }

    public void consume(Instant now) {
        usedAt = now;
    }

    public UUID userId() {
        return userId;
    }

    public UUID tenantId() {
        return tenantId;
    }
}
