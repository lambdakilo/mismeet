# Google Play listing

The text Play Console holds for the Android app, kept here so each version's listing is
reviewed and versioned like the code. Google's policies want the background location
disclosure in the description too, so the description repeats what the app's own dialog says.

## Main store listing

- App name: Mismeet
- Category: Maps & Navigation
- Website: https://github.com/lambdakilo/mismeet
- Privacy policy: https://github.com/lambdakilo/mismeet/blob/main/PRIVACY.md
- App icon: `design/icon-512.png`
- Feature graphic: `design/play-store/feature-graphic.png`
- Phone screenshots: `design/play-store/phone/`, in file order

Short description:

```text
Share your location with people you choose, end-to-end encrypted over Nostr.
```

Full description:

```text
Mismeet shows the people you choose where you are, and you where they are, without an account and without a company server in the middle.

Your phone publishes its location to Nostr relays, encrypted separately for each person you share with. Relays pass it along but can read neither the location nor who it is for, and every location expires after 24 hours.

- Add a contact by scanning the QR code of their invite, or by pasting it.
- See everyone on the map at a glance, with how long ago each position was seen.
- Watch your contacts without sharing anything yourself: sharing is off until you turn it on, and until then the app asks for no permission.
- Choose for each contact whether you share your location with them.
- No sign-up, no email, no phone number. Your identity is a key that stays on your phone.
- No Google Play Services, no ads and no tracking.

When you turn sharing on, Mismeet collects location data to share your position with the contacts you choose, even when the app is closed or not in use. While sharing is on, a notification shows that it is running, and the switch on the Me screen stops it.

Mismeet is free and open source under the GNU Affero General Public License. Its protocol and source code are at github.com/lambdakilo/mismeet.
```

## Release notes

Play takes up to 500 characters per language, from the version's section of `CHANGELOG.md`.

0.1.0:

```text
First release. Add the people you know by their invite QR code and see them on a map. Sharing your own location is off until you turn it on, and you choose who gets it. Locations are end-to-end encrypted Nostr events that expire after 24 hours, with no account and no server of its own.
```

## App content

- Privacy policy: the URL above.
- Ads: no ads.
- App access: all functionality is available without special access; there is no login.
- Content rating: the app shares the user's location with other users; nothing else in the
  questionnaire applies (no violence, sexual content, language, drugs, gambling, purchases or
  user-generated content).
- Target audience: 18 and over. A location sharing app aimed at children would fall under the
  Families policy, and the app has no age checks.
- Data safety: no data collected and none shared. The developer runs no server; the location
  leaves the phone only end-to-end encrypted for the chosen contacts, which Play exempts from
  disclosure, and the app has no analytics, ads or crash reporting. Data is encrypted in
  transit. The relays the user chooses still see a public key, an IP address and publish times,
  as `PRIVACY.md` says.

## Sensitive permissions

Location in the background, one feature:

```text
Sharing the user's location with the contacts they chose
```

Why the feature needs location in the background:

```text
Mismeet is a location sharing app: its core purpose is to let the family members and friends a user chooses see where the user is. Once the user turns sharing on, a foreground service with a visible notification publishes the position every 10 minutes, end-to-end encrypted for those contacts only. The service has to start without the app on screen, after the phone or the service restarts, which needs background location. The location is never used for ads or analytics.
```

Video: a recording from the emulator under 30 seconds, uploaded to YouTube as unlisted or to
Google Drive: the Me screen, the disclosure, the system prompt, "Allow all the time", then the
app closed with the sharing notification showing.

Foreground service type `location`, the same video:

```text
User-initiated location sharing. The user turns it on with the Share my location switch and off with the same switch. While it runs, the service reads the position and publishes it every 10 minutes to the contacts the user chose. If it were deferred or interrupted, those contacts would see a stale position.
```

Battery optimisation: the Me screen asks for the exemption with
`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, because stock Android 12 otherwise stops the
service. Play accepts that request from safety apps that keep users and their families safe;
if review refuses it, the fallback is the settings list, `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`,
without the permission.
