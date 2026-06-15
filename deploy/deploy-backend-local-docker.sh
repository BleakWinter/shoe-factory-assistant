#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BACKEND_DIR="$REPO_ROOT/backend"

CONTAINER_NAME="${CONTAINER_NAME:-shoe-factory-backend}"
IMAGE="${IMAGE:-eclipse-temurin:17-jre-jammy}"
HOST_PORT="${HOST_PORT:-8080}"

LOCAL_PROJECT_DIR="${LOCAL_PROJECT_DIR:-/Volumes/T9/Develop/shoe-factory-assistant}"
LOCAL_APP_DIR="${LOCAL_APP_DIR:-$LOCAL_PROJECT_DIR/backend}"
LOCAL_FILE_DIR="${LOCAL_FILE_DIR:-$LOCAL_PROJECT_DIR/files}"
CONTAINER_FILE_DIR="${CONTAINER_FILE_DIR:-/data/shoe-factory-assistant/files}"

DB_HOST="${DB_HOST:-host.docker.internal}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-shoe_factory_assistant_prod}"
DB_USERNAME="${DB_USERNAME:-root}"
DB_PASSWORD="${DB_PASSWORD:-123456}"
DATASOURCE_URL="${DATASOURCE_URL:-jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true}"

JAR_NAME="shoe-factory-backend-0.1.0-SNAPSHOT.jar"
SOURCE_JAR="$BACKEND_DIR/target/$JAR_NAME"
TARGET_JAR="$LOCAL_APP_DIR/app.jar"

echo "Building prod jar..."
cd "$BACKEND_DIR"
mvn -Pprod -Dmaven.test.skip=true package

if [[ ! -f "$SOURCE_JAR" ]]; then
  echo "Jar not found: $SOURCE_JAR" >&2
  exit 1
fi

echo "Preparing local docker directories..."
mkdir -p "$LOCAL_APP_DIR/logs" "$LOCAL_FILE_DIR"
cp "$SOURCE_JAR" "$TARGET_JAR"

if docker ps -a --format '{{.Names}}' | grep -qx "$CONTAINER_NAME"; then
  echo "Restarting existing container: $CONTAINER_NAME"
  docker restart "$CONTAINER_NAME" >/dev/null
else
  echo "Creating container: $CONTAINER_NAME"
  docker run -d \
    --name "$CONTAINER_NAME" \
    --restart unless-stopped \
    -p "$HOST_PORT:8080" \
    -e TZ=Asia/Shanghai \
    -e APP_FILE_STORAGE_ROOT_PATH="$CONTAINER_FILE_DIR" \
    -e SPRING_DATASOURCE_URL="$DATASOURCE_URL" \
    -e SPRING_DATASOURCE_USERNAME="$DB_USERNAME" \
    -e SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD" \
    -v "$TARGET_JAR:/app/app.jar:ro" \
    -v "$LOCAL_APP_DIR/logs:/app/logs" \
    -v "$LOCAL_FILE_DIR:$CONTAINER_FILE_DIR" \
    "$IMAGE" \
    java -jar /app/app.jar --spring.profiles.active=prod
fi

echo "Backend container is running: http://localhost:$HOST_PORT"
echo "Logs: docker logs -f $CONTAINER_NAME"
