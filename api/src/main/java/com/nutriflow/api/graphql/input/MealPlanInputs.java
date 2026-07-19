package com.nutriflow.api.graphql.input;

import com.nutriflow.api.mealplan.MealPlanCommand;
import com.nutriflow.api.mealplan.MealPlanDay;
import com.nutriflow.api.mealplan.PlannedMeal;
import com.nutriflow.api.recipe.MealType;
import java.time.LocalDate;
import java.util.List;

public final class MealPlanInputs {

    private MealPlanInputs() {}

    public record PlannedMealInput(String recipeId, MealType mealType) {

        PlannedMeal toValue() {
            return new PlannedMeal(recipeId, mealType);
        }
    }

    public record MealPlanDayInput(LocalDate date, List<PlannedMealInput> meals) {

        MealPlanDay toValue() {
            return new MealPlanDay(
                    date, meals.stream().map(PlannedMealInput::toValue).toList());
        }
    }

    public record MealPlanInput(
            String clientId,
            String nutritionistId,
            LocalDate weekStartDate,
            List<MealPlanDayInput> days) {

        public MealPlanCommand toCommand() {
            return new MealPlanCommand(
                    GraphQlInputSupport.uuid(clientId, "mealPlan.clientId"),
                    GraphQlInputSupport.uuid(
                            nutritionistId, "mealPlan.nutritionistId"),
                    weekStartDate,
                    toDays(days));
        }
    }

    public record MealPlanScheduleInput(
            LocalDate weekStartDate, List<MealPlanDayInput> days) {

        public List<MealPlanDay> mappedDays() {
            return toDays(days);
        }
    }

    private static List<MealPlanDay> toDays(List<MealPlanDayInput> days) {
        return days.stream().map(MealPlanDayInput::toValue).toList();
    }
}
