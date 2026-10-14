# Mismeet privacy policy

Mismeet has no server and no account. Its developer receives no data from the app: no
location, no contacts, no analytics, no crash reports and no advertising identifiers. There is
no tracking.

## What stays on your phone

The app keeps its own Nostr key pair, the contacts you add (their public keys, the names you
give them and their relays), their last known positions, and your relay list. Removing the app
deletes all of it.

## What leaves your phone

Only when you share your location, which on Android is off until you turn it on:

- Your location goes to the Nostr relays in your relay list as one event, encrypted end to end
  separately for each contact you share with (NIP-44). It expires after 24 hours, and each new
  publish replaces the previous one. Relays cannot read the location or tell who it is for.
- Your relay list goes to the same relays, so that contacts know where to look.

When you look at your contacts, the app asks those relays for their latest events.

The relays are run by third parties under their own terms. The defaults are relay.damus.io,
nos.lol and relay.primal.net, and you can change them. A relay can see your public key, when
you publish, your IP address, roughly how many contacts you share with (rounded up to 4, 8, 16,
32 or 64) and, when you fetch, which public keys you ask about. Section 9 of the
[protocol specification](spec/SPEC.md) describes this in detail.

## Maps and camera

On iOS the map comes from Apple Maps, under Apple's privacy policy. On Android it comes from
OpenStreetMap's tile servers, which see your IP address and the map area you view. The camera
is used only to scan invite QR codes; no image is stored or sent.

## Stopping and removing

Switching sharing off for a contact, or removing the contact, publishes a replacement event
without them at once. Contacts keep what they already saw. Events left on relays expire after
24 hours.

## Contact

Questions go to the project's issue tracker: <https://github.com/lambdakilo/mismeet/issues>.
