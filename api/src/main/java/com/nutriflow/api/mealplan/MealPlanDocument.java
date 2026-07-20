package com.nutriflow.api.mealplan;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("meal_plans")
@CompoundIndexes({
    @CompoundIndex(
            name = "meal_plan_client_status_idx",
            def = "{'clientId': 1, 'status': 1}"),
    @CompoundIndex(
            name = "meal_plan_outbox_status_idx",
            def = "{'submissionOutbox.status': 1}",
            partialFilter = "{'submissionOutbox.status': 'PENDING'}")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MealPlanDocument {

    @Id
    private String id;

    private UUID clientId;
    private UUID nutritionistId;
    private LocalDate weekStartDate;
    @Getter(AccessLevel.NONE)
    private List<MealPlanDay> days;
    private MealPlanStatus status;
    private MealPlanResult result;
    private TargetComparison targetComparison;
    private SubmissionOutboxEvent submissionOutbox;
    private UUID processedEventId;
    private String processingError;
    private Instant createdAt;
    private Instant updatedAt;

    @Version
    @Field("_version")
    private Long version;

    public MealPlanDocument(
            String id,
            UUID clientId,
            UUID nutritionistId,
            LocalDate weekStartDate,
            List<MealPlanDay> days) {
        this.id = id == null ? UUID.randomUUID().toString() : id;
        this.clientId = clientId;
        this.nutritionistId = nutritionistId;
        this.weekStartDate = weekStartDate;
        this.days = new ArrayList<>(days);
        this.status = MealPlanStatus.DRAFT;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public List<MealPlanDay> getDays() {
        return List.copyOf(days);
    }

    public void replaceSchedule(LocalDate weekStartDate, List<MealPlanDay> days) {
        this.weekStartDate = weekStartDate;
        this.days = new ArrayList<>(days);
        this.updatedAt = Instant.now();
    }

    public void submit(SubmissionOutboxEvent outboxEvent) {
        this.status = MealPlanStatus.SUBMITTED;
        this.submissionOutbox = outboxEvent;
        this.updatedAt = Instant.now();
    }

    public boolean markOutboxSent(UUID eventId, Instant sentAt) {
        if (submissionOutbox == null
                || submissionOutbox.status() != OutboxStatus.PENDING
                || !submissionOutbox.eventId().equals(eventId)) {
            return false;
        }
        submissionOutbox = submissionOutbox.markSent(sentAt);
        updatedAt = sentAt;
        return true;
    }
}
