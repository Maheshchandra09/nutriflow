package com.nutriflow.api.mealplan;

import java.util.List;
import java.util.UUID;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MealPlanRepository extends MongoRepository<MealPlanDocument, String> {

    List<MealPlanDocument> findAllByClientIdAndStatus(
            UUID clientId, MealPlanStatus status);

    List<MealPlanDocument> findAllBySubmissionOutboxStatus(OutboxStatus status);
}
