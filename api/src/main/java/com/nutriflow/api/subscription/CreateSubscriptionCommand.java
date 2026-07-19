package com.nutriflow.api.subscription;

import java.time.LocalDate;
import java.util.UUID;

public record CreateSubscriptionCommand(
        UUID clientId,
        UUID nutritionistId,
        PlanTier planTier,
        SubscriptionStatus status,
        LocalDate startDate) {}
