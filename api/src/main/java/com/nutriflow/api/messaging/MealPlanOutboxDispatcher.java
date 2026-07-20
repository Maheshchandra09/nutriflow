package com.nutriflow.api.messaging;

import com.nutriflow.api.mealplan.MealPlanDocument;
import com.nutriflow.api.mealplan.MealPlanRepository;
import com.nutriflow.api.mealplan.OutboxStatus;
import com.nutriflow.api.mealplan.SubmissionOutboxEvent;
import com.nutriflow.contracts.MealPlanSubmittedV1;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "nutriflow.messaging.sqs",
        name = "enabled",
        havingValue = "true")
public class MealPlanOutboxDispatcher {

    private final MealPlanRepository mealPlanRepository;
    private final MealPlanEventPublisher eventPublisher;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${nutriflow.messaging.sqs.dispatch-delay-ms:1000}")
    public void dispatchPending() {
        mealPlanRepository
                .findTop100BySubmissionOutboxStatusOrderBySubmissionOutboxOccurredAtAsc(
                        OutboxStatus.PENDING)
                .forEach(this::dispatch);
    }

    void dispatch(MealPlanDocument plan) {
        SubmissionOutboxEvent outbox = plan.getSubmissionOutbox();
        MealPlanSubmittedV1 event =
                new MealPlanSubmittedV1(
                        outbox.eventId(),
                        outbox.schemaVersion(),
                        UUID.fromString(plan.getId()),
                        plan.getClientId(),
                        outbox.occurredAt());
        eventPublisher.publish(event);
        if (plan.markOutboxSent(outbox.eventId(), Instant.now(clock))) {
            mealPlanRepository.save(plan);
        }
    }
}
