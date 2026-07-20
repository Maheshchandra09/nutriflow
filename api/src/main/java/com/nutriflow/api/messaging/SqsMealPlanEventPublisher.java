package com.nutriflow.api.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutriflow.contracts.MealPlanSubmittedV1;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Component
@ConditionalOnProperty(
        prefix = "nutriflow.messaging.sqs",
        name = "enabled",
        havingValue = "true")
public class SqsMealPlanEventPublisher implements MealPlanEventPublisher {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final String queueUrl;

    public SqsMealPlanEventPublisher(
            SqsClient sqsClient,
            ObjectMapper objectMapper,
            SqsMessagingProperties properties) {
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
        this.queueUrl = properties.queueUrl();
        if (queueUrl == null || queueUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "nutriflow.messaging.sqs.queue-url is required when SQS is enabled");
        }
    }

    @Override
    public void publish(MealPlanSubmittedV1 event) {
        sqsClient.sendMessage(
                SendMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .messageBody(serialize(event))
                        .build());
    }

    private String serialize(MealPlanSubmittedV1 event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize meal-plan event", exception);
        }
    }
}
