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

The GitHub Action uploads through the API; that needs a service account linked to Play Console.

1. Play Console → **Setup → API access** → link or create a Google Cloud project.
2. Google Cloud Console → **IAM & Admin → Service accounts** → create a service account
   (e.g. `play-upload@…`).
3. Create a **JSON key** and download it once.
4. Back in Play Console → **API access** → grant that service account access to the app with
   **Release to production, exclude devices, and use Play App Signing** (or Admin for simplicity
   on a solo project).

Store the JSON locally at `~/.config/simplicity/play-service-account.json` for `fastlane`, or only
in GitHub Actions secrets (below).

Enable **Google Play Android Developer API** in the linked Cloud project if it is not already on.

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
