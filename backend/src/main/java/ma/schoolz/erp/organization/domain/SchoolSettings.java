package ma.schoolz.erp.organization.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "school_settings")
public class SchoolSettings {

    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;

    @Column(name = "organization_id", nullable = false, unique = true)
    private UUID organizationId;

    @Column(name = "default_locale", nullable = false, length = 10)
    private String defaultLocale;

    @Column(name = "supported_locales", nullable = false, length = 80)
    private String supportedLocales;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(nullable = false, length = 80)
    private String timezone;

    @Column(name = "week_starts_on", nullable = false)
    private int weekStartsOn;

    @Column(name = "working_days", nullable = false, length = 100)
    private String workingDays;

    @Column(name = "day_starts_at", nullable = false)
    private LocalTime dayStartsAt;

    @Column(name = "day_ends_at", nullable = false)
    private LocalTime dayEndsAt;

    @Column(name = "calendar_notes", length = 500)
    private String calendarNotes;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version private long version;

    protected SchoolSettings() {}

    private SchoolSettings(UUID tenantId, UUID organizationId, Values values, Instant now) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.organizationId = organizationId;
        update(values, now);
    }

    public static SchoolSettings create(
            UUID tenantId, UUID organizationId, Values values, Instant now) {
        return new SchoolSettings(tenantId, organizationId, values, now);
    }

    public void update(Values values, Instant now) {
        this.defaultLocale = values.defaultLocale();
        this.supportedLocales = values.supportedLocales();
        this.currencyCode = values.currencyCode();
        this.timezone = values.timezone();
        this.weekStartsOn = values.weekStartsOn();
        this.workingDays = values.workingDays();
        this.dayStartsAt = values.dayStartsAt();
        this.dayEndsAt = values.dayEndsAt();
        this.calendarNotes = values.calendarNotes();
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

    public String defaultLocale() {
        return defaultLocale;
    }

    public String supportedLocales() {
        return supportedLocales;
    }

    public String currencyCode() {
        return currencyCode;
    }

    public String timezone() {
        return timezone;
    }

    public int weekStartsOn() {
        return weekStartsOn;
    }

    public String workingDays() {
        return workingDays;
    }

    public LocalTime dayStartsAt() {
        return dayStartsAt;
    }

    public LocalTime dayEndsAt() {
        return dayEndsAt;
    }

    public String calendarNotes() {
        return calendarNotes;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public record Values(
            String defaultLocale,
            String supportedLocales,
            String currencyCode,
            String timezone,
            int weekStartsOn,
            String workingDays,
            LocalTime dayStartsAt,
            LocalTime dayEndsAt,
            String calendarNotes) {}
}
