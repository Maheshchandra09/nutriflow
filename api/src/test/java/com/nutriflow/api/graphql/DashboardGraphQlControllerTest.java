package com.nutriflow.api.graphql;

import static org.mockito.Mockito.when;

import com.nutriflow.api.dashboard.ClientDashboard;
import com.nutriflow.api.dashboard.ClientDashboardService;
import com.nutriflow.api.nutrition.NutritionTargetEntity;
import com.nutriflow.api.subscription.PlanTier;
import com.nutriflow.api.subscription.SubscriptionEntity;
import com.nutriflow.api.subscription.SubscriptionStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@GraphQlTest(DashboardGraphQlController.class)
@Import({GraphQlScalarConfiguration.class, DomainExceptionResolver.class})
class DashboardGraphQlControllerTest {

    @Autowired private GraphQlTester graphQlTester;

    @MockitoBean private ClientDashboardService dashboardService;

    @Test
    void dashboardCombinesSubscriptionAndOptionalTargetData() {
        UUID clientId = UUID.randomUUID();
        SubscriptionEntity subscription =
                new SubscriptionEntity(
                        UUID.randomUUID(),
                        clientId,
                        UUID.randomUUID(),
                        PlanTier.PREMIUM,
                        SubscriptionStatus.ACTIVE,
                        LocalDate.of(2026, 1, 1));
        NutritionTargetEntity target =
                new NutritionTargetEntity(
                        clientId,
                        2_000,
                        new BigDecimal("120"),
                        new BigDecimal("200"),
                        new BigDecimal("70"));
        when(dashboardService.get(clientId))
                .thenReturn(new ClientDashboard(subscription, target, null));

        graphQlTester
                .document(
                        """
                        query Dashboard($clientId: ID!) {
                          clientDashboard(clientId: $clientId) {
                            subscription { planTier status }
                            nutritionTarget {
                              dailyCalorieTarget
                              proteinTargetGrams
                            }
                            activeMealPlan { id }
                          }
                        }
                        """)
                .variable("clientId", clientId.toString())
                .execute()
                .path("clientDashboard.subscription.planTier")
                .entity(String.class)
                .isEqualTo("PREMIUM")
                .path("clientDashboard.nutritionTarget.dailyCalorieTarget")
                .entity(Integer.class)
                .isEqualTo(2_000)
                .path("clientDashboard.activeMealPlan")
                .valueIsNull();
    }
}
