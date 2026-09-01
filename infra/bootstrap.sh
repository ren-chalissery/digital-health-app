#!/usr/bin/env bash
#
# Stands up every stack, in order, from nothing.
#
# Deliberately not part of the pipeline: this is the one-off that has to happen before GitHub
# Actions can deploy anything, and it does things the pipeline should never do — creating the
# deploy role, issuing certificates, and pushing the first image so the service has something to
# start.
#
# Safe to run repeatedly. Every step reuses what already exists.
#
# What it will not do is register a domain. That is a purchase with contact details attached, and
# it belongs to a human. See the README.

set -euo pipefail

readonly EXPECTED_ACCOUNT="${EXPECTED_ACCOUNT:-917993967729}"
readonly REGION="${AWS_REGION:-ap-southeast-2}"
readonly DOMAIN="${DOMAIN:-simplicityhelp.com}"
readonly WEB_HOST="${WEB_HOST:-app.${DOMAIN}}"
readonly API_HOST="${API_HOST:-api.${DOMAIN}}"
readonly MAIL_FROM="${MAIL_FROM:-no-reply@${DOMAIN}}"
readonly GITHUB_REPO="${GITHUB_REPO:-ren-chalissery/digital-health-app}"

readonly NETWORK_STACK=digital-health-network
readonly DATA_STACK=digital-health-data
readonly AUTH_STACK=digital-health-auth
readonly WEB_STACK=digital-health-web
readonly APP_STACK=digital-health-app
readonly MEDIA_STACK=digital-health-media
readonly MAIL_STACK=digital-health-mail
readonly ROLE_STACK=digital-health-deploy-role

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${HERE}/.." && pwd)"
readonly HERE ROOT

export AWS_REGION="${REGION}"
export AWS_DEFAULT_REGION="${REGION}"

step() { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
info() { printf '    %s\n' "$*"; }
die() { printf '\n\033[31mError: %s\033[0m\n' "$*" >&2; exit 1; }

output() {
  aws cloudformation describe-stacks --stack-name "$1" \
    --query "Stacks[0].Outputs[?OutputKey=='$2'].OutputValue" --output text 2>/dev/null
}

deployStack() {
  local stack="$1" template="$2"
  shift 2
  local args=(
    --stack-name "${stack}"
    --template-file "${HERE}/${template}"
    --capabilities CAPABILITY_NAMED_IAM
    --no-fail-on-empty-changeset
  )
  # Parameters left out here keep whatever the stack already has, which is how the app stack holds
  # on to the certificate it issued for itself.
  if (($#)); then
    args+=(--parameter-overrides "$@")
  fi
  info "deploying ${stack}"
  aws cloudformation deploy "${args[@]}" >/dev/null
}

# ---------------------------------------------------------------------------------------------
step "Preflight"

account="$(aws sts get-caller-identity --query Account --output text)" \
  || die "No AWS credentials. Authenticate first."
[[ "${account}" == "${EXPECTED_ACCOUNT}" ]] \
  || die "Refusing to run: credentials are for account ${account}, expected ${EXPECTED_ACCOUNT}."
info "account ${account}, region ${REGION}"

command -v docker >/dev/null || die "Docker is required to build and push the first image."
docker info >/dev/null 2>&1 || die "The Docker daemon is not running."

zoneId="$(aws route53 list-hosted-zones-by-name --dns-name "${DOMAIN}." \
  --query "HostedZones[?Name=='${DOMAIN}.'].Id | [0]" --output text | sed 's|/hostedzone/||')"
[[ -n "${zoneId}" && "${zoneId}" != "None" ]] \
  || die "No Route 53 hosted zone for ${DOMAIN}. Register or delegate the domain first; see infra/README.md."
info "hosted zone ${zoneId}"

# ---------------------------------------------------------------------------------------------
step "CloudFront certificate in us-east-1"

# CloudFront only reads certificates from us-east-1, whatever region everything else lives in, and
# a stack cannot create one outside its own region. Hence doing it here rather than in web.yaml.
cfCert="$(aws acm list-certificates --region us-east-1 \
  --query "CertificateSummaryList[?DomainName=='${WEB_HOST}'].CertificateArn | [0]" --output text)"

if [[ -z "${cfCert}" || "${cfCert}" == "None" ]]; then
  cfCert="$(aws acm request-certificate --region us-east-1 \
    --domain-name "${WEB_HOST}" --validation-method DNS \
    --query CertificateArn --output text)"
  info "requested ${cfCert}"
fi

# Checked rather than assumed: an interrupted run leaves a certificate that exists but was never
# validated, and treating that as finished would fail the web stack several minutes later.
status="$(aws acm describe-certificate --region us-east-1 --certificate-arn "${cfCert}" \
  --query Certificate.Status --output text)"

if [[ "${status}" != "ISSUED" ]]; then
  # ACM publishes the validation record asynchronously, so it is not readable straight away.
  for _ in $(seq 1 30); do
    read -r rrName rrValue <<<"$(aws acm describe-certificate --region us-east-1 \
      --certificate-arn "${cfCert}" \
      --query "Certificate.DomainValidationOptions[0].ResourceRecord.[Name,Value]" --output text)"
    [[ -n "${rrName}" && "${rrName}" != "None" ]] && break
    sleep 2
  done
  [[ -n "${rrName}" && "${rrName}" != "None" ]] || die "ACM never published a validation record."

  aws route53 change-resource-record-sets --hosted-zone-id "${zoneId}" --change-batch "$(
    cat <<EOF
{"Changes":[{"Action":"UPSERT","ResourceRecordSet":{
  "Name":"${rrName}","Type":"CNAME","TTL":300,"ResourceRecords":[{"Value":"${rrValue}"}]}}]}
EOF
  )" >/dev/null
  info "validation record written, waiting for issuance (a few minutes)"
  aws acm wait certificate-validated --region us-east-1 --certificate-arn "${cfCert}"
fi
info "certificate ${cfCert}"

# ---------------------------------------------------------------------------------------------
step "Foundation stacks"

# The OIDC subject carries numeric ids, not just names, so the role has to be told them.
ownerId="$(gh api "repos/${GITHUB_REPO}" --jq .owner.id)"
repoId="$(gh api "repos/${GITHUB_REPO}" --jq .id)"
deployStack "${ROLE_STACK}" deploy-role.yaml \
  "GitHubOrg=${GITHUB_REPO%%/*}" "GitHubRepository=${GITHUB_REPO##*/}" \
  "GitHubOwnerId=${ownerId}" "GitHubRepositoryId=${repoId}"

deployStack "${NETWORK_STACK}" network.yaml

dataParams=("NetworkStackName=${NETWORK_STACK}")
if [[ -n "${DB_MASTER_PASSWORD:-}" ]]; then
  dataParams+=("DatabaseMasterPassword=${DB_MASTER_PASSWORD}")
elif ! aws cloudformation describe-stacks --stack-name "${DATA_STACK}" >/dev/null 2>&1; then
  die "Set DB_MASTER_PASSWORD before the first data-stack deploy."
fi
deployStack "${DATA_STACK}" data.yaml "${dataParams[@]}"

# Cognito verification mail must go through the verified domain, not Cognito's shared sender.
if ! aws sesv2 get-email-identity --email-identity "${DOMAIN}" >/dev/null 2>&1; then
  aws sesv2 create-email-identity --email-identity "${DOMAIN}" >/dev/null
  info "created the SES identity for ${DOMAIN}"
fi
sesSourceArn="arn:aws:ses:${REGION}:${account}:identity/${DOMAIN}"
deployStack "${AUTH_STACK}" auth.yaml \
  "WebBaseUrl=https://${WEB_HOST}" \
  "SesSourceArn=${sesSourceArn}" \
  "SesFromAddress=${MAIL_FROM}"

# web before app: app needs the web origin for CORS and for the links in invitation emails.
deployStack "${WEB_STACK}" web.yaml \
  "WebDomainName=${WEB_HOST}" "CertificateArn=${cfCert}" "HostedZoneId=${zoneId}"

# The upload bucket's CORS rule names the web origin, so this follows web too.
deployStack "${MEDIA_STACK}" media.yaml "WebOrigin=https://${WEB_HOST}"

# ---------------------------------------------------------------------------------------------
step "Mail stack"

# Owned outside the app stack so bounce monitoring survives pausing managed compute.
"${HERE}/bootstrap-mail.sh"

mailConfigurationSet="$(output "${MAIL_STACK}" MailConfigurationSetName)"
alarmTopicArn="$(output "${MAIL_STACK}" AlarmTopicArn)"

# ---------------------------------------------------------------------------------------------
step "Application stack and its first image"

appParams=(
  "NetworkStackName=${NETWORK_STACK}"
  "DataStackName=${DATA_STACK}"
  "AuthStackName=${AUTH_STACK}"
  "MediaStackName=${MEDIA_STACK}"
  "ApiDomainName=${API_HOST}"
  "HostedZoneId=${zoneId}"
  "WebBaseUrl=https://${WEB_HOST}"
  "MailFrom=${MAIL_FROM}"
  "MailConfigurationSet=${mailConfigurationSet}"
  "AlarmTopicArn=${alarmTopicArn}"
)

# The repository has to exist before anything can be pushed to it, and the service cannot reach a
# steady state with no image to run. Hence zero tasks on the first pass.
if ! aws cloudformation describe-stacks --stack-name "${APP_STACK}" >/dev/null 2>&1; then
  info "first deploy, so no tasks yet"
  deployStack "${APP_STACK}" app.yaml "${appParams[@]}" "DesiredCount=0" "ImageTag=bootstrap"
fi

repository="$(output "${APP_STACK}" RepositoryUri)"
sha="$(git -C "${ROOT}" rev-parse HEAD)"

# Tags are immutable, so a rerun at the same commit must not try to push again. Only the sha is
# ever tagged: deploys pin to a commit, and a moving 'latest' cannot exist in an immutable
# repository anyway.
if aws ecr describe-images --repository-name "${repository##*/}" \
    --image-ids "imageTag=${sha}" >/dev/null 2>&1; then
  info "image ${sha} is already in the registry"
else
  info "pushing ${repository}:${sha}"
  aws ecr get-login-password | docker login --username AWS --password-stdin "${repository%%/*}" >/dev/null
  docker build --platform linux/arm64 -q -t "${repository}:${sha}" "${ROOT}/backend" >/dev/null
  docker push -q "${repository}:${sha}" >/dev/null
fi

deployStack "${APP_STACK}" app.yaml "${appParams[@]}" "DesiredCount=1" "ImageTag=${sha}"

info "waiting for the service to stabilise"
aws ecs wait services-stable \
  --cluster "$(output "${APP_STACK}" ClusterName)" \
  --services "$(output "${APP_STACK}" ServiceName)"

# ---------------------------------------------------------------------------------------------
step "Web bundle"

# The same upload the pipeline performs. Without it the distribution serves 403 from an empty
# bucket, and the URL printed at the end of this script would be a lie.
(cd "${ROOT}/web" && npm ci --silent && npm run build --silent) >/dev/null
readonly BUNDLE="${ROOT}/web/dist/web/browser"

# Written here rather than built in, so one artefact can be promoted between environments.
jq -n \
  --arg apiBaseUrl "$(output "${APP_STACK}" ApiBaseUrl)" \
  --arg userPoolId "$(output "${AUTH_STACK}" UserPoolId)" \
  --arg userPoolClientId "$(output "${AUTH_STACK}" WebClientId)" \
  '{apiBaseUrl: $apiBaseUrl, cognito: {userPoolId: $userPoolId, userPoolClientId: $userPoolClientId}}' \
  >"${BUNDLE}/config.json"

bucket="$(output "${WEB_STACK}" BucketName)"
# Fingerprinted assets cached hard; the two files that are not fingerprinted must never be cached,
# or a deploy would leave browsers running the previous bundle against the new API.
aws s3 sync "${BUNDLE}" "s3://${bucket}" --delete --only-show-errors \
  --cache-control 'public,max-age=31536000,immutable' \
  --exclude index.html --exclude config.json
aws s3 cp "${BUNDLE}/index.html" "s3://${bucket}/index.html" --only-show-errors \
  --cache-control 'no-cache,no-store,must-revalidate'
aws s3 cp "${BUNDLE}/config.json" "s3://${bucket}/config.json" --only-show-errors \
  --cache-control 'no-cache,no-store,must-revalidate'
info "uploaded to ${bucket}"

distribution="$(output "${WEB_STACK}" DistributionId)"
invalidation="$(aws cloudfront create-invalidation --distribution-id "${distribution}" \
  --paths '/index.html' '/config.json' --query 'Invalidation.Id' --output text)"
aws cloudfront wait invalidation-completed \
  --distribution-id "${distribution}" --id "${invalidation}"
info "cache invalidated"

# ---------------------------------------------------------------------------------------------
step "Email"

if ! aws sesv2 get-email-identity --email-identity "${DOMAIN}" >/dev/null 2>&1; then
  aws sesv2 create-email-identity --email-identity "${DOMAIN}" >/dev/null
  info "created the SES identity for ${DOMAIN}"
fi

# Signing with DKIM is what keeps invitations out of spam folders. The tokens are stable, so
# rewriting the records on a rerun is a no-op.
tokens="$(aws sesv2 get-email-identity --email-identity "${DOMAIN}" \
  --query 'DkimAttributes.Tokens' --output text)"
for token in ${tokens}; do
  aws route53 change-resource-record-sets --hosted-zone-id "${zoneId}" --change-batch "$(
    cat <<EOF
{"Changes":[{"Action":"UPSERT","ResourceRecordSet":{
  "Name":"${token}._domainkey.${DOMAIN}","Type":"CNAME","TTL":1800,
  "ResourceRecords":[{"Value":"${token}.dkim.amazonses.com"}]}}]}
EOF
  )" >/dev/null
done
info "DKIM records written"

# SPF and DMARC align the From domain with SES for Cognito verification mail as well as invitations.
aws route53 change-resource-record-sets --hosted-zone-id "${zoneId}" --change-batch "$(
  cat <<EOF
{"Changes":[
  {"Action":"UPSERT","ResourceRecordSet":{
    "Name":"${DOMAIN}","Type":"TXT","TTL":300,
    "ResourceRecords":[{"Value":"\"v=spf1 include:amazonses.com ~all\""}]}},
  {"Action":"UPSERT","ResourceRecordSet":{
    "Name":"_dmarc.${DOMAIN}","Type":"TXT","TTL":300,
    "ResourceRecords":[{"Value":"\"v=DMARC1; p=none; rua=mailto:${MAIL_FROM}\""}]}}
]}
EOF
)" >/dev/null
info "SPF and DMARC records written"

production="$(aws sesv2 get-account --query ProductionAccessEnabled --output text)"
if [[ "${production}" != "True" ]]; then
  cat <<EOF

    This account is still in the SES sandbox, so invitations will only reach addresses you have
    verified by hand, and never a real clinician. Request production access now rather than on the
    day you need it — approval usually takes about a day:

      https://${REGION}.console.aws.amazon.com/ses/home?region=${REGION}#/account

EOF
fi

# ---------------------------------------------------------------------------------------------
step "GitHub"

if command -v gh >/dev/null && gh auth status >/dev/null 2>&1; then
  setVariable() { gh variable set "$1" --repo "${GITHUB_REPO}" --body "$2" >/dev/null && info "$1"; }
  setVariable AWS_REGION "${REGION}"
  setVariable AWS_DEPLOY_ROLE_ARN "$(output "${ROLE_STACK}" DeployRoleArn)"
  setVariable API_DOMAIN_NAME "${API_HOST}"
  setVariable WEB_DOMAIN_NAME "${WEB_HOST}"
  setVariable WEB_CERTIFICATE_ARN "${cfCert}"
  setVariable WEB_BASE_URL "https://${WEB_HOST}"
  setVariable HOSTED_ZONE_ID "${zoneId}"
  setVariable MAIL_FROM "${MAIL_FROM}"
else
  info "gh is not authenticated; set the variables listed in infra/README.md by hand"
fi

# ---------------------------------------------------------------------------------------------
step "Done"

cat <<EOF

    Web    https://${WEB_HOST}
    API    https://${API_HOST}

    Pushes to main will now deploy on their own. Check the API is answering:

      curl https://${API_HOST}/actuator/health/readiness

EOF
