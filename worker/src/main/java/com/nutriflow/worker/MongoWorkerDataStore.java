package com.nutriflow.worker;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.set;
import static com.mongodb.client.model.Updates.unset;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.nutriflow.contracts.MealPlanSubmittedV1;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.bson.Document;
import org.bson.types.Decimal128;

final class MongoWorkerDataStore
        implements MealPlanProcessingStore, RecipeDataStore {

    private static final Set<String> TERMINAL_STATUSES =
            Set.of("PROCESSED", "FLAGGED");

    private final MongoCollection<Document> mealPlans;
    private final MongoCollection<Document> recipes;
    private final Clock clock;

    MongoWorkerDataStore(MongoDatabase database, Clock clock) {
        this.mealPlans = database.getCollection("meal_plans");
        this.recipes = database.getCollection("recipes");
        this.clock = clock;
    }

    @Override
    public ProcessingModels.ClaimResult claim(MealPlanSubmittedV1 event) {
        Document plan = requirePlan(event.mealPlanId());
        String status = plan.getString("status");
        UUID processedEventId = uuidValue(plan.get("processedEventId"));

        if (TERMINAL_STATUSES.contains(status)) {
            if (event.eventId().equals(processedEventId)) {
                return ProcessingModels.ClaimResult.DUPLICATE_TERMINAL;
            }
            throw new IllegalStateException(
                    "Meal plan already has a terminal result");
        }
        if ("PROCESSING".equals(status)) {
            if (event.eventId().equals(processedEventId)) {
                return ProcessingModels.ClaimResult.PROCESS;
            }
            throw new IllegalStateException(
                    "Meal plan is processing a different event");
        }
        if (!Set.of("SUBMITTED", "FAILED").contains(status)) {
            throw new IllegalStateException(
                    "Meal plan is not ready for processing");
        }

        var update =
                mealPlans.updateOne(
                        and(eq("_id", event.mealPlanId().toString()), eq("status", status)),
                        combine(
                                set("status", "PROCESSING"),
                                set("processedEventId", event.eventId()),
                                unset("processingError"),
                                set("updatedAt", Date.from(Instant.now(clock)))));
        if (update.getModifiedCount() != 1) {
            throw new IllegalStateException(
                    "Meal plan processing claim conflicted");
        }
        return ProcessingModels.ClaimResult.PROCESS;
    }

    @Override
    public ProcessingModels.MealPlanWork load(UUID mealPlanId) {
        Document plan = requirePlan(mealPlanId);
        List<String> recipeIds = new ArrayList<>();
        for (Document day : documents(plan, "days")) {
            for (Document meal : documents(day, "meals")) {
                recipeIds.add(requiredString(meal, "recipeId"));
            }
        }
        return new ProcessingModels.MealPlanWork(
                mealPlanId,
                requiredUuid(plan, "clientId"),
                recipeIds);
    }

    @Override
    public List<ProcessingModels.RecipeData> findAllById(
            Set<String> recipeIds) {
        if (recipeIds.isEmpty()) {
            return List.of();
        }
        return StreamSupport.stream(
                        recipes.find(in("_id", recipeIds)).spliterator(), false)
                .map(this::recipe)
                .toList();
    }

    @Override
    public void complete(
            UUID mealPlanId,
            UUID eventId,
            ProcessingModels.ProcessingResult result) {
        List<org.bson.conversions.Bson> updates =
                new ArrayList<>(
                        List.of(
                                set("status", result.status().name()),
                                set("result", resultDocument(result)),
                                unset("processingError"),
                                set("updatedAt", Date.from(Instant.now(clock)))));
        if (result.targetComparison() == null) {
            updates.add(unset("targetComparison"));
        } else {
            updates.add(
                    set(
                            "targetComparison",
                            comparisonDocument(result.targetComparison())));
        }
        var update =
                mealPlans.updateOne(
                        and(
                                eq("_id", mealPlanId.toString()),
                                eq("status", "PROCESSING"),
                                eq("processedEventId", eventId)),
                        combine(updates));
        if (update.getModifiedCount() == 1 || isCompletedBy(mealPlanId, eventId)) {
            return;
        }
        throw new IllegalStateException("Meal-plan completion lost its claim");
    }

    @Override
    public void fail(UUID mealPlanId, UUID eventId, String sanitizedError) {
        mealPlans.updateOne(
                and(
                        eq("_id", mealPlanId.toString()),
                        eq("status", "PROCESSING"),
                        eq("processedEventId", eventId)),
                combine(
                        set("status", "FAILED"),
                        set("processingError", sanitizedError),
                        set("updatedAt", Date.from(Instant.now(clock)))));
    }

    private Document requirePlan(UUID mealPlanId) {
        Document plan =
                mealPlans.find(eq("_id", mealPlanId.toString())).first();
        if (plan == null) {
            throw new IllegalStateException("Submitted meal plan was not found");
        }
        return plan;
    }

    private boolean isCompletedBy(UUID mealPlanId, UUID eventId) {
        Document plan = requirePlan(mealPlanId);
        return TERMINAL_STATUSES.contains(plan.getString("status"))
                && eventId.equals(uuidValue(plan.get("processedEventId")));
    }

    private ProcessingModels.RecipeData recipe(Document recipe) {
        Document macros = recipe.get("macros", Document.class);
        if (macros == null) {
            throw new IllegalStateException("Recipe macros are missing");
        }
        List<ProcessingModels.IngredientValue> ingredients =
                documents(recipe, "ingredients").stream()
                        .map(
                                ingredient ->
                                        new ProcessingModels.IngredientValue(
                                                requiredString(ingredient, "name"),
                                                decimalValue(
                                                        ingredient.get("quantity")),
                                                requiredString(ingredient, "unit")))
                        .toList();
        return new ProcessingModels.RecipeData(
                requiredString(recipe, "_id"),
                new ProcessingModels.MacroValues(
                        numberValue(macros, "calories").intValueExact(),
                        decimalValue(macros.get("proteinGrams")),
                        decimalValue(macros.get("carbohydrateGrams")),
                        decimalValue(macros.get("fatGrams"))),
                ingredients);
    }

    private Document resultDocument(ProcessingModels.ProcessingResult result) {
        ProcessingModels.MacroValues totals = result.weeklyTotals();
        Document macros =
                new Document("calories", totals.calories())
                        .append(
                                "proteinGrams",
                                decimal128(totals.proteinGrams()))
                        .append(
                                "carbohydrateGrams",
                                decimal128(totals.carbohydrateGrams()))
                        .append("fatGrams", decimal128(totals.fatGrams()));
        List<Document> groceryList =
                result.groceryList().stream()
                        .map(
                                ingredient ->
                                        new Document("name", ingredient.name())
                                                .append(
                                                        "quantity",
                                                        decimal128(
                                                                ingredient
                                                                        .quantity()))
                                                .append("unit", ingredient.unit()))
                        .toList();
        return new Document("weeklyTotals", macros)
                .append("groceryList", groceryList);
    }

    private Document comparisonDocument(
            ProcessingModels.TargetComparisonValues comparison) {
        return new Document(
                        "caloriePercentageDifference",
                        decimal128(comparison.caloriePercentageDifference()))
                .append(
                        "proteinPercentageDifference",
                        decimal128(comparison.proteinPercentageDifference()))
                .append(
                        "carbohydratePercentageDifference",
                        decimal128(
                                comparison.carbohydratePercentageDifference()))
                .append(
                        "fatPercentageDifference",
                        decimal128(comparison.fatPercentageDifference()));
    }

    private Decimal128 decimal128(BigDecimal value) {
        return new Decimal128(value);
    }

    private BigDecimal numberValue(Document document, String field) {
        return decimalValue(document.get(field));
    }

    private BigDecimal decimalValue(Object value) {
        if (value instanceof Decimal128 decimal) {
            return decimal.bigDecimalValue();
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value instanceof String text) {
            try {
                return new BigDecimal(text);
            } catch (NumberFormatException exception) {
                throw new IllegalStateException(
                        "Expected numeric persisted value", exception);
            }
        }
        throw new IllegalStateException("Expected numeric persisted value");
    }

    @SuppressWarnings("unchecked")
    private List<Document> documents(Document parent, String field) {
        List<?> values = parent.getList(field, Object.class, List.of());
        return values.stream()
                .map(
                        value -> {
                            if (value instanceof Document document) {
                                return document;
                            }
                            if (value instanceof java.util.Map<?, ?> map) {
                                return new Document((java.util.Map<String, Object>) map);
                            }
                            throw new IllegalStateException(
                                    "Expected embedded document list");
                        })
                .toList();
    }

    private String requiredString(Document document, String field) {
        Object value = document.get(field);
        if (value == null) {
            throw new IllegalStateException(
                    "Required persisted field is missing");
        }
        return value.toString();
    }

    private UUID requiredUuid(Document document, String field) {
        UUID value = uuidValue(document.get(field));
        if (value == null) {
            throw new IllegalStateException(
                    "Required persisted UUID is missing");
        }
        return value;
    }

    private UUID uuidValue(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof String text) {
            return UUID.fromString(text);
        }
        return null;
    }
}
