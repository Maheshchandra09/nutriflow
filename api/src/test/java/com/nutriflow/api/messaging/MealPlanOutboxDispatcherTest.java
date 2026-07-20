package com.nutriflow.api.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nutriflow.api.mealplan.MealPlanDay;
import com.nutriflow.api.mealplan.MealPlanDocument;
import com.nutriflow.api.mealplan.MealPlanRepository;
import com.nutriflow.api.mealplan.OutboxStatus;
import com.nutriflow.api.mealplan.PlannedMeal;
import com.nutriflow.api.mealplan.SubmissionOutboxEvent;
import com.nutriflow.api.recipe.MealType;
import com.nutriflow.contracts.MealPlanSubmittedV1;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MealPlanOutboxDispatcherTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-07-19T20:00:00Z");
    private static final Instant SENT_AT = Instant.parse("2026-07-19T20:01:00Z");

    @Mock private MealPlanRepository repository;
    @Mock private MealPlanEventPublisher publisher;

    private MealPlanOutboxDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher =
                new MealPlanOutboxDispatcher(
                        repository,
                        publisher,
                        Clock.fixed(SENT_AT, ZoneOffset.UTC));
    }

    @Test
    void publishesPendingEventThenMarksItSent() {
        MealPlanDocument plan = submittedPlan();
        when(repository
                        .findTop100BySubmissionOutboxStatusOrderBySubmissionOutboxOccurredAtAsc(
                                OutboxStatus.PENDING))
                .thenReturn(List.of(plan));

        dispatcher.dispatchPending();

        ArgumentCaptor<MealPlanSubmittedV1> eventCaptor =
                ArgumentCaptor.forClass(MealPlanSubmittedV1.class);
        verify(publisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventId())
                .isEqualTo(plan.getSubmissionOutbox().eventId());
        assertThat(eventCaptor.getValue().mealPlanId())
                .isEqualTo(UUID.fromString(plan.getId()));
        assertThat(eventCaptor.getValue().clientId()).isEqualTo(plan.getClientId());
        assertThat(plan.getSubmissionOutbox().status()).isEqualTo(OutboxStatus.SENT);
        assertThat(plan.getSubmissionOutbox().sentAt()).isEqualTo(SENT_AT);
        verify(repository).save(plan);
    }

    @Test
    void leavesEventPendingWhenSqsPublishFails() {
        MealPlanDocument plan = submittedPlan();
        RuntimeException failure = new RuntimeException("SQS unavailable");
        org.mockito.Mockito.doThrow(failure).when(publisher).publish(any());

        assertThatThrownBy(() -> dispatcher.dispatch(plan)).isSameAs(failure);

        assertThat(plan.getSubmissionOutbox().status()).isEqualTo(OutboxStatus.PENDING);
        verify(repository, never()).save(any());
    }

    private MealPlanDocument submittedPlan() {
        LocalDate date = LocalDate.of(2026, 7, 20);
        MealPlanDocument plan =
                new MealPlanDocument(
                        UUID.randomUUID().toString(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        date,
                        List.of(
                                new MealPlanDay(
                                        date,
                                        List.of(
                                                new PlannedMeal(
                                                        UUID.randomUUID().toString(),
                                                        MealType.DINNER)))));
        plan.submit(
                new SubmissionOutboxEvent(
                        UUID.randomUUID(), 1, OutboxStatus.PENDING, OCCURRED_AT));
        return plan;
    }
}
