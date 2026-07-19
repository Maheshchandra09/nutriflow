package com.nutriflow.api.recipe;

import java.math.BigDecimal;

public record Macros(
        Integer calories,
        BigDecimal proteinGrams,
        BigDecimal carbohydrateGrams,
        BigDecimal fatGrams) {}
