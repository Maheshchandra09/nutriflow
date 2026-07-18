package com.nutriflow.contracts;

import java.time.Instant;
import java.util.UUID;

public record MealPlanSubmittedV1(
        UUID eventId,
        int schemaVersion,
        UUID mealPlanId,
        UUID clientId,
        Instant occurredAt) {

    public static final int SCHEMA_VERSION = 1;
}

