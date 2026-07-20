package com.nutriflow.api.mealplan;

import java.time.Instant;
import java.util.UUID;

public record SubmissionOutboxEvent(
        UUID eventId,
        int schemaVersion,
        OutboxStatus status,
        Instant occurredAt,
        Instant sentAt) {

    public SubmissionOutboxEvent(
            UUID eventId, int schemaVersion, OutboxStatus status, Instant occurredAt) {
        this(eventId, schemaVersion, status, occurredAt, null);
    }

    public SubmissionOutboxEvent markSent(Instant timestamp) {
        return new SubmissionOutboxEvent(
                eventId, schemaVersion, OutboxStatus.SENT, occurredAt, timestamp);
    }
}
