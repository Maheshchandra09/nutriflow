package com.nutriflow.api.recipe;

import java.math.BigDecimal;

public record Ingredient(String name, BigDecimal quantity, String unit) {}
