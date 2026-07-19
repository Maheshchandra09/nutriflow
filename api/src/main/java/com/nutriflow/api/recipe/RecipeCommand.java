package com.nutriflow.api.recipe;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RecipeCommand(
        String name,
        String description,
        DietType dietType,
        List<String> prepSteps,
        UUID createdBy,
        List<Ingredient> ingredients,
        Macros macros,
        Map<String, Object> dietAttributes) {}
