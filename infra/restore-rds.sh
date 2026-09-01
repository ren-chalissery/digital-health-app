#!/usr/bin/env bash
#
# Restores the managed RDS instance from a snapshot. Requires a fixed database password in Secrets
# Manager (see data.yaml) so the restored instance accepts the same credentials the app already uses.
#
# Usage:
#   ./infra/restore-rds.sh --snapshot digital-health-data-snapshot-database-m2lopqrw4seh
#   ./infra/restore-rds.sh --latest-pause-snapshot
#   ./infra/restore-rds.sh --dry-run --latest-pause-snapshot
#
# The API is unavailable from step 1 until the service stabilises at the end.

set -euo pipefail

readonly EXPECTED_ACCOUNT="${EXPECTED_ACCOUNT:-917993967729}"
readonly REGION="${AWS_REGION:-ap-southeast-2}"
readonly ENVIRONMENT="${ENVIRONMENT:-prod}"
readonly NETWORK_STACK="${NETWORK_STACK:-digital-health-network}"
readonly DATA_STACK="${DATA_STACK:-digital-health-data}"
readonly APP_STACK="${APP_STACK:-digital-health-app}"
readonly DB_IDENTIFIER="${DB_IDENTIFIER:-digital-health-${ENVIRONMENT}}"
readonly SECRET_NAME="digital-health/${ENVIRONMENT}/database"

export AWS_REGION="${REGION}"
export AWS_DEFAULT_REGION="${REGION}"

step() { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
info() { printf '    %s\n' "$*"; }
die() { printf '\n\033[31mError: %s\033[0m\n' "$*" >&2; exit 1; }

output() {
  aws cloudformation describe-stacks --stack-name "$1" \
    --query "Stacks[0].Outputs[?OutputKey=='$2'].OutputValue" --output text 2>/dev/null
}

run() {
  if [[ "${DRY_RUN:-false}" == true ]]; then
    printf '    [dry-run] %q\n' "$*"
  else
    "$@"
  fi
}

usage() {
  cat <<EOF
Usage: restore-rds.sh [--dry-run] (--snapshot <id> | --latest-pause-snapshot)

  --snapshot <id>              Snapshot identifier to restore from
  --latest-pause-snapshot      Use the newest manual snapshot from the data stack
  --dry-run                    Print commands without running them
EOF
  exit 1
}

DRY_RUN=false
SNAPSHOT=""

while (($#)); do
  case "$1" in
    --dry-run)
      DRY_RUN=true
      shift
      ;;
    --snapshot)
      shift
      SNAPSHOT="${1:?--snapshot requires an id}"
      shift
      ;;
    --latest-pause-snapshot)
      SNAPSHOT="$(aws rds describe-db-snapshots \
        --query "reverse(sort_by(DBSnapshots[?starts_with(DBSnapshotIdentifier, 'digital-health-data-snapshot')], &SnapshotCreateTime))[0].DBSnapshotIdentifier" \
        --output text)"
      [[ -n "${SNAPSHOT}" && "${SNAPSHOT}" != "None" ]] \
        || die "No digital-health-data-snapshot* snapshots found."
      shift
      ;;
    -h|--help) usage ;;
    *) die "Unknown option: $1" ;;
  esac
done

[[ -n "${SNAPSHOT}" ]] || usage

# ---------------------------------------------------------------------------------------------
step "Preflight"

account="$(aws sts get-caller-identity --query Account --output text)" \
  || die "No AWS credentials."
[[ "${account}" == "${EXPECTED_ACCOUNT}" ]] \
  || die "Refusing to run: credentials are for account ${account}, expected ${EXPECTED_ACCOUNT}."
info "account ${account}, region ${REGION}"
info "snapshot ${SNAPSHOT}"

if aws cloudformation describe-stacks --stack-name digital-health-box >/dev/null 2>&1; then
  die "The box stack is deployed. This script is for the managed RDS topology only."
fi

cluster="$(output "${APP_STACK}" ClusterName)"
service="$(output "${APP_STACK}" ServiceName)"
[[ -n "${cluster}" && -n "${service}" ]] || die "App stack outputs missing. Is ${APP_STACK} deployed?"

dbPassword="$(aws secretsmanager get-secret-value --secret-id "${SECRET_NAME}" \
  --query SecretString --output text | jq -r .password)" \
  || die "Could not read ${SECRET_NAME}. Deploy the data stack with DB_MASTER_PASSWORD first."

subnetGroup="$(aws rds describe-db-instances --db-instance-identifier "${DB_IDENTIFIER}" \
  --query 'DBInstances[0].DBSubnetGroup.DBSubnetGroupName' --output text 2>/dev/null || true)"
if [[ -z "${subnetGroup}" || "${subnetGroup}" == "None" ]]; then
  subnetGroup="$(aws cloudformation describe-stack-resources --stack-name "${DATA_STACK}" \
    --logical-resource-id DbSubnetGroup \
    --query 'StackResources[0].PhysicalResourceId' --output text)"
fi
[[ -n "${subnetGroup}" && "${subnetGroup}" != "None" ]] || die "Could not resolve the DB subnet group."

dataSg="$(aws cloudformation list-exports \
  --query "Exports[?Name=='${NETWORK_STACK}-DataSecurityGroupId'].Value | [0]" --output text)"
[[ -n "${dataSg}" && "${dataSg}" != "None" ]] || die "Could not resolve the data security group."

info "subnet group ${subnetGroup}"
info "security group ${dataSg}"

# ---------------------------------------------------------------------------------------------
step "Scale API to zero"

run aws ecs update-service --cluster "${cluster}" --service "${service}" --desired-count 0
if [[ "${DRY_RUN}" != true ]]; then
  info "waiting for tasks to stop"
  aws ecs wait services-stable --cluster "${cluster}" --services "${service}"
fi

# ---------------------------------------------------------------------------------------------
step "Replace the database instance"

if [[ "${DRY_RUN}" != true ]] \
    && aws rds describe-db-instances --db-instance-identifier "${DB_IDENTIFIER}" >/dev/null 2>&1; then
  protection="$(aws rds describe-db-instances --db-instance-identifier "${DB_IDENTIFIER}" \
    --query 'DBInstances[0].DeletionProtection' --output text)"
  if [[ "${protection}" == "True" ]]; then
    info "disabling deletion protection"
    aws rds modify-db-instance \
      --db-instance-identifier "${DB_IDENTIFIER}" \
      --no-deletion-protection \
      --apply-immediately >/dev/null
    aws rds wait db-instance-available --db-instance-identifier "${DB_IDENTIFIER}"
  fi

  info "deleting ${DB_IDENTIFIER}"
  aws rds delete-db-instance \
    --db-instance-identifier "${DB_IDENTIFIER}" \
    --skip-final-snapshot >/dev/null
  aws rds wait db-instance-deleted --db-instance-identifier "${DB_IDENTIFIER}"
else
  run aws rds delete-db-instance \
    --db-instance-identifier "${DB_IDENTIFIER}" \
    --skip-final-snapshot
fi

info "restoring ${SNAPSHOT} as ${DB_IDENTIFIER}"
run aws rds restore-db-instance-from-db-snapshot \
  --db-instance-identifier "${DB_IDENTIFIER}" \
  --db-snapshot-identifier "${SNAPSHOT}" \
  --db-subnet-group-name "${subnetGroup}" \
  --vpc-security-group-ids "${dataSg}" \
  --no-publicly-accessible \
  --master-user-password "${dbPassword}"

if [[ "${DRY_RUN}" != true ]]; then
  aws rds wait db-instance-available --db-instance-identifier "${DB_IDENTIFIER}"

  protection="$(aws rds describe-db-instances --db-instance-identifier "${DB_IDENTIFIER}" \
    --query 'DBInstances[0].DeletionProtection' --output text)"
  if [[ "${protection}" != "True" ]]; then
    info "re-enabling deletion protection"
    aws rds modify-db-instance \
      --db-instance-identifier "${DB_IDENTIFIER}" \
      --deletion-protection \
      --apply-immediately >/dev/null
    aws rds wait db-instance-available --db-instance-identifier "${DB_IDENTIFIER}"
  fi
fi

# ---------------------------------------------------------------------------------------------
step "Scale API back up"

run aws ecs update-service --cluster "${cluster}" --service "${service}" --desired-count 1
if [[ "${DRY_RUN}" != true ]]; then
  info "waiting for the service to stabilise"
  aws ecs wait services-stable --cluster "${cluster}" --services "${service}"
fi

# ---------------------------------------------------------------------------------------------
step "Done"

apiHost="${API_HOST:-api.simplicityhelp.com}"
cat <<EOF

    RDS restored from ${SNAPSHOT}.
    Check the API:

      curl https://${apiHost}/actuator/health/readiness

EOF
