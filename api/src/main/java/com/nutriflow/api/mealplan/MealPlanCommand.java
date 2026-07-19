package com.nutriflow.api.mealplan;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MealPlanCommand(
        UUID clientId,
        UUID nutritionistId,
        LocalDate weekStartDate,
        List<MealPlanDay> days) {}
