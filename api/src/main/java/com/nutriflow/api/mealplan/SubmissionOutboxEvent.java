package com.nutriflow.api.mealplan;

import java.time.Instant;
import java.util.UUID;

public record SubmissionOutboxEvent(
        UUID eventId, int schemaVersion, OutboxStatus status, Instant occurredAt) {}
