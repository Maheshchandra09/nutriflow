# LocalStack meal-plan worker

The worker must be packaged before it can be deployed as a Java Lambda:

```bash
./mvnw -pl worker -am package
```

Start the databases and LocalStack on the shared `nutriflow` Docker network:

```bash
docker compose up -d postgres mongodb localstack
```

Deploy or update the worker and create its batch-size-1 SQS event-source mapping:

```bash
docker compose exec -T localstack /opt/nutriflow/deploy-worker.sh
```

Inspect the deployed function and mapping:

```bash
docker compose exec -T localstack \
  awslocal lambda get-function-configuration \
  --function-name nutriflow-meal-plan-worker

docker compose exec -T localstack \
  awslocal lambda list-event-source-mappings \
  --function-name nutriflow-meal-plan-worker
```

`./mvnw clean verify` performs a separate isolated end-to-end test. It packages
the shaded worker JAR, deploys it to a Testcontainers-managed LocalStack Lambda,
connects a real SQS queue, sends `MealPlanSubmittedV1`, and verifies the worker
stores a processed result using temporary MongoDB and PostgreSQL containers.
