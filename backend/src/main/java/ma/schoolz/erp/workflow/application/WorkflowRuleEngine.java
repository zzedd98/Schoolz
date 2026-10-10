package ma.schoolz.erp.workflow.application;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class WorkflowRuleEngine {

    public Evaluation evaluate(List<Condition> conditions, Map<String, Object> payload) {
        List<ConditionResult> results =
                conditions.stream().map(condition -> evaluate(condition, payload)).toList();
        return new Evaluation(results.stream().allMatch(ConditionResult::matched), results);
    }

    private ConditionResult evaluate(Condition condition, Map<String, Object> payload) {
        Object actual = resolve(payload, condition.fieldPath());
        boolean matched = compare(actual, condition);
        return new ConditionResult(
                condition.fieldPath(),
                condition.operator(),
                actual == null ? null : String.valueOf(actual),
                condition.expectedValue(),
                matched);
    }

    private Object resolve(Map<String, Object> payload, String path) {
        Object current = payload;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) return null;
            current = map.get(segment);
        }
        return current;
    }

    private boolean compare(Object actual, Condition condition) {
        if (condition.operator() == Operator.EXISTS) return actual != null;
        if (actual == null) return false;
        if (condition.operator() == Operator.IN) {
            return List.of(condition.expectedValue().split(",")).stream()
                    .map(String::strip)
                    .anyMatch(candidate -> candidate.equalsIgnoreCase(String.valueOf(actual)));
        }
        int comparison = compareValues(actual, condition.expectedValue(), condition.valueType());
        return switch (condition.operator()) {
            case EQ -> comparison == 0;
            case NE -> comparison != 0;
            case GT -> comparison > 0;
            case GTE -> comparison >= 0;
            case LT -> comparison < 0;
            case LTE -> comparison <= 0;
            case EXISTS, IN -> throw new IllegalStateException("Operator already handled");
        };
    }

    private int compareValues(Object actual, String expected, ValueType type) {
        return switch (type) {
            case NUMBER ->
                    new BigDecimal(String.valueOf(actual)).compareTo(new BigDecimal(expected));
            case BOOLEAN ->
                    Boolean.compare(
                            Boolean.parseBoolean(String.valueOf(actual)),
                            Boolean.parseBoolean(expected));
            case STRING -> String.valueOf(actual).compareToIgnoreCase(expected);
        };
    }

    public enum Operator {
        EQ,
        NE,
        GT,
        GTE,
        LT,
        LTE,
        EXISTS,
        IN
    }

    public enum ValueType {
        STRING,
        NUMBER,
        BOOLEAN
    }

    public record Condition(
            String fieldPath, Operator operator, String expectedValue, ValueType valueType) {}

    public record ConditionResult(
            String fieldPath,
            Operator operator,
            String actualValue,
            String expectedValue,
            boolean matched) {}

    public record Evaluation(boolean matched, Collection<ConditionResult> conditions) {}
}
