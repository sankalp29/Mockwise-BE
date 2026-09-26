---
name: sonar-quality-loop
description: >
  Run SonarQube analysis, wait for completion, export open issues, then spawn
  SonarQubeFixer agents until issues are resolved. After Sonar is clean, run
  Checkstyle via the Checkstyle agent; on failures spawn CheckstyleFixer until
  green. Use when the user runs /sonar-quality-loop, /sonarqube, asks to fix
  Sonar issues, run SonarQube then Checkstyle, quality gate loop, or
  SonarQubeFixer / CheckstyleFixer workflow.
---

# SonarQube → Fixer → Checkstyle → Fixer

Orchestrated quality loop for **MockWise backend** (`Mockwise-BE` Maven root).

```text
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│ 1. Run Sonar    │────►│ 2. SonarQubeFixer│────►│ re-scan until 0 │
│    wait + export│     │    (per issue /  │     │    open issues  │
└─────────────────┘     │     batch)       │     └────────┬────────┘
                        └──────────────────┘              │
                                                          ▼
                        ┌──────────────────┐     ┌─────────────────┐
                        │ 4. Checkstyle    │◄────│ 3. Checkstyle   │
                        │    Fixer if FAIL │     │    agent (run)  │
                        └────────┬─────────┘     └─────────────────┘
                                 │
                                 ▼
                        re-run until PASS → DONE
```

## Preconditions

- Working directory: **Mockwise-BE** repo root (where `pom.xml` and `./mvnw` live).
- Docker available **or** an existing SonarQube server:
  - `SONAR_HOST_URL` (default `http://127.0.0.1:9000`)
  - `SONAR_TOKEN` (preferred) or `SONAR_LOGIN` / `SONAR_PASSWORD`
  - Local Docker default first-boot: `admin` / `admin` (script default)
- Scripts:
  - `./scripts/quality/ensure-sonar.sh`
  - `./scripts/quality/run-sonar.sh`  → writes `.quality/sonar-issues.*` (exit 0 = no issues, 2 = issues remain)
  - `./scripts/quality/run-checkstyle.sh` → writes `.quality/checkstyle-issues.*` (exit 0 / 2)

## Limits

| Gate | Max fix rounds |
|------|----------------|
| SonarQube | 5 full scan→fix cycles |
| Checkstyle | 5 run→fix cycles |

If still failing after max rounds, stop and report remaining issues — do not suppress findings.

---

## Phase 0 — Setup

1. Confirm you are in the backend root (`test -f pom.xml && test -x mvnw`).
2. Create todo list tracking: Sonar run → Sonar fix loop → Checkstyle run → Checkstyle fix loop → summary.
3. Optional: `export SONAR_HOST_URL=... SONAR_TOKEN=...` if the user provided them.

---

## Phase 1 — SonarQube Identifier (run + wait + export)

1. Run (long timeout — first Docker pull/start can take minutes):

```bash
./scripts/quality/run-sonar.sh
```

2. Script behavior:
   - Ensures Sonar is UP (starts Docker container `mockwise-sonarqube` if needed)
   - Compiles sources
   - Runs `sonar:sonar`
   - **Waits** for CE task `SUCCESS` via `/api/ce/task`
   - Exports open issues to:
     - `.quality/sonar-issues.json`
     - `.quality/sonar-issues.md`
     - `.quality/sonar-issue-count.txt`

3. Read `sonar-issue-count.txt`.
   - If **0** → skip Phase 2, go to Phase 3.
   - If **> 0** → continue Phase 2.

On hard failure (exit 1): diagnose (Docker, auth, compile) and report to the user. Do not invent issue lists.

---

## Phase 2 — SonarQubeFixer loop

Repeat until issue count is 0 or max rounds reached:

### 2a. Spawn SonarQubeFixer

Prefer **one agent for the whole open list** when total ≤ 40.  
If total > 40, batch by file or chunks of ~25 issues (multiple parallel `spawn_subagent` calls).

```
spawn_subagent:
  subagent_type: sonarqube-fixer
  description: Fix SonarQube issues
  capability_mode: all
  background: false  # wait for completion when possible
  cwd: <absolute path to Mockwise-BE>
  prompt: |
    Fix all open SonarQube issues for MockWise backend.

    Issue catalog (read fully):
    - .quality/sonar-issues.md
    - .quality/sonar-issues.json

    Workspace root: <absolute Mockwise-BE path>

    Rules: minimal correct fixes; no //NOSONAR unless true false positive
    with explanation. Compile with ./mvnw -q -DskipTests compile when done.
    End with SONAR_FIX_REPORT.
```

If `sonarqube-fixer` type is unavailable, use `general-purpose` with the same prompt and prefix: `You are SonarQubeFixer. Follow .grok/agents/sonarqube-fixer.md`.

### 2b. Re-run Sonar

```bash
./scripts/quality/run-sonar.sh
```

### 2c. Decide

- count == 0 → Phase 3
- count > 0 and rounds left → 2a again (pass only remaining issues if useful)
- rounds exhausted → report remaining Sonar issues and **stop** (do not run Checkstyle if user required Sonar clean; if user asked for full loop anyway, note residual Sonar risk and continue only if they insisted)

Default: **require Sonar clean before Checkstyle**.

---

## Phase 3 — Checkstyle agent

Spawn (or run inline if faster — both OK):

```
spawn_subagent:
  subagent_type: checkstyle-agent
  description: Run Checkstyle gate
  capability_mode: all
  cwd: <Mockwise-BE>
  prompt: |
    Run ./scripts/quality/run-checkstyle.sh from the repo root.
    Report CHECKSTYLE_VERDICT: PASS or FAIL with count and report path.
```

Alternatively, the orchestrator may run the script itself and parse `.quality/checkstyle-issue-count.txt` — same gate.

- **PASS** (count 0) → Phase 5 summary
- **FAIL** → Phase 4

---

## Phase 4 — CheckstyleFixer loop

Repeat until PASS or max rounds:

```
spawn_subagent:
  subagent_type: checkstyle-fixer
  description: Fix Checkstyle issues
  capability_mode: all
  cwd: <Mockwise-BE>
  prompt: |
    Fix all Checkstyle violations listed in:
    - .quality/checkstyle-issues.md
    - .quality/checkstyle-issues.json
    Config: config/checkstyle/checkstyle.xml
    After fixes re-run ./scripts/quality/run-checkstyle.sh until clean
    (or report blocked). End with CHECKSTYLE_FIX_REPORT.
```

Then re-run Checkstyle (agent or script). Loop.

---

## Phase 5 — Final report to user

```markdown
## Quality loop result

| Gate | Status | Remaining |
|------|--------|-----------|
| SonarQube | PASS/FAIL | N |
| Checkstyle | PASS/FAIL | N |

### Sonar
- Rounds: x
- Key fixes: ...

### Checkstyle
- Rounds: x
- Key fixes: ...

### Artifacts
- `.quality/sonar-issues.*`
- `.quality/checkstyle-issues.*`

### Next steps
- (optional) commit, PR, CI with `-Pquality`
```

Do **not** commit unless the user asks.

---

## Commands cheat sheet

```bash
# Full Sonar identify + export
./scripts/quality/run-sonar.sh

# Checkstyle only
./scripts/quality/run-checkstyle.sh

# Maven bindings
./mvnw checkstyle:check
./mvnw -Pquality validate   # Checkstyle on validate phase
./mvnw sonar:sonar -Dsonar.host.url="$SONAR_HOST_URL" -Dsonar.token="$SONAR_TOKEN"
```

## Environment

| Variable | Purpose |
|----------|---------|
| `SONAR_HOST_URL` | Server URL (default local :9000) |
| `SONAR_TOKEN` | Auth token |
| `SONAR_LOGIN` / `SONAR_PASSWORD` | Alternate auth |
| `SONAR_PROJECT_KEY` | Default `mockwise-backend` |
| `SONAR_SKIP_DOCKER=1` | Do not auto-start Docker |
| `QUALITY_OUT_DIR` | Override `.quality` |

## Anti-patterns

| Do not | Do instead |
|--------|------------|
| Skip waiting for CE task | Use `run-sonar.sh` (waits) |
| Mass `//NOSONAR` | Real fix or documented false positive |
| Disable Checkstyle in pom to pass | Fix sources |
| Declare done with open issues | Keep looping or report blocked |
| Fix frontend from this skill | This loop is backend-only |

## Slash command

`/sonar-quality-loop`
