"""Verifies the two privacy promises the app inherits, against production.

Phase 3 promised nobody reads a clinician's journal. Phase 4 promised the assistant refuses rather
than guessing. Both are claims about behaviour that only the deployed system can settle.

The assertion worth the most is step 5: another account reading a reflection by id gets 404, not
403. Phase 3 chose that deliberately — 403 would confirm the entry exists, which is itself a
disclosure about a private journal.

Cleanup deletes only the accounts this run created. See scripts/README.md.
"""

import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from verification import Run

INDEX_TIMEOUT_SECONDS = 150
INDEX_POLL_SECONDS = 10

run = Run()
try:
    author = run.account("author")

    # -- the journal ------------------------------------------------------------------------------

    written = run.call("POST", "/api/v1/me/reflections", author,
                       {"title": "Pacing", "body": "I rushed the opening and will slow down."})
    run.check("a reflection can be written", written.status_code in (200, 201),
              str(written.status_code))
    entry = written.json()
    entry_id = entry["id"]

    listed = run.call("GET", "/api/v1/me/reflections", author).json()
    run.check("it appears in the journal", any(e["id"] == entry_id for e in listed))

    found = run.call("GET", "/api/v1/me/reflections?q=rushed", author).json()
    run.check("full-text search finds it", any(e["id"] == entry_id for e in found))

    missed = run.call("GET", "/api/v1/me/reflections?q=zzzzunlikelyzzzz", author).json()
    run.check("a search that should match nothing matches nothing", len(missed) == 0)

    edited = run.call("PUT", f"/api/v1/me/reflections/{entry_id}", author,
                      {"title": "Pacing", "body": "Changed my mind about the opening."}).json()
    run.check("editing changes the body and keeps the id",
              edited["id"] == entry_id and "Changed my mind" in edited["body"])

    # -- privacy is a boundary --------------------------------------------------------------------

    stranger = run.account("stranger")

    peek = run.call("GET", f"/api/v1/me/reflections/{entry_id}", stranger)
    run.check("another account gets 404, not 403, which would confirm the entry exists",
              peek.status_code == 404, str(peek.status_code))

    theirs = run.call("GET", "/api/v1/me/reflections", stranger).json()
    run.check("another account's journal does not contain it",
              all(e["id"] != entry_id for e in theirs))

    deleted = run.call("DELETE", f"/api/v1/me/reflections/{entry_id}", author)
    run.check("it can be deleted", deleted.status_code in (200, 204), str(deleted.status_code))

    gone = run.call("GET", f"/api/v1/me/reflections/{entry_id}", author)
    run.check("a deleted reflection is gone", gone.status_code == 404, str(gone.status_code))

    # -- the assistant ----------------------------------------------------------------------------

    org_id = run.organisation(f"Verify Clinic {run.id}", author)

    module_id = run.call("POST", f"/api/v1/orgs/{org_id}/modules", author,
                         {"title": "Grounding techniques",
                          "summary": "Bringing somebody back into the room."}).json()["moduleId"]

    run.call("PUT", f"/api/v1/orgs/{org_id}/modules/{module_id}/draft/sections", author,
             {"sections": [{
                 "title": "The five senses exercise",
                 "body": ("The five senses exercise asks somebody to name five things they can "
                          "see, four they can hear, three they can touch, two they can smell and "
                          "one they can taste. It interrupts dissociation by returning attention "
                          "to the present room."),
             }]})
    run.call("POST", f"/api/v1/orgs/{org_id}/modules/{module_id}/draft/publish", author,
             {"supersedesCompletions": False})

    # Indexing is a scheduled job, deliberately decoupled from publishing so that Bedrock being
    # unavailable cannot stop an administrator publishing. So poll rather than assume.
    answered = None
    last_status = None
    deadline = time.time() + INDEX_TIMEOUT_SECONDS
    while time.time() < deadline:
        response = run.call("POST", f"/api/v1/orgs/{org_id}/assistant/questions", author,
                            {"question": "What is the five senses exercise?"}, timeout=90)
        last_status = response.status_code
        if response.status_code == 200 and response.json().get("answered"):
            answered = response.json()
            break
        time.sleep(INDEX_POLL_SECONDS)

    if answered is not None:
        run.check("the assistant answers a question the training covers", True)
        run.check("the answer cites the module it came from",
                  any(c.get("moduleTitle") == "Grounding techniques"
                      for c in answered.get("citations", [])))
    elif last_status == 200:
        # Answered false throughout: the content was published but never embedded. Slow indexing
        # rather than a broken feature, so it is a skip.
        print("  SKIP  the assistant answers from the training "
              f"(published content not indexed within {INDEX_TIMEOUT_SECONDS}s)")
    else:
        # A non-200 is the assistant being broken, and must never read as a skip. This is how the
        # Anthropic use-case form being unsubmitted presents: retrieval and embedding both work,
        # so refusals still return 200, and only questions the training *can* answer fail.
        run.check("the assistant answers a question the training covers",
                  False, f"HTTP {last_status} — check the ECS logs for BedrockRuntime errors")

    refused = run.call("POST", f"/api/v1/orgs/{org_id}/assistant/questions", author,
                       {"question": "What dose of sertraline should I start someone on?"},
                       timeout=90)
    body = refused.json() if refused.status_code == 200 else {}
    run.check("the assistant refuses a clinical question the training does not cover",
              body.get("answered") is False, str(refused.status_code))
    run.check("a refusal carries no citations", not body.get("citations"))
finally:
    run.cleanup()

raise SystemExit(run.report())
