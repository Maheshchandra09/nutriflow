package com.nutriflow.api.graphql;

import static com.nutriflow.api.common.DomainErrors.notFound;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nutriflow.api.recipe.DietType;
import com.nutriflow.api.recipe.Ingredient;
import com.nutriflow.api.recipe.Macros;
import com.nutriflow.api.recipe.RecipeCommand;
import com.nutriflow.api.recipe.RecipeDocument;
import com.nutriflow.api.recipe.RecipeSearchCriteria;
import com.nutriflow.api.recipe.RecipeSearchService;
import com.nutriflow.api.recipe.RecipeService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@GraphQlTest(RecipeGraphQlController.class)
@Import({GraphQlScalarConfiguration.class, DomainExceptionResolver.class})
class RecipeGraphQlControllerTest {

    @Autowired private GraphQlTester graphQlTester;

    @MockitoBean private RecipeService recipeService;
    @MockitoBean private RecipeSearchService recipeSearchService;

    @Test
    void createRecipeMapsJsonAttributesIntoCommand() {
        UUID creatorId = UUID.randomUUID();
        RecipeDocument recipe = recipe("recipe-1", creatorId);
        when(recipeService.create(any())).thenReturn(recipe);

        graphQlTester
                .document(
                        """
                        mutation CreateRecipe($input: CreateRecipeInput!) {
                          createRecipe(input: $input) {
                            id
                            name
                            attributes
                            macros { calories proteinGrams }
                          }
                        }
                        """)
                .variable(
                        "input",
                        Map.of(
                                "name", "Keto bowl",
                                "description", "Dinner",
                                "dietType", "KETO",
                                "prepSteps", List.of("Cook"),
                                "createdBy", creatorId.toString(),
                                "ingredients",
                                        List.of(
                                                Map.of(
                                                        "name", "Avocado",
                                                        "quantity", 1.0,
                                                        "unit", "piece")),
                                "macros",
                                        Map.of(
                                                "calories", 400,
                                                "proteinGrams", 20.0,
                                                "carbohydrateGrams", 10.0,
                                                "fatGrams", 30.0),
                                "attributes",
                                        Map.of(
                                                "netCarbsG", 8,
                                                "fatG", 30,
                                                "proteinG", 20,
                                                "ketoRatio", 1.5)))
                .execute()
                .path("createRecipe.attributes.netCarbsG")
                .entity(Integer.class)
                .isEqualTo(8);

        ArgumentCaptor<RecipeCommand> command =
                ArgumentCaptor.forClass(RecipeCommand.class);
        verify(recipeService).create(command.capture());
        assertThat(command.getValue().createdBy()).isEqualTo(creatorId);
        assertThat(command.getValue().ingredients()).hasSize(1);
    }

    @Test
    void mapsDomainFailuresToTypedGraphQlErrors() {
        when(recipeService.getActive("missing"))
                .thenThrow(notFound("recipe", "missing"));

        graphQlTester
                .document("{ recipe(id: \"missing\") { id } }")
                .execute()
                .errors()
                .satisfy(
                        errors -> {
                            assertThat(errors).hasSize(1);
                            assertThat(errors.getFirst().getMessage())
                                    .doesNotContain("DomainException");
                            assertThat(errors.getFirst().getExtensions())
                                    .containsEntry("code", "NOT_FOUND")
                                    .containsEntry("fieldPath", "recipe.id");
                        });
    }

    @Test
    void searchRecipeBindsWhitelistedFilterShapeAndPagination() {
        when(recipeSearchService.search(any())).thenReturn(List.of());

        graphQlTester
                .document(
                        """
                        query Search($input: RecipeSearchInput) {
                          searchRecipes(input: $input) { id }
                        }
                        """)
                .variable(
                        "input",
                        Map.of(
                                "dietType", "DIABETIC_FRIENDLY",
                                "filters",
                                        List.of(
                                                Map.of(
                                                        "path",
                                                                "attributes.glycemicIndex",
                                                        "operator", "LT",
                                                        "value", 50)),
                                "page", 1,
                                "size", 10))
                .execute()
                .path("searchRecipes")
                .entityList(Object.class)
                .hasSize(0);

        ArgumentCaptor<RecipeSearchCriteria> criteria =
                ArgumentCaptor.forClass(RecipeSearchCriteria.class);
        verify(recipeSearchService).search(criteria.capture());
        assertThat(criteria.getValue().dietType())
                .isEqualTo(DietType.DIABETIC_FRIENDLY);
        assertThat(criteria.getValue().filters()).hasSize(1);
        assertThat(criteria.getValue().page()).isEqualTo(1);
        assertThat(criteria.getValue().size()).isEqualTo(10);
    }

    private RecipeDocument recipe(String id, UUID creatorId) {
        return new RecipeDocument(
                id,
                "Keto bowl",
                "Dinner",
                DietType.KETO,
                List.of("Cook"),
                creatorId,
                List.of(new Ingredient("Avocado", BigDecimal.ONE, "piece")),
                new Macros(
                        400,
                        new BigDecimal("20"),
                        new BigDecimal("10"),
                        new BigDecimal("30")),
                Map.of(
                        "netCarbsG", 8,
                        "fatG", 30,
                        "proteinG", 20,
                        "ketoRatio", 1.5));
    }
}
