package ma.schoolz.erp.grading.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public final class GradingCalculationEngine {

    public CalculationResult calculate(List<ScoredItem> source, CalculationModel model) {
        if (model == null) throw new IllegalArgumentException("Le modèle de calcul est requis");
        List<ScoredItem> items = validateItems(source);
        if (items.isEmpty()) return new CalculationResult(null, Map.of(), 0);
        return switch (model.strategy()) {
            case WEIGHTED_MEAN -> weightedResult(items, model);
            case CATEGORY_WEIGHTED -> categoryResult(items, model);
        };
    }

    private CalculationResult weightedResult(List<ScoredItem> items, CalculationModel model) {
        List<ScoredItem> retained = dropLowest(items, model.dropLowest());
        BigDecimal average = weightedMean(retained, model.roundingScale());
        return new CalculationResult(
                average, categoryAverages(retained, model.roundingScale()), retained.size());
    }

    private CalculationResult categoryResult(List<ScoredItem> items, CalculationModel model) {
        if (model.categoryWeights() == null || model.categoryWeights().isEmpty()) {
            throw new IllegalArgumentException(
                    "Les poids de catégories sont requis pour CATEGORY_WEIGHTED");
        }
        Map<String, List<ScoredItem>> grouped = new LinkedHashMap<>();
        for (ScoredItem item : items) {
            grouped.computeIfAbsent(item.category(), ignored -> new ArrayList<>()).add(item);
        }
        Map<String, BigDecimal> categoryAverages = new LinkedHashMap<>();
        BigDecimal numerator = BigDecimal.ZERO;
        BigDecimal denominator = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> configured : model.categoryWeights().entrySet()) {
            String category = normalizeCategory(configured.getKey());
            BigDecimal categoryWeight = positive(configured.getValue(), "poids de catégorie");
            List<ScoredItem> values = grouped.getOrDefault(category, List.of());
            if (!values.isEmpty()) {
                List<ScoredItem> retained = dropLowest(values, model.dropLowest());
                BigDecimal average = weightedMean(retained, model.roundingScale() + 4);
                categoryAverages.put(
                        category, average.setScale(model.roundingScale(), RoundingMode.HALF_UP));
                numerator = numerator.add(average.multiply(categoryWeight));
                denominator = denominator.add(categoryWeight);
            } else if (model.missingCategoryMode() == MissingCategoryMode.ZERO) {
                categoryAverages.put(category, BigDecimal.ZERO.setScale(model.roundingScale()));
                denominator = denominator.add(categoryWeight);
            }
        }
        if (denominator.signum() == 0) {
            return new CalculationResult(null, categoryAverages, items.size());
        }
        return new CalculationResult(
                numerator.divide(denominator, model.roundingScale(), RoundingMode.HALF_UP),
                Map.copyOf(categoryAverages),
                items.size());
    }

    private static Map<String, BigDecimal> categoryAverages(
            List<ScoredItem> items, int roundingScale) {
        Map<String, List<ScoredItem>> grouped = new LinkedHashMap<>();
        for (ScoredItem item : items) {
            grouped.computeIfAbsent(item.category(), ignored -> new ArrayList<>()).add(item);
        }
        Map<String, BigDecimal> averages = new LinkedHashMap<>();
        grouped.forEach(
                (category, values) -> averages.put(category, weightedMean(values, roundingScale)));
        return Map.copyOf(averages);
    }

    private static BigDecimal weightedMean(List<ScoredItem> items, int roundingScale) {
        BigDecimal numerator = BigDecimal.ZERO;
        BigDecimal denominator = BigDecimal.ZERO;
        for (ScoredItem item : items) {
            numerator = numerator.add(item.score().multiply(item.weight()));
            denominator = denominator.add(item.weight());
        }
        if (denominator.signum() == 0) throw new IllegalArgumentException("Le poids total est nul");
        return numerator.divide(denominator, roundingScale, RoundingMode.HALF_UP);
    }

    private static List<ScoredItem> dropLowest(List<ScoredItem> source, int dropLowest) {
        if (dropLowest < 0)
            throw new IllegalArgumentException("dropLowest ne peut pas être négatif");
        if (dropLowest == 0 || source.size() <= 1) return List.copyOf(source);
        int removable = Math.min(dropLowest, source.size() - 1);
        List<ScoredItem> sorted = new ArrayList<>(source);
        sorted.sort(Comparator.comparing(ScoredItem::score));
        return List.copyOf(sorted.subList(removable, sorted.size()));
    }

    private static List<ScoredItem> validateItems(List<ScoredItem> source) {
        if (source == null) return List.of();
        List<ScoredItem> result = new ArrayList<>();
        for (ScoredItem item : source) {
            if (item == null || item.score() == null || item.weight() == null) {
                throw new IllegalArgumentException(
                        "Chaque résultat doit avoir une note et un poids");
            }
            if (item.score().signum() < 0) {
                throw new IllegalArgumentException("Une note normalisée ne peut pas être négative");
            }
            result.add(
                    new ScoredItem(
                            normalizeCategory(item.category()),
                            item.score(),
                            positive(item.weight(), "poids")));
        }
        return List.copyOf(result);
    }

    private static BigDecimal positive(BigDecimal value, String label) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("Le " + label + " doit être positif");
        }
        return value;
    }

    private static String normalizeCategory(String value) {
        if (value == null || value.isBlank()) return "DEFAULT";
        return value.strip().toUpperCase(Locale.ROOT);
    }

    public enum Strategy {
        WEIGHTED_MEAN,
        CATEGORY_WEIGHTED
    }

    public enum MissingCategoryMode {
        RENORMALIZE,
        ZERO
    }

    public record ScoredItem(String category, BigDecimal score, BigDecimal weight) {}

    public record CalculationModel(
            Strategy strategy,
            Map<String, BigDecimal> categoryWeights,
            MissingCategoryMode missingCategoryMode,
            int dropLowest,
            int roundingScale) {
        public CalculationModel {
            if (strategy == null) strategy = Strategy.WEIGHTED_MEAN;
            categoryWeights = categoryWeights == null ? Map.of() : Map.copyOf(categoryWeights);
            if (missingCategoryMode == null) missingCategoryMode = MissingCategoryMode.RENORMALIZE;
            if (dropLowest < 0) throw new IllegalArgumentException("dropLowest invalide");
            if (roundingScale < 0 || roundingScale > 4) {
                throw new IllegalArgumentException("La précision doit être comprise entre 0 et 4");
            }
        }
    }

    public record CalculationResult(
            BigDecimal average, Map<String, BigDecimal> categoryAverages, int includedItems) {}
}
