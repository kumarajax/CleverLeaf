#!/usr/bin/env sh
set -eu

backup_dir="${1:-./backups}"
timestamp="$(date +%Y%m%d-%H%M%S)"
mkdir -p "$backup_dir"

docker compose exec -T postgres pg_dump \
  -U "${POSTGRES_USER:-clearleaf}" \
  -d "${POSTGRES_DB:-clearleaf}" \
  > "$backup_dir/postgres-$timestamp.sql"

echo "Created $backup_dir/postgres-$timestamp.sql"
