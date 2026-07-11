# MockWise Backend

Spring Boot API for MockWise mock interviews: auth (Supabase), interview lifecycle, AI feedback (Claude), dashboard metrics, and syntax checks.

## Requirements

- Java 17+
- Maven Wrapper (`./mvnw`) included
- PostgreSQL (local or Supabase) for `dev` / `prod`
- Optional: Python 3 + g++ for multi-language syntax checks

## Quick start

```bash
# 1. Configure secrets (pick one)
export SUPABASE_URL=...
export SUPABASE_ANON_KEY=...
export SUPABASE_SERVICE_KEY=...
export CLAUDE_API_KEY=...
export SPRING_DATASOURCE_URL=jdbc:postgresql://...

# Or copy the local example:
# cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
# then run with: --spring.profiles.active=dev,local

# 2. Run
./mvnw spring-boot:run
```

API base: `http://localhost:8080`

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
  -e SPRING_DATASOURCE_URL=... \
  -e SUPABASE_URL=... \
  -e SUPABASE_ANON_KEY=... \
  -e SUPABASE_SERVICE_KEY=... \
  -e CLAUDE_API_KEY=... \
  mockwise-backend
```

## Docs

- [Repository structure review](docs/repository-structure-review.md)
- [Architecture](docs/architecture.md)
- [Claude setup](docs/CLAUDE_SETUP.md)

## Required environment variables

| Variable | Purpose |
|----------|---------|
| `SPRING_DATASOURCE_URL` | JDBC URL (prod required) |
| `SUPABASE_URL` | Supabase project URL |
| `SUPABASE_ANON_KEY` | Supabase anon key |
| `SUPABASE_SERVICE_KEY` | Supabase service role key |
| `CLAUDE_API_KEY` | Anthropic API key |
| `JUDGE0_API_KEY` | Optional Judge0 key |
| `SPRING_PROFILES_ACTIVE` | `dev` / `prod` / `test` |
