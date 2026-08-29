#!/usr/bin/env bash
#
# Provisions the local Cognito pool and SES sender identity in Floci, then writes the two config
# files that the backend and the web app read at startup.
#
# Safe to run repeatedly: an existing pool is reused rather than duplicated. Floci keeps its state
# in memory, so this needs running again after `docker compose down`.

set -euo pipefail

readonly ENDPOINT="${AWS_ENDPOINT_URL:-http://localhost:4566}"
readonly POOL_NAME="digital-health-local"
readonly MAIL_FROM="no-reply@simplicity.local"

readonly ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly BACKEND_ENV="${ROOT}/backend/.env.local"
readonly WEB_CONFIG="${ROOT}/web/public/config.json"

export AWS_ENDPOINT_URL="${ENDPOINT}"
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1

log() { printf '  %s\n' "$*"; }

waitForFloci() {
  for _ in $(seq 1 30); do
    if curl -sf "${ENDPOINT}/health" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  echo "Floci did not become ready at ${ENDPOINT}. Is 'docker compose up' running?" >&2
  exit 1
}

findPool() {
  aws cognito-idp list-user-pools --max-results 60 \
    --query "UserPools[?Name=='${POOL_NAME}'].Id | [0]" --output text 2>/dev/null
}

findClient() {
  aws cognito-idp list-user-pool-clients --user-pool-id "$1" --max-results 60 \
    --query "UserPoolClients[?ClientName=='web'].ClientId | [0]" --output text 2>/dev/null
}

echo "Waiting for Floci..."
waitForFloci

poolId="$(findPool)"
if [[ "${poolId}" == "None" || -z "${poolId}" ]]; then
  # Mirrors infra/auth.yaml: email is the identifier and is auto-verified. The password policy
  # matches too, so a password rejected here would be rejected in production.
  poolId="$(aws cognito-idp create-user-pool \
    --pool-name "${POOL_NAME}" \
    --username-attributes email \
    --auto-verified-attributes email \
    --policies 'PasswordPolicy={MinimumLength=12,RequireUppercase=true,RequireLowercase=true,RequireNumbers=true,RequireSymbols=false}' \
    --query 'UserPool.Id' --output text)"
  log "created user pool ${poolId}"
else
  log "reusing user pool ${poolId}"
fi

clientId="$(findClient "${poolId}")"
if [[ "${clientId}" == "None" || -z "${clientId}" ]]; then
  clientId="$(aws cognito-idp create-user-pool-client \
    --user-pool-id "${poolId}" \
    --client-name web \
    --no-generate-secret \
    --explicit-auth-flows ALLOW_USER_SRP_AUTH ALLOW_USER_PASSWORD_AUTH ALLOW_REFRESH_TOKEN_AUTH \
    --query 'UserPoolClient.ClientId' --output text)"
  log "created app client ${clientId}"
else
  log "reusing app client ${clientId}"
fi

aws sesv2 create-email-identity --email-identity "${MAIL_FROM}" >/dev/null 2>&1 || true
log "verified sender ${MAIL_FROM}"

cat > "${BACKEND_ENV}" <<EOF
# Written by local/bootstrap.sh. Not for any deployed environment.
AWS_ENDPOINT_URL=${ENDPOINT}
AWS_REGION=us-east-1
AWS_ACCESS_KEY_ID=test
AWS_SECRET_ACCESS_KEY=test
COGNITO_ISSUER_URI=${ENDPOINT}/${poolId}
COGNITO_USER_POOL_ID=${poolId}
COGNITO_CLIENT_ID=${clientId}
MAIL_ENABLED=true
MAIL_FROM=${MAIL_FROM}
EOF
log "wrote ${BACKEND_ENV#"${ROOT}/"}"

cat > "${WEB_CONFIG}" <<EOF
{
  "apiBaseUrl": "http://localhost:8080",
  "cognito": {
    "userPoolId": "${poolId}",
    "userPoolClientId": "${clientId}",
    "endpoint": "${ENDPOINT}"
  }
}
EOF
log "wrote ${WEB_CONFIG#"${ROOT}/"}"

cat <<EOF

Local AWS is ready.

  Backend   set -a && source backend/.env.local && set +a && ./gradlew bootRun
  Web       npm start in web/, then sign up with any address

Confirmation codes and invitation emails go to Floci rather than a real inbox. Read them with:

  AWS_ENDPOINT_URL=${ENDPOINT} AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test \\
    aws sesv2 list-email-identities
EOF
