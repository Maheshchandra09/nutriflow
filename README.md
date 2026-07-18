# NutriFlow

NutriFlow is a backend-focused nutrition and meal-planning platform built with Java 21, Spring Boot, GraphQL, PostgreSQL, MongoDB, SQS, and AWS Lambda.

## Repository layout

```text
.
├── api/          Spring Boot GraphQL API
├── contracts/    Shared versioned event contracts
├── worker/       AWS Lambda meal-plan processor
├── k8s/base/     Kubernetes manifests
├── compose.yml   PostgreSQL, MongoDB, and LocalStack
└── pom.xml       Maven reactor build
```

## Getting started

Prerequisites: Java 21+, Docker with Compose, and Maven 3.9+.

```bash
docker compose up -d
./mvnw verify
./mvnw -pl api -am spring-boot:run
```

GraphiQL is available at <http://localhost:8080/graphiql>. Health information is available at <http://localhost:8080/actuator/health>.

Stop local dependencies with:

```bash
docker compose down
```

See [TECHNICAL_PLAN.md](TECHNICAL_PLAN.md) for implementation scope and delivery order.
