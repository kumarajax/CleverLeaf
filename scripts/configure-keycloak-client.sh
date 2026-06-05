#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

if [ -f "$ROOT_DIR/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  . "$ROOT_DIR/.env"
  set +a
fi

# shellcheck disable=SC1091
. "$ROOT_DIR/scripts/runtime-env.sh"
runtime_urls

KEYCLOAK_ADMIN_USER="${KEYCLOAK_ADMIN_USER:-admin}"
KEYCLOAK_ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-local-dev-change-me}"
CONTAINER="${KEYCLOAK_CONTAINER:-}"

if [ -z "$CONTAINER" ]; then
  CONTAINER="$(docker compose ps -q keycloak 2>/dev/null || true)"
fi

if [ -z "$CONTAINER" ]; then
  CONTAINER="cleverleaf-keycloak-1"
fi

upsert_env() {
  local key="$1"
  local value="$2"
  local env_file="$ROOT_DIR/.env"
  local tmp_file

  touch "$env_file"
  tmp_file="$(mktemp)"
  if grep -q "^${key}=" "$env_file"; then
    awk -v key="$key" -v value="$value" 'BEGIN { prefix=key "=" } index($0, prefix) == 1 { print key "=" value; next } { print }' "$env_file" > "$tmp_file"
  else
    cp "$env_file" "$tmp_file"
    printf '%s=%s\n' "$key" "$value" >> "$tmp_file"
  fi
  mv "$tmp_file" "$env_file"
}

APP_CORS_ALLOWED_ORIGINS="${FRONTEND_LOCAL_ORIGIN},${FRONTEND_LOOPBACK_ORIGIN},${FRONTEND_LAN_ORIGIN}"

upsert_env "APP_PUBLIC_BASE_URL" "$FRONTEND_LAN_ORIGIN"
upsert_env "APP_CORS_ALLOWED_ORIGINS" "$APP_CORS_ALLOWED_ORIGINS"
upsert_env "NEXT_PUBLIC_API_BASE_URL" "$BACKEND_LAN_ORIGIN"
upsert_env "NEXT_PUBLIC_KEYCLOAK_URL" "$KEYCLOAK_LAN_ORIGIN"

redirect_uris_json=$(printf '["%s","%s/*","%s","%s/*","%s","%s/*"]' \
  "$FRONTEND_LOCAL_ORIGIN" "$FRONTEND_LOCAL_ORIGIN" \
  "$FRONTEND_LOOPBACK_ORIGIN" "$FRONTEND_LOOPBACK_ORIGIN" \
  "$FRONTEND_LAN_ORIGIN" "$FRONTEND_LAN_ORIGIN")
web_origins_json=$(printf '["%s","%s","%s"]' \
  "$FRONTEND_LOCAL_ORIGIN" "$FRONTEND_LOOPBACK_ORIGIN" "$FRONTEND_LAN_ORIGIN")
logout_uris="${FRONTEND_LOCAL_ORIGIN}/*##${FRONTEND_LOOPBACK_ORIGIN}/*##${FRONTEND_LAN_ORIGIN}/*"

echo "Detected CleverLeaf host IP: $CLEVERLEAF_HOST_IP_DETECTED"
echo "Configuring Keycloak client $KEYCLOAK_CLIENT_ID for $FRONTEND_LAN_ORIGIN ..."

for attempt in $(seq 1 30); do
  if docker exec "$CONTAINER" /opt/keycloak/bin/kcadm.sh config credentials \
    --server "$KEYCLOAK_INTERNAL_URL" \
    --realm master \
    --user "$KEYCLOAK_ADMIN_USER" \
    --password "$KEYCLOAK_ADMIN_PASSWORD" >/dev/null 2>&1; then
    break
  fi

  if [ "$attempt" -eq 30 ]; then
    echo "Keycloak did not become ready in time. Is docker compose running?" >&2
    exit 1
  fi

  sleep 2
done

docker exec "$CONTAINER" /bin/sh -c "
  set -e
  /opt/keycloak/bin/kcadm.sh update realms/'$KEYCLOAK_REALM' \
    -s 'sslRequired=none' \
    -s 'registrationAllowed=false'
  CLIENT_UUID=\$(/opt/keycloak/bin/kcadm.sh get clients -r '$KEYCLOAK_REALM' -q clientId='$KEYCLOAK_CLIENT_ID' --fields id --format csv --noquotes | tail -n 1)
  if [ -z \"\$CLIENT_UUID\" ]; then
    echo 'Client $KEYCLOAK_CLIENT_ID not found in realm $KEYCLOAK_REALM' >&2
    exit 1
  fi
  /opt/keycloak/bin/kcadm.sh update clients/\$CLIENT_UUID -r '$KEYCLOAK_REALM' \
    -s 'redirectUris=$redirect_uris_json' \
    -s 'webOrigins=$web_origins_json' \
    -s 'attributes.\"pkce.code.challenge.method\"=S256' \
    -s 'attributes.\"post.logout.redirect.uris\"=$logout_uris'
"

echo "Updated .env:"
echo "  APP_PUBLIC_BASE_URL=$FRONTEND_LAN_ORIGIN"
echo "  APP_CORS_ALLOWED_ORIGINS=$APP_CORS_ALLOWED_ORIGINS"
echo "  NEXT_PUBLIC_API_BASE_URL=$BACKEND_LAN_ORIGIN"
echo "  NEXT_PUBLIC_KEYCLOAK_URL=$KEYCLOAK_LAN_ORIGIN"
echo
echo "Keycloak client ready for:"
echo "  $FRONTEND_LOCAL_ORIGIN"
echo "  $FRONTEND_LOOPBACK_ORIGIN"
echo "  $FRONTEND_LAN_ORIGIN"
echo
echo "Rebuild/restart Docker so the backend CORS and frontend browser bundle pick up .env:"
echo "  docker compose up --build"
