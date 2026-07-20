package com.nutriflow.api.recipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nutriflow.api.common.DomainException;
import com.nutriflow.api.mealplan.MealPlanRepository;
import com.nutriflow.api.user.UserEntity;
import com.nutriflow.api.user.UserRepository;
import com.nutriflow.api.user.UserRole;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.bson.types.Decimal128;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class RecipeServiceTest {

    @Mock private RecipeRepository recipeRepository;
    @Mock private MealPlanRepository mealPlanRepository;
    @Mock private UserRepository userRepository;

    private RecipeService service;

    @BeforeEach
    void setUp() {
        service =
                new RecipeService(
                        recipeRepository,
                        mealPlanRepository,
                        userRepository,
                        new DietAttributeValidator());
    }

    @Test
    void createsValidatedRecipeForNutritionist() {
        UUID creatorId = UUID.randomUUID();
        when(userRepository.findById(creatorId))
                .thenReturn(
                        Optional.of(
                                new UserEntity(
                                        creatorId,
                                        "Nutritionist",
                                        "nutritionist@example.com",
                                        UserRole.NUTRITIONIST)));
        when(recipeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RecipeDocument created = service.create(ketoCommand(creatorId));

        assertThat(created.getName()).isEqualTo("Keto bowl");
        assertThat(created.getCreatedBy()).isEqualTo(creatorId);
        assertThat(created.getDietAttributes()).containsOnlyKeys(
                "netCarbsG", "fatG", "proteinG", "ketoRatio");
    }

    @Test
    void rejectsRecipeWithInvalidDietAttributesBeforeSaving() {
        UUID creatorId = UUID.randomUUID();
        RecipeCommand command =
                new RecipeCommand(
                        "Invalid",
                        null,
                        DietType.KETO,
                        List.of("Cook"),
                        creatorId,
                        List.of(new Ingredient("Food", BigDecimal.ONE, "g")),
                        macros(),
                        Map.of("netCarbsG", 10));

        assertThatThrownBy(() -> service.create(command))
                .isInstanceOf(DomainException.class)
                .extracting("code")
                .isEqualTo("VALIDATION_ERROR");
        verify(recipeRepository, never()).save(any());
    }

    @Test
    void preventsUpdateWhileRecipeIsBeingProcessed() {
        RecipeDocument recipe = recipe("recipe-1", UUID.randomUUID());
        when(recipeRepository.findByIdAndActiveTrue("recipe-1"))
                .thenReturn(Optional.of(recipe));
        when(mealPlanRepository.existsByDaysMealsRecipeIdAndStatusIn(
                        eq("recipe-1"), anySet()))
                .thenReturn(true);

        assertThatThrownBy(
                        () ->
                                service.update(
                                        "recipe-1",
                                        new RecipeUpdateCommand(
                                                recipe.getName(),
                                                recipe.getDescription(),
                                                recipe.getDietType(),
                                                recipe.getPrepSteps(),
                                                recipe.getIngredients(),
                                                recipe.getMacros(),
                                                recipe.getDietAttributes())))
                .isInstanceOf(DomainException.class)
                .extracting("code")
                .isEqualTo("CONFLICT");
        verify(recipeRepository, never()).save(any());
    }

    @Test
    void addsOneReviewAndRecalculatesAverage() {
        UUID existingReviewer = UUID.randomUUID();
        UUID newReviewer = UUID.randomUUID();
        RecipeDocument recipe = recipe("recipe-1", UUID.randomUUID());
        recipe.addReview(
                new RecipeReview(existingReviewer, 3, null, java.time.Instant.now()),
                new BigDecimal("3.00"));
        when(recipeRepository.findByIdAndActiveTrue("recipe-1"))
                .thenReturn(Optional.of(recipe));
        when(userRepository.existsById(newReviewer)).thenReturn(true);
        when(recipeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RecipeDocument updated =
                service.addReview("recipe-1", new ReviewCommand(newReviewer, 4, "Good"));

        assertThat(updated.getReviews()).hasSize(2);
        assertThat(updated.getAverageRating()).isEqualByComparingTo("3.50");
    }

    @Test
    void rejectsDuplicateReviewFromSameUser() {
        UUID reviewer = UUID.randomUUID();
        RecipeDocument recipe = recipe("recipe-1", UUID.randomUUID());
        recipe.addReview(
                new RecipeReview(reviewer, 4, null, java.time.Instant.now()),
                new BigDecimal("4.00"));
        when(recipeRepository.findByIdAndActiveTrue("recipe-1"))
                .thenReturn(Optional.of(recipe));
        when(userRepository.existsById(reviewer)).thenReturn(true);

        assertThatThrownBy(
                        () ->
                                service.addReview(
                                        "recipe-1", new ReviewCommand(reviewer, 5, null)))
                .isInstanceOf(DomainException.class)
                .extracting("fieldPath")
                .isEqualTo("review.userId");
    }

    @Test
    void computesInclusiveFifteenPercentSwapBoundaries() {
        RecipeDocument recipe = recipe("recipe-1", UUID.randomUUID());
        when(recipeRepository.findByIdAndActiveTrue("recipe-1"))
                .thenReturn(Optional.of(recipe));
        when(recipeRepository.findSwapCandidates(
                        any(),
                        any(),
                        anyInt(),
                        anyInt(),
                        any(),
                        any(),
                        any(Pageable.class)))
                .thenReturn(List.of());

        service.swapSuggestions("recipe-1", 20);

        ArgumentCaptor<Decimal128> minimumProtein = ArgumentCaptor.forClass(Decimal128.class);
        ArgumentCaptor<Decimal128> maximumProtein = ArgumentCaptor.forClass(Decimal128.class);
        verify(recipeRepository)
                .findSwapCandidates(
                        eq("recipe-1"),
                        eq(DietType.KETO),
                        eq(340),
                        eq(460),
                        minimumProtein.capture(),
                        maximumProtein.capture(),
                        any(Pageable.class));
        assertThat(minimumProtein.getValue().bigDecimalValue()).isEqualByComparingTo("17.00");
        assertThat(maximumProtein.getValue().bigDecimalValue()).isEqualByComparingTo("23.00");
    }

    private RecipeCommand ketoCommand(UUID creatorId) {
        return new RecipeCommand(
                " Keto bowl ",
                "Dinner",
                DietType.KETO,
                List.of("Cook", "Serve"),
                creatorId,
                List.of(new Ingredient("Avocado", BigDecimal.ONE, "piece")),
                macros(),
                ketoAttributes());
    }

    private RecipeDocument recipe(String id, UUID creatorId) {
        return new RecipeDocument(
                id,
                "Keto bowl",
                "Dinner",
                DietType.KETO,
                List.of("Cook", "Serve"),
                creatorId,
                List.of(new Ingredient("Avocado", BigDecimal.ONE, "piece")),
                macros(),
                ketoAttributes());
    }

    private Macros macros() {
        return new Macros(
                400,
                new BigDecimal("20"),
                new BigDecimal("10"),
                new BigDecimal("30"));
    }

    private Map<String, Object> ketoAttributes() {
        return Map.of(
                "netCarbsG", new BigDecimal("8"),
                "fatG", new BigDecimal("30"),
                "proteinG", new BigDecimal("20"),
                "ketoRatio", new BigDecimal("1.5"));
    }
}
