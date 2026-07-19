package com.nutriflow.api.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.when;

import com.nutriflow.api.mealplan.MealPlanDocument;
import com.nutriflow.api.mealplan.MealPlanRepository;
import com.nutriflow.api.nutrition.NutritionTargetRepository;
import com.nutriflow.api.subscription.PlanTier;
import com.nutriflow.api.subscription.SubscriptionEntity;
import com.nutriflow.api.subscription.SubscriptionRepository;
import com.nutriflow.api.subscription.SubscriptionStatus;
import com.nutriflow.api.user.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClientDashboardServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private NutritionTargetRepository targetRepository;
    @Mock private MealPlanRepository mealPlanRepository;

    @Test
    void combinesRelationalAccountDataWithLatestMongoPlan() {
        UUID clientId = UUID.randomUUID();
        UUID nutritionistId = UUID.randomUUID();
        SubscriptionEntity subscription =
                new SubscriptionEntity(
                        UUID.randomUUID(),
                        clientId,
                        nutritionistId,
                        PlanTier.PREMIUM,
                        SubscriptionStatus.ACTIVE,
                        LocalDate.now());
        MealPlanDocument plan =
                new MealPlanDocument(
                        "plan-1",
                        clientId,
                        nutritionistId,
                        LocalDate.now(),
                        List.of());
        when(userRepository.existsById(clientId)).thenReturn(true);
        when(subscriptionRepository.findByClientIdAndStatus(
                        clientId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(subscription));
        when(targetRepository.findById(clientId)).thenReturn(Optional.empty());
        when(mealPlanRepository
                        .findFirstByClientIdAndStatusInOrderByWeekStartDateDesc(
                                org.mockito.ArgumentMatchers.eq(clientId), anySet()))
                .thenReturn(Optional.of(plan));

        ClientDashboard dashboard =
                new ClientDashboardService(
                                userRepository,
                                subscriptionRepository,
                                targetRepository,
                                mealPlanRepository)
                        .get(clientId);

        assertThat(dashboard.subscription()).isSameAs(subscription);
        assertThat(dashboard.nutritionTarget()).isNull();
        assertThat(dashboard.activeMealPlan()).isSameAs(plan);
    }
}
