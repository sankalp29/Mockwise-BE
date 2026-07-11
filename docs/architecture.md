# MockWise Backend Architecture

## Package map (`com.mockwise.backend`)

| Package | Responsibility |
|---------|----------------|
| `auth` | Supabase JWT filter, security config, WebClient beans, `SupabaseUser` |
| `interview` | Interview lifecycle API, DTOs, domain, application services, repos |
| `question` | Questions, code stubs, optimal solutions, selection |
| `submission` | User submission entity + repository |
| `dashboard` | Metrics/progress API, aggregates, rating extraction |
| `progress` | User-question-seen tracking |
| `evaluation` | Claude / Anthropic feedback integration |
| `codesyntax` | Syntax check via Strategy + Registry + Facade (Java / Python / C++ today) |

### `codesyntax` layout (Step 1 multi-language foundation)

```
codesyntax/
  LanguageToolchain.java          # Strategy interface
  LanguageToolchainRegistry.java  # alias-aware lookup
  SyntaxCheckFacade.java          # workspace + dispatch
  SyntaxCheckService.java         # API-compatible List<String> adapter
  model/ SyntaxCheckResult, ToolStatus
  support/ ProcessRunner, TempWorkspace
  languages/ JavaToolchain, PythonToolchain, CppToolchain
```

Add a new language by implementing `LanguageToolchain` as a `@Component` (Open/Closed).

Supported language ids: `java`, `python`, `cpp`, `javascript`, `typescript`, `go`, `rust`, `ruby`, `scala`, `csharp`.

Discovery APIs:
- `GET /api/interview/supported-languages`
- `GET /api/codesyntax/languages`

| `common` | Shared exception handling, auth helpers |

### Layers inside features

- `api` — REST controllers + `dto`
- `domain` — JPA entities
- `application` — services / use cases
- `infrastructure` — Spring Data repositories

## Config profiles

| Profile | File | Notes |
|---------|------|-------|
| default | `application.yml` | Shared non-secret defaults; Flyway off |
| `dev` | `application-dev.yml` | Local Postgres; `ddl-auto=update` |
| `prod` | `application-prod.yml` | Secrets via env; `ddl-auto=validate` |
| `test` | `src/test/resources/application-test.yml` | In-memory H2 |
| `local` | `application-local.yml` (gitignored) | Optional machine secrets |

## Schema ownership

Historically schema was evolved with Hibernate `ddl-auto=update`. Flyway is available but **disabled** until a baseline is applied. See `src/main/resources/db/migration/README.md`.

## How to run

See root `README.md`.
