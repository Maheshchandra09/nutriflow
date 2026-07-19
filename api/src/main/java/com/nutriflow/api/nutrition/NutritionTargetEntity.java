package com.nutriflow.api.nutrition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "nutrition_targets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NutritionTargetEntity {

    @Id
    @Column(name = "client_id")
    private UUID clientId;

    @Column(name = "daily_calorie_target", nullable = false)
    private Integer dailyCalorieTarget;

    @Column(name = "protein_target_g", nullable = false, precision = 8, scale = 2)
    private BigDecimal proteinTargetGrams;

    @Column(name = "carb_target_g", nullable = false, precision = 8, scale = 2)
    private BigDecimal carbTargetGrams;

    @Column(name = "fat_target_g", nullable = false, precision = 8, scale = 2)
    private BigDecimal fatTargetGrams;

    public NutritionTargetEntity(
            UUID clientId,
            Integer dailyCalorieTarget,
            BigDecimal proteinTargetGrams,
            BigDecimal carbTargetGrams,
            BigDecimal fatTargetGrams) {
        this.clientId = clientId;
        this.dailyCalorieTarget = dailyCalorieTarget;
        this.proteinTargetGrams = proteinTargetGrams;
        this.carbTargetGrams = carbTargetGrams;
        this.fatTargetGrams = fatTargetGrams;
    }

}
