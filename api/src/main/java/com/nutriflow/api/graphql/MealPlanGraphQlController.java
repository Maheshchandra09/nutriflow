package com.nutriflow.api.graphql;

import com.nutriflow.api.graphql.input.MealPlanInputs.MealPlanInput;
import com.nutriflow.api.graphql.input.MealPlanInputs.MealPlanScheduleInput;
import com.nutriflow.api.mealplan.MealPlanDocument;
import com.nutriflow.api.mealplan.MealPlanService;
import com.nutriflow.api.mealplan.PlannedMeal;
import com.nutriflow.api.recipe.RecipeDocument;
import com.nutriflow.api.recipe.RecipeRepository;
import com.nutriflow.api.recipe.RecipeService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class MealPlanGraphQlController {

    private final MealPlanService mealPlanService;
    private final RecipeService recipeService;
    private final RecipeRepository recipeRepository;

    @QueryMapping
    public MealPlanDocument mealPlan(@Argument String id) {
        return mealPlanService.get(id);
    }

    @MutationMapping
    public MealPlanDocument createMealPlan(@Argument MealPlanInput input) {
        return mealPlanService.createDraft(input.toCommand());
    }

    @MutationMapping
    public MealPlanDocument updateMealPlan(
            @Argument String id, @Argument MealPlanScheduleInput input) {
        return mealPlanService.updateDraft(
                id, input.weekStartDate(), input.mappedDays());
    }

    @MutationMapping
    public MealPlanDocument submitMealPlan(@Argument String id) {
        return mealPlanService.submit(id);
    }

    @SchemaMapping(typeName = "MealPlan", field = "swapSuggestions")
    public List<RecipeDocument> swapSuggestions(
            MealPlanDocument mealPlan, @Argument String recipeId, @Argument int size) {
        return recipeService.swapSuggestions(recipeId, size);
    }

    @BatchMapping(typeName = "PlannedMeal", field = "recipe")
    public Map<PlannedMeal, RecipeDocument> recipes(List<PlannedMeal> meals) {
        Map<String, RecipeDocument> recipesById =
                recipeRepository.findAllById(
                                meals.stream().map(PlannedMeal::recipeId).distinct().toList())
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        RecipeDocument::getId, Function.identity()));
        Map<PlannedMeal, RecipeDocument> result = new HashMap<>();
        meals.forEach(meal -> result.put(meal, recipesById.get(meal.recipeId())));
        return result;
    }
}
