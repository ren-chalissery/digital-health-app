#!/usr/bin/env bash
#
# Restores a dump written by backup.sh.
#
# Run on the box, through Session Manager:
#
#   sudo /opt/box/restore.sh --latest
#   sudo /opt/box/restore.sh digitalhealth-2026-09-01T14-30-00Z.sql.gz
#
# The application is stopped for the duration. Restoring into a live application means Flyway and
# the restore writing the same tables at the same time.

set -euo pipefail

cd /opt/box
# shellcheck disable=SC1091  # Written by the instance at first boot, not present in the repository.
set -a; . /opt/box/.env; set +a

: "${BACKUP_BUCKET:?BACKUP_BUCKET is not set in /opt/box/.env}"

usage() { echo "Usage: restore.sh [--latest | <dump-name>]" >&2; exit 1; }
[ $# -eq 1 ] || usage

if [ "$1" = "--latest" ]; then
  name="$(aws s3 ls "s3://${BACKUP_BUCKET}/backups/" | sort | tail -1 | awk '{print $4}')"
  [ -n "$name" ] || { echo "No backups found in s3://${BACKUP_BUCKET}/backups/" >&2; exit 1; }
else
  name="$1"
fi

local_file="/tmp/${name}"
cleanup() { rm -f "$local_file"; }
trap cleanup EXIT

echo "Restoring ${name}"
aws s3 cp "s3://${BACKUP_BUCKET}/backups/${name}" "$local_file" --only-show-errors

printf 'This overwrites the current database. Type the dump name to continue: '
read -r confirm
[ "$confirm" = "$name" ] || { echo "Aborted." >&2; exit 1; }

docker compose stop app
# The dump carries --clean --if-exists, so it drops what it is about to recreate and a restore over
# a populated database does not collide.
gunzip -c "$local_file" \
  | docker compose exec -T -e "PGPASSWORD=${DB_PASSWORD}" postgres \
      psql --username digitalhealth --dbname digitalhealth --quiet
docker compose start app

echo "Restored. The application is starting; Flyway will report the schema already current."
