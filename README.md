# Ektrepha API

Backend service built with Java 17 and Spring Boot.

## Tech Stack

- Java 17 (Amazon Corretto or Eclipse Temurin recommended)
- Spring Boot 4.1.1
- Maven
- PostgreSQL
- Spring Data JPA, Spring Web, Spring Validation, Spring Boot Actuator
- Spring Security (OAuth2 Client)
- Lombok

## Prerequisites

- JDK 17
- PostgreSQL 16 (running locally, or reachable via `DB_URL`)
- Maven is not required — this repo includes the Maven Wrapper (`./mvnw`)

## Project Structure

```
src/main/java/com/ektrepha/
├── config/          # Spring configuration classes (security, CORS, beans, etc.)
├── controller/      # REST controllers
├── service/         # Business logic
├── repository/      # Spring Data JPA repositories
├── model/           # JPA entities
├── dto/             # Request/response objects
├── exception/        # Custom exceptions + global exception handler
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

### Local (`dev` profile)

Install and start PostgreSQL:

```bash
brew install postgresql@16
brew services start postgresql@16
```

Create the role and database:

```bash
export PATH="/opt/homebrew/opt/postgresql@16/bin:$PATH"
psql -d postgres -c "CREATE ROLE postgres LOGIN SUPERUSER PASSWORD 'yourpassword';"
createdb -O postgres ektrepha_api
```

The `dev` profile connects to `jdbc:postgresql://localhost:5432/ektrepha_api` as user `postgres`, with the password read from the `DB_PASSWORD` environment variable.

### Stage / Prod

The `stage` and `prod` profiles read the full connection from environment variables — no hardcoded host, so they can point at any Postgres instance (RDS, Docker, etc.):

- `DB_URL` — full JDBC URL, e.g. `jdbc:postgresql://<host>:5432/ektrepha_api`
- `DB_USERNAME`
- `DB_PASSWORD`

## 3. Configure Environment Variables

| Variable | Used by | Example |
|---|---|---|
| `DB_PASSWORD` | `dev` | `postgres_dev_pw` |
| `DB_URL` | `stage`, `prod` | `jdbc:postgresql://db-host:5432/ektrepha_api` |
| `DB_USERNAME` | `stage`, `prod` | `ektrepha_app` |
| `DB_PASSWORD` | `stage`, `prod` | `<secret>` |

Set them in your shell before running, e.g.:

```bash
export DB_PASSWORD=postgres_dev_pw
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
| `dev` | hardcoded `localhost:5432/ektrepha_api` | `update` | health, info |
| `stage` | env vars (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`) | `validate` | health, info, metrics |
| `prod` | env vars (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`) | `validate` | health, info |
