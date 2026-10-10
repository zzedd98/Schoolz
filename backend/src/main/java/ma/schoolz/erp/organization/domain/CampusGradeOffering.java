package ma.schoolz.erp.organization.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "campus_grade_offering")
public class CampusGradeOffering {
    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "campus_id", nullable = false)
    private UUID campusId;

    @Column(name = "grade_level_id", nullable = false)
    private UUID gradeLevelId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CampusGradeOffering() {}

    private CampusGradeOffering(UUID tenantId, UUID campusId, UUID gradeLevelId, Instant now) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.campusId = campusId;
        this.gradeLevelId = gradeLevelId;
        this.createdAt = now;
    }

    public static CampusGradeOffering create(
            UUID tenantId, UUID campusId, UUID gradeLevelId, Instant now) {
        return new CampusGradeOffering(tenantId, campusId, gradeLevelId, now);
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID campusId() {
        return campusId;
    }

    public UUID gradeLevelId() {
        return gradeLevelId;
    }
}
