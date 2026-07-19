package com.nutriflow.api.mealplan;

import java.math.BigDecimal;

public record TargetComparison(
        BigDecimal caloriePercentageDifference,
        BigDecimal proteinPercentageDifference,
        BigDecimal carbohydratePercentageDifference,
        BigDecimal fatPercentageDifference) {}
