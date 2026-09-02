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
# The network, auth and mail stacks, all of which are free or near-free at idle and are shared
# with the expensive topology. Run bootstrap.sh first if this is a brand new account, then
# teardown.sh --pause to remove the parts this replaces.
#
# ## What it is not
#
# A production deployment. One instance, one local database, no failover, no point-in-time
# recovery, and a recovery point of however long ago the nightly dump last ran. Read the risk
# section of infra/README.md before pointing anything real at this.
#
# ## Where the data comes from
#
# A new box starts with an empty database unless teardown.sh archived one, in which case the newest
# archived dump is restored before the health check. That is what makes teardown.sh a way to stop
# paying rather than a way to lose everything.
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
readonly MAIL_STACK=digital-health-mail

readonly NETWORK_STACK=digital-health-network
readonly AUTH_STACK=digital-health-auth
readonly MEDIA_STACK=digital-health-media
readonly APP_STACK=digital-health-app
readonly BOX_STACK=digital-health-box

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA="$(cd "${HERE}/.." && pwd)"
ROOT="$(cd "${INFRA}/.." && pwd)"
readonly HERE INFRA ROOT

# Files the instance needs under s3://<config-bucket>/box/. The template and scripts in this
# directory stay local.
readonly RUNTIME_SYNC_EXCLUDES=(
  --exclude 'box.yaml'
  --exclude 'bootstrap.sh'
  --exclude 'teardown.sh'
)

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

deployBoxStack() {
  aws cloudformation deploy \
    --stack-name "${BOX_STACK}" \
    --template-file "${HERE}/box.yaml" \
    --capabilities CAPABILITY_IAM \
    --no-fail-on-empty-changeset \
    --parameter-overrides "$@" >/dev/null
}

# `aws cloudformation deploy` refuses to start while another operation is running on the stack, and
# an interrupted earlier run leaves exactly that behind. Waiting is always the right answer, so wait
# rather than making the operator work out which half-finished state they are in.
#
# REVIEW_IN_PROGRESS is not an operation in flight: it is a stack that only ever had a change set
# created against it, and it is settled as far as deploying is concerned.
waitForStack() {
  local stack="$1" status=''
  for _ in $(seq 1 90); do
    status="$(aws cloudformation describe-stacks --stack-name "${stack}" \
      --query 'Stacks[0].StackStatus' --output text 2>/dev/null || echo ABSENT)"
    case "${status}" in
      ABSENT|REVIEW_IN_PROGRESS|*_COMPLETE|*_FAILED) return 0 ;;
    esac
    info "${stack} is ${status}; waiting for it to finish"
    sleep 10
  done
  die "${stack} is still ${status} after fifteen minutes."
}

# Runs one command on the instance and writes what it printed to stdout. Polls rather than
# `ssm wait command-executed`, which reports Failed as an error without ever showing the output that
# says why.
runOnInstance() {
  local instance="$1" comment="$2" command="$3"
  local commandId status=Pending

  commandId="$(aws ssm send-command \
    --instance-ids "${instance}" \
    --document-name AWS-RunShellScript \
    --comment "${comment}" \
    --parameters "commands=[\"${command}\"]" \
    --timeout-seconds 600 \
    --query 'Command.CommandId' --output text)"

  for _ in $(seq 1 90); do
    status="$(aws ssm get-command-invocation --command-id "${commandId}" \
      --instance-id "${instance}" --query Status --output text 2>/dev/null || echo Pending)"
    case "${status}" in
      Success) break ;;
      Failed|Cancelled|TimedOut)
        aws ssm get-command-invocation --command-id "${commandId}" --instance-id "${instance}" \
          --query StandardErrorContent --output text >&2
        die "${command} failed on the instance."
        ;;
    esac
    sleep 10
  done
  [[ "${status}" == "Success" ]] || die "${command} did not finish in time."

  aws ssm get-command-invocation --command-id "${commandId}" --instance-id "${instance}" \
    --query StandardOutputContent --output text
}

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
stackExists "${MAIL_STACK}" || die "${MAIL_STACK} is missing. Run bootstrap.sh first."

mediaStack=''
if stackExists "${MEDIA_STACK}"; then
  mediaStack="${MEDIA_STACK}"
  info "media stack found, so video is available"
else
  warn "No media stack. Video will be unavailable; everything else works."
fi

mailConfigurationSet="$(output "${MAIL_STACK}" MailConfigurationSetName)"
[[ "${mailConfigurationSet}" == "None" || -z "${mailConfigurationSet}" ]] \
  && die "${MAIL_STACK} is missing MailConfigurationSetName. Run ./infra/bootstrap.sh."

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

# Written by teardown.sh, read here. Outside CloudFormation and outside the configuration bucket,
# because the whole point is that it outlives both.
readonly ARCHIVE_BUCKET="digital-health-box-backups-${ENVIRONMENT}-${account}"

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
aws s3 sync "${HERE}/" "s3://${CONFIG_BUCKET}/box/" --delete --only-show-errors \
  "${RUNTIME_SYNC_EXCLUDES[@]}"

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
waitForStack "${BOX_STACK}"
if ! deployBoxStack "${boxParams[@]}"; then
  # Seen once against an empty account: `deploy` aborted with the stack already CREATE_IN_PROGRESS,
  # eleven seconds after the create it had itself just started. Whatever the cause, the stack was
  # building correctly and the only thing wrong was that the script had stopped watching it.
  info "the deploy did not go through; waiting for the stack to settle and trying once more"
  waitForStack "${BOX_STACK}"
  deployBoxStack "${boxParams[@]}" || die "Could not deploy ${BOX_STACK}."
fi

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

  # Only .bootstrap-complete will do. It is touched at the very end of the instance's user data,
  # after its own call to deploy.sh has returned, and this used to also accept the mere existence of
  # deploy.sh and .env — which are in place minutes earlier, while that first deploy is still
  # bringing containers up. Sending a second deploy into that window fails on the container names
  # the first one is in the middle of creating. The comment in box.yaml expects first-boot deploy to
  # be a no-op because the registry is empty, and that is true only on the very first deploy into a
  # new account; on any later rebuild the image is already there and the first-boot deploy runs for
  # real.
  probeId="$(aws ssm send-command \
    --instance-ids "${instanceId}" \
    --document-name AWS-RunShellScript \
    --comment "box bootstrap probe" \
    --parameters 'commands=["if test -f /opt/box/.bootstrap-complete; then echo ready; else echo waiting; fi"]' \
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
runOnInstance "${instanceId}" "box bootstrap ${sha}" "/opt/box/deploy.sh ${sha}" \
  | tail -3 | sed 's/^/    /'

# ---------------------------------------------------------------------------------------------
step "Database"

# teardown.sh dumps the database here before it deletes anything, so a box that was torn down and
# stood back up comes back with its data. The marker records which dump this box was restored from:
# without it a second run of this script — which is meant to be harmless — would replay the same
# dump over everything the box had done since the first one.
if aws s3api head-object --bucket "${CONFIG_BUCKET}" --key restored-from >/dev/null 2>&1; then
  info "already restored from $(aws s3 cp "s3://${CONFIG_BUCKET}/restored-from" - 2>/dev/null)"
else
  archived=''
  if aws s3api head-bucket --bucket "${ARCHIVE_BUCKET}" >/dev/null 2>&1; then
    # A bucket with no backups/ prefix yet is not an error, but `aws s3 ls` has historically exited
    # non-zero for it, and pipefail would turn that into a failed bootstrap.
    archived="$(aws s3 ls "s3://${ARCHIVE_BUCKET}/backups/" | sort | tail -1 | awk '{print $4}')" \
      || archived=''
  fi

  if [[ -z "${archived}" ]]; then
    info "nothing archived in ${ARCHIVE_BUCKET}, so this box starts empty"
  else
    info "restoring ${archived}"
    # Into the configuration bucket rather than straight onto the instance: restore.sh reads from
    # the bucket named by BACKUP_BUCKET in its environment, and the instance role deliberately has
    # no reach outside its own two buckets.
    aws s3 cp "s3://${ARCHIVE_BUCKET}/backups/${archived}" \
      "s3://${CONFIG_BUCKET}/backups/${archived}" --only-show-errors
    runOnInstance "${instanceId}" "box restore ${archived}" "/opt/box/restore.sh ${archived}" \
      | tail -2 | sed 's/^/    /'
    printf '%s\n' "${archived}" \
      | aws s3 cp - "s3://${CONFIG_BUCKET}/restored-from" --only-show-errors
  fi
fi

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
info "Stop paying between demonstrations, keeping the address, the disk and the data (~\$8/month):"
info "  aws ec2 stop-instances --instance-ids ${instanceId}"
info "  aws ec2 start-instances --instance-ids ${instanceId}"
info ""
info "Or stop paying almost entirely (~\$1/month). The database is dumped and restored on the way"
info "back, so this costs the writes since the dump rather than everything:"
info "  ./infra/box/teardown.sh"
