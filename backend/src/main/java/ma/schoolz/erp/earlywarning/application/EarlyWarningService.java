package ma.schoolz.erp.earlywarning.application;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import ma.schoolz.erp.earlywarning.application.EarlyWarningRuleEngine.Condition;
import ma.schoolz.erp.earlywarning.application.EarlyWarningRuleEngine.ConditionResult;
import ma.schoolz.erp.earlywarning.application.EarlyWarningRuleEngine.Evaluation;
import ma.schoolz.erp.earlywarning.application.EarlyWarningRuleEngine.MatchMode;
import ma.schoolz.erp.earlywarning.application.EarlyWarningRuleEngine.Operator;
import ma.schoolz.erp.earlywarning.application.EarlyWarningRuleEngine.Rule;
import ma.schoolz.erp.earlywarning.application.EarlyWarningRuleEngine.Signal;
import ma.schoolz.erp.earlywarning.application.EarlyWarningRuleEngine.SignalType;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.identity.IdentityAuditTrail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EarlyWarningService {
    private static final Set<String> SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> PROHIBITED_TERMS =
            Set.of(
                    "diagnostic",
                    "psycholog",
                    "depress",
                    "anxieux",
                    "anxiete",
                    "patholog",
                    "trouble mental",
                    "maladie mentale");
    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_-]{2,79}");
    private final JdbcTemplate jdbc;
    private final IdentityAuditTrail audit;
    private final Clock clock;
    private final EarlyWarningRuleEngine engine = new EarlyWarningRuleEngine();

    public EarlyWarningService(JdbcTemplate jdbc, IdentityAuditTrail audit, Clock clock) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('early-warning.read')")
    public Dashboard dashboard(SchoolPrincipal actor, LocalDate requestedDate) {
        return dashboard(actor.tenantId(), validatedDate(requestedDate));
    }

    @Transactional
    @PreAuthorize("hasAuthority('early-warning.manage')")
    public Dashboard evaluate(SchoolPrincipal actor, LocalDate requestedDate, String ipAddress) {
        LocalDate asOf = validatedDate(requestedDate);
        EvaluationRun run = evaluateForTenant(actor.tenantId(), actor.accountId(), asOf);
        recordAudit(actor, "EARLY_WARNING_EVALUATED", "evaluation", run.id(), ipAddress, run);
        return dashboard(actor.tenantId(), asOf);
    }

    @Transactional
    @PreAuthorize("hasAuthority('early-warning.manage')")
    public RuleView createRule(SchoolPrincipal actor, RuleCommand command, String ipAddress) {
        validate(command);
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        String code = command.code().trim().toUpperCase(Locale.ROOT);
        jdbc.update(
                """
                INSERT INTO early_warning_rule
                    (id,tenant_id,code,name,description,severity,match_mode,active,
                     created_by_user_id,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """,
                id,
                actor.tenantId(),
                code,
                clean(command.name()),
                clean(command.description()),
                command.severity(),
                command.matchMode(),
                command.active(),
                actor.accountId(),
                timestamp(now),
                timestamp(now));
        for (int index = 0; index < command.conditions().size(); index++) {
            ConditionCommand condition = command.conditions().get(index);
            jdbc.update(
                    """
                    INSERT INTO early_warning_rule_condition
                        (id,tenant_id,rule_id,signal_type,comparison_operator,threshold_value,
                         sort_order,created_at)
                    VALUES (?,?,?,?,?,?,?,?)
                    """,
                    UUID.randomUUID(),
                    actor.tenantId(),
                    id,
                    condition.signalType(),
                    condition.operator(),
                    condition.threshold(),
                    index,
                    timestamp(now));
        }
        RuleView created =
                rules(actor.tenantId()).stream()
                        .filter(rule -> rule.id().equals(id))
                        .findFirst()
                        .orElseThrow();
        recordAudit(actor, "EARLY_WARNING_RULE_CREATED", "rule", id, ipAddress, created);
        return created;
    }

    @Transactional
    @PreAuthorize("hasAuthority('early-warning.manage')")
    public AlertView acknowledge(
            SchoolPrincipal actor, UUID alertId, AlertAction command, String ipAddress) {
        String note =
                required(
                        command == null ? null : command.note(),
                        "La note de prise en charge est requise");
        ensureFactual(note);
        ensureAlertStatus(actor.tenantId(), alertId, Set.of("OPEN"));
        Instant now = clock.instant();
        jdbc.update(
                """
                UPDATE early_warning_alert
                SET status='ACKNOWLEDGED',acknowledged_at=?,acknowledged_by_user_id=?,
                    acknowledgement_note=?,updated_at=?
                WHERE tenant_id=? AND id=?
                """,
                timestamp(now),
                actor.accountId(),
                note,
                timestamp(now),
                actor.tenantId(),
                alertId);
        AlertView updated = alert(actor.tenantId(), alertId);
        recordAudit(
                actor, "EARLY_WARNING_ALERT_ACKNOWLEDGED", "alert", alertId, ipAddress, updated);
        return updated;
    }

    @Transactional
    @PreAuthorize("hasAuthority('early-warning.manage')")
    public AlertView resolve(
            SchoolPrincipal actor, UUID alertId, AlertAction command, String ipAddress) {
        String note =
                required(
                        command == null ? null : command.note(),
                        "La résolution factuelle est requise");
        ensureFactual(note);
        ensureAlertStatus(actor.tenantId(), alertId, Set.of("OPEN", "ACKNOWLEDGED"));
        Instant now = clock.instant();
        jdbc.update(
                """
                UPDATE early_warning_alert
                SET status='RESOLVED',resolved_at=?,resolved_by_user_id=?,resolution_note=?,updated_at=?
                WHERE tenant_id=? AND id=?
                """,
                timestamp(now),
                actor.accountId(),
                note,
                timestamp(now),
                actor.tenantId(),
                alertId);
        AlertView updated = alert(actor.tenantId(), alertId);
        recordAudit(actor, "EARLY_WARNING_ALERT_RESOLVED", "alert", alertId, ipAddress, updated);
        return updated;
    }

    EvaluationRun evaluateForTenant(UUID tenantId, UUID actorId, LocalDate asOf) {
        Instant started = clock.instant();
        List<RuleView> activeRules = rules(tenantId).stream().filter(RuleView::active).toList();
        List<StudentView> students = students(tenantId, asOf, List.of());
        int created = 0;
        int refreshed = 0;
        for (RuleView rule : activeRules) {
            Rule engineRule =
                    new Rule(
                            MatchMode.valueOf(rule.matchMode()),
                            rule.conditions().stream()
                                    .map(
                                            condition ->
                                                    new Condition(
                                                            condition.id(),
                                                            SignalType.valueOf(
                                                                    condition.signalType()),
                                                            Operator.valueOf(condition.operator()),
                                                            condition.threshold()))
                                    .toList());
            for (StudentView student : students) {
                Map<SignalType, Signal> signals = new EnumMap<>(SignalType.class);
                student.signals()
                        .forEach(
                                value ->
                                        signals.put(
                                                SignalType.valueOf(value.signalType()),
                                                new Signal(value.value(), value.factualDetail())));
                Evaluation evaluation = engine.evaluate(engineRule, signals);
                if (!evaluation.triggered()) continue;
                UUID alertId = activeAlert(tenantId, rule.id(), student.id());
                if (alertId == null) {
                    alertId = UUID.randomUUID();
                    Instant now = clock.instant();
                    jdbc.update(
                            """
                            INSERT INTO early_warning_alert
                                (id,tenant_id,rule_id,student_id,status,severity,score,
                                 factual_summary,first_detected_at,last_detected_at,
                                 created_at,updated_at)
                            VALUES (?,?,?,?,'OPEN',?,?,?,?,?,?,?)
                            """,
                            alertId,
                            tenantId,
                            rule.id(),
                            student.id(),
                            rule.severity(),
                            evaluation.score(),
                            summary(evaluation.conditions()),
                            timestamp(now),
                            timestamp(now),
                            timestamp(now),
                            timestamp(now));
                    created++;
                } else {
                    jdbc.update(
                            """
                            UPDATE early_warning_alert
                            SET last_detected_at=?,score=?,factual_summary=?,updated_at=?
                            WHERE tenant_id=? AND id=?
                            """,
                            timestamp(clock.instant()),
                            evaluation.score(),
                            summary(evaluation.conditions()),
                            timestamp(clock.instant()),
                            tenantId,
                            alertId);
                    refreshed++;
                }
                replaceEvidence(tenantId, alertId, evaluation.conditions());
            }
        }
        UUID runId = UUID.randomUUID();
        Instant completed = clock.instant();
        jdbc.update(
                """
                INSERT INTO early_warning_evaluation
                    (id,tenant_id,evaluated_on,rules_evaluated,students_evaluated,
                     alerts_created,alerts_refreshed,status,started_at,completed_at,
                     initiated_by_user_id)
                VALUES (?,?,?,?,?,?,?,'COMPLETED',?,?,?)
                """,
                runId,
                tenantId,
                asOf,
                activeRules.size(),
                students.size(),
                created,
                refreshed,
                timestamp(started),
                timestamp(completed),
                actorId);
        return new EvaluationRun(
                runId, asOf, activeRules.size(), students.size(), created, refreshed, completed);
    }

    private Dashboard dashboard(UUID tenantId, LocalDate asOf) {
        List<RuleView> rules = rules(tenantId);
        List<AlertView> alerts = alerts(tenantId);
        List<StudentView> students = students(tenantId, asOf, alerts);
        long activeAlerts = alerts.stream().filter(AlertView::active).count();
        long high =
                alerts.stream()
                        .filter(AlertView::active)
                        .filter(alert -> Set.of("HIGH", "CRITICAL").contains(alert.severity()))
                        .count();
        return new Dashboard(
                clock.instant(),
                asOf,
                new EthicalPolicy(
                        "SchoolZ rapproche uniquement des faits scolaires et administratifs.",
                        "Aucun diagnostic psychologique, médical ou comportemental n’est produit.",
                        true),
                new Statistics(
                        rules.stream().filter(RuleView::active).count(),
                        students.size(),
                        activeAlerts,
                        alerts.stream()
                                .filter(alert -> "ACKNOWLEDGED".equals(alert.status()))
                                .count(),
                        high),
                signalDefinitions(),
                rules,
                students,
                alerts,
                evaluations(tenantId));
    }

    private List<RuleView> rules(UUID tenantId) {
        Map<UUID, List<ConditionView>> conditions =
                jdbc
                        .query(
                                """
                                SELECT id,rule_id,signal_type,comparison_operator,threshold_value,sort_order
                                FROM early_warning_rule_condition
                                WHERE tenant_id=? ORDER BY rule_id,sort_order
                                """,
                                (rs, row) ->
                                        new ConditionView(
                                                rs.getObject("id", UUID.class),
                                                rs.getObject("rule_id", UUID.class),
                                                rs.getString("signal_type"),
                                                signalLabel(rs.getString("signal_type")),
                                                rs.getString("comparison_operator"),
                                                rs.getBigDecimal("threshold_value"),
                                                rs.getInt("sort_order")),
                                tenantId)
                        .stream()
                        .collect(
                                Collectors.groupingBy(
                                        ConditionView::ruleId,
                                        LinkedHashMap::new,
                                        Collectors.toList()));
        return jdbc.query(
                """
                SELECT id,code,name,description,severity,match_mode,active,created_at,updated_at
                FROM early_warning_rule WHERE tenant_id=? ORDER BY active DESC,severity DESC,name
                """,
                (rs, row) ->
                        new RuleView(
                                rs.getObject("id", UUID.class),
                                rs.getString("code"),
                                rs.getString("name"),
                                rs.getString("description"),
                                rs.getString("severity"),
                                rs.getString("match_mode"),
                                rs.getBoolean("active"),
                                conditions.getOrDefault(rs.getObject("id", UUID.class), List.of()),
                                instant(rs, "created_at"),
                                instant(rs, "updated_at")),
                tenantId);
    }

    private List<StudentView> students(UUID tenantId, LocalDate asOf, List<AlertView> alerts) {
        List<SignalRow> rows =
                jdbc.query(
                        """
                        SELECT latest.student_id,p.first_name,p.last_name,
                               COALESCE(si.identifier_value,'—') identifier_value,
                               COALESCE(c.name,'Sans classe') classroom_name,
                               latest.measured_on,latest.signal_type,latest.numeric_value,
                               latest.unit_code,latest.evidence_count,latest.source_module,
                               latest.factual_detail
                        FROM (
                            SELECT s.*,row_number() OVER (
                                PARTITION BY s.student_id,s.signal_type
                                ORDER BY s.measured_on DESC,s.created_at DESC) rn
                            FROM early_warning_student_signal s
                            WHERE s.tenant_id=? AND s.measured_on<=?
                        ) latest
                        JOIN student_profile sp ON sp.tenant_id=latest.tenant_id
                                               AND sp.id=latest.student_id
                        JOIN person p ON p.tenant_id=sp.tenant_id AND p.id=sp.person_id
                        LEFT JOIN student_identifier si ON si.tenant_id=sp.tenant_id
                            AND si.student_id=sp.id AND si.primary_identifier=TRUE
                        LEFT JOIN student_enrollment se ON se.tenant_id=sp.tenant_id
                            AND se.student_id=sp.id AND se.status='ACTIVE'
                        LEFT JOIN classroom c ON c.tenant_id=se.tenant_id AND c.id=se.classroom_id
                        WHERE latest.rn=1
                        ORDER BY p.last_name,p.first_name,latest.signal_type
                        """,
                        (rs, row) -> signalRow(rs),
                        tenantId,
                        asOf);
        Map<UUID, List<SignalRow>> grouped =
                rows.stream()
                        .collect(
                                Collectors.groupingBy(
                                        SignalRow::studentId,
                                        LinkedHashMap::new,
                                        Collectors.toList()));
        Map<UUID, List<AlertView>> studentAlerts =
                alerts.stream()
                        .filter(AlertView::active)
                        .collect(Collectors.groupingBy(AlertView::studentId));
        return grouped.entrySet().stream()
                .map(
                        entry -> {
                            SignalRow first = entry.getValue().getFirst();
                            List<AlertView> current =
                                    studentAlerts.getOrDefault(entry.getKey(), List.of());
                            return new StudentView(
                                    first.studentId(),
                                    first.identifier(),
                                    first.displayName(),
                                    first.classroomName(),
                                    entry.getValue().stream().map(SignalRow::view).toList(),
                                    current.size(),
                                    current.stream()
                                            .map(AlertView::severity)
                                            .max(
                                                    Comparator.comparingInt(
                                                            EarlyWarningService::severityRank))
                                            .orElse("NONE"));
                        })
                .toList();
    }

    private List<AlertView> alerts(UUID tenantId) {
        Map<UUID, List<EvidenceView>> evidence =
                jdbc
                        .query(
                                """
                                SELECT id,alert_id,signal_type,actual_value,threshold_value,
                                       comparison_operator,matched,factual_detail
                                FROM early_warning_alert_evidence WHERE tenant_id=?
                                ORDER BY alert_id,created_at
                                """,
                                (rs, row) ->
                                        new EvidenceView(
                                                rs.getObject("id", UUID.class),
                                                rs.getObject("alert_id", UUID.class),
                                                rs.getString("signal_type"),
                                                signalLabel(rs.getString("signal_type")),
                                                rs.getBigDecimal("actual_value"),
                                                rs.getBigDecimal("threshold_value"),
                                                rs.getString("comparison_operator"),
                                                rs.getBoolean("matched"),
                                                rs.getString("factual_detail")),
                                tenantId)
                        .stream()
                        .collect(
                                Collectors.groupingBy(
                                        EvidenceView::alertId,
                                        LinkedHashMap::new,
                                        Collectors.toList()));
        return jdbc.query(
                """
                SELECT a.*,r.code rule_code,r.name rule_name,p.first_name,p.last_name,
                       COALESCE(si.identifier_value,'—') identifier_value
                FROM early_warning_alert a
                JOIN early_warning_rule r ON r.tenant_id=a.tenant_id AND r.id=a.rule_id
                JOIN student_profile sp ON sp.tenant_id=a.tenant_id AND sp.id=a.student_id
                JOIN person p ON p.tenant_id=sp.tenant_id AND p.id=sp.person_id
                LEFT JOIN student_identifier si ON si.tenant_id=sp.tenant_id
                    AND si.student_id=sp.id AND si.primary_identifier=TRUE
                WHERE a.tenant_id=?
                ORDER BY CASE a.status WHEN 'OPEN' THEN 0 WHEN 'ACKNOWLEDGED' THEN 1 ELSE 2 END,
                         CASE a.severity WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1
                              WHEN 'MEDIUM' THEN 2 ELSE 3 END,a.last_detected_at DESC
                """,
                (rs, row) -> alertRow(rs, evidence),
                tenantId);
    }

    private AlertView alert(UUID tenantId, UUID alertId) {
        return alerts(tenantId).stream()
                .filter(item -> item.id().equals(alertId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Alerte introuvable"));
    }

    private List<EvaluationView> evaluations(UUID tenantId) {
        return jdbc.query(
                """
                SELECT id,evaluated_on,rules_evaluated,students_evaluated,alerts_created,
                       alerts_refreshed,status,completed_at
                FROM early_warning_evaluation WHERE tenant_id=?
                ORDER BY completed_at DESC FETCH FIRST 10 ROWS ONLY
                """,
                (rs, row) ->
                        new EvaluationView(
                                rs.getObject("id", UUID.class),
                                rs.getObject("evaluated_on", LocalDate.class),
                                rs.getInt("rules_evaluated"),
                                rs.getInt("students_evaluated"),
                                rs.getInt("alerts_created"),
                                rs.getInt("alerts_refreshed"),
                                rs.getString("status"),
                                instant(rs, "completed_at")),
                tenantId);
    }

    private void replaceEvidence(UUID tenantId, UUID alertId, List<ConditionResult> results) {
        jdbc.update(
                "DELETE FROM early_warning_alert_evidence WHERE tenant_id=? AND alert_id=?",
                tenantId,
                alertId);
        for (ConditionResult result : results) {
            jdbc.update(
                    """
                    INSERT INTO early_warning_alert_evidence
                        (id,tenant_id,alert_id,condition_id,signal_type,actual_value,
                         threshold_value,comparison_operator,matched,factual_detail,created_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?)
                    """,
                    UUID.randomUUID(),
                    tenantId,
                    alertId,
                    result.conditionId(),
                    result.signalType().name(),
                    result.actual(),
                    result.threshold(),
                    result.operator().name(),
                    result.matched(),
                    result.factualDetail(),
                    timestamp(clock.instant()));
        }
    }

    private UUID activeAlert(UUID tenantId, UUID ruleId, UUID studentId) {
        List<UUID> ids =
                jdbc.query(
                        """
                        SELECT id FROM early_warning_alert
                        WHERE tenant_id=? AND rule_id=? AND student_id=?
                          AND status IN ('OPEN','ACKNOWLEDGED')
                        ORDER BY first_detected_at FETCH FIRST 1 ROW ONLY
                        """,
                        (rs, row) -> rs.getObject(1, UUID.class),
                        tenantId,
                        ruleId,
                        studentId);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private void ensureAlertStatus(UUID tenantId, UUID alertId, Set<String> allowed) {
        List<String> statuses =
                jdbc.query(
                        "SELECT status FROM early_warning_alert WHERE tenant_id=? AND id=?",
                        (rs, row) -> rs.getString(1),
                        tenantId,
                        alertId);
        if (statuses.isEmpty()) throw new IllegalArgumentException("Alerte introuvable");
        if (!allowed.contains(statuses.getFirst())) {
            throw new IllegalArgumentException("Cette transition d’alerte n’est pas autorisée");
        }
    }

    private LocalDate validatedDate(LocalDate requestedDate) {
        LocalDate date = requestedDate == null ? LocalDate.now(clock) : requestedDate;
        LocalDate today = LocalDate.now(clock);
        if (date.isBefore(today.minusYears(5)) || date.isAfter(today.plusDays(1))) {
            throw new IllegalArgumentException(
                    "La date d’évaluation est hors de la période autorisée");
        }
        return date;
    }

    private void validate(RuleCommand command) {
        if (command == null) throw new IllegalArgumentException("La règle est requise");
        String code = required(command.code(), "Le code est requis").toUpperCase(Locale.ROOT);
        if (!CODE.matcher(code).matches())
            throw new IllegalArgumentException("Le code de règle est invalide");
        ensureFactual(required(command.name(), "Le nom est requis"));
        ensureFactual(required(command.description(), "La description est requise"));
        if (!SEVERITIES.contains(command.severity()))
            throw new IllegalArgumentException("Sévérité invalide");
        try {
            MatchMode.valueOf(command.matchMode());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Mode de correspondance invalide");
        }
        if (command.conditions() == null
                || command.conditions().isEmpty()
                || command.conditions().size() > 7) {
            throw new IllegalArgumentException("Une règle exige de une à sept conditions");
        }
        Set<String> signals = new java.util.HashSet<>();
        for (ConditionCommand condition : command.conditions()) {
            if (condition == null
                    || condition.threshold() == null
                    || condition.threshold().signum() < 0) {
                throw new IllegalArgumentException("Chaque seuil doit être positif ou nul");
            }
            try {
                SignalType.valueOf(condition.signalType());
                Operator.valueOf(condition.operator());
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("Signal ou opérateur invalide");
            }
            if (!signals.add(condition.signalType())) {
                throw new IllegalArgumentException(
                        "Un signal ne peut apparaître qu’une fois par règle");
            }
        }
    }

    private static void ensureFactual(String text) {
        String normalized =
                Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "");
        if (PROHIBITED_TERMS.stream().anyMatch(normalized::contains)) {
            throw new IllegalArgumentException(
                    "Le texte doit rester factuel et ne peut contenir de diagnostic psychologique"
                            + " ou médical");
        }
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return clean(value);
    }

    private static String clean(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private static String summary(List<ConditionResult> results) {
        return results.stream()
                .filter(ConditionResult::matched)
                .map(
                        result ->
                                signalLabel(result.signalType().name())
                                        + " : "
                                        + result.actual().stripTrailingZeros().toPlainString()
                                        + " (seuil "
                                        + operatorLabel(result.operator().name())
                                        + " "
                                        + result.threshold().stripTrailingZeros().toPlainString()
                                        + ")")
                .collect(Collectors.joining(" ; "));
    }

    private static List<SignalDefinition> signalDefinitions() {
        return List.of(
                new SignalDefinition("ABSENCE_RATE", "Absences", "%", "attendance"),
                new SignalDefinition("LATE_COUNT", "Retards", "nombre", "attendance"),
                new SignalDefinition("AVERAGE_GRADE", "Moyenne", "/20", "grading"),
                new SignalDefinition("GRADE_DROP", "Baisse des notes", "points", "grading"),
                new SignalDefinition("INCIDENT_COUNT", "Incidents", "nombre", "attendance"),
                new SignalDefinition("UNPAID_AMOUNT", "Impayés", "MAD", "billing"),
                new SignalDefinition(
                        "MISSING_DOCUMENT_COUNT", "Documents manquants", "nombre", "documents"));
    }

    private static String signalLabel(String code) {
        return signalDefinitions().stream()
                .filter(item -> item.code().equals(code))
                .map(SignalDefinition::label)
                .findFirst()
                .orElse(code);
    }

    private static String operatorLabel(String operator) {
        return switch (operator) {
            case "GT" -> ">";
            case "GTE" -> "≥";
            case "LT" -> "<";
            case "LTE" -> "≤";
            default -> "=";
        };
    }

    private static int severityRank(String severity) {
        return switch (severity) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    private SignalRow signalRow(ResultSet rs) throws SQLException {
        return new SignalRow(
                rs.getObject("student_id", UUID.class),
                rs.getString("identifier_value"),
                rs.getString("first_name") + " " + rs.getString("last_name"),
                rs.getString("classroom_name"),
                rs.getObject("measured_on", LocalDate.class),
                rs.getString("signal_type"),
                rs.getBigDecimal("numeric_value"),
                rs.getString("unit_code"),
                rs.getInt("evidence_count"),
                rs.getString("source_module"),
                rs.getString("factual_detail"));
    }

    private AlertView alertRow(ResultSet rs, Map<UUID, List<EvidenceView>> evidence)
            throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        return new AlertView(
                id,
                rs.getObject("rule_id", UUID.class),
                rs.getString("rule_code"),
                rs.getString("rule_name"),
                rs.getObject("student_id", UUID.class),
                rs.getString("identifier_value"),
                rs.getString("first_name") + " " + rs.getString("last_name"),
                rs.getString("status"),
                rs.getString("severity"),
                rs.getBigDecimal("score"),
                rs.getString("factual_summary"),
                instant(rs, "first_detected_at"),
                instant(rs, "last_detected_at"),
                instant(rs, "acknowledged_at"),
                rs.getString("acknowledgement_note"),
                instant(rs, "resolved_at"),
                rs.getString("resolution_note"),
                evidence.getOrDefault(id, List.of()));
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private void recordAudit(
            SchoolPrincipal actor,
            String action,
            String resourceType,
            UUID resourceId,
            String ipAddress,
            Object after) {
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                action,
                resourceType,
                resourceId.toString(),
                ipAddress,
                Map.of("module", "early-warning"),
                null,
                after);
    }

    public record Dashboard(
            Instant generatedAt,
            LocalDate asOf,
            EthicalPolicy ethicalPolicy,
            Statistics statistics,
            List<SignalDefinition> signalDefinitions,
            List<RuleView> rules,
            List<StudentView> students,
            List<AlertView> alerts,
            List<EvaluationView> evaluations) {}

    public record EthicalPolicy(String purpose, String prohibition, boolean factualOnly) {}

    public record Statistics(
            long activeRules,
            long studentsObserved,
            long activeAlerts,
            long acknowledgedAlerts,
            long highAlerts) {}

    public record SignalDefinition(String code, String label, String unit, String sourceModule) {}

    public record RuleView(
            UUID id,
            String code,
            String name,
            String description,
            String severity,
            String matchMode,
            boolean active,
            List<ConditionView> conditions,
            Instant createdAt,
            Instant updatedAt) {}

    public record ConditionView(
            UUID id,
            UUID ruleId,
            String signalType,
            String signalLabel,
            String operator,
            BigDecimal threshold,
            int order) {}

    public record StudentView(
            UUID id,
            String identifier,
            String displayName,
            String classroomName,
            List<SignalView> signals,
            int activeAlertCount,
            String highestSeverity) {}

    public record SignalView(
            String signalType,
            String label,
            LocalDate measuredOn,
            BigDecimal value,
            String unit,
            int evidenceCount,
            String sourceModule,
            String factualDetail) {}

    public record AlertView(
            UUID id,
            UUID ruleId,
            String ruleCode,
            String ruleName,
            UUID studentId,
            String studentIdentifier,
            String studentName,
            String status,
            String severity,
            BigDecimal score,
            String factualSummary,
            Instant firstDetectedAt,
            Instant lastDetectedAt,
            Instant acknowledgedAt,
            String acknowledgementNote,
            Instant resolvedAt,
            String resolutionNote,
            List<EvidenceView> evidence) {
        public boolean active() {
            return "OPEN".equals(status) || "ACKNOWLEDGED".equals(status);
        }
    }

    public record EvidenceView(
            UUID id,
            UUID alertId,
            String signalType,
            String signalLabel,
            BigDecimal actual,
            BigDecimal threshold,
            String operator,
            boolean matched,
            String factualDetail) {}

    public record EvaluationView(
            UUID id,
            LocalDate evaluatedOn,
            int rulesEvaluated,
            int studentsEvaluated,
            int alertsCreated,
            int alertsRefreshed,
            String status,
            Instant completedAt) {}

    public record RuleCommand(
            String code,
            String name,
            String description,
            String severity,
            String matchMode,
            boolean active,
            List<ConditionCommand> conditions) {}

    public record ConditionCommand(String signalType, String operator, BigDecimal threshold) {}

    public record AlertAction(String note) {}

    record EvaluationRun(
            UUID id,
            LocalDate evaluatedOn,
            int rulesEvaluated,
            int studentsEvaluated,
            int alertsCreated,
            int alertsRefreshed,
            Instant completedAt) {}

    private record SignalRow(
            UUID studentId,
            String identifier,
            String displayName,
            String classroomName,
            LocalDate measuredOn,
            String signalType,
            BigDecimal value,
            String unit,
            int evidenceCount,
            String sourceModule,
            String factualDetail) {
        SignalView view() {
            return new SignalView(
                    signalType,
                    signalLabel(signalType),
                    measuredOn,
                    value,
                    unit,
                    evidenceCount,
                    sourceModule,
                    factualDetail);
        }
    }
}
