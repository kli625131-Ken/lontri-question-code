#!/usr/bin/env bash
set -Eeuo pipefail

usage() {
  cat <<'USAGE'
Usage:
  bash scripts/server-oneclick-deploy.sh prod /home/lontri/questioncode-deploy-YYYYMMDD_HHMMSS.zip
  bash scripts/server-oneclick-deploy.sh test /home/lontri/questioncode-deploy-YYYYMMDD_HHMMSS.zip

Optional environment variables:
  MYSQL_ROOT_PASSWORD     required
  MYSQL_PASSWORD          required
  JWT_SECRET              required
  IMPORT_DB              auto | always | never, default: auto
  PRESERVE_UPLOADS        true | false, default: true

Result:
  prod -> /opt/questioncode-prod, http://<server-ip>/
  test -> /opt/questioncode-test, http://<server-ip>:8081/
USAGE
}

ENV_NAME="${1:-}"
PACKAGE_PATH="${2:-}"

if [[ -z "$ENV_NAME" || -z "$PACKAGE_PATH" ]]; then
  usage
  exit 1
fi

if [[ "$ENV_NAME" != "prod" && "$ENV_NAME" != "test" ]]; then
  echo "ERROR: env must be prod or test"
  exit 1
fi

if [[ ! -f "$PACKAGE_PATH" ]]; then
  echo "ERROR: package not found: $PACKAGE_PATH"
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: docker is not installed"
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "ERROR: docker compose v2 is not available"
  exit 1
fi

MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:?Set MYSQL_ROOT_PASSWORD}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:?Set MYSQL_PASSWORD}"
JWT_SECRET="${JWT_SECRET:?Set JWT_SECRET}"
IMPORT_DB="${IMPORT_DB:-auto}"
PRESERVE_UPLOADS="${PRESERVE_UPLOADS:-true}"
TIMESTAMP="$(date +%F_%H%M%S)"
STAGING="/tmp/questioncode-deploy-$TIMESTAMP"
DEPLOY_USER="${SUDO_USER:-$(id -un)}"
DEPLOY_GROUP="$(id -gn "$DEPLOY_USER" 2>/dev/null || id -gn)"
SUDO=""
if [[ "$(id -u)" -ne 0 ]]; then
  SUDO="sudo"
fi

if [[ "$ENV_NAME" == "prod" ]]; then
  TARGET="/opt/questioncode-prod"
  APP_NAME="questioncode-prod"
  COMPOSE_PROJECT_NAME="questioncode_prod"
  MYSQL_PORT="127.0.0.1:3307"
  BACKEND_PORT="127.0.0.1:8082"
  FRONTEND_PORT="80"
else
  TARGET="/opt/questioncode-test"
  APP_NAME="questioncode-test"
  COMPOSE_PROJECT_NAME="questioncode_test"
  MYSQL_PORT="127.0.0.1:13307"
  BACKEND_PORT="127.0.0.1:18082"
  FRONTEND_PORT="8081"
fi

BACKUP_DIR="$TARGET/backup"
OLD_TARGET="/opt/$(basename "$TARGET")-old-$TIMESTAMP"
FIRST_INSTALL="false"

write_env_file() {
  local env_file="$1"
  cat > "$env_file" <<EOF
APP_NAME=$APP_NAME
COMPOSE_PROJECT_NAME=$COMPOSE_PROJECT_NAME

MYSQL_ROOT_PASSWORD=$MYSQL_ROOT_PASSWORD
MYSQL_DATABASE=problem_db
MYSQL_USER=lontri
MYSQL_PASSWORD=$MYSQL_PASSWORD
MYSQL_PORT=$MYSQL_PORT

BACKEND_PORT=$BACKEND_PORT
FRONTEND_PORT=$FRONTEND_PORT
TZ=Asia/Shanghai

JWT_SECRET=$JWT_SECRET

OPS_IMPORT_BOOTSTRAP_ENABLED=false
OPS_IMPORT_RESET_ON_BOOTSTRAP=false
OPS_IMPORT_DEFAULT_REMIND_AFTER_DAYS=7
OPS_UPLOAD_ROOT_PATH=/uploads
OPS_UPLOAD_MAX_FILE_SIZE_BYTES=10485760
EOF
}

wait_for_mysql() {
  local seconds=0
  until docker compose exec -T mysql sh -c 'mysqladmin ping -h localhost -uroot -p"$MYSQL_ROOT_PASSWORD" --silent' >/dev/null 2>&1; do
    seconds=$((seconds + 3))
    if [[ "$seconds" -gt 120 ]]; then
      echo "ERROR: mysql did not become ready in 120 seconds"
      docker compose ps
      exit 1
    fi
    sleep 3
  done
}

wait_for_backend() {
  local url="$1"
  local seconds=0
  until curl -fsS "$url" >/dev/null 2>&1; do
    seconds=$((seconds + 3))
    if [[ "$seconds" -gt 180 ]]; then
      echo "ERROR: backend health check failed: $url"
      docker compose ps
      docker compose logs --tail=120 backend || true
      exit 1
    fi
    sleep 3
  done
}

backup_current_environment() {
  if [[ ! -d "$TARGET" || ! -f "$TARGET/docker-compose.yml" ]]; then
    FIRST_INSTALL="true"
    return
  fi

  mkdir -p "$BACKUP_DIR"
  cd "$TARGET"

  echo "Backing up current database and uploads for $ENV_NAME..."
  if docker compose ps mysql >/dev/null 2>&1; then
    docker compose exec -T mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 problem_db' > "$BACKUP_DIR/problem_db_before_deploy_$TIMESTAMP.sql" || true
  fi

  if [[ -d "$TARGET/uploads" ]]; then
    tar -czf "$BACKUP_DIR/uploads_before_deploy_$TIMESTAMP.tar.gz" -C "$TARGET" uploads || true
  fi
}

extract_package() {
  rm -rf "$STAGING"
  mkdir -p "$STAGING"
  unzip -q "$PACKAGE_PATH" -d "$STAGING"

  if [[ -f "$STAGING/questioncode/docker-compose.yml" ]]; then
    SOURCE="$STAGING/questioncode"
  elif [[ -f "$STAGING/docker-compose.yml" ]]; then
    SOURCE="$STAGING"
  else
    echo "ERROR: docker-compose.yml not found in package"
    exit 1
  fi
}

install_new_release() {
  if [[ -d "$TARGET" && -f "$TARGET/docker-compose.yml" ]]; then
    cd "$TARGET"
    docker compose down
  fi

  if [[ -d "$TARGET" ]]; then
    $SUDO mv "$TARGET" "$OLD_TARGET"
  fi

  $SUDO mkdir -p "$(dirname "$TARGET")"
  $SUDO mv "$SOURCE" "$TARGET"
  $SUDO chown -R "$DEPLOY_USER:$DEPLOY_GROUP" "$TARGET"
  chmod -R u+rwX "$TARGET"

  if [[ -f "$OLD_TARGET/.env" ]]; then
    cp "$OLD_TARGET/.env" "$TARGET/.env"
  else
    write_env_file "$TARGET/.env"
  fi

  if [[ "$PRESERVE_UPLOADS" == "true" && -d "$OLD_TARGET/uploads" ]]; then
    rm -rf "$TARGET/uploads"
    cp -a "$OLD_TARGET/uploads" "$TARGET/uploads"
  else
    mkdir -p "$TARGET/uploads"
  fi

  mkdir -p "$TARGET/backup"
}

start_environment() {
  cd "$TARGET"
  docker compose up -d --build
  wait_for_mysql

  local should_import="false"
  if [[ "$IMPORT_DB" == "always" ]]; then
    should_import="true"
  elif [[ "$IMPORT_DB" == "auto" && "$FIRST_INSTALL" == "true" && -f "$TARGET/backup/problem_db_deploy.sql" ]]; then
    should_import="true"
  fi

  if [[ "$should_import" == "true" ]]; then
    echo "Importing database from backup/problem_db_deploy.sql..."
    docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "DROP DATABASE IF EXISTS problem_db; CREATE DATABASE problem_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"'
    cat "$TARGET/backup/problem_db_deploy.sql" | docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" problem_db'
    docker compose restart backend
  fi

  local health_url
  if [[ "$ENV_NAME" == "prod" ]]; then
    health_url="http://127.0.0.1:8082/actuator/health"
  else
    health_url="http://127.0.0.1:18082/actuator/health"
  fi

  wait_for_backend "$health_url"
  docker compose ps
}

echo "Deploying $ENV_NAME from $PACKAGE_PATH"
backup_current_environment
extract_package
install_new_release
start_environment

if [[ "$ENV_NAME" == "prod" ]]; then
  echo "DONE: production is available at http://192.168.0.90/"
else
  echo "DONE: test is available at http://192.168.0.90:8081/"
fi
