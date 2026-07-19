package com.nutriflow.api.graphql.input;

import com.nutriflow.api.nutrition.SetNutritionTargetCommand;
import com.nutriflow.api.subscription.CreateSubscriptionCommand;
import com.nutriflow.api.subscription.PlanTier;
import com.nutriflow.api.subscription.SubscriptionStatus;
import com.nutriflow.api.user.CreateUserCommand;
import com.nutriflow.api.user.UserRole;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class AccountInputs {

    private AccountInputs() {}

    public record CreateUserInput(String name, String email, UserRole role) {

        public CreateUserCommand toCommand() {
            return new CreateUserCommand(name, email, role);
        }
    }

    public record CreateSubscriptionInput(
            String clientId,
            String nutritionistId,
            PlanTier planTier,
            SubscriptionStatus status,
            LocalDate startDate) {

        public CreateSubscriptionCommand toCommand() {
            return new CreateSubscriptionCommand(
                    GraphQlInputSupport.uuid(clientId, "subscription.clientId"),
                    GraphQlInputSupport.uuid(
                            nutritionistId, "subscription.nutritionistId"),
                    planTier,
                    status,
                    startDate);
        }
    }

    public record NutritionTargetInput(
            String clientId,
            Integer dailyCalories,
            BigDecimal proteinGrams,
            BigDecimal carbohydrateGrams,
            BigDecimal fatGrams) {

        public SetNutritionTargetCommand toCommand() {
            return new SetNutritionTargetCommand(
                    GraphQlInputSupport.uuid(clientId, "nutritionTarget.clientId"),
                    dailyCalories,
                    proteinGrams,
                    carbohydrateGrams,
                    fatGrams);
        }
    }
}
