package com.nutriflow.api.graphql;

import com.nutriflow.api.graphql.input.RecipeInputs.CreateRecipeInput;
import com.nutriflow.api.graphql.input.RecipeInputs.RecipeSearchInput;
import com.nutriflow.api.graphql.input.RecipeInputs.ReviewInput;
import com.nutriflow.api.graphql.input.RecipeInputs.UpdateRecipeInput;
import com.nutriflow.api.recipe.RecipeDocument;
import com.nutriflow.api.recipe.RecipeSearchCriteria;
import com.nutriflow.api.recipe.RecipeSearchService;
import com.nutriflow.api.recipe.RecipeService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class RecipeGraphQlController {

    private final RecipeService recipeService;
    private final RecipeSearchService recipeSearchService;

    @QueryMapping
    public RecipeDocument recipe(@Argument String id) {
        return recipeService.getActive(id);
    }

    @QueryMapping
    public List<RecipeDocument> searchRecipes(@Argument RecipeSearchInput input) {
        RecipeSearchCriteria criteria =
                input == null
                        ? new RecipeSearchCriteria(null, List.of(), 0, 20)
                        : input.toCriteria();
        return recipeSearchService.search(criteria);
    }

    @MutationMapping
    public RecipeDocument createRecipe(@Argument CreateRecipeInput input) {
        return recipeService.create(input.toCommand());
    }

    @MutationMapping
    public RecipeDocument updateRecipe(
            @Argument String id, @Argument UpdateRecipeInput input) {
        return recipeService.update(id, input.toCommand());
    }

    @MutationMapping
    public RecipeDocument deleteRecipe(@Argument String id) {
        return recipeService.softDelete(id);
    }

    @MutationMapping
    public RecipeDocument addReview(
            @Argument String recipeId, @Argument ReviewInput input) {
        return recipeService.addReview(recipeId, input.toCommand());
    }

    @SchemaMapping(typeName = "Recipe", field = "attributes")
    public Map<String, Object> attributes(RecipeDocument recipe) {
        return recipe.getDietAttributes();
    }
}
