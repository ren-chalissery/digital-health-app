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
# Roughly $74 a month, of which the load balancer, RDS, ElastiCache and Fargate are 96%. `--pause`
# removes all four and keeps everything that is free — the Cognito pool, the VPC and the deploy
# role — leaving about seventy cents a month: fifty for the Route 53 hosted zone and twenty for the
# database snapshot, which bills on the 1.6GB actually used rather than the 20GB allocated.
#
# That is the option most people want, and unlike a full teardown the infrastructure genuinely
# comes back. The *data* does not: bootstrap.sh creates an empty database and never reads the
# snapshot, so restoring it is a manual `aws rds restore-db-instance-from-db-snapshot` followed by
# repointing the app stack. Fine while the only rows are test data. Not fine after launch.

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
Usage: teardown.sh [--pause] [--purge] [--yes] [--dry-run]

  --pause      Delete only what costs money while idle: the load balancer, Fargate task, database
               and cache. Keeps auth, network, web, media and the deploy role, all of which are
               free or near-free when nothing is running. About $74 a month becomes about $0.70.

               Accounts keep their pool ids, so the phone apps still work; CI can still deploy;
               the marketing site stays up. Restart with bootstrap.sh.

               The database comes back EMPTY. See the note below.

  --keep-auth  The same thing, under its original name.

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
    --pause|--keep-auth) KEEP_AUTH=true ;;
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
  # Everything skipped here is free, or close enough, when nothing is running: an IAM role and a
  # VPC without a NAT gateway cost nothing, Cognito is free below ten thousand users, and web and
  # media are S3 behind CloudFront with an on-demand MediaConvert queue, all billed per request.
  #
  # So deleting them saves nothing and costs plenty: the deploy role is what CI authenticates with,
  # and the web stack owns the DNS record and the CloudFront distribution, which take fifteen
  # minutes to come back and change the site's certificate validation on the way.
  if [ "$KEEP_AUTH" = true ]; then
    case "$stack" in
      digital-health-auth|digital-health-network|digital-health-deploy-role|\
      digital-health-web|digital-health-media) continue ;;
    esac
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
  if [ "$KEEP_AUTH" = true ]; then
    info "- The Cognito pool, with its ids, so the phone apps keep working."
    info "- The network, the deploy role and the web and media stacks, which are free while idle."
    info "- The database, as a final snapshot, costing about twenty cents a month."
    info ""
    warn "bootstrap.sh creates an EMPTY database. It does not restore the snapshot."
    warn "Modules, reflections and memberships will be gone unless you restore it by hand."
    info ""
    info "Restart with: ./infra/bootstrap.sh"
  else
    info "- The Cognito user pool, orphaned. A later bootstrap makes a new one, and nobody can sign in."
    info "- Both S3 buckets, with their contents."
    info "- The database, as a final snapshot. Restoring it is manual."
    warn "This is not a pause. Use --pause for that."
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

# Bucket names are generated by CloudFormation, so they have to be read from the stack outputs,
# and read *now*: once the stack is gone the outputs go with it and the retained bucket is only
# findable by trawling every bucket in the account.
bucketFromStack() {
  aws cloudformation describe-stacks --stack-name "$1" \
    --query "Stacks[0].Outputs[?OutputKey=='$2'].OutputValue | [0]" \
    --output text 2>/dev/null || true
}

retainedBuckets=()
if [ "$PURGE" = true ]; then
  for pair in "digital-health-web:BucketName" "digital-health-media:AssetBucketName"; do
    name="$(bucketFromStack "${pair%%:*}" "${pair##*:}")"
    [ -n "${name:-}" ] && [ "$name" != "None" ] && retainedBuckets+=("$name")
  done

  step "Purging retained resources first"
  for bucket in "${retainedBuckets[@]:-}"; do
    [ -n "$bucket" ] && emptyBucket "$bucket"
  done
fi

# ECR refuses to delete a repository holding images unless told otherwise, and the app stack owns
# it. A pause keeps the images: storage is pennies a month, and bootstrap.sh needs an image to
# start the task, so throwing them away would mean a full CI run before the estate came back.
repo=""
if [ "$KEEP_AUTH" = false ]; then
  repo="$(aws ecr describe-repositories \
    --query "repositories[?contains(repositoryName, 'digital-health')].repositoryName | [0]" \
    --output text 2>/dev/null || true)"
fi
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
  for bucket in "${retainedBuckets[@]:-}"; do
    [ -z "$bucket" ] && continue
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
if [ "$KEEP_AUTH" = true ]; then
  info "Paused. Roughly \$0.70 a month remains: the hosted zone and the database snapshot."
  info "Run ./infra/bootstrap.sh to bring it back, remembering the database returns empty."
elif [ "$PURGE" = false ]; then
  info "The database snapshot, both buckets and the Cognito pool remain."
  info "Before running bootstrap.sh again, read the header of this script."
fi
