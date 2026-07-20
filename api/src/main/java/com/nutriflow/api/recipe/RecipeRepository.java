package com.nutriflow.api.recipe;

import java.util.List;
import java.util.Optional;
import org.bson.types.Decimal128;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface RecipeRepository extends MongoRepository<RecipeDocument, String> {

    Optional<RecipeDocument> findByIdAndActiveTrue(String id);

    List<RecipeDocument> findAllByDietTypeAndActiveTrue(
            DietType dietType, Pageable pageable);

    @Query(
            value =
                    "{ '_id': { $ne: ?0 }, 'dietType': ?1, 'active': true,"
                            + " 'macros.calories': { $gte: ?2, $lte: ?3 },"
                            + " 'macros.proteinGrams': { $gte: ?4, $lte: ?5 } }")
    List<RecipeDocument> findSwapCandidates(
            String excludedId,
            DietType dietType,
            int minimumCalories,
            int maximumCalories,
            Decimal128 minimumProtein,
            Decimal128 maximumProtein,
            Pageable pageable);
}
