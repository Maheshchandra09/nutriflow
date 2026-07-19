package com.nutriflow.api.mealplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nutriflow.api.common.DomainException;
import com.nutriflow.api.recipe.DietType;
import com.nutriflow.api.recipe.Ingredient;
import com.nutriflow.api.recipe.Macros;
import com.nutriflow.api.recipe.MealType;
import com.nutriflow.api.recipe.RecipeDocument;
import com.nutriflow.api.recipe.RecipeRepository;
import com.nutriflow.api.subscription.PlanTier;
import com.nutriflow.api.subscription.SubscriptionEntity;
import com.nutriflow.api.subscription.SubscriptionRepository;
import com.nutriflow.api.subscription.SubscriptionStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MealPlanServiceTest {

    @Mock private MealPlanRepository mealPlanRepository;
    @Mock private RecipeRepository recipeRepository;
    @Mock private SubscriptionRepository subscriptionRepository;

    private MealPlanService service;
    private UUID clientId;
    private UUID nutritionistId;
    private LocalDate weekStart;

    @BeforeEach
    void setUp() {
        service =
                new MealPlanService(
                        mealPlanRepository, recipeRepository, subscriptionRepository);
        clientId = UUID.randomUUID();
        nutritionistId = UUID.randomUUID();
        weekStart = LocalDate.of(2026, 7, 20);
    }

    @Test
    void createsValidatedSevenDayDraft() {
        stubActiveAssignment();
        RecipeDocument recipe = recipe("recipe-1");
        when(recipeRepository.findAllById(any())).thenReturn(List.of(recipe));
        when(mealPlanRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MealPlanDocument plan =
                service.createDraft(
                        new MealPlanCommand(
                                clientId,
                                nutritionistId,
                                weekStart,
                                sevenDays("recipe-1")));

        assertThat(plan.getStatus()).isEqualTo(MealPlanStatus.DRAFT);
        assertThat(plan.getDays()).hasSize(7);
    }

    @Test
    void rejectsNonConsecutiveDates() {
        stubActiveAssignment();
        List<MealPlanDay> days = new java.util.ArrayList<>(sevenDays("recipe-1"));
        days.set(
                6,
                new MealPlanDay(
                        weekStart.plusDays(8),
                        List.of(new PlannedMeal("recipe-1", MealType.DINNER))));

        assertThatThrownBy(
                        () ->
                                service.createDraft(
                                        new MealPlanCommand(
                                                clientId,
                                                nutritionistId,
                                                weekStart,
                                                days)))
                .isInstanceOf(DomainException.class)
                .extracting("fieldPath")
                .isEqualTo("mealPlan.days");
        verify(mealPlanRepository, never()).save(any());
    }

    @Test
    void rejectsInactiveOrMissingRecipeReferences() {
        stubActiveAssignment();
        when(recipeRepository.findAllById(any())).thenReturn(List.of());

        assertThatThrownBy(
                        () ->
                                service.createDraft(
                                        new MealPlanCommand(
                                                clientId,
                                                nutritionistId,
                                                weekStart,
                                                sevenDays("missing"))))
                .isInstanceOf(DomainException.class)
                .extracting("fieldPath")
                .isEqualTo("mealPlan.days.meals.recipeId");
    }

    @Test
    void submissionAtomicallyStoresPendingOutboxState() {
        MealPlanDocument plan =
                new MealPlanDocument(
                        "plan-1",
                        clientId,
                        nutritionistId,
                        weekStart,
                        sevenDays("recipe-1"));
        when(mealPlanRepository.findById("plan-1")).thenReturn(Optional.of(plan));
        stubActiveAssignment();
        when(recipeRepository.findAllById(any())).thenReturn(List.of(recipe("recipe-1")));
        when(mealPlanRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MealPlanDocument submitted = service.submit("plan-1");

        assertThat(submitted.getStatus()).isEqualTo(MealPlanStatus.SUBMITTED);
        assertThat(submitted.getSubmissionOutbox().status()).isEqualTo(OutboxStatus.PENDING);
        assertThat(submitted.getSubmissionOutbox().schemaVersion()).isEqualTo(1);
    }

    private void stubActiveAssignment() {
        when(subscriptionRepository.findByClientIdAndStatus(
                        clientId, SubscriptionStatus.ACTIVE))
                .thenReturn(
                        Optional.of(
                                new SubscriptionEntity(
                                        UUID.randomUUID(),
                                        clientId,
                                        nutritionistId,
                                        PlanTier.BASIC,
                                        SubscriptionStatus.ACTIVE,
                                        LocalDate.now())));
    }

    private List<MealPlanDay> sevenDays(String recipeId) {
        return java.util.stream.IntStream.range(0, 7)
                .mapToObj(
                        offset ->
                                new MealPlanDay(
                                        weekStart.plusDays(offset),
                                        List.of(
                                                new PlannedMeal(
                                                        recipeId, MealType.DINNER))))
                .toList();
    }

    private RecipeDocument recipe(String id) {
        return new RecipeDocument(
                id,
                "Keto bowl",
                "Dinner",
                DietType.KETO,
                List.of("Cook"),
                nutritionistId,
                List.of(new Ingredient("Avocado", BigDecimal.ONE, "piece")),
                new Macros(
                        400,
                        new BigDecimal("20"),
                        new BigDecimal("10"),
                        new BigDecimal("30")),
                Map.of(
                        "netCarbsG", new BigDecimal("8"),
                        "fatG", new BigDecimal("30"),
                        "proteinG", new BigDecimal("20"),
                        "ketoRatio", new BigDecimal("1.5")));
    }
}
