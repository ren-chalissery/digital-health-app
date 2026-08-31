# Digital Health App

A digital training package that prepares mental health professionals to safely deliver
**Simplicity**, a digital therapeutic tool, to people living with serious mental illness such as
schizophrenia and psychosis.

Clinicians work through training modules authored by their organisation's administrators, prove
their understanding by answering quiz questions, and keep a private journal of practice
reflections.

## Status

Deployed and running. Every phase but Android has shipped, and each is verified against the
deployed environment rather than only by the test suite — see [scripts/](scripts/).

Each phase has a design specification in
[docs/superpowers/specs/](docs/superpowers/specs/) explaining the reasoning, starting with
[Phase 1](docs/superpowers/specs/2026-08-29-digital-health-app-phase1-design.md) for the
architecture.

| Phase | Scope | State |
| --- | --- | --- |
| 1 | Auth, profiles, organisations, teams, invitations, deployed AWS environment | Shipped |
| 2 | Module authoring, video, quizzes, Dashboard and Learn | Shipped |
| 3 | Reflect journal with search | Shipped |
| 4 | Assistant over the training content | Shipped |
| 5 | Native iOS client | Shipped, TestFlight pending a bundle id |
| 5 | Native Android client | **Not started** — needs an Android SDK |
| — | Hardening: revocation, token validation, rate limits, alarms, dependency scanning | Shipped |

### Known gaps

Named here rather than left to be discovered. All but the first were deliberate:

- **Android.** Committed to in Phase 1 and scoped into Phase 5, and the only item that falls short
  of what was promised rather than what was chosen.
- **No staging environment.** Both the Phase 1 and hardening specs call this the right next
  infrastructure decision. Everything is verified against production, which is the condition that
  made the Cognito cleanup incident possible.
- **Captions play on the web but not on iOS.** `AVFoundation` cannot side-load WebVTT onto a
  progressive MP4; it needs HLS, which is a transcode, backend, web and migration change.
- **Valkey traffic is not encrypted in transit.** Deferred deliberately — it is four deployments
  and a change to how the stacks reference each other, to encrypt traffic that never leaves the
  VPC. The path is written down in
  [the operations plan](docs/superpowers/plans/2026-08-31-hardening-2-operations.md).
- **Authentication events are not audited.** Organisation, team and module changes are; sign-ins
  are not.

## Layout

| Directory | Contents |
| --- | --- |
| [backend/](backend/) | Spring Boot API — Java 17, Gradle, Spring Boot 4.1 |
| [web/](web/) | Angular web client |
| [infra/](infra/) | CloudFormation and SAM templates |
| [api-contract/](api-contract/) | Generated `openapi.yaml` and client generator configuration |
| [docs/](docs/) | Design specifications |
| [ios/](ios/) | Native iOS client — SwiftUI, ten local packages, XcodeGen |
| `android/` | Placeholder; the Android client has not been started |
| [scripts/](scripts/) | Checks that run against a deployed environment |

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

## Personal information and how long it is kept

Worth stating plainly, because a product handling clinical data should be able to answer this
without somebody reading the schema.

| What | Kept for | Why |
| --- | --- | --- |
| Reflections | As long as the account | They belong to the clinician, are private to them, and go when the account does |
| Audit entries | Indefinitely | Who changed what in an organisation. Operational history, holding no personal information beyond user ids stored anyway |
| **Source address on an audit entry** | **180 days** | The one piece of personal information the audit table adds. Long enough to investigate something noticed late, and then cleared |
| Cached principals | 5 minutes | A cache |
| Revoked sessions | 15 minutes | Exactly as long as a token could live |
| Uploaded source video | 7 days | S3 lifecycle rule, after transcoding |

`AuditRetentionJob` clears the address nightly. It keeps the entry: deleting whole entries would
answer the privacy question by destroying the audit trail, which is the wrong trade. Change the
window with `app.audit.ip-retention`.

Principle 9 of the Privacy Act 2020 is the reason there is a number in that table at all — personal
information is not to be kept longer than it is needed for, and "forever" is not a retention period.

## Requirements

- Java 17 (Amazon Corretto)
- Node 24.20.0 (see `.nvmrc`)
- Docker, for local infrastructure and the test suite
