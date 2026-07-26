# Local API endpoints

Use when running the backend against the local database (`SPRING_PROFILES_ACTIVE=local`, which is the default).

**Base URL:** `http://127.0.0.1:8080`

**Local JWT** (no Supabase required — token is decoded only; signature is not verified):

```text
eyJhbGciOiAibm9uZSIsICJ0eXAiOiAiSldUIn0.eyJzdWIiOiAibG9jYWwtdXNlci0wMDEiLCAiZW1haWwiOiAibG9jYWxAdGVzdC5jb20ifQ.bG9jYWw
```

Regenerate anytime:

```bash
./scripts/local-jwt.sh
```

All authenticated requests need:

```http
Authorization: Bearer <token>
```

---

## Auth

### `GET /api/interview/test-auth`

```bash
curl -sS -w "\nHTTP %{http_code}\n" \
  -H "Authorization: Bearer eyJhbGciOiAibm9uZSIsICJ0eXAiOiAiSldUIn0.eyJzdWIiOiAibG9jYWwtdXNlci0wMDEiLCAiZW1haWwiOiAibG9jYWxAdGVzdC5jb20ifQ.bG9jYWw" \
  http://127.0.0.1:8080/api/interview/test-auth
```

---

## Interview

### `POST /api/interview/start`

```bash
curl -sS -w "\nHTTP %{http_code}\n" \
  -X POST http://127.0.0.1:8080/api/interview/start \
  -H "Authorization: Bearer eyJhbGciOiAibm9uZSIsICJ0eXAiOiAiSldUIn0.eyJzdWIiOiAibG9jYWwtdXNlci0wMDEiLCAiZW1haWwiOiAibG9jYWxAdGVzdC5jb20ifQ.bG9jYWw" \
  -H "Content-Type: application/json" \
  -d '{"difficulty":"EASY","numQuestions":1,"timeMinutes":30}'
```

Body fields:

| Field | Example | Notes |
|-------|---------|--------|
| `difficulty` | `EASY` / `MEDIUM` / `HARD` | Required |
| `numQuestions` | `1` | Positive integer |
| `timeMinutes` | `30` | Positive integer |

From the response, use `interview.id` and `questions[0].id` for submit/feedback.

### `POST /api/interview/{interviewId}/submit`

```bash
curl -sS -w "\nHTTP %{http_code}\n" \
  -X POST http://127.0.0.1:8080/api/interview/90af7876-6cc3-43da-8e68-534f14012d45/submit \
  -H "Authorization: Bearer eyJhbGciOiAibm9uZSIsICJ0eXAiOiAiSldUIn0.eyJzdWIiOiAibG9jYWwtdXNlci0wMDEiLCAiZW1haWwiOiAibG9jYWxAdGVzdC5jb20ifQ.bG9jYWw" \
  -H "Content-Type: application/json" \
  -d '{"submissions":[{"questionId":"32d459b4-41f3-437a-a2fd-30c6246086a3","code":"int add(int a,int b){return a+b;}","language":"java","timeComplexity":"O(1)","spaceComplexity":"O(1)"}]}'
```

Replace `{interviewId}` and `questionId` with values from **start**. Example IDs above are samples only.

### `GET /api/interview/{interviewId}/feedback`

```bash
curl -sS -w "\nHTTP %{http_code}\n" \
  -H "Authorization: Bearer eyJhbGciOiAibm9uZSIsICJ0eXAiOiAiSldUIn0.eyJzdWIiOiAibG9jYWwtdXNlci0wMDEiLCAiZW1haWwiOiAibG9jYWxAdGVzdC5jb20ifQ.bG9jYWw" \
  http://127.0.0.1:8080/api/interview/4044e4bc-ea11-42ce-9b0b-2eae8d2f1253/feedback
```

### `POST /api/interview/check-syntax`

```bash
curl -sS -w "\nHTTP %{http_code}\n" \
  -X POST http://127.0.0.1:8080/api/interview/check-syntax \
  -H "Authorization: Bearer eyJhbGciOiAibm9uZSIsICJ0eXAiOiAiSldUIn0.eyJzdWIiOiAibG9jYWwtdXNlci0wMDEiLCAiZW1haWwiOiAibG9jYWxAdGVzdC5jb20ifQ.bG9jYWw" \
  -H "Content-Type: application/json" \
  -d '{"language":"java","code":"public int add(int a, int b) { return a + b; }"}'
```

Supported languages: `java`, `python`, `cpp`.

### `GET /api/interview/ongoing`

```bash
curl -sS -w "\nHTTP %{http_code}\n" \
  -H "Authorization: Bearer eyJhbGciOiAibm9uZSIsICJ0eXAiOiAiSldUIn0.eyJzdWIiOiAibG9jYWwtdXNlci0wMDEiLCAiZW1haWwiOiAibG9jYWxAdGVzdC5jb20ifQ.bG9jYWw" \
  http://127.0.0.1:8080/api/interview/ongoing
```

### `GET /api/interview/{interviewId}/validate`

```bash
curl -sS -w "\nHTTP %{http_code}\n" \
  -H "Authorization: Bearer eyJhbGciOiAibm9uZSIsICJ0eXAiOiAiSldUIn0.eyJzdWIiOiAibG9jYWwtdXNlci0wMDEiLCAiZW1haWwiOiAibG9jYWxAdGVzdC5jb20ifQ.bG9jYWw" \
  http://127.0.0.1:8080/api/interview/b42ad138-7d9a-4a63-8338-a8a710eb0f2e/validate
```

### `GET /api/interview/questions`

```bash
curl -sS -w "\nHTTP %{http_code}\n" \
  -H "Authorization: Bearer eyJhbGciOiAibm9uZSIsICJ0eXAiOiAiSldUIn0.eyJzdWIiOiAibG9jYWwtdXNlci0wMDEiLCAiZW1haWwiOiAibG9jYWxAdGVzdC5jb20ifQ.bG9jYWw" \
  "http://127.0.0.1:8080/api/interview/questions?difficulty=EASY&count=3"
```

### `GET /api/interview/questions/{questionId}/stub`

```bash
curl -sS -w "\nHTTP %{http_code}\n" \
  -H "Authorization: Bearer eyJhbGciOiAibm9uZSIsICJ0eXAiOiAiSldUIn0.eyJzdWIiOiAibG9jYWwtdXNlci0wMDEiLCAiZW1haWwiOiAibG9jYWxAdGVzdC5jb20ifQ.bG9jYWw" \
  "http://127.0.0.1:8080/api/interview/questions/05564f0b-97b5-4aff-8686-7ef5450aec4e/stub?language=java"
```

### `GET /api/interview/optimal-code`

```bash
curl -sS -w "\nHTTP %{http_code}\n" \
  -H "Authorization: Bearer eyJhbGciOiAibm9uZSIsICJ0eXAiOiAiSldUIn0.eyJzdWIiOiAibG9jYWwtdXNlci0wMDEiLCAiZW1haWwiOiAibG9jYWxAdGVzdC5jb20ifQ.bG9jYWw" \
  "http://127.0.0.1:8080/api/interview/optimal-code?questionId=05564f0b-97b5-4aff-8686-7ef5450aec4e&language=java"
```

### `POST /api/interview/{interviewId}/generate-feedback`

```bash
curl -sS -w "\nHTTP %{http_code}\n" \
  -X POST http://127.0.0.1:8080/api/interview/b42ad138-7d9a-4a63-8338-a8a710eb0f2e/generate-feedback \
  -H "Authorization: Bearer eyJhbGciOiAibm9uZSIsICJ0eXAiOiAiSldUIn0.eyJzdWIiOiAibG9jYWwtdXNlci0wMDEiLCAiZW1haWwiOiAibG9jYWxAdGVzdC5jb20ifQ.bG9jYWw"
```

---

## Dashboard

### `GET /api/dashboard/metrics`

```bash
curl -sS -w "\nHTTP %{http_code}\n" \
  -H "Authorization: Bearer eyJhbGciOiAibm9uZSIsICJ0eXAiOiAiSldUIn0.eyJzdWIiOiAibG9jYWwtdXNlci0wMDEiLCAiZW1haWwiOiAibG9jYWxAdGVzdC5jb20ifQ.bG9jYWw" \
  http://127.0.0.1:8080/api/dashboard/metrics
```

### `GET /api/dashboard/progress`

```bash
curl -sS -w "\nHTTP %{http_code}\n" \
  -H "Authorization: Bearer eyJhbGciOiAibm9uZSIsICJ0eXAiOiAiSldUIn0.eyJzdWIiOiAibG9jYWwtdXNlci0wMDEiLCAiZW1haWwiOiAibG9jYWxAdGVzdC5jb20ifQ.bG9jYWw" \
  http://127.0.0.1:8080/api/dashboard/progress
```

---

## Endpoint index

| Method | Path | Auth |
|--------|------|------|
| GET | `/api/interview/test-auth` | Yes |
| POST | `/api/interview/start` | Yes |
| POST | `/api/interview/{interviewId}/submit` | Yes |
| GET | `/api/interview/{interviewId}/feedback` | Yes |
| POST | `/api/interview/{interviewId}/generate-feedback` | Yes |
| GET | `/api/interview/{interviewId}/validate` | Yes |
| GET | `/api/interview/ongoing` | Yes |
| GET | `/api/interview/questions` | Yes |
| GET | `/api/interview/questions/{questionId}/stub` | Yes |
| GET | `/api/interview/optimal-code` | Yes |
| POST | `/api/interview/check-syntax` | Yes |
| GET | `/api/dashboard/metrics` | Yes |
| GET | `/api/dashboard/progress` | Yes |

---

## Local run reminder

```bash
cd /Users/sankalpbhagwat/Desktop/MockWise/Mockwise-BE
# Profile "local" is the default; export only if you overrode it.
export SPRING_PROFILES_ACTIVE=local
./mvnw spring-boot:run
```

- DB: `mockwise_local` on `localhost:5432` (create with `createdb mockwise_local` if needed)
- Config: `src/main/resources/application-local.yml` (committed safe defaults)
- Optional secrets: `application-local-secrets.yml` (gitignored; see `.example`)
- Without `Authorization`, most APIs return **401/403**

---

## Supported languages (syntax check)

### `GET /api/interview/supported-languages`

```bash
curl -sS -w "\nHTTP %{http_code}\n" \
  -H "Authorization: Bearer eyJhbGciOiAibm9uZSIsICJ0eXAiOiAiSldUIn0.eyJzdWIiOiAibG9jYWwtdXNlci0wMDEiLCAiZW1haWwiOiAibG9jYWxAdGVzdC5jb20ifQ.bG9jYWw" \
  http://127.0.0.1:8080/api/interview/supported-languages
```

### `GET /api/codesyntax/languages`

```bash
curl -sS -w "\nHTTP %{http_code}\n" \
  -H "Authorization: Bearer eyJhbGciOiAibm9uZSIsICJ0eXAiOiAiSldUIn0.eyJzdWIiOiAibG9jYWwtdXNlci0wMDEiLCAiZW1haWwiOiAibG9jYWxAdGVzdC5jb20ifQ.bG9jYWw" \
  http://127.0.0.1:8080/api/codesyntax/languages
```

### `POST /api/codesyntax/check` (structured)

```bash
curl -sS -w "\nHTTP %{http_code}\n" \
  -X POST http://127.0.0.1:8080/api/codesyntax/check \
  -H "Authorization: Bearer eyJhbGciOiAibm9uZSIsICJ0eXAiOiAiSldUIn0.eyJzdWIiOiAibG9jYWwtdXNlci0wMDEiLCAiZW1haWwiOiAibG9jYWxAdGVzdC5jb20ifQ.bG9jYWw" \
  -H "Content-Type: application/json" \
  -d '{"language":"javascript","code":"const x = 1;"}'
```
