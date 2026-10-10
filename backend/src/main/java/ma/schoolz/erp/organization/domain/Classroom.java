package ma.schoolz.erp.organization.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "classroom")
public class Classroom {
    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "campus_id", nullable = false)
    private UUID campusId;

    @Column(name = "academic_year_id", nullable = false)
    private UUID academicYearId;

    @Column(name = "grade_level_id", nullable = false)
    private UUID gradeLevelId;

    @Column(name = "physical_room_id")
    private UUID physicalRoomId;

    @Column(nullable = false, length = 60)
    private String code;

    @Column(nullable = false, length = 140)
    private String name;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version private long version;

    protected Classroom() {}

    private Classroom(
            UUID tenantId,
            UUID campusId,
            UUID yearId,
            UUID gradeId,
            UUID roomId,
            String code,
            String name,
            int capacity,
            Instant now) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.campusId = campusId;
        this.academicYearId = yearId;
        this.gradeLevelId = gradeId;
        this.physicalRoomId = roomId;
        this.code = OrganizationCode.normalize(code);
        this.name = name.strip();
        this.capacity = capacity;
        this.active = true;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Classroom create(
            UUID tenantId,
            UUID campusId,
            UUID yearId,
            UUID gradeId,
            UUID roomId,
            String code,
            String name,
            int capacity,
            Instant now) {
        return new Classroom(
                tenantId, campusId, yearId, gradeId, roomId, code, name, capacity, now);
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

    public UUID academicYearId() {
        return academicYearId;
    }

    public UUID gradeLevelId() {
        return gradeLevelId;
    }

    public UUID physicalRoomId() {
        return physicalRoomId;
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public int capacity() {
        return capacity;
    }

    public boolean active() {
        return active;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
