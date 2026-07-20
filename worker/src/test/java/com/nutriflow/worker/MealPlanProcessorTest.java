package com.nutriflow.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nutriflow.contracts.MealPlanSubmittedV1;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MealPlanProcessorTest {

    @Test
    void completesClaimedEventWithCalculatedResult() {
        MealPlanSubmittedV1 event = event(1);
        FakeMealPlanStore plans =
                new FakeMealPlanStore(
                        new ProcessingModels.MealPlanWork(
                                event.mealPlanId(),
                                event.clientId(),
                                List.of("recipe-1")));
        MealPlanProcessor processor =
                processor(plans, List.of(recipe("recipe-1")));

        processor.process(event);

        assertEquals(event.eventId(), plans.completedEventId);
        assertEquals(300, plans.completedResult.weeklyTotals().calories());
    }

    @Test
    void duplicateTerminalEventIsAcknowledgedWithoutRecalculation() {
        MealPlanSubmittedV1 event = event(1);
        FakeMealPlanStore plans =
                new FakeMealPlanStore(
                        new ProcessingModels.MealPlanWork(
                                event.mealPlanId(),
                                event.clientId(),
                                List.of("missing")));
        plans.claimResult = ProcessingModels.ClaimResult.DUPLICATE_TERMINAL;

        processor(plans, List.of()).process(event);

        assertEquals(0, plans.loadCount);
    }

    @Test
    void recordsSanitizedFailureAndRethrowsForSqsRetry() {
        MealPlanSubmittedV1 event = event(1);
        FakeMealPlanStore plans =
                new FakeMealPlanStore(
                        new ProcessingModels.MealPlanWork(
                                event.mealPlanId(),
                                event.clientId(),
                                List.of("missing")));
        MealPlanProcessor processor = processor(plans, List.of());

        IllegalStateException failure =
                assertThrows(
                        IllegalStateException.class,
                        () -> processor.process(event));

        assertEquals(
                "Submitted meal plan references unavailable recipe",
                failure.getMessage());
        assertEquals(
                "Processing failed: IllegalStateException", plans.failure);
    }

    @Test
    void rejectsUnsupportedContractBeforeClaimingPlan() {
        MealPlanSubmittedV1 event = event(2);
        FakeMealPlanStore plans =
                new FakeMealPlanStore(
                        new ProcessingModels.MealPlanWork(
                                event.mealPlanId(), event.clientId(), List.of()));

        assertThrows(
                IllegalArgumentException.class,
                () -> processor(plans, List.of()).process(event));

        assertEquals(0, plans.claimCount);
    }

    private MealPlanProcessor processor(
            FakeMealPlanStore plans,
            List<ProcessingModels.RecipeData> recipes) {
        return new MealPlanProcessor(
                plans,
                ignored -> recipes,
                ignored -> Optional.empty(),
                new MealPlanCalculator());
    }

    private MealPlanSubmittedV1 event(int schemaVersion) {
        return new MealPlanSubmittedV1(
                UUID.randomUUID(),
                schemaVersion,
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.parse("2026-07-19T20:00:00Z"));
    }

    private ProcessingModels.RecipeData recipe(String id) {
        return new ProcessingModels.RecipeData(
                id,
                new ProcessingModels.MacroValues(
                        300,
                        new BigDecimal("20"),
                        new BigDecimal("30"),
                        new BigDecimal("10")),
                List.of());
    }

    private static final class FakeMealPlanStore
            implements MealPlanProcessingStore {

        private final ProcessingModels.MealPlanWork plan;
        private ProcessingModels.ClaimResult claimResult =
                ProcessingModels.ClaimResult.PROCESS;
        private int claimCount;
        private int loadCount;
        private UUID completedEventId;
        private ProcessingModels.ProcessingResult completedResult;
        private String failure;

        private FakeMealPlanStore(ProcessingModels.MealPlanWork plan) {
            this.plan = plan;
        }

        @Override
        public ProcessingModels.ClaimResult claim(
                MealPlanSubmittedV1 event) {
            claimCount++;
            return claimResult;
        }

        @Override
        public ProcessingModels.MealPlanWork load(UUID mealPlanId) {
            loadCount++;
            return plan;
        }

        @Override
        public void complete(
                UUID mealPlanId,
                UUID eventId,
                ProcessingModels.ProcessingResult result) {
            completedEventId = eventId;
            completedResult = result;
        }

        @Override
        public void fail(
                UUID mealPlanId, UUID eventId, String sanitizedError) {
            failure = sanitizedError;
        }
    }
}
