package com.nutriflow.worker;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.nutriflow.contracts.MealPlanSubmittedV1;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.bson.Document;
import org.bson.UuidRepresentation;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.wait.strategy.Wait;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.CreateEventSourceMappingRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionRequest;
import software.amazon.awssdk.services.lambda.model.Environment;
import software.amazon.awssdk.services.lambda.model.FunctionCode;
import software.amazon.awssdk.services.lambda.model.GetEventSourceMappingRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionConfigurationRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionConfigurationResponse;
import software.amazon.awssdk.services.lambda.model.Runtime;
import software.amazon.awssdk.services.lambda.model.State;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Testcontainers
class LocalStackSqsLambdaIT {

    private static final String NETWORK_NAME =
            "nutriflow-lambda-it-" + UUID.randomUUID();
    private static final Network NETWORK =
            Network.builder()
                    .createNetworkCmdModifier(
                            command -> command.withName(NETWORK_NAME))
                    .build();

    @Container
    private static final GenericContainer<?> MONGODB =
            new GenericContainer<>(DockerImageName.parse("mongo:8"))
                    .withExposedPorts(27017)
                    .withNetwork(NETWORK)
                    .withNetworkAliases("mongodb")
                    .waitingFor(Wait.forListeningPort());

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                            DockerImageName.parse("postgres:17-alpine"))
                    .withDatabaseName("nutriflow")
                    .withUsername("nutriflow")
                    .withPassword("nutriflow")
                    .withNetwork(NETWORK)
                    .withNetworkAliases("postgres");

    @Container
    private static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(
                            DockerImageName.parse("localstack/localstack:4.0.3"))
                    .withServices(
                            LocalStackContainer.Service.SQS,
                            LocalStackContainer.Service.LAMBDA)
                    .withNetwork(NETWORK)
                    .withNetworkAliases("localstack")
                    .withEnv("LAMBDA_DOCKER_NETWORK", NETWORK_NAME);

    private static MongoClient mongoClient;
    private static MongoDatabase mongoDatabase;
    private static LambdaClient lambdaClient;
    private static SqsClient sqsClient;

    @BeforeAll
    static void setUpClients() throws Exception {
        mongoClient =
                MongoClients.create(
                        MongoClientSettings.builder()
                                .applyConnectionString(
                                        new ConnectionString(
                                                "mongodb://"
                                                        + MONGODB.getHost()
                                                        + ":"
                                                        + MONGODB.getMappedPort(
                                                                27017)
                                                        + "/nutriflow"))
                                .uuidRepresentation(
                                        UuidRepresentation.JAVA_LEGACY)
                                .build());
        mongoDatabase = mongoClient.getDatabase("nutriflow");
        var credentials =
                StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                LOCALSTACK.getAccessKey(),
                                LOCALSTACK.getSecretKey()));
        Region region = Region.of(LOCALSTACK.getRegion());
        lambdaClient =
                LambdaClient.builder()
                        .endpointOverride(
                                LOCALSTACK.getEndpointOverride(
                                        LocalStackContainer.Service.LAMBDA))
                        .region(region)
                        .credentialsProvider(credentials)
                        .httpClientBuilder(UrlConnectionHttpClient.builder())
                        .build();
        sqsClient =
                SqsClient.builder()
                        .endpointOverride(
                                LOCALSTACK.getEndpointOverride(
                                        LocalStackContainer.Service.SQS))
                        .region(region)
                        .credentialsProvider(credentials)
                        .httpClientBuilder(UrlConnectionHttpClient.builder())
                        .build();
        createNutritionTargetTable();
    }

    @AfterAll
    static void closeClientsAndNetwork() {
        if (lambdaClient != null) {
            lambdaClient.close();
        }
        if (sqsClient != null) {
            sqsClient.close();
        }
        if (mongoClient != null) {
            mongoClient.close();
        }
        NETWORK.close();
    }

    @Test
    void sqsEventSourceInvokesPackagedWorkerAndPersistsResult()
            throws Exception {
        UUID clientId = UUID.randomUUID();
        UUID mealPlanId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String recipeId = UUID.randomUUID().toString();
        insertTarget(clientId);
        insertRecipe(recipeId);
        insertSubmittedPlan(mealPlanId, clientId, recipeId);

        String queueUrl =
                sqsClient.createQueue(
                                CreateQueueRequest.builder()
                                        .queueName("meal-plan-lambda-it")
                                        .attributes(
                                                Map.of(
                                                        QueueAttributeName
                                                                .VISIBILITY_TIMEOUT,
                                                        "5"))
                                        .build())
                        .queueUrl();
        String queueArn =
                sqsClient.getQueueAttributes(
                                GetQueueAttributesRequest.builder()
                                        .queueUrl(queueUrl)
                                        .attributeNames(
                                                QueueAttributeName.QUEUE_ARN)
                                        .build())
                        .attributes()
                        .get(QueueAttributeName.QUEUE_ARN);
        String functionName = "nutriflow-worker-it-" + UUID.randomUUID();
        deployFunction(functionName);
        String mappingId =
                lambdaClient.createEventSourceMapping(
                                CreateEventSourceMappingRequest.builder()
                                        .functionName(functionName)
                                        .eventSourceArn(queueArn)
                                        .batchSize(1)
                                        .enabled(true)
                                        .build())
                        .uuid();
        awaitMappingEnabled(mappingId);

        MealPlanSubmittedV1 event =
                new MealPlanSubmittedV1(
                        eventId,
                        MealPlanSubmittedV1.SCHEMA_VERSION,
                        mealPlanId,
                        clientId,
                        Instant.parse("2026-07-20T06:00:00Z"));
        ObjectMapper objectMapper =
                JsonMapper.builder().addModule(new JavaTimeModule()).build();
        sqsClient.sendMessage(
                SendMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .messageBody(objectMapper.writeValueAsString(event))
                        .build());

        await().atMost(Duration.ofMinutes(3))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(
                        () -> {
                            Document plan =
                                    mongoDatabase
                                            .getCollection("meal_plans")
                                            .find(
                                                    new Document(
                                                            "_id",
                                                            mealPlanId
                                                                    .toString()))
                                            .first();
                            assertNotNull(plan);
                            assertEquals(
                                    "PROCESSED", plan.getString("status"),
                                    () ->
                                            "Worker error: "
                                                    + plan.getString(
                                                            "processingError"));
                            assertEquals(
                                    eventId,
                                    plan.get(
                                            "processedEventId",
                                            UUID.class));
                            Document result =
                                    plan.get("result", Document.class);
                            assertNotNull(result);
                            assertEquals(
                                    2100,
                                    result.get(
                                                    "weeklyTotals",
                                                    Document.class)
                                            .getInteger("calories"));
                        });
    }

    private void deployFunction(String functionName) throws Exception {
        Path artifact =
                Path.of(
                        "target",
                        "worker-0.1.0-SNAPSHOT.jar");
        if (!Files.isRegularFile(artifact)) {
            throw new IllegalStateException(
                    "Failsafe requires packaged worker artifact: " + artifact);
        }
        lambdaClient.createFunction(
                CreateFunctionRequest.builder()
                        .functionName(functionName)
                        .runtime(Runtime.JAVA21)
                        .handler(
                                "com.nutriflow.worker.MealPlanEventHandler::handleRequest")
                        .role(
                                "arn:aws:iam::000000000000:role/nutriflow-lambda-role")
                        .timeout(60)
                        .memorySize(512)
                        .environment(
                                Environment.builder()
                                        .variables(
                                                Map.of(
                                                        "MONGODB_URI",
                                                        "mongodb://mongodb:27017/nutriflow",
                                                        "MONGODB_DATABASE",
                                                        "nutriflow",
                                                        "POSTGRES_URL",
                                                        "jdbc:postgresql://postgres:5432/nutriflow",
                                                        "POSTGRES_USER",
                                                        "nutriflow",
                                                        "POSTGRES_PASSWORD",
                                                        "nutriflow"))
                                        .build())
                        .code(
                                FunctionCode.builder()
                                        .zipFile(
                                                SdkBytes.fromByteArray(
                                                        Files.readAllBytes(
                                                                artifact)))
                                        .build())
                        .build());
        await().atMost(Duration.ofMinutes(2))
                .pollInterval(Duration.ofSeconds(1))
                .until(
                        () ->
                                functionConfiguration(functionName).state()
                                        == State.ACTIVE);
    }

    private GetFunctionConfigurationResponse functionConfiguration(
            String functionName) {
        return lambdaClient.getFunctionConfiguration(
                GetFunctionConfigurationRequest.builder()
                        .functionName(functionName)
                        .build());
    }

    private void awaitMappingEnabled(String mappingId) {
        await().atMost(Duration.ofMinutes(1))
                .pollInterval(Duration.ofSeconds(1))
                .until(
                        () ->
                                "Enabled"
                                        .equals(
                                                lambdaClient
                                                        .getEventSourceMapping(
                                                                GetEventSourceMappingRequest
                                                                        .builder()
                                                                        .uuid(
                                                                                mappingId)
                                                                        .build())
                                                        .state()));
    }

    private static void createNutritionTargetTable() throws Exception {
        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement = connection.createStatement()) {
            statement.execute(
                    """
                    CREATE TABLE nutrition_targets (
                        client_id UUID PRIMARY KEY,
                        daily_calorie_target INTEGER NOT NULL,
                        protein_target_g NUMERIC(8,2) NOT NULL,
                        carb_target_g NUMERIC(8,2) NOT NULL,
                        fat_target_g NUMERIC(8,2) NOT NULL
                    )
                    """);
        }
    }

    private void insertTarget(UUID clientId) throws Exception {
        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                """
                                INSERT INTO nutrition_targets
                                (client_id, daily_calorie_target, protein_target_g,
                                 carb_target_g, fat_target_g)
                                VALUES (?, 300, 20, 30, 10)
                                """)) {
            statement.setObject(1, clientId);
            statement.executeUpdate();
        }
    }

    private void insertRecipe(String recipeId) {
        mongoDatabase.getCollection("recipes")
                .insertOne(
                        new Document("_id", recipeId)
                                .append(
                                        "macros",
                                        new Document("calories", 300)
                                                .append(
                                                        "proteinGrams",
                                                        decimal("20"))
                                                .append(
                                                        "carbohydrateGrams",
                                                        decimal("30"))
                                                .append(
                                                        "fatGrams",
                                                        decimal("10")))
                                .append(
                                        "ingredients",
                                        List.of(
                                                new Document(
                                                                "name",
                                                                " Spinach ")
                                                        .append(
                                                                "quantity",
                                                                decimal("100"))
                                                        .append("unit", "g"))));
    }

    private void insertSubmittedPlan(
            UUID mealPlanId, UUID clientId, String recipeId) {
        List<Document> days =
                IntStream.range(0, 7)
                        .mapToObj(
                                ignored ->
                                        new Document(
                                                "meals",
                                                List.of(
                                                        new Document(
                                                                        "recipeId",
                                                                        recipeId)
                                                                .append(
                                                                        "mealType",
                                                                        "DINNER"))))
                        .toList();
        mongoDatabase.getCollection("meal_plans")
                .insertOne(
                        new Document("_id", mealPlanId.toString())
                                .append("clientId", clientId)
                                .append("status", "SUBMITTED")
                                .append("days", days));
    }

    private Decimal128 decimal(String value) {
        return new Decimal128(new java.math.BigDecimal(value));
    }
}
