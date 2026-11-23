#!/usr/bin/env bash
set -euo pipefail

NETWORK="${DOCKER_NETWORK:-prestamype-entrega_default}"
FINERACT_URL="https://fineract:8443/fineract-provider/api/v1"
FINERACT_USERNAME="${FINERACT_USERNAME:-mifos}"
FINERACT_PASSWORD="${FINERACT_PASSWORD:-password}"

fineract_api() {
  local method="$1"
  local endpoint="$2"
  local payload="${3:-}"

  local args=(
    --rm
    --network "$NETWORK"
    curlimages/curl:latest
    -ksS
    --fail-with-body
    -u "$FINERACT_USERNAME:$FINERACT_PASSWORD"
    -X "$method"
    -H "Fineract-Platform-TenantId: default"
    -H "Content-Type: application/json"
  )

  if [[ -n "$payload" ]]; then
    args+=(-d "$payload")
  fi

  docker run "${args[@]}" "$FINERACT_URL$endpoint"
}

echo "====================================="
echo " PRESTAMYPE - PREPARACION DEMO"
echo "====================================="

echo "Verificando disponibilidad de Fineract..."
"$(dirname "$0")/wait-for-fineract.sh"

echo "Consultando clientes existentes..."
CLIENTS=$(fineract_api GET "/clients?limit=100")

CLIENT_ID=$(echo "$CLIENTS" | jq -r '
  .pageItems[]
  | select(.firstname == "Ana" and .lastname == "Demo Integracion")
  | .id
' | head -n 1)

if [[ -n "$CLIENT_ID" ]]; then
  echo "El cliente Ana ya existe. ID: $CLIENT_ID"
else
  echo "Creando cliente Ana Demo Integracion..."

CLIENT_RESPONSE=$(fineract_api POST "/clients" '{
    "officeId": 1,
    "firstname": "Ana",
    "lastname": "Demo Integracion",
    "legalFormId": 1,
    "active": true,
    "activationDate": "15 September 2026",
    "dateFormat": "dd MMMM yyyy",
    "locale": "en"
    }')

CLIENT_ID=$(echo "$CLIENT_RESPONSE" | jq -r '.clientId')

echo "Cliente creado correctamente. ID: $CLIENT_ID"

  echo
  echo "Cliente creado correctamente."
fi

echo
echo "Preparacion inicial completada."

echo
echo "Consultando productos financieros..."

PRODUCTS=$(fineract_api GET "/loanproducts")
PRODUCT_ID=$(echo "$PRODUCTS" | jq -r '
  .[]
  | select(.name == "Prestamype Demo PEN")
  | .id
' | head -n 1)

if echo "$PRODUCTS" | grep -q '"name":"Prestamype Demo PEN"'; then
  echo "El producto Prestamype Demo PEN ya existe."
else
  echo "Creando producto financiero..."

PRODUCT_RESPONSE=$(fineract_api POST "/loanproducts" '{
    "name": "Prestamype Demo PEN",
    "shortName": "PDPN",
    "description": "Producto de demostracion para Collection Hub",
    "currencyCode": "PEN",
    "digitsAfterDecimal": 2,
    "inMultiplesOf": 1,
    "principal": 12000,
    "minPrincipal": 12000,
    "maxPrincipal": 12000,
    "numberOfRepayments": 12,
    "minNumberOfRepayments": 12,
    "maxNumberOfRepayments": 12,
    "repaymentEvery": 1,
    "repaymentFrequencyType": 2,
    "interestRatePerPeriod": 1,
    "interestRateFrequencyType": 2,
    "amortizationType": 0,
    "interestType": 0,
    "loanScheduleType": "CUMULATIVE",
    "interestCalculationPeriodType": 1,
    "isInterestRecalculationEnabled": false,
    "transactionProcessingStrategyCode": "mifos-standard-strategy",
    "daysInYearType": 360,
    "daysInMonthType": 30,
    "accountingRule": 1,
    "locale": "en"
    }')

PRODUCT_ID=$(echo "$PRODUCT_RESPONSE" | jq -er '.resourceId')

echo "Producto financiero creado. ID: $PRODUCT_ID"

  echo
  echo "Producto financiero creado."
fi
echo
echo
echo "Consultando prestamos existentes..."

LOANS=$(fineract_api GET "/loans?clientId=$CLIENT_ID&limit=100")

LOAN_ID=$(echo "$LOANS" | jq -r \
  --argjson clientId "$CLIENT_ID" \
  --argjson productId "$PRODUCT_ID" \
  '
  .pageItems[]
  | select(.clientId == $clientId and .loanProductId == $productId)  | .id
  ' | head -n 1)

if [[ -n "$LOAN_ID" ]]; then
  echo "Prestamo existente encontrado. ID: $LOAN_ID"
else
  echo "No se encontro un prestamo de demostracion."
  echo "Creando solicitud de prestamo..."

  LOAN_RESPONSE=$(fineract_api POST "/loans" "$(jq -n \
    --argjson clientId "$CLIENT_ID" \
    --argjson productId "$PRODUCT_ID" \
    '{
      clientId: $clientId,
      productId: $productId,
      principal: 12000,
      loanTermFrequency: 12,
      loanTermFrequencyType: 2,
      numberOfRepayments: 12,
      repaymentEvery: 1,
      repaymentFrequencyType: 2,
      interestRatePerPeriod: 1,
      interestRateFrequencyType: 2,
      amortizationType: 0,
      interestType: 0,
      interestCalculationPeriodType: 1,
      transactionProcessingStrategyCode: "mifos-standard-strategy",
      loanType: "individual",
      expectedDisbursementDate: "15 September 2026",
      submittedOnDate: "15 September 2026",
      dateFormat: "dd MMMM yyyy",
      locale: "en"
    }')")

  LOAN_ID=$(echo "$LOAN_RESPONSE" | jq -er '.loanId')

  echo "Solicitud de prestamo creada. ID: $LOAN_ID"
fi
echo
echo "Verificando estado del prestamo..."

LOAN_DETAILS=$(fineract_api GET "/loans/$LOAN_ID")

if echo "$LOAN_DETAILS" | grep -q '"pendingApproval":true'; then

  echo "Aprobando prestamo..."

  fineract_api POST "/loans/$LOAN_ID?command=approve" '{
    "approvedOnDate": "15 September 2026",
    "dateFormat": "dd MMMM yyyy",
    "locale": "en"
  }'

  echo
  echo "Prestamo aprobado correctamente."

else
  echo "El prestamo no esta pendiente de aprobacion."
fi
echo
echo "Verificando si el prestamo requiere desembolso..."

LOAN_DETAILS=$(fineract_api GET "/loans/$LOAN_ID")

if echo "$LOAN_DETAILS" | grep -q '"waitingForDisbursal":true'; then

  echo "Desembolsando prestamo de S/ 12000..."

  fineract_api POST "/loans/$LOAN_ID?command=disburse" '{
    "actualDisbursementDate": "15 September 2026",
    "transactionAmount": 12000,
    "dateFormat": "dd MMMM yyyy",
    "locale": "en"
  }'

  echo
  echo "Prestamo desembolsado correctamente."

else
  echo "El prestamo no requiere desembolso."
fi

echo
echo "Guardando identificador del prestamo de demostracion..."

mkdir -p .demo

printf '%s\n' "$LOAN_ID" > .demo/loan-id

echo "Prestamo de demostracion: $LOAN_ID"
echo "Identificador guardado en .demo/loan-id"