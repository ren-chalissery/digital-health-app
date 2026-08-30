# Hardening — Design

Date: 2026-08-30
Status: approved, not yet implemented
Follows: [Phase 1](2026-08-29-digital-health-app-phase1-design.md), [Phase 5 — iOS](2026-08-29-phase5-ios-design.md)

## 1. Scope

Phase 5 was written as "native iOS and Android clients, hardening". iOS shipped across six slices;
Android is blocked for want of an SDK; hardening never got a specification. This is it.

It is deliberately grounded in what an audit of the running system actually found, not in a generic
checklist. Every item below is something that is missing or wrong today, with the evidence quoted.

### Definition of done

Removing somebody from an organisation ends their access in seconds rather than minutes. A token
that is not an access token from a known client is refused. Somebody is told when production
breaks. The rate limits the specs describe exist, and the ones that guard sign-in-adjacent paths
refuse rather than wave through when Redis is unavailable. Dependencies are watched. And the app
stops telling a clinician something untrue about what leaving an organisation does.

### What this is not

Not a penetration test, not a compliance exercise, and not a rewrite. The authorisation model is
already good — `@authz` is applied consistently, there are 35 to 45 tests asserting cross-tenant
and role boundaries, and `MultiTenancyIsolationTest` alone has twenty. Error handling already
refuses to leak internals. Those are left alone.

## 2. A correction first

The iOS app currently warns a sole administrator that leaving "would strand this organisation" and
tells them to promote somebody first. That is not what happens:

```java
if (lastAdmin) {
  // Nobody is left who could administer it, so it goes with them rather than lingering in a
  // state only a database console could recover from.
  archive(actor, orgId);
}
```

Leaving as the last administrator **archives the organisation**. That is a deliberate and
defensible decision, and it was reported as a server gap in the Plan 4 pull request, which was
wrong. The consequence for the person doing it is severe and entirely different from what the app
says, so the warning is corrected as part of this work.

`removeMember` separately refuses to remove the last administrator, which is the right asymmetry:
you may take an organisation down with you, but you may not have that done to you.

## 3. Decisions and their rationale

### 3.1 Revocation exists and is not wired up

`TokenRevocationService` maintains a Redis denylist keyed on the Cognito subject, with a TTL equal
to the access token lifetime. `SessionService.accessRevoked(userId)` populates it.
`PrincipalResolutionFilter` checks it. All of that works.

Nothing in production calls it. `grep accessRevoked src/main` finds only the definition;
`removeMember` calls `sessions.rolesChanged(userId)`, which evicts the principal cache but leaves
the token valid. **A clinician removed from an organisation keeps working access for up to fifteen
minutes.**

That is the single most consequential gap found, because it is the one where the product does
something a user would reasonably assume it does not. Removal, deactivation and suspension all
denylist from now on.

Role *changes* deliberately do not. Demoting an administrator to a member should take effect
promptly but need not sign them out of the product mid-sentence, and cache eviction already covers
it within the same request.

### 3.2 The JWT decoder validates almost nothing

```java
return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
```

`withJwkSetUri` gives signature and expiry checking and nothing else. Notably absent: `iss`, and
`token_use`.

The `token_use` omission is the one that matters. Cognito issues both **ID tokens** and **access
tokens** from the same pool, signed by the same keys, so an ID token presented as a bearer token is
accepted today. ID tokens are handed around far more freely by clients — they are the thing a
front-end reads a display name out of — and are not meant to authorise anything.

**A trap to avoid while fixing this.** The obvious next step is validating `client_id` against
`app.cognito.client-id`. That property is populated from the **web** client
(`infra/app.yaml` imports `${AuthStackName}-WebClientId`), and the iOS app authenticates with a
different one. Validating against a single value would reject every request from the iOS app the
moment it deployed. The property becomes a **list**, and `infra/app.yaml` passes all three client
ids that `auth.yaml` already exports.

### 3.3 Nobody would know

`infra/` contains no `Alarm`, no `SNS`, no `Dashboard`, and Container Insights is explicitly
disabled. The ECS circuit breaker will roll a bad deployment back, which is genuinely useful, and
CloudWatch has the logs. But if the task began crash-looping an hour after a deployment, or RDS ran
out of storage, or the error rate went to a third of requests, the first report would come from a
clinician.

At pilot scale the answer is not a monitoring stack. It is a single SNS topic with an email
subscription and a small number of alarms that only fire when something is genuinely wrong:
unhealthy targets, 5xx rate, RDS free storage and CPU, and the ECS service running below its
desired count.

Alarms nobody acts on are worse than none, so the set stays small enough to stay credible.

### 3.4 Rate limiting fails open, and barely exists

```java
} catch (DataAccessException e) {
  log.warn("Rate limiting unavailable for {}, allowing the request", redisKey, e);
  return true;
}
```

Failing open is right for the assistant: Redis being down should not stop a clinician asking about
their training. It is wrong for invitations, where the failure mode is somebody using the product
as a mail relay.

So `tryAcquire` grows an explicit failure posture per call site rather than one global choice, and
the caller states which it wants. Nothing about the existing behaviour changes silently.

Two limits are missing outright. The Phase 5 spec says media upload is limited to one asset per
user per minute; `MediaService.register` has no limiter at all. And reflection writes are unlimited,
which is not an abuse concern so much as a runaway-client one.

### 3.5 Smaller things, worth doing while we are here

**Valkey is encrypted at rest but not in transit**, and the task sets `REDIS_SSL_ENABLED: 'false'`.
The traffic stays inside the VPC, so this is defence in depth rather than an open door — but the
data crossing it is principal caches and denylist entries, and enabling it is a template change plus
a client flag.

**The API serves no security headers.** CloudFront sets HSTS, `X-Content-Type-Options` and
`X-Frame-Options` for the web bundle; the ALB and Spring set none for the API. For a JSON API the
practical wins are HSTS and `X-Content-Type-Options: nosniff`.

**No dependency scanning of any kind** — no Dependabot, no `npm audit` in CI, no OWASP check. For a
product handling clinical data with three ecosystems of dependencies, that is the cheapest
meaningful control available.

**`audit_event.ip_address` is never populated.** The column exists and the entity has the field;
`AuditService.record` never sets it. Either fill it in or drop the column, because a schema that
promises something it never delivers misleads whoever reads it next.

**The captions endpoint takes an unbounded `String` body.** Everything else has a `@Size` cap;
this one does not.

## 4. What changes

| Area | Change |
| --- | --- |
| Revocation | `removeMember`, `leave`, deactivation and suspension call `accessRevoked` |
| JWT | Validate `iss` and `token_use == "access"`; validate `client_id` against a **list** |
| Config | `app.cognito.client-id` becomes `client-ids`; `infra/app.yaml` passes web, iOS and Android |
| Alerting | One SNS topic, email subscription, and alarms for 5xx, unhealthy targets, RDS storage and CPU, ECS running count |
| Rate limits | Per-call-site fail-open/fail-closed; add media upload and reflection write limits |
| Redis | `TransitEncryptionEnabled: true`, `REDIS_SSL_ENABLED: 'true'` |
| Headers | HSTS and `nosniff` on API responses |
| Supply chain | Dependabot for Gradle, npm and Actions; `npm audit` in the web job |
| Audit | Populate `ip_address` |
| Validation | `@Size` cap on the WebVTT body |
| iOS | Correct the sole-administrator warning to say the organisation will be archived |

## 5. Testing

The interesting assertions, all of which fail today:

- A removed member's existing token is refused on the next request, not in fifteen minutes.
- Somebody who leaves has their token refused too.
- A deactivated user's token is refused.
- An **ID token** is refused where an access token is expected.
- A token from an unknown client id is refused.
- A token from **each** of the three known client ids is accepted — the test that would have caught
  the single-client-id trap in §3.2.
- A token from another issuer is refused.
- The media limiter refuses a second upload registration within the minute.
- Invitations fail **closed** when Redis is unavailable, and the assistant fails **open**.
- The captions endpoint refuses an oversized body.

Alarms are verified by the CloudFormation stack deploying and the topic having a confirmed
subscription. Whether an alarm is well-tuned is not something a test can answer; the smoke check
after deployment already exercises the health path that the unhealthy-target alarm watches.

## 6. Risks

| Risk | Mitigation |
| --- | --- |
| Client-id validation locks out a client | It is a list, and a test asserts all three are accepted before this ships |
| Denylisting on removal signs somebody out mid-task | That is the intent; the TTL is fifteen minutes, so it self-heals rather than persisting |
| Alarms that cry wolf | A deliberately small set, thresholds set above normal variation, one email topic rather than paging |
| Turning on Redis TLS breaks the app | A template and a flag deployed together; the smoke check after deployment fails loudly if the cache is unreachable |
| Fail-closed rate limiting turns a Redis outage into an outage | Applied only to invitations, never to reading or writing training or reflections |

## 7. Out of scope

Android, which needs an SDK that is not installed. A staging environment, which Phase 1 flagged and
which deserves its own decision about cost. HLS and captions, which is a backend, transcode, web and
data-migration change. Resumable upload. An audit-read API. Multi-AZ RDS and a Valkey replica, which
are availability rather than hardening and cost real money. Penetration testing.

One of those is worth naming rather than listing: **there is still no staging environment.** Every
change in this document will be verified against production, which is exactly the condition that
made the Cognito cleanup incident possible. It is the right next infrastructure decision and it is
not this one.
