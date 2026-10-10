package ma.schoolz.erp.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "identity_audit_log")
public class AuditLog {

    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(nullable = false, length = 120)
    private String action;

    @Column(name = "resource_type", nullable = false, length = 120)
    private String resourceType;

    @Column(name = "resource_id", length = 120)
    private String resourceId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "before_state", columnDefinition = "TEXT")
    private String beforeState;

    @Column(name = "after_state", columnDefinition = "TEXT")
    private String afterState;

    protected AuditLog() {}

    public static AuditLog create(
            UUID tenantId,
            UUID actorUserId,
            String action,
            String resourceType,
            String resourceId,
            Instant occurredAt,
            String ipAddress,
            String metadata,
            String beforeState,
            String afterState) {
        AuditLog log = new AuditLog();
        log.id = UUID.randomUUID();
        log.tenantId = tenantId;
        log.actorUserId = actorUserId;
        log.action = action;
        log.resourceType = resourceType;
        log.resourceId = resourceId;
        log.occurredAt = occurredAt;
        log.ipAddress = ipAddress;
        log.metadata = metadata;
        log.beforeState = beforeState;
        log.afterState = afterState;
        return log;
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID actorUserId() {
        return actorUserId;
    }

    public String action() {
        return action;
    }

    public String resourceType() {
        return resourceType;
    }

    public String resourceId() {
        return resourceId;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    public String ipAddress() {
        return ipAddress;
    }

    public String metadata() {
        return metadata;
    }
}
