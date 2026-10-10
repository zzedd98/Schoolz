package ma.schoolz.erp.organization.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "school_organization")
public class Organization {

    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;

    @Column(nullable = false, length = 80)
    private String code;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(name = "legal_name", length = 220)
    private String legalName;

    @Column(name = "logo_object_key", length = 500)
    private String logoObjectKey;

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

    @Column(name = "business_identifiers", nullable = false, columnDefinition = "TEXT")
    private String businessIdentifiers;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version private long version;

    protected Organization() {}

    private Organization(UUID tenantId, Details details, String identifiers, Instant now) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.createdAt = now;
        update(details, identifiers, now);
    }

    public static Organization create(
            UUID tenantId, Details details, String identifiers, Instant now) {
        return new Organization(tenantId, details, identifiers, now);
    }

    public void update(Details details, String identifiers, Instant now) {
        this.code = OrganizationCode.normalize(details.code());
        this.name = clean(details.name());
        this.legalName = nullable(details.legalName());
        this.logoObjectKey = nullable(details.logoObjectKey());
        this.addressLine1 = nullable(details.addressLine1());
        this.addressLine2 = nullable(details.addressLine2());
        this.city = nullable(details.city());
        this.region = nullable(details.region());
        this.postalCode = nullable(details.postalCode());
        this.countryCode = clean(details.countryCode()).toUpperCase(java.util.Locale.ROOT);
        this.phone = nullable(details.phone());
        this.email = nullable(details.email());
        this.businessIdentifiers = identifiers;
        this.updatedAt = now;
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private static String nullable(String value) {
        String cleaned = clean(value);
        return cleaned.isEmpty() ? null : cleaned;
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

    public String legalName() {
        return legalName;
    }

    public String logoObjectKey() {
        return logoObjectKey;
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

    public String businessIdentifiers() {
        return businessIdentifiers;
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
            String legalName,
            String logoObjectKey,
            String addressLine1,
            String addressLine2,
            String city,
            String region,
            String postalCode,
            String countryCode,
            String phone,
            String email) {}
}
