package com.nutriflow.api.nutrition;

import java.math.BigDecimal;
import java.util.UUID;

public record SetNutritionTargetCommand(
        UUID clientId,
        Integer dailyCalories,
        BigDecimal proteinGrams,
        BigDecimal carbohydrateGrams,
        BigDecimal fatGrams) {}
