# Ektrepha API

Backend service built with Java 17 and Spring Boot.

## Tech Stack

- Java 17 (Amazon Corretto or Eclipse Temurin recommended)
- Spring Boot 4.1.1
- Maven
- PostgreSQL
- Spring Data JPA, Spring Web, Spring Validation, Spring Boot Actuator
- Spring Security (JWT auth + OAuth2 Client), BCrypt, JJWT
- Liquibase (schema migrations)
- Lombok

## Prerequisites

- JDK 17
- PostgreSQL 16 and Redis (run locally, via Docker Compose, or reachable via `DB_URL`)
- Maven is not required — this repo includes the Maven Wrapper (`./mvnw`)
- Docker, if using `docker-compose.yml` instead of native installs

## Project Structure

Cross-cutting code (config, generic exception handling, standalone controllers
like health/version) is organized by layer at the top level. A bounded domain
with its own entities, endpoints, and business logic gets its own
feature-package instead — `auth` is the first example, and `parent`, `nanny`,
`booking`, etc. are expected to follow the same shape as they're built.

```
src/main/java/com/ektrepha/
├── config/          # Spring configuration classes (security, CORS, beans, etc.)
├── controller/      # REST controllers not owned by a feature module (health, version, samples)
├── service/         # Shared business logic (currently empty — reserved for cross-module logic)
├── repository/      # Shared Spring Data JPA repositories (currently empty)
├── model/           # Shared JPA entities (currently empty)
├── dto/             # Shared request/response objects (currently empty)
├── exception/       # Global exception handler (delegates to each module's own exception types)
├── auth/            # Authentication & identity feature module
│   ├── controller/  #   AuthController (register, login, refresh, logout, forgot/reset password)
│   ├── service/      #   AuthService, EmailService
│   ├── dto/          #   Request/response records for the auth endpoints
│   ├── model/        #   User, Role, RefreshToken, PasswordResetToken (JPA entities)
│   ├── repository/   #   Spring Data JPA repositories for the entities above
│   ├── security/      #   JwtService, JwtAuthenticationFilter
│   └── exception/     #   Auth-specific exceptions (EmailAlreadyExists, InvalidCredentials, InvalidToken)
└── EktrephaApplication.java
src/main/resources/
├── application.yml        # base config, sets default active profile
├── application-dev.yml    # local development
├── application-stage.yml  # staging
└── application-prod.yml   # production
```

## 1. Clone and Install Dependencies

```bash
git clone <repo-url>
cd ektrepha_api
./mvnw -q -DskipTests compile
```

## 2. Set Up the Database

### Local (`dev` profile) — via Docker Compose (recommended)

```bash
export DB_PASSWORD=Ektrepha
docker compose up -d
```

Starts Postgres 16 on `5432` and Redis 7 on `6379`, matching the `dev` profile's expected credentials.

### Local (`dev` profile) — native install

```bash
brew install postgresql@16
brew services start postgresql@16
export PATH="/opt/homebrew/opt/postgresql@16/bin:$PATH"
psql -d postgres -c "CREATE ROLE postgres LOGIN SUPERUSER PASSWORD 'yourpassword';"
createdb -O postgres ektrepha_api
```

Either way, the `dev` profile connects to `jdbc:postgresql://localhost:5432/ektrepha_api` as user `postgres`, with the password read from the `DB_PASSWORD` environment variable.

### Schema migrations

Schema is managed entirely by Liquibase (`src/main/resources/db/changelog/`) — Hibernate's `ddl-auto` is `validate` on every profile, never `update`. Add a new `NNN-description.sql` file under `db/changelog/changes/`, wrap it in a matching changeset YAML, and include that YAML from `db.changelog-master.yaml`; migrations run automatically on app startup.

### Stage / Prod

The `stage` and `prod` profiles read the full connection from environment variables — no hardcoded host, so they can point at any Postgres instance (RDS, Docker, etc.):

- `DB_URL` — full JDBC URL, e.g. `jdbc:postgresql://<host>:5432/ektrepha_api`
- `DB_USERNAME`
- `DB_PASSWORD`

## 3. Configure Environment Variables

| Variable | Used by | Example |
|---|---|---|
| `DB_PASSWORD` | `dev` | `Ektrepha` |
| `DB_URL` | `stage`, `prod` | `jdbc:postgresql://db-host:5432/ektrepha_api` |
| `DB_USERNAME` | `stage`, `prod` | `ektrepha_app` |
| `DB_PASSWORD` | `stage`, `prod` | `<secret>` |

Set them in your shell before running, e.g.:

```bash
export DB_PASSWORD=Ektrepha
```

## 4. Run the App

Defaults to the `dev` profile (set in `application.yml`):

```bash
./mvnw spring-boot:run
```

Run against a different environment:

```bash
SPRING_PROFILES_ACTIVE=stage ./mvnw spring-boot:run
SPRING_PROFILES_ACTIVE=prod ./mvnw spring-boot:run
```

## 5. Verify

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

## 6. Run Tests

```bash
./mvnw test
```

## Environments Summary

| Profile | Datasource | `ddl-auto` | Actuator endpoints |
|---|---|---|---|
| `dev` | hardcoded `localhost:5432/ektrepha_api` | `validate` | health, info |
| `stage` | env vars (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`) | `validate` | health, info, metrics |
| `prod` | env vars (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`) | `validate` | health, info |

## API Conventions

All application endpoints are versioned under `/api/v1/...`. Actuator (`/actuator/...`) stays unversioned since it's operational, not part of the public contract. See [`docs/adr/0001-api-gateway-and-versioning.md`](docs/adr/0001-api-gateway-and-versioning.md) for the reasoning, including the reverse-proxy choice.

## CI

Every push and PR against `master` runs `.github/workflows/ci.yml`: builds against JDK 17 and runs the full test suite against a real Postgres 16 service container.
