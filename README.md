# Digital Health App

A digital training package that prepares mental health professionals to safely deliver
**Simplicity**, a digital therapeutic tool, to people living with serious mental illness such as
schizophrenia and psychosis.

Clinicians work through training modules authored by their organisation's administrators, prove
their understanding by answering quiz questions, and keep a private journal of practice
reflections.

## Status

Phase 1 — foundation, identity, and teams. See
[the Phase 1 design spec](docs/superpowers/specs/2026-08-29-digital-health-app-phase1-design.md)
for the architecture and the reasoning behind it.

| Phase | Scope | State |
| --- | --- | --- |
| 1 | Auth, profiles, organisations, teams, invitations, deployed AWS environment | In progress |
| 2 | Module authoring, quizzes, Dashboard and Learn tabs | Not started |
| 3 | Reflect journal with search | Not started |
| 4 | AI assistant over the training content | Not started |
| 5 | Native iOS and Android clients | Not started |

## Layout

| Directory | Contents |
| --- | --- |
| [backend/](backend/) | Spring Boot API — Java 17, Gradle, Spring Boot 4.1 |
| [web/](web/) | Angular web client |
| [infra/](infra/) | CloudFormation and SAM templates |
| [api-contract/](api-contract/) | Generated `openapi.yaml` and client generator configuration |
| [docs/](docs/) | Design specifications |
| `ios/`, `android/` | Native clients, Phase 5 |

## Running locally

Start Postgres and Valkey:

```bash
docker compose up -d
```

Run the API on port 8080:

```bash
cd backend
./gradlew bootRun
```

Alternatively run `TestTrainingApplication` from your IDE, which starts the same application with
throwaway Testcontainers instances and needs no `docker compose`.

Run the web client on port 4200:

```bash
cd web
nvm use          # Node 24.20.0, pinned in .nvmrc
npm install
npm start
```

## Testing

```bash
cd backend && ./gradlew test    # requires a running Docker daemon for Testcontainers
cd web && npm test
```

Backend integration tests run against real PostgreSQL and Valkey containers rather than an
in-memory substitute, so the Flyway migrations and Postgres-specific SQL are exercised on every
run.

## Requirements

- Java 17 (Amazon Corretto)
- Node 24.20.0 (see `.nvmrc`)
- Docker, for local infrastructure and the test suite
