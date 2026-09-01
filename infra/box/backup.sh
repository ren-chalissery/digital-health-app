#!/usr/bin/env bash
#
# Nightly database dump to S3.
#
# This is what makes the instance disposable, and it is the only thing standing between a
# terminated instance and total data loss. The root volume is deleted with the instance, and there
# is no standby and no point-in-time recovery, so the recovery point is however long ago this last
# ran. Restore with box/restore.sh.

set -euo pipefail

cd /opt/box
# shellcheck disable=SC1091  # Written by the instance at first boot, not present in the repository.
set -a; . /opt/box/.env; set +a

: "${BACKUP_BUCKET:?BACKUP_BUCKET is not set in /opt/box/.env}"

readonly RETAIN_DAYS=14
STAMP="$(date -u +%Y-%m-%dT%H-%M-%SZ)"
readonly STAMP
readonly NAME="digitalhealth-${STAMP}.sql.gz"
readonly LOCAL="/tmp/${NAME}"

cleanup() { rm -f "$LOCAL"; }
trap cleanup EXIT

# --clean --if-exists so the dump can be replayed over a database that already has a schema, which
# is the normal case when restoring onto a box that has already booted and run its migrations.
docker compose exec -T -e "PGPASSWORD=${DB_PASSWORD}" postgres \
  pg_dump --username digitalhealth --dbname digitalhealth --clean --if-exists \
  | gzip -9 > "$LOCAL"

# A dump that fails halfway still exits zero through the pipe under some shells, and gzip happily
# writes a valid empty archive. Anything under a kilobyte is not a real database.
size="$(stat -c %s "$LOCAL")"
if [ "$size" -lt 1024 ]; then
  echo "Dump is only ${size} bytes, refusing to upload it over a good backup" >&2
  exit 1
fi

aws s3 cp "$LOCAL" "s3://${BACKUP_BUCKET}/backups/${NAME}" \
  --storage-class STANDARD_IA \
  --only-show-errors

echo "Uploaded ${NAME} (${size} bytes)"

# Prune by age. Deliberately after the upload, so a failed backup never removes an older good one.
cutoff="$(date -u -d "${RETAIN_DAYS} days ago" +%Y-%m-%d)"
aws s3 ls "s3://${BACKUP_BUCKET}/backups/" \
  | awk -v cutoff="$cutoff" '$1 < cutoff { print $4 }' \
  | while read -r old; do
      [ -n "$old" ] || continue
      aws s3 rm "s3://${BACKUP_BUCKET}/backups/${old}" --only-show-errors
      echo "Pruned ${old}"
    done
