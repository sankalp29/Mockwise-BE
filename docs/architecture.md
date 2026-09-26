# MockWise Backend Architecture

## Package map (`com.mockwise.backend`)

The layout and the rules for changing it live in [CLAUDE.md](../CLAUDE.md). Summary:

| Package | Responsibility |
|---------|----------------|
| `controller/<feature>` | REST controllers and request/response DTOs. No repositories. |
| `service/<feature>` | Use cases. Interview lifecycle, feedback, questions, dashboard, syntax check, Claude. |
| `repository/<feature>` | JPA entities and Spring Data repositories for that feature. |
| `config` | Security filter, Supabase clients, async executor, auth helpers. |
| `exception` | API error types and `GlobalExceptionHandler`. |

Features: `interview`, `question`, `submission`, `dashboard`, `progress`, `evaluation`, `codesyntax`, `auth`.

### `codesyntax` layout

Lives under `service/codesyntax/`. The HTTP entry is `controller/codesyntax/CodeSyntaxController`.

```text
service/codesyntax/
  LanguageToolchain.java          # Strategy interface
  LanguageToolchainRegistry.java  # alias-aware lookup
  SyntaxCheckFacade.java          # workspace + dispatch
  SyntaxCheckService.java         # API-compatible adapter
  model/ SyntaxCheckResult, ToolStatus
  support/ ProcessRunner, TempWorkspace
  languages/ one toolchain class per language
```

Add a new language by implementing `LanguageToolchain` as a `@Component`.

Supported language ids: `java`, `python`, `cpp`, `javascript`, `typescript`, `go`, `rust`, `ruby`, `scala`, `csharp`.

Discovery API:

- `GET /api/codesyntax/languages`

### Layers inside features

Code is grouped by role first, then by feature. Do not add a new `api` / `domain` / `application` / `infrastructure` tree.

- `controller/<feature>` — REST controllers + `dto`. Maps JSON to and from service types.
- `service/<feature>` — use cases and the types those use cases accept and return.
- `repository/<feature>` — JPA entities and Spring Data repositories.

Dependency direction and the mapping rules are in [CLAUDE.md](../CLAUDE.md). A service does not import `controller`. A repository does not import `service` or `controller`.

## Config profiles

| Profile | File | When |
|---------|------|------|
| _(always)_ | `application.yml` | Shared non-secret defaults; default active profile = `local` |
| **`local`** | `application-local.yml` | Laptop + localhost Postgres (`mockwise_local`); `ddl-auto=update` |
| **`prod`** | `application-prod.yml` | Deployed environment; secrets from env; `ddl-auto=validate` |
| **`test`** | `src/test/resources/application-test.yml` | Automated tests (H2); set via `@ActiveProfiles("test")` |
| _(optional)_ | `application-local-secrets.yml` | Machine-only secrets; gitignored; auto-included with `local` |

There is **no** `dev` profile. Local development uses **`local`** only.

### Naming rule

`application-<profile>.yml` where `<profile>` is exactly the environment: `local` | `prod` | `test`.

## Schema ownership

Historically schema was evolved with Hibernate `ddl-auto=update`. Flyway is available but **disabled** until a baseline is applied. See `src/main/resources/db/migration/README.md`.

## How to run

See root `README.md`.