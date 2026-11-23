#!/usr/bin/env bash
set -euo pipefail

NETWORK="${DOCKER_NETWORK:-prestamype-entrega_default}"
MAX_ATTEMPTS=60
WAIT_SECONDS=5

echo "Esperando a que Apache Fineract esté disponible..."

for ((attempt=1; attempt<=MAX_ATTEMPTS; attempt++)); do

  HTTP_CODE=$(docker run --rm \
    --network "$NETWORK" \
    curlimages/curl:latest \
    -ksS -o /dev/null -w "%{http_code}" \
    --connect-timeout 5 \
    --max-time 10 \
    -u "${FINERACT_USERNAME:-mifos}:${FINERACT_PASSWORD:-password}" \
    -H "Fineract-Platform-TenantId: default" \
    "https://fineract:8443/fineract-provider/api/v1/clients" \
    2>/dev/null) || HTTP_CODE="000"

  if [[ "$HTTP_CODE" == "200" ]]; then
    echo "Apache Fineract está listo (HTTP 200)."
    exit 0
  fi

  echo "Intento $attempt/$MAX_ATTEMPTS - HTTP $HTTP_CODE"
  sleep "$WAIT_SECONDS"

done

echo "ERROR: Apache Fineract no respondió correctamente."
exit 1
