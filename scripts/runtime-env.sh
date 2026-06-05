#!/usr/bin/env bash
set -euo pipefail

detect_host_ip() {
  if [ -n "${CLEVERLEAF_HOST_IP:-}" ]; then
    printf '%s\n' "$CLEVERLEAF_HOST_IP"
    return
  fi

  local ip=""
  if command -v ipconfig >/dev/null 2>&1; then
    ip="$(ipconfig getifaddr en0 2>/dev/null || true)"
    if [ -z "$ip" ]; then
      ip="$(ipconfig getifaddr en1 2>/dev/null || true)"
    fi
  fi

  if [ -z "$ip" ] && command -v hostname >/dev/null 2>&1; then
    ip="$(hostname -I 2>/dev/null | awk '{print $1}' || true)"
  fi

  if [ -z "$ip" ] && command -v ifconfig >/dev/null 2>&1; then
    ip="$(ifconfig 2>/dev/null | awk '
      $1 ~ /^en[0-9]+:/ { iface=$1; sub(":", "", iface); active[iface]=0 }
      iface != "" && $1 == "status:" && $2 == "active" { active[iface]=1 }
      iface != "" && $1 == "inet" && $2 !~ /^127\./ {
        candidate[iface]=$2
      }
      END {
        for (name in candidate) {
          split(candidate[name], parts, ".")
          if (active[name] && parts[1] == "192" && parts[2] == "168") {
            print candidate[name]
            exit
          }
        }
        for (name in candidate) {
          split(candidate[name], parts, ".")
          if (active[name] && parts[1] == "10") {
            print candidate[name]
            exit
          }
        }
        for (name in candidate) {
          split(candidate[name], parts, ".")
          if (active[name] && parts[1] == "172" && parts[2] >= 16 && parts[2] <= 31) {
            print candidate[name]
            exit
          }
        }
      }
    ' || true)"
  fi

  if [ -z "$ip" ]; then
    ip="127.0.0.1"
  fi

  printf '%s\n' "$ip"
}

runtime_urls() {
  CLEVERLEAF_HOST_IP_DETECTED="$(detect_host_ip)"
  FRONTEND_PORT="${FRONTEND_PORT:-3000}"
  BACKEND_PORT="${BACKEND_PORT:-8081}"
  KEYCLOAK_PORT="${KEYCLOAK_PORT:-8080}"
  KEYCLOAK_REALM="${KEYCLOAK_REALM:-clearleaf}"
  KEYCLOAK_CLIENT_ID="${KEYCLOAK_CLIENT_ID:-clearleaf-web}"
  KEYCLOAK_INTERNAL_URL="${KEYCLOAK_INTERNAL_URL:-http://localhost:${KEYCLOAK_PORT}}"

  FRONTEND_LOCAL_ORIGIN="http://localhost:${FRONTEND_PORT}"
  FRONTEND_LOOPBACK_ORIGIN="http://127.0.0.1:${FRONTEND_PORT}"
  FRONTEND_LAN_ORIGIN="http://${CLEVERLEAF_HOST_IP_DETECTED}:${FRONTEND_PORT}"
  BACKEND_LOCAL_ORIGIN="http://localhost:${BACKEND_PORT}"
  BACKEND_LOOPBACK_ORIGIN="http://127.0.0.1:${BACKEND_PORT}"
  BACKEND_LAN_ORIGIN="http://${CLEVERLEAF_HOST_IP_DETECTED}:${BACKEND_PORT}"
  KEYCLOAK_LOCAL_ORIGIN="http://localhost:${KEYCLOAK_PORT}"
  KEYCLOAK_LOOPBACK_ORIGIN="http://127.0.0.1:${KEYCLOAK_PORT}"
  KEYCLOAK_LAN_ORIGIN="http://${CLEVERLEAF_HOST_IP_DETECTED}:${KEYCLOAK_PORT}"

  export CLEVERLEAF_HOST_IP_DETECTED
  export FRONTEND_PORT BACKEND_PORT KEYCLOAK_PORT KEYCLOAK_REALM KEYCLOAK_CLIENT_ID KEYCLOAK_INTERNAL_URL
  export FRONTEND_LOCAL_ORIGIN FRONTEND_LOOPBACK_ORIGIN FRONTEND_LAN_ORIGIN
  export BACKEND_LOCAL_ORIGIN BACKEND_LOOPBACK_ORIGIN BACKEND_LAN_ORIGIN
  export KEYCLOAK_LOCAL_ORIGIN KEYCLOAK_LOOPBACK_ORIGIN KEYCLOAK_LAN_ORIGIN
}
