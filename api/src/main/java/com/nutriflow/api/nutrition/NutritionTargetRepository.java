package com.nutriflow.api.nutrition;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NutritionTargetRepository
        extends JpaRepository<NutritionTargetEntity, UUID> {}
