package com.nutriflow.api.graphql;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nutriflow.api.mealplan.MealPlanDay;
import com.nutriflow.api.mealplan.MealPlanCommand;
import com.nutriflow.api.mealplan.MealPlanDocument;
import com.nutriflow.api.mealplan.MealPlanService;
import com.nutriflow.api.mealplan.PlannedMeal;
import com.nutriflow.api.recipe.DietType;
import com.nutriflow.api.recipe.Ingredient;
import com.nutriflow.api.recipe.Macros;
import com.nutriflow.api.recipe.MealType;
import com.nutriflow.api.recipe.RecipeDocument;
import com.nutriflow.api.recipe.RecipeRepository;
import com.nutriflow.api.recipe.RecipeService;
import java.math.BigDecimal;
import java.time.LocalDate;
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

@GraphQlTest(MealPlanGraphQlController.class)
@Import({GraphQlScalarConfiguration.class, DomainExceptionResolver.class})
class MealPlanGraphQlControllerTest {

    @Autowired private GraphQlTester graphQlTester;

    @MockitoBean private MealPlanService mealPlanService;
    @MockitoBean private RecipeService recipeService;
    @MockitoBean private RecipeRepository recipeRepository;

    @Test
    void resolvesNestedRecipesThroughOneBatchRepositoryCall() {
        UUID clientId = UUID.randomUUID();
        UUID nutritionistId = UUID.randomUUID();
        PlannedMeal meal = new PlannedMeal("recipe-1", MealType.DINNER);
        MealPlanDocument plan =
                new MealPlanDocument(
                        "plan-1",
                        clientId,
                        nutritionistId,
                        LocalDate.of(2026, 7, 20),
                        List.of(
                                new MealPlanDay(
                                        LocalDate.of(2026, 7, 20),
                                        List.of(meal))));
        RecipeDocument recipe = recipe("recipe-1", nutritionistId);
        when(mealPlanService.get("plan-1")).thenReturn(plan);
        when(recipeRepository.findAllById(any())).thenReturn(List.of(recipe));

        graphQlTester
                .document(
                        """
                        {
                          mealPlan(id: "plan-1") {
                            id
                            weekStartDate
                            days {
                              meals {
                                mealType
                                recipe { id name }
                              }
                            }
                          }
                        }
                        """)
                .execute()
                .path("mealPlan.days[0].meals[0].recipe.name")
                .entity(String.class)
                .isEqualTo("Keto bowl");

        verify(recipeRepository).findAllById(any());
    }

    @Test
    void createMealPlanBindsDateAndNestedMealInputs() {
        UUID clientId = UUID.randomUUID();
        UUID nutritionistId = UUID.randomUUID();
        LocalDate startDate = LocalDate.of(2026, 7, 20);
        MealPlanDocument plan =
                new MealPlanDocument(
                        "plan-1",
                        clientId,
                        nutritionistId,
                        startDate,
                        List.of(
                                new MealPlanDay(
                                        startDate,
                                        List.of(
                                                new PlannedMeal(
                                                        "recipe-1",
                                                        MealType.DINNER)))));
        when(mealPlanService.createDraft(any())).thenReturn(plan);

        graphQlTester
                .document(
                        """
                        mutation CreatePlan($input: MealPlanInput!) {
                          createMealPlan(input: $input) { id status }
                        }
                        """)
                .variable(
                        "input",
                        Map.of(
                                "clientId", clientId.toString(),
                                "nutritionistId", nutritionistId.toString(),
                                "weekStartDate", "2026-07-20",
                                "days",
                                        List.of(
                                                Map.of(
                                                        "date", "2026-07-20",
                                                        "meals",
                                                                List.of(
                                                                        Map.of(
                                                                                "recipeId",
                                                                                        "recipe-1",
                                                                                "mealType",
                                                                                        "DINNER"))))))
                .execute()
                .path("createMealPlan.status")
                .entity(String.class)
                .isEqualTo("DRAFT");

        ArgumentCaptor<MealPlanCommand> command =
                ArgumentCaptor.forClass(MealPlanCommand.class);
        verify(mealPlanService).createDraft(command.capture());
        org.assertj.core.api.Assertions.assertThat(command.getValue().weekStartDate())
                .isEqualTo(startDate);
        org.assertj.core.api.Assertions.assertThat(
                        command.getValue().days().getFirst().meals().getFirst().mealType())
                .isEqualTo(MealType.DINNER);
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
