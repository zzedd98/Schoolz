package ma.schoolz.erp.analytics.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ma.schoolz.erp.identity.TenantDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@Order(270)
public class DevDirectionAnalyticsSeeder implements ApplicationRunner {
    private static final LocalDate LATEST_DATE = LocalDate.of(2026, 8, 10);
    private final JdbcTemplate jdbc;
    private final TenantDirectory tenants;
    private final Clock clock;
    private final boolean enabled;
    private final String tenantSlug;

    public DevDirectionAnalyticsSeeder(
            JdbcTemplate jdbc,
            TenantDirectory tenants,
            Clock clock,
            @Value("${schoolz.demo.seed-enabled:false}") boolean enabled,
            @Value("${schoolz.demo.tenant-slug:}") String tenantSlug) {
        this.jdbc = jdbc;
        this.tenants = tenants;
        this.clock = clock;
        this.enabled = enabled;
        this.tenantSlug = tenantSlug;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (!enabled || tenantSlug.isBlank()) return;
        UUID tenantId =
                tenants.findActiveBySlug(tenantSlug)
                        .orElseThrow(() -> new IllegalStateException("Demo tenant was not seeded"))
                        .id();
        if (count("SELECT count(*) FROM analytics_snapshot WHERE tenant_id=?", tenantId) > 0) {
            return;
        }
        UUID organizationId =
                maybeId(
                        "SELECT id FROM school_organization WHERE tenant_id=? ORDER BY created_at"
                                + " LIMIT 1",
                        tenantId);
        UUID casablanca =
                maybeId(
                        "SELECT id FROM school_campus WHERE tenant_id=? AND code='CASA-CENTRE'",
                        tenantId);
        if (organizationId == null || casablanca == null) return;
        UUID rabat = campus(tenantId, organizationId, "RABAT-AGDAL", "Campus Rabat Agdal", "Rabat");
        UUID marrakech =
                campus(
                        tenantId,
                        organizationId,
                        "MARRAKECH-GUELIZ",
                        "Campus Marrakech Guéliz",
                        "Marrakech");

        List<CampusSeed> campuses =
                List.of(
                        new CampusSeed(casablanca, 552, 524, 28, 1, 610_000, 510_000, 6, 640),
                        new CampusSeed(rabat, 411, 389, 22, 1, 455_000, 380_000, 5, 480),
                        new CampusSeed(marrakech, 321, 303, 18, 1, 355_000, 290_000, 3, 380));
        for (int month = 0; month < 12; month++) {
            LocalDate date = LocalDate.of(2025, 9, 10).plusMonths(month);
            for (CampusSeed campus : campuses) seedSnapshot(tenantId, campus, date, month);
        }
        seedDimensions(tenantId, LATEST_DATE);
    }

    private UUID campus(UUID tenantId, UUID organizationId, String code, String name, String city) {
        List<UUID> existing =
                jdbc.query(
                        "SELECT id FROM school_campus WHERE tenant_id=? AND code=?",
                        (rs, row) -> rs.getObject(1, UUID.class),
                        tenantId,
                        code);
        if (!existing.isEmpty()) return existing.getFirst();
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                """
                INSERT INTO school_campus
                    (id,tenant_id,organization_id,code,name,city,country_code,locale,timezone,
                     active,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'MA', 'fr-MA', 'Africa/Casablanca', TRUE, ?, ?)
                """,
                id,
                tenantId,
                organizationId,
                code,
                name,
                city,
                timestamp(now),
                timestamp(now));
        return id;
    }

    private void seedSnapshot(UUID tenantId, CampusSeed campus, LocalDate date, int month) {
        boolean latest = month == 11;
        BigDecimal progress =
                BigDecimal.valueOf(month).divide(BigDecimal.valueOf(11), 8, RoundingMode.HALF_UP);
        BigDecimal studentFactor =
                BigDecimal.valueOf(0.84).add(progress.multiply(BigDecimal.valueOf(0.16)));
        long total = scale(campus.totalStudents(), studentFactor);
        long absent =
                latest
                        ? campus.absentStudents()
                        : Math.max(1, Math.round(total * (0.038 + (month % 4) * 0.006)));
        long present = latest ? campus.presentStudents() : total - absent;
        long teachersAbsent = latest ? campus.teachersAbsent() : month % 3 == 0 ? 1 : 0;
        BigDecimal revenueFactor =
                BigDecimal.valueOf(0.55).add(progress.multiply(BigDecimal.valueOf(0.45)));
        BigDecimal expected =
                money(BigDecimal.valueOf(campus.expectedRevenue()).multiply(revenueFactor));
        BigDecimal collectionRate =
                BigDecimal.valueOf(0.72).add(progress.multiply(BigDecimal.valueOf(0.11)));
        BigDecimal collected =
                latest
                        ? BigDecimal.valueOf(campus.collectedRevenue())
                        : money(expected.multiply(collectionRate));
        BigDecimal outstanding = expected.subtract(collected);
        long applications =
                latest ? campus.newApplications() : 2 + ((month + campus.totalStudents()) % 7);
        jdbc.update(
                """
                INSERT INTO analytics_snapshot
                    (id,tenant_id,campus_id,snapshot_date,total_students,present_students,
                     absent_students,teachers_absent,expected_revenue,collected_revenue,
                     outstanding_revenue,new_applications,enrolled_students,capacity_total,
                     currency_code,source,created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'MAD', 'SCHEDULED', ?)
                """,
                UUID.randomUUID(),
                tenantId,
                campus.id(),
                date,
                total,
                present,
                absent,
                teachersAbsent,
                expected,
                collected,
                outstanding,
                applications,
                total,
                campus.capacity(),
                timestamp(clock.instant()));
    }

    private void seedDimensions(UUID tenantId, LocalDate date) {
        dimension(
                tenantId,
                date,
                "CLASS_DISTRIBUTION",
                List.of(
                        new MetricSeed("PRESCHOOL", "Maternelle", "التعليم الأولي", 286, 330L),
                        new MetricSeed("PRIMARY", "Primaire", "الابتدائي", 617, 700L),
                        new MetricSeed("MIDDLE", "Collège", "الإعدادي", 263, 320L),
                        new MetricSeed("HIGH", "Lycée", "الثانوي", 118, 150L)));
        dimension(
                tenantId,
                date,
                "ADMISSIONS_FUNNEL",
                List.of(
                        new MetricSeed("PROSPECT", "Prospects", "المرشحون", 76, null),
                        new MetricSeed("APPLICATION", "Candidatures", "الطلبات", 52, null),
                        new MetricSeed(
                                "COMPLETE", "Dossiers complets", "الملفات المكتملة", 39, null),
                        new MetricSeed("ADMITTED", "Admis", "المقبولون", 24, null),
                        new MetricSeed("ENROLLED", "Inscrits", "المسجلون", 14, null)));
        dimension(
                tenantId,
                date,
                "REVENUE_BY_SERVICE",
                List.of(
                        new MetricSeed("TUITION", "Scolarité", "التمدرس", 980_000, null),
                        new MetricSeed("REGISTRATION", "Inscription", "التسجيل", 120_000, null),
                        new MetricSeed("TRANSPORT", "Transport", "النقل", 110_000, null),
                        new MetricSeed("CAFETERIA", "Cantine", "المطعم", 90_000, null),
                        new MetricSeed("ACTIVITY", "Activités", "الأنشطة", 70_000, null),
                        new MetricSeed("DAYCARE", "Garderie", "الحضانة", 50_000, null)));
        dimension(
                tenantId,
                date,
                "UNPAID_AGING",
                List.of(
                        new MetricSeed("CURRENT", "À échoir", "غير مستحق", 95_000, null),
                        new MetricSeed("D1_7", "1–7 jours", "1–7 أيام", 55_000, null),
                        new MetricSeed("D8_30", "8–30 jours", "8–30 يوماً", 48_000, null),
                        new MetricSeed("D31_60", "31–60 jours", "31–60 يوماً", 27_000, null),
                        new MetricSeed("D60_PLUS", "+60 jours", "+60 يوماً", 15_000, null)));
    }

    private void dimension(
            UUID tenantId, LocalDate date, String metricCode, List<MetricSeed> values) {
        for (int index = 0; index < values.size(); index++) {
            MetricSeed value = values.get(index);
            jdbc.update(
                    """
                    INSERT INTO analytics_dimension_value
                        (id,tenant_id,campus_id,snapshot_date,metric_code,dimension_code,label_fr,
                         label_ar,metric_value,target_value,sort_order,created_at)
                    VALUES (?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    UUID.randomUUID(),
                    tenantId,
                    date,
                    metricCode,
                    value.code(),
                    value.labelFr(),
                    value.labelAr(),
                    BigDecimal.valueOf(value.value()),
                    value.target() == null ? null : BigDecimal.valueOf(value.target()),
                    index,
                    timestamp(clock.instant()));
        }
    }

    private UUID maybeId(String sql, Object... arguments) {
        List<UUID> ids = jdbc.query(sql, (rs, row) -> rs.getObject(1, UUID.class), arguments);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private int count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Integer.class, arguments);
    }

    private static long scale(long value, BigDecimal factor) {
        return BigDecimal.valueOf(value)
                .multiply(factor)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private record CampusSeed(
            UUID id,
            long totalStudents,
            long presentStudents,
            long absentStudents,
            long teachersAbsent,
            long expectedRevenue,
            long collectedRevenue,
            long newApplications,
            long capacity) {}

    private record MetricSeed(
            String code, String labelFr, String labelAr, long value, Long target) {}
}
