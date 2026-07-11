---
name: root-cause-analysis
description: >
  Deep-dive the codebase to find the true root cause of a stated problem, explain
  it in plain language, and produce a concrete implementation plan that fixes the
  root cause (not symptoms) using the best-fit solution. Prefer evidence from code,
  logs, tests, and git history over guesses. Use when the user runs
  /root-cause-analysis, or asks for RCA, root cause analysis, why is this broken,
  deep dive bug, find root cause, or an implementation plan to fix the underlying
  issue.
---

# Root Cause Analysis

Investigate a stated problem until the **true root cause** is identified with evidence, then deliver:

1. A plain-language explanation of the RCA
2. An implementation plan that fixes the **root cause** (not a band-aid)
3. The best-fit solution among realistic options, with trade-offs

**Default mode is analysis + plan only.** Do **not** implement code unless the user explicitly asks to implement after the plan.

## Hard rules

1. **Root cause ≠ symptom.** A failing test, 500 response, or NPE is usually a symptom. Keep asking “why?” until the underlying defect, design gap, or process break is clear.
2. **Evidence over vibes.** Every claim about cause must cite concrete evidence: file paths, symbols, stack traces, SQL, config keys, git blame/log, or reproducible steps.
3. **No premature fixes.** Do not patch, disable tests, weaken assertions, or “just make it work” during RCA unless the user asked to implement.
4. **Best known solution** means best for *this* codebase and constraints (correctness first, then simplicity, safety, maintainability)—not the flashiest pattern.
5. **Prefer reversible, minimal plans** that remove the cause with the smallest durable change set.
6. If multiple root causes exist, rank them (primary vs contributing) and plan for the primary first.
7. If blocked after thorough search, say what is unknown, what was checked, and the smallest experiment to unstick RCA.

## Inputs

Collect or infer:

- **Problem statement** (required): what is wrong, expected vs actual
- **Repro steps**, error messages, logs, screenshots, failing tests
- **Scope hints**: service, endpoint, package, PR, commit, environment (local/staging/prod)
- **Constraints**: must not break API, deadline, no schema change, etc.

If the problem is too vague to investigate (e.g. “app is broken” with no signal), ask **one** tight clarifying question, then proceed with the best available evidence.

## Workflow

### Phase 0 — Frame the problem

Write a short problem frame before deep search:

```markdown
## Problem frame
- Observed: ...
- Expected: ...
- Impact: ...
- Environment: ...
- First seen / recent changes (if known): ...
```

### Phase 1 — Reproduce or bound the failure

1. Try to reproduce with the smallest command (test, curl, app log, SQL).
2. If not reproducible, bound it: which layer fails (client / API / service / DB / external)?
3. Capture **exact** error text, status codes, and stack frames.

### Phase 2 — Deep dive (evidence gathering)

Search broadly, then narrow. Use tools as needed:

| Signal | Where to look |
|--------|----------------|
| Stack trace | Class/method at top non-framework frames |
| Wrong data | Controllers → services → repos → entities → migrations |
| Auth/403/empty body | Security config, filters, token handling |
| Intermittent | Concurrency, transactions, lazy loading, pools, retries |
| Regression | `git log` / `git blame` on hot files; recent PRs |
| Config | `application*.yml`, env, profiles, feature flags |
| Tests | Failing cases + missing scenarios that would have caught this |

Do a real codebase dive:

- Grep for error strings, endpoint paths, entity/table names
- Read the full call path (entry → domain → infra)
- Note invariants that are violated (nullability, ownership, session boundaries, auth principal type)
- Check for known anti-patterns in this repo (god services, lazy load after session close, fire-and-forget without error isolation, etc.)

### Phase 3 — 5 Whys / causal chain

Build an explicit chain from symptom to root:

```text
Symptom → Why1 → Why2 → Why3 → ... → Root cause
```

Mark each step with evidence. Stop when the next “why” leaves the system under change (e.g. “because the vendor was down”) and treat the in-scope defect as the root *for the plan* (e.g. missing timeout/fallback/handling).

### Phase 4 — Rule out alternatives

List competing hypotheses and **disprove** them quickly:

| Hypothesis | Evidence for | Evidence against | Status |
|------------|--------------|------------------|--------|
| ... | ... | ... | ruled out / possible / confirmed |

Do not present a root cause as certain if it is only the best remaining hypothesis—label confidence.

### Phase 5 — Root cause statement

Write one clear paragraph + a one-liner:

```markdown
## Root cause (one-liner)
...

## Root cause (plain language)
...

## Evidence
- `path/File.java` — ...
- Log/test: ...

## Contributing factors (optional)
- ...

## Confidence
High | Medium | Low — why
```

**Plain language bar:** a teammate unfamiliar with the file should understand *what broke*, *why*, and *why symptoms appeared as they did*.

### Phase 6 — Solution options

Propose **2–3** realistic fixes when non-trivial:

| Option | How it addresses root cause | Pros | Cons | Effort |
|--------|----------------------------|------|------|--------|
| A (recommended) | ... | ... | ... | S/M/L |
| B | ... | ... | ... | |
| C (band-aid only if needed) | ... | ... | ... | |

**Choose recommended** using:

1. Fixes root cause permanently
2. Minimal blast radius
3. Fits existing architecture (or intentionally improves it)
4. Testable
5. Safe for prod data/API clients

Reject options that only hide symptoms (broader catch, disable test, increase timeout without understanding, `@JsonIgnore` without ownership of session/fetch design—unless that *is* the correct API boundary fix).

### Phase 7 — Implementation plan

Produce a step-by-step plan the user (or `/tdd-workflow`) can execute:

```markdown
## Implementation plan
1. **Scenarios / tests first** (if behavior fix): list cases that fail today and must pass
2. **Code changes**: files/packages to touch; what changes in each
3. **Data/config**: migrations, profile keys, env vars
4. **Verification**: commands (unit/integration/curl), success criteria
5. **Rollout / risk**: backward compatibility, feature flag, rollback
6. **Out of scope**: related cleanups deferred on purpose
```

Keep steps ordered and concrete (file-level, not “clean up auth”).

### Phase 8 — Deliverable format (always use this structure)

```markdown
# RCA: <short title>

## TL;DR
2–4 sentences: problem, root cause, recommended fix.

## Problem frame
...

## Investigation summary
What was checked (bullet list). Key dead ends briefly.

## Causal chain
1. ...
2. ...
3. **Root:** ...

## Root cause explained (plain language)
...

## Evidence
...

## Why not the other hypotheses
...

## Recommended solution
What + why it is best here.

## Alternatives considered
Table or bullets.

## Implementation plan
Numbered steps with files and verification.

## Risks & follow-ups
...
```

## Collaboration with other skills

- After the plan is approved, use **`/tdd-workflow`** to implement with tests first.
- When committing the fix, use **`/commit-change`** for a problem-scoped commit.
- Do **not** auto-start implementation from this skill alone.

## Anti-patterns

| Anti-pattern | Do instead |
|--------------|------------|
| “NullPointerException is the root cause” | Find why the reference was null |
| Blame “race condition” with no proof | Show interleaving or shared mutable state |
| Fix by catching Exception | Fix producer of invalid state |
| Huge rewrite plan for a local bug | Smallest durable fix first |
| RCA without reading code | Dive into call path and config |
| Implementing mid-RCA | Finish plan; wait for go-ahead |

## When the skill does not apply

- Pure feature request with no defect → design/implement skills, not RCA
- User already knows root cause and only wants code → implement under TDD
- Infra outage fully outside the repo with no app-side defect → document external cause; plan only app hardening if relevant
