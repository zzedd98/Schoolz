package ma.schoolz.erp.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import ma.schoolz.erp.workflow.application.WorkflowRuleEngine;
import ma.schoolz.erp.workflow.application.WorkflowRuleEngine.Condition;
import ma.schoolz.erp.workflow.application.WorkflowRuleEngine.Operator;
import ma.schoolz.erp.workflow.application.WorkflowRuleEngine.ValueType;
import org.junit.jupiter.api.Test;

class WorkflowRuleEngineTests {
    private final WorkflowRuleEngine engine = new WorkflowRuleEngine();

    @Test
    void allConditionsMustMatch() {
        var result =
                engine.evaluate(
                        List.of(
                                new Condition("days", Operator.GT, "7", ValueType.NUMBER),
                                new Condition("status", Operator.EQ, "OVERDUE", ValueType.STRING)),
                        Map.of("days", 12, "status", "overdue"));

        assertThat(result.matched()).isTrue();
        assertThat(result.conditions()).allMatch(WorkflowRuleEngine.ConditionResult::matched);
    }

    @Test
    void nestedFieldsAreResolved() {
        var result =
                engine.evaluate(
                        List.of(
                                new Condition(
                                        "student.absence.unjustified",
                                        Operator.EQ,
                                        "true",
                                        ValueType.BOOLEAN)),
                        Map.of("student", Map.of("absence", Map.of("unjustified", true))));

        assertThat(result.matched()).isTrue();
    }

    @Test
    void missingValueFailsExceptForNegatedExistenceNotBeingSupported() {
        var result =
                engine.evaluate(
                        List.of(new Condition("missing", Operator.GT, "0", ValueType.NUMBER)),
                        Map.of("days", 12));

        assertThat(result.matched()).isFalse();
        assertThat(result.conditions()).first().extracting("actualValue").isNull();
    }

    @Test
    void membershipAndExistenceAreSupported() {
        var result =
                engine.evaluate(
                        List.of(
                                new Condition(
                                        "channel", Operator.IN, "EMAIL, PUSH", ValueType.STRING),
                                new Condition(
                                        "reference", Operator.EXISTS, null, ValueType.STRING)),
                        Map.of("channel", "push", "reference", "FAC-42"));

        assertThat(result.matched()).isTrue();
    }
}
