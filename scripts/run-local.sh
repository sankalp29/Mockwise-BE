#!/usr/bin/env bash
# Run the backend with a clean classpath (profile: local).
# Always cleans first so deleted config files cannot linger in target/classes.
set -euo pipefail
cd "$(dirname "$0")/.."

SECRETS="src/main/resources/application-local-secrets.yml"
if [[ ! -f "$SECRETS" ]]; then
  echo "ERROR: missing $SECRETS"
  echo "  cp src/main/resources/application-local-secrets.yml.example $SECRETS"
  echo "  then fill Mockwise-Local Supabase Auth + Database credentials."
  exit 1
fi

export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-local}"
echo "Starting MockWise backend (profile=${SPRING_PROFILES_ACTIVE})..."
echo "  Secrets: $SECRETS (gitignored)"
exec ./mvnw clean spring-boot:run
