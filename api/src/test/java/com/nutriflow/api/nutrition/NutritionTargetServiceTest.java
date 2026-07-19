package com.nutriflow.api.nutrition;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.nutriflow.api.common.DomainException;
import com.nutriflow.api.user.UserEntity;
import com.nutriflow.api.user.UserRepository;
import com.nutriflow.api.user.UserRole;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NutritionTargetServiceTest {

    @Mock private NutritionTargetRepository targetRepository;
    @Mock private UserRepository userRepository;

    @Test
    void rejectsNonPositiveTargets() {
        UUID clientId = UUID.randomUUID();
        when(userRepository.findById(clientId))
                .thenReturn(
                        Optional.of(
                                new UserEntity(
                                        clientId,
                                        "Client",
                                        "client@example.com",
                                        UserRole.CLIENT)));
        NutritionTargetService service =
                new NutritionTargetService(targetRepository, userRepository);

        assertThatThrownBy(
                        () ->
                                service.set(
                                        new SetNutritionTargetCommand(
                                                clientId,
                                                2_000,
                                                BigDecimal.ZERO,
                                                new BigDecimal("200"),
                                                new BigDecimal("70"))))
                .isInstanceOf(DomainException.class)
                .extracting("fieldPath")
                .isEqualTo("nutritionTarget");
    }
}
