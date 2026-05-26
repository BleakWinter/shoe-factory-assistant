#!/usr/bin/env bash
set -euo pipefail

HOST_NAME="${HOST_NAME:-192.168.10.62}"
USER_NAME="${USER_NAME:-root}"
KEY_PATH="${KEY_PATH:-$HOME/.ssh/id_rsa}"
REMOTE_DIR="${REMOTE_DIR:-/data/web/shoe-factory-assistant}"
NGINX_CONTAINER="${NGINX_CONTAINER:-nginx}"
REMOTE_STAGE="${REMOTE_STAGE:-/tmp/shoe-factory-assistant-web}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
WEB_DIR="$REPO_ROOT/web"
DIST_DIR="$WEB_DIR/dist"
REMOTE="${USER_NAME}@${HOST_NAME}"

echo "Building frontend..."
cd "$WEB_DIR"
npm.cmd run build

if [[ ! -d "$DIST_DIR" ]]; then
  echo "Dist directory not found: $DIST_DIR" >&2
  exit 1
fi

echo "Uploading to $REMOTE..."
ssh -i "$KEY_PATH" "$REMOTE" "rm -rf '$REMOTE_STAGE' && mkdir -p '$REMOTE_STAGE' '$REMOTE_DIR'"
scp -i "$KEY_PATH" -r "$DIST_DIR" "$REMOTE:$REMOTE_STAGE/"

echo "Deploying to $REMOTE_DIR..."
ssh -i "$KEY_PATH" "$REMOTE" "
find '$REMOTE_DIR' -mindepth 1 -maxdepth 1 -exec rm -rf {} + &&
cp -a '$REMOTE_STAGE'/dist/. '$REMOTE_DIR'/ &&
chmod -R 755 '$REMOTE_DIR' &&
rm -rf '$REMOTE_STAGE' &&
if command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' | grep -qx '$NGINX_CONTAINER'; then
  docker restart '$NGINX_CONTAINER'
elif command -v nginx >/dev/null 2>&1; then
  nginx -s reload || systemctl reload nginx
fi
"

echo "Frontend deployed: http://$HOST_NAME/"
