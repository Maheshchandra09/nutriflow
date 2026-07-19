package com.nutriflow.api.recipe;

import java.util.List;

public record RecipeSearchCriteria(
        DietType dietType, List<RecipeFilter> filters, Integer page, Integer size) {}
