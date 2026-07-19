package com.nutriflow.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.nutriflow.api.nutrition.NutritionTargetEntity;
import com.nutriflow.api.nutrition.NutritionTargetRepository;
import com.nutriflow.api.recipe.DietType;
import com.nutriflow.api.recipe.Ingredient;
import com.nutriflow.api.recipe.Macros;
import com.nutriflow.api.recipe.RecipeDocument;
import com.nutriflow.api.recipe.RecipeRepository;
import com.nutriflow.api.subscription.PlanTier;
import com.nutriflow.api.subscription.SubscriptionEntity;
import com.nutriflow.api.subscription.SubscriptionRepository;
import com.nutriflow.api.subscription.SubscriptionStatus;
import com.nutriflow.api.user.UserEntity;
import com.nutriflow.api.user.UserRepository;
import com.nutriflow.api.user.UserRole;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
class PersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGODB = new MongoDBContainer("mongo:8");

    @Autowired private UserRepository userRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private NutritionTargetRepository nutritionTargetRepository;
    @Autowired private RecipeRepository recipeRepository;

    @Test
    void persistsRelationalAggregatesAfterFlywayMigration() {
        UserEntity client =
                userRepository.save(
                        new UserEntity(
                                null, "Client One", " CLIENT@Example.com ", UserRole.CLIENT));
        UserEntity nutritionist =
                userRepository.save(
                        new UserEntity(
                                null,
                                "Nutritionist One",
                                "nutritionist@example.com",
                                UserRole.NUTRITIONIST));

        subscriptionRepository.save(
                new SubscriptionEntity(
                        null,
                        client.getId(),
                        nutritionist.getId(),
                        PlanTier.BASIC,
                        SubscriptionStatus.ACTIVE,
                        LocalDate.now()));
        nutritionTargetRepository.save(
                new NutritionTargetEntity(
                        client.getId(),
                        2_000,
                        new BigDecimal("120"),
                        new BigDecimal("210"),
                        new BigDecimal("70")));

        assertThat(userRepository.findByEmail("client@example.com")).isPresent();
        assertThat(
                        subscriptionRepository.findByClientIdAndStatus(
                                client.getId(), SubscriptionStatus.ACTIVE))
                .isPresent();
        assertThat(nutritionTargetRepository.findById(client.getId())).isPresent();
    }

    @Test
    void persistsAndQueriesMongoRecipeDocument() {
        RecipeDocument recipe =
                recipeRepository.save(
                        new RecipeDocument(
                                null,
                                "Tofu bowl",
                                "Plant-based dinner",
                                DietType.VEGAN,
                                List.of("Cook tofu", "Assemble bowl"),
                                UUID.randomUUID(),
                                List.of(
                                        new Ingredient(
                                                "Tofu", new BigDecimal("200"), "g")),
                                new Macros(
                                        450,
                                        new BigDecimal("30"),
                                        new BigDecimal("42"),
                                        new BigDecimal("18")),
                                Map.of("containsAnimalProducts", false)));

        assertThat(recipeRepository.findByIdAndActiveTrue(recipe.getId())).isPresent();
        assertThat(
                        recipeRepository.findAllByDietTypeAndActiveTrue(
                                DietType.VEGAN,
                                org.springframework.data.domain.PageRequest.of(0, 20)))
                .extracting(RecipeDocument::getId)
                .contains(recipe.getId());
    }
}
