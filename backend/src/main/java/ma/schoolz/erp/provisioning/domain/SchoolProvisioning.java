package ma.schoolz.erp.provisioning.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "platform_school_provisioning")
public class SchoolProvisioning {

    @Id private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 120)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "campus_id", nullable = false)
    private UUID campusId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SchoolProvisioning() {}

    private SchoolProvisioning(
            String idempotencyKey,
            String requestFingerprint,
            UUID tenantId,
            UUID ownerUserId,
            UUID organizationId,
            UUID campusId,
            Instant createdAt) {
        this.id = UUID.randomUUID();
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.tenantId = tenantId;
        this.ownerUserId = ownerUserId;
        this.organizationId = organizationId;
        this.campusId = campusId;
        this.createdAt = createdAt;
    }

    public static SchoolProvisioning create(
            String idempotencyKey,
            String requestFingerprint,
            UUID tenantId,
            UUID ownerUserId,
            UUID organizationId,
            UUID campusId,
            Instant createdAt) {
        return new SchoolProvisioning(
                idempotencyKey,
                requestFingerprint,
                tenantId,
                ownerUserId,
                organizationId,
                campusId,
                createdAt);
    }

    public String requestFingerprint() {
        return requestFingerprint;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID ownerUserId() {
        return ownerUserId;
    }

    public UUID organizationId() {
        return organizationId;
    }

    public UUID campusId() {
        return campusId;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
