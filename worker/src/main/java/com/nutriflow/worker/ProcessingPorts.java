package com.nutriflow.worker;

import com.nutriflow.contracts.MealPlanSubmittedV1;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

interface MealPlanProcessingStore {

    ProcessingModels.ClaimResult claim(MealPlanSubmittedV1 event);

    ProcessingModels.MealPlanWork load(UUID mealPlanId);

    void complete(
            UUID mealPlanId,
            UUID eventId,
            ProcessingModels.ProcessingResult result);

    void fail(UUID mealPlanId, UUID eventId, String sanitizedError);
}

interface RecipeDataStore {

    List<ProcessingModels.RecipeData> findAllById(Set<String> recipeIds);
}

interface NutritionTargetDataStore {

    Optional<ProcessingModels.NutritionTarget> findByClientId(UUID clientId);
}
