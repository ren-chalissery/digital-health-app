# Verification scripts

Checks that run against a **deployed** environment, doing what the test suite cannot: driving the
real Cognito, the real API, and the real AWS services behind them.

Every AWS-facing bug in this project so far was found here rather than by the suite — a MediaConvert
job rejected for an audio selector it had never been given, Claude refusing a bare model id, an
OpenAPI document that made every generated client parse responses as blobs. None of those are
reachable offline, because the fakes accept whatever they are handed.

## The rule

**Never enumerate the user pool and delete what you find.**

A run once cleaned up by listing every account in the pool and deleting all of them, which removed
a real person's login along with its own test accounts. [`verification.py`](verification.py) exists
so that cleanup can only ever remove accounts the running process created and is holding in memory.

Accounts are named `verify-<label>-<runid>@simplicityhelp.com`. If a run dies before cleaning up,
the leftovers are obvious and can be removed deliberately — a much better failure than deleting
somebody's account.

This matters because there is no staging environment. The Phase 1 spec called that out as
acceptable at pilot scale and worth revisiting; until it is, verification runs against production
and cleanup has to be precise rather than thorough.

## Writing one

```python
from verification import Run

run = Run()
try:
    admin = run.account("admin")
    org = run.call("POST", "/api/v1/organisations", admin,
                   {"name": f"Clinic {run.id}", "organisationType": "CLINIC"}).json()["id"]

    r = run.call("GET", "/api/v1/me", admin)
    run.check("the profile comes back", r.status_code == 200, str(r.status_code))
finally:
    run.cleanup()

raise SystemExit(run.report())
```

## Running one

```bash
python3 -m venv .venv && .venv/bin/pip install -q pycognito requests boto3

AWS_PROFILE=simplicity \
POOL_ID=$(aws cloudformation describe-stacks --stack-name digital-health-auth \
  --query "Stacks[0].Outputs[?OutputKey=='UserPoolId'].OutputValue" --output text) \
CLIENT_ID=$(aws cloudformation describe-stacks --stack-name digital-health-auth \
  --query "Stacks[0].Outputs[?OutputKey=='WebClientId'].OutputValue" --output text) \
  .venv/bin/python your_check.py
```

## Anything else it touches

Deleting the Cognito account does not remove what it created. Use `run.organisation(...)` rather
than posting to `/api/v1/organisations` directly, and cleanup archives it — modules, teams and
assignments go with it. Runs that skipped this left organisations behind in production
indefinitely.

Reflections belong to the account and go when it does. Objects left in the upload bucket expire
after seven days by lifecycle rule. Nothing here should delete an S3 bucket's contents wholesale,
for the same reason it should not delete users wholesale.

## When a check fails against production

Treat it as production being wrong until proven otherwise — that is what these scripts are for.
The ECS logs are usually the fastest answer:

```bash
AWS_PROFILE=simplicity AWS_REGION=ap-southeast-2 \
  aws logs filter-log-events --log-group-name /ecs/digital-health-prod \
  --start-time $(python3 -c "import time;print(int((time.time()-1800)*1000))") \
  --filter-pattern '?WARN ?ERROR' --query 'events[*].message' --output text
```

A check that cannot decide should skip loudly rather than pass quietly, and must never skip for a
reason that is actually a fault. `verify_ios_reflect_assistant.py` distinguishes the two: content
not yet indexed is a skip, a non-200 from the assistant is a failure.
