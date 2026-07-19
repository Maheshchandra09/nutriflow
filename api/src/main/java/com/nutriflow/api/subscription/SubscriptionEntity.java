package com.nutriflow.api.subscription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "subscriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionEntity {

    @Id
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "nutritionist_id", nullable = false)
    private UUID nutritionistId;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_tier", nullable = false, length = 30)
    private PlanTier planTier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SubscriptionStatus status;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    public SubscriptionEntity(
            UUID id,
            UUID clientId,
            UUID nutritionistId,
            PlanTier planTier,
            SubscriptionStatus status,
            LocalDate startDate) {
        this.id = id;
        this.clientId = clientId;
        this.nutritionistId = nutritionistId;
        this.planTier = planTier;
        this.status = status;
        this.startDate = startDate;
    }

    @PrePersist
    void assignId() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

}
