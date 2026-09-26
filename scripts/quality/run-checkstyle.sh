#!/usr/bin/env bash
# Run Checkstyle and export violations for the CheckstyleFixer agent.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

OUT_DIR="${QUALITY_OUT_DIR:-${ROOT}/.quality}"
mkdir -p "${OUT_DIR}"

REPORT_XML="${ROOT}/target/checkstyle-result.xml"
ISSUES_JSON="${OUT_DIR}/checkstyle-issues.json"
ISSUES_MD="${OUT_DIR}/checkstyle-issues.md"
LOG="${OUT_DIR}/checkstyle.log"

echo "==> Running Checkstyle"
set +e
./mvnw -q checkstyle:check 2>&1 | tee "${LOG}"
set -e

# Maven may write the report even on failure
if [[ ! -f "${REPORT_XML}" ]]; then
  ./mvnw -q checkstyle:checkstyle 2>&1 | tee -a "${LOG}" || true
fi

export ROOT OUT_DIR ISSUES_JSON ISSUES_MD LOG
python3 - <<'PY'
import json
import os
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

root = pathlib.Path(os.environ["ROOT"])
out_dir = pathlib.Path(os.environ["OUT_DIR"])
issues_json = pathlib.Path(os.environ["ISSUES_JSON"])
issues_md = pathlib.Path(os.environ["ISSUES_MD"])
log_path = pathlib.Path(os.environ["LOG"])

xml_path = root / "target" / "checkstyle-result.xml"
candidates = list((root / "target").rglob("checkstyle-result.xml"))
if not xml_path.exists() and candidates:
    xml_path = candidates[0]

issues = []
if xml_path.exists():
    tree = ET.parse(xml_path)
    for file_el in tree.getroot().findall("file"):
        name = file_el.get("name", "")
        try:
            rel = str(pathlib.Path(name).resolve().relative_to(root.resolve()))
        except Exception:
            rel = name
        for err in file_el.findall("error"):
            source = err.get("source") or ""
            issues.append(
                {
                    "file": rel,
                    "line": int(err.get("line") or 0),
                    "column": int(err.get("column") or 0),
                    "severity": err.get("severity", "error"),
                    "message": err.get("message", ""),
                    "source": source,
                    "rule": source.rsplit(".", 1)[-1],
                }
            )
else:
    log = log_path.read_text(errors="replace")
    pat = re.compile(
        r"\[(?:ERROR|WARN|WARNING|INFO)\]\s+(.+?):(\d+)(?::(\d+))?:\s+(.+?)(?:\s+\[([^\]]+)\])?\s*$"
    )
    for line in log.splitlines():
        m = pat.search(line)
        if not m:
            continue
        f, ln, col, msg, rule = m.groups()
        try:
            rel = str(pathlib.Path(f).resolve().relative_to(root.resolve()))
        except Exception:
            rel = f
        issues.append(
            {
                "file": rel,
                "line": int(ln),
                "column": int(col or 0),
                "severity": "error",
                "message": msg.strip(),
                "source": rule or "",
                "rule": rule or "unknown",
            }
        )

issues_json.write_text(json.dumps({"total": len(issues), "issues": issues}, indent=2))
lines = [f"# Checkstyle issues ({len(issues)})", ""]
for idx, i in enumerate(issues, 1):
    lines.append(f"## Issue {idx}: `{i.get('rule')}`")
    lines.append(f"- **File:** `{i['file']}`")
    lines.append(f"- **Line:** {i['line']}:{i.get('column') or 0}")
    lines.append(f"- **Severity:** {i.get('severity')}")
    lines.append(f"- **Message:** {i.get('message')}")
    lines.append("")
issues_md.write_text("\n".join(lines) + "\n")
(out_dir / "checkstyle-issue-count.txt").write_text(str(len(issues)))
print(f"Checkstyle issues: {len(issues)}")
print(f"JSON: {issues_json}")
print(f"MD:   {issues_md}")
sys.exit(0 if len(issues) == 0 else 2)
PY
