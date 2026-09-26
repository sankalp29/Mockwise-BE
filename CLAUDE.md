# MockWise backend

This file is the source of truth for what the backend is and how new code must be shaped. Read it before adding a feature.

## Product

MockWise is a timed mock-interview product. The backend in this repository is the API for **data-structures and algorithms** interviews.

What it does today:

- Starts a timed interview, assigns questions the user has not already seen, and tracks the session (`IN_PROGRESS`, `COMPLETED`, `ABANDONED`).
- Accepts code submissions for the questions that belong to that interview.
- Checks syntax for several languages on the machine running the API.
- Asks Claude for written feedback and stores it on each submission.
- Rolls completed interviews into dashboard metrics and a progress series.
- Authenticates callers with a Supabase JWT. The `local` profile can skip that check and use a guest user.

What it does not do yet:

- System-design interviews. When that offering is added, it is a new feature under the same three layers. It must not be folded into the DSA interview classes.

The HTTP API stays under `/api/interview`, `/api/questions`, `/api/dashboard`, and `/api/codesyntax`. The React client lives in the separate `mockwise-frontend` repository and calls this API at `VITE_API_BASE_URL`.

Local run, profiles, and the database setup are in [README.md](README.md).

## Code architecture

Root package: `com.mockwise.backend`.

The first folder is the role. The second folder is the feature.

```text
com.mockwise.backend
  controller/          HTTP. One package per feature. DTOs live in dto/
    interview/
    dashboard/
    codesyntax/
  service/             Use cases. One package per feature.
    interview/         start, submit, ownership, feedback orchestration
    question/          selection, stubs, optimal solutions
    submission/        (persistence only today; no service yet)
    dashboard/         metrics, progress, aggregate updates
    progress/          questions the user has already seen
    evaluation/        Claude transport. Each interview type owns its prompt.
    codesyntax/        language toolchains (strategy + registry + facade)
    auth/              Supabase user lookup
  repository/          JPA entities and Spring Data interfaces, per feature
    interview/
    question/
    submission/
    dashboard/
    progress/
  config/              security filter, CORS, Supabase WebClient, async executor
  exception/           API exceptions and GlobalExceptionHandler
```

Request flow is one direction:

```text
controller  ->  service  ->  repository
```

Nothing below may import the layer above it. A service does not import `controller`. A repository does not import `service` or `controller`.

### Controller

A controller is the HTTP edge for one feature.

- It authenticates the caller, checks that the JSON body is present and well-shaped, calls a service, and writes the HTTP response.
- Its request and response types live in `controller/<feature>/dto`. Those types describe the JSON contract. They do not hold use-case rules. A request DTO may use an enum that already lives on an entity when that enum is the wire value, as `Question.Difficulty` does on start. It does not take the entity type itself, and it does not follow entity relations.
- Before the service call, map a request DTO into a type the service owns. `SubmittedSolution` is that type for interview submit. `SubmissionRequest` stays in the controller.
- After the service call, map a service outcome into a response DTO. `FeedbackRequestOutcome` is the service result (`STARTED`, `READY`). `GenerateFeedbackResponse` is the JSON the client sees.
- It does not inject a repository, open a transaction, or call Claude.
- It may read an entity a service returned, only to copy fields into a response DTO. `QuestionResponse.from` is that copy. Do not add a new endpoint that returns an entity. Start, validate, and feedback still serialize `Interview`, `Question`, and `UserSubmission`. Leave those until they are mapped. Do not copy that pattern.

### Service

A service owns one use case and the transaction around it.

- It depends on repositories and other services. It accepts and returns its own types, or entities it loaded. `SubmittedSolution` and `FeedbackRequestOutcome` live in `service/interview` for that reason.
- It does not import `controller`, read `HttpServletRequest`, or build a response DTO.
- It does not choose HTTP status lines. It reports failure by throwing the exceptions in `exception/`. The handler turns those into status codes.
- It may call another service. It does not reach past a service to a second feature's repository when that feature already has a service for the question.

### Repository

A repository is a Spring Data interface plus the entity it loads. Both stay in `repository/<feature>`.

- The entity is the persistence model: table, columns, and relations. It is not the JSON body and it is not a use-case input.
- A repository has no business rules, no HTTP types, and no calls into `service` or `controller`.
- Moving an entity to another package does not change `@Table` or `@Column`.

`config` and `exception` are the only packages that are not feature folders. Do not put business rules in them. `exception` types are the shared failure signal a service throws and a controller lets propagate.

### Where a new interview style goes

A system-design interview gets its own feature name, for example `systemdesign`, with:

- `controller/systemdesign/`
- `service/systemdesign/`
- `repository/systemdesign/`

Reuse `config`, `exception`, and `service/auth`. Do not add system-design fields onto `Interview`, `Question`, or `InterviewController`.

### Syntax checking

`service/codesyntax` is a strategy. `LanguageToolchain` is the interface. Each language is a `@Component` in `languages/`. `LanguageToolchainRegistry` finds one by id or alias. `SyntaxCheckFacade` runs it in a temp workspace. Add a language by adding a `ProgrammingLanguage` constant and a toolchain class. Do not edit a switch statement. The constant's id is the database and JSON value.

### Model evaluation

`EvaluationSpec` is the strategy. `CodingEvaluation` and `DesignEvaluation` each own that style's prompt and fallback JSON. `ClaudeService.complete` sends the prompt with the shared model and token limit from `claude.model` and `claude.max-tokens`. Add a style by adding a class. Do not add a prompt method to `ClaudeService`.

## Rules

Follow these on every change.

### Layering

- The arrow is `controller -> service -> repository`. Do not import upward.
- Do not inject a repository into a controller.
- Do not return a repository from a service just to let the controller query it.
- Keep HTTP DTOs in `controller/<feature>/dto`. Keep use-case inputs and outcomes in `service/<feature>`.
- Do not use an entity as the request body. Do not return an entity from a new endpoint.

### SOLID

- One class, one reason to change. Interview start/submit stays in `InterviewService`. Feedback gating stays in `FeedbackService`. Claude I/O stays in `ClaudeService`. A new interview style adds an `EvaluationSpec`; it does not add a method to `ClaudeService`. Model name and token limit stay on `ClaudeService`.
- Open for a new language or a new interview style by adding a class, not by editing a central conditional.
- Depend on the service or repository you need, not on a class that happens to be nearby.
- Small interfaces. `LanguageToolchain` is the model: one method the caller needs, not a grab bag.
- `FeedbackGenerationWorker` does not check ownership. Callers prove ownership on the request thread before scheduling it. Do not move that check into the async method.

### DRY

- One query, one repository method. Do not add a second method that selects the same rows under a different name.
- Shared calculations (score rounding, "has this user seen this question") live in one service method.
- Do not copy a block into a second feature. Extract it if both features need it, and only if the behavior is actually the same.

### Names

- Variables, parameters, and fields are `camelCase`.
- Types and components are `PascalCase`: `InterviewController`, `InterviewService`, `InterviewRepository`.
- Do not suffix a name with a data type or a collection type. Use `questions`, not `questionList`. Use `alreadySeen`, not `existingSeenSet`. Use `aggregate`, not `aggMap`.
- `Id` on a domain identifier is fine (`questionId`). A Java type name at the end is not (`difficultyString`, `countInteger`).
- Names say what the value is for. Prefer `requested` over `submissionReq`, `interview` over `iv`.

### Tests

- New service behavior gets a unit test in the matching `src/test/java/com/mockwise/backend/service/<feature>/` package.
- Controller security and status codes get a `@SpringBootTest` or slice test when the behavior is about HTTP, not about a calculation.
- Do not delete or disable a test to make a change compile. Update it when the behavior change is intentional.
- Run `./mvnw test` before considering a change done.

### Persistence

- Table and column names stay stable. Moving a class to a new package does not change `@Table` or `@Column`.
- Local profile uses `ddl-auto=update`. Production uses `validate`. Do not turn Flyway on until a baseline exists (`src/main/resources/db/migration/README.md`).

## Debugging map

| You are looking at | Start here |
|--------------------|------------|
| `POST /api/interview/start` or `/submit` | `controller/interview/InterviewController`, then `service/interview/InterviewService` |
| Feedback never appears | `service/interview/FeedbackService`, then `FeedbackGenerationWorker`, then `service/evaluation/ClaudeService` |
| Wrong question was assigned | `service/question/QuestionSelectionService` and `service/progress/UserQuestionSeenService` |
| Dashboard numbers | `service/dashboard/DashboardService` |
| Syntax check | `service/codesyntax/SyntaxCheckFacade` and the toolchain for that language |
| 401 / 403 | `config/SupabaseAuthFilter`, `config/SecurityConfig`, `exception/GlobalExceptionHandler` |
