#!/usr/bin/env bash
# Ensure a SonarQube server is reachable. Optionally start a local Docker one.
set -euo pipefail

SONAR_HOST_URL="${SONAR_HOST_URL:-http://127.0.0.1:9000}"
SONAR_CONTAINER="${SONAR_CONTAINER:-mockwise-sonarqube}"
SONAR_IMAGE="${SONAR_IMAGE:-sonarqube:community}"
MAX_WAIT_SEC="${SONAR_READY_TIMEOUT:-300}"

is_up() {
  curl -sf "${SONAR_HOST_URL}/api/system/status" 2>/dev/null | grep -q '"status":"UP"'
}

if is_up; then
  echo "SonarQube already UP at ${SONAR_HOST_URL}"
  exit 0
fi

if [[ "${SONAR_SKIP_DOCKER:-0}" == "1" ]]; then
  echo "ERROR: SonarQube not reachable at ${SONAR_HOST_URL} and SONAR_SKIP_DOCKER=1" >&2
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: SonarQube not at ${SONAR_HOST_URL} and docker is unavailable." >&2
  echo "Set SONAR_HOST_URL + SONAR_TOKEN for a remote server, or install Docker." >&2
  exit 1
fi

if docker ps -a --format '{{.Names}}' | grep -qx "${SONAR_CONTAINER}"; then
  echo "Starting existing container ${SONAR_CONTAINER}..."
  docker start "${SONAR_CONTAINER}" >/dev/null
else
  echo "Creating SonarQube container ${SONAR_CONTAINER} (${SONAR_IMAGE})..."
  docker run -d \
    --name "${SONAR_CONTAINER}" \
    -p 9000:9000 \
    -e SONAR_ES_BOOTSTRAP_CHECKS_DISABLE=true \
    "${SONAR_IMAGE}" >/dev/null
fi

echo "Waiting for SonarQube UP at ${SONAR_HOST_URL} (timeout ${MAX_WAIT_SEC}s)..."
elapsed=0
while (( elapsed < MAX_WAIT_SEC )); do
  if is_up; then
    echo "SonarQube is UP."
    exit 0
  fi
  sleep 5
  elapsed=$((elapsed + 5))
  printf '.'
done
echo
echo "ERROR: SonarQube did not become UP within ${MAX_WAIT_SEC}s" >&2
exit 1
