package ma.schoolz.erp.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "identity_tenant")
public class IdentityTenant {

    @Id private UUID id;

    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @Column(name = "display_name", nullable = false, length = 180)
    private String displayName;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdentityTenant() {}

    private IdentityTenant(UUID id, String slug, String displayName, Instant createdAt) {
        this.id = id;
        this.slug = normalizeSlug(slug);
        this.displayName = displayName.strip();
        this.active = true;
        this.createdAt = createdAt;
    }

    public static IdentityTenant create(String slug, String displayName, Instant createdAt) {
        return new IdentityTenant(UUID.randomUUID(), slug, displayName, createdAt);
    }

    public UUID id() {
        return id;
    }

    public String slug() {
        return slug;
    }

    public String displayName() {
        return displayName;
    }

    public boolean active() {
        return active;
    }

    public static String normalizeSlug(String slug) {
        return slug == null ? "" : slug.strip().toLowerCase(Locale.ROOT);
    }
}
