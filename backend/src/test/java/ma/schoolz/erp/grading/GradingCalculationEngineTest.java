package ma.schoolz.erp.grading;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import ma.schoolz.erp.grading.engine.GradingCalculationEngine;
import ma.schoolz.erp.grading.engine.GradingCalculationEngine.CalculationModel;
import ma.schoolz.erp.grading.engine.GradingCalculationEngine.MissingCategoryMode;
import ma.schoolz.erp.grading.engine.GradingCalculationEngine.ScoredItem;
import ma.schoolz.erp.grading.engine.GradingCalculationEngine.Strategy;
import org.junit.jupiter.api.Test;

class GradingCalculationEngineTest {
    private final GradingCalculationEngine engine = new GradingCalculationEngine();

    @Test
    void supportsDifferentCalculationModelsWithoutKnowingSchoolCycles() {
        List<ScoredItem> scores = List.of(item("CONTINUOUS", "10", "1"), item("EXAM", "18", "1"));

        var simple =
                engine.calculate(
                        scores,
                        new CalculationModel(
                                Strategy.WEIGHTED_MEAN,
                                Map.of(),
                                MissingCategoryMode.RENORMALIZE,
                                0,
                                2));
        var categoryWeighted =
                engine.calculate(
                        scores,
                        new CalculationModel(
                                Strategy.CATEGORY_WEIGHTED,
                                Map.of(
                                        "CONTINUOUS", new BigDecimal("60"),
                                        "EXAM", new BigDecimal("40")),
                                MissingCategoryMode.RENORMALIZE,
                                0,
                                2));

        assertThat(simple.average()).isEqualByComparingTo("14.00");
        assertThat(categoryWeighted.average()).isEqualByComparingTo("13.20");
        assertThat(categoryWeighted.categoryAverages())
                .containsEntry("CONTINUOUS", new BigDecimal("10.00"))
                .containsEntry("EXAM", new BigDecimal("18.00"));
    }

    @Test
    void missingCategoryAndDropLowestAreExplicitConfigurationChoices() {
        List<ScoredItem> scores =
                List.of(item("CONTINUOUS", "8", "1"), item("CONTINUOUS", "12", "1"));
        Map<String, BigDecimal> weights =
                Map.of(
                        "CONTINUOUS", new BigDecimal("60"),
                        "EXAM", new BigDecimal("40"));

        var renormalized =
                engine.calculate(
                        scores,
                        new CalculationModel(
                                Strategy.CATEGORY_WEIGHTED,
                                weights,
                                MissingCategoryMode.RENORMALIZE,
                                1,
                                2));
        var zeroFilled =
                engine.calculate(
                        scores,
                        new CalculationModel(
                                Strategy.CATEGORY_WEIGHTED,
                                weights,
                                MissingCategoryMode.ZERO,
                                1,
                                2));

        assertThat(renormalized.average()).isEqualByComparingTo("12.00");
        assertThat(zeroFilled.average()).isEqualByComparingTo("7.20");
        assertThat(zeroFilled.categoryAverages()).containsEntry("EXAM", new BigDecimal("0.00"));
    }

    private static ScoredItem item(String category, String score, String weight) {
        return new ScoredItem(category, new BigDecimal(score), new BigDecimal(weight));
    }
}
