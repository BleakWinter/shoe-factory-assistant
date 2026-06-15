#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
WEB_DIR="$REPO_ROOT/web"

CONTAINER_NAME="${CONTAINER_NAME:-shoe-factory-frontend}"
IMAGE="${IMAGE:-nginx:1.27-alpine}"
HOST_PORT="${HOST_PORT:-80}"
PACKAGE_MANAGER="${PACKAGE_MANAGER:-}"

LOCAL_PROJECT_DIR="${LOCAL_PROJECT_DIR:-/Volumes/T9/Develop/shoe-factory-assistant}"
LOCAL_FRONTEND_DIR="${LOCAL_FRONTEND_DIR:-$LOCAL_PROJECT_DIR/frontend}"
LOCAL_HTML_DIR="$LOCAL_FRONTEND_DIR/html"
LOCAL_CONF_DIR="$LOCAL_FRONTEND_DIR/conf"
LOCAL_LOG_DIR="$LOCAL_FRONTEND_DIR/logs"

NGINX_CONF_TARGET="$LOCAL_CONF_DIR/default.conf"

echo "Building frontend..."
cd "$WEB_DIR"
if [[ -n "$PACKAGE_MANAGER" ]]; then
  "$PACKAGE_MANAGER" run build
elif command -v pnpm >/dev/null 2>&1; then
  pnpm run build
elif command -v corepack >/dev/null 2>&1; then
  corepack pnpm run build
else
  echo "pnpm not found. Please install pnpm or enable corepack first." >&2
  echo "Try: corepack enable && corepack prepare pnpm@latest --activate" >&2
  exit 1
fi

if [[ ! -d "$WEB_DIR/dist" ]]; then
  echo "Frontend dist not found: $WEB_DIR/dist" >&2
  exit 1
fi

echo "Preparing local nginx directories..."
mkdir -p "$LOCAL_CONF_DIR" "$LOCAL_LOG_DIR"
rm -rf "$LOCAL_HTML_DIR"
mkdir -p "$LOCAL_HTML_DIR"
cp -R "$WEB_DIR/dist/." "$LOCAL_HTML_DIR/"

if [[ ! -f "$NGINX_CONF_TARGET" ]]; then
  echo "Nginx config not found: $NGINX_CONF_TARGET" >&2
  echo "Please create it before running this script." >&2
  exit 1
fi

if docker ps -a --format '{{.Names}}' | grep -qx "$CONTAINER_NAME"; then
  echo "Restarting existing container: $CONTAINER_NAME"
  docker restart "$CONTAINER_NAME" >/dev/null
else
  echo "Creating container: $CONTAINER_NAME"
  docker run -d \
    --name "$CONTAINER_NAME" \
    --restart unless-stopped \
    -p "$HOST_PORT:80" \
    -v "$LOCAL_HTML_DIR:/usr/share/nginx/html:ro" \
    -v "$NGINX_CONF_TARGET:/etc/nginx/conf.d/default.conf:ro" \
    -v "$LOCAL_LOG_DIR:/var/log/nginx" \
    "$IMAGE"
fi

echo "Frontend container is running: http://localhost:$HOST_PORT"
echo "Logs: docker logs -f $CONTAINER_NAME"
