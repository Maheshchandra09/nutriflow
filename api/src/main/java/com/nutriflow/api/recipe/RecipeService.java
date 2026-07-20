package com.nutriflow.api.recipe;

import static com.nutriflow.api.common.DomainErrors.conflict;
import static com.nutriflow.api.common.DomainErrors.notFound;
import static com.nutriflow.api.common.DomainErrors.validation;

import com.nutriflow.api.mealplan.MealPlanRepository;
import com.nutriflow.api.mealplan.MealPlanStatus;
import com.nutriflow.api.user.UserEntity;
import com.nutriflow.api.user.UserRepository;
import com.nutriflow.api.user.UserRole;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.bson.types.Decimal128;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecipeService {

    private static final BigDecimal SWAP_TOLERANCE = new BigDecimal("0.15");
    private static final Set<MealPlanStatus> LOCKING_PLAN_STATUSES =
            Set.of(MealPlanStatus.SUBMITTED, MealPlanStatus.PROCESSING);

    private final RecipeRepository recipeRepository;
    private final MealPlanRepository mealPlanRepository;
    private final UserRepository userRepository;
    private final DietAttributeValidator dietAttributeValidator;

    public RecipeDocument create(RecipeCommand command) {
        if (command == null) {
            throw validation("recipe", "Recipe input is required");
        }
        validateRecipe(
                command.name(),
                command.prepSteps(),
                command.ingredients(),
                command.macros(),
                command.dietType(),
                command.dietAttributes());
        if (command.createdBy() == null) {
            throw validation("recipe.createdBy", "Recipe creator is required");
        }
        UserEntity creator =
                userRepository
                        .findById(command.createdBy())
                        .orElseThrow(() -> notFound("user", command.createdBy()));
        if (creator.getRole() != UserRole.NUTRITIONIST && creator.getRole() != UserRole.ADMIN) {
            throw validation(
                    "recipe.createdBy", "Recipes must be created by a nutritionist or admin");
        }

        return recipeRepository.save(
                new RecipeDocument(
                        null,
                        command.name().trim(),
                        trimToNull(command.description()),
                        command.dietType(),
                        command.prepSteps(),
                        command.createdBy(),
                        command.ingredients(),
                        command.macros(),
                        command.dietAttributes()));
    }

    public RecipeDocument getActive(String id) {
        return recipeRepository
                .findByIdAndActiveTrue(id)
                .orElseThrow(() -> notFound("recipe", id));
    }

    public RecipeDocument update(String id, RecipeUpdateCommand command) {
        RecipeDocument recipe = getActive(id);
        ensureNotUsedByProcessingPlan(id);
        validateRecipe(
                command.name(),
                command.prepSteps(),
                command.ingredients(),
                command.macros(),
                command.dietType(),
                command.dietAttributes());
        recipe.update(
                command.name().trim(),
                trimToNull(command.description()),
                command.dietType(),
                command.prepSteps(),
                command.ingredients(),
                command.macros(),
                command.dietAttributes());
        return recipeRepository.save(recipe);
    }

    public RecipeDocument softDelete(String id) {
        RecipeDocument recipe = getActive(id);
        ensureNotUsedByProcessingPlan(id);
        recipe.softDelete();
        return recipeRepository.save(recipe);
    }

    public RecipeDocument addReview(String recipeId, ReviewCommand command) {
        RecipeDocument recipe = getActive(recipeId);
        if (command == null || command.userId() == null) {
            throw validation("review.userId", "Review user is required");
        }
        if (!userRepository.existsById(command.userId())) {
            throw notFound("user", command.userId());
        }
        if (command.rating() < 1 || command.rating() > 5) {
            throw validation("review.rating", "Rating must be between 1 and 5");
        }
        if (recipe.getReviews().stream()
                .anyMatch(review -> review.userId().equals(command.userId()))) {
            throw conflict("review.userId", "A user may review a recipe only once");
        }

        BigDecimal currentTotal =
                recipe.getReviews().stream()
                        .map(review -> BigDecimal.valueOf(review.rating()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        int newReviewCount = recipe.getReviews().size() + 1;
        BigDecimal newAverage =
                currentTotal
                        .add(BigDecimal.valueOf(command.rating()))
                        .divide(BigDecimal.valueOf(newReviewCount), 2, RoundingMode.HALF_UP);
        recipe.addReview(
                new RecipeReview(
                        command.userId(),
                        command.rating(),
                        trimToNull(command.comment()),
                        Instant.now()),
                newAverage);
        return recipeRepository.save(recipe);
    }

    public List<RecipeDocument> swapSuggestions(String recipeId, int size) {
        RecipeDocument original = getActive(recipeId);
        int boundedSize = Math.min(Math.max(size, 1), 100);
        BigDecimal calories = BigDecimal.valueOf(original.getMacros().calories());
        BigDecimal protein = original.getMacros().proteinGrams();
        int minimumCalories =
                calories
                        .multiply(BigDecimal.ONE.subtract(SWAP_TOLERANCE))
                        .setScale(0, RoundingMode.CEILING)
                        .intValueExact();
        int maximumCalories =
                calories
                        .multiply(BigDecimal.ONE.add(SWAP_TOLERANCE))
                        .setScale(0, RoundingMode.FLOOR)
                        .intValueExact();
        BigDecimal minimumProtein =
                protein.multiply(BigDecimal.ONE.subtract(SWAP_TOLERANCE));
        BigDecimal maximumProtein =
                protein.multiply(BigDecimal.ONE.add(SWAP_TOLERANCE));
        return recipeRepository.findSwapCandidates(
                recipeId,
                original.getDietType(),
                minimumCalories,
                maximumCalories,
                new Decimal128(minimumProtein),
                new Decimal128(maximumProtein),
                PageRequest.of(0, boundedSize));
    }

    private void ensureNotUsedByProcessingPlan(String recipeId) {
        if (mealPlanRepository.existsByDaysMealsRecipeIdAndStatusIn(
                recipeId, LOCKING_PLAN_STATUSES)) {
            throw conflict(
                    "recipe.id",
                    "Recipe cannot change while a submitted meal plan is processing");
        }
    }

    private void validateRecipe(
            String name,
            List<String> prepSteps,
            List<Ingredient> ingredients,
            Macros macros,
            DietType dietType,
            java.util.Map<String, Object> dietAttributes) {
        if (name == null || name.isBlank()) {
            throw validation("recipe.name", "Name is required");
        }
        if (prepSteps == null
                || prepSteps.isEmpty()
                || prepSteps.stream().anyMatch(step -> step == null || step.isBlank())) {
            throw validation(
                    "recipe.prepSteps", "At least one non-blank preparation step is required");
        }
        if (ingredients == null || ingredients.isEmpty()) {
            throw validation("recipe.ingredients", "At least one ingredient is required");
        }
        for (int index = 0; index < ingredients.size(); index++) {
            Ingredient ingredient = ingredients.get(index);
            String path = "recipe.ingredients[" + index + "]";
            if (ingredient == null
                    || ingredient.name() == null
                    || ingredient.name().isBlank()) {
                throw validation(path + ".name", "Ingredient name is required");
            }
            if (ingredient.quantity() == null || ingredient.quantity().signum() <= 0) {
                throw validation(path + ".quantity", "Ingredient quantity must be positive");
            }
            if (ingredient.unit() == null || ingredient.unit().isBlank()) {
                throw validation(path + ".unit", "Ingredient unit is required");
            }
        }
        if (macros == null
                || macros.calories() == null
                || macros.calories() <= 0
                || macros.proteinGrams() == null
                || macros.proteinGrams().signum() <= 0
                || macros.carbohydrateGrams() == null
                || macros.carbohydrateGrams().signum() <= 0
                || macros.fatGrams() == null
                || macros.fatGrams().signum() <= 0) {
            throw validation("recipe.macros", "All macro values must be positive");
        }
        dietAttributeValidator.validate(dietType, dietAttributes);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
