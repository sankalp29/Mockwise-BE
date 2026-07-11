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
| `codesyntax` | Local syntax checking (Java / Python / C++) |
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
