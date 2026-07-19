package com.nutriflow.api.nutrition;

import static com.nutriflow.api.common.DomainErrors.notFound;
import static com.nutriflow.api.common.DomainErrors.validation;

import com.nutriflow.api.user.UserEntity;
import com.nutriflow.api.user.UserRepository;
import com.nutriflow.api.user.UserRole;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NutritionTargetService {

    private final NutritionTargetRepository nutritionTargetRepository;
    private final UserRepository userRepository;

    @Transactional
    public NutritionTargetEntity set(SetNutritionTargetCommand command) {
        UserEntity client =
                userRepository
                        .findById(command.clientId())
                        .orElseThrow(() -> notFound("client", command.clientId()));
        if (client.getRole() != UserRole.CLIENT) {
            throw validation("nutritionTarget.clientId", "User must have the CLIENT role");
        }
        if (command.dailyCalories() == null
                || command.dailyCalories() <= 0
                || !isPositive(command.proteinGrams())
                || !isPositive(command.carbohydrateGrams())
                || !isPositive(command.fatGrams())) {
            throw validation(
                    "nutritionTarget", "Calories and all macro targets must be positive");
        }
        return nutritionTargetRepository.save(
                new NutritionTargetEntity(
                        command.clientId(),
                        command.dailyCalories(),
                        command.proteinGrams(),
                        command.carbohydrateGrams(),
                        command.fatGrams()));
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }
}
