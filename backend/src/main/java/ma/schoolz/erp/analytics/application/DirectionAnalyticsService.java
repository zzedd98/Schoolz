package ma.schoolz.erp.analytics.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DirectionAnalyticsService {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public DirectionAnalyticsService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('analytics.read')")
    public DirectionDashboard direction(
            SchoolPrincipal actor, LocalDate requestedDate, UUID campusId) {
        LocalDate asOf = requestedDate == null ? LocalDate.now(clock) : requestedDate;
        if (asOf.isBefore(LocalDate.now(clock).minusYears(5))
                || asOf.isAfter(LocalDate.now(clock).plusYears(1))) {
            throw new IllegalArgumentException(
                    "La date analytics est hors de la période autorisée");
        }
        if (campusId != null && !campusExists(actor.tenantId(), campusId)) {
            throw new IllegalArgumentException("Campus introuvable dans cet établissement");
        }

        List<Snapshot> latest = latest(actor.tenantId(), asOf, campusId);
        if (latest.isEmpty()) {
            return new DirectionDashboard(
                    clock.instant(),
                    asOf,
                    campusId,
                    campuses(actor.tenantId()),
                    Summary.empty(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of("Aucun snapshot de pilotage n’est encore disponible."));
        }

        Summary summary = summarize(latest);
        Summary groupSummary = summarize(latest(actor.tenantId(), asOf, null));
        List<DimensionValue> dimensions = dimensions(actor.tenantId(), asOf);
        if (campusId != null && groupSummary.totalStudents() > 0) {
            dimensions = scaleDimensions(dimensions, summary, groupSummary);
        }
        List<TrendPoint> trends = trends(actor.tenantId(), asOf, campusId);
        List<EnrollmentPoint> enrollments =
                trends.stream()
                        .map(
                                point ->
                                        new EnrollmentPoint(
                                                point.date(),
                                                point.totalStudents(),
                                                point.newApplications()))
                        .toList();
        List<CollectionPoint> collections =
                trends.stream()
                        .map(
                                point ->
                                        new CollectionPoint(
                                                point.date(),
                                                point.expectedRevenue(),
                                                point.collectedRevenue(),
                                                point.outstandingRevenue(),
                                                percentage(
                                                        point.collectedRevenue(),
                                                        point.expectedRevenue())))
                        .toList();
        List<AttendancePoint> attendance =
                trends.stream()
                        .map(
                                point ->
                                        new AttendancePoint(
                                                point.date(),
                                                point.presentStudents(),
                                                point.absentStudents(),
                                                percentage(
                                                        BigDecimal.valueOf(point.absentStudents()),
                                                        BigDecimal.valueOf(
                                                                point.presentStudents()
                                                                        + point.absentStudents()))))
                        .toList();
        return new DirectionDashboard(
                clock.instant(),
                asOf,
                campusId,
                campuses(actor.tenantId()),
                summary,
                enrollments,
                collections,
                attendance,
                dimension(dimensions, "CLASS_DISTRIBUTION"),
                dimension(dimensions, "ADMISSIONS_FUNNEL"),
                dimension(dimensions, "REVENUE_BY_SERVICE"),
                dimension(dimensions, "UNPAID_AGING"),
                comparisons(actor.tenantId(), asOf),
                insights(summary));
    }

    private List<Snapshot> latest(UUID tenantId, LocalDate asOf, UUID campusId) {
        String campusClause = campusId == null ? "" : " AND s.campus_id=?";
        List<Object> arguments = new ArrayList<>();
        arguments.add(tenantId);
        arguments.add(asOf);
        arguments.add(tenantId);
        arguments.add(asOf);
        if (campusId != null) arguments.add(campusId);
        return jdbc.query(
                """
                SELECT s.*, c.code campus_code, c.name campus_name, c.city campus_city
                FROM analytics_snapshot s
                JOIN school_campus c ON c.tenant_id=s.tenant_id AND c.id=s.campus_id
                WHERE s.tenant_id=? AND s.snapshot_date=(
                    SELECT max(s2.snapshot_date) FROM analytics_snapshot s2
                    WHERE s2.tenant_id=? AND s2.snapshot_date<=?
                )
                """
                                .replace(
                                        "WHERE s.tenant_id=?",
                                        "WHERE s.tenant_id=? AND s.snapshot_date<=?")
                        + campusClause
                        + " ORDER BY c.name",
                this::snapshot,
                arguments.toArray());
    }

    private List<TrendPoint> trends(UUID tenantId, LocalDate asOf, UUID campusId) {
        String campusClause = campusId == null ? "" : " AND campus_id=?";
        List<Object> arguments = new ArrayList<>();
        arguments.add(tenantId);
        arguments.add(asOf);
        if (campusId != null) arguments.add(campusId);
        List<TrendPoint> result =
                jdbc.query(
                        """
                        SELECT snapshot_date,
                               sum(total_students),sum(present_students),sum(absent_students),
                               sum(expected_revenue),sum(collected_revenue),sum(outstanding_revenue),
                               sum(new_applications)
                        FROM analytics_snapshot
                        WHERE tenant_id=? AND snapshot_date<=?
                        """
                                + campusClause
                                + " GROUP BY snapshot_date ORDER BY snapshot_date",
                        (rs, row) ->
                                new TrendPoint(
                                        rs.getObject(1, LocalDate.class),
                                        rs.getLong(2),
                                        rs.getLong(3),
                                        rs.getLong(4),
                                        rs.getBigDecimal(5),
                                        rs.getBigDecimal(6),
                                        rs.getBigDecimal(7),
                                        rs.getLong(8)),
                        arguments.toArray());
        return result.size() <= 12 ? result : result.subList(result.size() - 12, result.size());
    }

    private List<CampusOption> campuses(UUID tenantId) {
        return jdbc.query(
                """
                SELECT DISTINCT c.id,c.code,c.name,c.city
                FROM school_campus c
                JOIN analytics_snapshot s ON s.tenant_id=c.tenant_id AND s.campus_id=c.id
                WHERE c.tenant_id=? AND c.active=TRUE ORDER BY c.city,c.name
                """,
                (rs, row) ->
                        new CampusOption(
                                rs.getObject(1, UUID.class),
                                rs.getString(2),
                                rs.getString(3),
                                rs.getString(4)),
                tenantId);
    }

    private List<CampusComparison> comparisons(UUID tenantId, LocalDate asOf) {
        return latest(tenantId, asOf, null).stream()
                .map(
                        snapshot ->
                                new CampusComparison(
                                        snapshot.campusId(),
                                        snapshot.campusCode(),
                                        snapshot.campusName(),
                                        snapshot.campusCity(),
                                        snapshot.totalStudents(),
                                        snapshot.presentStudents(),
                                        snapshot.absentStudents(),
                                        snapshot.expectedRevenue(),
                                        snapshot.collectedRevenue(),
                                        snapshot.outstandingRevenue(),
                                        snapshot.capacityTotal(),
                                        percentage(
                                                BigDecimal.valueOf(snapshot.enrolledStudents()),
                                                BigDecimal.valueOf(snapshot.capacityTotal())),
                                        percentage(
                                                snapshot.collectedRevenue(),
                                                snapshot.expectedRevenue())))
                .toList();
    }

    private List<DimensionValue> dimensions(UUID tenantId, LocalDate asOf) {
        return jdbc.query(
                """
                SELECT metric_code,dimension_code,label_fr,label_ar,metric_value,target_value,sort_order
                FROM analytics_dimension_value d
                WHERE d.tenant_id=? AND d.campus_id IS NULL AND d.snapshot_date=(
                    SELECT max(d2.snapshot_date) FROM analytics_dimension_value d2
                    WHERE d2.tenant_id=? AND d2.campus_id IS NULL AND d2.snapshot_date<=?
                )
                ORDER BY metric_code,sort_order,dimension_code
                """,
                (rs, row) ->
                        new DimensionValue(
                                rs.getString(1),
                                rs.getString(2),
                                rs.getString(3),
                                rs.getString(4),
                                rs.getBigDecimal(5),
                                rs.getBigDecimal(6),
                                rs.getInt(7)),
                tenantId,
                tenantId,
                asOf);
    }

    private List<DimensionValue> scaleDimensions(
            List<DimensionValue> values, Summary campus, Summary group) {
        return values.stream()
                .map(
                        value -> {
                            BigDecimal ratio =
                                    switch (value.metricCode()) {
                                        case "REVENUE_BY_SERVICE" ->
                                                ratio(
                                                        campus.expectedRevenue(),
                                                        group.expectedRevenue());
                                        case "UNPAID_AGING" ->
                                                ratio(
                                                        campus.outstandingRevenue(),
                                                        group.outstandingRevenue());
                                        default ->
                                                ratio(
                                                        BigDecimal.valueOf(campus.totalStudents()),
                                                        BigDecimal.valueOf(group.totalStudents()));
                                    };
                            return new DimensionValue(
                                    value.metricCode(),
                                    value.code(),
                                    value.labelFr(),
                                    value.labelAr(),
                                    value.value().multiply(ratio).setScale(0, RoundingMode.HALF_UP),
                                    value.target() == null
                                            ? null
                                            : value.target()
                                                    .multiply(ratio)
                                                    .setScale(0, RoundingMode.HALF_UP),
                                    value.order());
                        })
                .toList();
    }

    private boolean campusExists(UUID tenantId, UUID campusId) {
        Integer count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM school_campus WHERE tenant_id=? AND id=? AND"
                                + " active=TRUE",
                        Integer.class,
                        tenantId,
                        campusId);
        return count != null && count > 0;
    }

    private Snapshot snapshot(ResultSet rs, int row) throws SQLException {
        return new Snapshot(
                rs.getObject("campus_id", UUID.class),
                rs.getString("campus_code"),
                rs.getString("campus_name"),
                rs.getString("campus_city"),
                rs.getObject("snapshot_date", LocalDate.class),
                rs.getLong("total_students"),
                rs.getLong("present_students"),
                rs.getLong("absent_students"),
                rs.getLong("teachers_absent"),
                rs.getBigDecimal("expected_revenue"),
                rs.getBigDecimal("collected_revenue"),
                rs.getBigDecimal("outstanding_revenue"),
                rs.getLong("new_applications"),
                rs.getLong("enrolled_students"),
                rs.getLong("capacity_total"),
                rs.getString("currency_code"));
    }

    private Summary summarize(List<Snapshot> snapshots) {
        long total = snapshots.stream().mapToLong(Snapshot::totalStudents).sum();
        long present = snapshots.stream().mapToLong(Snapshot::presentStudents).sum();
        long absent = snapshots.stream().mapToLong(Snapshot::absentStudents).sum();
        BigDecimal expected = sum(snapshots.stream().map(Snapshot::expectedRevenue).toList());
        BigDecimal collected = sum(snapshots.stream().map(Snapshot::collectedRevenue).toList());
        BigDecimal outstanding = sum(snapshots.stream().map(Snapshot::outstandingRevenue).toList());
        long capacity = snapshots.stream().mapToLong(Snapshot::capacityTotal).sum();
        long enrolled = snapshots.stream().mapToLong(Snapshot::enrolledStudents).sum();
        return new Summary(
                snapshots.stream()
                        .map(Snapshot::snapshotDate)
                        .max(LocalDate::compareTo)
                        .orElse(null),
                total,
                present,
                absent,
                snapshots.stream().mapToLong(Snapshot::teachersAbsent).sum(),
                expected,
                collected,
                outstanding,
                snapshots.stream().mapToLong(Snapshot::newApplications).sum(),
                capacity,
                enrolled,
                percentage(BigDecimal.valueOf(absent), BigDecimal.valueOf(present + absent)),
                percentage(collected, expected),
                percentage(BigDecimal.valueOf(enrolled), BigDecimal.valueOf(capacity)),
                snapshots.getFirst().currencyCode());
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal percentage(BigDecimal value, BigDecimal total) {
        if (total == null || total.signum() == 0) return BigDecimal.ZERO;
        return value.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal ratio(BigDecimal value, BigDecimal total) {
        if (total.signum() == 0) return BigDecimal.ZERO;
        return value.divide(total, 8, RoundingMode.HALF_UP);
    }

    private static List<DimensionValue> dimension(List<DimensionValue> values, String metricCode) {
        return values.stream().filter(value -> metricCode.equals(value.metricCode())).toList();
    }

    private static List<String> insights(Summary summary) {
        List<String> insights = new ArrayList<>();
        if (summary.collectionRate().compareTo(BigDecimal.valueOf(85)) < 0) {
            insights.add(
                    "Le recouvrement est inférieur à 85 % : prioriser les échéances anciennes et"
                            + " les familles déjà contactées.");
        }
        if (summary.absenceRate().compareTo(BigDecimal.valueOf(5)) >= 0) {
            insights.add(
                    "L’absentéisme atteint au moins 5 % : comparer les campus avant toute action"
                            + " ciblée.");
        }
        if (summary.capacityRate().compareTo(BigDecimal.valueOf(90)) >= 0) {
            insights.add(
                    "La capacité dépasse 90 % : préparer les ouvertures de classes et les"
                            + " recrutements associés.");
        }
        if (insights.isEmpty()) {
            insights.add("Les indicateurs principaux restent dans les seuils de pilotage retenus.");
        }
        return List.copyOf(insights);
    }

    public record DirectionDashboard(
            Instant generatedAt,
            LocalDate asOf,
            UUID selectedCampusId,
            List<CampusOption> campuses,
            Summary summary,
            List<EnrollmentPoint> enrollmentTrend,
            List<CollectionPoint> collectionTrend,
            List<AttendancePoint> absenteeismTrend,
            List<DimensionValue> classDistribution,
            List<DimensionValue> admissionsFunnel,
            List<DimensionValue> revenueByService,
            List<DimensionValue> unpaidAging,
            List<CampusComparison> campusComparison,
            List<String> insights) {}

    public record CampusOption(UUID id, String code, String name, String city) {}

    public record Summary(
            LocalDate snapshotDate,
            long totalStudents,
            long presentStudents,
            long absentStudents,
            long teachersAbsent,
            BigDecimal expectedRevenue,
            BigDecimal collectedRevenue,
            BigDecimal outstandingRevenue,
            long newApplications,
            long capacityTotal,
            long enrolledStudents,
            BigDecimal absenceRate,
            BigDecimal collectionRate,
            BigDecimal capacityRate,
            String currencyCode) {
        static Summary empty() {
            return new Summary(
                    null,
                    0,
                    0,
                    0,
                    0,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    0,
                    0,
                    0,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    "MAD");
        }
    }

    public record EnrollmentPoint(LocalDate date, long totalStudents, long newApplications) {}

    public record CollectionPoint(
            LocalDate date,
            BigDecimal expected,
            BigDecimal collected,
            BigDecimal outstanding,
            BigDecimal collectionRate) {}

    public record AttendancePoint(
            LocalDate date, long present, long absent, BigDecimal absenceRate) {}

    public record DimensionValue(
            String metricCode,
            String code,
            String labelFr,
            String labelAr,
            BigDecimal value,
            BigDecimal target,
            int order) {}

    public record CampusComparison(
            UUID campusId,
            String campusCode,
            String campusName,
            String city,
            long totalStudents,
            long presentStudents,
            long absentStudents,
            BigDecimal expectedRevenue,
            BigDecimal collectedRevenue,
            BigDecimal outstandingRevenue,
            long capacityTotal,
            BigDecimal capacityRate,
            BigDecimal collectionRate) {}

    private record Snapshot(
            UUID campusId,
            String campusCode,
            String campusName,
            String campusCity,
            LocalDate snapshotDate,
            long totalStudents,
            long presentStudents,
            long absentStudents,
            long teachersAbsent,
            BigDecimal expectedRevenue,
            BigDecimal collectedRevenue,
            BigDecimal outstandingRevenue,
            long newApplications,
            long enrolledStudents,
            long capacityTotal,
            String currencyCode) {}

    private record TrendPoint(
            LocalDate date,
            long totalStudents,
            long presentStudents,
            long absentStudents,
            BigDecimal expectedRevenue,
            BigDecimal collectedRevenue,
            BigDecimal outstandingRevenue,
            long newApplications) {}
}
