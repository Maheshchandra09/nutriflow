package com.nutriflow.api.dashboard;

import static com.nutriflow.api.common.DomainErrors.notFound;

import com.nutriflow.api.mealplan.MealPlanRepository;
import com.nutriflow.api.mealplan.MealPlanStatus;
import com.nutriflow.api.nutrition.NutritionTargetRepository;
import com.nutriflow.api.subscription.SubscriptionEntity;
import com.nutriflow.api.subscription.SubscriptionRepository;
import com.nutriflow.api.subscription.SubscriptionStatus;
import com.nutriflow.api.user.UserRepository;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClientDashboardService {

    private static final Set<MealPlanStatus> ACTIVE_PLAN_STATUSES =
            Set.of(
                    MealPlanStatus.SUBMITTED,
                    MealPlanStatus.PROCESSING,
                    MealPlanStatus.PROCESSED,
                    MealPlanStatus.FLAGGED);

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final NutritionTargetRepository nutritionTargetRepository;
    private final MealPlanRepository mealPlanRepository;

    public ClientDashboard get(UUID clientId) {
        if (!userRepository.existsById(clientId)) {
            throw notFound("client", clientId);
        }
        SubscriptionEntity subscription =
                subscriptionRepository
                        .findByClientIdAndStatus(clientId, SubscriptionStatus.ACTIVE)
                        .orElseThrow(() -> notFound("activeSubscription", clientId));
        return new ClientDashboard(
                subscription,
                nutritionTargetRepository.findById(clientId).orElse(null),
                mealPlanRepository
                        .findFirstByClientIdAndStatusInOrderByWeekStartDateDesc(
                                clientId, ACTIVE_PLAN_STATUSES)
                        .orElse(null));
    }
}
