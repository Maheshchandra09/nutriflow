package com.nutriflow.worker;

import com.nutriflow.contracts.MealPlanSubmittedV1;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

final class MealPlanProcessor {

    private final MealPlanProcessingStore mealPlanStore;
    private final RecipeDataStore recipeStore;
    private final NutritionTargetDataStore nutritionTargetStore;
    private final MealPlanCalculator calculator;

    MealPlanProcessor(
            MealPlanProcessingStore mealPlanStore,
            RecipeDataStore recipeStore,
            NutritionTargetDataStore nutritionTargetStore,
            MealPlanCalculator calculator) {
        this.mealPlanStore = mealPlanStore;
        this.recipeStore = recipeStore;
        this.nutritionTargetStore = nutritionTargetStore;
        this.calculator = calculator;
    }

    void process(MealPlanSubmittedV1 event) {
        validate(event);
        if (mealPlanStore.claim(event)
                == ProcessingModels.ClaimResult.DUPLICATE_TERMINAL) {
            return;
        }

        try {
            ProcessingModels.MealPlanWork plan =
                    mealPlanStore.load(event.mealPlanId());
            if (!plan.clientId().equals(event.clientId())) {
                throw new IllegalStateException(
                        "Event client does not match submitted meal plan");
            }
            Set<String> recipeIds = Set.copyOf(plan.recipeIds());
            Map<String, ProcessingModels.RecipeData> recipes =
                    recipeStore.findAllById(recipeIds).stream()
                            .collect(
                                    Collectors.toUnmodifiableMap(
                                            ProcessingModels.RecipeData::id,
                                            Function.identity()));
            ProcessingModels.ProcessingResult result =
                    calculator.calculate(
                            plan,
                            recipes,
                            nutritionTargetStore.findByClientId(plan.clientId()));
            mealPlanStore.complete(event.mealPlanId(), event.eventId(), result);
        } catch (RuntimeException failure) {
            recordFailure(event, failure);
            throw failure;
        }
    }

    private void validate(MealPlanSubmittedV1 event) {
        if (event == null
                || event.eventId() == null
                || event.mealPlanId() == null
                || event.clientId() == null
                || event.occurredAt() == null) {
            throw new IllegalArgumentException("Meal-plan event is incomplete");
        }
        if (event.schemaVersion() != MealPlanSubmittedV1.SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported meal-plan event schema version");
        }
    }

    private void recordFailure(
            MealPlanSubmittedV1 event, RuntimeException processingFailure) {
        try {
            mealPlanStore.fail(
                    event.mealPlanId(),
                    event.eventId(),
                    "Processing failed: "
                            + processingFailure.getClass().getSimpleName());
        } catch (RuntimeException persistenceFailure) {
            processingFailure.addSuppressed(persistenceFailure);
        }
    }
}
