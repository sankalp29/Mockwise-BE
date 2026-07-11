---
name: commit-change
description: >
  Analyze uncommitted changes, isolate the files that solve one specific problem
  statement (from the user or inferred from the diff), stage only those files, and
  create a single focused git commit. Write a concise, descriptive bullet-point
  commit message that answers: What changed, Why it was needed, What happens if not
  done, and Impact. Exclude unrelated files, secrets, and noise (.DS_Store, logs,
  build artifacts). Never amend unless explicitly asked. Never add Co-authored-by,
  Signed-off-by, or similar trailers. Use when the user runs /commit-change, or asks
  for a problem-scoped commit, selective stage-and-commit, commit only related files,
  or a What/Why/Impact commit message for one change.
---

# Commit Change

Problem-scoped stage + commit. One problem → one commit → only related files → structured but short message.

## Hard rules

- **Never** add `Co-authored-by`, `Signed-off-by`, `Made-with`, or any similar trailer/footer.
- **Never** amend unless the user explicitly asks to amend.
- **Never** force-push, rewrite history, or push unless the user explicitly asks.
- **Never** stage secrets, credentials, `.env`, private keys, or local-only noise (`.DS_Store`, `logs/`, `target/`, build output) unless the user explicitly insists.
- Stage **only** files that belong to the stated problem. Leave everything else unstaged/uncommitted.
- Do **not** commit if there is nothing relevant to commit; explain what was left out and why.
- Prefer a single logical commit per problem. If the working tree mixes multiple unrelated problems, commit only the scoped set and list what remains dirty.

## Inputs

1. **Problem statement** (required for a good commit):
   - Use the user's description if they gave one.
   - If missing, infer the primary intent from the diff, then state your inferred problem in one sentence and proceed unless the diff clearly mixes multiple unrelated concerns—in that case, ask which problem to commit first.
2. Optional: explicit file list or paths to include/exclude.

## Workflow

Run these steps in order. Use the repo's real git state; do not invent files or diffs.

### 1. Inspect

Run in parallel:

```bash
git status --short
git diff
git diff --cached
git log -5 --oneline
git rev-parse --abbrev-ref HEAD
```

- Include untracked files that might belong to the problem (`git status -u`).
- Note current branch; warn if detached HEAD or unexpected branch, but still proceed if the user asked to commit.

### 2. Scope the problem

- Cluster changed/untracked files by theme (feature, fix, docs, config, test, refactor).
- Select the **minimal file set** that fully implements the one problem statement.
- Exclude:
  - Files for a different problem or WIP
  - Generated/build artifacts
  - Editor/OS junk
  - Secrets and local config with credentials
- If selection is ambiguous (two plausible problem boundaries), briefly list candidate groups and ask which to commit. Do not stage until clear.

### 3. Stage only the selected set

```bash
git add -- path/to/file1 path/to/file2 ...
```

- Prefer explicit paths over `git add .` or `git add -A`.
- After staging, verify:

```bash
git diff --cached --stat
git diff --cached
git status --short
```

- Confirm staged set matches the problem. Unstage mistakes with `git restore --staged -- <path>` before committing.

### 4. Write the commit message

**Format** (subject + body bullets; keep it tight—readable in `git log` without a wall of text):

```text
<imperative subject ≤ ~72 chars>

- What: <what changed, in plain language>
- Why: <reason / problem being solved>
- If not done: <risk, bug, or gap that remains>
- Impact: <user-facing or system effect; who/what benefits>
```

**Message quality bar**

| Section | Do | Don't |
|---------|----|--------|
| Subject | Imperative, specific (`Fix pool leak on submit`) | Vague (`Update code`, `Fix stuff`) |
| What | Concrete files/behavior at a high level | File laundry list or dump of the diff |
| Why | Motivation tied to the problem statement | Restate "What" with different words |
| If not done | Real consequence | Filler (`things break`) |
| Impact | Outcome (perf, correctness, DX, security) | Empty claims (`improves quality`) |

- Match repo style from recent commits when sensible (prefix like `fix:`, `docs:` only if the repo already uses them).
- Body must stay **bullet points**, not paragraphs.
- Each bullet one short line when possible; two lines max per bullet.
- **No** co-author, sign-off, or tool attribution lines anywhere in the message.

### 5. Commit

Use a HEREDOC so formatting is preserved:

```bash
git commit -m "$(cat <<'EOF'
<subject>

- What: ...
- Why: ...
- If not done: ...
- Impact: ...
EOF
)"
```

### 6. Verify and report

```bash
git status --short
git log -1 --format=full
```

Tell the user:

- Branch name
- Commit hash + subject
- Files included in the commit
- Files **left uncommitted** (if any) and why
- That nothing was pushed (unless they asked)

## Decision cheatsheet

| Situation | Action |
|-----------|--------|
| User gave a clear problem | Scope files to that problem only |
| Mixed unrelated changes | Commit one problem; leave the rest dirty |
| Only unrelated or junk changes | Do not commit; explain |
| Secrets in the relevant set | Stop; warn; do not stage secrets |
| Nothing staged after filter | Do not create an empty commit |
| Hook failed | Fix if trivial and retry once; do not `--no-verify` unless user asks |
| User asks to amend | Only if they explicitly requested amend; still no co-authored trailers |

## Example

**Problem:** "Connection pool was leaking after interview submit."

**Staged:** `InterviewService.java`, `application.properties.example`

**Message:**

```text
Fix Hikari connection leak after interview submit

- What: Close response paths and tighten pool settings used on submit
- Why: Each submit held connections past request end under load
- If not done: Pool exhaustion, timeouts, and cascading 5xx under concurrency
- Impact: Stable submit path; fewer connection-wait failures in staging
```

## Anti-patterns

- Staging the entire working tree "to be safe"
- One commit that mixes docs + feature + unrelated cleanup for different problems
- Verbose essay commit messages
- Subject that is just `WIP` or `updates`
- Adding any `Co-authored-by:` line
- Pushing or opening a PR unless asked

## When not to use this skill

- User only wants a message drafted, not a commit → draft only, do not run `git commit`
- User wants everything committed regardless of problem boundaries → clarify; if they insist, still write the structured message but warn about mixed scope
- Empty working tree → report clean state; no commit
