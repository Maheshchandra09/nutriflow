package com.nutriflow.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.nutriflow.contracts.MealPlanSubmittedV1;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.bson.Document;
import org.bson.UuidRepresentation;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class WorkerPersistenceIntegrationTest {

    @Container
    private static final MongoDBContainer MONGODB =
            new MongoDBContainer(DockerImageName.parse("mongo:8"));

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                            DockerImageName.parse("postgres:17-alpine"))
                    .withDatabaseName("nutriflow")
                    .withUsername("nutriflow")
                    .withPassword("nutriflow");

    private static MongoClient mongoClient;
    private static MongoDatabase database;

    @BeforeAll
    static void setUpDatabases() throws Exception {
        mongoClient =
                MongoClients.create(
                        MongoClientSettings.builder()
                                .applyConnectionString(
                                        new ConnectionString(
                                                MONGODB.getReplicaSetUrl()))
                                .uuidRepresentation(
                                        UuidRepresentation.JAVA_LEGACY)
                                .build());
        database = mongoClient.getDatabase("nutriflow");
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

    @AfterAll
    static void closeMongoClient() {
        if (mongoClient != null) {
            mongoClient.close();
        }
    }

    @Test
    void workerProcessesPersistedPlanAndDuplicateDeliveryIsNoOp()
            throws Exception {
        UUID clientId = UUID.randomUUID();
        UUID mealPlanId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String recipeId = UUID.randomUUID().toString();
        insertTarget(clientId);
        insertRecipe(recipeId);
        insertSubmittedPlan(mealPlanId, clientId, recipeId);

        MongoWorkerDataStore mongoStore =
                new MongoWorkerDataStore(
                        database,
                        Clock.fixed(
                                Instant.parse("2026-07-19T20:01:00Z"),
                                ZoneOffset.UTC));
        MealPlanProcessor processor =
                new MealPlanProcessor(
                        mongoStore,
                        mongoStore,
                        new JdbcNutritionTargetDataStore(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword()),
                        new MealPlanCalculator());
        MealPlanSubmittedV1 event =
                new MealPlanSubmittedV1(
                        eventId,
                        1,
                        mealPlanId,
                        clientId,
                        Instant.parse("2026-07-19T20:00:00Z"));

        processor.process(event);
        Document firstResult =
                database.getCollection("meal_plans")
                        .find(new Document("_id", mealPlanId.toString()))
                        .first();
        processor.process(event);
        Document afterDuplicate =
                database.getCollection("meal_plans")
                        .find(new Document("_id", mealPlanId.toString()))
                        .first();

        assertNotNull(firstResult);
        assertEquals("PROCESSED", firstResult.getString("status"));
        assertEquals(eventId, firstResult.get("processedEventId", UUID.class));
        Document result = firstResult.get("result", Document.class);
        assertEquals(
                2100,
                result.get("weeklyTotals", Document.class)
                        .getInteger("calories"));
        Document groceryItem =
                result.getList("groceryList", Document.class).getFirst();
        assertEquals("spinach", groceryItem.getString("name"));
        assertEquals(
                new Decimal128(new java.math.BigDecimal("700")),
                groceryItem.get("quantity", Decimal128.class));
        assertEquals(
                firstResult.get("result"), afterDuplicate.get("result"));
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
        database.getCollection("recipes")
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
        database.getCollection("meal_plans")
                .insertOne(
                        new Document("_id", mealPlanId.toString())
                                .append("clientId", clientId)
                                .append("status", "SUBMITTED")
                                .append("days", days)
                                .append(
                                        "updatedAt",
                                        Date.from(
                                                Instant.parse(
                                                        "2026-07-19T20:00:00Z"))));
    }

    private Decimal128 decimal(String value) {
        return new Decimal128(new java.math.BigDecimal(value));
    }
}
