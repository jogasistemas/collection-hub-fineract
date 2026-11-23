#!/usr/bin/env bash
set -euo pipefail

NETWORK="${DOCKER_NETWORK:-prestamype-entrega_default}"
HUB_URL="http://hub:8081/api/v1"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
LOAN_ID_FILE="$PROJECT_DIR/.demo/loan-id"

if [[ -n "${DEMO_LOAN_ID:-}" ]]; then
  LOAN_ID="$DEMO_LOAN_ID"
elif [[ -f "$LOAN_ID_FILE" ]]; then
  LOAN_ID="$(cat "$LOAN_ID_FILE")"
else
  echo "ERROR: No existe .demo/loan-id."
  echo "Ejecuta primero ./scripts/setup-demo.sh"
  exit 1
fi

if [[ ! "$LOAN_ID" =~ ^[1-9][0-9]*$ ]]; then
  echo "ERROR: Identificador de prestamo invalido: $LOAN_ID"
  exit 1
fi
CUTOFF_DATE="${DEMO_CUTOFF_DATE:-2026-10-15}"
HORIZON_DAYS="${DEMO_HORIZON_DAYS:-31}"
IDEMPOTENCY_KEY="${DEMO_IDEMPOTENCY_KEY:-prestamype-loan-${LOAN_ID}-${CUTOFF_DATE}-${HORIZON_DAYS}}"

hub_api() {
  local method="$1"
  local endpoint="$2"
  local payload="${3:-}"

  local args=(
    --rm
    --network "$NETWORK"
    curlimages/curl:latest
    -sS
    --fail-with-body
    -X "$method"
    -H "Content-Type: application/json"
    -H "Idempotency-Key: $IDEMPOTENCY_KEY"
  )

  if [[ -n "$payload" ]]; then
    args+=(-d "$payload")
  fi

  docker run "${args[@]}" "$HUB_URL$endpoint"
}

echo "======================================"
echo " PRESTAMYPE - DEMOSTRACION COLLECTION HUB"
echo "======================================"

echo
echo "Verificando Apache Fineract..."
"$(dirname "$0")/wait-for-fineract.sh"

echo
echo "Generando lote de cobranza..."

PAYLOAD=$(printf \
  '{"loanIds":[%s],"cutoffDate":"%s","horizonDays":%s}' \
  "$LOAN_ID" "$CUTOFF_DATE" "$HORIZON_DAYS")

hub_api POST "/collection-batches" "$PAYLOAD"

echo
echo
echo "Consultando ordenes del prestamo $LOAN_ID..."

hub_api GET "/collection-orders?loanId=$LOAN_ID"

echo
echo
echo "Demostracion finalizada."
