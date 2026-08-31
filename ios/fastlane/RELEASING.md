# Releasing to TestFlight

> Not `README.md`. fastlane **overwrites** `fastlane/README.md` with a generated lane list on every
> run, and it silently destroyed this document once — including corrections merged twenty minutes
> earlier. Anything hand-written has to live under a name fastlane does not own.

Five things have to exist before any of this works, and only signing is done. The rest are Apple
account and brand steps behind a login only you have.

## 0. fastlane itself

Not installed on this machine, so none of the lanes below have ever run:

```bash
brew install fastlane
```

## 1. An Apple ID with access to team `UJY6H4M6AZ`, signed into Xcode — **done**

Xcode → Settings → Accounts. Confirmed working: an archive on 31 August 2026 signed as

```
TeamIdentifier = UJY6H4M6AZ
Authority      = Apple Development: Ren Chalissery (U8F22V4YCJ)
```

## 2. The bundle identifier registered **explicitly**

Automatic signing does **not** do this, whatever a successful archive suggests. It reaches for the
team's wildcard App ID instead, and the profile name says so:

```
iOS Team Provisioning Profile: *   ->   UJY6H4M6AZ.*
```

A wildcard signs a development build perfectly well, so the archive succeeds and nothing looks
wrong. But an App Store Connect app record can only be created against an **explicit** App ID, so
`io.simplicity.training` never appears in the bundle-id dropdown and TestFlight is unreachable.

Register it by hand at
[Certificates, Identifiers & Profiles → Identifiers](https://developer.apple.com/account/resources/identifiers/list):
**+** → App IDs → App → **Explicit** → `io.simplicity.training`. No capabilities are needed.

Separately, from the command line, signing needs `-allowProvisioningUpdates`, without which it
fails with `No profiles for 'io.simplicity.training' were found ... Automatic signing is disabled`.
Xcode passes it for you; `xcodebuild` does not.

## 2b. The app record in App Store Connect

Also not automatic, and distinct from the App ID above.
[App Store Connect → Apps](https://appstoreconnect.apple.com/apps) → **+** → New App, selecting the
explicit bundle id from the dropdown.

App names are unique across the whole store, so "Simplicity" is likely taken. The store name is
independent of `CFBundleDisplayName`, which stays "Simplicity" on the home screen.

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

## 4. An app icon — **still missing**

The asset catalogue has no `AppIcon.appiconset` at all, and the archive confirms it: the built
`Simplicity_iOS.app` contains no icon. App Store Connect rejects a build without one.

This is a brand decision rather than a technical one, so it is deliberately not filled in with
something generated — a 1024×1024 PNG with **no alpha channel** dropped into
`Simplicity_iOS/Assets.xcassets/AppIcon.appiconset` is all it needs.

Whatever the mark is, shrink it to 40 pixels before committing to it. That is the size it is
actually seen at, and it is where thin strokes and fine detail disappear.

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
