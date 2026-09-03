# Releasing to Google Play internal testing

Android’s equivalent of TestFlight is **Play Console → Internal testing**: up to 100 testers, no
review, install via a link or the Play Store app.

Five things must exist. Only the upload keystore and the service-account JSON are secret; the rest
are either in this repo or one-time console clicks behind a Google login only you have.

## 0. What is already in the repo

| Item | Location |
| --- | --- |
| Application id | `io.simplicity.training` — same as iOS |
| Cognito Android client | `app/src/main/res/raw/amplifyconfiguration.json` |
| Play track | `internal` in [`play-store.env`](play-store.env) |
| Launcher icons | `app/src/main/res/mipmap-*/` (from the iOS app icon) |
| High-res store icon (512×512) | [`play-store-icon-512.png`](play-store-icon-512.png) for the listing |
| Release workflow | [`.github/workflows/android-play-internal.yml`](../.github/workflows/android-play-internal.yml) |

The native app is still early — **sign-in works**; Learn, Reflect and the rest arrive in later
slices. Internal testing is for exercising auth and the shell on real devices.

---

## 1. Google Play developer account — **you**

1. Open [Google Play Console](https://play.google.com/console).
2. Pay the **one-time USD 25** registration fee if prompted.
3. Complete identity and developer profile verification (can take a day).

Nothing in git can do this step.

---

## 2. Create the app record — **you**

Play Console → **Create app**:

| Field | Value |
| --- | --- |
| App name | Simplicity (or “Simplicity Training” if the short name is taken) |
| Default language | English (Australia) or English (United States) |
| App or game | App |
| Free or paid | Free |

When asked for the **Android package name**, enter exactly:

```text
io.simplicity.training
```

You cannot change the package name later. It must match `applicationId` in `app/build.gradle.kts`.

Complete the mandatory policy questionnaires (App access, Ads, Content rating, Target audience,
Data safety). For internal testing many can be minimal/draft, but **Content rating** and **Data
safety** usually block publishing until submitted.

Under **Store presence → Main store listing**, upload [`play-store-icon-512.png`](play-store-icon-512.png)
as the app icon if the console asks before the first upload.

Phone screenshots and the feature graphic live in
[`store-assets/screenshots/`](store-assets/screenshots/) (1080×1920 PNGs plus a 1024×500 banner).
Regenerate from the HTML mockups with `android/store-assets/generate-screenshots.mjs` if the UI
changes.

---

## 3. Upload keystore — **once**

Google Play App Signing holds the **app signing key**. You keep an **upload key** that signs each
AAB you upload.

```bash
cd android
chmod +x scripts/create-upload-keystore.sh
./scripts/create-upload-keystore.sh
```

This creates `upload-keystore.jks` and `keystore.properties` (both gitignored). **Back them up**
— losing the upload key requires a Play Console reset.

---

## 4. Play Developer API service account — **you**

Google removed **Setup → API access**. Access is granted by inviting a Google Cloud
service account as a user.

1. [Google Cloud Console](https://console.cloud.google.com/) → create or pick a project.
2. Enable [Google Play Android Developer API](https://console.cloud.google.com/apis/library/androidpublisher.googleapis.com).
3. **IAM & Admin → Service accounts → Create service account** (no GCP roles required).
4. **Keys → Add key → JSON** → download once. Note the service account email
   (`…@….iam.gserviceaccount.com`).
5. [Play Console](https://play.google.com/console) → **Users and permissions** → **Invite new
   users** → paste that email.
6. **App permissions** → add **Simplicity Training** (`io.simplicity.training`) → enable at least
   **View app information** and **Manage testing tracks and releases** (Admin is fine on a solo
   account).

Store the JSON locally at `~/.config/simplicity/play-service-account.json` for `fastlane`, or only
in GitHub Actions secrets (below). Permissions can take a few minutes to propagate.

---

## 5. GitHub Actions secrets

After steps 3–4, add **repository secrets** (Settings → Secrets and variables → Actions):

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | `base64 < android/upload-keystore.jks` (single line, no wraps) |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password from step 3 |
| `ANDROID_KEY_ALIAS` | `upload` |
| `ANDROID_KEY_PASSWORD` | Key password from step 3 |
| `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` | Full service-account JSON file contents |

Public identifiers stay in [`play-store.env`](play-store.env) — do not duplicate them as secrets.

---

## 6. Release

### From GitHub (recommended)

Actions → **Release Android to Play internal testing** → Run workflow → branch `main`.

The job runs unit tests, builds a signed `.aab`, uploads to the **internal** track, and prints
recent version codes.

### From your Mac

```bash
cd android
export GOOGLE_PLAY_JSON_KEY_PATH=~/.config/simplicity/play-service-account.json
export VERSION_CODE=$(date -u +%Y%m%d%H%M)

fastlane android internal      # build + upload
fastlane android releases      # what the track holds
```

`VERSION_CODE` must increase on every upload. CI uses `github.run_number`.

---

## 7. Add testers

Play Console → **Testing → Internal testing**:

1. Create or open the **Internal testing** release (the workflow uploads here).
2. **Testers** tab → create an email list → add addresses.
3. Copy the **opt-in URL** and open it on each Android phone (signed into Google Play with that
   email).
4. Install **Simplicity** from the Play Store when prompted.

Unlike TestFlight, there is no separate “individual testers” gotcha — the opt-in link is what
matters.

---

## What CI does and does not do

The `android` job in [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) runs unit tests and
`assembleDebug` when `android/` changes. It does **not** upload to Play.

Only the manual **Release Android to Play internal testing** workflow distributes builds.

---

## Troubleshooting

| Symptom | Likely cause |
| --- | --- |
| “Package not found” on upload | App record in step 2 not created for `io.simplicity.training` |
| 403 from Play API | Service account not invited under API access, or wrong JSON |
| Upload rejected: version code | `VERSION_CODE` not higher than the last upload — bump and retry |
| “You need to use a different package name” | Console app used a different id than `io.simplicity.training` |
| Testers see nothing | They have not opened the internal-testing opt-in URL |
