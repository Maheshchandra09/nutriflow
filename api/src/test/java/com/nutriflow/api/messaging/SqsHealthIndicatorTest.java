package com.nutriflow.api.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

class SqsHealthIndicatorTest {

    private static final String QUEUE_URL =
            "http://localhost:4566/000000000000/meal-plan-events";

    @Test
    void reportsQueueConnectivity() {
        SqsClient client = mock(SqsClient.class);
        when(client.getQueueAttributes(
                        any(
                                software.amazon.awssdk.services.sqs.model
                                        .GetQueueAttributesRequest.class)))
                .thenReturn(
                        GetQueueAttributesResponse.builder()
                                .attributes(
                                        Map.of(
                                                QueueAttributeName.QUEUE_ARN,
                                                "arn:aws:sqs:us-east-1:000000000000:meal-plan-events"))
                                .build());

        var indicator =
                new SqsHealthIndicator(client, properties());

        assertEquals(Status.UP, indicator.health().getStatus());
    }

    @Test
    void reportsFailureWithoutThrowingFromActuator() {
        SqsClient client = mock(SqsClient.class);
        when(client.getQueueAttributes(
                        any(
                                software.amazon.awssdk.services.sqs.model
                                        .GetQueueAttributesRequest.class)))
                .thenThrow(new IllegalStateException("unavailable"));

        var indicator =
                new SqsHealthIndicator(client, properties());

        assertEquals(Status.DOWN, indicator.health().getStatus());
    }

    private SqsMessagingProperties properties() {
        return new SqsMessagingProperties(
                true,
                QUEUE_URL,
                "us-east-1",
                URI.create("http://localhost:4566"),
                1000);
    }
}
