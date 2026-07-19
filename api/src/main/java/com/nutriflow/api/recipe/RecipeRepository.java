package com.nutriflow.api.recipe;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RecipeRepository extends MongoRepository<RecipeDocument, String> {

    Optional<RecipeDocument> findByIdAndActiveTrue(String id);

    List<RecipeDocument> findAllByDietTypeAndActiveTrue(
            DietType dietType, Pageable pageable);
}
