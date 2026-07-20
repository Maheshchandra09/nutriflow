package com.nutriflow.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.nutriflow.api.nutrition.NutritionTargetEntity;
import com.nutriflow.api.nutrition.NutritionTargetRepository;
import com.nutriflow.api.recipe.DietType;
import com.nutriflow.api.recipe.Ingredient;
import com.nutriflow.api.recipe.Macros;
import com.nutriflow.api.recipe.RecipeDocument;
import com.nutriflow.api.recipe.RecipeRepository;
import com.nutriflow.api.recipe.RecipeService;
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
import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
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
    @Autowired private RecipeService recipeService;
    @Autowired private MongoTemplate mongoTemplate;

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
        Document persisted =
                mongoTemplate
                        .getCollection("recipes")
                        .find(new Document("_id", recipe.getId()))
                        .first();
        assertThat(persisted).isNotNull();
        assertThat(
                        persisted
                                .get("macros", Document.class)
                                .get("proteinGrams"))
                .isInstanceOf(Decimal128.class);
        assertThat(
                        persisted
                                .getList("ingredients", Document.class)
                                .getFirst()
                                .get("quantity"))
                .isInstanceOf(Decimal128.class);
    }

    @Test
    void findsSwapCandidateAcrossDecimal128ProteinRange() {
        RecipeDocument original =
                recipeRepository.save(
                        recipeWithMacros("Original tofu bowl", 650, "35"));
        RecipeDocument candidate =
                recipeRepository.save(
                        recipeWithMacros("Tempeh alternative", 630, "34"));
        recipeRepository.save(
                recipeWithMacros("Low-protein alternative", 620, "20"));

        assertThat(recipeService.swapSuggestions(original.getId(), 20))
                .extracting(RecipeDocument::getId)
                .containsExactly(candidate.getId());
    }

    private RecipeDocument recipeWithMacros(
            String name, int calories, String proteinGrams) {
        return new RecipeDocument(
                null,
                name,
                "Swap query integration fixture",
                DietType.VEGAN,
                List.of("Prepare", "Serve"),
                UUID.randomUUID(),
                List.of(new Ingredient("Ingredient", BigDecimal.ONE, "g")),
                new Macros(
                        calories,
                        new BigDecimal(proteinGrams),
                        new BigDecimal("75"),
                        new BigDecimal("20")),
                Map.of(
                        "proteinSource", "soy",
                        "b12Fortified", false,
                        "veganCertified", true));
    }
}
