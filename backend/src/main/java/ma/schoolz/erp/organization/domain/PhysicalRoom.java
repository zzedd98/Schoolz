package ma.schoolz.erp.organization.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "physical_room")
public class PhysicalRoom {
    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "campus_id", nullable = false)
    private UUID campusId;

    @Column(nullable = false, length = 60)
    private String code;

    @Column(nullable = false, length = 140)
    private String name;

    @Column(name = "room_type", nullable = false, length = 40)
    private String roomType;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "floor_label", length = 40)
    private String floorLabel;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version private long version;

    protected PhysicalRoom() {}

    private PhysicalRoom(
            UUID tenantId,
            UUID campusId,
            String code,
            String name,
            String roomType,
            int capacity,
            String floorLabel,
            Instant now) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.campusId = campusId;
        this.code = OrganizationCode.normalize(code);
        this.name = name.strip();
        this.roomType = OrganizationCode.normalize(roomType);
        this.capacity = capacity;
        this.floorLabel = blankToNull(floorLabel);
        this.active = true;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static PhysicalRoom create(
            UUID tenantId,
            UUID campusId,
            String code,
            String name,
            String roomType,
            int capacity,
            String floorLabel,
            Instant now) {
        return new PhysicalRoom(
                tenantId, campusId, code, name, roomType, capacity, floorLabel, now);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.strip();
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

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public String roomType() {
        return roomType;
    }

    public int capacity() {
        return capacity;
    }

    public String floorLabel() {
        return floorLabel;
    }

    public boolean active() {
        return active;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
