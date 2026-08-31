# Hardening 2 — Operations — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Somebody finds out when production breaks without a user telling them, the cache stops carrying principal data in clear text, dependency vulnerabilities surface before an audit does, and the audit table stops promising a column it never fills.

**Architecture:** Almost entirely CloudFormation and CI. One small backend change for the audit column and one for the captions cap.

**Tech Stack:** CloudFormation, CloudWatch, SNS, GitHub Actions, Dependabot, Spring Boot.

**Spec:** [docs/superpowers/specs/2026-08-30-hardening-design.md](../specs/2026-08-30-hardening-design.md) §3.3, §3.5

## Global Constraints

- No Spotless in this repository; match surrounding style by hand.
- `./gradlew test` passes before each commit.
- Template changes are validated with `aws cloudformation validate-template` and inspected as a
  **change set** before deploying. Two tasks here can replace a live resource if got wrong.
- Verification runs against production, because there is no staging environment.

## What is already done, contrary to the spec

**Security headers need no work.** Production already answers with HSTS, `nosniff`,
`X-Frame-Options: DENY` and `no-store`. The spec claimed otherwise by reading `SecurityConfig` for
an explicit `headers(...)` block; Spring Security emits them by default. Verified by `curl`, struck
from the spec, and not a task here.

## The trap in this work

**Enabling transit encryption in one step is an outage, in either order.** A cache that requires TLS
rejects a client that is not using it; a client using TLS is rejected by a cache that does not
accept it. Task 3 is deliberately three deployments, and its steps must not be collapsed.

---

### Task 1: Somebody hears about it when production breaks

**Files:**
- Modify: `infra/app.yaml`
- Modify: `infra/README.md`

**Interfaces:**
- Produces: an SNS topic and CloudWatch alarms, with a new `AlarmEmail` parameter.

There are currently **zero** CloudWatch alarms and **zero** SNS topics in the account. The first
anybody knows about an outage is a clinician saying so.

- [ ] **Step 1: A topic and a subscription**

`AWS::SNS::Topic` plus an `AWS::SNS::Subscription` of protocol `email` to a new `AlarmEmail`
parameter. Note in the README that the subscription sits in `PendingConfirmation` until the
recipient clicks the link — the stack reports success either way, so an unconfirmed subscription is
a silent failure of the whole task.

- [ ] **Step 2: Alarms worth waking up for**

Each alarm names the user-visible consequence in its `AlarmDescription`, not the metric.

Metric names and dimensions below were read out of CloudWatch rather than recalled. Two would have
been wrong: `AWS/ECS` publishes `CPUUtilization`, `MemoryUtilization` and `LiveTaskCount` but **no
`RunningTaskCount`**, and the cache dimension is `CacheClusterId: digital-health-prod-001` — the
node, with its `-001` suffix, not the replication group.

| Alarm | Condition | Why it matters |
| --- | --- | --- |
| API returning errors | ALB `HTTPCode_Target_5XX_Count` > 5 in 5 minutes | Requests are failing |
| API has no healthy target | ALB `HealthyHostCount` < 1 for 5 minutes | Nothing is serving |
| API partly unhealthy | ALB `UnHealthyHostCount` >= 1 for 10 minutes | Degraded, or flapping |
| Database nearly full | RDS `FreeStorageSpace` < 2GB | Writes stop when it fills |
| Database struggling | RDS `CPUUtilization` > 80% for 15 minutes | Slow before it is broken |
| Cache struggling | ElastiCache `DatabaseMemoryUsagePercentage` > 80% | Evictions mean revocations are lost |

`HealthyHostCount` rather than an ECS task count: it is the metric that means "a request can be
served", it needs no Container Insights, and its dimensions come from
`!GetAtt LoadBalancer.LoadBalancerFullName` rather than a constructed string.

`TreatMissingData: notBreaching` everywhere except `HealthyHostCount`, where no data means nothing
is reporting and *is* the alarm — `breaching` there.

Storage: `AllocatedStorage` is a parameter with `MaxAllocatedStorage: 100`, so autoscaling handles
growth and 2GB free is a floor that only trips if autoscaling itself has stopped.

- [ ] **Step 3: Validate, change-set, deploy**

```bash
aws cloudformation validate-template --template-body file://infra/app.yaml
```

Then a change set, confirming every change is `Add` and nothing touches the service or the
load balancer.

- [ ] **Step 4: Prove one fires**

Set the 5xx alarm's threshold to 0 temporarily, cause one 401... no — a 401 is not a 5xx. Use
`aws cloudwatch set-alarm-state` to push it to `ALARM` and confirm the email arrives. That tests the
topic, the subscription and the confirmation, which is the part that silently fails.

- [ ] **Step 5: Commit**

---

### Task 2: Dependency scanning

**Files:**
- Create: `.github/dependabot.yml`
- Modify: `.github/workflows/ci.yml`

There is no Dependabot, no `npm audit`, no OWASP check, across three dependency ecosystems.

- [ ] **Step 1: Dependabot**

Weekly, for `gradle` (`/backend`), `npm` (`/web`), `github-actions` (`/`), and `swift` (`/ios`).
Grouped minor and patch updates so it opens a handful of PRs rather than dozens.

- [ ] **Step 2: An advisory job, not a blocking one**

A `security` job running `npm audit --audit-level=high` and Gradle's dependency check, with
`continue-on-error: true`.

Deliberately advisory. A transitive vulnerability with no published fix would otherwise block every
unrelated PR, and a check people learn to force past is worse than no check. Revisit once the noise
level is known — that judgement needs data this repository does not have yet.

- [ ] **Step 3: Commit**

---

### Task 3: Valkey stops carrying principal data in clear text

**Files:**
- Modify: `infra/data.yaml`, then `infra/app.yaml`, then `infra/data.yaml` again

**Three deployments. Do not collapse them.**

**CI does not deploy `data.yaml`** — it only passes the stack name. So steps 1 and 3 are applied by
hand and step 2 happens on merge, which fixes the order rigidly: merging step 2 before step 1 has
been applied by hand points a TLS client at a cache that does not accept TLS. Step 1 must be live in
AWS *before* the step 2 commit reaches `main`.

#### Attempted, and it failed. The three steps are not enough.

Step 1 was applied on 31 August and CloudFormation rolled it back:

```
Update canceled. Cannot update export digital-health-data-CacheEndpoint
as it is in use by digital-health-app.
```

Enabling transit encryption changes the cache's primary endpoint address. `data.yaml` exports that
address and `app.yaml` imports it, and **CloudFormation refuses to change an exported value while
another stack imports it**. The cache modification itself succeeded — sixteen minutes, in place, no
data lost — and only then did the stack update fail and reverse it. Another sixteen minutes back.

Two things worth keeping from that:

- **A change set does not catch this.** It correctly reported `Modify` with `Replacement: False`,
  which is what was checked before executing. Change sets are scoped to one stack and say nothing
  about cross-stack imports. The check that would have caught it is
  `aws cloudformation list-imports --export-name digital-health-data-CacheEndpoint`, before
  touching anything that could change that value.
- **Production was unaffected throughout.** `preferred` accepts plaintext, so the running task never
  lost the cache, and the rollback returned it to exactly where it started.

**The real sequence** has to break the export coupling first, because that coupling is what makes
the cache endpoint effectively unchangeable:

1. `app.yaml` takes the cache endpoint as a *parameter* rather than an `Fn::ImportValue`, with CI
   resolving it from the data stack's output at deploy time. `data.yaml` keeps the output and drops
   the `Export:`. Deploy app — nothing imports the export now.
2. Enable `TransitEncryptionEnabled: true` / `TransitEncryptionMode: preferred` on the data stack.
   The endpoint may change freely, because nothing imports it.
3. Deploy app again, picking up the new endpoint and `REDIS_SSL_ENABLED: 'true'`.
4. `TransitEncryptionMode: required` on the data stack.

That is four deployments, two of them sixteen-minute cache operations, plus a structural change to
how the two stacks reference each other. Against a benefit of encrypting traffic that already never
leaves the VPC and is already restricted by security group, **this is worth re-deciding rather than
pressing on** — the estimate this plan was approved against was wrong by a wide margin.

The `CachePort` export has the same problem and the same fix.

- [ ] **Step 1: Make the cache accept both**

```yaml
TransitEncryptionEnabled: true
TransitEncryptionMode: preferred
```

Valkey 8.0 supports `preferred`, which accepts TLS and plaintext at once. Inspect the change set and
**confirm the replication group is `Modify` and not `Replace`** before applying. A replacement drops
every principal cache entry and every revocation, which is survivable but should be a decision
rather than a surprise.

- [ ] **Step 2: Move the client**

`REDIS_SSL_ENABLED: 'true'` in `infra/app.yaml`. Deploy, then confirm the application is healthy and
that revocation still works — `scripts/verify_hardening.py` exercises exactly that path and is the
fastest proof the cache is still reachable.

- [ ] **Step 3: Require it**

`TransitEncryptionMode: required`. Deploy, and run the verification once more.

- [ ] **Step 4: Update the comment that documents the old trade-off**

The comment in `data.yaml` explaining why transit encryption is off must go with it.

- [ ] **Step 5: Commit**

Each step is its own commit, so a bisect lands on the deployment that broke rather than the batch.

---

### Task 4: The audit table stops promising a column it never fills

**Files:**
- Modify: `backend/src/main/java/io/simplicity/training/service/AuditService.java`
- Modify: `backend/src/main/java/io/simplicity/training/security/AppPrincipal.java` or a request-scoped holder
- Test: `backend/src/test/java/io/simplicity/training/AuditTest.java`

`audit_event.ip_address` exists on the table and the entity; `AuditService.record` never sets it.

- [x] **Step 1: Decided — fill it now, retention later**

Answered: fill the column and accept indefinite retention for the time being. **That leaves a
retention rule owed on `audit_event`**, and it is now owed more urgently than before, because the
table holds personal information it did not hold yesterday.

This task was written expecting to fill the column, on the reasoning that an audit trail without a
source address answers "who" and "what" but never "from where", which is the first question asked
when an account is suspected of being compromised.

Checking changed that. **There is no retention rule on `audit_event` anywhere** — no scheduled
purge, no lifecycle, nothing. Rows live forever. Filling this column therefore means retaining
health professionals' IP addresses indefinitely, which is personal information under the Privacy
Act 2020, whose principle 9 says not to keep it longer than needed.

So the choice was not "fill it or leave it broken" but whether to add retention first, drop the
column, or accept the retention gap. The third was chosen deliberately.

- [ ] **Step 2: The address has to be the client's**

Behind an ALB, `request.getRemoteAddr()` is the load balancer. The client is the **last** entry the
ALB appended to `X-Forwarded-For`, and the earlier entries are caller-supplied and forgeable.

A test asserts a forged `X-Forwarded-For` does not end up in the column, because taking the first
entry is the obvious implementation and it records whatever the caller claims.

- [ ] **Step 3 to 5:** failing test, implement, pass, commit.

---

### Task 5: The captions endpoint gets a bound

**Files:**
- Modify: `backend/src/main/java/io/simplicity/training/controller/MediaController.java`
- Test: existing media tests

`setCaptions` takes `@RequestBody String webvtt` with no `@Size`, alone among the write endpoints.

- [ ] **Step 1: A failing test** posting a body past the cap and expecting 400.

- [ ] **Step 2: Implement.** `@Size` on a bare `@RequestBody String` needs `@Validated` on the
class to fire at all; if that does not work cleanly, check the length in `MediaService` and throw
`BadRequestException`, which is what every other size rule in that service already does.

A cap of 2MB. A WebVTT track for a one-hour video is tens of kilobytes, so this bounds abuse without
coming near legitimate use.

- [ ] **Step 3 to 4:** pass, commit.

---

### Task 6: Verify

- [ ] Extend `scripts/verify_hardening.py`, or add a sibling, asserting the captions cap is enforced
      and that a forged `X-Forwarded-For` is not recorded.
- [ ] Run `scripts/verify_hardening.py` after Task 3's third deployment. Revocation depends on
      Redis, so it is the check that proves TLS did not quietly break the denylist.

---

## Self-review against the spec

| Spec item | Covered by |
| --- | --- |
| §3.3 alarms and SNS | Task 1 |
| §3.5 dependency scanning | Task 2 |
| §3.5 Valkey transit encryption | Task 3 |
| §3.5 `audit_event.ip_address` | Task 4 |
| §3.5 captions size cap | Task 5 |
| §3.5 security headers | Nothing to do; the spec was wrong and is corrected |

Still deferred after this: audit logging of authentication events and API access, which the spec
raises under §3.3 and which is a larger design question about what a clinical product should retain
and for how long. It needs its own decision rather than being absorbed here.
