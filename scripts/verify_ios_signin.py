"""Confirms the iOS Cognito app client works the way the app assumes it does.

The simulator cannot be driven from here, so this exercises the same client id through SRP and the
same API the app calls. That is where the interesting failures live: a client without SRP enabled,
a token the API will not accept, a /me that does not provision on first call. The Amplify SRP
implementation itself is only provable by signing in on a device, which is a manual step.

Cleanup deletes only the account this run created. See scripts/README.md for why that matters.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from pycognito import Cognito

from verification import POOL, PREFIX, Run

IOS_CLIENT = os.environ["IOS_CLIENT_ID"]

run = Run()
try:
    email = f"{PREFIX}-ios-{run.id}@simplicityhelp.com"
    run.idp.admin_create_user(
        UserPoolId=POOL,
        Username=email,
        MessageAction="SUPPRESS",
        UserAttributes=[
            {"Name": "email", "Value": email},
            {"Name": "email_verified", "Value": "true"},
        ],
    )
    run.idp.admin_set_user_password(
        UserPoolId=POOL, Username=email, Password=run.password, Permanent=True
    )
    run._created.append(email)

    user = Cognito(POOL, IOS_CLIENT, username=email)
    user.authenticate(password=run.password)
    run.check("SRP succeeds against the iOS app client", bool(user.access_token))

    headers = {"Authorization": "Bearer " + user.access_token}

    me = run.call("GET", "/api/v1/me", headers)
    run.check(
        "the API accepts a token issued to the iOS client",
        me.status_code == 200,
        str(me.status_code),
    )

    body = me.json() if me.status_code == 200 else {}
    run.check(
        "a new user is provisioned on first call, needing onboarding",
        body.get("profileCompleted") is False,
        repr(body.get("profileCompleted")),
    )
    run.check(
        "the verified address comes back, which the app has no other way to learn",
        body.get("email") == email,
        repr(body.get("email")),
    )

    # The app sends no Authorization header when signed out, so this is a path it really takes.
    anonymous = run.call("GET", "/api/v1/me", {})
    run.check(
        "the API refuses an unauthenticated call",
        anonymous.status_code == 401,
        str(anonymous.status_code),
    )
finally:
    run.cleanup()

raise SystemExit(run.report())
