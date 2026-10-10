package ma.schoolz.erp.organization.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "academic_year")
public class AcademicYear {
    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 140)
    private String name;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on", nullable = false)
    private LocalDate endsOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AcademicYearStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version private long version;

    protected AcademicYear() {}

    private AcademicYear(
            UUID tenantId,
            UUID organizationId,
            String code,
            String name,
            LocalDate startsOn,
            LocalDate endsOn,
            Instant now) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.organizationId = organizationId;
        this.code = OrganizationCode.normalize(code);
        this.name = name.strip();
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.status = AcademicYearStatus.DRAFT;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static AcademicYear create(
            UUID tenantId,
            UUID organizationId,
            String code,
            String name,
            LocalDate startsOn,
            LocalDate endsOn,
            Instant now) {
        return new AcademicYear(tenantId, organizationId, code, name, startsOn, endsOn, now);
    }

    public void changeStatus(AcademicYearStatus status, Instant now) {
        this.status = status;
        this.updatedAt = now;
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

    public LocalDate startsOn() {
        return startsOn;
    }

    public LocalDate endsOn() {
        return endsOn;
    }

    public AcademicYearStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
