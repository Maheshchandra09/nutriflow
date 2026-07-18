package com.nutriflow.worker;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.nutriflow.contracts.MealPlanSubmittedV1;

public class MealPlanEventHandler implements RequestHandler<MealPlanSubmittedV1, Void> {

    @Override
    public Void handleRequest(MealPlanSubmittedV1 event, Context context) {
        context.getLogger().log(
                "Received meal-plan event %s for plan %s%n"
                        .formatted(event.eventId(), event.mealPlanId()));
        return null;
    }
}

