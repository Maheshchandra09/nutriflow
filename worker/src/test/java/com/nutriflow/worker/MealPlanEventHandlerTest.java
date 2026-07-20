package com.nutriflow.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nutriflow.contracts.MealPlanSubmittedV1;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MealPlanEventHandlerTest {

    @Test
    void parsesSqsBodyAndDelegatesVersionedEvent() throws Exception {
        RecordingPlanStore plans = new RecordingPlanStore();
        MealPlanProcessor processor =
                new MealPlanProcessor(
                        plans,
                        ignored -> List.of(),
                        ignored -> Optional.empty(),
                        new MealPlanCalculator());
        var objectMapper =
                JsonMapper.builder().addModule(new JavaTimeModule()).build();
        MealPlanEventHandler handler =
                new MealPlanEventHandler(processor, objectMapper);
        MealPlanSubmittedV1 event =
                new MealPlanSubmittedV1(
                        UUID.randomUUID(),
                        1,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        Instant.parse("2026-07-19T20:00:00Z"));
        plans.plan =
                new ProcessingModels.MealPlanWork(
                        event.mealPlanId(), event.clientId(), List.of());
        SQSEvent sqsEvent = sqsEvent(objectMapper.writeValueAsString(event));

        handler.handleRequest(sqsEvent, new TestContext());

        assertEquals(event, plans.claimed);
        assertEquals(event.eventId(), plans.completedEventId);
    }

    @Test
    void malformedBodyFailsSoSqsCanRetryAndEventuallyUseDlq() {
        MealPlanProcessor processor =
                new MealPlanProcessor(
                        new RecordingPlanStore(),
                        ignored -> List.of(),
                        ignored -> Optional.empty(),
                        new MealPlanCalculator());
        MealPlanEventHandler handler =
                new MealPlanEventHandler(
                        processor,
                        JsonMapper.builder()
                                .addModule(new JavaTimeModule())
                                .build());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        handler.handleRequest(
                                sqsEvent("{not-json"), new TestContext()));
    }

    private SQSEvent sqsEvent(String body) {
        SQSEvent.SQSMessage message = new SQSEvent.SQSMessage();
        message.setMessageId("message-1");
        message.setBody(body);
        SQSEvent event = new SQSEvent();
        event.setRecords(List.of(message));
        return event;
    }

    private static final class RecordingPlanStore
            implements MealPlanProcessingStore {

        private ProcessingModels.MealPlanWork plan;
        private MealPlanSubmittedV1 claimed;
        private UUID completedEventId;

        @Override
        public ProcessingModels.ClaimResult claim(
                MealPlanSubmittedV1 event) {
            claimed = event;
            return ProcessingModels.ClaimResult.PROCESS;
        }

        @Override
        public ProcessingModels.MealPlanWork load(UUID mealPlanId) {
            return plan;
        }

        @Override
        public void complete(
                UUID mealPlanId,
                UUID eventId,
                ProcessingModels.ProcessingResult result) {
            completedEventId = eventId;
        }

        @Override
        public void fail(
                UUID mealPlanId, UUID eventId, String sanitizedError) {}
    }

    private static final class TestContext implements Context {

        @Override
        public String getAwsRequestId() {
            return "request-1";
        }

        @Override
        public String getLogGroupName() {
            return "test";
        }

        @Override
        public String getLogStreamName() {
            return "test";
        }

        @Override
        public String getFunctionName() {
            return "test";
        }

        @Override
        public String getFunctionVersion() {
            return "1";
        }

        @Override
        public String getInvokedFunctionArn() {
            return "test";
        }

        @Override
        public CognitoIdentity getIdentity() {
            return null;
        }

        @Override
        public ClientContext getClientContext() {
            return null;
        }

        @Override
        public int getRemainingTimeInMillis() {
            return 1000;
        }

        @Override
        public int getMemoryLimitInMB() {
            return 512;
        }

        @Override
        public LambdaLogger getLogger() {
            return new LambdaLogger() {
                @Override
                public void log(String message) {}

                @Override
                public void log(byte[] message) {}
            };
        }
    }
}
