# Phase 2b — Video — Design

Date: 2026-08-29
Status: approved, not yet implemented
Follows: [Phase 2a](2026-08-29-phase2a-modules-and-learning-design.md)

## 1. Scope

An organisation administrator uploads a video and attaches it to a module section. Clinicians
assigned that module watch it inline. Nothing else: no live streaming, no adaptive bitrate, no
DRM, no watermarking.

This completes Phase 2, alongside 2a (modules) and 2c (quizzes).

### Definition of done

An administrator uploads an MP4 from the module editor, sees it become playable once processing
finishes, and attaches it to a section. A clinician opens the module and the video plays. Somebody
who is not assigned the module cannot obtain a playback URL.

## 2. What was taken from tinderbox2_server, and what was not

`tinderbox2_server` is a video platform, so it was read before designing this rather than after.
Three of its decisions are adopted and two are deliberately not.

**Adopted: processing is asynchronous and polled.** Upload completion enqueues work; the transcode
runs elsewhere; the application discovers it finished by polling rather than by waiting. Moxion
polls MediaConvert every thirty seconds in `TranscodeExternalStep.waitForExternalTranscode()`.
Transcoding inside a request would tie up a thread for minutes.

**Adopted: separate buckets for ingest and output.** Moxion has `-upload` and `-asset`. Raw
uploads are untrusted input with a different lifecycle from processed output, and mixing them makes
both harder to reason about.

**Adopted: MediaConvert.** Moxion's *primary* path is ffmpeg, but offloaded to Lambda or Fargate
workers (`LambdaVideoProcessor`, `FargateVideoSegmentProcessingTask`) — never in the API process.
We have no worker tier, and running ffmpeg in the API container would starve a 0.5 vCPU task of CPU
for tens of minutes per video. MediaConvert is Moxion's fallback path and the one that fits here.

**Not adopted: Akamai with Redis access grants and API byte-proxying.** Moxion hands out CDN URLs,
records a grant in Redis, and proxies bytes from S3 through the API after re-checking that grant.
It is a stronger property than a signed URL — authorisation is re-evaluated per request — but it
only works because Akamai absorbs the traffic in front of it. Proxying through one small Fargate
task would make video throughput the application's bottleneck.

Instead, playback uses a **presigned S3 GET, generated per request with a short expiry**. The
trade is explicit: a presigned URL is a bearer credential for as long as it lives, so it lives for
fifteen minutes and is minted only after the same assignment check that guards the module.

**Not adopted: multipart upload.** Moxion chunks because it ingests camera masters. Training
videos are capped at 500MB, which one presigned PUT handles. If uploads prove unreliable on real
connections, multipart is the upgrade — the same `init`/`part`/`complete` shape Moxion uses.

Also not adopted, and not close to needed: HLS and DASH packaging, multi-resolution ladders, DRM,
forensic watermarking, and MediaStore.

## 3. Decisions and their rationale

### 3.1 Media is an organisation library, not a section attachment

An asset belongs to the organisation and a section references it by id. If media hung off a section
directly, opening a draft would have to copy S3 objects, and deleting a version would need
reference counting to decide whether the file is still wanted.

It also means the same briefing video can appear in several modules without being uploaded twice.

### 3.2 One rendition, 720p H.264

Multi-bitrate exists to serve viewers on unpredictable connections. These are clinicians on
hospital or home broadband watching short training videos, and a single 720p H.264 MP4 plays in
every browser without a player library.

### 3.3 Status is explicit and visible

An asset is `UPLOADING`, then `PROCESSING`, then `READY` or `FAILED`. The editor shows it, because
a video that silently does nothing for four minutes reads as broken. `FAILED` carries the reason
MediaConvert gave.

### 3.4 A section holds at most one video

Modelled as a nullable `media_asset_id` on `module_section`. Several videos in one section is a
formatting problem dressed up as a data model; an author who wants two makes two sections.

## 4. Schema

Migration `V5__media.sql`:

```sql
CREATE TABLE media_asset (
    id                UUID PRIMARY KEY,
    org_id            UUID        NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    filename          TEXT        NOT NULL,
    content_type      TEXT        NOT NULL,
    size_bytes        BIGINT,
    status            TEXT        NOT NULL CHECK (status IN ('UPLOADING','PROCESSING','READY','FAILED')),
    failure_reason    TEXT,
    upload_key        TEXT        NOT NULL,
    playback_key      TEXT,
    duration_seconds  INT,
    transcode_job_id  TEXT,
    created_by        UUID REFERENCES app_user (id) ON DELETE SET NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE module_section
    ADD COLUMN media_asset_id UUID REFERENCES media_asset (id) ON DELETE SET NULL;

CREATE INDEX idx_media_asset_org ON media_asset (org_id);
-- The poller asks only this question, several times a minute.
CREATE INDEX idx_media_asset_processing ON media_asset (status) WHERE status = 'PROCESSING';
```

`ON DELETE SET NULL` on the section reference: deleting an asset should empty the sections that used
it, not delete them.

## 5. Infrastructure

A new stack, `infra/media.yaml`:

- **Upload bucket**, private, CORS allowing `PUT` from the web origin, and a lifecycle rule expiring
  objects after seven days. Once transcoded, the source is dead weight.
- **Asset bucket**, private, no public access and no CloudFront. Reached only by presigned GET.
- **MediaConvert queue** and a role MediaConvert assumes to read the upload bucket and write the
  asset bucket, mirroring Moxion's `MoxionMediaConvertRole`.

The application's task role gains `s3:PutObject` and `s3:GetObject` on both buckets,
`mediaconvert:CreateJob` and `GetJob`, and `iam:PassRole` for the MediaConvert role only.

## 6. API surface

Authoring, `ORG_ADMIN`:

| Method | Path | Effect |
| --- | --- | --- |
| `POST` | `/api/v1/orgs/{orgId}/media` | Registers an asset, returns a presigned PUT for the browser |
| `POST` | `/api/v1/orgs/{orgId}/media/{assetId}/uploaded` | Upload finished; submits the transcode |
| `GET` | `/api/v1/orgs/{orgId}/media` | The library, with statuses |
| `DELETE` | `/api/v1/orgs/{orgId}/media/{assetId}` | Deletes the asset and empties any section using it |

Learning:

| Method | Path | Effect |
| --- | --- | --- |
| `GET` | `/api/v1/orgs/{orgId}/learning/media/{assetId}/playback` | A presigned GET, valid fifteen minutes |

The playback endpoint re-checks that the asset belongs to a section of a module assigned to one of
the caller's teams. Holding an asset id is not authorisation, and an id is guessable enough not to
be a secret.

Sections gain an optional `mediaAssetId`, set through the existing wholesale section replacement.

## 7. Processing

`MediaConvertPoller`, a Spring `@Scheduled` job every thirty seconds, mirroring Moxion's polling
interval. It reads assets in `PROCESSING`, asks MediaConvert for each job, and moves them to `READY`
with a `playback_key` and duration, or to `FAILED` with the error.

Running in the API task is acceptable because polling is a few API calls a minute, not the
transcode itself. With more than one task, several pollers would ask the same question and write
the same answer, which is wasteful but not wrong; a single-row advisory lock is the fix if the
service is ever scaled out.

## 8. Client

The module editor gains a media panel: choose a file, upload it straight to S3 against the
presigned URL with a progress bar, and watch its status. Each section gains a picker listing
`READY` assets.

The reader renders a plain `<video controls>` for a section's asset, with the playback URL fetched
when the section comes into view rather than for every section on load — fifteen-minute URLs
minted for videos nobody watches are wasted work.

Watching is not tracked. Completion remains reading sections and passing the quiz; whether a video
was actually watched is not something a `<video>` element can honestly report.

## 9. Testing

- A clinician not assigned the module cannot get a playback URL for its asset.
- Nor can a member of another organisation.
- An asset still `PROCESSING` has no playback URL.
- Registering an upload returns a presigned URL scoped to that key alone.
- The poller moves a finished job to `READY` and a failed one to `FAILED` with its reason.
- Deleting an asset empties the sections that referenced it and leaves them otherwise intact.
- A rejected content type never reaches S3.

MediaConvert is stubbed in tests behind the interface the service depends on. The contract test
against a real emulator that Floci gives us for SES has no MediaConvert equivalent.

## 10. Out of scope

Adaptive bitrate, thumbnails, watch tracking, resumable uploads, and any file type other than
video.

**Captions were considered for this phase and deliberately deferred to the next one.** A video
whose content is carried in narration is unusable to anyone deaf or hard of hearing, and to anyone
watching at a ward station without headphones — which is most of a hospital. Some of the clinicians
this trains will themselves be hard of hearing, and WCAG treats captions on pre-recorded video as
a baseline rather than an enhancement.

The cost of deferring is not the implementation, which is a WebVTT file beside the video and a
`<track>` element. It is that every module published before captions arrive has to be revisited,
with people having watched uncaptioned content meanwhile. Worth doing soon and worth doing before
the pilot widens.

## 11. Risks

| Risk | Mitigation |
| --- | --- |
| A presigned URL is forwarded | Fifteen minutes, minted per request after the assignment check |
| Transcode fails silently | Explicit `FAILED` status carrying MediaConvert's reason, shown in the editor |
| Upload bucket accumulates cost | Lifecycle expiry after seven days |
| Several tasks poll the same job | Harmless duplicate writes today; advisory lock if scaled out |
| Video without captions excludes people | Named as the first thing to add after this |
