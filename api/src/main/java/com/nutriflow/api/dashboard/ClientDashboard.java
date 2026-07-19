package com.nutriflow.api.dashboard;

import com.nutriflow.api.mealplan.MealPlanDocument;
import com.nutriflow.api.nutrition.NutritionTargetEntity;
import com.nutriflow.api.subscription.SubscriptionEntity;

public record ClientDashboard(
        SubscriptionEntity subscription,
        NutritionTargetEntity nutritionTarget,
        MealPlanDocument activeMealPlan) {}
