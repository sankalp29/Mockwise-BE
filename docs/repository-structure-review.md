# MockWise Backend — Repository & Folder Structure Review

**Branch:** `docs/repository-structure-review`  
**Scope:** Structure & readability only (not a full code review)  
**Date:** 2026-07-10

---

## Verdict

**Partially readable at a glance; not scalable.**  
A new engineer can open the repo and find the Spring Boot app, but the Java package layout does not express domains, layers, or boundaries. Most business logic sits in one flat package and one oversized service class. The repo will get harder to navigate as features grow.

---

## Current Layout (as observed)

```
Mockwise-BE/                          # git root
├── .vscode/
└── mockwise-backend/                 # actual project
    ├── CLAUDE_SETUP.md
    ├── Dockerfile
    ├── HELP.md                       # Spring Initializr boilerplate
    ├── logs/                         # runtime logs (tracked)
    ├── pom.xml
    └── src/
        ├── main/java/com/mockwise/mockwise_backend/
        │   ├── MockwiseBackendApplication.java
        │   ├── auth/                 # 3 files (Supabase auth + security)
        │   ├── config/               # 1 file (SupabaseConfig only)
        │   └── interview/            # ~20 files (everything else)
        ├── main/resources/
        │   ├── application.properties(.example)
        │   └── db/migration/         # only V3__*.sql
        └── test/java/.../            # 1 smoke test only
```

**Rough size signals**

| Area | Observation |
|------|-------------|
| Java source files | ~27 |
| Packages under root | 3 (`auth`, `config`, `interview`) |
| `interview/` package | ~20 mixed types |
| `InterviewService` | ~757 lines (god service) |
| `InterviewController` | ~373 lines |
| Tests | 1 context-load test |

---

## Problems

### 1. Repository root is a thin wrapper, not a project

- Git root (`Mockwise-BE`) only contains `.vscode/` and `mockwise-backend/`.
- No root `README.md`, no contribution guide, no high-level map of the system.
- Naming is inconsistent across levels: `Mockwise-BE` → `mockwise-backend` → package `mockwise_backend` → class `MockwiseBackendApplication`.
- Extra nesting forces every path, Docker context, and IDE open to go one level deeper for no multi-module benefit.

**Why it hurts readability:** First-time visitors cannot tell if this is a monorepo, a single service, or a placeholder for more apps.

---

### 2. Base package name is awkward and redundant

- Package is `com.mockwise.mockwise_backend` (underscore forced by invalid hyphen from Spring Initializr — noted in `HELP.md`).
- Repeats the product name (`mockwise` twice) and encodes the deployable artifact name into the package.

**Why it hurts readability:** Long, non-idiomatic package paths on every import; does not match common Spring style (`com.mockwise.backend` or `com.mockwise.api`).

---

### 3. `interview/` is a “god package”

Almost all domain code is dumped into one directory:

- Controllers: `InterviewController`, `DashboardController`
- Services: `InterviewService`, `UserQuestionSeenService`, `ClaudeService`
- Entities: `Interview`, `Question`, `UserSubmission`, `DashboardAggregate`, …
- Repositories: 8+ `*Repository` interfaces
- Unrelated concerns: AI feedback (`ClaudeService`), syntax checking (inside `InterviewService`), optimal solutions, seen-questions tracking

**Why it hurts readability:**

- Cannot browse by feature (“where is dashboard?”) or by layer (“where are entities?”).
- File list is alphabetical noise: entity next to controller next to AI client.
- New code has no obvious home → package keeps growing.

---

### 4. Layers and responsibilities are not separated

- Controllers, services, JPA entities, repositories, and request models live side-by-side.
- DTOs/request bodies are nested static classes inside controllers/services (`StartInterviewRequest`, `SubmissionRequest`, etc.) instead of a clear `dto` / `api` package.
- `config/` only holds Supabase wiring that conceptually belongs with `auth` / security.
- HTTP validation, auth extraction, orchestration, persistence, and code-compile checks are mixed across controller + service.

**Why it hurts readability:** No predictable “where do I put X?” rule. Mental model of the app is not mirrored by folders.

---

### 5. `InterviewService` concentrates too many domains

From structure and size alone, one service owns:

- Interview lifecycle (start / submit / access checks)
- Question selection & seen-question logic touchpoints
- Dashboard data helpers
- Optimal-solution lookup
- Multi-language syntax checking / local compile
- Claude feedback orchestration

**Why it hurts readability:** Package structure cannot be clean while a single class is the application’s gravitational center. Folder refactors without splitting this class will only move the problem.

---

### 6. Missing structural building blocks

| Missing | Impact |
|---------|--------|
| No `dto` / `api` package | Request/response shapes hidden inside large types |
| No `exception` + global handler package | Errors scattered; API contract hard to see |
| No `integration` / `client` package | Claude (and Judge0 config) not treated as external systems |
| No feature split for `question`, `dashboard`, `submission` | Domains only exist as class names, not folders |
| No shared `common` package | Utilities and cross-cutting types have no home |

---

### 7. Resources & config structure is incomplete / misleading

- `spring.profiles.active=dev` is set, but there is no `application-dev.properties` / `application-prod.properties` split.
- `db/migration/` exists with **only** `V3__add_user_question_seen_table.sql` — no V1/V2 baseline, and **no Flyway/Liquibase dependency** in `pom.xml`.
- Schema is driven by `spring.jpa.hibernate.ddl-auto=update`, so the migration folder is aspirational, not the source of truth.
- Secrets live in local `application.properties` (gitignored — good), but the example file and profile layout do not clearly show production-safe config shape.

**Why it hurts readability:** Resource tree *looks* like a production-ready setup (profiles, migrations) but does not behave like one.

---

### 8. Test tree does not mirror production structure

- Only `MockwiseBackendApplicationTests` (context loads).
- No `src/test/java/.../interview/`, `auth/`, etc.
- No fixtures, no test resources, no slice tests.

**Why it hurts readability:** Tests are often the best documentation of package intent. Their absence means structure is only explained by production code volume.

---

### 9. Repo hygiene reduces structural clarity

- **Tracked runtime logs** under `mockwise-backend/logs/` (`.log` + rotated `.gz`).
- Multiple `.DS_Store` files present under `src/`.
- `HELP.md` is Spring Initializr boilerplate; no product README at root or under the app.
- `CLAUDE_SETUP.md` is useful but sits alone without a general onboarding doc.
- `.gitignore` ignores `application.properties` and `.vscode/` but does **not** ignore `logs/`.

**Why it hurts readability:** Noise in the tree competes with source; newcomers cannot tell what is product code vs local residue.

---

### 10. Docker / build layout is fine, but orphaned from docs

- Multi-stage `Dockerfile` is reasonable for a single JAR.
- No root-level compose, no README “how to run”, no mention of required env vars in a single entrypoint doc.

---

## Is it readable today?

| Question | Answer |
|----------|--------|
| Can you find the Spring Boot entrypoint? | Yes |
| Can you find auth code? | Yes (`auth/`) |
| Can you find interview HTTP APIs? | Yes, but buried among 20 peers |
| Can you find dashboard as a domain? | No — only by class name inside `interview/` |
| Can you find AI / Judge0 integrations? | Partially — Claude is under `interview/`; Judge0 is config-only |
| Can you understand schema history from folders? | No — incomplete migrations + Hibernate DDL |
| Can a new contributor onboard from the repo alone? | Weakly — no real README, boilerplate HELP |

**Summary:** Readable as a small demo; **not readable as a growing product backend.**

---

## Recommended Refactor (target structure)

### A. Flatten the repository (pick one)

**Option A1 — Single-service repo (recommended for current size)**

```
Mockwise-BE/                    # git root = Maven project root
├── README.md
├── Dockerfile
├── pom.xml
├── docs/
│   └── architecture.md
└── src/main/java/com/mockwise/backend/...
```

Move contents of `mockwise-backend/` up to the git root (or rename repo to match). Remove the empty outer shell.

**Option A2 — Keep nested only if multi-module is planned**

```
Mockwise-BE/
├── README.md
├── pom.xml                     # parent POM
├── mockwise-api/               # this backend
└── mockwise-worker/            # future
```

Do **not** keep a single nested module with no parent POM — that is nesting without benefit.

---

### B. Rename base package

```
com.mockwise.mockwise_backend  →  com.mockwise.backend
```

Short, standard, no underscore. Do as a dedicated mechanical PR (IDE refactor + test).

---

### C. Prefer package-by-feature (with thin layers inside)

Suggested target:

```
com.mockwise.backend/
├── MockwiseBackendApplication.java
├── auth/
│   ├── api/                    # filters if needed
│   ├── SupabaseAuthFilter.java
│   ├── SupabaseAuthService.java
│   ├── SupabaseUser.java       # promote nested type
│   └── SecurityConfig.java
├── interview/
│   ├── api/
│   │   ├── InterviewController.java
│   │   └── dto/
│   ├── domain/                 # Interview, InterviewQuestion, Status
│   ├── application/            # InterviewService (lifecycle only)
│   └── infrastructure/         # InterviewRepository, ...
├── question/
│   ├── domain/                 # Question, QuestionCodeStub, OptimalSolution
│   ├── application/
│   └── infrastructure/
├── submission/
│   ├── domain/                 # UserSubmission
│   ├── application/
│   └── infrastructure/
├── dashboard/
│   ├── api/DashboardController.java
│   ├── domain/DashboardAggregate.java
│   ├── application/
│   └── infrastructure/
├── progress/                   # UserQuestionSeen*
├── evaluation/                 # Claude feedback integration
│   └── ClaudeService.java
├── codesyntax/                 # extract compile/syntax check out of InterviewService
│   └── SyntaxCheckService.java
├── common/
│   ├── exception/
│   │   ├── ApiExceptionHandler.java
│   │   └── ...
│   └── util/
└── config/                     # pure Spring/infra config only
```

**Rules of thumb**

1. **One feature folder = one reason to change.**
2. Controllers only in `api/`; entities only in `domain/`; Spring Data only in `infrastructure/`.
3. DTOs live next to the API that exposes them (`api/dto`), not as nested static classes in 300+ line files.
4. External systems (Claude, Judge0, Supabase client wiring) live in feature or `integration` packages — never inside domain entity folders.
5. If a class name would fit two folders, split the class first, then move.

---

### D. Split god types before moving folders

Mechanical package moves without splits will re-create the same mess. Priority splits:

1. **`InterviewService`** → interview lifecycle / submission scoring / dashboard queries / syntax check / optimal solution lookup.
2. **`InterviewController`** → extract DTOs; thin controller methods; push validation to `@Valid` + DTO annotations.
3. **`ClaudeService`** → `evaluation` (or `integration.claude`).
4. **Dashboard** → leave `interview` entirely.

---

### E. Align resources with real practice

```
src/main/resources/
├── application.yml                 # shared non-secret defaults
├── application-dev.yml             # local overrides (optional gitignore for secrets)
├── application-prod.yml            # prod-safe, secrets via env
└── db/migration/
    ├── V1__baseline.sql            # real baseline or generate from current schema
    ├── V2__....sql
    └── V3__add_user_question_seen_table.sql
```

- Add Flyway (or Liquibase) dependency **or** remove the `db/migration` folder until you adopt it.
- Set `ddl-auto=validate` (or `none`) once migrations own the schema.
- Document required env vars in `README.md` (Claude, Supabase, DB, Judge0).

---

### F. Mirror tests to packages

```
src/test/java/com/mockwise/backend/
├── interview/
│   ├── InterviewServiceTest.java
│   └── InterviewControllerTest.java
├── auth/
└── ...
src/test/resources/
└── application-test.yml
```

Even a few focused tests will force cleaner package boundaries.

---

### G. Hygiene fixes (low effort, high clarity)

- Add root `README.md` (what the service does, how to run, env vars, package map).
- Delete or untrack `logs/`; add `logs/` to `.gitignore`.
- Remove `.DS_Store` from tree; ensure `.gitignore` covers them at repo root.
- Replace or delete `HELP.md` boilerplate; keep `CLAUDE_SETUP.md` under `docs/`.
- Ignore local IDE noise consistently at git root.

---

## Suggested refactor sequence (safe order)

1. **Docs + hygiene** (this branch): README outline, ignore logs, map current → target. No package moves.
2. **Extract DTOs** from controllers/services into `.../dto` without renaming base package.
3. **Split `InterviewService`** into focused services (still under current packages).
4. **Introduce feature packages** (`dashboard`, `question`, `evaluation`, `codesyntax`) and move types.
5. **Rename base package** `mockwise_backend` → `backend` in one PR.
6. **Flatten repo root** (optional but recommended) once package rename settles.
7. **Migrations + profiles**: Flyway baseline, profile files, `ddl-auto=validate`.
8. **Tests**: mirror structure; cover critical interview/auth paths.

Do not combine package rename + repo flatten + service splits in one PR.

---

## Target “readable” checklist

After refactor, a new engineer should answer these in under 2 minutes:

- [ ] Where is the HTTP API for starting an interview?
- [ ] Where is auth enforced?
- [ ] Where do question entities live?
- [ ] Where does Claude get called?
- [ ] Where is schema defined?
- [ ] Where do I add a new dashboard metric?
- [ ] How do I run the app locally?

If any answer requires opening a 700-line class, structure still needs work.

---

## Out of scope for this document

- Security of secrets currently present in local config (separate urgent hygiene task).
- Code-quality / performance review of individual methods.
- API design / URL versioning.
- Frontend or monorepo coordination with other MockWise repos.

---

## Bottom line

| Dimension | Current grade | Main fix |
|-----------|---------------|----------|
| Repo layout | Poor | Flatten or real multi-module; root README |
| Package naming | Weak | `com.mockwise.backend` |
| Feature separation | Poor | Split god `interview/` package |
| Layer clarity | Poor | `api` / `domain` / `application` / `infrastructure` |
| Config & migrations | Misleading | Profiles + real Flyway (or remove fake migrations) |
| Tests as structure docs | Missing | Mirror packages under `src/test` |
| Noise (logs, DS_Store) | Poor | `.gitignore` + cleanup |

**The structure is understandable as a prototype. It is not yet organized for a product team.** The highest-leverage moves are: (1) split `InterviewService`, (2) introduce feature packages, (3) add a real README and flatten the outer folder.
