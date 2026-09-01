#!/usr/bin/env bash
#
# Stands up the single-instance topology, the cheap alternative to bootstrap.sh.
#
# Same application, same Cognito pool, same buckets, about a quarter of the price: one Graviton
# instance running the API, Postgres, Valkey and Caddy as containers, in place of the load
# balancer, Fargate service, RDS instance and ElastiCache cluster.
#
# ## What it expects to already exist
#
# The network, auth and (for video) media stacks, all of which are free or near-free at idle and
# are shared with the expensive topology. Run bootstrap.sh first if this is a brand new account,
# then teardown.sh --pause to remove the parts this replaces.
#
# ## What it is not
#
# A production deployment. One instance, one local database, no failover, no point-in-time
# recovery, and a recovery point of however long ago the nightly dump last ran. Read the risk
# section of infra/README.md before pointing anything real at this.
#
# Safe to run repeatedly. Every step reuses what already exists.

set -euo pipefail

readonly EXPECTED_ACCOUNT="${EXPECTED_ACCOUNT:-917993967729}"
readonly REGION="${AWS_REGION:-ap-southeast-2}"
readonly ENVIRONMENT="${ENVIRONMENT:-prod}"
readonly DOMAIN="${DOMAIN:-simplicityhelp.com}"
readonly WEB_HOST="${WEB_HOST:-app.${DOMAIN}}"
readonly API_HOST="${API_HOST:-api.${DOMAIN}}"
readonly MAIL_FROM="${MAIL_FROM:-no-reply@${DOMAIN}}"

readonly NETWORK_STACK=digital-health-network
readonly AUTH_STACK=digital-health-auth
readonly MEDIA_STACK=digital-health-media
readonly APP_STACK=digital-health-app
readonly BOX_STACK=digital-health-box

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${HERE}/.." && pwd)"
readonly HERE ROOT

export AWS_REGION="${REGION}"
export AWS_DEFAULT_REGION="${REGION}"

step() { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
info() { printf '    %s\n' "$*"; }
warn() { printf '\033[33m    %s\033[0m\n' "$*"; }
die() { printf '\n\033[31mError: %s\033[0m\n' "$*" >&2; exit 1; }

output() {
  aws cloudformation describe-stacks --stack-name "$1" \
    --query "Stacks[0].Outputs[?OutputKey=='$2'].OutputValue" --output text 2>/dev/null
}

stackExists() { aws cloudformation describe-stacks --stack-name "$1" >/dev/null 2>&1; }

# ---------------------------------------------------------------------------------------------
step "Preflight"

account="$(aws sts get-caller-identity --query Account --output text)" \
  || die "No AWS credentials. Authenticate first."
[[ "${account}" == "${EXPECTED_ACCOUNT}" ]] \
  || die "Refusing to run: credentials are for account ${account}, expected ${EXPECTED_ACCOUNT}."
info "account ${account}, region ${REGION}, environment ${ENVIRONMENT}"

command -v docker >/dev/null || die "Docker is required to build and push the image."
docker info >/dev/null 2>&1 || die "The Docker daemon is not running."

stackExists "${NETWORK_STACK}" || die "${NETWORK_STACK} is missing. Run bootstrap.sh first."
stackExists "${AUTH_STACK}" || die "${AUTH_STACK} is missing. Run bootstrap.sh first."

mediaStack=''
if stackExists "${MEDIA_STACK}"; then
  mediaStack="${MEDIA_STACK}"
  info "media stack found, so video is available"
else
  warn "No media stack. Video will be unavailable; everything else works."
fi

# The configuration set lives in the mail stack so it survives app-stack pause.
mailConfigurationSet=''
if stackExists "digital-health-mail"; then
  mailConfigurationSet="$(output "digital-health-mail" MailConfigurationSetName)"
  [[ "${mailConfigurationSet}" == "None" ]] && mailConfigurationSet=''
elif stackExists "${APP_STACK}"; then
  mailConfigurationSet="$(output "${APP_STACK}" MailConfigurationSetName)"
  [[ "${mailConfigurationSet}" == "None" ]] && mailConfigurationSet=''
fi

zoneId="$(aws route53 list-hosted-zones-by-name --dns-name "${DOMAIN}." \
  --query "HostedZones[?Name=='${DOMAIN}.'].Id | [0]" --output text | sed 's|/hostedzone/||')"
[[ -n "${zoneId}" && "${zoneId}" != "None" ]] \
  || die "No Route 53 hosted zone for ${DOMAIN}. See infra/README.md."
info "hosted zone ${zoneId}"

if stackExists "${APP_STACK}" && [[ "$(output "${APP_STACK}" ApiBaseUrl)" == *"${API_HOST}"* ]]; then
  warn "The app stack currently owns the ${API_HOST} record."
  warn "Both topologies cannot answer on one name. Run:  ./infra/teardown.sh --pause"
  die "Refusing to fight the load balancer for DNS."
fi

# ---------------------------------------------------------------------------------------------
step "Configuration bucket"

# Deliberately created here rather than by the stack. The instance reads its compose file from this
# bucket at first boot, so a bucket the stack creates in the same deploy would still be empty when
# the instance needed it. Owning it outside CloudFormation also means a stack delete cannot orphan
# it, which is the trap the retained buckets in web.yaml and media.yaml fall into.
readonly CONFIG_BUCKET="digital-health-box-${ENVIRONMENT}-${account}"

if aws s3api head-bucket --bucket "${CONFIG_BUCKET}" >/dev/null 2>&1; then
  info "reusing ${CONFIG_BUCKET}"
else
  info "creating ${CONFIG_BUCKET}"
  aws s3api create-bucket --bucket "${CONFIG_BUCKET}" \
    --create-bucket-configuration "LocationConstraint=${REGION}" >/dev/null
  aws s3api put-public-access-block --bucket "${CONFIG_BUCKET}" \
    --public-access-block-configuration \
    'BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true'
  aws s3api put-bucket-encryption --bucket "${CONFIG_BUCKET}" \
    --server-side-encryption-configuration \
    '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'
  # Database dumps live here too, and a demonstration environment does not need them for ever.
  aws s3api put-bucket-lifecycle-configuration --bucket "${CONFIG_BUCKET}" \
    --lifecycle-configuration '{"Rules":[{"ID":"expire-backups","Status":"Enabled",
      "Filter":{"Prefix":"backups/"},"Expiration":{"Days":30}}]}'
fi

info "uploading the compose file and helper scripts"
aws s3 sync "${HERE}/box/" "s3://${CONFIG_BUCKET}/box/" --delete --only-show-errors

# ---------------------------------------------------------------------------------------------
step "Box stack"

boxParams=(
  "EnvironmentName=${ENVIRONMENT}"
  "NetworkStackName=${NETWORK_STACK}"
  "AuthStackName=${AUTH_STACK}"
  "MediaStackName=${mediaStack}"
  "ApiDomainName=${API_HOST}"
  "HostedZoneId=${zoneId}"
  "WebBaseUrl=https://${WEB_HOST}"
  "MailFrom=${MAIL_FROM}"
  "MailConfigurationSet=${mailConfigurationSet}"
  "ConfigBucket=${CONFIG_BUCKET}"
)

info "deploying ${BOX_STACK}"
aws cloudformation deploy \
  --stack-name "${BOX_STACK}" \
  --template-file "${HERE}/box.yaml" \
  --capabilities CAPABILITY_IAM \
  --no-fail-on-empty-changeset \
  --parameter-overrides "${boxParams[@]}" >/dev/null

instanceId="$(output "${BOX_STACK}" InstanceId)"
repository="$(output "${BOX_STACK}" RepositoryUri)"
publicIp="$(output "${BOX_STACK}" PublicIp)"
info "instance ${instanceId} at ${publicIp}"

# ---------------------------------------------------------------------------------------------
step "Image"

sha="$(git -C "${ROOT}" rev-parse HEAD)"

# Tags are immutable, so a rerun at the same commit must not try to push again.
if aws ecr describe-images --repository-name "${repository##*/}" \
    --image-ids "imageTag=${sha}" >/dev/null 2>&1; then
  info "image ${sha} is already in the registry"
else
  info "building and pushing ${repository}:${sha}"
  aws ecr get-login-password | docker login --username AWS --password-stdin "${repository%%/*}" >/dev/null
  docker build --platform linux/arm64 -q -t "${repository}:${sha}" "${ROOT}/backend" >/dev/null
  docker push -q "${repository}:${sha}" >/dev/null
fi

# ---------------------------------------------------------------------------------------------
step "Deploy"

info "waiting for the instance to finish first-boot setup"
bootstrapped=false
for _ in $(seq 1 90); do
  ping="$(aws ssm describe-instance-information \
    --filters "Key=InstanceIds,Values=${instanceId}" \
    --query 'InstanceInformationList[0].PingStatus' --output text 2>/dev/null || true)"
  [[ "${ping}" == "Online" ]] || { sleep 10; continue; }

  probeId="$(aws ssm send-command \
    --instance-ids "${instanceId}" \
    --document-name AWS-RunShellScript \
    --comment "bootstrap-box probe" \
    --parameters 'commands=["if test -f /opt/box/.bootstrap-complete; then echo ready; elif test -x /opt/box/deploy.sh && test -f /opt/box/.env; then echo ready; else echo waiting; fi"]' \
    --timeout-seconds 30 \
    --query 'Command.CommandId' --output text)"
  for _ in $(seq 1 12); do
    probeStatus="$(aws ssm get-command-invocation --command-id "${probeId}" \
      --instance-id "${instanceId}" --query Status --output text 2>/dev/null || echo Pending)"
    [[ "${probeStatus}" == "InProgress" || "${probeStatus}" == "Pending" ]] && { sleep 2; continue; }
    break
  done
  probeOut="$(aws ssm get-command-invocation --command-id "${probeId}" \
    --instance-id "${instanceId}" --query StandardOutputContent --output text 2>/dev/null || true)"
  if [[ "${probeOut}" == *ready* ]]; then
    bootstrapped=true
    break
  fi
  sleep 10
done
[[ "${bootstrapped}" == true ]] \
  || die "First-boot setup did not finish. Check /var/log/box-bootstrap.log via Session Manager."

info "running deploy.sh on the instance"
commandId="$(aws ssm send-command \
  --instance-ids "${instanceId}" \
  --document-name AWS-RunShellScript \
  --comment "bootstrap-box ${sha}" \
  --parameters "commands=[\"/opt/box/deploy.sh ${sha}\"]" \
  --timeout-seconds 600 \
  --query 'Command.CommandId' --output text)"

# Polls rather than `ssm wait command-executed`, which reports Failed as an error without ever
# showing the output that says why.
for _ in $(seq 1 90); do
  status="$(aws ssm get-command-invocation --command-id "${commandId}" \
    --instance-id "${instanceId}" --query Status --output text 2>/dev/null || echo Pending)"
  case "${status}" in
    Success) break ;;
    Failed|Cancelled|TimedOut)
      aws ssm get-command-invocation --command-id "${commandId}" --instance-id "${instanceId}" \
        --query StandardErrorContent --output text >&2
      die "deploy.sh failed on the instance."
      ;;
  esac
  sleep 10
done
[[ "${status}" == "Success" ]] || die "deploy.sh did not finish in time."

aws ssm get-command-invocation --command-id "${commandId}" --instance-id "${instanceId}" \
  --query StandardOutputContent --output text | tail -3 | sed 's/^/    /'

# ---------------------------------------------------------------------------------------------
step "Checking it answers"

# Caddy asks Let's Encrypt for a certificate on the first request to the new name, which takes a
# few seconds and fails until the A record has propagated far enough for the challenge to reach
# this instance.
for _ in $(seq 1 30); do
  if curl --fail --silent --max-time 5 "https://${API_HOST}/actuator/health" >/dev/null 2>&1; then
    ok=true
    break
  fi
  sleep 10
done

if [[ "${ok:-false}" == true ]]; then
  info "https://${API_HOST} is serving over TLS"
else
  warn "No answer from https://${API_HOST} yet."
  warn "Usually DNS propagation or the ACME challenge. Check with:"
  warn "  aws ssm start-session --target ${instanceId}"
  warn "  sudo docker compose -f /opt/box/docker-compose.yml logs caddy"
fi

# ---------------------------------------------------------------------------------------------
step "Done"
info "API        https://${API_HOST}"
info "Instance   ${instanceId} (${publicIp})"
info "Shell      aws ssm start-session --target ${instanceId}"
info ""
info "Stop paying between demonstrations, keeping the address, the disk and the data:"
info "  aws ec2 stop-instances --instance-ids ${instanceId}"
info "  aws ec2 start-instances --instance-ids ${instanceId}"
