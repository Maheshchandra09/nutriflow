package com.nutriflow.worker;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

final class JdbcNutritionTargetDataStore implements NutritionTargetDataStore {

    private static final String FIND_TARGET =
            """
            SELECT daily_calorie_target, protein_target_g, carb_target_g, fat_target_g
            FROM nutrition_targets
            WHERE client_id = ?
            """;

    private final String jdbcUrl;
    private final String username;
    private final String password;

    JdbcNutritionTargetDataStore(
            String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    @Override
    public Optional<ProcessingModels.NutritionTarget> findByClientId(
            UUID clientId) {
        try (var connection =
                        DriverManager.getConnection(jdbcUrl, username, password);
                var statement = connection.prepareStatement(FIND_TARGET)) {
            statement.setObject(1, clientId);
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(
                        new ProcessingModels.NutritionTarget(
                                result.getInt("daily_calorie_target"),
                                requiredDecimal(
                                        result.getBigDecimal("protein_target_g")),
                                requiredDecimal(
                                        result.getBigDecimal("carb_target_g")),
                                requiredDecimal(
                                        result.getBigDecimal("fat_target_g"))));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Could not load nutrition target", exception);
        }
    }

    private BigDecimal requiredDecimal(BigDecimal value) {
        if (value == null) {
            throw new IllegalStateException("Nutrition target is incomplete");
        }
        return value;
    }
}
