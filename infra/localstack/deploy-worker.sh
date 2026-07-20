#!/bin/sh
set -eu

function_name="nutriflow-meal-plan-worker"
handler="com.nutriflow.worker.MealPlanEventHandler::handleRequest"
role_arn="arn:aws:iam::000000000000:role/nutriflow-lambda-role"
artifact="/opt/nutriflow/worker/worker-0.1.0-SNAPSHOT.jar"
queue_arn="arn:aws:sqs:us-east-1:000000000000:meal-plan-events"
environment="Variables={MONGODB_URI=mongodb://mongodb:27017/nutriflow,MONGODB_DATABASE=nutriflow,POSTGRES_URL=jdbc:postgresql://postgres:5432/nutriflow,POSTGRES_USER=nutriflow,POSTGRES_PASSWORD=nutriflow}"

if [ ! -f "$artifact" ]; then
    echo "Worker artifact is missing: $artifact" >&2
    echo "Run ./mvnw -pl worker -am package first." >&2
    exit 1
fi

if awslocal lambda get-function --function-name "$function_name" >/dev/null 2>&1; then
    awslocal lambda update-function-code \
        --function-name "$function_name" \
        --zip-file "fileb://$artifact" >/dev/null
    awslocal lambda wait function-updated-v2 \
        --function-name "$function_name"
    awslocal lambda update-function-configuration \
        --function-name "$function_name" \
        --handler "$handler" \
        --runtime java21 \
        --timeout 60 \
        --memory-size 512 \
        --environment "$environment" >/dev/null
else
    awslocal lambda create-function \
        --function-name "$function_name" \
        --runtime java21 \
        --handler "$handler" \
        --role "$role_arn" \
        --timeout 60 \
        --memory-size 512 \
        --zip-file "fileb://$artifact" \
        --environment "$environment" >/dev/null
fi

awslocal lambda wait function-active-v2 \
    --function-name "$function_name"

mapping_count="$(
    awslocal lambda list-event-source-mappings \
        --function-name "$function_name" \
        --event-source-arn "$queue_arn" \
        --query 'length(EventSourceMappings)' \
        --output text
)"
if [ "$mapping_count" = "0" ]; then
    awslocal lambda create-event-source-mapping \
        --function-name "$function_name" \
        --event-source-arn "$queue_arn" \
        --batch-size 1 \
        --enabled >/dev/null
fi

echo "Deployed $function_name and connected $queue_arn with batch size 1."
