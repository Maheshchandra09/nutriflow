package com.nutriflow.api.recipe;

import static com.nutriflow.api.common.DomainErrors.validation;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class DietAttributeValidator {

    private static final Set<String> KETO_KEYS =
            Set.of("netCarbsG", "fatG", "proteinG", "ketoRatio");
    private static final Set<String> VEGAN_KEYS =
            Set.of("proteinSource", "b12Fortified", "veganCertified");
    private static final Set<String> DIABETIC_KEYS =
            Set.of("glycemicIndex", "sugarG", "carbExchangeUnits");

    public void validate(DietType dietType, Map<String, Object> attributes) {
        if (dietType == null) {
            throw validation("recipe.dietType", "Diet type is required");
        }
        if (attributes == null) {
            throw validation("recipe.dietAttributes", "Diet attributes are required");
        }

        Set<String> expected =
                switch (dietType) {
                    case KETO -> KETO_KEYS;
                    case VEGAN -> VEGAN_KEYS;
                    case DIABETIC_FRIENDLY -> DIABETIC_KEYS;
                };
        if (!attributes.keySet().equals(expected)) {
            throw validation(
                    "recipe.dietAttributes",
                    "Expected exactly these attributes for "
                            + dietType
                            + ": "
                            + expected);
        }

        switch (dietType) {
            case KETO -> {
                requireNonNegativeNumber(attributes, "netCarbsG");
                requirePositiveNumber(attributes, "fatG");
                requirePositiveNumber(attributes, "proteinG");
                requirePositiveNumber(attributes, "ketoRatio");
            }
            case VEGAN -> {
                requireText(attributes, "proteinSource");
                requireBoolean(attributes, "b12Fortified");
                requireBoolean(attributes, "veganCertified");
            }
            case DIABETIC_FRIENDLY -> {
                BigDecimal glycemicIndex = requireNonNegativeNumber(attributes, "glycemicIndex");
                if (glycemicIndex.compareTo(BigDecimal.valueOf(100)) > 0) {
                    throw validation(
                            "recipe.dietAttributes.glycemicIndex",
                            "Glycemic index must not exceed 100");
                }
                requireNonNegativeNumber(attributes, "sugarG");
                requirePositiveNumber(attributes, "carbExchangeUnits");
            }
        }
    }

    private BigDecimal requirePositiveNumber(Map<String, Object> attributes, String key) {
        BigDecimal value = requireNumber(attributes, key);
        if (value.signum() <= 0) {
            throw validation(path(key), "Must be greater than zero");
        }
        return value;
    }

    private BigDecimal requireNonNegativeNumber(Map<String, Object> attributes, String key) {
        BigDecimal value = requireNumber(attributes, key);
        if (value.signum() < 0) {
            throw validation(path(key), "Must not be negative");
        }
        return value;
    }

    private BigDecimal requireNumber(Map<String, Object> attributes, String key) {
        Object value = attributes.get(key);
        if (!(value instanceof Number number)) {
            throw validation(path(key), "Must be numeric");
        }
        return new BigDecimal(number.toString());
    }

    private void requireText(Map<String, Object> attributes, String key) {
        if (!(attributes.get(key) instanceof String value) || value.isBlank()) {
            throw validation(path(key), "Must be non-blank text");
        }
    }

    private void requireBoolean(Map<String, Object> attributes, String key) {
        if (!(attributes.get(key) instanceof Boolean)) {
            throw validation(path(key), "Must be boolean");
        }
    }

    private String path(String key) {
        return "recipe.dietAttributes." + key;
    }
}
