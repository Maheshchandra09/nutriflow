package com.nutriflow.api.mealplan;

import com.nutriflow.api.recipe.MealType;

public record PlannedMeal(String recipeId, MealType mealType) {}
