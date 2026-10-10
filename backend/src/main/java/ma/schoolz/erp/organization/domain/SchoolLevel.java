package ma.schoolz.erp.organization.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "school_level")
public class SchoolLevel {
    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 60)
    private String code;

    @Column(nullable = false, length = 140)
    private String name;

    @Column(name = "sequence_number", nullable = false)
    private int sequence;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version private long version;

    protected SchoolLevel() {}

    private SchoolLevel(
            UUID tenantId,
            UUID organizationId,
            String code,
            String name,
            int sequence,
            Instant now) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.organizationId = organizationId;
        this.code = OrganizationCode.normalize(code);
        this.name = name.strip();
        this.sequence = sequence;
        this.active = true;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static SchoolLevel create(
            UUID tenantId,
            UUID organizationId,
            String code,
            String name,
            int sequence,
            Instant now) {
        return new SchoolLevel(tenantId, organizationId, code, name, sequence, now);
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID organizationId() {
        return organizationId;
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public int sequence() {
        return sequence;
    }

    public boolean active() {
        return active;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
