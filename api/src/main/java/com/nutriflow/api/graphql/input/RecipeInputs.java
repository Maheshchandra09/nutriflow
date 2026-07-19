package com.nutriflow.api.graphql.input;

import com.nutriflow.api.recipe.DietType;
import com.nutriflow.api.recipe.FilterOperator;
import com.nutriflow.api.recipe.Ingredient;
import com.nutriflow.api.recipe.Macros;
import com.nutriflow.api.recipe.RecipeCommand;
import com.nutriflow.api.recipe.RecipeFilter;
import com.nutriflow.api.recipe.RecipeSearchCriteria;
import com.nutriflow.api.recipe.RecipeUpdateCommand;
import com.nutriflow.api.recipe.ReviewCommand;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class RecipeInputs {

    private RecipeInputs() {}

    public record IngredientInput(String name, BigDecimal quantity, String unit) {

        Ingredient toValue() {
            return new Ingredient(name, quantity, unit);
        }
    }

    public record MacrosInput(
            Integer calories,
            BigDecimal proteinGrams,
            BigDecimal carbohydrateGrams,
            BigDecimal fatGrams) {

        Macros toValue() {
            return new Macros(
                    calories, proteinGrams, carbohydrateGrams, fatGrams);
        }
    }

    public record CreateRecipeInput(
            String name,
            String description,
            DietType dietType,
            List<String> prepSteps,
            String createdBy,
            List<IngredientInput> ingredients,
            MacrosInput macros,
            Map<String, Object> attributes) {

        public RecipeCommand toCommand() {
            return new RecipeCommand(
                    name,
                    description,
                    dietType,
                    prepSteps,
                    GraphQlInputSupport.uuid(createdBy, "recipe.createdBy"),
                    ingredients.stream().map(IngredientInput::toValue).toList(),
                    macros.toValue(),
                    attributes);
        }
    }

    public record UpdateRecipeInput(
            String name,
            String description,
            DietType dietType,
            List<String> prepSteps,
            List<IngredientInput> ingredients,
            MacrosInput macros,
            Map<String, Object> attributes) {

        public RecipeUpdateCommand toCommand() {
            return new RecipeUpdateCommand(
                    name,
                    description,
                    dietType,
                    prepSteps,
                    ingredients.stream().map(IngredientInput::toValue).toList(),
                    macros.toValue(),
                    attributes);
        }
    }

    public record ReviewInput(String userId, Integer rating, String comment) {

        public ReviewCommand toCommand() {
            return new ReviewCommand(
                    GraphQlInputSupport.uuid(userId, "review.userId"), rating, comment);
        }
    }

    public record RecipeFilterInput(
            String path, FilterOperator operator, Object value) {

        RecipeFilter toValue() {
            return new RecipeFilter(path, operator, value);
        }
    }

    public record RecipeSearchInput(
            DietType dietType,
            List<RecipeFilterInput> filters,
            Integer page,
            Integer size) {

        public RecipeSearchCriteria toCriteria() {
            return new RecipeSearchCriteria(
                    dietType,
                    filters == null
                            ? List.of()
                            : filters.stream().map(RecipeFilterInput::toValue).toList(),
                    page,
                    size);
        }
    }
}
