package com.nutriflow.api.mealplan;

import com.nutriflow.api.recipe.Ingredient;
import com.nutriflow.api.recipe.Macros;
import java.util.List;

public record MealPlanResult(Macros weeklyTotals, List<Ingredient> groceryList) {

    public MealPlanResult {
        groceryList = List.copyOf(groceryList);
    }
}
