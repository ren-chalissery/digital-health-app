#!/usr/bin/env bash
#
# Stands up SES bounce and complaint monitoring without the managed app stack.

set -euo pipefail

readonly EXPECTED_ACCOUNT="${EXPECTED_ACCOUNT:-917993967729}"
readonly REGION="${AWS_REGION:-ap-southeast-2}"
readonly ENVIRONMENT="${ENVIRONMENT:-prod}"
readonly MAIL_STACK="digital-health-mail"
readonly ALARM_EMAIL="${ALARM_EMAIL:-ren.chalissery@gmail.com}"

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export AWS_REGION="${REGION}"
export AWS_DEFAULT_REGION="${REGION}"

step() { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
info() { printf '    %s\n' "$*"; }
warn() { printf '\033[33m    %s\033[0m\n' "$*"; }
die() { printf '\n\033[31mError: %s\033[0m\n' "$*" >&2; exit 1; }

step "Preflight"
account="$(aws sts get-caller-identity --query Account --output text)" \
  || die "No AWS credentials."
[[ "${account}" == "${EXPECTED_ACCOUNT}" ]] \
  || die "Refusing to run: credentials are for account ${account}, expected ${EXPECTED_ACCOUNT}."
info "account ${account}, region ${REGION}, alarm email ${ALARM_EMAIL}"

step "Mail stack"
aws cloudformation deploy \
  --stack-name "${MAIL_STACK}" \
  --template-file "${HERE}/mail.yaml" \
  --parameter-overrides \
    "EnvironmentName=${ENVIRONMENT}" \
    "AlarmEmail=${ALARM_EMAIL}" \
  --no-fail-on-empty-changeset >/dev/null

configSet="$(aws cloudformation describe-stacks --stack-name "${MAIL_STACK}" \
  --query "Stacks[0].Outputs[?OutputKey=='MailConfigurationSetName'].OutputValue" --output text)"
info "configuration set ${configSet}"
info "mail events topic digital-health-${ENVIRONMENT}-mail-events"

step "Done"
warn "AWS sends confirmation links to ${ALARM_EMAIL} for two SNS subscriptions."
warn "Mail is not monitored until both links are clicked."
info "Reply to the SES support case once the subscriptions show Confirmed in the SNS console."
