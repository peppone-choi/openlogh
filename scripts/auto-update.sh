#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   ./scripts/auto-update.sh                # run once
#   ./scripts/auto-update.sh --loop 300     # poll every 300s (5 min)
#   ./scripts/auto-update.sh --loop         # poll every 300s (default)
#
# Cron example (every 5 min):
#   */5 * * * * /opt/opensamguk/scripts/auto-update.sh >> /var/log/opensam-update.log 2>&1
#
# Requirements:
#   - docker compose v2+
#   - GHCR credentials configured (ghcr.io login)
#   - Run from the repo root (where docker-compose.yml lives)

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"
INTERVAL="${2:-300}"

GAME_IMAGE="ghcr.io/peppone-choi/opensam-game:${TAG:-latest}"
GATEWAY_IMAGE="ghcr.io/peppone-choi/opensam-gateway:${TAG:-latest}"
FRONTEND_IMAGE="ghcr.io/peppone-choi/opensam-frontend:${TAG:-latest}"

log() {
  echo "$(date '+%Y-%m-%d %H:%M:%S.%3N') | $*"
}

get_image_id() {
  docker image inspect "$1" --format '{{.Id}}' 2>/dev/null || echo "none"
}

docker_compose() {
  docker compose -f "$COMPOSE_FILE" "$@"
}

check_docker() {
  if ! docker info >/dev/null 2>&1; then
    log "[auto-update] ERROR: docker not reachable. Attempting context fix..."
    docker context use default 2>/dev/null || true
    if ! docker info >/dev/null 2>&1; then
      log "[auto-update] ERROR: docker still not reachable after context fix. Aborting."
      return 1
    fi
    log "[auto-update] Docker context fixed to 'default'."
  fi
}

run_update() {
  log "[auto-update] Checking for image updates..."

  if ! check_docker; then
    return 1
  fi

  local game_before gateway_before frontend_before
  game_before=$(get_image_id "$GAME_IMAGE")
  gateway_before=$(get_image_id "$GATEWAY_IMAGE")
  frontend_before=$(get_image_id "$FRONTEND_IMAGE")

  docker pull "$GAME_IMAGE" --quiet >/dev/null 2>&1 || true
  docker pull "$GATEWAY_IMAGE" --quiet >/dev/null 2>&1 || true
  docker pull "$FRONTEND_IMAGE" --quiet >/dev/null 2>&1 || true

  local game_after gateway_after frontend_after
  game_after=$(get_image_id "$GAME_IMAGE")
  gateway_after=$(get_image_id "$GATEWAY_IMAGE")
  frontend_after=$(get_image_id "$FRONTEND_IMAGE")

  local changed=false

  if [ "$game_before" != "$game_after" ]; then
    changed=true
    log "[auto-update] Game image changed — running bootstrap migration..."
    docker_compose run --rm bootstrap || {
      log "[auto-update] ERROR: bootstrap migration failed."
      return 1
    }
    log "[auto-update] Bootstrap migration complete."
  fi

  if [ "$gateway_before" != "$gateway_after" ]; then
    changed=true
    log "[auto-update] Gateway image changed — restarting..."
    docker_compose up -d --no-deps gateway
    log "[auto-update] Gateway restarted."
  fi

  if [ "$frontend_before" != "$frontend_after" ]; then
    changed=true
    log "[auto-update] Frontend image changed — restarting..."
    docker_compose up -d --no-deps frontend
    log "[auto-update] Frontend restarted."
  fi

  if [ "$changed" = true ]; then
    docker image prune -f --filter "until=24h" >/dev/null 2>&1 || true
    log "[auto-update] Update complete."
  else
    log "[auto-update] No changes detected."
  fi
}

if [ "${1:-}" = "--loop" ]; then
  log "[auto-update] Starting poll loop (interval: ${INTERVAL}s)"
  while true; do
    run_update || true
    sleep "$INTERVAL"
  done
else
  run_update
fi
