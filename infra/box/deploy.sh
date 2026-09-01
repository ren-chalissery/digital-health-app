#!/usr/bin/env bash
#
# Brings the containers up on the requested image tag.
#
# The single entry point for deployment on this topology. The instance calls it at first boot,
# bootstrap-box.sh calls it over Session Manager once the first image exists, and the pipeline
# calls it the same way on every merge:
#
#   aws ssm send-command --document-name AWS-RunShellScript \
#     --parameters 'commands=["/opt/box/deploy.sh <sha>"]'

set -euo pipefail

cd /opt/box

# shellcheck disable=SC1091  # Written by the instance at first boot, not present in the repository.
set -a; . ./.env; set +a

tag="${1:-}"
if [ -z "$tag" ]; then
  # No argument means this is a boot rather than a deploy. A merge that lands while the box is
  # stopped records its tag here instead of failing, so this is where the instance catches up.
  tag="$(aws ssm get-parameter --region "$AWS_REGION" \
    --name "/digital-health/${ENVIRONMENT:-prod}/box/image-tag" \
    --query Parameter.Value --output text 2>/dev/null || true)"
  [ "$tag" = "None" ] && tag=''
fi

if [ -n "$tag" ] && [ "$tag" != "${IMAGE_TAG:-}" ]; then
  # Persisted rather than passed through, so an instance rebooting on its own comes back on the
  # tag it was last deployed with instead of whatever the stack was created with.
  sed -i "s|^IMAGE_TAG=.*|IMAGE_TAG=${tag}|" .env
  IMAGE_TAG="$tag"
fi

[ -n "${IMAGE_TAG:-}" ] || { echo "No image tag to deploy yet." >&2; exit 1; }

aws ecr get-login-password --region "$AWS_REGION" \
  | docker login --username AWS --password-stdin "${REGISTRY%%/*}"

# Idempotent: a partial first-boot deploy and a Run Command deploy can both reach this point.
docker compose down --remove-orphans 2>/dev/null || true
docker compose pull
docker compose up -d --remove-orphans

# Ten images at 400MB would fill a 30GB volume on their own.
docker image prune --force

echo "Waiting for the application to report ready"
for _ in $(seq 1 60); do
  if curl --fail --silent --max-time 2 http://localhost:8080/actuator/health/readiness >/dev/null 2>&1; then
    echo "Ready on ${IMAGE_TAG}"
    exit 0
  fi
  sleep 5
done

echo "Did not become ready within five minutes. Recent logs:" >&2
docker compose logs --tail 50 app >&2
exit 1
