package ma.schoolz.erp.organization.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "school_campus")
public class Campus {

    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 80)
    private String code;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(name = "address_line1", length = 220)
    private String addressLine1;

    @Column(name = "address_line2", length = 220)
    private String addressLine2;

    @Column(length = 120)
    private String city;

    @Column(length = 120)
    private String region;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(length = 40)
    private String phone;

    @Column(length = 254)
    private String email;

    @Column(nullable = false, length = 10)
    private String locale;

    @Column(nullable = false, length = 80)
    private String timezone;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version private long version;

    protected Campus() {}

    private Campus(UUID tenantId, UUID organizationId, Details details, Instant now) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.organizationId = organizationId;
        this.createdAt = now;
        this.active = true;
        update(details, now);
    }

    public static Campus create(UUID tenantId, UUID organizationId, Details details, Instant now) {
        return new Campus(tenantId, organizationId, details, now);
    }

    public void update(Details value, Instant now) {
        this.code = OrganizationCode.normalize(value.code());
        this.name = clean(value.name());
        this.addressLine1 = nullable(value.addressLine1());
        this.addressLine2 = nullable(value.addressLine2());
        this.city = nullable(value.city());
        this.region = nullable(value.region());
        this.postalCode = nullable(value.postalCode());
        this.countryCode = clean(value.countryCode()).toUpperCase(java.util.Locale.ROOT);
        this.phone = nullable(value.phone());
        this.email = nullable(value.email());
        this.locale = value.locale();
        this.timezone = value.timezone();
        this.updatedAt = now;
    }

    public void setActive(boolean active, Instant now) {
        this.active = active;
        this.updatedAt = now;
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private static String nullable(String value) {
        String v = clean(value);
        return v.isEmpty() ? null : v;
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

    public String addressLine1() {
        return addressLine1;
    }

    public String addressLine2() {
        return addressLine2;
    }

    public String city() {
        return city;
    }

    public String region() {
        return region;
    }

    public String postalCode() {
        return postalCode;
    }

    public String countryCode() {
        return countryCode;
    }

    public String phone() {
        return phone;
    }

    public String email() {
        return email;
    }

    public String locale() {
        return locale;
    }

    public String timezone() {
        return timezone;
    }

    public boolean active() {
        return active;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public record Details(
            String code,
            String name,
            String addressLine1,
            String addressLine2,
            String city,
            String region,
            String postalCode,
            String countryCode,
            String phone,
            String email,
            String locale,
            String timezone) {}
}
