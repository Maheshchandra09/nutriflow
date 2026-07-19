package com.nutriflow.api.subscription;

import static com.nutriflow.api.common.DomainErrors.conflict;
import static com.nutriflow.api.common.DomainErrors.notFound;
import static com.nutriflow.api.common.DomainErrors.validation;

import com.nutriflow.api.user.UserEntity;
import com.nutriflow.api.user.UserRepository;
import com.nutriflow.api.user.UserRole;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    @Transactional
    public SubscriptionEntity create(CreateSubscriptionCommand command) {
        UserEntity client =
                userRepository
                        .findById(command.clientId())
                        .orElseThrow(() -> notFound("client", command.clientId()));
        UserEntity nutritionist =
                userRepository
                        .findById(command.nutritionistId())
                        .orElseThrow(
                                () -> notFound("nutritionist", command.nutritionistId()));
        if (client.getRole() != UserRole.CLIENT) {
            throw validation("subscription.clientId", "User must have the CLIENT role");
        }
        if (nutritionist.getRole() != UserRole.NUTRITIONIST) {
            throw validation(
                    "subscription.nutritionistId",
                    "User must have the NUTRITIONIST role");
        }
        if (command.planTier() == null || command.status() == null) {
            throw validation(
                    "subscription", "Plan tier and subscription status are required");
        }
        if (command.startDate() == null
                || command.startDate().isAfter(LocalDate.now())) {
            throw validation(
                    "subscription.startDate", "Start date is required and cannot be future-dated");
        }
        if (command.status() == SubscriptionStatus.ACTIVE
                && subscriptionRepository.existsByClientIdAndStatus(
                        command.clientId(), SubscriptionStatus.ACTIVE)) {
            throw conflict(
                    "subscription.clientId", "Client already has an active subscription");
        }
        return subscriptionRepository.save(
                new SubscriptionEntity(
                        null,
                        command.clientId(),
                        command.nutritionistId(),
                        command.planTier(),
                        command.status(),
                        command.startDate()));
    }
}
