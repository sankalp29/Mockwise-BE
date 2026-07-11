# Database migrations

Flyway is on the classpath but **disabled by default** (`spring.flyway.enabled=false`)
because this project historically used `spring.jpa.hibernate.ddl-auto=update`.

## Existing files

- `V3__add_user_question_seen_table.sql` — historical change for `user_question_seen`.

There is no V1/V2 baseline checked in yet. Before enabling Flyway in production:

1. Generate a baseline from the live schema (or author `V1__baseline.sql`).
2. `flyway baseline` on existing environments.
3. Set `FLYWAY_ENABLED=true` and `spring.jpa.hibernate.ddl-auto=validate` (see `application-prod.yml`).
