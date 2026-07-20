#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cluster_name="${KIND_CLUSTER_NAME:-nutriflow}"
image="${NUTRIFLOW_IMAGE:-nutriflow-api:local}"

require_command() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "Required command is missing: $1" >&2
        exit 1
    fi
}

for command in docker kubectl kind; do
    require_command "$command"
done

cd "$root_dir"

echo "Packaging the API and Lambda worker..."
./mvnw --batch-mode -pl api,worker -am package -DskipTests

echo "Starting PostgreSQL, MongoDB, and LocalStack..."
docker compose up -d --wait postgres mongodb
# Maven clean can replace worker/target's directory inode. Recreate LocalStack
# so Docker Desktop/WSL always binds the current shaded worker artifact.
docker compose up -d --wait --force-recreate localstack

echo "Deploying the worker Lambda and its SQS event-source mapping..."
docker compose exec -T localstack /opt/nutriflow/deploy-worker.sh

echo "Building $image..."
docker build -t "$image" api

if ! kind get clusters | grep -Fxq "$cluster_name"; then
    echo "Creating kind cluster $cluster_name..."
    kind create cluster \
        --name "$cluster_name" \
        --config k8s/kind-config.yml
fi

echo "Loading $image into kind..."
kind load docker-image "$image" --name "$cluster_name"

kubectl config use-context "kind-$cluster_name" >/dev/null
kubectl apply -f k8s/base/namespace.yml
kubectl -n nutriflow create secret generic nutriflow-api-secrets \
    --from-literal=POSTGRES_USER=nutriflow \
    --from-literal=POSTGRES_PASSWORD=nutriflow \
    --from-literal=AWS_ACCESS_KEY_ID=test \
    --from-literal=AWS_SECRET_ACCESS_KEY=test \
    --dry-run=client \
    -o yaml | kubectl apply -f -

kubectl apply -k k8s/local

# Native Linux Docker does not always provide this hostname inside containers.
# Only patch it when needed; Docker Desktop/WSL already resolves it.
if ! docker exec "${cluster_name}-control-plane" \
    getent hosts host.docker.internal >/dev/null 2>&1; then
    host_gateway="$(
        docker inspect \
            --format '{{range .NetworkSettings.Networks}}{{println .Gateway}}{{end}}' \
            "${cluster_name}-control-plane" |
            sed -n '/./{p;q;}'
    )"
    if [[ -n "$host_gateway" ]]; then
        kubectl -n nutriflow patch deployment nutriflow-api \
            --type=merge \
            -p "{\"spec\":{\"template\":{\"spec\":{\"hostAliases\":[{\"ip\":\"${host_gateway}\",\"hostnames\":[\"host.docker.internal\"]}]}}}}" \
            >/dev/null
    fi
fi

# The local tag is intentionally stable, so explicitly replace pods after
# loading a newly built image into kind.
kubectl -n nutriflow rollout restart deployment/nutriflow-api
kubectl -n nutriflow rollout status deployment/nutriflow-api --timeout=180s
kubectl -n nutriflow get deployment,pods,service

cat <<'EOF'

NutriFlow is running. In another terminal, expose the API:

  kubectl -n nutriflow port-forward service/nutriflow-api 8080:80

Then run the complete GraphQL -> outbox -> SQS -> Lambda demo:

  ./k8s/e2e-demo.sh
EOF
