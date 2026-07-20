package com.nutriflow.api.recipe;

import java.math.BigDecimal;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

public record Macros(
        Integer calories,
        @Field(targetType = FieldType.DECIMAL128) BigDecimal proteinGrams,
        @Field(targetType = FieldType.DECIMAL128) BigDecimal carbohydrateGrams,
        @Field(targetType = FieldType.DECIMAL128) BigDecimal fatGrams) {}
