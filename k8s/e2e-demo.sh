#!/usr/bin/env bash
set -euo pipefail

api_url="${NUTRIFLOW_API_URL:-http://localhost:8080/graphql}"
run_id="$(date +%s)"

graphql() {
    local payload="$1"
    local response
    response="$(curl --fail --silent --show-error \
        -H 'Content-Type: application/json' \
        --data "$payload" \
        "$api_url")"
    if grep -q '"errors"' <<<"$response"; then
        echo "GraphQL request failed: $response" >&2
        exit 1
    fi
    printf '%s\n' "$response"
}

extract_id() {
    grep -o '"id":"[^"]*"' | head -n 1 | cut -d'"' -f4
}

echo "Creating a client and nutritionist..."
client_response="$(graphql \
    "{\"query\":\"mutation { createUser(input: {name: \\\"Kubernetes Client\\\", email: \\\"client-${run_id}@example.com\\\", role: CLIENT}) { id } }\"}")"
client_id="$(printf '%s' "$client_response" | extract_id)"

nutritionist_response="$(graphql \
    "{\"query\":\"mutation { createUser(input: {name: \\\"Kubernetes Nutritionist\\\", email: \\\"nutritionist-${run_id}@example.com\\\", role: NUTRITIONIST}) { id } }\"}")"
nutritionist_id="$(printf '%s' "$nutritionist_response" | extract_id)"

echo "Creating their active subscription and nutrition target..."
graphql \
    "{\"query\":\"mutation { createSubscription(input: {clientId: \\\"${client_id}\\\", nutritionistId: \\\"${nutritionist_id}\\\", planTier: PREMIUM, status: ACTIVE, startDate: \\\"2026-01-01\\\"}) { id } }\"}" \
    >/dev/null
graphql \
    "{\"query\":\"mutation { setNutritionTarget(input: {clientId: \\\"${client_id}\\\", dailyCalories: 500, proteinGrams: 25, carbohydrateGrams: 40, fatGrams: 20}) { clientId } }\"}" \
    >/dev/null

echo "Creating the recipe used on all seven days..."
recipe_response="$(graphql \
    "{\"query\":\"mutation { createRecipe(input: {name: \\\"Kubernetes Tofu Bowl\\\", description: \\\"End-to-end demo recipe\\\", dietType: VEGAN, prepSteps: [\\\"Cook and serve\\\"], createdBy: \\\"${nutritionist_id}\\\", ingredients: [{name: \\\"Spinach\\\", quantity: 100, unit: \\\"g\\\"}], macros: {calories: 500, proteinGrams: 25, carbohydrateGrams: 40, fatGrams: 20}, attributes: {proteinSource: \\\"tofu\\\", b12Fortified: true, veganCertified: true}}) { id } }\"}")"
recipe_id="$(printf '%s' "$recipe_response" | extract_id)"

echo "Creating and submitting a seven-day meal plan..."
plan_response="$(graphql \
    "{\"query\":\"mutation { createMealPlan(input: {clientId: \\\"${client_id}\\\", nutritionistId: \\\"${nutritionist_id}\\\", weekStartDate: \\\"2026-07-20\\\", days: [{date: \\\"2026-07-20\\\", meals: [{recipeId: \\\"${recipe_id}\\\", mealType: DINNER}]}, {date: \\\"2026-07-21\\\", meals: [{recipeId: \\\"${recipe_id}\\\", mealType: DINNER}]}, {date: \\\"2026-07-22\\\", meals: [{recipeId: \\\"${recipe_id}\\\", mealType: DINNER}]}, {date: \\\"2026-07-23\\\", meals: [{recipeId: \\\"${recipe_id}\\\", mealType: DINNER}]}, {date: \\\"2026-07-24\\\", meals: [{recipeId: \\\"${recipe_id}\\\", mealType: DINNER}]}, {date: \\\"2026-07-25\\\", meals: [{recipeId: \\\"${recipe_id}\\\", mealType: DINNER}]}, {date: \\\"2026-07-26\\\", meals: [{recipeId: \\\"${recipe_id}\\\", mealType: DINNER}]}]}) { id status } }\"}")"
plan_id="$(printf '%s' "$plan_response" | extract_id)"
graphql \
    "{\"query\":\"mutation { submitMealPlan(id: \\\"${plan_id}\\\") { id status } }\"}" \
    >/dev/null

echo "Waiting for outbox -> SQS -> Lambda processing..."
for attempt in {1..60}; do
    result="$(graphql \
        "{\"query\":\"query { mealPlan(id: \\\"${plan_id}\\\") { id status result { weeklyTotals { calories proteinGrams carbohydrateGrams fatGrams } groceryList { name quantity unit } } targetComparison { caloriePercentageDifference proteinPercentageDifference carbohydratePercentageDifference fatPercentageDifference } } }\"}")"
    status="$(printf '%s' "$result" | grep -o '"status":"[^"]*"' | head -n 1 | cut -d'"' -f4)"
    if [[ "$status" == "PROCESSED" || "$status" == "FLAGGED" ]]; then
        echo "End-to-end flow completed:"
        printf '%s\n' "$result"
        exit 0
    fi
    # FAILED is retryable by design; SQS may move it back to PROCESSING.
    sleep 2
done

echo "Timed out waiting for meal plan ${plan_id}; last response: ${result}" >&2
exit 1
