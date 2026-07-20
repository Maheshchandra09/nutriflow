package com.nutriflow.worker;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.time.Clock;
import org.bson.UuidRepresentation;

final class WorkerRuntime {

    private WorkerRuntime() {}

    static MealPlanProcessor createProcessor() {
        String mongoUri =
                environment(
                        "MONGODB_URI", "mongodb://localhost:27017/nutriflow");
        String mongoDatabase =
                environment("MONGODB_DATABASE", databaseName(mongoUri));
        MongoClientSettings settings =
                MongoClientSettings.builder()
                        .applyConnectionString(new ConnectionString(mongoUri))
                        .uuidRepresentation(UuidRepresentation.JAVA_LEGACY)
                        .build();
        MongoClient mongoClient = MongoClients.create(settings);
        MongoWorkerDataStore mongoStore =
                new MongoWorkerDataStore(
                        mongoClient.getDatabase(mongoDatabase),
                        Clock.systemUTC());
        JdbcNutritionTargetDataStore targetStore =
                new JdbcNutritionTargetDataStore(
                        environment(
                                "POSTGRES_URL",
                                "jdbc:postgresql://localhost:5432/nutriflow"),
                        environment("POSTGRES_USER", "nutriflow"),
                        environment("POSTGRES_PASSWORD", "nutriflow"));
        return new MealPlanProcessor(
                mongoStore,
                mongoStore,
                targetStore,
                new MealPlanCalculator());
    }

    private static String environment(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String databaseName(String mongoUri) {
        String database = new ConnectionString(mongoUri).getDatabase();
        return database == null || database.isBlank() ? "nutriflow" : database;
    }
}
