#!/usr/bin/env bash
#
# Removes the single-box topology completely.
#
# Counterpart to bootstrap.sh in this directory. Deletes the CloudFormation stack, the
# configuration bucket bootstrap.sh creates outside it, and the deploy tag in Parameter Store.
# Shared stacks — network, auth, web, media and mail — are untouched.
#
# The database on the instance root volume is destroyed with the stack. Nightly dumps in the
# configuration bucket are deleted when that bucket goes.

set -euo pipefail

readonly EXPECTED_ACCOUNT="${EXPECTED_ACCOUNT:-917993967729}"
readonly REGION="${AWS_REGION:-ap-southeast-2}"
readonly ENVIRONMENT="${ENVIRONMENT:-prod}"
readonly DOMAIN="${DOMAIN:-simplicityhelp.com}"
readonly BOX_STACK=digital-health-box
readonly IMAGE_TAG_PARAMETER="/digital-health/${ENVIRONMENT}/box/image-tag"

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

if ! stackExists "${BOX_STACK}" \
  && ! aws s3api head-bucket --bucket "${CONFIG_BUCKET}" >/dev/null 2>&1 \
  && ! aws ssm get-parameter --name "${IMAGE_TAG_PARAMETER}" >/dev/null 2>&1; then
  step "Nothing to do"
  info "The box stack, configuration bucket and deploy tag are all absent."
  exit 0
fi

step "This will delete"
stackExists "${BOX_STACK}" && info "- CloudFormation stack ${BOX_STACK}"
info "- Configuration bucket ${CONFIG_BUCKET} (compose files and database backups)"
info "- Parameter Store ${IMAGE_TAG_PARAMETER}"

step "What survives"
info "- network, auth, web, media and mail stacks"
info "- Cognito pool, web bundle and uploaded media"
warn "The Postgres database on the instance disk is destroyed with the stack."
warn "Backups in ${CONFIG_BUCKET} are deleted when the bucket goes."

if [ "$ASSUME_YES" = false ] && [ "$DRY_RUN" = false ]; then
  printf '\nType the environment name (%s) to continue: ' "$ENVIRONMENT"
  read -r typed
  [ "$typed" = "$ENVIRONMENT" ] || die "Got '${typed}'. Nothing deleted."
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

step "Deleting the deploy tag"
if aws ssm get-parameter --name "${IMAGE_TAG_PARAMETER}" >/dev/null 2>&1 || [ "$DRY_RUN" = true ]; then
  run aws ssm delete-parameter --name "${IMAGE_TAG_PARAMETER}" && info "Deleted ${IMAGE_TAG_PARAMETER}" || true
fi

step "Done"
info "The box topology is gone. api.${DOMAIN} no longer points anywhere."
info "To stand up managed compute again:  ./infra/bootstrap.sh"
info "To stand up the box again:           ./infra/box/bootstrap.sh"
