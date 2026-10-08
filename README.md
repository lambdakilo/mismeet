# Mismeet

Location sharing in the style of Find My, built on Nostr, for iOS and Android. Each phone
publishes its own location to Nostr relays as one addressable event, encrypted separately for
each approved contact and carrying an expiration. Contacts fetch the latest event when they want
to see where someone is. There is no server of its own, no push service and no bridge, and the
social graph never appears in plaintext. The Android app has no Google Play Services dependency,
so it runs on GrapheneOS as well as on stock Android.

## Status

The protocol is specified and both apps build with the protocol core and its conformance
tests, a device key, local contact and relay storage, relay publishing and fetching, location
monitoring and a first set of screens. Neither app has been exercised end to end on a phone
yet. Invites are scanned as QR codes or pasted as text.

## Files

- [`spec/SPEC.md`](spec/SPEC.md): the protocol: event kind and tags, encryption and padding,
  expiration, relay handling, the contact model, client behaviour and the threat model.
- [`CLAUDE.md`](CLAUDE.md): the rules and the commands Claude Code follows in this repository.
- `ios/`: the Swift and SwiftUI app, generated from `ios/project.yml` with XcodeGen.
- `android/`: the Kotlin app and the `protocol` module it builds on.
- [`LICENSE`](LICENSE) and [`LICENSE-CC-BY-SA-4.0`](LICENSE-CC-BY-SA-4.0): the licence texts,
  see below.

## Development

### Requirements

- iOS: a Mac with Xcode 27 (the iOS 27 SDK; the deployment target is iOS 18), `xcodegen` and
  `xcbeautify` from Homebrew, an Apple ID (a free one is enough: the app uses no push and no
  special entitlement) and an iPhone on iOS 18 or later. The test device is an iPhone XR.
- Android: a JDK 17 or later (Temurin 21 here), the Android command line tools with
  `platforms;android-36`, `build-tools;36.0.0` and `platform-tools`, and a phone on Android 12
  or later. The test devices are a phone on GrapheneOS and a Motorola Moto G200 5G on Android
  12. Gradle comes from the checked-in wrapper; no Android Studio is needed.

### Set up once: iOS

1. Sign in to Xcode with the Apple ID under Xcode > Settings > Apple Accounts. Signing in
   alone creates nothing: a free account gets its personal team, certificate and profile only
   once a project asks for them, so `security find-identity` shows no identity yet.

2. Generate the project and open it in Xcode:

   ```bash
   xcodegen generate --spec ios/project.yml && open ios/Mismeet.xcodeproj
   ```

   Select the Mismeet target, then Signing & Capabilities, and pick the team named after you
   with "(Personal Team)". Xcode registers the app id and creates the development certificate
   and profile. Do the same for the MismeetTests target. Xcode writes the team id into the
   generated project; copy it to `ios/.team-id` (git-ignored, one line), which the command
   line builds read, since the next `xcodegen generate` discards the project's copy:

   ```bash
   grep -m1 -oE 'DEVELOPMENT_TEAM = [A-Z0-9]{10}' ios/Mismeet.xcodeproj/project.pbxproj | cut -d' ' -f3 | tee ios/.team-id
   ```

3. On the iPhone, enable Settings > Privacy & Security > Developer Mode, connect it by USB and
   trust the Mac. List it, then write its identifier (the first column) to `ios/.device-id`
   (git-ignored, one line):

   ```bash
   xcrun devicectl list devices
   ```

### Set up once: Android

1. Point Gradle at the SDK (`android/local.properties` is git-ignored); the path below is the
   Homebrew one:

   ```bash
   printf 'sdk.dir=/opt/homebrew/share/android-commandlinetools\n' > android/local.properties
   ```

2. Put `adb` on the PATH:

   ```bash
   ln -s /opt/homebrew/share/android-commandlinetools/platform-tools/adb /opt/homebrew/bin/adb
   ```

3. On the phone: Settings > About phone, tap Build number seven times, then Settings > System >
   Developer options > USB debugging. Connect by USB, accept the fingerprint prompt on the
   phone, and check that it shows as `device`:

   ```bash
   adb devices
   ```

### Build, test and run

iOS, after cloning and after every edit to `ios/project.yml`:

```bash
xcodegen generate --spec ios/project.yml
```

Build for the simulator (`xcrun simctl list devices available` lists the simulators; the
generic destination builds without booting one):

```bash
set -o pipefail && xcodebuild -project ios/Mismeet.xcodeproj -scheme Mismeet -destination 'platform=iOS Simulator,name=iPhone 17' -derivedDataPath ios/build build | xcbeautify
```

Unit tests:

```bash
set -o pipefail && xcodebuild -project ios/Mismeet.xcodeproj -scheme Mismeet -destination 'platform=iOS Simulator,name=iPhone 17' -derivedDataPath ios/build test | xcbeautify
```

Run on the simulator: boot one, show it, build for it as above, then install and launch:

```bash
xcrun simctl bootstatus "iPhone 17" -b && open -a Simulator
```

```bash
xcrun simctl install booted ios/build/Build/Products/Debug-iphonesimulator/Mismeet.app && xcrun simctl launch booted app.mismeet.ios
```

Build for the connected iPhone. The destination names the phone on purpose: a free team has
no registered devices, and only a build for a specific device registers it and creates the
provisioning profile; a `generic/platform=iOS` build fails with "no devices".

```bash
set -o pipefail && xcodebuild -project ios/Mismeet.xcodeproj -scheme Mismeet -destination "platform=iOS,id=$(cat ios/.device-id)" -derivedDataPath ios/build -allowProvisioningUpdates DEVELOPMENT_TEAM="$(cat ios/.team-id)" build | xcbeautify
```

Install and launch on the device (add `--console` to the launch to stream its output). The
first launch is refused until the phone trusts the personal team: Settings > General > VPN &
Device Management, tap the developer app entry, Trust. After that, launching works.

```bash
xcrun devicectl device install app --device "$(cat ios/.device-id)" ios/build/Build/Products/Debug-iphoneos/Mismeet.app && xcrun devicectl device process launch --device "$(cat ios/.device-id)" --terminate-existing app.mismeet.ios
```

Android, build:

```bash
android/gradlew -p android assembleDebug
```

Lint and unit tests:

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

### Notes

- Free Apple ID: the provisioning profile expires after seven days, so rebuild and reinstall
  weekly, and at most ten app ids can be registered per seven days.
- GrapheneOS has no Google location services: the app uses GPS, and the network provider only
  when GrapheneOS's own network location is turned on in Settings.
- Stock Android, and Android 12 in particular, stops background services it considers
  wasteful. The Me screen offers the battery optimisation exemption; grant it, or the sharing
  service will not survive the screen being off for long.
- Android asks for location "while in use" first and "all the time" only from the app's
  settings page; the Me screen walks through both.

## License

Prose, meaning `spec/SPEC.md`, `CLAUDE.md` and this README, is licensed under Creative Commons
Attribution-ShareAlike 4.0 International. See [`LICENSE-CC-BY-SA-4.0`](LICENSE-CC-BY-SA-4.0).

Everything else is licensed under the GNU Affero General Public License, version 3 or (at your
option) any later version. See [`LICENSE`](LICENSE).
