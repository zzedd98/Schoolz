package ma.schoolz.erp.organization.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "academic_term")
public class AcademicTerm {
    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "academic_year_id", nullable = false)
    private UUID academicYearId;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 140)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "term_type", nullable = false, length = 30)
    private TermType type;

    @Column(name = "sequence_number", nullable = false)
    private int sequence;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on", nullable = false)
    private LocalDate endsOn;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version private long version;

    protected AcademicTerm() {}

    private AcademicTerm(
            UUID tenantId,
            UUID yearId,
            String code,
            String name,
            TermType type,
            int sequence,
            LocalDate startsOn,
            LocalDate endsOn,
            Instant now) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.academicYearId = yearId;
        this.code = OrganizationCode.normalize(code);
        this.name = name.strip();
        this.type = type;
        this.sequence = sequence;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static AcademicTerm create(
            UUID tenantId,
            UUID yearId,
            String code,
            String name,
            TermType type,
            int sequence,
            LocalDate startsOn,
            LocalDate endsOn,
            Instant now) {
        return new AcademicTerm(
                tenantId, yearId, code, name, type, sequence, startsOn, endsOn, now);
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID academicYearId() {
        return academicYearId;
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public TermType type() {
        return type;
    }

    public int sequence() {
        return sequence;
    }

    public LocalDate startsOn() {
        return startsOn;
    }

    public LocalDate endsOn() {
        return endsOn;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
