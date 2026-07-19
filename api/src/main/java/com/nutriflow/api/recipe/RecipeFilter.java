package com.nutriflow.api.recipe;

public record RecipeFilter(String path, FilterOperator operator, Object value) {}
