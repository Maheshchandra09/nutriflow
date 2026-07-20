package com.nutriflow.api.recipe;

import java.math.BigDecimal;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

public record Ingredient(
        String name,
        @Field(targetType = FieldType.DECIMAL128) BigDecimal quantity,
        String unit) {}
