package com.nutriflow.api.mealplan;

import static com.nutriflow.api.common.DomainErrors.invalidState;
import static com.nutriflow.api.common.DomainErrors.notFound;
import static com.nutriflow.api.common.DomainErrors.validation;

import com.nutriflow.api.recipe.RecipeDocument;
import com.nutriflow.api.recipe.RecipeRepository;
import com.nutriflow.api.subscription.SubscriptionEntity;
import com.nutriflow.api.subscription.SubscriptionRepository;
import com.nutriflow.api.subscription.SubscriptionStatus;
import com.nutriflow.contracts.MealPlanSubmittedV1;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MealPlanService {

    private final MealPlanRepository mealPlanRepository;
    private final RecipeRepository recipeRepository;
    private final SubscriptionRepository subscriptionRepository;

    public MealPlanDocument createDraft(MealPlanCommand command) {
        if (command == null) {
            throw validation("mealPlan", "Meal-plan input is required");
        }
        validateAssignment(command.clientId(), command.nutritionistId());
        validateSchedule(command.weekStartDate(), command.days());
        validateRecipeReferences(command.days());
        return mealPlanRepository.save(
                new MealPlanDocument(
                        null,
                        command.clientId(),
                        command.nutritionistId(),
                        command.weekStartDate(),
                        command.days()));
    }

    public MealPlanDocument get(String id) {
        return mealPlanRepository
                .findById(id)
                .orElseThrow(() -> notFound("mealPlan", id));
    }

    public MealPlanDocument updateDraft(
            String id, LocalDate weekStartDate, List<MealPlanDay> days) {
        MealPlanDocument plan = get(id);
        requireDraft(plan);
        validateAssignment(plan.getClientId(), plan.getNutritionistId());
        validateSchedule(weekStartDate, days);
        validateRecipeReferences(days);
        plan.replaceSchedule(weekStartDate, days);
        return mealPlanRepository.save(plan);
    }

    public MealPlanDocument submit(String id) {
        MealPlanDocument plan = get(id);
        requireDraft(plan);
        validateAssignment(plan.getClientId(), plan.getNutritionistId());
        validateSchedule(plan.getWeekStartDate(), plan.getDays());
        validateRecipeReferences(plan.getDays());
        plan.submit(
                new SubmissionOutboxEvent(
                        UUID.randomUUID(),
                        MealPlanSubmittedV1.SCHEMA_VERSION,
                        OutboxStatus.PENDING,
                        Instant.now()));
        return mealPlanRepository.save(plan);
    }

    private void validateAssignment(UUID clientId, UUID nutritionistId) {
        if (clientId == null || nutritionistId == null) {
            throw validation(
                    "mealPlan.assignment", "Client and nutritionist are required");
        }
        SubscriptionEntity subscription =
                subscriptionRepository
                        .findByClientIdAndStatus(clientId, SubscriptionStatus.ACTIVE)
                        .orElseThrow(
                                () ->
                                        validation(
                                                "mealPlan.clientId",
                                                "Client must have an active subscription"));
        if (!subscription.getNutritionistId().equals(nutritionistId)) {
            throw validation(
                    "mealPlan.nutritionistId",
                    "Meal plan nutritionist must match the active subscription");
        }
    }

    private void validateSchedule(LocalDate weekStartDate, List<MealPlanDay> days) {
        if (weekStartDate == null) {
            throw validation("mealPlan.weekStartDate", "Week start date is required");
        }
        if (days == null || days.size() != 7) {
            throw validation("mealPlan.days", "A meal plan must contain exactly seven days");
        }
        Set<LocalDate> actualDates = new HashSet<>();
        for (int dayIndex = 0; dayIndex < days.size(); dayIndex++) {
            MealPlanDay day = days.get(dayIndex);
            if (day == null || day.date() == null) {
                throw validation(
                        "mealPlan.days[" + dayIndex + "].date", "Date is required");
            }
            if (!actualDates.add(day.date())) {
                throw validation("mealPlan.days", "Meal-plan dates must be unique");
            }
            if (day.meals() == null || day.meals().isEmpty()) {
                throw validation(
                        "mealPlan.days[" + dayIndex + "].meals",
                        "Each day must contain at least one meal");
            }
            for (int mealIndex = 0; mealIndex < day.meals().size(); mealIndex++) {
                PlannedMeal meal = day.meals().get(mealIndex);
                if (meal == null
                        || meal.recipeId() == null
                        || meal.recipeId().isBlank()
                        || meal.mealType() == null) {
                    throw validation(
                            "mealPlan.days[" + dayIndex + "].meals[" + mealIndex + "]",
                            "Recipe and meal type are required");
                }
            }
        }
        Set<LocalDate> expectedDates = new HashSet<>();
        for (int offset = 0; offset < 7; offset++) {
            expectedDates.add(weekStartDate.plusDays(offset));
        }
        if (!actualDates.equals(expectedDates)) {
            throw validation(
                    "mealPlan.days",
                    "Dates must be the seven consecutive days beginning at weekStartDate");
        }
    }

    private void validateRecipeReferences(List<MealPlanDay> days) {
        Set<String> requestedIds = new HashSet<>();
        days.forEach(
                day ->
                        day.meals()
                                .forEach(meal -> requestedIds.add(meal.recipeId())));
        List<RecipeDocument> recipes = recipeRepository.findAllById(requestedIds);
        Set<String> activeIds = new HashSet<>();
        recipes.stream()
                .filter(RecipeDocument::isActive)
                .map(RecipeDocument::getId)
                .forEach(activeIds::add);
        if (!activeIds.equals(requestedIds)) {
            Set<String> invalidIds = new HashSet<>(requestedIds);
            invalidIds.removeAll(activeIds);
            throw validation(
                    "mealPlan.days.meals.recipeId",
                    "Recipes must exist and be active: " + invalidIds);
        }
    }

    private void requireDraft(MealPlanDocument plan) {
        if (plan.getStatus() != MealPlanStatus.DRAFT) {
            throw invalidState(
                    "mealPlan.status", "Only a DRAFT meal plan can be changed or submitted");
        }
    }
}
