package com.nutriflow.api.mealplan;

import java.time.LocalDate;
import java.util.List;

public record MealPlanDay(LocalDate date, List<PlannedMeal> meals) {

    public MealPlanDay {
        meals = List.copyOf(meals);
    }
}
