package com.nutriflow.api.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nutriflow.contracts.MealPlanSubmittedV1;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

@Testcontainers
class SqsPublisherIntegrationTest {

    @Container
    private static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(
                            DockerImageName.parse("localstack/localstack:4.0.3"))
                    .withServices(LocalStackContainer.Service.SQS);

    @Test
    void publishedContractCanBeReceivedAndDeserializedFromLocalStack()
            throws Exception {
        ObjectMapper objectMapper =
                JsonMapper.builder().addModule(new JavaTimeModule()).build();
        try (SqsClient sqsClient =
                SqsClient.builder()
                        .endpointOverride(LOCALSTACK.getEndpointOverride(SQS))
                        .region(Region.of(LOCALSTACK.getRegion()))
                        .credentialsProvider(
                                StaticCredentialsProvider.create(
                                        AwsBasicCredentials.create(
                                                LOCALSTACK.getAccessKey(),
                                                LOCALSTACK.getSecretKey())))
                        .build()) {
            String queueUrl =
                    sqsClient.createQueue(
                                    CreateQueueRequest.builder()
                                            .queueName("meal-plan-events")
                                            .build())
                            .queueUrl();
            SqsMealPlanEventPublisher publisher =
                    new SqsMealPlanEventPublisher(
                            sqsClient,
                            objectMapper,
                            new SqsMessagingProperties(
                                    true,
                                    queueUrl,
                                    LOCALSTACK.getRegion(),
                                    LOCALSTACK.getEndpointOverride(SQS),
                                    1000));
            MealPlanSubmittedV1 expected =
                    new MealPlanSubmittedV1(
                            UUID.randomUUID(),
                            1,
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            Instant.parse("2026-07-19T20:00:00Z"));

            publisher.publish(expected);

            String body =
                    sqsClient.receiveMessage(
                                    ReceiveMessageRequest.builder()
                                            .queueUrl(queueUrl)
                                            .waitTimeSeconds(1)
                                            .maxNumberOfMessages(1)
                                            .build())
                            .messages()
                            .getFirst()
                            .body();
            assertThat(
                            objectMapper.readValue(
                                    body, MealPlanSubmittedV1.class))
                    .isEqualTo(expected);
        }
    }

    private static final LocalStackContainer.Service SQS =
            LocalStackContainer.Service.SQS;
}
