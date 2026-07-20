package com.nutriflow.worker;

import static com.nutriflow.worker.ProcessingModels.CompletionStatus.FLAGGED;
import static com.nutriflow.worker.ProcessingModels.CompletionStatus.PROCESSED;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class MealPlanCalculator {

    private static final BigDecimal DAYS_PER_PLAN = BigDecimal.valueOf(7);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TARGET_TOLERANCE_PERCENT = BigDecimal.valueOf(15);

    ProcessingModels.ProcessingResult calculate(
            ProcessingModels.MealPlanWork plan,
            Map<String, ProcessingModels.RecipeData> recipesById,
            Optional<ProcessingModels.NutritionTarget> target) {
        ProcessingModels.MacroValues totals = ProcessingModels.MacroValues.zero();
        Map<IngredientKey, BigDecimal> ingredientTotals = new LinkedHashMap<>();

        for (String recipeId : plan.recipeIds()) {
            ProcessingModels.RecipeData recipe = recipesById.get(recipeId);
            if (recipe == null) {
                throw new IllegalStateException(
                        "Submitted meal plan references unavailable recipe");
            }
            totals = totals.add(recipe.macros());
            for (ProcessingModels.IngredientValue ingredient : recipe.ingredients()) {
                IngredientKey key =
                        new IngredientKey(
                                normalizeName(ingredient.name()),
                                ingredient.unit().trim());
                ingredientTotals.merge(key, ingredient.quantity(), BigDecimal::add);
            }
        }

        List<ProcessingModels.IngredientValue> groceryList =
                ingredientTotals.entrySet().stream()
                        .sorted(
                                Map.Entry.comparingByKey(
                                        Comparator.comparing(IngredientKey::name)
                                                .thenComparing(IngredientKey::unit)))
                        .map(
                                entry ->
                                        new ProcessingModels.IngredientValue(
                                                entry.getKey().name(),
                                                entry.getValue(),
                                                entry.getKey().unit()))
                        .toList();

        ProcessingModels.MacroValues weeklyTotals = totals;
        ProcessingModels.TargetComparisonValues comparison =
                target.map(value -> compare(weeklyTotals, value)).orElse(null);
        ProcessingModels.CompletionStatus status =
                comparison != null
                                && comparison.outsideTolerance(
                                        TARGET_TOLERANCE_PERCENT)
                        ? FLAGGED
                        : PROCESSED;
        return new ProcessingModels.ProcessingResult(
                weeklyTotals, groceryList, comparison, status);
    }

    private ProcessingModels.TargetComparisonValues compare(
            ProcessingModels.MacroValues weeklyTotals,
            ProcessingModels.NutritionTarget target) {
        return new ProcessingModels.TargetComparisonValues(
                percentageDifference(
                        BigDecimal.valueOf(weeklyTotals.calories())
                                .divide(DAYS_PER_PLAN, 8, RoundingMode.HALF_UP),
                        BigDecimal.valueOf(target.dailyCalories())),
                percentageDifference(
                        weeklyTotals.proteinGrams()
                                .divide(DAYS_PER_PLAN, 8, RoundingMode.HALF_UP),
                        target.proteinGrams()),
                percentageDifference(
                        weeklyTotals.carbohydrateGrams()
                                .divide(DAYS_PER_PLAN, 8, RoundingMode.HALF_UP),
                        target.carbohydrateGrams()),
                percentageDifference(
                        weeklyTotals.fatGrams()
                                .divide(DAYS_PER_PLAN, 8, RoundingMode.HALF_UP),
                        target.fatGrams()));
    }

    private BigDecimal percentageDifference(BigDecimal actual, BigDecimal target) {
        if (target.signum() <= 0) {
            throw new IllegalStateException("Nutrition targets must be positive");
        }
        return actual.subtract(target)
                .multiply(HUNDRED)
                .divide(target, 2, RoundingMode.HALF_UP);
    }

    private String normalizeName(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private record IngredientKey(String name, String unit) {}
}
