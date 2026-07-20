#!/usr/bin/env bash
set -euo pipefail

api_url="${NUTRIFLOW_API_URL:-http://localhost:8080/graphql}"
run_id="${NUTRIFLOW_SEED_RUN_ID:-$(date -u +%Y%m%d%H%M%S)}"

graphql() {
    local query="$1"
    local escaped response
    escaped="${query//\\/\\\\}"
    escaped="${escaped//\"/\\\"}"
    escaped="${escaped//$'\n'/\\n}"
    response="$(
        curl --fail --silent --show-error \
            -H 'Content-Type: application/json' \
            --data "{\"query\":\"${escaped}\"}" \
            "$api_url"
    )"
    if grep -q '"errors"' <<<"$response"; then
        echo "GraphQL request failed: $response" >&2
        exit 1
    fi
    printf '%s\n' "$response"
}

extract_id() {
    grep -o '"id":"[^"]*"' | head -n 1 | cut -d'"' -f4
}

create_user() {
    local name="$1" email="$2" role="$3"
    graphql "mutation {
      createUser(input: {name: \"${name}\", email: \"${email}\", role: ${role}}) { id }
    }" | extract_id
}

create_subscription() {
    local client_id="$1" nutritionist_id="$2" tier="$3"
    graphql "mutation {
      createSubscription(input: {
        clientId: \"${client_id}\"
        nutritionistId: \"${nutritionist_id}\"
        planTier: ${tier}
        status: ACTIVE
        startDate: \"2026-01-01\"
      }) { id }
    }" >/dev/null
}

set_target() {
    local client_id="$1" calories="$2" protein="$3" carbs="$4" fat="$5"
    graphql "mutation {
      setNutritionTarget(input: {
        clientId: \"${client_id}\"
        dailyCalories: ${calories}
        proteinGrams: ${protein}
        carbohydrateGrams: ${carbs}
        fatGrams: ${fat}
      }) { clientId }
    }" >/dev/null
}

create_recipe() {
    local creator_id="$1" name="$2" description="$3" diet="$4"
    local calories="$5" protein="$6" carbs="$7" fat="$8"
    local ingredients="$9" attributes="${10}"
    graphql "mutation {
      createRecipe(input: {
        name: \"${name}\"
        description: \"${description} [seed ${run_id}]\"
        dietType: ${diet}
        prepSteps: [\"Prepare ingredients\", \"Cook until ready\", \"Portion and serve\"]
        createdBy: \"${creator_id}\"
        ingredients: [${ingredients}]
        macros: {
          calories: ${calories}
          proteinGrams: ${protein}
          carbohydrateGrams: ${carbs}
          fatGrams: ${fat}
        }
        attributes: ${attributes}
      }) { id }
    }" | extract_id
}

add_review() {
    local recipe_id="$1" user_id="$2" rating="$3" comment="$4"
    graphql "mutation {
      addReview(recipeId: \"${recipe_id}\", input: {
        userId: \"${user_id}\"
        rating: ${rating}
        comment: \"${comment}\"
      }) { id averageRating }
    }" >/dev/null
}

build_schedule() {
    local week_start="$1"
    shift
    local offset date_value day_separator="" meal_separator="" meal_spec
    local recipe_id meal_type
    for offset in {0..6}; do
        date_value="$(date -I -d "${week_start} +${offset} day")"
        printf '%s{date: "%s", meals: [' "$day_separator" "$date_value"
        meal_separator=""
        for meal_spec in "$@"; do
            recipe_id="${meal_spec%%:*}"
            meal_type="${meal_spec##*:}"
            printf '%s{recipeId: "%s", mealType: %s}' \
                "$meal_separator" "$recipe_id" "$meal_type"
            meal_separator=", "
        done
        printf ']}'
        day_separator=", "
    done
}

create_plan() {
    local client_id="$1" nutritionist_id="$2" week_start="$3"
    shift 3
    local schedule
    schedule="$(build_schedule "$week_start" "$@")"
    graphql "mutation {
      createMealPlan(input: {
        clientId: \"${client_id}\"
        nutritionistId: \"${nutritionist_id}\"
        weekStartDate: \"${week_start}\"
        days: [${schedule}]
      }) { id status }
    }" | extract_id
}

submit_plan() {
    graphql "mutation {
      submitMealPlan(id: \"$1\") { id status }
    }" >/dev/null
}

wait_for_status() {
    local plan_id="$1" expected_status="$2" expect_target="$3"
    local attempt response status
    for attempt in {1..75}; do
        response="$(graphql "query {
          mealPlan(id: \"${plan_id}\") {
            id
            status
            result {
              weeklyTotals {
                calories
                proteinGrams
                carbohydrateGrams
                fatGrams
              }
              groceryList { name quantity unit }
            }
            targetComparison {
              caloriePercentageDifference
              proteinPercentageDifference
              carbohydratePercentageDifference
              fatPercentageDifference
            }
          }
        }")"
        status="$(
            printf '%s' "$response" |
                grep -o '"status":"[^"]*"' |
                head -n 1 |
                cut -d'"' -f4
        )"
        if [[ "$status" == "PROCESSED" || "$status" == "FLAGGED" ]]; then
            if [[ "$status" != "$expected_status" ]]; then
                echo "Plan ${plan_id} finished as ${status}; expected ${expected_status}" >&2
                echo "$response" >&2
                exit 1
            fi
            if [[ "$expect_target" == "none" ]] &&
                ! grep -q '"targetComparison":null' <<<"$response"; then
                echo "Plan ${plan_id} unexpectedly has a target comparison" >&2
                exit 1
            fi
            printf '%s\n' "$response"
            return
        fi
        sleep 2
    done
    echo "Timed out waiting for plan ${plan_id}; last status was ${status}" >&2
    exit 1
}

echo "Checking NutriFlow API at ${api_url}..."
graphql 'query { status }' >/dev/null

echo "Creating 10 users for seed run ${run_id}..."
nutritionist_maya="$(
    create_user "Maya Patel RD" "maya.patel+${run_id}@nutriflow.demo" NUTRITIONIST
)"
nutritionist_daniel="$(
    create_user "Daniel Brooks RD" "daniel.brooks+${run_id}@nutriflow.demo" NUTRITIONIST
)"
nutritionist_elena="$(
    create_user "Elena Rossi RD" "elena.rossi+${run_id}@nutriflow.demo" NUTRITIONIST
)"
admin_priya="$(
    create_user "Priya Operations" "priya.ops+${run_id}@nutriflow.demo" ADMIN
)"
client_olivia="$(
    create_user "Olivia Green" "olivia.green+${run_id}@nutriflow.demo" CLIENT
)"
client_ethan="$(
    create_user "Ethan Cole" "ethan.cole+${run_id}@nutriflow.demo" CLIENT
)"
client_sophia="$(
    create_user "Sophia Chen" "sophia.chen+${run_id}@nutriflow.demo" CLIENT
)"
client_liam="$(
    create_user "Liam Miller" "liam.miller+${run_id}@nutriflow.demo" CLIENT
)"
client_ava="$(
    create_user "Ava Wilson" "ava.wilson+${run_id}@nutriflow.demo" CLIENT
)"
client_noah="$(
    create_user "Noah Reviewer" "noah.reviewer+${run_id}@nutriflow.demo" CLIENT
)"

echo "Creating active subscriptions and nutrition targets..."
create_subscription "$client_olivia" "$nutritionist_maya" PREMIUM
create_subscription "$client_ethan" "$nutritionist_daniel" PREMIUM
create_subscription "$client_sophia" "$nutritionist_elena" PREMIUM
create_subscription "$client_liam" "$nutritionist_maya" BASIC
create_subscription "$client_ava" "$nutritionist_elena" BASIC

set_target "$client_olivia" 1600 83 215 48
set_target "$client_ethan" 2200 150 50 150
set_target "$client_sophia" 1600 120 145 52
set_target "$client_liam" 1900 110 220 60
# Ava deliberately has no target to exercise the optional-target worker path.

echo "Creating 10 nutritionist-designed recipes..."
overnight_oats="$(
    create_recipe "$nutritionist_maya" "Berry Chia Overnight Oats" \
        "Fiber-rich breakfast with fortified soy milk" VEGAN 500 20 70 16 \
        '{name: "rolled oats", quantity: 70, unit: "g"}, {name: "fortified soy milk", quantity: 250, unit: "ml"}, {name: "blueberries", quantity: 100, unit: "g"}, {name: "chia seeds", quantity: 20, unit: "g"}' \
        '{proteinSource: "soy and chia", b12Fortified: true, veganCertified: true}'
)"
tofu_bowl="$(
    create_recipe "$nutritionist_maya" "Ginger Tofu Quinoa Bowl" \
        "Complete-protein lunch with colorful vegetables" VEGAN 650 35 80 22 \
        '{name: "firm tofu", quantity: 180, unit: "g"}, {name: "cooked quinoa", quantity: 180, unit: "g"}, {name: "broccoli", quantity: 120, unit: "g"}, {name: "sesame oil", quantity: 10, unit: "ml"}' \
        '{proteinSource: "tofu and quinoa", b12Fortified: false, veganCertified: true}'
)"
lentil_soup="$(
    create_recipe "$nutritionist_maya" "Red Lentil Vegetable Soup" \
        "High-fiber legume dinner with leafy greens" VEGAN 450 28 65 10 \
        '{name: "red lentils", quantity: 100, unit: "g"}, {name: "spinach", quantity: 100, unit: "g"}, {name: "carrot", quantity: 120, unit: "g"}, {name: "olive oil", quantity: 8, unit: "ml"}' \
        '{proteinSource: "red lentils", b12Fortified: false, veganCertified: true}'
)"
tempeh_bowl="$(
    create_recipe "$admin_priya" "Tempeh Brown Rice Bowl" \
        "Tofu-bowl alternative suitable for swap suggestions" VEGAN 630 34 75 21 \
        '{name: "tempeh", quantity: 170, unit: "g"}, {name: "cooked brown rice", quantity: 170, unit: "g"}, {name: "broccoli", quantity: 120, unit: "g"}, {name: "sesame oil", quantity: 9, unit: "ml"}' \
        '{proteinSource: "tempeh", b12Fortified: false, veganCertified: true}'
)"
egg_skillet="$(
    create_recipe "$nutritionist_daniel" "Keto Egg Spinach Skillet" \
        "Low-carbohydrate breakfast with eggs and greens" KETO 500 32 9 38 \
        '{name: "egg", quantity: 3, unit: "piece"}, {name: "spinach", quantity: 100, unit: "g"}, {name: "cheddar", quantity: 40, unit: "g"}, {name: "olive oil", quantity: 10, unit: "ml"}' \
        '{netCarbsG: 6, fatG: 38, proteinG: 32, ketoRatio: 1.19}'
)"
salmon_plate="$(
    create_recipe "$nutritionist_daniel" "Salmon Avocado Plate" \
        "Omega-3-rich keto lunch with non-starchy vegetables" KETO 700 45 12 52 \
        '{name: "salmon fillet", quantity: 200, unit: "g"}, {name: "avocado", quantity: 150, unit: "g"}, {name: "asparagus", quantity: 150, unit: "g"}, {name: "olive oil", quantity: 10, unit: "ml"}' \
        '{netCarbsG: 8, fatG: 52, proteinG: 45, ketoRatio: 1.16}'
)"
chicken_cauliflower="$(
    create_recipe "$nutritionist_daniel" "Chicken Cauliflower Bowl" \
        "Protein-forward keto dinner with cauliflower mash" KETO 650 50 15 40 \
        '{name: "chicken thigh", quantity: 220, unit: "g"}, {name: "cauliflower", quantity: 250, unit: "g"}, {name: "butter", quantity: 20, unit: "g"}, {name: "green beans", quantity: 120, unit: "g"}' \
        '{netCarbsG: 10, fatG: 40, proteinG: 50, ketoRatio: 0.8}'
)"
yogurt_bowl="$(
    create_recipe "$nutritionist_elena" "Greek Yogurt Berry Bowl" \
        "Protein-rich breakfast with controlled added sugar" DIABETIC_FRIENDLY 380 28 38 12 \
        '{name: "plain greek yogurt", quantity: 250, unit: "g"}, {name: "mixed berries", quantity: 120, unit: "g"}, {name: "walnuts", quantity: 20, unit: "g"}, {name: "cinnamon", quantity: 2, unit: "g"}' \
        '{glycemicIndex: 35, sugarG: 12, carbExchangeUnits: 2.5}'
)"
barley_salad="$(
    create_recipe "$nutritionist_elena" "Chicken Barley Garden Salad" \
        "Balanced lunch with lean protein and intact whole grains" DIABETIC_FRIENDLY 620 48 60 20 \
        '{name: "chicken breast", quantity: 180, unit: "g"}, {name: "cooked barley", quantity: 160, unit: "g"}, {name: "cucumber", quantity: 100, unit: "g"}, {name: "olive oil", quantity: 12, unit: "ml"}' \
        '{glycemicIndex: 42, sugarG: 7, carbExchangeUnits: 4}'
)"
cod_vegetables="$(
    create_recipe "$nutritionist_elena" "Baked Cod and Vegetables" \
        "Lean dinner with legumes and roasted vegetables" DIABETIC_FRIENDLY 560 46 42 18 \
        '{name: "cod fillet", quantity: 220, unit: "g"}, {name: "chickpeas", quantity: 100, unit: "g"}, {name: "zucchini", quantity: 150, unit: "g"}, {name: "olive oil", quantity: 12, unit: "ml"}' \
        '{glycemicIndex: 38, sugarG: 6, carbExchangeUnits: 2.8}'
)"

echo "Adding realistic reviews..."
add_review "$overnight_oats" "$client_olivia" 5 "Easy prep and satisfying through the morning"
add_review "$overnight_oats" "$client_noah" 4 "Good texture after an overnight soak"
add_review "$tofu_bowl" "$client_olivia" 5 "Filling lunch with clear portions"
add_review "$lentil_soup" "$client_ava" 4 "Comforting and easy to batch cook"
add_review "$tempeh_bowl" "$client_noah" 4 "Useful alternative to the tofu bowl"
add_review "$egg_skillet" "$client_ethan" 4 "Quick breakfast with stable energy"
add_review "$salmon_plate" "$client_ethan" 5 "Fresh and very filling"
add_review "$chicken_cauliflower" "$client_liam" 4 "The cauliflower mash worked well"
add_review "$yogurt_bowl" "$client_sophia" 5 "Balanced sweetness and protein"
add_review "$barley_salad" "$client_sophia" 5 "Great lunch for meal prep"

echo "Creating five meal-plan scenarios..."
vegan_plan="$(
    create_plan "$client_olivia" "$nutritionist_maya" "2026-08-03" \
        "${overnight_oats}:BREAKFAST" "${tofu_bowl}:LUNCH" "${lentil_soup}:DINNER"
)"
keto_flagged_plan="$(
    create_plan "$client_ethan" "$nutritionist_daniel" "2026-08-10" \
        "${egg_skillet}:BREAKFAST" "${salmon_plate}:LUNCH" \
        "${chicken_cauliflower}:DINNER"
)"
diabetic_plan="$(
    create_plan "$client_sophia" "$nutritionist_elena" "2026-08-17" \
        "${yogurt_bowl}:BREAKFAST" "${barley_salad}:LUNCH" \
        "${cod_vegetables}:DINNER"
)"
draft_plan="$(
    create_plan "$client_liam" "$nutritionist_maya" "2026-08-24" \
        "${tempeh_bowl}:LUNCH"
)"
no_target_plan="$(
    create_plan "$client_ava" "$nutritionist_elena" "2026-08-31" \
        "${overnight_oats}:BREAKFAST" "${tempeh_bowl}:DINNER"
)"

submit_plan "$vegan_plan"
submit_plan "$keto_flagged_plan"
submit_plan "$diabetic_plan"
submit_plan "$no_target_plan"

echo "Waiting for four asynchronous plans to reach their expected outcomes..."
vegan_result="$(wait_for_status "$vegan_plan" PROCESSED present)"
keto_result="$(wait_for_status "$keto_flagged_plan" FLAGGED present)"
diabetic_result="$(wait_for_status "$diabetic_plan" PROCESSED present)"
no_target_result="$(wait_for_status "$no_target_plan" PROCESSED none)"

cat <<EOF

Seed run ${run_id} completed successfully.

Users:          10
Subscriptions:   5 active
Targets:         4 (one client intentionally has none)
Recipes:        10 across VEGAN, KETO, and DIABETIC_FRIENDLY
Reviews:        10
Meal plans:      5

Scenario plan IDs:
  on-target vegan PROCESSED:       ${vegan_plan}
  off-target keto FLAGGED:         ${keto_flagged_plan}
  diabetic-friendly PROCESSED:     ${diabetic_plan}
  editable DRAFT:                  ${draft_plan}
  no-target PROCESSED:             ${no_target_plan}

Useful GraphQL demo IDs:
  client with dashboard:            ${client_olivia}
  assigned nutritionist:            ${nutritionist_maya}
  tofu recipe:                      ${tofu_bowl}
  compatible swap recipe:           ${tempeh_bowl}

Representative processed results:
  Vegan:      ${vegan_result}
  Keto:       ${keto_result}
  Diabetic:   ${diabetic_result}
  No target:  ${no_target_result}

Use this run ID to find relational users by email or MongoDB recipes by
description:
  ${run_id}
EOF
