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