"""Verifies the hardening work against a deployed environment.

Run this only after the change is deployed: it asserts behaviour the previous build does not have,
so against an older deployment every check here fails honestly.

    POOL_ID=... CLIENT_ID=... python scripts/verify_hardening.py

Revocation is exercised through leaving rather than being removed. A second member cannot be added
programmatically — invitations are accepted by emailed link, as `verify_ios_org_admin` records —
and leaving runs through exactly the same `accessRevoked` path that removal does.

Four things are checked, each corresponding to something the audit found.

  - Leaving stops the token being held from working immediately, rather than at expiry.
  - A token minted in the same second as the revocation is refused too. That is deliberate rather
    than incidental: a JWT `iat` is whole seconds, so it cannot be ordered against the revocation
    and the safe reading is that it came first.
  - A token issued a moment later works, so nobody is locked out of the product for fifteen
    minutes over losing one organisation. A naive subject-wide ban fails this check.
  - An ID token is refused where an access token belongs. Cognito signs both with the same keys,
    so this only holds if `token_use` is genuinely validated.
  - A second upload registration inside the minute is refused.
"""

import sys
import time

from pycognito import Cognito

from verification import CLIENT, POOL, Run


def main() -> int:
    run = Run()
    print(f"Verifying hardening against the deployed API (run {run.id})")

    try:
        person = run.account("harden")
        email = run.call("GET", "/api/v1/me", person).json()["email"]

        # -- revocation ---------------------------------------------------------------------
        org_id = run.organisation(f"Hardening {run.id}", person)

        held = dict(person)
        run.check(
            "the token works while they are a member",
            run.call("GET", "/api/v1/me", held).status_code == 200,
        )

        left = run.call("DELETE", f"/api/v1/orgs/{org_id}/members/me", held)
        run.check("leaving succeeds", left.status_code == 204, f"got {left.status_code}")

        run.check(
            "the token they were holding stops working at once",
            run.call("GET", "/api/v1/me", held).status_code == 401,
            "stayed valid for up to fifteen minutes before this change",
        )

        run.check(
            "a token minted in the same second as the revocation is also refused",
            run.call("GET", "/api/v1/me", bearer(email, run.password, "access_token")).status_code
            == 401,
            "deliberate: a JWT iat is whole seconds, so that one cannot be ordered against it",
        )

        # Past the second boundary the ambiguity is gone. Two seconds rather than one, so a clock
        # landing near the edge cannot make this flake.
        time.sleep(2)
        fresh = bearer(email, run.password, "access_token")
        run.check(
            "a token issued a moment later works",
            run.call("GET", "/api/v1/me", fresh).status_code == 200,
            "a subject-wide ban would fail here, locking them out of everything",
        )

        # -- token validation ---------------------------------------------------------------
        run.check(
            "an ID token is refused where an access token belongs",
            run.call("GET", "/api/v1/me", bearer(email, run.password, "id_token")).status_code
            == 401,
            "Cognito signs both with the same keys, so only token_use separates them",
        )

        # -- rate limiting ------------------------------------------------------------------
        second_org = run.organisation(f"Hardening media {run.id}", fresh)
        first = register_upload(run, second_org, fresh)
        second = register_upload(run, second_org, fresh)

        run.check(
            "the first upload registration of the minute is accepted",
            first.status_code == 201,
            f"got {first.status_code}",
        )
        run.check(
            "a second upload registration inside the minute is refused",
            second.status_code == 409,
            f"got {second.status_code}",
        )
    finally:
        run.cleanup()

    return run.report()


def bearer(email: str, password: str, kind: str) -> dict:
    """A freshly signed-in token of the given kind, which is the point of the ID token check."""
    user = Cognito(POOL, CLIENT, username=email)
    user.authenticate(password=password)
    return {"Authorization": "Bearer " + getattr(user, kind)}


def register_upload(run: Run, org_id: str, headers: dict):
    return run.call(
        "POST",
        f"/api/v1/orgs/{org_id}/media",
        headers,
        {"filename": "clip.mp4", "contentType": "video/mp4", "sizeBytes": 1024},
    )


if __name__ == "__main__":
    sys.exit(main())
