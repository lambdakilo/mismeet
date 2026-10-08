# Mismeet protocol specification

Version 1, draft for review, 2026-10-07.

Mismeet is a location sharing app in the style of Find My, built on Nostr, for iOS and
Android. Each phone publishes its own location to Nostr relays as one addressable event,
encrypted so that only approved contacts can read it. Contacts fetch the latest event when they
want to see where someone is. There is no server of our own, no push service and no bridge.

This document defines the wire protocol and the behaviour both apps must share. It does not
define user interface. The key words MUST, MUST NOT, SHOULD, SHOULD NOT and MAY are to be read
as in RFC 2119.

## 1. Terminology

- Publisher: a device publishing its location.
- Reader: a device fetching a contact's location. Every device is both a publisher and a reader.
- Contact: a public key the user has added on their device, with a local display name.
- Location event: the kind `31122` event defined in section 5.
- Entry: one NIP-44 ciphertext inside the content of a location event.
- Real entry: an entry a contact can decrypt. Dummy entry: padding that nobody can decrypt.
- Fix: one position report from the platform location API.

## 2. Design summary

| Decision | Choice |
| --- | --- |
| Event | Addressable kind `31122`, `d` tag `location`, one per publisher |
| Content | JSON with a list of NIP-44 v2 ciphertexts, one per approved contact, padded with dummies |
| Tags | `d` and `expiration` only, never a `p` tag |
| Expiration | NIP-40, 24 hours after `created_at` |
| Contacts | Local only, exchanged as `nostr:nprofile` invites, adding is approving |
| Relays | Per-user list, published as NIP-65 kind `10002`; readers query each contact's relays |
| Keys | One secp256k1 key pair per app install, kept in the platform key store |

## 3. Identity and keys

Each app install generates its own secp256k1 key pair on first launch. The secret key is stored in
the iOS Keychain with an access class that allows use after the first unlock, and in
Android Keystore backed storage, so that background publishing works after a reboot once the
phone has been unlocked.

The app MUST NOT publish a kind `0` profile, a kind `3` contact list or any NIP-51 list for this
key. The key has no public profile: contacts name each other locally.

Importing an existing key is out of scope for version 1, as are key backup and key rotation.
A person with two phones appears to their contacts as two contacts.

## 4. Contacts

### 4.1 Invite

A user shares their identity as a NIP-21 URI holding a NIP-19 `nprofile`:

```
nostr:nprofile1...
```

The `nprofile` carries the user's public key (TLV type 0) and up to four of the user's relays
(TLV type 1), as absolute `wss://` URLs. The app shows it as a QR code and as copyable text.
The app MUST also accept a bare `nprofile1...` and a bare or `nostr:` prefixed `npub1...`.
An invite without relays gets the app's default relay list until a kind `10002` is seen
(section 7.1).

An invite contains no secret. It should still be exchanged in person or over a channel the user
trusts, because whoever substitutes their own key in an invite receives the location meant for
the invitee. The app shows the first eight characters of the `npub` next to each contact so two
people can compare them.

### 4.2 Adding is approving

Scanning or pasting an invite creates a contact record on the device that did the scanning,
with a display name typed by that user. Adding a contact has two effects on the adder's device:

- Sharing: the adder's location events include a real entry for the contact.
- Watching: the adder's reader fetches and decrypts the contact's location events.

Sharing can be switched off per contact (`share` flag, default on). Watching is always on; when
the contact has not added the adder, the reader finds no entry it can decrypt and shows the
contact as not sharing.

Mutual sharing therefore needs each person to add the other. One-way sharing needs only one
side to add. Nothing is sent to anyone when a contact is added or removed, and the protocol has
no request or acceptance message: the only signal a contact gets is whether an entry decrypts.

### 4.3 Contact record

Each contact record holds, locally only:

- public key (32 bytes),
- display name,
- relay URLs, from the invite and later from kind `10002` (section 7.1),
- `share` flag,
- the latest decrypted payload with its event `created_at` and event id (section 8.4).

The contact list never leaves the device. Backup of the contact list is out of scope for
version 1.

### 4.4 Removal

Removing a contact, or switching `share` off, triggers a publish without that contact
(section 8.5). Data the removed contact already fetched stays on their device.

## 5. Location event

```json
{
  "kind": 31122,
  "pubkey": "<publisher public key, 32 bytes lowercase hex>",
  "created_at": 1759840123,
  "tags": [
    ["d", "location"],
    ["expiration", "1759926523"]
  ],
  "content": "{\"v\":1,\"c\":[\"AtK3...\",\"AnQ9...\",\"Ag7c...\",\"AuPx...\"]}",
  "id": "<sha256 of the serialised event>",
  "sig": "<schnorr signature>"
}
```

- `kind` is `31122`, an addressable kind (NIP-01: 30000 to 39999). It is not registered in the
  NIPs repository; section 13 explains the choice.
- `tags` MUST be exactly one `d` tag with value `location` and one `expiration` tag, in that
  order. No other tag is allowed. In particular there is no `p`, `g`, `client` or `alt` tag,
  so the event reveals nothing about recipients, place or software.
- `created_at` is the publish time in Unix seconds. The publisher MUST use a value greater than
  the `created_at` of its previous location event, so that relays replace the old event
  (NIP-01 keeps the newest event per kind, pubkey and `d`, and the lowest id on a tie). If the
  clock has gone backwards, the publisher uses the previous value plus one.
- `expiration` is `created_at + 86400` as a decimal string (section 6).
- `content` is compact JSON (no whitespace) with the object below.

### 5.1 Content

```json
{"v":1,"c":["<entry>","<entry>","<entry>","<entry>"]}
```

- `v`: integer content version, `1`.
- `c`: array of entries. Each entry is a NIP-44 v2 payload as a base64 string, exactly as the
  NIP-44 `encrypt` function returns it.

The array holds one real entry per contact with `share` on, plus dummy entries up to the bucket
size, in random order (section 5.4). The array length is one of 4, 8, 16, 32 or 64. A payload
of section 5.2 pads to 128 bytes under NIP-44, so an entry is 260 base64 characters and an
event with 64 entries is about 17 KB; relays that cap event size below 32 KB are unsuitable
(section 7.4).

### 5.2 Payload

Every real entry in one event encrypts the same plaintext: the payload JSON below, compact,
with the keys in exactly this order.

```json
{"v":1,"ts":1759840101,"lat":60.169856,"lon":24.938379,"acc":12,"alt":21,"spd":1.4,"hdg":270,"bat":0.73}
```

| Key | Type | Required | Meaning |
| --- | --- | --- | --- |
| `v` | integer | yes | Payload version, `1` |
| `ts` | integer | yes | Time of the fix, Unix seconds |
| `lat` | number | yes | WGS84 latitude in degrees, at most 6 decimals |
| `lon` | number | yes | WGS84 longitude in degrees, at most 6 decimals |
| `acc` | integer | yes | Horizontal accuracy radius in metres, as the platform reports it |
| `alt` | integer | no | Altitude in metres above the WGS84 ellipsoid |
| `spd` | number | no | Ground speed in metres per second, at most 1 decimal |
| `hdg` | integer | no | Course over ground in degrees clockwise from true north, 0 to 359 |
| `bat` | number | no | Battery level from 0 to 1, at most 2 decimals |

Rules:

- Numbers are written without exponent notation and without a leading plus sign.
- An optional key is omitted when the platform reports the value as unknown: on iOS a negative
  `verticalAccuracy`, `speed` or `course`; on Android `hasAltitude()`, `hasSpeed()` or
  `hasBearing()` returning false. Altitude on iOS comes from `ellipsoidalAltitude`, not
  `altitude`, so that both platforms report the same datum.
- A fix with unknown horizontal accuracy (negative on iOS) MUST NOT be published.
- `hdg` is the direction of travel, not the direction the phone is facing.
- Readers MUST ignore keys they do not know and MUST reject a payload whose `v` is not `1` or
  whose required keys are missing or of the wrong type.

### 5.3 Encryption

Each real entry is `nip44_encrypt(publisher_secret_key, contact_public_key, payload, v2)`.
NIP-44 version 2 only. The conversation key is symmetric, so the contact decrypts with
`nip44_decrypt(contact_secret_key, publisher_public_key, entry)`.

Each dummy entry is `nip44_encrypt(publisher_secret_key, throwaway_public_key, filler, v2)`,
where `throwaway_public_key` belongs to a key pair generated for that entry with a
cryptographically secure random generator and discarded, and `filler` is a random string of
ASCII letters and digits with the same byte length as the payload. NIP-44 pads by plaintext
length, so a dummy has exactly the same length as a real entry and the same structure, and
cannot be told apart without a key.

### 5.4 Padding and order

Let `n` be the number of contacts with `share` on. The bucket size is the smallest of 4, 8, 16,
32 and 64 that is at least `n`. The publisher adds `bucket - n` dummy entries, then shuffles the
whole array with a cryptographically secure random generator. More than 64 sharing contacts is
not supported in version 1: the app refuses to switch sharing on for a 65th contact.

Readers MUST NOT rely on the order of entries.

### 5.5 Reading an event

For one event from one contact, a reader:

1. Verifies the signature, that `pubkey` is the contact's key, that `kind` is `31122` and
   that the `d` tag is `location`.
2. Rejects the event if the `expiration` tag is missing, is not a decimal integer, or is not
   later than the current time (section 6).
3. Parses `content`; rejects the event if it is not an object with `v` equal to `1` and `c` an
   array of strings.
4. Tries `nip44_decrypt` on each entry in turn with its own secret key and the contact's
   public key. A failure is expected for every entry that is not meant for this reader and
   is not an error. The first entry that decrypts to a valid payload (section 5.2) is the
   result.
5. If no entry decrypts, the contact is not sharing with this reader.

With at most 64 entries and one ECDH per attempt this is a few milliseconds per contact.

## 6. Expiration

Every location event carries a NIP-40 `expiration` tag of `created_at + 86400` (24 hours).

Relays that support NIP-40 drop the event after that time and stop serving it. Many relays do
not, so readers enforce the tag themselves: an event whose expiration has passed is treated as
absent, whatever the relay returns.

The tag bounds how long a location stays readable on a relay after the phone stops publishing,
and how long a removed contact can still fetch the last event that included them when the
publisher could not republish. It does not delete anything a reader already fetched; readers
keep the last payload locally and show its age (section 8.4).

NIP-40 says clients SHOULD NOT send expiring events to relays that do not advertise the NIP.
Mismeet sends the same signed event to all of the publisher's relays anyway: an addressable
event is replaced by the next publish on every relay, and readers enforce the expiration.
Section 13 lists this deviation.

## 7. Relays

### 7.1 Relay lists

Each user has a relay list in the app, two to four `wss://` URLs, seeded from the app's default
list and editable. The list is used for both writing and reading.

The publisher advertises its list as a NIP-65 kind `10002` event with one `r` tag per relay and
no marker (read and write), empty content, published to every relay on the list. It is
published on first run and whenever the list changes.

A reader keeps a relay set per contact: the relays from the contact's invite, merged with the
`r` tags without marker or with marker `write` from the newest kind `10002` of that contact.
Readers refresh a contact's kind `10002` at most once per 24 hours, and only from relays
already in that contact's set. A reader uses at most eight relays per contact; if more are
known it takes the first eight from the newest source.

### 7.2 Publishing

1. Build the payload from the latest fix (section 8.1).
2. Build the entries (sections 5.3 and 5.4).
3. Choose `created_at` as described in section 5 and sign the event.
4. Send the event to every relay in the publisher's list, in parallel, and wait for each
   relay's `OK`. The publish succeeds when at least one relay answers `OK` with `true`.
   Record `created_at` as the previous value for the next publish.
5. An `OK` with `false` whose message starts with `rate-limited:` makes the publisher wait at
   least five minutes before publishing to that relay again. Other failures are logged.

Timeouts: five seconds to connect, ten seconds to receive `OK`, and at most 25 seconds for the
whole publish, so that an iOS background wakeup finishes inside its budget.

### 7.3 Fetching

The reader groups its contacts by relay. To each relay it sends one filter with `kinds`
`[31122]`, `authors` set to the contacts whose relay set includes that relay, and `#d`
`["location"]`. A reader MUST NOT send a relay the keys of contacts not associated with it:
the author list is the reader's contact list, and each relay should see only its own part.

The reader closes the subscription after `EOSE`, or MAY keep it open while the map is on screen
to receive new events as they arrive.

For each author the reader first discards expired events, then keeps the event with the
greatest `created_at`, and on a tie the lowest id in lexical order, across all relays. It then
reads that event as in section 5.5.

Readers do not authenticate to relays (NIP-42) in version 1, because authenticating would tell
the relay which key is asking about which authors. A relay that demands `AUTH` to serve the
event is unusable for reading; section 13 records this.

### 7.4 Relay requirements

A relay is suitable when it:

- accepts kind `31122` and kind `10002` from any public key, without `AUTH`, payment or
  proof of work,
- replaces addressable events as NIP-01 describes,
- allows events of at least 32 KB (NIP-11 `limitation.max_message_length` and
  `max_content_length`, when published),
- preferably lists NIP-40 in its NIP-11 `supported_nips`,
- serves events to unauthenticated readers.

The app checks NIP-11 when a relay is added and warns on a known mismatch. Candidate defaults,
to be verified against this list before release: `wss://relay.damus.io`, `wss://nos.lol`,
`wss://relay.primal.net`.

## 8. Client behaviour

### 8.1 When to publish

A publisher publishes when:

- iOS: a significant location change wakes the app, the app comes to the foreground, or the
  user asks for a refresh;
- Android: the foreground service's location timer fires (default every 10 minutes, whether or
  not the position changed), the app comes to the foreground, or the user asks for a refresh;
- either platform: a contact is removed or its `share` flag changes, or the relay list changes.

Publishing is throttled to at most once per 60 seconds, except for the contact and relay list
changes above. The fix used MUST be at most one hour old; if no such fix exists the publisher
requests one, and skips the publish if none arrives in time. The previous event then stays on
the relays until it expires.

Android publishes on a fixed timer so that publish times tell an observer nothing about
movement. iOS cannot schedule background work without a push service, so iOS publish times
follow the phone's movement; section 9 lists this leak.

### 8.2 When to fetch

A reader fetches when the app comes to the foreground, when the user asks for a refresh, and
every 60 seconds while the contact list or map is on screen. There is no background fetching
on either platform in version 1.

### 8.3 Revocation and the empty recipient set

When a contact is removed or its `share` flag is switched off, the publisher publishes
immediately with the new recipient set, using the cached fix if it is at most one hour old. If
no fix that recent exists, it publishes an event whose entries are all dummies, with a
`filler` length of 100 bytes, so that the old event is replaced at once. If the device has
never published there is nothing to revoke.

When no contact has `share` on, the publisher stops publishing after that one revocation
event.

### 8.4 Reader cache

For each contact the reader stores the latest valid payload with the event's `created_at` and
id. An event with a `created_at` lower than the stored one is ignored, which blocks a relay
from replaying an older unexpired event. When a fetch finds no valid event, the reader keeps
showing the cached payload, marked stale, with the age of `ts`. Removing a contact deletes the
cached payload.

### 8.5 Clocks

Both roles use the device clock. Relays reject events whose `created_at` is far in the future;
the publisher logs the relay's `OK` message in that case. Expiration checks compare the tag
against the device clock without tolerance.

## 9. Privacy and threat model

What a relay operator, or anyone reading the relay, learns from the protocol:

- that a public key uses Mismeet (the kind), and which relays it uses;
- when it publishes, which on iOS correlates with movement (section 8.1);
- a bucketed count of recipients: 4, 8, 16, 32 or 64;
- the reader's IP address and the authors it asks about on that relay, at fetch time, without
  the reader's key (section 7.3).

What they do not learn:

- any location, past or present;
- who the recipients are, or whether two keys are contacts, from the events alone;
- the exact number of recipients.

Other properties:

- Events are signed, so a relay cannot forge or alter a location. It can withhold events or
  serve an older unexpired one; the reader cache (section 8.4) limits replay to the newest
  event the reader has seen, and expiration bounds it to 24 hours.
- A removed contact keeps what they already fetched, and can fetch the last event that
  included them until it is replaced or expires (section 8.3).
- A relay that sees both a publisher's event and a reader's query for that author links the
  two by IP address and time. Querying through Tor or a VPN is out of scope for version 1.
- Device compromise, and keys extracted from an unlocked phone, are out of scope.

## 10. Constants and limits

| Name | Value |
| --- | --- |
| Event kind | `31122` |
| `d` tag | `location` |
| Content version | `1` |
| Payload version | `1` |
| Expiration | `created_at + 86400` seconds |
| Entry buckets | 4, 8, 16, 32, 64 |
| Maximum sharing contacts | 64 |
| Relays per user list | 2 to 4 |
| Relays queried per contact | at most 8 |
| Kind `10002` refresh | at most once per 24 hours per contact |
| Publish throttle | 60 seconds |
| Maximum fix age at publish | 3600 seconds |
| Android publish interval | 600 seconds |
| Foreground fetch interval | 60 seconds |
| Connect timeout | 5 seconds |
| `OK` timeout | 10 seconds |
| Whole publish timeout | 25 seconds |
| Rate-limit backoff | 300 seconds |
| Coordinate precision | 6 decimals |
| Dummy filler length when no fix exists | 100 bytes |

## 11. Versioning

The content `v` and the payload `v` are independent. A reader that meets an unknown content
version ignores the event. A reader that decrypts a payload with an unknown version ignores the
entry and keeps trying the others. A future version that changes the event shape in a way an
old reader would misread uses a new `d` tag value.

## 12. Conformance checklist

Both apps MUST behave identically on every point below. Each point is a unit test on each
platform, and the protocol constants live in a single constants file per platform.

1. Payload JSON is compact, keys in the order of section 5.2, numbers without exponents,
   coordinates rounded to 6 decimals, optional keys omitted when unknown.
2. Entry count is the bucket of section 5.4, dummies are built as in section 5.3, and the
   array is shuffled with a secure random generator.
3. The event has exactly the two tags of section 5, in that order, and `expiration` equals
   `created_at + 86400`.
4. `created_at` is strictly greater than the previous published value.
5. A reader rejects events with a missing, malformed or past `expiration`.
6. A reader picks the newest event across relays, lowest id on a tie, and ignores events older
   than its cache.
7. A reader accepts a payload with unknown extra keys and rejects one with a missing required
   key or the wrong `v`.
8. A reader sends each relay only the authors associated with that relay.
9. Invites round-trip through `nostr:nprofile` with up to four relays, and `npub` input is
   accepted.
10. Nothing is ever published except kinds `31122` and `10002`.

## 13. Deviations from NIPs and open questions for review

1. Kind `31122` is not registered. It was chosen from an unused part of the addressable range
   after checking the NIPs repository kind table on 2026-10-07. Kind `30078` (NIP-78
   application data) was rejected because NIP-78 asks relays to serve those events only to
   their authenticated author, which would stop contacts from reading them. If a collision
   with another project turns up before release, change the number; a NIP could be proposed
   later.
2. NIP-40: the expiration tag is sent to every relay, including ones that do not advertise
   NIP-40 (section 6).
3. NIP-42 is not used. Relays that demand `AUTH` for writing or reading are unsupported in
   version 1. Allowing `AUTH` for publishing only would be a small change; allowing it for
   reading would expose the reader's contact list to the relay per key.
4. Expiration of 24 hours: shorter means less exposure after a phone goes quiet, longer means
   contacts see a location after a longer offline period. Readers show cached data either way.
5. Padding to powers of two hides the exact recipient count at the cost of up to twice the
   bandwidth. Padding to multiples of four would be cheaper and leak more.
6. The Android publish interval of 10 minutes trades battery for freshness and for hiding
   movement in publish timing. iOS cannot offer the same.
7. Default relays need verification against section 7.4 before release.
8. Out of scope for version 1, possibly later: per-contact precision (coarse location for
   some contacts), an in-band contact request flow, key import, backup and rotation, multiple
   devices under one key, publishing through Tor.

## 14. References

- NIP-01, basic protocol flow and addressable events:
  https://github.com/nostr-protocol/nips/blob/master/01.md
- NIP-11, relay information document: https://github.com/nostr-protocol/nips/blob/master/11.md
- NIP-19, bech32 entities and `nprofile`: https://github.com/nostr-protocol/nips/blob/master/19.md
- NIP-21, `nostr:` URI scheme: https://github.com/nostr-protocol/nips/blob/master/21.md
- NIP-40, expiration timestamp: https://github.com/nostr-protocol/nips/blob/master/40.md
- NIP-42, client authentication: https://github.com/nostr-protocol/nips/blob/master/42.md
- NIP-44, versioned encryption: https://github.com/nostr-protocol/nips/blob/master/44.md
- NIP-65, relay list metadata: https://github.com/nostr-protocol/nips/blob/master/65.md
- NIP-78, application-specific data (rejected, see section 13):
  https://github.com/nostr-protocol/nips/blob/master/78.md
- Nostr Dev Kit, the Rust implementation and its Swift and Kotlin bindings, formerly
  rust-nostr: https://github.com/nostrdevkit/nostr and https://github.com/nostrdevkit/nostr-sdk-ffi
