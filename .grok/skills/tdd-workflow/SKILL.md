---
name: tdd-workflow
description: >
  Enforce a strict TDD workflow for every feature or code change to prevent
  regressions: enumerate scenarios, write tests first, implement the feature, run
  the tests, and loop on root-cause fixes until all tests pass. On failure, inspect
  only failed test logs, fix the true root cause (no short-term hacks), then re-run.
  Treat the feature as complete only when the full relevant suite is green. Use when
  the user runs /tdd-workflow, or asks for TDD, test-driven development, implement
  with tests first, no-regression workflow, red-green-refactor, or scenario-driven
  feature development.
---

# TDD Workflow

Mandatory loop for implementing features or changes without regressing working code.

**Order is non-negotiable:** scenarios → tests → implement → run → root-cause fix → re-run → complete only when green.

## Hard rules

1. **Tests before production code** for the change. Do not implement feature logic first and “add tests later.”
2. **Enumerate scenarios** before writing any test code. Incomplete scenario lists = incomplete feature.
3. **Never short-term fixes:** no deleting/skipping/commenting out tests, no `@Disabled`/`@Ignore` to go green, no weakening assertions, no `// TODO fix later`, no catching-and-swallowing to silence failures, no `--force` / ignore-failures flags.
4. **On failure:** read **only** the failed test output (and the minimal production code those failures point to). Do not re-read the entire green suite as if it failed.
5. **Root-cause only:** fix the underlying bug or missing behavior. Prefer fixing production code when the test correctly encodes the requirement; fix the test only if the test itself is wrong (wrong expectation, flaky setup, bad fixture)—and say so explicitly.
6. **Feature is not done** until:
   - All **new** tests for the feature pass, and
   - The **relevant existing** suite still passes (no regressions).
7. Do not mark complete, commit as “done,” or move on while any targeted test is red.
8. Prefer the smallest change that makes the failing tests pass for the right reason (red → green → optional refactor with tests still green).

## When this skill applies

- New feature, bug fix, refactor with behavior risk, or API/behavior change
- User runs `/tdd-workflow` or asks to implement with TDD / no regressions
- Default for non-trivial implementation work unless the user explicitly waives TDD (e.g. pure docs, config comments)

## Workflow

### Phase 0 — Understand the change

- Restate the problem/feature in 1–3 sentences.
- Identify modules/packages/APIs touched.
- Note existing tests that already cover adjacent behavior (so you know what must stay green).
- Pick the correct test level(s):
  - **Unit** — pure logic, services with mocked deps
  - **Slice / Web** — controllers, security filters (`@WebMvcTest`, etc.)
  - **Integration** — DB, Spring context, real wiring when behavior depends on it
- Match the project’s test stack (this repo: Java/Spring Boot + Maven → JUnit 5, Spring Test, `./mvnw test` or targeted `-Dtest=...`).

### Phase 1 — Scenario catalog (before any code)

List **all** scenarios for the feature. Use a short checklist the user can scan.

For each scenario capture:

| Field | Content |
|-------|---------|
| Name | Short id (`happy_start_interview`, `reject_null_difficulty`) |
| Type | happy path / edge / error / auth / regression |
| Given | Preconditions |
| When | Action |
| Then | Expected outcome |
| Level | unit / slice / integration |

**Coverage expectations (adapt to the feature):**

- Happy path(s)
- Validation / invalid input
- Auth / authorization (if applicable)
- Not-found / empty / boundary values
- Idempotency / duplicates (if applicable)
- Failure of dependencies (timeouts, 4xx/5xx from clients) when relevant
- **Regression:** existing behaviors that must not change

Do **not** start Phase 2 until the scenario list is written. If the domain is ambiguous, ask only the minimum clarifying questions—then lock the list.

### Phase 2 — Write tests first (Red)

1. Add test classes/methods that encode **every** scenario from Phase 1.
2. Name tests after scenarios (`shouldRejectNullDifficulty`, `shouldReturnAssignedQuestionsOnStart`).
3. Assert observable behavior (status, return values, persisted state, interactions)—not implementation trivia.
4. Prefer focused tests over one mega-test.
5. Run the **new** tests (or the target class). **Expect red** for missing behavior.
6. If new tests are green before any production change, the tests are wrong or the behavior already exists—investigate; do not pretend TDD happened.

**Commands (Maven / this repo):**

```bash
# Single class while developing
./mvnw -q -Dtest=InterviewServiceTest test

# Multiple classes
./mvnw -q -Dtest=InterviewServiceTest,InterviewControllerTest test

# Broader regression after green
./mvnw -q test
```

Adjust module path/cwd to the Maven project root (`mockwise-backend/` if nested).

### Phase 3 — Implement the feature (Green)

1. Write the **minimum** production code to satisfy the failing tests.
2. Do not expand scope beyond the scenario catalog without adding scenarios + tests first.
3. Keep changes cohesive; avoid drive-by refactors while red.

### Phase 4 — Run tests

1. Re-run the **new/feature** tests first.
2. If green, run the **broader relevant suite** (module or full `./mvnw test`) to catch regressions.
3. Parse results carefully: failures vs errors vs skipped.

### Phase 5 — Failure loop (only if red)

Repeat until green:

1. **Collect** only failed test names + their assertion/exception logs (Surefire reports under `target/surefire-reports/` if needed).
2. **Root-cause** each failure:
   - Wrong production logic?
   - Missing branch / null / edge?
   - Incorrect test setup or obsolete expectation?
   - Environment/config issue (call out; don’t mask with sleeps or disabled tests)?
3. **Fix the root cause** in production code (or correct a truly wrong test with a clear explanation).
4. **Re-run** the failed tests, then the feature suite, then broader regression as needed.
5. **Never**:
   - Skip/disable the failing test
   - Loosen assertions to pass
   - Catch Exception and ignore
   - Ship with “known failing” tests

If stuck after multiple honest root-cause attempts on the same failure, stop and report: failure log summary, hypotheses tried, and what you need from the user—do **not** hack a green build.

### Phase 6 — Optional refactor (still green)

- Clean structure only after tests are green.
- Re-run tests after each meaningful refactor.
- No behavior change without a new scenario + test.

### Phase 7 — Done criteria

Feature/change is **complete** only when all are true:

- [ ] Scenario catalog written and each scenario has at least one test
- [ ] New tests written **before** (or driving) the implementation
- [ ] All new/feature tests pass
- [ ] Relevant existing tests pass (no regression)
- [ ] No skipped/disabled tests introduced to hide failures
- [ ] Brief summary prepared for the user (see below)

## Output to the user

When finished (or when blocked), report:

1. **Scenarios** covered (bullet list or table)
2. **Tests added/updated** (class/method names)
3. **Implementation** touchpoints (files/classes)
4. **Test run result** (command + pass/fail counts)
5. If any failures occurred mid-loop: **root causes** found and how they were fixed
6. **Residual risk** (untested areas, manual checks, flaky env)

## Regression guardrails

- Prefer running the **smallest** package/class set while iterating; run **wider** before declaring done.
- If you change shared code (auth, config, base entities), expand the regression surface accordingly.
- Do not delete or gut existing tests to make a change easier—update them only when requirements intentionally change, and document that in the summary.

## Anti-patterns

| Anti-pattern | Do instead |
|--------------|------------|
| Implement first, tests later | Scenarios → tests → code |
| One vague test for a whole feature | One test per scenario |
| `@Disabled` to go green | Fix root cause |
| “Works on my machine” without running tests | Run Maven test commands |
| Fixing symptoms (retry, swallow errors) | Fix underlying defect |
| Claiming done with red CI/local tests | Stay in Phase 5 |
| Changing many unrelated files while red | Minimal code to pass tests |

## Quick reference loop

```text
SCENARIOS ──► WRITE TESTS ──► RUN (expect red)
                    │
                    ▼
              IMPLEMENT ──► RUN TESTS
                    │            │
                    │            ├─ pass + regression green ──► DONE
                    │            │
                    │            └─ fail ──► failed logs only
                    │                          ──► root cause
                    │                          ──► fix properly
                    │                          ──► RUN again ─┘
```

## When not to force full TDD

Still protect against regression, but lighten process only when the user explicitly scopes it:

- Docs-only, comments, pure formatting
- Mechanical renames with compiler + existing suite as the safety net (still run tests)
- Exploratory spikes **explicitly labeled non-production**—do not merge as feature complete without returning to this workflow

If unsure, follow the full workflow.
