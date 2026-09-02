#!/usr/bin/env bash
#
# Removes the single-box topology completely.
#
# Counterpart to bootstrap.sh in this directory. Deletes the CloudFormation stack, the
# configuration bucket bootstrap.sh creates outside it, and the deploy tag in Parameter Store.
# Shared stacks — network, auth, web, media and mail — are untouched.
#
# The database on the instance root volume is destroyed with the stack, and the nightly dumps go
# with the configuration bucket, so this takes one final dump and copies every dump it can find to
# an archive bucket that nothing here deletes. bootstrap.sh restores the newest of them onto the
# next box, which makes teardown the cheap way to idle rather than a way to lose the data.
#
# If the dump cannot be taken, nothing is deleted.

set -euo pipefail

readonly EXPECTED_ACCOUNT="${EXPECTED_ACCOUNT:-917993967729}"
readonly REGION="${AWS_REGION:-ap-southeast-2}"
readonly ENVIRONMENT="${ENVIRONMENT:-prod}"
readonly DOMAIN="${DOMAIN:-simplicityhelp.com}"
readonly BOX_STACK=digital-health-box
readonly IMAGE_TAG_PARAMETER="/digital-health/${ENVIRONMENT}/box/image-tag"
readonly DB_PASSWORD_PARAMETER="/digital-health/${ENVIRONMENT}/box/db-password"

export AWS_REGION="${REGION}"
export AWS_DEFAULT_REGION="${REGION}"

ASSUME_YES=false
DRY_RUN=false

step() { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
info() { printf '    %s\n' "$*"; }
warn() { printf '\033[33m    %s\033[0m\n' "$*"; }
die()  { printf '\n\033[31mError: %s\033[0m\n' "$*" >&2; exit 1; }

usage() {
  cat <<'USAGE'
Usage: teardown.sh [--yes] [--dry-run]

  --yes        Skip the confirmation prompt.

  --dry-run    Print every destructive call without making it.
USAGE
}

while [ $# -gt 0 ]; do
  case "$1" in
    --yes) ASSUME_YES=true ;;
    --dry-run) DRY_RUN=true ;;
    -h|--help) usage; exit 0 ;;
    *) usage; die "Unknown argument: $1" ;;
  esac
  shift
done

run() {
  if [ "$DRY_RUN" = true ]; then
    printf '    \033[36mwould run:\033[0m %s\n' "$*" >&2
    return 0
  fi
  "$@"
}

stackExists() { aws cloudformation describe-stacks --stack-name "$1" >/dev/null 2>&1; }

output() {
  aws cloudformation describe-stacks --stack-name "$1" \
    --query "Stacks[0].Outputs[?OutputKey=='$2'].OutputValue | [0]" --output text 2>/dev/null || true
}

emptyBucket() {
  local bucket="$1"
  aws s3api head-bucket --bucket "$bucket" >/dev/null 2>&1 || return 0
  info "Emptying ${bucket}"
  run aws s3 rm "s3://${bucket}" --recursive || true
  [ "$DRY_RUN" = true ] && return 0
  python3 - "$bucket" <<'PY' || true
import json, subprocess, sys
bucket = sys.argv[1]
for key in ("Versions", "DeleteMarkers"):
    while True:
        out = subprocess.run(
            ["aws", "s3api", "list-object-versions", "--bucket", bucket,
             "--max-keys", "500", "--query", f"{key}[].{{Key:Key,VersionId:VersionId}}",
             "--output", "json"],
            capture_output=True, text=True,
        )
        items = json.loads(out.stdout or "null") or []
        if not items:
            break
        subprocess.run(
            ["aws", "s3api", "delete-objects", "--bucket", bucket,
             "--delete", json.dumps({"Objects": items, "Quiet": True})],
            capture_output=True, text=True,
        )
PY
}

# Deliberately without a lifecycle rule, unlike the backups/ prefix of the configuration bucket.
# That one expires because it holds nightly dumps of a box that is still running; this holds the
# only surviving copy of a box that no longer exists, and there is no length of idle after which
# throwing it away is the right answer. A gzipped dump is measured in megabytes.
ensureArchiveBucket() {
  local bucket="$1"
  aws s3api head-bucket --bucket "$bucket" >/dev/null 2>&1 && return 0
  info "Creating ${bucket}"
  run aws s3api create-bucket --bucket "$bucket" \
    --create-bucket-configuration "LocationConstraint=${REGION}" >/dev/null
  run aws s3api put-public-access-block --bucket "$bucket" \
    --public-access-block-configuration \
    'BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true'
  run aws s3api put-bucket-encryption --bucket "$bucket" \
    --server-side-encryption-configuration \
    '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'
}

# The instance is often stopped between demonstrations, and a stopped instance cannot be asked for a
# dump. Starting it costs a few minutes and an hour of instance time, against losing the database.
wakeInstance() {
  local instance="$1" state
  state="$(aws ec2 describe-instances --instance-ids "$instance" \
    --query 'Reservations[0].Instances[0].State.Name' --output text 2>/dev/null || echo unknown)"
  [ "$state" = "running" ] && return 0

  if [ "$state" = "stopping" ]; then
    info "Instance is stopping; waiting for it to settle before starting it again"
    aws ec2 wait instance-stopped --instance-ids "$instance"
    state=stopped
  fi
  [ "$state" = "stopped" ] \
    || die "Instance ${instance} is ${state}, so its database cannot be dumped."

  info "Starting ${instance} to take the final dump"
  aws ec2 start-instances --instance-ids "$instance" >/dev/null
  aws ec2 wait instance-running --instance-ids "$instance"
}

waitForAgent() {
  local instance="$1" ping
  for _ in $(seq 1 60); do
    ping="$(aws ssm describe-instance-information \
      --filters "Key=InstanceIds,Values=${instance}" \
      --query 'InstanceInformationList[0].PingStatus' --output text 2>/dev/null || true)"
    [ "$ping" = "Online" ] && return 0
    sleep 10
  done
  die "Session Manager never came online for ${instance}, so the database cannot be dumped."
}

dumpDatabase() {
  local instance="$1" commandId status=Pending
  commandId="$(aws ssm send-command \
    --instance-ids "$instance" \
    --document-name AWS-RunShellScript \
    --comment "box teardown dump" \
    --parameters 'commands=["/opt/box/backup.sh"]' \
    --timeout-seconds 600 \
    --query 'Command.CommandId' --output text)"

  for _ in $(seq 1 60); do
    status="$(aws ssm get-command-invocation --command-id "$commandId" \
      --instance-id "$instance" --query Status --output text 2>/dev/null || echo Pending)"
    case "$status" in
      Success) break ;;
      Failed|Cancelled|TimedOut)
        aws ssm get-command-invocation --command-id "$commandId" --instance-id "$instance" \
          --query StandardErrorContent --output text >&2
        die "backup.sh failed on the instance. Nothing has been deleted."
        ;;
    esac
    sleep 10
  done
  [ "$status" = "Success" ] || die "backup.sh did not finish in time. Nothing has been deleted."

  aws ssm get-command-invocation --command-id "$commandId" --instance-id "$instance" \
    --query StandardOutputContent --output text | sed 's/^/    /'
}

emptyRepository() {
  local repo="$1"
  aws ecr describe-repositories --repository-names "$repo" >/dev/null 2>&1 || return 0
  while true; do
    local ids remaining
    ids="$(aws ecr list-images --repository-name "$repo" --query 'imageIds[*]' --output json 2>/dev/null || echo '[]')"
    [ "$ids" = "[]" ] && break
    info "Removing images from ${repo}"
    run aws ecr batch-delete-image --repository-name "$repo" --image-ids "$ids" >/dev/null || true
    remaining="$(aws ecr list-images --repository-name "$repo" --query 'length(imageIds)' --output text 2>/dev/null || echo 0)"
    [ "$remaining" = "0" ] && break
  done
}

step "Checking which account this is"
account="$(aws sts get-caller-identity --query Account --output text)" \
  || die "No AWS credentials. Authenticate first."
[ "$account" = "$EXPECTED_ACCOUNT" ] \
  || die "This is account ${account}, not ${EXPECTED_ACCOUNT}. Refusing to delete anything."
info "Account ${account}, region ${REGION}, environment ${ENVIRONMENT}"

readonly CONFIG_BUCKET="digital-health-box-${ENVIRONMENT}-${account}"
readonly ARCHIVE_BUCKET="digital-health-box-backups-${ENVIRONMENT}-${account}"

if ! stackExists "${BOX_STACK}" \
  && ! aws s3api head-bucket --bucket "${CONFIG_BUCKET}" >/dev/null 2>&1 \
  && ! aws ssm get-parameter --name "${IMAGE_TAG_PARAMETER}" >/dev/null 2>&1 \
  && ! aws ssm get-parameter --name "${DB_PASSWORD_PARAMETER}" >/dev/null 2>&1; then
  step "Nothing to do"
  info "The box stack, configuration bucket and deploy tag are all absent."
  exit 0
fi

step "This will delete"
stackExists "${BOX_STACK}" && info "- CloudFormation stack ${BOX_STACK}"
info "- Configuration bucket ${CONFIG_BUCKET} (compose files and database backups)"
info "- Parameter Store ${IMAGE_TAG_PARAMETER} and ${DB_PASSWORD_PARAMETER}"

step "What survives"
info "- network, auth, web, media and mail stacks"
info "- Cognito pool, web bundle and uploaded media"
info "- Every database dump, copied to ${ARCHIVE_BUCKET} before anything is deleted"
info "  box/bootstrap.sh restores the newest one onto the next box."
warn "The Postgres database on the instance disk is destroyed with the stack, so what comes back"
warn "is the dump taken below, not the moment you ran this."

if [ "$ASSUME_YES" = false ] && [ "$DRY_RUN" = false ]; then
  printf '\nType the environment name (%s) to continue: ' "$ENVIRONMENT"
  read -r typed
  [ "$typed" = "$ENVIRONMENT" ] || die "Got '${typed}'. Nothing deleted."
fi

step "Archiving the database"

instanceId=""
if stackExists "${BOX_STACK}"; then
  instanceId="$(output "${BOX_STACK}" InstanceId)"
  [ "$instanceId" = "None" ] && instanceId=""
fi

configBucketExists=false
if aws s3api head-bucket --bucket "${CONFIG_BUCKET}" >/dev/null 2>&1; then
  configBucketExists=true
fi

if [ -z "$instanceId" ] && [ "$configBucketExists" = false ]; then
  info "No instance and no configuration bucket, so there is nothing to archive."
else
  ensureArchiveBucket "${ARCHIVE_BUCKET}"

  if [ -z "$instanceId" ]; then
    # A stack that never reached an instance, or a bucket left behind by an interrupted run. There
    # is no database to dump, but dumps already in the bucket are still worth carrying out.
    info "No instance to dump; archiving the dumps already in ${CONFIG_BUCKET}."
  elif [ "$DRY_RUN" = true ]; then
    info "would run /opt/box/backup.sh on ${instanceId}"
  else
    wakeInstance "$instanceId"
    waitForAgent "$instanceId"
    dumpDatabase "$instanceId"
  fi

  if [ "$configBucketExists" = true ]; then
    info "Copying dumps to ${ARCHIVE_BUCKET}"
    # Every dump rather than only the newest, because they are small and the newest is the one most
    # likely to be a dump of something that has just gone wrong.
    run aws s3 sync "s3://${CONFIG_BUCKET}/backups/" "s3://${ARCHIVE_BUCKET}/backups/" \
      --only-show-errors
  fi

  # An empty archive after dumping a live instance means the dump did not arrive, whatever the exit
  # codes said. Deleting the stack now would be deleting the only copy.
  if [ "$DRY_RUN" = false ] && [ -n "$instanceId" ]; then
    archived="$(aws s3 ls "s3://${ARCHIVE_BUCKET}/backups/" 2>/dev/null | wc -l | tr -d ' ')" \
      || archived=0
    [ "$archived" -gt 0 ] \
      || die "No dump reached ${ARCHIVE_BUCKET}. Refusing to delete a box whose data is not saved."
    info "${archived} dump(s) in ${ARCHIVE_BUCKET}"
  fi
fi

repo=""
if stackExists "${BOX_STACK}"; then
  uri="$(output "${BOX_STACK}" RepositoryUri)"
  if [ -n "${uri:-}" ] && [ "$uri" != "None" ]; then
    repo="${uri##*/}"
  fi
fi
if [ -z "$repo" ]; then
  repo="digital-health-${ENVIRONMENT}-box"
fi

step "Emptying the container repository"
emptyRepository "$repo"

if stackExists "${BOX_STACK}"; then
  step "Deleting ${BOX_STACK}"
  run aws cloudformation delete-stack --stack-name "${BOX_STACK}"
  if [ "$DRY_RUN" = false ]; then
    if aws cloudformation wait stack-delete-complete --stack-name "${BOX_STACK}" 2>/dev/null; then
      info "Stack gone."
    else
      reason="$(aws cloudformation describe-stack-events --stack-name "${BOX_STACK}" \
        --query "StackEvents[?ResourceStatus=='DELETE_FAILED'].ResourceStatusReason | [0]" \
        --output text 2>/dev/null || true)"
      die "Could not delete ${BOX_STACK}. ${reason:-Check the console.}"
    fi
  else
    info "would wait for ${BOX_STACK} to finish deleting"
  fi
fi

step "Deleting the configuration bucket"
emptyBucket "${CONFIG_BUCKET}"
if aws s3api head-bucket --bucket "${CONFIG_BUCKET}" >/dev/null 2>&1 || [ "$DRY_RUN" = true ]; then
  run aws s3api delete-bucket --bucket "${CONFIG_BUCKET}" && info "Deleted ${CONFIG_BUCKET}" || true
fi

step "Deleting Parameter Store entries"
if aws ssm get-parameter --name "${IMAGE_TAG_PARAMETER}" >/dev/null 2>&1 || [ "$DRY_RUN" = true ]; then
  run aws ssm delete-parameter --name "${IMAGE_TAG_PARAMETER}" && info "Deleted ${IMAGE_TAG_PARAMETER}" || true
fi
if aws ssm get-parameter --name "${DB_PASSWORD_PARAMETER}" >/dev/null 2>&1 || [ "$DRY_RUN" = true ]; then
  run aws ssm delete-parameter --name "${DB_PASSWORD_PARAMETER}" && info "Deleted ${DB_PASSWORD_PARAMETER}" || true
fi

step "Done"
info "The box topology is gone. api.${DOMAIN} no longer points anywhere."
info "The database is in s3://${ARCHIVE_BUCKET}/backups/."
info "To stand up managed compute again:  ./infra/bootstrap.sh"
info "To stand up the box again:           ./infra/box/bootstrap.sh  (restores the newest dump)"
