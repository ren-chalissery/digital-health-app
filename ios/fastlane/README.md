# Releasing to TestFlight

Three things have to exist before any of this works, and none of them can be automated — they are
Apple account steps behind a login only you have.

## 1. An Apple ID with access to team `UJY6H4M6AZ`, signed into Xcode

Without it, archiving fails immediately:

```
error: No Account for Team "UJY6H4M6AZ". Add a new account in Accounts settings
```

The only signing identity on this machine belongs to team `PC85YM5JUY`, which is a different team.
Xcode → Settings → Accounts → add the Apple ID that has access to `UJY6H4M6AZ`.

## 2. The bundle identifier registered

```
error: No profiles for 'io.simplicity.training' were found
```

Once the account above is signed in, automatic signing will register
`io.simplicity.training` and create the profile the first time you archive from Xcode. Doing it
once by hand in Xcode is the quickest path; after that `fastlane beta` can run unattended.

## 3. An App Store Connect API key

App Store Connect → Users and Access → Integrations → App Store Connect API → generate a key with
the **App Manager** role.

**The `.p8` downloads exactly once.** There is no way to retrieve it again; a lost key means
revoking it and issuing another.

```bash
mkdir -p ~/.private_keys
mv ~/Downloads/AuthKey_XXXXXXXXXX.p8 ~/.private_keys/
```

Then, in your shell profile:

```bash
export APPLE_KEY_ID=XXXXXXXXXX       # the key id, also in the filename
export APPLE_ISSUER_ID=...           # shown once at the top of the Integrations page
```

A key rather than an Apple ID and password: it survives two-factor authentication, it is scoped to
what it needs, and it can be revoked without touching the account.

## 4. An app icon

The asset catalogue has no `AppIcon`, and App Store Connect rejects a build without one. This is a
brand decision rather than a technical one, so it is deliberately not filled in with something
generated — a 1024×1024 PNG with **no alpha channel** dropped into
`Simplicity_iOS/Assets.xcassets/AppIcon.appiconset` is all it needs.

## Then

```bash
cd ios

fastlane test    # every package's tests, then the app's
fastlane build   # a signed archive, no upload
fastlane beta    # archive and upload to TestFlight
```

`beta` sets the build number from the wall clock, because App Store Connect refuses a build number
it has seen before and discovering that after a ten-minute archive is a poor way to learn it.

## What CI does and does not do

The `ios` job in [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml) builds and tests on
every push that touches `ios/` or `api-contract/`. It does **not** upload: a build reaching
TestFlight should be a deliberate act, and a runner has no reason to hold signing credentials.

It pins Xcode explicitly rather than taking the image default, which moves — it went from 26.5 to
26.6 in July — and a runner image update should not silently change the compiler.
