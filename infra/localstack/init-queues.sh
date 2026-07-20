#!/bin/sh
set -eu

dlq_url="$(
    awslocal sqs create-queue \
        --queue-name meal-plan-events-dlq \
        --query QueueUrl \
        --output text
)"
dlq_arn="$(
    awslocal sqs get-queue-attributes \
        --queue-url "$dlq_url" \
        --attribute-names QueueArn \
        --query Attributes.QueueArn \
        --output text
)"
queue_attributes="$(
    printf \
        '{"RedrivePolicy":"{\\"deadLetterTargetArn\\":\\"%s\\",\\"maxReceiveCount\\":\\"3\\"}"}' \
        "$dlq_arn"
)"

awslocal sqs create-queue \
    --queue-name meal-plan-events \
    --attributes "$queue_attributes"
