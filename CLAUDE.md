# Mismeet

Nostr-based location sharing for iOS and Android, in the style of Find My, with no server of
its own. The protocol lives in [spec/SPEC.md](spec/SPEC.md) and is the source of truth: app code
follows it, and a protocol change edits the spec first, in the same change.

## Layout

- `spec/`: the protocol specification.
- `ios/`: Swift and SwiftUI app. XcodeGen generates `ios/Mismeet.xcodeproj` from
  `ios/project.yml`. Never edit the `.pbxproj`; change `project.yml` and regenerate. Deployment
  target iOS 18. Location comes from significant location change monitoring with the "Always"
  permission, and the app publishes on each wakeup. Under `ios/Mismeet/`: `Protocol/` (the
  wire format, mirrored in Kotlin), `App/` (the model and the app delegate that starts
  monitoring on a background launch), `Identity/` (Keychain), `Store/` (JSON state in
  Application Support), `Nostr/` (one short-lived client per publish or fetch), `Location/`,
  `UI/`.
- `android/`: Kotlin, built with the Gradle wrapper checked into `android/`. Two modules:
  `app` (the Android app, Compose) and `protocol` (a plain JVM library holding the protocol core
  and its tests). `compileSdk` and `targetSdk` are 36, `minSdk` is 31 so that stock Android 12
  phones run it. No Google Play Services dependency: location comes from the platform
  `LocationManager` inside a foreground service that publishes on a fixed timer, never from the
  fused provider. Under `android/app/src/main/kotlin/app/mismeet/android/`: `AppModel.kt`,
  `identity/` (a Keystore wrapped secret key), `store/` (JSON state in the files directory),
  `nostr/`, `location/` (the provider, the foreground service and the boot receiver), `ui/`.
  The map tab draws OpenStreetMap tiles through osmdroid, which needs the package name as user
  agent; a contact row also opens the position in whatever map app handles `geo:` URIs.

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

## Building and running

The prerequisites and the one-time setup (Apple ID and team id, device identifier, SDK path,
`adb`, developer options on the phones) are in README.md under Development, written for
people; they do not belong here. The commands below assume that setup is done, so that
`ios/.team-id`, `ios/.device-id` and `android/local.properties` exist. The README lists the
same commands for people: change both together.

### iOS

Generate the project after cloning and after every edit to `ios/project.yml`:

```bash
xcodegen generate --spec ios/project.yml
```

Build for the simulator:

```bash
set -o pipefail && xcodebuild -project ios/Mismeet.xcodeproj -scheme Mismeet -destination 'generic/platform=iOS Simulator' -derivedDataPath ios/build build | xcbeautify
```

Unit tests (iPhone 17 is one of the installed simulators; `xcrun simctl list devices available`
lists them):

```bash
set -o pipefail && xcodebuild -project ios/Mismeet.xcodeproj -scheme Mismeet -destination 'platform=iOS Simulator,name=iPhone 17' -derivedDataPath ios/build test | xcbeautify
```

Build for the device:

```bash
set -o pipefail && xcodebuild -project ios/Mismeet.xcodeproj -scheme Mismeet -destination 'generic/platform=iOS' -derivedDataPath ios/build -allowProvisioningUpdates DEVELOPMENT_TEAM="$(cat ios/.team-id)" build | xcbeautify
```

Install and launch on the device:

```bash
xcrun devicectl device install app --device "$(cat ios/.device-id)" ios/build/Build/Products/Debug-iphoneos/Mismeet.app && xcrun devicectl device process launch --device "$(cat ios/.device-id)" --terminate-existing app.mismeet.ios
```

Add `--console` to the launch command to stream the app's stdout until Ctrl-C. Leave it out
when testing background wakeups, so the app runs detached as it would for a user.

Device logs: Console.app, select the iPhone, filter on process `Mismeet`. Simulator logs:

```bash
xcrun simctl spawn booted log stream --level debug --predicate 'subsystem == "app.mismeet.ios"'
```

Simulated movement, one position or a built-in scenario (`xcrun simctl location booted list`
names them):

```bash
xcrun simctl location booted set 60.1699,24.9384
```

```bash
xcrun simctl location booted run "Freeway Drive"
```

- Significant location change delivery on the simulator is unreliable; the real test is a walk
  or drive with the iPhone.
- Background location needs `UIBackgroundModes` with `location` and the two location usage
  strings in `Info.plist`, defined in `project.yml`. It is not an entitlement, so it works with
  the free team.
- Always pass `-derivedDataPath ios/build`, so the app path in the install command stays valid.
- The free team's profile lasts seven days and at most ten app ids can be registered per seven
  days, so never churn the bundle identifier.

### Android

Build:

```bash
android/gradlew -p android assembleDebug
```

Lint and unit tests, the verify command; it runs lint on the app and the unit tests of both
modules:

```bash
android/gradlew -p android lint test
```

Install and launch on the phone:

```bash
android/gradlew -p android installDebug && adb shell am start -n app.mismeet.android/.MainActivity
```

Logs:

```bash
adb logcat --pid="$(adb shell pidof -s app.mismeet.android)"
```

Relay integration test: publishes a location event and a relay list under throwaway keys to
the default public relays and reads them back. Skipped unless the variable is set, so the
verify command stays offline.

```bash
MISMEET_RELAY_TESTS=1 android/gradlew -p android :protocol:test --tests 'app.mismeet.protocol.RelayIntegrationTest'
```

Instrumented tests on the phone:

```bash
android/gradlew -p android connectedDebugAndroidTest
```

- Build with the checked-in wrapper only, never the Homebrew `gradle` (it is Gradle 9.8 on JDK
  27, which the Android Gradle Plugin does not support); the wrapper pins the version the plugin
  supports.
- GrapheneOS has no Google location services. The network location provider exists only when the
  user turns on GrapheneOS's own network location in Settings, so rely on `GPS_PROVIDER` and
  treat `NETWORK_PROVIDER` as optional.
- `minSdk` is 31: `POST_NOTIFICATIONS` exists from API 33, so permission checks for it are gated
  on the API level, and stock Android 12 needs the battery optimisation exemption the Me screen
  offers or it stops the foreground service.
- Background location is a foreground service with `foregroundServiceType="location"`. It needs
  `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `ACCESS_FINE_LOCATION`,
  `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION` and `POST_NOTIFICATIONS`. Android
  grants "while in use" first and "all the time" only from the app's settings page, so the app
  has to send the user there.
- The Nostr Dev Kit AAR ships native libraries for every ABI; the phones are arm64-v8a. A native
  crash at startup on GrapheneOS is most likely its hardened memory allocator: check the
  per-app exploit protection compatibility mode before debugging anything else, and report it.
- AndroidX is pinned to the last versions that compile against API 36; the newer ones demand
  compileSdk 37 and lint flags the pins as outdated. Compose material icons are a separate
  dependency, `material-icons-core`, not pulled in by material3.

## Verify after edits

Run before saying a change is done, not only before committing:

- iOS: the simulator build and the unit tests above.
- Android: the lint and unit test command above.

Stop what was started: `android/gradlew -p android --stop` for the Gradle daemon and
`xcrun simctl shutdown all` for simulators, so nothing keeps running when the turn ends.

## Git-ignored paths

`.gitignore` covers the generated Xcode project, build output, the Gradle caches and the
local-only files named above (`ios/.team-id`, `ios/.device-id`, `android/local.properties`).
Anything else that is generated or machine-specific goes there too, never into a commit.
