package ma.schoolz.erp.identity.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "identity_role")
public class Role {

    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 80)
    private String code;

    @Column(nullable = false, length = 140)
    private String name;

    @Column(name = "system_role", nullable = false)
    private boolean systemRole;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "identity_role_permission", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission_code", nullable = false, length = 120)
    private Set<String> permissions = new LinkedHashSet<>();

    protected Role() {}

    private Role(
            UUID tenantId,
            String code,
            String name,
            boolean systemRole,
            Set<String> permissions,
            Instant createdAt) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.code = normalizeCode(code);
        this.name = name.strip();
        this.systemRole = systemRole;
        this.createdAt = createdAt;
        replacePermissions(permissions);
    }

    public static Role create(
            UUID tenantId,
            String code,
            String name,
            boolean systemRole,
            Set<String> permissions,
            Instant createdAt) {
        return new Role(tenantId, code, name, systemRole, permissions, createdAt);
    }

    public void update(String name, Set<String> permissions) {
        this.name = name.strip();
        replacePermissions(permissions);
    }

    private void replacePermissions(Set<String> permissions) {
        if (!permissions.stream().allMatch(PermissionCode::isKnown)) {
            throw new IllegalArgumentException("Unknown permission code");
        }
        this.permissions.clear();
        this.permissions.addAll(permissions);
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public boolean systemRole() {
        return systemRole;
    }

    public Set<String> permissions() {
        return Set.copyOf(permissions);
    }

    public static String normalizeCode(String code) {
        return code == null
                ? ""
                : code.strip().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_");
    }
}
