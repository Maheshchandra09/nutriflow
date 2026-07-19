package com.nutriflow.api.recipe;

import java.util.List;
import java.util.Map;

public record RecipeUpdateCommand(
        String name,
        String description,
        DietType dietType,
        List<String> prepSteps,
        List<Ingredient> ingredients,
        Macros macros,
        Map<String, Object> dietAttributes) {}
