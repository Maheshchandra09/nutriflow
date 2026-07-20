package com.nutriflow.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MealPlanCalculatorTest {

    private final MealPlanCalculator calculator = new MealPlanCalculator();

    @Test
    void aggregatesEveryMealOccurrenceAndOnlyMatchingNameUnitPairs() {
        ProcessingModels.MealPlanWork plan =
                new ProcessingModels.MealPlanWork(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        List.of("recipe-a", "recipe-a", "recipe-b"));
        Map<String, ProcessingModels.RecipeData> recipes =
                Map.of(
                        "recipe-a",
                        recipe(
                                "recipe-a",
                                100,
                                "10",
                                "20",
                                "5",
                                List.of(ingredient(" Avocado ", "1", "piece"))),
                        "recipe-b",
                        recipe(
                                "recipe-b",
                                200,
                                "15",
                                "30",
                                "10",
                                List.of(
                                        ingredient("avocado", "2", "piece"),
                                        ingredient("AVOCADO", "100", "g"))));

        ProcessingModels.ProcessingResult result =
                calculator.calculate(plan, recipes, Optional.empty());

        assertEquals(400, result.weeklyTotals().calories());
        assertEquals(new BigDecimal("35"), result.weeklyTotals().proteinGrams());
        assertEquals(
                List.of(
                        ingredient("avocado", "100", "g"),
                        ingredient("avocado", "4", "piece")),
                result.groceryList());
        assertNull(result.targetComparison());
        assertEquals(
                ProcessingModels.CompletionStatus.PROCESSED, result.status());
    }

    @Test
    void flagsOnlyWhenDailyAverageIsOutsideInclusiveFifteenPercentBand() {
        UUID planId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        ProcessingModels.NutritionTarget target =
                new ProcessingModels.NutritionTarget(
                        100,
                        new BigDecimal("10"),
                        new BigDecimal("20"),
                        new BigDecimal("5"));

        ProcessingModels.ProcessingResult boundary =
                calculator.calculate(
                        sevenMealPlan(planId, clientId, "boundary"),
                        Map.of(
                                "boundary",
                                recipe(
                                        "boundary",
                                        115,
                                        "10",
                                        "20",
                                        "5",
                                        List.of())),
                        Optional.of(target));
        ProcessingModels.ProcessingResult outside =
                calculator.calculate(
                        sevenMealPlan(planId, clientId, "outside"),
                        Map.of(
                                "outside",
                                recipe(
                                        "outside",
                                        116,
                                        "10",
                                        "20",
                                        "5",
                                        List.of())),
                        Optional.of(target));

        assertEquals(new BigDecimal("15.00"),
                boundary.targetComparison().caloriePercentageDifference());
        assertEquals(
                ProcessingModels.CompletionStatus.PROCESSED,
                boundary.status());
        assertEquals(
                ProcessingModels.CompletionStatus.FLAGGED, outside.status());
    }

    private ProcessingModels.MealPlanWork sevenMealPlan(
            UUID planId, UUID clientId, String recipeId) {
        return new ProcessingModels.MealPlanWork(
                planId,
                clientId,
                java.util.stream.IntStream.range(0, 7)
                        .mapToObj(ignored -> recipeId)
                        .toList());
    }

    private ProcessingModels.RecipeData recipe(
            String id,
            int calories,
            String protein,
            String carbs,
            String fat,
            List<ProcessingModels.IngredientValue> ingredients) {
        return new ProcessingModels.RecipeData(
                id,
                new ProcessingModels.MacroValues(
                        calories,
                        new BigDecimal(protein),
                        new BigDecimal(carbs),
                        new BigDecimal(fat)),
                ingredients);
    }

    private ProcessingModels.IngredientValue ingredient(
            String name, String quantity, String unit) {
        return new ProcessingModels.IngredientValue(
                name, new BigDecimal(quantity), unit);
    }
}
