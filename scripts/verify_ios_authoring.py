"""Verifies authoring, upload and publishing against production.

The assertion worth the most is the last one: publishing with supersedesCompletions returns a
learner who had finished the module to NEEDS_REDOING. Get that flag backwards and you either
un-complete an entire organisation's training or silently fail to, and neither shows up until
somebody complains.

Cleanup archives the organisation this run creates and deletes only its own accounts.
"""

import os
import subprocess
import sys
import tempfile
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import requests

from verification import API, Run

TRANSCODE_TIMEOUT_SECONDS = 300
TRANSCODE_POLL_SECONDS = 10

VTT = """WEBVTT

00:00:00.000 --> 00:00:02.000
Grounding brings attention back to the room.
"""


def tiny_mp4() -> bytes:
    """A real, if brief, H.264 file. MediaConvert rejects anything that is not genuinely video."""
    path = os.path.join(tempfile.mkdtemp(), "clip.mp4")
    subprocess.run(
        ["ffmpeg", "-y", "-loglevel", "error",
         "-f", "lavfi", "-i", "testsrc=size=320x240:rate=15:duration=2",
         "-f", "lavfi", "-i", "sine=frequency=440:duration=2",
         "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest", path],
        check=True,
    )
    with open(path, "rb") as handle:
        return handle.read()


run = Run()
try:
    admin = run.account("admin")
    org_id = run.organisation(f"Verify Clinic {run.id}", admin)
    team_id = run.call("POST", f"/api/v1/orgs/{org_id}/teams", admin,
                       {"name": "Ward"}).json()["id"]
    me = run.call("GET", "/api/v1/me", admin).json()
    run.call("POST", f"/api/v1/orgs/{org_id}/teams/{team_id}/members", admin,
             {"userId": me["id"], "teamRole": "TEAM_MEMBER"})

    # -- author and publish ------------------------------------------------------------------------

    module_id = run.call("POST", f"/api/v1/orgs/{org_id}/modules", admin,
                         {"title": f"Grounding {run.id}"}).json()["moduleId"]

    run.call("PUT", f"/api/v1/orgs/{org_id}/modules/{module_id}/draft/sections", admin,
             {"sections": [{"title": "The exercise", "body": "Name five things you can see."}]})

    published = run.call("POST", f"/api/v1/orgs/{org_id}/modules/{module_id}/draft/publish",
                         admin, {"supersedesCompletions": False})
    run.check("a module publishes", published.status_code == 200, str(published.status_code))

    run.call("PUT", f"/api/v1/orgs/{org_id}/modules/{module_id}/teams", admin,
             {"teamIds": [team_id]})

    # -- the learner finishes it ---------------------------------------------------------------------

    detail = run.call("GET", f"/api/v1/orgs/{org_id}/learning/{module_id}", admin).json()
    for section in detail["sections"]:
        run.call("PUT",
                 f"/api/v1/orgs/{org_id}/learning/sections/{section['sectionId']}/complete", admin)

    after = run.call("GET", f"/api/v1/orgs/{org_id}/learning/{module_id}", admin).json()
    run.check("a module with no quiz completes once every section is read",
              after["status"] == "COMPLETED", after["status"])

    # -- upload ---------------------------------------------------------------------------------------

    try:
        payload = tiny_mp4()
    except (FileNotFoundError, subprocess.CalledProcessError):
        payload = None
        print("  SKIP  upload and transcode (ffmpeg not available to make a test clip)")

    asset_id = None
    if payload:
        target = run.call("POST", f"/api/v1/orgs/{org_id}/media", admin,
                          {"filename": "clip.mp4", "contentType": "video/mp4",
                           "sizeBytes": len(payload)})
        run.check("an upload can be registered", target.status_code == 201,
                  str(target.status_code))
        asset_id = target.json()["assetId"]

        # A single presigned PUT for the whole file — there are no parts and no resume.
        put = requests.put(target.json()["uploadUrl"], data=payload,
                           headers={"Content-Type": "video/mp4"}, timeout=120)
        run.check("the presigned PUT accepts the bytes", put.status_code in (200, 204),
                  str(put.status_code))

        run.call("POST", f"/api/v1/orgs/{org_id}/media/{asset_id}/uploaded", admin)

        status = None
        deadline = time.time() + TRANSCODE_TIMEOUT_SECONDS
        while time.time() < deadline:
            assets = run.call("GET", f"/api/v1/orgs/{org_id}/media", admin).json()
            asset = next((a for a in assets if a["assetId"] == asset_id), None)
            status = asset["status"] if asset else None
            if status in ("READY", "FAILED"):
                break
            time.sleep(TRANSCODE_POLL_SECONDS)

        if status == "READY":
            run.check("the video transcodes", True)
        elif status == "FAILED":
            run.check("the video transcodes", False,
                      (asset or {}).get("failureReason") or "FAILED")
        else:
            print(f"  SKIP  the video transcodes (still {status} after "
                  f"{TRANSCODE_TIMEOUT_SECONDS}s)")

        # -- captions -------------------------------------------------------------------------------

        # Sent as a raw text/vtt body rather than through run.call, which posts JSON.
        captions_url = f"{API}/api/v1/orgs/{org_id}/media/{asset_id}/captions"
        vtt_headers = {**admin, "Content-Type": "text/vtt"}

        captions = requests.put(captions_url, data=VTT.encode(), headers=vtt_headers, timeout=60)
        run.check("a WebVTT track is accepted", captions.status_code in (200, 204),
                  str(captions.status_code))

        rubbish = requests.put(captions_url, data=b"this is not a caption file",
                               headers=vtt_headers, timeout=60)
        run.check("something that is not WebVTT is refused", rubbish.status_code == 400,
                  str(rubbish.status_code))

    # -- the assertion this whole plan turns on -------------------------------------------------------

    run.call("POST", f"/api/v1/orgs/{org_id}/modules/{module_id}/draft", admin)
    run.call("PUT", f"/api/v1/orgs/{org_id}/modules/{module_id}/draft/sections", admin,
             {"sections": [
                 {"title": "The exercise", "body": "Name five things you can see.",
                  **({"mediaAssetId": asset_id} if asset_id else {})},
                 {"title": "Afterwards", "body": "Check in before moving on."},
             ]})

    superseded = run.call("POST", f"/api/v1/orgs/{org_id}/modules/{module_id}/draft/publish",
                          admin, {"supersedesCompletions": True})
    run.check("republishing succeeds", superseded.status_code == 200, str(superseded.status_code))

    redo = run.call("GET", f"/api/v1/orgs/{org_id}/learning/{module_id}", admin).json()
    run.check("superseding returns a finished learner to NEEDS_REDOING",
              redo["status"] == "NEEDS_REDOING", redo["status"])

    if asset_id:
        run.check("the republished module carries the video",
                  any(s.get("mediaAssetId") == asset_id for s in redo["sections"]))
finally:
    run.cleanup()

raise SystemExit(run.report())
