# Mismeet

Location sharing in the style of Find My, built on Nostr, for iOS and GrapheneOS. Each phone
publishes its own location to Nostr relays as one addressable event, encrypted separately for
each approved contact and carrying an expiration. Contacts fetch the latest event when they want
to see where someone is. There is no server of its own, no push service and no bridge, and the
social graph never appears in plaintext.

## Status

The protocol is specified and both apps build with the protocol core and its conformance
tests, a device key, local contact and relay storage, relay publishing and fetching, location
monitoring and a first set of screens. Neither app has been exercised end to end on a phone
yet. Invites are pasted as text (no QR scanning yet), and the Android app has no map view.

## Files

- [`spec/SPEC.md`](spec/SPEC.md): the protocol: event kind and tags, encryption and padding,
  expiration, relay handling, the contact model, client behaviour and the threat model.
- [`CLAUDE.md`](CLAUDE.md): the build, test, install and log commands for both platforms, and
  the rules the code follows.
- `ios/`: the Swift and SwiftUI app, generated from `ios/project.yml` with XcodeGen.
- `android/`: the Kotlin app and the `protocol` module it builds on.
- [`LICENSE`](LICENSE) and [`LICENSE-CC-BY-SA-4.0`](LICENSE-CC-BY-SA-4.0): the licence texts,
  see below.

## License

Prose, meaning `spec/SPEC.md`, `CLAUDE.md` and this README, is licensed under Creative Commons
Attribution-ShareAlike 4.0 International. See [`LICENSE-CC-BY-SA-4.0`](LICENSE-CC-BY-SA-4.0).

Everything else is licensed under the GNU Affero General Public License, version 3 or (at your
option) any later version. See [`LICENSE`](LICENSE).
