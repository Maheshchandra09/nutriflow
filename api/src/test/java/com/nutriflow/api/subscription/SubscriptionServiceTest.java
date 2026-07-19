package com.nutriflow.api.subscription;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.nutriflow.api.common.DomainException;
import com.nutriflow.api.user.UserEntity;
import com.nutriflow.api.user.UserRepository;
import com.nutriflow.api.user.UserRole;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private UserRepository userRepository;

    @Test
    void rejectsSecondActiveSubscriptionForClient() {
        UUID clientId = UUID.randomUUID();
        UUID nutritionistId = UUID.randomUUID();
        when(userRepository.findById(clientId))
                .thenReturn(
                        Optional.of(
                                new UserEntity(
                                        clientId,
                                        "Client",
                                        "client@example.com",
                                        UserRole.CLIENT)));
        when(userRepository.findById(nutritionistId))
                .thenReturn(
                        Optional.of(
                                new UserEntity(
                                        nutritionistId,
                                        "Nutritionist",
                                        "nutritionist@example.com",
                                        UserRole.NUTRITIONIST)));
        when(subscriptionRepository.existsByClientIdAndStatus(
                        clientId, SubscriptionStatus.ACTIVE))
                .thenReturn(true);

        SubscriptionService service =
                new SubscriptionService(subscriptionRepository, userRepository);

        assertThatThrownBy(
                        () ->
                                service.create(
                                        new CreateSubscriptionCommand(
                                                clientId,
                                                nutritionistId,
                                                PlanTier.BASIC,
                                                SubscriptionStatus.ACTIVE,
                                                LocalDate.now())))
                .isInstanceOf(DomainException.class)
                .extracting("code")
                .isEqualTo("CONFLICT");
    }
}
