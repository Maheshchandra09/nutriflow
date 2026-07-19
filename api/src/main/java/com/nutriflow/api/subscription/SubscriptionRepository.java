package com.nutriflow.api.subscription;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<SubscriptionEntity, UUID> {

    Optional<SubscriptionEntity> findByClientIdAndStatus(
            UUID clientId, SubscriptionStatus status);

    List<SubscriptionEntity> findAllByNutritionistIdAndStatus(
            UUID nutritionistId, SubscriptionStatus status);
}
