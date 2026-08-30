"""Drives the whole learning loop against production, the way the iOS app does.

The interesting assertion is step 6: completing every section does NOT complete a module that has
a quiz. That is the one place the client and the server could quietly disagree about what "done"
means, and the app deliberately never decides it locally.

Cleanup deletes only the accounts this run created. See scripts/README.md for why that matters.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from verification import Run

run = Run()
try:
    admin = run.account("admin")

    org_id = run.organisation(f"Verify Clinic {run.id}", admin)

    team = run.call("POST", f"/api/v1/orgs/{org_id}/teams", admin,
                    {"name": f"Team {run.id}"}).json()
    team_id = team["id"]

    me = run.call("GET", "/api/v1/me", admin).json()
    run.call("POST", f"/api/v1/orgs/{org_id}/teams/{team_id}/members", admin,
             {"userId": me["id"], "teamRole": "TEAM_MEMBER"})

    # -- author and publish ---------------------------------------------------------------------

    module = run.call("POST", f"/api/v1/orgs/{org_id}/modules", admin,
                      {"title": f"Verify module {run.id}",
                       "summary": "Created by the iOS verification run."}).json()
    module_id = module["moduleId"]

    run.call("PUT", f"/api/v1/orgs/{org_id}/modules/{module_id}/draft/sections", admin,
             {"sections": [
                 {"title": "First section",
                  "body": "# Heading\n\nSome **bold** text and a [link](https://example.com)."},
                 {"title": "Second section",
                  "body": "- one\n- two\n\n```\ncode\n```"},
             ]})

    run.call("PUT", f"/api/v1/orgs/{org_id}/modules/{module_id}/draft/quiz", admin,
             {"questions": [
                 {"prompt": "Is this the first question?",
                  "explanation": "It is.",
                  "options": [{"label": "Yes", "correct": True},
                              {"label": "No", "correct": False}]},
                 {"prompt": "Is this the second question?",
                  "explanation": "It is.",
                  "options": [{"label": "Yes", "correct": True},
                              {"label": "No", "correct": False}]},
             ]})

    # supersedesCompletions false: this is a first publication, so there is nothing to supersede.
    published = run.call("POST", f"/api/v1/orgs/{org_id}/modules/{module_id}/draft/publish",
                         admin, {"supersedesCompletions": False})
    run.check("the module publishes", published.status_code in (200, 201),
              str(published.status_code))

    run.call("PUT", f"/api/v1/orgs/{org_id}/modules/{module_id}/teams", admin,
             {"teamIds": [team_id]})

    # -- the learner's view ---------------------------------------------------------------------

    assigned = run.call("GET", f"/api/v1/orgs/{org_id}/learning", admin).json()
    mine = next((m for m in assigned if m["moduleId"] == module_id), None)
    run.check("the assigned module appears in learning", mine is not None)
    run.check("it starts not started", mine and mine["status"] == "NOT_STARTED",
              mine and mine["status"])
    run.check("it reports two sections", mine and mine["sectionCount"] == 2,
              mine and str(mine["sectionCount"]))

    detail = run.call("GET", f"/api/v1/orgs/{org_id}/learning/{module_id}", admin).json()
    sections = detail["sections"]
    run.check("the reader gets both sections with their Markdown",
              len(sections) == 2 and all(s.get("body") for s in sections))

    first = run.call("PUT",
                     f"/api/v1/orgs/{org_id}/learning/sections/{sections[0]['sectionId']}/complete",
                     admin).json()
    run.check("one section read makes it in progress", first["status"] == "IN_PROGRESS",
              first["status"])

    second = run.call("PUT",
                      f"/api/v1/orgs/{org_id}/learning/sections/{sections[1]['sectionId']}/complete",
                      admin).json()
    run.check("every section read is still not complete, because the quiz has not been passed",
              second["status"] != "COMPLETED", second["status"])

    # -- the quiz -------------------------------------------------------------------------------

    quiz = run.call("GET", f"/api/v1/orgs/{org_id}/learning/{module_id}/quiz", admin).json()
    questions = quiz["questions"]
    run.check("the quiz has both questions", len(questions) == 2)
    run.check("the learner is not told which option is correct",
              all("correct" not in option for q in questions for option in q["options"]))

    def answer(label):
        return {"answers": [
            {"questionId": q["questionId"],
             "optionId": next(o["optionId"] for o in q["options"] if o["label"] == label)}
            for q in questions
        ]}

    wrong = run.call("POST", f"/api/v1/orgs/{org_id}/learning/{module_id}/quiz/attempts",
                     admin, answer("No")).json()
    run.check("a wrong attempt does not pass", wrong["passed"] is False)
    run.check("a wrong attempt is marked per question and explains itself",
              len(wrong.get("questions", [])) == 2
              and any(q.get("explanation") for q in wrong["questions"]))

    right = run.call("POST", f"/api/v1/orgs/{org_id}/learning/{module_id}/quiz/attempts",
                     admin, answer("Yes")).json()
    run.check("the correct attempt passes", right["passed"] is True)

    after = run.call("GET", f"/api/v1/orgs/{org_id}/learning/{module_id}", admin).json()
    run.check("passing the quiz completes the module", after["status"] == "COMPLETED",
              after["status"])

    # -- assignment is a boundary, not a suggestion -----------------------------------------------

    outsider = run.account("outsider")
    outsider_me = run.call("GET", "/api/v1/me", outsider).json()
    run.call("POST", f"/api/v1/orgs/{org_id}/invitations", admin,
             {"email": outsider_me["email"], "orgRole": "ORG_MEMBER"})

    visible = run.call("GET", f"/api/v1/orgs/{org_id}/learning", outsider)
    ids = [m["moduleId"] for m in visible.json()] if visible.status_code == 200 else []
    run.check("somebody outside the team does not see the module",
              module_id not in ids, f"status {visible.status_code}")
finally:
    run.cleanup()

raise SystemExit(run.report())
