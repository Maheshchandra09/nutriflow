package com.nutriflow.api.mealplan;

import java.time.LocalDate;
import java.util.List;

public record MealPlanDay(LocalDate date, List<String> recipeIds) {

    public MealPlanDay {
        recipeIds = List.copyOf(recipeIds);
    }
}
