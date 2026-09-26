# MockWise Backend

Spring Boot API for MockWise mock interviews: auth (Supabase), interview lifecycle, AI feedback (Claude), dashboard metrics, and syntax checks.

The API listens on **http://localhost:8080**. Start the frontend from `mockwise-frontend` in a second terminal and open **http://localhost:5173**.

## How to start

### Requirements

- **JDK 17 or newer.** `./mvnw` uses `JAVA_HOME` when it is set, otherwise `java` on your `PATH`. A start on this machine used Eclipse Temurin 26 because `JAVA_HOME` pointed at SDKMAN's `current`. The project compiles with `--release 17`, so JDK 17 is enough.
- The Maven Wrapper shipped in this repo (`./mvnw`). A separate Maven install is not required.
- A Supabase project for local Auth and Postgres. Use the **session pooler** connection (port **6543**). The direct host `db.<project-ref>.supabase.co:5432` is often IPv6-only, and the connection pool then fails to start.
- Optional: Python 3 and g++ for multi-language syntax checks.

### One-time configuration

From this repository, create the secrets file only when it is not already there. Do not copy over an existing file. That replaces a working database URL and API keys with the placeholders in the example.

```bash
cp -n src/main/resources/application-local-secrets.yml.example \
      src/main/resources/application-local-secrets.yml
```

Edit `application-local-secrets.yml`. That file is gitignored. `cp -n` prints a warning and leaves the file alone when it already exists.

| Key | Where to get it |
|-----|-----------------|
| `spring.datasource.url` | Supabase → Database → Connect → **Session** mode. JDBC URL on port 6543, with `sslmode=require` |
| `spring.datasource.username` | `postgres.<project-ref>` |
| `spring.datasource.password` | Database password |
| `supabase.url` | `https://<project-ref>.supabase.co` |
| `supabase.anon.key` | Project Settings → API |
| `supabase.service.key` | Project Settings → API (service role) |

`./scripts/run-local.sh` exits immediately if `application-local-secrets.yml` is missing.

The `local` profile loads that file over the defaults in `application-local.yml`. If you run Maven without the secrets file, the fallback database is `jdbc:postgresql://localhost:5432/mockwise_local`.

Use the **same** Supabase project in the frontend `.env` (anon key only):

```bash
VITE_SUPABASE_URL=https://<project-ref>.supabase.co
VITE_SUPABASE_ANON_KEY=<anon key>
VITE_API_BASE_URL=http://localhost:8080
```

Keep local and production on different Supabase projects so interviews do not cross environments.

### Start the API

```bash
./scripts/run-local.sh
```

The script selects the `local` profile and runs `./mvnw clean spring-boot:run`. The clean step matters: Maven leaves deleted config files under `target/classes`, and a stale file can force a database named `mockwise`.

Wait until the log contains both of these lines:

```text
Tomcat started on port 8080 (http) with context path '/'
Started MockwiseBackendApplication
```

The active profiles on a normal local start are `local` and `local-secrets`. Hikari then logs `HikariPool-local - Start completed.`

The same run, after the secrets file exists:

```bash
./mvnw clean spring-boot:run
```

Prefer `./scripts/run-local.sh`. It fails fast when the secrets file is missing.

### Check that it started

```bash
curl -sS http://127.0.0.1:8080/api/interview/test-auth
```

The local profile disables Supabase JWT checks (`mockwise.auth.disabled=true`), so a healthy process responds:

```json
{"status":"authenticated","user":"test@mockwise.local"}
```

A second check:

```bash
curl -sS -o /dev/null -w "%{http_code}\n" http://127.0.0.1:8080/api/codesyntax/languages
```

That prints `200`. `GET /` returns 404. There is no health page on `/` or `/health`.

Once the database is connected, dashboard metrics respond as well:

```bash
curl -sS http://127.0.0.1:8080/api/dashboard/metrics
```

An empty local database returns `"totalInterviews":0`.

### JDBC connection error on startup

`JDBCConnectionException: Unable to open JDBC Connection for DDL execution` / `The connection attempt failed` means Hibernate never reached Postgres. Read the `Caused by:` line under it.

If that line is `UnknownHostException: aws-0-REGION.pooler.supabase.com`, `application-local-secrets.yml` is still the example template. `aws-0-REGION` is not a real host. Replace `spring.datasource.url`, `username`, and `password` with a database you can reach.

On this laptop, Postgres is already running and the app data is in **`mockwise_local`** (not `mockwise_db`). Local sockets are `trust`, and the role is your macOS user, not `postgres`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://127.0.0.1:5432/mockwise_local
    username: sankalpbhagwat
    password: ""
```

Restart with `./scripts/run-local.sh` after saving that file.

### Stop

Press `Ctrl+C` in the terminal that is running Maven.

If port 8080 is already taken, the new process cannot bind. Find the listener and stop it, then start again:

```bash
lsof -nP -iTCP:8080 -sTCP:LISTEN
```

### Open the app with the frontend

In `mockwise-frontend`, run `npm run dev` and open **http://localhost:5173**.

The API allowlist includes `http://localhost:5173`. Opening **http://127.0.0.1:5173** sends a different browser origin, and the API responds `Invalid CORS request`.

## Configuration (profiles)

| Profile | File | Purpose |
|---------|------|---------|
| **`local`** (default) | `application-local.yml` | Laptop. Also loads `application-local-secrets.yml` when that file exists |
| **`prod`** | `application-prod.yml` | Production. All secrets come from the environment |
| **`test`** | `src/test/resources/application-test.yml` | Automated tests (H2) |

Shared defaults live in `application.yml`. There is no `dev` profile. Use **`local`** for development.

## Package map

Root package: **`com.mockwise.backend`**

| Layer | Path |
|-------|------|
| HTTP | `controller/<feature>/` |
| Use cases | `service/<feature>/` |
| Persistence (entities and Spring Data) | `repository/<feature>/` |
| Security and app config | `config/` |
| API errors | `exception/` |

Features today: `interview`, `question`, `submission`, `dashboard`, `progress`, `evaluation`, `codesyntax`, `auth`.

Standards and the product map: [CLAUDE.md](CLAUDE.md). Profiles: [docs/architecture.md](docs/architecture.md).

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
- [Local API endpoints](docs/local-api-endpoints.md)

## Required environment variables

These override the YAML files. The local secrets file is the normal way to set them on a laptop. Production sets them in the host environment.

| Variable | Purpose |
|----------|---------|
| `SPRING_PROFILES_ACTIVE` | `local` (default) / `prod` / `test` |
| `SPRING_DATASOURCE_URL` | JDBC URL (`prod` required; `local` defaults to `jdbc:postgresql://localhost:5432/mockwise_local` unless the secrets file sets it) |
| `SPRING_DATASOURCE_USERNAME` | DB user (`local` YAML default is `postgres`; the secrets file should use `postgres.<project-ref>` for Supabase) |
| `SPRING_DATASOURCE_PASSWORD` | DB password |
| `SUPABASE_URL` | Supabase project URL (`prod` required) |
| `SUPABASE_ANON_KEY` | Supabase anon key (`prod` required) |
| `SUPABASE_SERVICE_KEY` | Supabase service role key (`prod` required) |
| `CLAUDE_API_KEY` | Anthropic API key (`prod` required; optional for local syntax-only work) |
| `JUDGE0_API_KEY` | Optional Judge0 key |
| `MOCKWISE_AUTH_DISABLED` | Set `false` to turn local auth back on. The `local` profile defaults this to on (auth skipped) |
