# Mismeet

Nostr-based location sharing for iOS and GrapheneOS, in the style of Find My, with no server of
its own. The protocol lives in [spec/SPEC.md](spec/SPEC.md) and is the source of truth: app code
follows it, and a protocol change edits the spec first, in the same change.

## Layout

- `spec/`: the protocol specification.
- `ios/`: Swift and SwiftUI app. XcodeGen generates `ios/Mismeet.xcodeproj` from
  `ios/project.yml`. Never edit the `.pbxproj`; change `project.yml` and regenerate. Deployment
  target iOS 18. Location comes from significant location change monitoring with the "Always"
  permission, and the app publishes on each wakeup.
- `android/`: Kotlin, built with the Gradle wrapper checked into `android/`. Two modules:
  `app` (the Android app, Compose) and `protocol` (a plain JVM library holding the protocol core
  and its tests). `compileSdk` and `targetSdk` are 36, `minSdk` is 34. No Google Play Services
  dependency: location comes from the platform `LocationManager` inside a foreground service,
  never from the fused provider.

## Shared rules

- Both apps use the Nostr Dev Kit bindings (the project formerly called rust-nostr; its GitHub
  organisation is now `nostrdevkit`). Keep both platforms on the same version so protocol
  behaviour matches. Current version: 0.45.1.
  - iOS: Swift package product `NostrSDK` from `https://github.com/nostrdevkit/nostr-sdk-swift`,
    pinned to an exact version in `project.yml`.
  - Android: `org.nostrdevkit:nostr-sdk:0.45.1` from Maven Central, Kotlin package
    `org.nostrdevkit.sdk`. The `protocol` module compiles and tests against the JVM flavour
    `org.nostrdevkit:nostr-sdk-jvm`, which carries macOS native libraries, so its tests run on
    this Mac; the `app` module excludes that jar and uses the Android AAR, which has the same
    API.
- Protocol constants (kind, `d` tag, expiration, buckets, timeouts) live in
  `ios/Mismeet/Protocol/ProtocolConstants.swift` and
  `android/protocol/src/main/kotlin/app/mismeet/protocol/ProtocolConstants.kt` and match the
  table in the spec, section 10. The conformance checklist in section 12 is a unit test on each
  platform, under `ios/MismeetTests` and `android/protocol/src/test`.
- Nothing but kinds `31122` and `10002` is ever published. No profile, no contact list, no DMs.
- Never log a secret key, a decrypted payload or a contact's public key above debug level.
- Bundle identifier `app.mismeet.ios`, Android `applicationId` `app.mismeet.android`. Changing
  one means changing it here and in the project files in the same commit.
- Pipe every `xcodebuild` through `xcbeautify` with `pipefail` set, as in the commands below.

## iOS

Prerequisites on this Mac: Xcode 27 with the iOS 27 SDK, `xcodegen` 2.46 and `xcbeautify` 3.2
from Homebrew. The test device is an iPhone XR on iOS 18, signed with a free Apple ID personal
team, so there is no push, no app group and no other special entitlement.

### Set up once

1. Sign in to Xcode with the Apple ID under Xcode > Settings > Accounts. That creates the
   personal team. The first device build with `-allowProvisioningUpdates` then creates the
   development certificate and profile. Find the team id, the ten characters in parentheses at
   the end of the identity name:

   ```bash
   security find-identity -v -p codesigning
   ```

   Write it to `ios/.team-id` (git-ignored, one line).

2. On the iPhone, enable Settings > Privacy & Security > Developer Mode, connect it by USB and
   trust the Mac. List it, then write its identifier (the first column) to `ios/.device-id`
   (git-ignored, one line):

   ```bash
   xcrun devicectl list devices
   ```

### Generate the project

After cloning and after every edit to `ios/project.yml`:

```bash
xcodegen generate --spec ios/project.yml
```

### Build for the simulator

```bash
set -o pipefail && xcodebuild -project ios/Mismeet.xcodeproj -scheme Mismeet -destination 'generic/platform=iOS Simulator' -derivedDataPath ios/build build | xcbeautify
```

### Unit tests

iPhone 17 is one of the installed simulators; `xcrun simctl list devices available` lists them.

```bash
set -o pipefail && xcodebuild -project ios/Mismeet.xcodeproj -scheme Mismeet -destination 'platform=iOS Simulator,name=iPhone 17' -derivedDataPath ios/build test | xcbeautify
```

### Build for the device

```bash
set -o pipefail && xcodebuild -project ios/Mismeet.xcodeproj -scheme Mismeet -destination 'generic/platform=iOS' -derivedDataPath ios/build -allowProvisioningUpdates DEVELOPMENT_TEAM="$(cat ios/.team-id)" build | xcbeautify
```

### Install and launch on the device

```bash
xcrun devicectl device install app --device "$(cat ios/.device-id)" ios/build/Build/Products/Debug-iphoneos/Mismeet.app && xcrun devicectl device process launch --device "$(cat ios/.device-id)" --terminate-existing app.mismeet.ios
```

Add `--console` to the launch command to stream the app's stdout until Ctrl-C. Leave it out
when testing background wakeups, so the app runs detached as it would for a user.

### Logs

Device logs: Console.app, select the iPhone, filter on process `Mismeet`. Simulator logs:

```bash
xcrun simctl spawn booted log stream --level debug --predicate 'subsystem == "app.mismeet.ios"'
```

### Simulated movement

Set one position on the booted simulator:

```bash
xcrun simctl location booted set 60.1699,24.9384
```

Or run one of the built-in scenarios (`xcrun simctl location booted list` names them):

```bash
xcrun simctl location booted run "Freeway Drive"
```

Significant location change delivery on the simulator is unreliable; the real test is a walk or
drive with the iPhone.

### Gotchas

- Free Apple ID: provisioning profiles expire after seven days, so rebuild and reinstall weekly.
  At most ten app ids can be registered per seven days, so do not churn the bundle identifier.
- Background location needs `UIBackgroundModes` with `location` and the two location usage
  strings in `Info.plist`, defined in `project.yml`. It is not an entitlement, so it works with
  the free team.
- Always pass `-derivedDataPath ios/build`, so the app path in the install command stays valid.

## Android

Prerequisites on this Mac: Temurin JDK 21 (what `java` on the PATH resolves to), and the Android
command line tools from Homebrew at `/opt/homebrew/share/android-commandlinetools`, with
`platforms;android-36`, `build-tools;36.0.0` and `platform-tools` installed. Build with the
checked-in wrapper only, never the Homebrew `gradle` (it is Gradle 9.8 on JDK 27, which the
Android Gradle Plugin does not support); the wrapper pins the version the plugin supports.

### Set up once

Point Gradle at the SDK (`android/local.properties` is git-ignored):

```bash
printf 'sdk.dir=/opt/homebrew/share/android-commandlinetools\n' > android/local.properties
```

Put `adb` on the PATH:

```bash
ln -s /opt/homebrew/share/android-commandlinetools/platform-tools/adb /opt/homebrew/bin/adb
```

On the GrapheneOS phone: Settings > About phone, tap Build number seven times, then
Settings > System > Developer options > USB debugging. Connect by USB, accept the fingerprint
prompt on the phone, and check that the phone shows as `device`:

```bash
adb devices
```

### Build

```bash
android/gradlew -p android assembleDebug
```

### Lint and unit tests

Runs lint on the app and the unit tests of both modules.

```bash
android/gradlew -p android lint test
```

### Install and launch on the phone

```bash
android/gradlew -p android installDebug && adb shell am start -n app.mismeet.android/.MainActivity
```

### Logs

```bash
adb logcat --pid="$(adb shell pidof -s app.mismeet.android)"
```

### Instrumented tests on the phone

```bash
android/gradlew -p android connectedDebugAndroidTest
```

### Gotchas

- GrapheneOS has no Google location services. The network location provider exists only when the
  user turns on GrapheneOS's own network location in Settings, so rely on `GPS_PROVIDER` and
  treat `NETWORK_PROVIDER` as optional.
- Background location is a foreground service with `foregroundServiceType="location"`. It needs
  `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `ACCESS_FINE_LOCATION`,
  `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION` and `POST_NOTIFICATIONS`. Android
  grants "while in use" first and "all the time" only from the app's settings page, so the app
  has to send the user there.
- The Nostr Dev Kit AAR ships native libraries for every ABI; the phone is arm64-v8a. A native
  crash at startup on GrapheneOS is most likely its hardened memory allocator: check the
  per-app exploit protection compatibility mode before debugging anything else, and report it.

## Verify after edits

Run before saying a change is done, not only before committing:

- iOS: the simulator build and the unit tests above.
- Android: the lint and unit test command above.

## Git-ignored paths

`.gitignore` covers the generated Xcode project, build output, the Gradle caches and the
local-only files named above (`ios/.team-id`, `ios/.device-id`, `android/local.properties`).
Anything else that is generated or machine-specific goes there too, never into a commit.
