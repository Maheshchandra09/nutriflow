package com.nutriflow.api.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nutriflow.contracts.MealPlanSubmittedV1;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@ExtendWith(MockitoExtension.class)
class SqsMealPlanEventPublisherTest {

    @Mock private SqsClient sqsClient;

    @Test
    void serializesVersionedContractAsSqsMessageBody() throws Exception {
        ObjectMapper objectMapper =
                JsonMapper.builder().addModule(new JavaTimeModule()).build();
        SqsMealPlanEventPublisher publisher =
                new SqsMealPlanEventPublisher(
                        sqsClient,
                        objectMapper,
                        new SqsMessagingProperties(
                                true,
                                "http://localhost/queue/meal-plan-events",
                                "us-east-1",
                                null,
                                1000));
        MealPlanSubmittedV1 event =
                new MealPlanSubmittedV1(
                        UUID.randomUUID(),
                        MealPlanSubmittedV1.SCHEMA_VERSION,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        Instant.parse("2026-07-19T20:00:00Z"));

        publisher.publish(event);

        ArgumentCaptor<SendMessageRequest> requestCaptor =
                ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsClient).sendMessage(requestCaptor.capture());
        assertThat(requestCaptor.getValue().queueUrl())
                .isEqualTo("http://localhost/queue/meal-plan-events");
        assertThat(objectMapper.readValue(
                        requestCaptor.getValue().messageBody(),
                        MealPlanSubmittedV1.class))
                .isEqualTo(event);
    }
}
