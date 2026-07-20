package com.nutriflow.worker;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

final class ProcessingModels {

    private ProcessingModels() {}

    enum ClaimResult {
        PROCESS,
        DUPLICATE_TERMINAL
    }

    enum CompletionStatus {
        PROCESSED,
        FLAGGED
    }

    record MealPlanWork(UUID id, UUID clientId, List<String> recipeIds) {
        MealPlanWork {
            recipeIds = List.copyOf(recipeIds);
        }
    }

    record MacroValues(
            int calories,
            BigDecimal proteinGrams,
            BigDecimal carbohydrateGrams,
            BigDecimal fatGrams) {

        static MacroValues zero() {
            return new MacroValues(
                    0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        MacroValues add(MacroValues other) {
            return new MacroValues(
                    Math.addExact(calories, other.calories),
                    proteinGrams.add(other.proteinGrams),
                    carbohydrateGrams.add(other.carbohydrateGrams),
                    fatGrams.add(other.fatGrams));
        }
    }

    record IngredientValue(String name, BigDecimal quantity, String unit) {}

    record RecipeData(
            String id, MacroValues macros, List<IngredientValue> ingredients) {
        RecipeData {
            ingredients = List.copyOf(ingredients);
        }
    }

    record NutritionTarget(
            int dailyCalories,
            BigDecimal proteinGrams,
            BigDecimal carbohydrateGrams,
            BigDecimal fatGrams) {}

    record TargetComparisonValues(
            BigDecimal caloriePercentageDifference,
            BigDecimal proteinPercentageDifference,
            BigDecimal carbohydratePercentageDifference,
            BigDecimal fatPercentageDifference) {

        boolean outsideTolerance(BigDecimal tolerance) {
            return caloriePercentageDifference.abs().compareTo(tolerance) > 0
                    || proteinPercentageDifference.abs().compareTo(tolerance) > 0
                    || carbohydratePercentageDifference.abs().compareTo(tolerance) > 0
                    || fatPercentageDifference.abs().compareTo(tolerance) > 0;
        }
    }

    record ProcessingResult(
            MacroValues weeklyTotals,
            List<IngredientValue> groceryList,
            TargetComparisonValues targetComparison,
            CompletionStatus status) {
        ProcessingResult {
            groceryList = List.copyOf(groceryList);
        }
    }
}
