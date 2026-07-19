package com.nutriflow.api.recipe;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecipeDocumentTest {

    @Test
    void newRecipeHasPersistenceDefaults() {
        RecipeDocument recipe =
                new RecipeDocument(
                        null,
                        "Avocado bowl",
                        "A quick lunch",
                        DietType.KETO,
                        List.of("Slice avocado", "Assemble bowl"),
                        UUID.randomUUID(),
                        List.of(new Ingredient("Avocado", BigDecimal.ONE, "piece")),
                        new Macros(
                                320,
                                new BigDecimal("12"),
                                new BigDecimal("15"),
                                new BigDecimal("24")),
                        Map.of("netCarbsGrams", new BigDecimal("8")));

        assertThat(recipe.getId()).isNotBlank();
        assertThat(recipe.isActive()).isTrue();
        assertThat(recipe.getAverageRating()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(recipe.getReviews()).isEmpty();
        assertThat(recipe.getCreatedAt()).isEqualTo(recipe.getUpdatedAt());
    }
}
