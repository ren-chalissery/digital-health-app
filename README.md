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

Start Postgres, Valkey, and the local AWS emulator, then provision the Cognito pool and SES sender:

```bash
docker compose up -d
./local/bootstrap.sh
```

`bootstrap.sh` writes `backend/.env.local` and `web/public/config.json`. Both are gitignored and
both need rewriting after `docker compose down`, because the emulator keeps its state in memory.
Running it again is harmless: an existing pool is reused rather than duplicated.

Run the API on port 8080:

```bash
cd backend
set -a && source .env.local && set +a
./gradlew bootRun
```

Alternatively run `TestTrainingApplication` from your IDE, which starts the same application with
throwaway Testcontainers instances and needs no `docker compose`. Authentication will not work in
that mode, because there is no Cognito for it to talk to.

Run the web client on port 4200:

```bash
cd web
nvm use          # Node 24.20.0, pinned in .nvmrc
npm install
npm start
```

You can then sign up with any address. Confirmation codes and invitation emails go to the emulator
instead of an inbox — read them with:

```bash
curl -s localhost:4566/_aws/ses | jq '.messages[-1]'
```

### Local AWS

[Floci](https://floci.io) stands in for Cognito and SES on port 4566. It is a drop-in LocalStack
replacement, MIT licensed, and needs no account or auth token.

Postgres and Valkey are deliberately *not* routed through it. Floci emulates RDS and ElastiCache by
starting those same engines in containers, so going via its control plane would add a hop to reach
what `docker compose` already provides directly.

Two things it gets wrong, both of which have bitten us:

- Its access tokens carry an `email` claim and put the address in `username`. A real pool that
  identifies users by email does neither, which is why the application resolves the address through
  `GetUser` instead of reading the token. A test that trusts those claims will pass here and fail in
  production.
- `GetUser` omits `email_verified` even for a confirmed user in an auto-verifying pool, where
  Cognito returns `"true"`. `AwsContractTest` sets the attribute explicitly rather than relax the
  check.

Applying the CloudFormation stacks against it does not work either: `network.yaml` and `web.yaml`
create cleanly, but `data.yaml` fails because `Fn::Split` over `Fn::ImportValue` goes unresolved,
and `auth.yaml` fails inside the emulator. The stacks are checked with `cfn-lint` instead.

## Testing

```bash
cd backend && ./gradlew test    # requires a running Docker daemon for Testcontainers
cd web && npm test
```

Backend integration tests run against real PostgreSQL and Valkey containers rather than an
in-memory substitute, so the Flyway migrations and Postgres-specific SQL are exercised on every
run.

The suite substitutes Cognito and SES, which keeps it fast but leaves the request shapes unverified.
`AwsContractTest` covers that gap by driving the real SDK clients against Floci: it is the test that
fails when an SES field is wrong or the provisioning path stops matching what Cognito returns.

## Requirements

- Java 17 (Amazon Corretto)
- Node 24.20.0 (see `.nvmrc`)
- Docker, for local infrastructure and the test suite
