package com.nutriflow.api.recipe;

import java.util.UUID;

public record ReviewCommand(UUID userId, int rating, String comment) {}
