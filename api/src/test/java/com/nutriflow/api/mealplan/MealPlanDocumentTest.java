package com.nutriflow.api.mealplan;

import static org.assertj.core.api.Assertions.assertThat;

import com.nutriflow.api.recipe.MealType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MealPlanDocumentTest {

    @Test
    void submissionStoresOutboxEventAndChangesStatusTogether() {
        LocalDate start = LocalDate.of(2026, 7, 20);
        MealPlanDocument plan =
                new MealPlanDocument(
                        null,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        start,
                        List.of(
                                new MealPlanDay(
                                        start,
                                        List.of(
                                                new PlannedMeal(
                                                        UUID.randomUUID().toString(),
                                                        MealType.DINNER)))));
        SubmissionOutboxEvent event =
                new SubmissionOutboxEvent(
                        UUID.randomUUID(), 1, OutboxStatus.PENDING, Instant.now());

        plan.submit(event);

        assertThat(plan.getStatus()).isEqualTo(MealPlanStatus.SUBMITTED);
        assertThat(plan.getSubmissionOutbox()).isEqualTo(event);
    }

    @Test
    void onlyMatchingPendingOutboxEventCanBeMarkedSent() {
        LocalDate start = LocalDate.of(2026, 7, 20);
        MealPlanDocument plan =
                new MealPlanDocument(
                        null,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        start,
                        List.of(
                                new MealPlanDay(
                                        start,
                                        List.of(
                                                new PlannedMeal(
                                                        UUID.randomUUID().toString(),
                                                        MealType.DINNER)))));
        UUID eventId = UUID.randomUUID();
        plan.submit(
                new SubmissionOutboxEvent(
                        eventId, 1, OutboxStatus.PENDING, Instant.now()));
        Instant sentAt = Instant.parse("2026-07-19T20:01:00Z");

        assertThat(plan.markOutboxSent(UUID.randomUUID(), sentAt)).isFalse();
        assertThat(plan.markOutboxSent(eventId, sentAt)).isTrue();
        assertThat(plan.markOutboxSent(eventId, sentAt.plusSeconds(1))).isFalse();
        assertThat(plan.getSubmissionOutbox().status()).isEqualTo(OutboxStatus.SENT);
        assertThat(plan.getSubmissionOutbox().sentAt()).isEqualTo(sentAt);
    }
}
