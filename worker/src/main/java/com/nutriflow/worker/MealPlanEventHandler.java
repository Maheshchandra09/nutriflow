package com.nutriflow.worker;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nutriflow.contracts.MealPlanSubmittedV1;

public class MealPlanEventHandler implements RequestHandler<SQSEvent, Void> {

    private final MealPlanProcessor processor;
    private final ObjectMapper objectMapper;

    public MealPlanEventHandler() {
        this(
                WorkerRuntime.createProcessor(),
                JsonMapper.builder().addModule(new JavaTimeModule()).build());
    }

    MealPlanEventHandler(
            MealPlanProcessor processor, ObjectMapper objectMapper) {
        this.processor = processor;
        this.objectMapper = objectMapper;
    }

    @Override
    public Void handleRequest(SQSEvent sqsEvent, Context context) {
        if (sqsEvent == null || sqsEvent.getRecords() == null) {
            throw new IllegalArgumentException("SQS event records are required");
        }
        for (SQSEvent.SQSMessage message : sqsEvent.getRecords()) {
            MealPlanSubmittedV1 event = deserialize(message.getBody());
            context.getLogger()
                    .log(
                            "Processing eventId=%s mealPlanId=%s%n"
                                    .formatted(
                                            event.eventId(),
                                            event.mealPlanId()));
            processor.process(event);
        }
        return null;
    }

    private MealPlanSubmittedV1 deserialize(String body) {
        try {
            return objectMapper.readValue(body, MealPlanSubmittedV1.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "SQS message body is not a valid meal-plan event",
                    exception);
        }
    }
}
