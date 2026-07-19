package com.nutriflow.api.graphql;

import com.nutriflow.api.graphql.input.AccountInputs.CreateSubscriptionInput;
import com.nutriflow.api.graphql.input.AccountInputs.CreateUserInput;
import com.nutriflow.api.graphql.input.AccountInputs.NutritionTargetInput;
import com.nutriflow.api.nutrition.NutritionTargetEntity;
import com.nutriflow.api.nutrition.NutritionTargetService;
import com.nutriflow.api.subscription.SubscriptionEntity;
import com.nutriflow.api.subscription.SubscriptionService;
import com.nutriflow.api.user.UserEntity;
import com.nutriflow.api.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class AccountGraphQlController {

    private final UserService userService;
    private final SubscriptionService subscriptionService;
    private final NutritionTargetService nutritionTargetService;

    @MutationMapping
    public UserEntity createUser(@Argument CreateUserInput input) {
        return userService.create(input.toCommand());
    }

    @MutationMapping
    public SubscriptionEntity createSubscription(
            @Argument CreateSubscriptionInput input) {
        return subscriptionService.create(input.toCommand());
    }

    @MutationMapping
    public NutritionTargetEntity setNutritionTarget(
            @Argument NutritionTargetInput input) {
        return nutritionTargetService.set(input.toCommand());
    }
}
