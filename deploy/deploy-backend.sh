#!/usr/bin/env bash
set -euo pipefail

HOST_NAME="${HOST_NAME:-192.168.10.62}"
USER_NAME="${USER_NAME:-root}"
KEY_PATH="${KEY_PATH:-$HOME/.ssh/id_rsa}"
REMOTE_APP_DIR="${REMOTE_APP_DIR:-/data/backend/shoe-factory-assistant}"
CONTAINER_NAME="${CONTAINER_NAME:-shoe-factory-backend}"
IMAGE="${IMAGE:-eclipse-temurin:17-jre-jammy}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BACKEND_DIR="$REPO_ROOT/backend"
JAR_PATH="$BACKEND_DIR/target/shoe-factory-backend-0.1.0-SNAPSHOT.jar"
REMOTE="${USER_NAME}@${HOST_NAME}"

echo "Building backend jar..."
cd "$BACKEND_DIR"
mvn.cmd -q -Pprod -Dmaven.test.skip=true package

if [[ ! -f "$JAR_PATH" ]]; then
  echo "Jar not found: $JAR_PATH" >&2
  exit 1
fi

echo "Uploading backend to $REMOTE..."
ssh -i "$KEY_PATH" "$REMOTE" "mkdir -p '$REMOTE_APP_DIR' '$REMOTE_APP_DIR/logs' /data/shoe-factory-assistant/files"
scp -i "$KEY_PATH" "$JAR_PATH" "$REMOTE:$REMOTE_APP_DIR/app.jar"

echo "Starting backend container..."
ssh -i "$KEY_PATH" "$REMOTE" "
docker pull '$IMAGE' &&
if docker ps -a --format '{{.Names}}' | grep -qx '$CONTAINER_NAME'; then
  docker restart '$CONTAINER_NAME'
else
  docker run -d \
    --name '$CONTAINER_NAME' \
    --restart unless-stopped \
    --network host \
    -e TZ=Asia/Shanghai \
    -e APP_FILE_STORAGE_ROOT_PATH=/data/shoe-factory-assistant/files \
    -v '$REMOTE_APP_DIR/app.jar:/app/app.jar:ro' \
    -v '/data/shoe-factory-assistant/files:/data/shoe-factory-assistant/files' \
    -v '$REMOTE_APP_DIR/logs:/app/logs' \
    '$IMAGE' \
    sh -lc 'java -jar /app/app.jar --spring.profiles.active=prod >> /app/logs/backend.log 2>> /app/logs/backend-error.log'
fi
"

echo "Backend deployed: http://$HOST_NAME:8080/"
echo "Logs: docker logs -f $CONTAINER_NAME"
