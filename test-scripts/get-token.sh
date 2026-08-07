#!/usr/bin/env bash
# ============================================================================
# get-token.sh — Fetch a JWT from the local mock-oauth2-server
#
# Usage:
#   ./test-scripts/get-token.sh                        # defaults
#   ./test-scripts/get-token.sh tenant-abc user-42     # custom claims
#
# Prerequisites:
#   - mock-oauth2-server running (docker compose -f infra/docker-compose.yml up -d mock-oauth2-server)
#   - curl and jq installed
# ============================================================================

set -euo pipefail

MOCK_SERVER_URL="${MOCK_OAUTH2_URL:-http://localhost:8099}"
ISSUER_ID="${MOCK_OAUTH2_ISSUER:-orderflow}"
TENANT_ID="${1:-dev-tenant}"
USER_ID="${2:-dev-user}"

TOKEN_ENDPOINT="${MOCK_SERVER_URL}/${ISSUER_ID}/token"

echo "Requesting token from: ${TOKEN_ENDPOINT}"
echo "  tenant_id = ${TENANT_ID}"
echo "  sub       = ${USER_ID}"
echo ""

RESPONSE=$(curl -s -X POST "${TOKEN_ENDPOINT}" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=test-client" \
  -d "client_secret=test-secret" \
  -d "scope=openid")

ACCESS_TOKEN=$(echo "${RESPONSE}" | jq -r '.access_token // empty')

if [ -z "${ACCESS_TOKEN}" ]; then
  echo "ERROR: Failed to obtain token. Response:"
  echo "${RESPONSE}" | jq . 2>/dev/null || echo "${RESPONSE}"
  exit 1
fi

echo "Access Token (use in Authorization header as 'Bearer <token>'):"
echo ""
echo "${ACCESS_TOKEN}"
echo ""
echo "--- Decoded payload ---"
echo "${ACCESS_TOKEN}" | cut -d. -f2 | base64 -d 2>/dev/null | jq . 2>/dev/null || echo "(could not decode)"
