# Mismeet privacy policy

Mismeet has no server and no account. Its developer receives no data from the app: no
location, no contacts, no analytics, no crash reports and no advertising identifiers. There is
no tracking.

## What stays on your phone

The app keeps its own Nostr key pair, the contacts you add (their public keys, the names you
give them and their relays), their last known positions, and your relay list. Removing the app
deletes all of it.

## What leaves your phone

Only when you share your location, which is off until you turn it on:

- Your location goes to the Nostr relays in your relay list as one event, encrypted end to end
  separately for each contact you share with (NIP-44). It expires after 24 hours, and each new
  publish replaces the previous one. Relays cannot read the location or tell who it is for.
- Your relay list goes to the same relays, so that contacts know where to look.

Sharing works in the background: once it is on, the app collects location data to share your
position with the contacts you choose, even when the app is closed or not in use. On Android a
notification shows while it runs.

When you look at your contacts, the app asks those relays for their latest events.

The relays are run by third parties under their own terms. The app uses relay.damus.io, nos.lol
and relay.primal.net. A relay can see your public key, when you publish, your IP address,
roughly how many contacts you share with (rounded up to 4, 8, 16, 32 or 64) and, when you
fetch, which public keys you ask about. Section 9 of the
[protocol specification](spec/SPEC.md) describes this in detail.

## Maps, roads and pictures

On iOS the map comes from Apple Maps, under Apple's privacy policy. On Android it comes from
OpenStreetMap's tile servers, which see your IP address and the map area you view.

When a contact is moving, the app guesses where the road has taken them since their last
position. For that it downloads the OpenStreetMap roads around them from the Overpass API at
overpass-api.de, in fixed squares of about a kilometre that are never centred on anyone, and
keeps them on the phone for 30 days. That server sees your IP address and those squares, much
as map tiles of the same area would show, but no position.

The map opens on the people in your own country. Which country a position lies in is worked out
on the phone from borders that come with the app, so no position is sent anywhere for it.

An invite link opens a page on lambdakilo.github.io, hosted by GitHub Pages, when Mismeet is
not installed. The invite sits after the `#` in the link, which browsers do not send, so GitHub
sees your IP address and that the page was opened, but not the invite.

The camera is used only to scan invite QR codes; no image is stored or sent. A photo you choose
for a contact is stored on your phone only and is never sent.

## Stopping and removing

Switching sharing off for a contact, or removing the contact, publishes a replacement event
without them at once. Contacts keep what they already saw. Events left on relays expire after
24 hours.

## Contact

Questions go to the project's issue tracker: <https://github.com/lambdakilo/mismeet/issues>.
