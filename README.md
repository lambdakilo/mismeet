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

### Android emulator

An emulator image without Google APIs, in line with the app's independence from Play Services.
Install the emulator and the image once, and create a virtual device:

```bash
sdkmanager "emulator" "system-images;android-36;default;arm64-v8a"
```

```bash
avdmanager create avd --name mismeet --package "system-images;android-36;default;arm64-v8a" --device pixel_7
```

Start it; it keeps running in that terminal, and `-no-window` runs it without a window:

```bash
/opt/homebrew/share/android-commandlinetools/emulator/emulator -avd mismeet
```

It then counts as the connected phone for the install and launch commands below. Set its
position (longitude first) and take a screenshot:

```bash
adb emu geo fix 24.9384 60.1699
```

```bash
adb exec-out screencap -p > emulator.png
```

### Testing without a second phone

A fake publisher on the Mac stands in for the iPhone. The watching app prints its own invite
at launch, `Invite: nostr:nprofile…`, to logcat on Android and to the simulator's log on iOS,
and shows it on the Me screen:

```bash
adb logcat -d -s Mismeet:I
```

```bash
xcrun simctl spawn booted log show --info --last 5m --predicate 'subsystem == "app.mismeet.ios"'
```

Publish a location for that invite; the tool prints the fake publisher's own invite and a QR
code to add on the watching app, and keeps its key under `android/protocol/build/` so that
later runs move the same contact. `MISMEET_FAKE_NAME=other` in the environment makes a second
publisher, and several readers go comma-separated:

```bash
android/gradlew -p android -q :protocol:fakePublish -PfakeArgs="<invite> 60.1699 24.9384"
```

Debug builds also take contacts at launch, so the simulator and the emulator need no typing;
the invites are separated by semicolons:

```bash
SIMCTL_CHILD_MISMEET_TEST_INVITES="<invite>;<invite>" xcrun simctl launch booted app.mismeet.ios
```

```bash
adb shell "am start -n app.mismeet.android/.MainActivity --es invites '<invite>;<invite>'"
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
  settings page; the Me screen walks through both, but only once "Share my location" is on.
  Off, which is the default, the phone only watches its contacts and needs no permission
  beyond the camera for scanning a QR code.

### First test between two phones

The iPhone publishes and the Android phone watches. Each phone scans the other's invite once:
the iPhone's scan tells it whom to encrypt for, the Android phone's scan tells it whose events
to fetch.

1. On the iPhone, open Me, tap "Allow location" and grant it, then "Allow location" again for
   "Always". The log on the same screen shows "Relay list: accepted by N of 3 relays".
2. On the Android phone, open Me: its invite QR code is on screen. On the iPhone, Contacts >
   "+" > "Scan QR code", scan it, give the contact a name and tap Add. The iPhone log shows
   "Location: accepted by N of 3 relays" within a few seconds.
3. On the iPhone, open Me to show its QR code. On the Android phone, Contacts > Add > "Scan QR
   code", allow the camera, scan it, name the contact, Add. The row shows "Seen ... within N m"
   after the next refresh, within a minute, or at once with Refresh. Both apps open on the map,
   framed around everyone with a known position; "Everyone" frames them again after panning.
4. Walk a few hundred metres with the iPhone, or drive. Significant location changes wake the
   app and the Android row's "Seen" time moves. Pulling the Contacts list down on the iPhone
   refreshes its own view; the "Publish now" button on its Me screen forces a publish.
5. To check revocation, switch "Share my location" off for the contact on the iPhone: the next
   refresh on the Android phone shows "no longer sharing", with the last position kept.

## License

Prose, meaning `spec/SPEC.md`, `CLAUDE.md` and this README, is licensed under Creative Commons
Attribution-ShareAlike 4.0 International. See [`LICENSE-CC-BY-SA-4.0`](LICENSE-CC-BY-SA-4.0).

Everything else is licensed under the GNU Affero General Public License, version 3 or (at your
option) any later version. See [`LICENSE`](LICENSE).
