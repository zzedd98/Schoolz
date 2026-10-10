package ma.schoolz.erp.earlywarning;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ma.schoolz.erp.earlywarning.application.EarlyWarningRuleEngine;
import ma.schoolz.erp.earlywarning.application.EarlyWarningRuleEngine.Condition;
import ma.schoolz.erp.earlywarning.application.EarlyWarningRuleEngine.MatchMode;
import ma.schoolz.erp.earlywarning.application.EarlyWarningRuleEngine.Operator;
import ma.schoolz.erp.earlywarning.application.EarlyWarningRuleEngine.Rule;
import ma.schoolz.erp.earlywarning.application.EarlyWarningRuleEngine.Signal;
import ma.schoolz.erp.earlywarning.application.EarlyWarningRuleEngine.SignalType;
import org.junit.jupiter.api.Test;

class EarlyWarningRuleEngineTests {
    private final EarlyWarningRuleEngine engine = new EarlyWarningRuleEngine();

    @Test
    void allModeRequiresAbsenteeismAndAverageConditionsTogether() {
        Rule rule =
                new Rule(
                        MatchMode.ALL,
                        List.of(
                                condition(SignalType.ABSENCE_RATE, Operator.GT, "10"),
                                condition(SignalType.AVERAGE_GRADE, Operator.LT, "10")));

        var result =
                engine.evaluate(
                        rule,
                        Map.of(
                                SignalType.ABSENCE_RATE,
                                signal("12.5"),
                                SignalType.AVERAGE_GRADE,
                                signal("9.2")));

        assertThat(result.triggered()).isTrue();
        assertThat(result.score()).isEqualByComparingTo("100.00");
        assertThat(result.conditions()).allMatch(EarlyWarningRuleEngine.ConditionResult::matched);
    }

    @Test
    void allModeDoesNotTriggerOnOneIsolatedSignal() {
        Rule rule =
                new Rule(
                        MatchMode.ALL,
                        List.of(
                                condition(SignalType.ABSENCE_RATE, Operator.GT, "10"),
                                condition(SignalType.AVERAGE_GRADE, Operator.LT, "10")));

        var result =
                engine.evaluate(
                        rule,
                        Map.of(
                                SignalType.ABSENCE_RATE,
                                signal("12.5"),
                                SignalType.AVERAGE_GRADE,
                                signal("14")));

        assertThat(result.triggered()).isFalse();
        assertThat(result.score()).isEqualByComparingTo("50.00");
    }

    @Test
    void anyModeTriggersAsSoonAsOneFactualConditionMatches() {
        Rule rule =
                new Rule(
                        MatchMode.ANY,
                        List.of(
                                condition(SignalType.LATE_COUNT, Operator.GTE, "3"),
                                condition(SignalType.INCIDENT_COUNT, Operator.GTE, "2")));

        var result =
                engine.evaluate(
                        rule,
                        Map.of(
                                SignalType.LATE_COUNT,
                                signal("3"),
                                SignalType.INCIDENT_COUNT,
                                signal("0")));

        assertThat(result.triggered()).isTrue();
    }

    @Test
    void aMissingMeasurementNeverCreatesAFalsePositiveForPositiveThresholds() {
        Rule rule =
                new Rule(
                        MatchMode.ALL,
                        List.of(condition(SignalType.UNPAID_AMOUNT, Operator.GT, "0")));

        var result = engine.evaluate(rule, Map.of());

        assertThat(result.triggered()).isFalse();
        assertThat(result.conditions().getFirst().factualDetail())
                .isEqualTo("Aucune mesure disponible");
    }

    private static Condition condition(SignalType type, Operator operator, String threshold) {
        return new Condition(UUID.randomUUID(), type, operator, new BigDecimal(threshold));
    }

    private static Signal signal(String value) {
        return new Signal(new BigDecimal(value), "Mesure factuelle de test");
    }
}
