# Android client

Native Kotlin / Jetpack Compose client. Phase 5.

## Run locally

```bash
cd android
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Play internal testing

See [RELEASING.md](RELEASING.md) for Google Play Console setup, signing, and the GitHub Action
that uploads to the internal track.
