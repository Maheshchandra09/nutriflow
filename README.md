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

Prerequisites: Java 21+, Docker with Compose, Maven 3.9+, `kubectl`, and
[`kind`](https://kind.sigs.k8s.io/).

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

## Local Kubernetes execution

The v1 deployment keeps the API in a two-replica local kind cluster. PostgreSQL,
MongoDB, and LocalStack run through Docker Compose, and the async worker remains
a Java 21 Lambda managed by LocalStack.

Build everything, deploy the Lambda, create/reuse the kind cluster, load the
local API image, apply the manifests, and wait for both replicas:

```bash
./k8s/run-local.sh
```

Expose the ClusterIP service in a second terminal:

```bash
kubectl -n nutriflow port-forward service/nutriflow-api 8080:80
```

Run a complete seeded flow through GraphQL, the MongoDB outbox, SQS, Lambda,
PostgreSQL nutrition targets, and the MongoDB result:

```bash
./k8s/e2e-demo.sh
```

Inspect or remove the local deployment with:

```bash
kubectl -n nutriflow get pods
kind delete cluster --name nutriflow
docker compose down
```

On `main`, CI can also push the commit-SHA API image to an existing ECR
repository. Configure repository variables `AWS_REGION` and `ECR_REPOSITORY`
plus the `AWS_ROLE_ARN` secret for a GitHub OIDC-enabled IAM role. When these
values are absent, CI still verifies the code, image, and Kubernetes manifests
without attempting an AWS push.
