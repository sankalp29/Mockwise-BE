# MockWise Backend

Spring Boot API for MockWise mock interviews: auth (Supabase), interview lifecycle, AI feedback (Claude), dashboard metrics, and syntax checks.

## Requirements

- Java 17+
- Maven Wrapper (`./mvnw`) included
- PostgreSQL (local or Supabase) for `dev` / `prod`
- Optional: Python 3 + g++ for multi-language syntax checks

## Configuration (profiles)

| Profile | File | Purpose |
|---------|------|---------|
| **`local`** (default) | `application-local.yml` | Laptop + local Postgres |
| **`prod`** | `application-prod.yml` | Production; all secrets from env |
| **`test`** | `src/test/resources/application-test.yml` | Automated tests (H2) |

Shared defaults live in `application.yml`. There is **no** `dev` profile — use **`local`** for development.

Optional machine secrets (Claude key, real Supabase keys):

```bash
cp src/main/resources/application-local-secrets.yml.example \
   src/main/resources/application-local-secrets.yml
# edit keys — file is gitignored
```

## Quick start (local)

Recommended: **one Supabase project** for local Auth **and** app Postgres (e.g. Mockwise-Local). Production uses a **different** Supabase project so interviews never cross environments.

```bash
# 1. Secrets (gitignored) — Auth keys + Supabase Postgres JDBC
cp src/main/resources/application-local-secrets.yml.example \
   src/main/resources/application-local-secrets.yml
# Edit: supabase.url / anon / service_role
#       spring.datasource.url = jdbc:postgresql://db.<ref>.supabase.co:5432/postgres?sslmode=require
#       spring.datasource.username / password

# 2. Frontend (mockwise-frontend/.env) — same local Supabase project, anon key only
#    VITE_SUPABASE_URL=https://<ref>.supabase.co
#    VITE_SUPABASE_ANON_KEY=...
#    VITE_API_BASE_URL=http://localhost:8080

# 3. Run backend (profile "local" is the default)
./scripts/run-local.sh
# or: ./mvnw clean spring-boot:run
```

API base: `http://localhost:8080`

> **If you see `FATAL: database "mockwise" does not exist`:** stale classpath config. Run `./mvnw clean spring-boot:run`.  
> Local app data should use the **Mockwise-Local Supabase Postgres**, not a leftover `mockwise` database name.

## Package map

Root package: **`com.mockwise.backend`**

| Area | Path |
|------|------|
| Auth / security | `auth/` |
| Interview API | `interview/api/` |
| Interview domain/services | `interview/domain`, `interview/application` |
| Questions | `question/` |
| Submissions | `submission/` |
| Dashboard | `dashboard/` |
| Progress (seen questions) | `progress/` |
| Claude evaluation | `evaluation/` |
| Syntax checking | `codesyntax/` |
| Shared exceptions/utils | `common/` |

See [docs/architecture.md](docs/architecture.md) for layers and profiles.

## Tests

```bash
./mvnw test
```

Unit tests cover pure helpers (`RatingExtractor`, `AuthSupport`, `SyntaxCheckService`).  
`MockwiseBackendApplicationTests` loads the Spring context with the `test` profile (H2).

## Docker

```bash
docker build -t mockwise-backend .
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://... \
  -e SUPABASE_URL=... \
  -e SUPABASE_ANON_KEY=... \
  -e SUPABASE_SERVICE_KEY=... \
  -e CLAUDE_API_KEY=... \
  mockwise-backend
```

`prod` requires `SPRING_DATASOURCE_URL`, Supabase, and Claude env vars (no insecure defaults).

## Docs

- [Repository structure review](docs/repository-structure-review.md)
- [Architecture](docs/architecture.md)
- [Claude setup](docs/CLAUDE_SETUP.md)

## Required environment variables

| Variable | Purpose |
|----------|---------|
| `SPRING_PROFILES_ACTIVE` | `local` (default) / `prod` / `test` |
| `SPRING_DATASOURCE_URL` | JDBC URL (`prod` required; `local` defaults to `jdbc:postgresql://localhost:5432/mockwise_local`) |
| `SPRING_DATASOURCE_USERNAME` | DB user (local defaults to `$USER`) |
| `SPRING_DATASOURCE_PASSWORD` | DB password (often empty for local trust auth) |
| `SUPABASE_URL` | Supabase project URL (`prod` required) |
| `SUPABASE_ANON_KEY` | Supabase anon key (`prod` required) |
| `SUPABASE_SERVICE_KEY` | Supabase service role key (`prod` required) |
| `CLAUDE_API_KEY` | Anthropic API key (`prod` required; optional for local syntax-only work) |
| `JUDGE0_API_KEY` | Optional Judge0 key |
