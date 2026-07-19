package com.nutriflow.api.recipe;

import java.time.Instant;
import java.util.UUID;

public record RecipeReview(UUID userId, int rating, String comment, Instant createdAt) {}
