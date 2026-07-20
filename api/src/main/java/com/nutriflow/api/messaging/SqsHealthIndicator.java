package com.nutriflow.api.messaging;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

@Component("sqs")
@ConditionalOnProperty(
        prefix = "nutriflow.messaging.sqs",
        name = "enabled",
        havingValue = "true")
public class SqsHealthIndicator implements HealthIndicator {

    private final SqsClient sqsClient;
    private final String queueUrl;

    public SqsHealthIndicator(
            SqsClient sqsClient, SqsMessagingProperties properties) {
        this.sqsClient = sqsClient;
        this.queueUrl = properties.queueUrl();
    }

    @Override
    public Health health() {
        try {
            String queueArn =
                    sqsClient
                            .getQueueAttributes(
                                    GetQueueAttributesRequest.builder()
                                            .queueUrl(queueUrl)
                                            .attributeNames(
                                                    QueueAttributeName.QUEUE_ARN)
                                            .build())
                            .attributes()
                            .get(QueueAttributeName.QUEUE_ARN);
            return Health.up().withDetail("queueArn", queueArn).build();
        } catch (RuntimeException exception) {
            return Health.down(exception).build();
        }
    }
}
