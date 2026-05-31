#!/usr/bin/env sh
set -eu

if [ "$#" -ne 1 ]; then
  echo "Usage: $0 path/to/postgres-backup.sql" >&2
  exit 1
fi

docker compose exec -T postgres psql \
  -U "${POSTGRES_USER:-clearleaf}" \
  -d "${POSTGRES_DB:-clearleaf}" \
  < "$1"

echo "Restored $1"
