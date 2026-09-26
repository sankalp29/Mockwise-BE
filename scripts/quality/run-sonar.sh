#!/usr/bin/env bash
# Run SonarQube analysis, wait for compute engine, export open issues to .quality/
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

OUT_DIR="${QUALITY_OUT_DIR:-${ROOT}/.quality}"
mkdir -p "${OUT_DIR}"

SONAR_HOST_URL="${SONAR_HOST_URL:-http://127.0.0.1:9000}"
SONAR_TOKEN="${SONAR_TOKEN:-}"
SONAR_PROJECT_KEY="${SONAR_PROJECT_KEY:-mockwise-backend}"
# Local default token empty — first-time server uses admin/admin; token preferred.
AUTH_ARGS=()
CURL_AUTH=()
if [[ -n "${SONAR_TOKEN}" ]]; then
  AUTH_ARGS=(-Dsonar.token="${SONAR_TOKEN}")
  CURL_AUTH=(-u "${SONAR_TOKEN}:")
elif [[ -n "${SONAR_LOGIN:-}" && -n "${SONAR_PASSWORD:-}" ]]; then
  AUTH_ARGS=(-Dsonar.login="${SONAR_LOGIN}" -Dsonar.password="${SONAR_PASSWORD}")
  CURL_AUTH=(-u "${SONAR_LOGIN}:${SONAR_PASSWORD}")
else
  # Fresh local SonarQube default (change after first login)
  AUTH_ARGS=(-Dsonar.login=admin -Dsonar.password=admin)
  CURL_AUTH=(-u "admin:admin")
fi

echo "==> Ensuring SonarQube is ready"
"${ROOT}/scripts/quality/ensure-sonar.sh"

echo "==> Compiling sources for analysis"
./mvnw -q -DskipTests compile test-compile

echo "==> Running sonar:sonar"
./mvnw -q org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
  -Dsonar.host.url="${SONAR_HOST_URL}" \
  -Dsonar.projectKey="${SONAR_PROJECT_KEY}" \
  "${AUTH_ARGS[@]}" \
  | tee "${OUT_DIR}/sonar-scan.log" || true

# Also re-run without -q on failure for visibility
if ! grep -q "ANALYSIS SUCCESSFUL\|EXECUTION SUCCESS" "${OUT_DIR}/sonar-scan.log" 2>/dev/null; then
  echo "Quiet scan may have failed; retrying with full output..."
  ./mvnw org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
    -Dsonar.host.url="${SONAR_HOST_URL}" \
    -Dsonar.projectKey="${SONAR_PROJECT_KEY}" \
    "${AUTH_ARGS[@]}" \
    | tee "${OUT_DIR}/sonar-scan.log"
fi

REPORT_TASK="${ROOT}/target/sonar/report-task.txt"
if [[ ! -f "${REPORT_TASK}" ]]; then
  # Fallback location
  REPORT_TASK="$(find "${ROOT}/target" -name report-task.txt 2>/dev/null | head -1 || true)"
fi
if [[ -z "${REPORT_TASK}" || ! -f "${REPORT_TASK}" ]]; then
  echo "ERROR: report-task.txt not found after sonar analysis" >&2
  exit 1
fi

# shellcheck disable=SC1090
source <(grep -E '^(ceTaskId|serverUrl|dashboardUrl)=' "${REPORT_TASK}" | sed 's/^/export /')
CE_TASK_ID="${ceTaskId:-}"
SERVER_URL="${serverUrl:-${SONAR_HOST_URL}}"

if [[ -z "${CE_TASK_ID}" ]]; then
  echo "ERROR: ceTaskId missing in ${REPORT_TASK}" >&2
  cat "${REPORT_TASK}" >&2
  exit 1
fi

echo "==> Waiting for CE task ${CE_TASK_ID}"
MAX_WAIT="${SONAR_CE_TIMEOUT:-600}"
elapsed=0
status="PENDING"
while (( elapsed < MAX_WAIT )); do
  resp="$(curl -sf "${CURL_AUTH[@]}" "${SERVER_URL}/api/ce/task?id=${CE_TASK_ID}" || true)"
  status="$(printf '%s' "${resp}" | python3 -c 'import sys,json; d=json.load(sys.stdin); print(d.get("task",{}).get("status",""))' 2>/dev/null || echo "")"
  if [[ "${status}" == "SUCCESS" ]]; then
    echo "CE task SUCCESS"
    break
  fi
  if [[ "${status}" == "FAILED" || "${status}" == "CANCELED" ]]; then
    echo "ERROR: CE task ${status}" >&2
    printf '%s\n' "${resp}" >&2
    exit 1
  fi
  sleep 3
  elapsed=$((elapsed + 3))
  printf '.'
done
echo
if [[ "${status}" != "SUCCESS" ]]; then
  echo "ERROR: CE task did not finish (last status=${status})" >&2
  exit 1
fi

ISSUES_JSON="${OUT_DIR}/sonar-issues.json"
ISSUES_MD="${OUT_DIR}/sonar-issues.md"
echo "==> Exporting open issues → ${ISSUES_JSON}"

# Paginate issues API
page=1
page_size=500
tmp_all="${OUT_DIR}/.sonar-issues-raw.jsonl"
: > "${tmp_all}"
total=1
fetched=0
while (( fetched < total )); do
  page_json="$(curl -sf "${CURL_AUTH[@]}" \
    "${SERVER_URL}/api/issues/search?componentKeys=${SONAR_PROJECT_KEY}&resolved=false&ps=${page_size}&p=${page}&additionalFields=_all" )"
  total="$(printf '%s' "${page_json}" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("total",0))')"
  printf '%s\n' "${page_json}" >> "${tmp_all}"
  count="$(printf '%s' "${page_json}" | python3 -c 'import sys,json; print(len(json.load(sys.stdin).get("issues",[])))')"
  fetched=$((fetched + count))
  echo "  page ${page}: +${count} (fetched ${fetched}/${total})"
  if (( count == 0 )); then break; fi
  page=$((page + 1))
done

export ROOT OUT_DIR ISSUES_JSON ISSUES_MD SONAR_PROJECT_KEY SERVER_URL
export TMP_ALL="${tmp_all}"
python3 - <<'PY'
import json
import os
import pathlib

tmp_all = pathlib.Path(os.environ["TMP_ALL"])
issues = []
for line in tmp_all.read_text().splitlines():
    if not line.strip():
        continue
    data = json.loads(line)
    issues.extend(data.get("issues", []))

seen = set()
unique = []
for i in issues:
    k = i.get("key")
    if k in seen:
        continue
    seen.add(k)
    unique.append(i)

server = os.environ.get("SERVER_URL", "")
project = os.environ.get("SONAR_PROJECT_KEY", "")
out = {
    "projectKey": project,
    "serverUrl": server,
    "total": len(unique),
    "issues": unique,
}
out_path = pathlib.Path(os.environ["ISSUES_JSON"])
out_path.write_text(json.dumps(out, indent=2))

lines = [
    f"# SonarQube open issues ({len(unique)})",
    "",
    f"Server: {server}",
    f"Project: {project}",
    "",
]
for idx, i in enumerate(unique, 1):
    comp = i.get("component", "")
    path = comp.split(":")[-1] if ":" in comp else comp
    lines.append(f"## Issue {idx}: `{i.get('rule')}` — {i.get('severity')}")
    lines.append(f"- **Key:** `{i.get('key')}`")
    lines.append(f"- **File:** `{path}`")
    lines.append(f"- **Line:** {i.get('line', 'n/a')}")
    lines.append(f"- **Type:** {i.get('type')}")
    lines.append(f"- **Message:** {i.get('message')}")
    lines.append("")
pathlib.Path(os.environ["ISSUES_MD"]).write_text("\n".join(lines) + "\n")
count_path = pathlib.Path(os.environ["OUT_DIR"]) / "sonar-issue-count.txt"
count_path.write_text(str(len(unique)))
print(f"Exported {len(unique)} issues")
print(f"JSON: {out_path}")
print(f"MD:   {os.environ['ISSUES_MD']}")
PY

rm -f "${tmp_all}"
count="$(cat "${OUT_DIR}/sonar-issue-count.txt")"
echo "==> Sonar open issues: ${count}"
if [[ "${count}" != "0" ]]; then
  exit 2  # signal: issues remain
fi
exit 0
