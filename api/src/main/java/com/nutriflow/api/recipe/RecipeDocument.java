package com.nutriflow.api.recipe;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("recipes")
@CompoundIndexes({
    @CompoundIndex(
            name = "recipe_diet_active_idx",
            def = "{'dietType': 1, 'active': 1}"),
    @CompoundIndex(
            name = "recipe_macro_search_idx",
            def = "{'macros.calories': 1, 'macros.proteinGrams': 1, 'active': 1}")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecipeDocument {

    @Id
    private String id;

    private String name;
    private String description;
    private DietType dietType;
    @Getter(AccessLevel.NONE)
    private List<Ingredient> ingredients;
    private Macros macros;
    @Getter(AccessLevel.NONE)
    private Map<String, Object> dietAttributes;

    @Getter(AccessLevel.NONE)
    private List<RecipeReview> reviews;
    private BigDecimal averageRating;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    @Version
    @Field("_version")
    private Long version;

    public RecipeDocument(
            String id,
            String name,
            String description,
            DietType dietType,
            List<Ingredient> ingredients,
            Macros macros,
            Map<String, Object> dietAttributes) {
        this.id = id == null ? UUID.randomUUID().toString() : id;
        this.name = name;
        this.description = description;
        this.dietType = dietType;
        this.ingredients = new ArrayList<>(ingredients);
        this.macros = macros;
        this.dietAttributes = new HashMap<>(dietAttributes);
        this.reviews = new ArrayList<>();
        this.averageRating = BigDecimal.ZERO;
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public List<Ingredient> getIngredients() {
        return List.copyOf(ingredients);
    }

    public Map<String, Object> getDietAttributes() {
        return Map.copyOf(dietAttributes);
    }

    public List<RecipeReview> getReviews() {
        return List.copyOf(reviews);
    }

}
