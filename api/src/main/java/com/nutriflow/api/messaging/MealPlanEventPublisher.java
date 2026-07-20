package com.nutriflow.api.messaging;

import com.nutriflow.contracts.MealPlanSubmittedV1;

public interface MealPlanEventPublisher {

    void publish(MealPlanSubmittedV1 event);
}
