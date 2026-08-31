#!/usr/bin/env bash
#
# Removes every stack, in the order the exports allow.
#
# The counterpart to bootstrap.sh, and far more dangerous, so it is deliberately awkward: it names
# what it will destroy, says what survives, and refuses to proceed without the environment typed
# back.
#
# ## Teardown is not reversible by default, and that is the important part
#
# Three resources are retained rather than deleted, which sounds safe and creates a trap:
#
#   * The Cognito user pool. It survives, but it is no longer managed by any stack, and a later
#     bootstrap creates a *new* pool with new ids. Every clinician's account is orphaned — they
#     cannot sign in, and their memberships point at a pool nothing reads.
#   * Both S3 buckets, whose names are deterministic
#     (digital-health-web-${env}-${account}). A later bootstrap fails outright, because the bucket
#     already exists and CloudFormation will not adopt it.
#   * The database, as a final snapshot. Restoring it is a manual operation.
#
# So: tearing down and running bootstrap.sh again does not give you back what you had. It gives you
# a failed deploy, and then, once that is worked around, an empty product whose users cannot log in.
#
# --purge deletes those three as well, which is honest about the outcome: nothing to collide with,
# nothing to restore, and no accounts.
#
# ## If the goal is to stop paying
#
# Roughly $74 a month, of which the load balancer, RDS, ElastiCache and Fargate are 96%. Deleting
# the app, data and web stacks removes almost all of it while leaving auth and network standing, so
# accounts survive. That is `--keep-auth`, and it is the option most people actually want.

set -euo pipefail

readonly EXPECTED_ACCOUNT="${EXPECTED_ACCOUNT:-917993967729}"
readonly REGION="${AWS_REGION:-ap-southeast-2}"
readonly ENVIRONMENT="${ENVIRONMENT:-prod}"

# Reverse of bootstrap's order. app first because it imports from network, data, auth and media —
# CloudFormation refuses to delete a stack whose exports are still in use, which is the same rule
# that made a cache change roll back in August.
readonly ORDER=(
  digital-health-app
  digital-health-web
  digital-health-media
  digital-health-auth
  digital-health-data
  digital-health-network
  digital-health-deploy-role
)

export AWS_REGION="${REGION}"
export AWS_DEFAULT_REGION="${REGION}"

PURGE=false
KEEP_AUTH=false
ASSUME_YES=false
DRY_RUN=false

step() { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
info() { printf '    %s\n' "$*"; }
warn() { printf '\033[33m    %s\033[0m\n' "$*"; }
die()  { printf '\n\033[31mError: %s\033[0m\n' "$*" >&2; exit 1; }

usage() {
  cat <<'USAGE'
Usage: teardown.sh [--keep-auth] [--purge] [--yes]

  --keep-auth  Leave the auth and network stacks standing, so Cognito accounts survive and a
               rebuild works. Removes the load balancer, database, cache and Fargate task, which
               is where the money goes. Use this to stop paying.

  --purge      Also delete the retained Cognito pool and both S3 buckets. Destroys every account,
               every uploaded video and the web bundle. Without it, a later bootstrap fails on the
               bucket names.

  --yes        Skip the confirmation prompt. For automation only.

  --dry-run    Print every destructive call without making it, and exit. The only way to inspect
               the destructive path without a disposable environment to run it against.
USAGE
}

while [ $# -gt 0 ]; do
  case "$1" in
    --purge) PURGE=true ;;
    --keep-auth) KEEP_AUTH=true ;;
    --yes) ASSUME_YES=true ;;
    --dry-run) DRY_RUN=true ;;
    -h|--help) usage; exit 0 ;;
    *) usage; die "Unknown argument: $1" ;;
  esac
  shift
done

if [ "$PURGE" = true ] && [ "$KEEP_AUTH" = true ]; then
  die "--purge and --keep-auth contradict each other: one destroys the accounts, the other keeps them."
fi

step "Checking which account this is"
account="$(aws sts get-caller-identity --query Account --output text)"
[ "$account" = "$EXPECTED_ACCOUNT" ] ||
  die "This is account ${account}, not ${EXPECTED_ACCOUNT}. Refusing to delete anything."
info "Account ${account}, region ${REGION}, environment ${ENVIRONMENT}"

targets=()
for stack in "${ORDER[@]}"; do
  if [ "$KEEP_AUTH" = true ] &&
     { [ "$stack" = digital-health-auth ] || [ "$stack" = digital-health-network ]; }; then
    continue
  fi
  if aws cloudformation describe-stacks --stack-name "$stack" >/dev/null 2>&1; then
    targets+=("$stack")
  fi
done

if [ ${#targets[@]} -eq 0 ]; then
  step "Nothing to do"
  info "None of the stacks exist."
  exit 0
fi

step "This will delete ${#targets[@]} stack(s)"
for stack in "${targets[@]}"; do info "- ${stack}"; done

step "What survives"
if [ "$PURGE" = true ]; then
  warn "Nothing. --purge deletes the Cognito pool, both S3 buckets and their contents."
  warn "Every clinician's account, every uploaded video and every reflection goes with the database."
else
  info "- The Cognito user pool, orphaned. A later bootstrap makes a new one and nobody can sign in."
  info "- Both S3 buckets, with their contents. A later bootstrap FAILS on the bucket names."
  info "- The database, as a final snapshot. Restoring it is manual."
  if [ "$KEEP_AUTH" = false ]; then
    warn "This is not a pause. Read the header of this script before continuing."
  fi
fi

if [ "$ASSUME_YES" = false ] && [ "$DRY_RUN" = false ]; then
  printf '\nType the environment name (%s) to continue: ' "$ENVIRONMENT"
  read -r typed
  [ "$typed" = "$ENVIRONMENT" ] || die "Got '${typed}'. Nothing deleted."
fi

# Every destructive call goes through here. Routing them all through one function is what stops
# --dry-run missing one: a new deletion added later has to be written as `run ...` to work at all.
run() {
  if [ "$DRY_RUN" = true ]; then
    # To stderr, so a caller redirecting stdout to /dev/null cannot silently hide what would be
    # destroyed. An under-reporting dry run is worse than none.
    printf '    \033[36mwould run:\033[0m %s\n' "$*" >&2
    return 0
  fi
  "$@"
}

emptyBucket() {
  local bucket="$1"
  aws s3api head-bucket --bucket "$bucket" >/dev/null 2>&1 || return 0
  info "Emptying ${bucket}"
  # Versions and delete markers as well as objects: a versioned bucket is not empty just because
  # `s3 rm` finished, and the delete then fails with BucketNotEmpty.
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

deleteStack() {
  local stack="$1"
  step "Deleting ${stack}"
  run aws cloudformation delete-stack --stack-name "$stack"
  if [ "$DRY_RUN" = true ]; then
    info "would wait for ${stack} to finish deleting"
    return 0
  fi
  if aws cloudformation wait stack-delete-complete --stack-name "$stack" 2>/dev/null; then
    info "Gone."
  else
    # Almost always an export still in use, or a bucket that would not empty.
    local reason
    reason="$(aws cloudformation describe-stack-events --stack-name "$stack" \
      --query "StackEvents[?ResourceStatus=='DELETE_FAILED'].ResourceStatusReason | [0]" \
      --output text 2>/dev/null || true)"
    die "Could not delete ${stack}. ${reason:-Check the console.}"
  fi
}

if [ "$PURGE" = true ]; then
  step "Purging retained resources first"
  for bucket in "digital-health-web-${ENVIRONMENT}-${account}" "digital-health-media-${ENVIRONMENT}-${account}"; do
    emptyBucket "$bucket"
  done
fi

# ECR refuses to delete a repository holding images unless told otherwise, and the app stack owns it.
repo="$(aws ecr describe-repositories \
  --query "repositories[?contains(repositoryName, 'digital-health')].repositoryName | [0]" \
  --output text 2>/dev/null || true)"
if [ -n "${repo:-}" ] && [ "$repo" != "None" ]; then
  step "Emptying the container repository"
  ids="$(aws ecr list-images --repository-name "$repo" --query 'imageIds[*]' --output json)"
  if [ "$ids" != "[]" ]; then
    run aws ecr batch-delete-image --repository-name "$repo" --image-ids "$ids"
    info "Removed the images from ${repo}"
  fi
fi

for stack in "${targets[@]}"; do
  deleteStack "$stack"
done

if [ "$PURGE" = true ]; then
  step "Deleting the retained buckets and user pool"
  for bucket in "digital-health-web-${ENVIRONMENT}-${account}" "digital-health-media-${ENVIRONMENT}-${account}"; do
    if aws s3api head-bucket --bucket "$bucket" >/dev/null 2>&1 || [ "$DRY_RUN" = true ]; then
      run aws s3api delete-bucket --bucket "$bucket" && info "Deleted ${bucket}" || true
    fi
  done
  pool="$(aws cognito-idp list-user-pools --max-results 60 \
    --query "UserPools[?Name=='digital-health-${ENVIRONMENT}'].Id | [0]" --output text 2>/dev/null || true)"
  if [ -n "${pool:-}" ] && [ "$pool" != "None" ]; then
    run aws cognito-idp delete-user-pool --user-pool-id "$pool" && info "Deleted user pool ${pool}"
  fi
fi

step "Done"
if [ "$PURGE" = false ]; then
  info "The database snapshot, both buckets and the Cognito pool remain."
  info "Before running bootstrap.sh again, read the header of this script."
fi
