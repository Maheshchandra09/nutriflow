package com.nutriflow.api.recipe;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nutriflow.api.common.DomainException;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DietAttributeValidatorTest {

    private final DietAttributeValidator validator = new DietAttributeValidator();

    @Test
    void acceptsExactAttributesForEveryDietType() {
        assertThatCode(
                        () ->
                                validator.validate(
                                        DietType.KETO,
                                        Map.of(
                                                "netCarbsG", new BigDecimal("10"),
                                                "fatG", new BigDecimal("40"),
                                                "proteinG", new BigDecimal("25"),
                                                "ketoRatio", new BigDecimal("1.6"))))
                .doesNotThrowAnyException();
        assertThatCode(
                        () ->
                                validator.validate(
                                        DietType.VEGAN,
                                        Map.of(
                                                "proteinSource", "tofu",
                                                "b12Fortified", true,
                                                "veganCertified", true)))
                .doesNotThrowAnyException();
        assertThatCode(
                        () ->
                                validator.validate(
                                        DietType.DIABETIC_FRIENDLY,
                                        Map.of(
                                                "glycemicIndex", 45,
                                                "sugarG", new BigDecimal("4"),
                                                "carbExchangeUnits", new BigDecimal("2"))))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingOrUnknownAttributes() {
        assertThatThrownBy(
                        () ->
                                validator.validate(
                                        DietType.VEGAN,
                                        Map.of(
                                                "proteinSource", "tofu",
                                                "b12Fortified", true,
                                                "unexpected", true)))
                .isInstanceOf(DomainException.class)
                .extracting("fieldPath")
                .isEqualTo("recipe.dietAttributes");
    }

    @Test
    void rejectsIncorrectAttributeTypesAndRanges() {
        assertThatThrownBy(
                        () ->
                                validator.validate(
                                        DietType.DIABETIC_FRIENDLY,
                                        Map.of(
                                                "glycemicIndex", 101,
                                                "sugarG", "four",
                                                "carbExchangeUnits", 2)))
                .isInstanceOf(DomainException.class)
                .extracting("fieldPath")
                .isEqualTo("recipe.dietAttributes.glycemicIndex");
    }
}
