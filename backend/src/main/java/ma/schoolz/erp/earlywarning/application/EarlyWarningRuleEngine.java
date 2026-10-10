package ma.schoolz.erp.earlywarning.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

public final class EarlyWarningRuleEngine {
    public Evaluation evaluate(Rule rule, Map<SignalType, Signal> signals) {
        List<ConditionResult> results =
                rule.conditions().stream()
                        .map(condition -> evaluate(condition, signals.get(condition.signalType())))
                        .toList();
        long matched = results.stream().filter(ConditionResult::matched).count();
        boolean triggered =
                switch (rule.matchMode()) {
                    case ALL -> matched == results.size() && !results.isEmpty();
                    case ANY -> matched > 0;
                };
        BigDecimal score =
                results.isEmpty()
                        ? BigDecimal.ZERO
                        : BigDecimal.valueOf(matched)
                                .multiply(BigDecimal.valueOf(100))
                                .divide(
                                        BigDecimal.valueOf(results.size()),
                                        2,
                                        RoundingMode.HALF_UP);
        return new Evaluation(triggered, score, results);
    }

    private ConditionResult evaluate(Condition condition, Signal signal) {
        BigDecimal actual = signal == null ? BigDecimal.ZERO : signal.value();
        int comparison = actual.compareTo(condition.threshold());
        boolean matched =
                switch (condition.operator()) {
                    case GT -> comparison > 0;
                    case GTE -> comparison >= 0;
                    case LT -> comparison < 0;
                    case LTE -> comparison <= 0;
                    case EQ -> comparison == 0;
                };
        return new ConditionResult(
                condition.id(),
                condition.signalType(),
                condition.operator(),
                actual,
                condition.threshold(),
                matched,
                signal == null ? "Aucune mesure disponible" : signal.factualDetail());
    }

    public enum SignalType {
        ABSENCE_RATE,
        LATE_COUNT,
        AVERAGE_GRADE,
        GRADE_DROP,
        INCIDENT_COUNT,
        UNPAID_AMOUNT,
        MISSING_DOCUMENT_COUNT
    }

    public enum Operator {
        GT,
        GTE,
        LT,
        LTE,
        EQ
    }

    public enum MatchMode {
        ALL,
        ANY
    }

    public record Signal(BigDecimal value, String factualDetail) {}

    public record Condition(
            java.util.UUID id, SignalType signalType, Operator operator, BigDecimal threshold) {}

    public record Rule(MatchMode matchMode, List<Condition> conditions) {
        public Rule {
            conditions = List.copyOf(conditions);
        }
    }

    public record ConditionResult(
            java.util.UUID conditionId,
            SignalType signalType,
            Operator operator,
            BigDecimal actual,
            BigDecimal threshold,
            boolean matched,
            String factualDetail) {}

    public record Evaluation(
            boolean triggered, BigDecimal score, List<ConditionResult> conditions) {
        public Evaluation {
            conditions = List.copyOf(conditions);
        }
    }
}
