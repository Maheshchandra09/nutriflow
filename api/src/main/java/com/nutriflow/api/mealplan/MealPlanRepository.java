package com.nutriflow.api.mealplan;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MealPlanRepository extends MongoRepository<MealPlanDocument, String> {

    List<MealPlanDocument> findAllByClientIdAndStatus(
            UUID clientId, MealPlanStatus status);

    List<MealPlanDocument> findAllByClientIdAndStatusIn(
            UUID clientId, Set<MealPlanStatus> statuses);

    Optional<MealPlanDocument> findFirstByClientIdAndStatusInOrderByWeekStartDateDesc(
            UUID clientId, Set<MealPlanStatus> statuses);

    List<MealPlanDocument> findAllBySubmissionOutboxStatus(OutboxStatus status);

    boolean existsByDaysMealsRecipeIdAndStatusIn(
            String recipeId, Set<MealPlanStatus> statuses);
}
