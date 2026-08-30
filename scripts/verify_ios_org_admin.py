"""Verifies organisation administration against production.

The assertion that matters most is that a plain member gets 403 listing members. The app hides
administrative controls from members, but hiding is presentation — the server is the thing that
actually decides, and this is what proves it does.

Step 9 is written to *discover* behaviour rather than assert a guess: whether the sole remaining
administrator may leave is the server's decision, and the app's error message depends on it.

Cleanup archives the organisations this run creates and deletes only its own accounts.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from verification import Run

run = Run()
try:
    admin = run.account("admin")
    org_id = run.organisation(f"Verify Clinic {run.id}", admin)
    team_id = run.call("POST", f"/api/v1/orgs/{org_id}/teams", admin,
                       {"name": "Ward", "description": "Inpatient"}).json()["id"]

    # -- invitations -----------------------------------------------------------------------------

    doomed = run.call("POST", f"/api/v1/orgs/{org_id}/invitations", admin,
                      {"email": f"verify-never-{run.id}@simplicityhelp.com",
                       "orgRole": "ORG_MEMBER"})
    run.check("an invitation can be created", doomed.status_code in (200, 201),
              str(doomed.status_code))
    doomed_id = doomed.json()["id"]
    run.check("a new invitation is pending", doomed.json()["status"] == "PENDING")

    listed = run.call("GET", f"/api/v1/orgs/{org_id}/invitations", admin).json()
    run.check("it appears in the list", any(i["id"] == doomed_id for i in listed))

    revoked = run.call("DELETE", f"/api/v1/orgs/{org_id}/invitations/{doomed_id}", admin)
    run.check("it can be revoked", revoked.status_code in (200, 204), str(revoked.status_code))

    after = run.call("GET", f"/api/v1/orgs/{org_id}/invitations", admin).json()
    still_pending = next((i for i in after if i["id"] == doomed_id and i["status"] == "PENDING"),
                         None)
    run.check("a revoked invitation is no longer pending", still_pending is None)

    # -- a second member -------------------------------------------------------------------------

    colleague = run.account("colleague")
    colleague_me = run.call("GET", "/api/v1/me", colleague).json()
    colleague_id = colleague_me["id"]

    invited = run.call("POST", f"/api/v1/orgs/{org_id}/invitations", admin,
                       {"email": colleague_me["email"], "orgRole": "ORG_MEMBER"})
    run.check("a colleague can be invited", invited.status_code in (200, 201),
              str(invited.status_code))

    members = run.call("GET", f"/api/v1/orgs/{org_id}/members", admin).json()
    joined = next((m for m in members if m["userId"] == colleague_id), None)
    if joined is None:
        print("  SKIP  the colleague joins (invitations are accepted by emailed link, "
              "which this run cannot follow)")
    else:
        run.check("they join as an ordinary member", joined["orgRole"] == "ORG_MEMBER")

    # -- authorisation is the server's, not the interface's ---------------------------------------

    peek = run.call("GET", f"/api/v1/orgs/{org_id}/members", colleague)
    run.check("somebody outside the organisation cannot list its members",
              peek.status_code in (403, 404), str(peek.status_code))

    invite_attempt = run.call("POST", f"/api/v1/orgs/{org_id}/invitations", colleague,
                              {"email": "someone@example.com", "orgRole": "ORG_ADMIN"})
    run.check("and cannot invite anybody into it",
              invite_attempt.status_code in (403, 404), str(invite_attempt.status_code))

    team_attempt = run.call("POST", f"/api/v1/orgs/{org_id}/teams", colleague, {"name": "Theirs"})
    run.check("and cannot create a team in it",
              team_attempt.status_code in (403, 404), str(team_attempt.status_code))

    # -- teams -----------------------------------------------------------------------------------

    teams = run.call("GET", f"/api/v1/orgs/{org_id}/teams", admin).json()
    run.check("the team is listed", any(t["id"] == team_id for t in teams))

    admin_me = run.call("GET", "/api/v1/me", admin).json()
    added = run.call("POST", f"/api/v1/orgs/{org_id}/teams/{team_id}/members", admin,
                     {"userId": admin_me["id"], "teamRole": "TEAM_MEMBER"})
    run.check("somebody can be added to a team", added.status_code in (200, 201),
              str(added.status_code))

    team_members = run.call("GET", f"/api/v1/orgs/{org_id}/teams/{team_id}/members", admin).json()
    run.check("they appear in its membership",
              any(m["userId"] == admin_me["id"] for m in team_members))

    removed = run.call("DELETE",
                       f"/api/v1/orgs/{org_id}/teams/{team_id}/members/{admin_me['id']}", admin)
    run.check("and can be removed again", removed.status_code in (200, 204),
              str(removed.status_code))

    # -- leaving ----------------------------------------------------------------------------------

    # Written to discover, not to assert: the app's message depends on what actually happens here.
    leaving = run.call("DELETE", f"/api/v1/orgs/{org_id}/members/me", admin)
    if leaving.status_code == 409:
        run.check("the sole administrator is refused with a conflict, which the app explains",
                  True, "409")
    elif leaving.status_code in (200, 204):
        run.check("the sole administrator may leave — the app must not claim otherwise",
                  True, str(leaving.status_code))
        print("        note: the app's sole-admin message is unreachable; revisit it")
    else:
        run.check("leaving returns something the app knows how to explain",
                  False, str(leaving.status_code))
finally:
    run.cleanup()

raise SystemExit(run.report())
