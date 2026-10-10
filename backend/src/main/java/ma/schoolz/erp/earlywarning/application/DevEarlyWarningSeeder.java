package ma.schoolz.erp.earlywarning.application;

import java.math.BigDecimal;
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
@Order(280)
public class DevEarlyWarningSeeder implements ApplicationRunner {
    private static final LocalDate DEMO_DATE = LocalDate.of(2026, 8, 10);
    private final JdbcTemplate jdbc;
    private final TenantDirectory tenants;
    private final EarlyWarningService service;
    private final Clock clock;
    private final boolean enabled;
    private final String tenantSlug;

    public DevEarlyWarningSeeder(
            JdbcTemplate jdbc,
            TenantDirectory tenants,
            EarlyWarningService service,
            Clock clock,
            @Value("${schoolz.demo.seed-enabled:false}") boolean enabled,
            @Value("${schoolz.demo.tenant-slug:}") String tenantSlug) {
        this.jdbc = jdbc;
        this.tenants = tenants;
        this.service = service;
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
                        .map(TenantDirectory.TenantSummary::id)
                        .orElse(null);
        if (tenantId == null) return;
        UUID actorId =
                maybeId(
                        "SELECT id FROM identity_user_account WHERE tenant_id=? ORDER BY created_at"
                                + " FETCH FIRST 1 ROW ONLY",
                        tenantId);
        UUID yasmine = student(tenantId, "SZ-2026-0001");
        UUID imane = student(tenantId, "SZ-2026-0002");
        UUID adam = student(tenantId, "SZ-2026-0003");
        if (actorId == null || yasmine == null || imane == null || adam == null) return;

        rule(
                tenantId,
                actorId,
                "ASSIDUITE_ET_NOTES",
                "Assiduité et résultats à accompagner",
                "Combine un taux d’absence élevé et une moyenne sous le seuil choisi.",
                "HIGH",
                "ALL",
                List.of(
                        new SeedCondition("ABSENCE_RATE", "GT", "10"),
                        new SeedCondition("AVERAGE_GRADE", "LT", "10")));
        rule(
                tenantId,
                actorId,
                "RETARDS_REPETES",
                "Retards répétés",
                "Signale au moins trois retards sur la période observée.",
                "MEDIUM",
                "ALL",
                List.of(new SeedCondition("LATE_COUNT", "GTE", "3")));
        rule(
                tenantId,
                actorId,
                "BAISSE_NOTES",
                "Baisse récente des notes",
                "Signale une baisse factuelle d’au moins deux points.",
                "HIGH",
                "ALL",
                List.of(new SeedCondition("GRADE_DROP", "GTE", "2")));
        rule(
                tenantId,
                actorId,
                "INCIDENTS_REPETES",
                "Incidents répétés",
                "Signale au moins deux incidents consignés sur la période.",
                "HIGH",
                "ALL",
                List.of(new SeedCondition("INCIDENT_COUNT", "GTE", "2")));
        rule(
                tenantId,
                actorId,
                "SOLDE_IMPAYE",
                "Solde famille à accompagner",
                "Signale un montant arrivé à échéance et restant dû.",
                "MEDIUM",
                "ALL",
                List.of(new SeedCondition("UNPAID_AMOUNT", "GT", "0")));
        rule(
                tenantId,
                actorId,
                "DOSSIER_INCOMPLET",
                "Dossier administratif incomplet",
                "Signale au moins un document obligatoire manquant.",
                "LOW",
                "ALL",
                List.of(new SeedCondition("MISSING_DOCUMENT_COUNT", "GT", "0")));

        signals(
                tenantId,
                yasmine,
                List.of(
                        new SeedSignal(
                                "ABSENCE_RATE",
                                "12.5",
                                "PERCENT",
                                5,
                                "attendance",
                                "5 absences sur 40 demi-journées"),
                        new SeedSignal(
                                "LATE_COUNT", "4", "COUNT", 4, "attendance", "4 retards consignés"),
                        new SeedSignal(
                                "AVERAGE_GRADE",
                                "9.2",
                                "SCORE_20",
                                6,
                                "grading",
                                "Moyenne calculée sur 6 évaluations"),
                        new SeedSignal(
                                "GRADE_DROP",
                                "2.8",
                                "POINTS",
                                3,
                                "grading",
                                "Écart de 2,8 points entre les deux périodes"),
                        new SeedSignal(
                                "INCIDENT_COUNT",
                                "2",
                                "COUNT",
                                2,
                                "attendance",
                                "2 incidents factuels consignés"),
                        new SeedSignal(
                                "UNPAID_AMOUNT",
                                "1500",
                                "MAD",
                                1,
                                "billing",
                                "1 500 MAD arrivés à échéance"),
                        new SeedSignal(
                                "MISSING_DOCUMENT_COUNT",
                                "1",
                                "COUNT",
                                1,
                                "documents",
                                "Autorisation de sortie manquante")));
        signals(
                tenantId,
                imane,
                List.of(
                        new SeedSignal(
                                "ABSENCE_RATE",
                                "2.5",
                                "PERCENT",
                                1,
                                "attendance",
                                "1 absence sur 40 demi-journées"),
                        new SeedSignal(
                                "LATE_COUNT",
                                "0",
                                "COUNT",
                                0,
                                "attendance",
                                "Aucun retard consigné"),
                        new SeedSignal(
                                "AVERAGE_GRADE",
                                "16.4",
                                "SCORE_20",
                                6,
                                "grading",
                                "Moyenne calculée sur 6 évaluations"),
                        new SeedSignal(
                                "GRADE_DROP",
                                "0",
                                "POINTS",
                                3,
                                "grading",
                                "Aucune baisse entre les deux périodes"),
                        new SeedSignal(
                                "INCIDENT_COUNT",
                                "0",
                                "COUNT",
                                0,
                                "attendance",
                                "Aucun incident consigné"),
                        new SeedSignal(
                                "UNPAID_AMOUNT",
                                "0",
                                "MAD",
                                0,
                                "billing",
                                "Aucun montant arrivé à échéance"),
                        new SeedSignal(
                                "MISSING_DOCUMENT_COUNT",
                                "0",
                                "COUNT",
                                0,
                                "documents",
                                "Dossier complet")));
        signals(
                tenantId,
                adam,
                List.of(
                        new SeedSignal(
                                "ABSENCE_RATE",
                                "5.8",
                                "PERCENT",
                                2,
                                "attendance",
                                "2 absences sur la période"),
                        new SeedSignal(
                                "LATE_COUNT", "3", "COUNT", 3, "attendance", "3 retards consignés"),
                        new SeedSignal(
                                "AVERAGE_GRADE",
                                "13.5",
                                "SCORE_20",
                                5,
                                "grading",
                                "Moyenne calculée sur 5 évaluations"),
                        new SeedSignal(
                                "GRADE_DROP",
                                "1",
                                "POINTS",
                                2,
                                "grading",
                                "Écart de 1 point entre les deux périodes"),
                        new SeedSignal(
                                "INCIDENT_COUNT",
                                "1",
                                "COUNT",
                                1,
                                "attendance",
                                "1 incident factuel consigné"),
                        new SeedSignal(
                                "UNPAID_AMOUNT",
                                "400",
                                "MAD",
                                1,
                                "billing",
                                "400 MAD arrivés à échéance"),
                        new SeedSignal(
                                "MISSING_DOCUMENT_COUNT",
                                "2",
                                "COUNT",
                                2,
                                "documents",
                                "2 documents administratifs manquants")));

        if (count("SELECT count(*) FROM early_warning_evaluation WHERE tenant_id=?", tenantId)
                == 0) {
            service.evaluateForTenant(tenantId, actorId, DEMO_DATE);
        }
    }

    private UUID rule(
            UUID tenantId,
            UUID actorId,
            String code,
            String name,
            String description,
            String severity,
            String matchMode,
            List<SeedCondition> conditions) {
        UUID existing =
                maybeId(
                        "SELECT id FROM early_warning_rule WHERE tenant_id=? AND code=?",
                        tenantId,
                        code);
        if (existing != null) return existing;
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                """
                INSERT INTO early_warning_rule
                    (id,tenant_id,code,name,description,severity,match_mode,active,
                     created_by_user_id,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,TRUE,?,?,?)
                """,
                id,
                tenantId,
                code,
                name,
                description,
                severity,
                matchMode,
                actorId,
                timestamp(now),
                timestamp(now));
        for (int index = 0; index < conditions.size(); index++) {
            SeedCondition condition = conditions.get(index);
            jdbc.update(
                    """
                    INSERT INTO early_warning_rule_condition
                        (id,tenant_id,rule_id,signal_type,comparison_operator,threshold_value,
                         sort_order,created_at)
                    VALUES (?,?,?,?,?,?,?,?)
                    """,
                    UUID.randomUUID(),
                    tenantId,
                    id,
                    condition.signalType(),
                    condition.operator(),
                    new BigDecimal(condition.threshold()),
                    index,
                    timestamp(now));
        }
        return id;
    }

    private void signals(UUID tenantId, UUID studentId, List<SeedSignal> values) {
        for (SeedSignal value : values) {
            if (count(
                            """
                            SELECT count(*) FROM early_warning_student_signal
                            WHERE tenant_id=? AND student_id=? AND measured_on=? AND signal_type=?
                            """,
                            tenantId,
                            studentId,
                            DEMO_DATE,
                            value.type())
                    > 0) continue;
            jdbc.update(
                    """
                    INSERT INTO early_warning_student_signal
                        (id,tenant_id,student_id,measured_on,signal_type,numeric_value,unit_code,
                         evidence_count,source_module,factual_detail,created_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?)
                    """,
                    UUID.randomUUID(),
                    tenantId,
                    studentId,
                    DEMO_DATE,
                    value.type(),
                    new BigDecimal(value.value()),
                    value.unit(),
                    value.evidenceCount(),
                    value.source(),
                    value.detail(),
                    timestamp(clock.instant()));
        }
    }

    private UUID student(UUID tenantId, String identifier) {
        return maybeId(
                "SELECT student_id FROM student_identifier WHERE tenant_id=? AND"
                        + " identifier_value=?",
                tenantId,
                identifier);
    }

    private UUID maybeId(String sql, Object... arguments) {
        List<UUID> ids = jdbc.query(sql, (rs, row) -> rs.getObject(1, UUID.class), arguments);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private int count(String sql, Object... arguments) {
        Integer value = jdbc.queryForObject(sql, Integer.class, arguments);
        return value == null ? 0 : value;
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private record SeedCondition(String signalType, String operator, String threshold) {}

    private record SeedSignal(
            String type,
            String value,
            String unit,
            int evidenceCount,
            String source,
            String detail) {}
}
