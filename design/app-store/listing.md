# App Store listing

The text App Store Connect holds for the iOS app, kept here so each version's listing is
reviewed and versioned like the code. Apple's guidelines keep it free of Apple trademarks such
as Find My and of other mobile platforms.

## App Information

- Name: Mismeet
- Subtitle: Private location sharing
- Primary category: Navigation
- Secondary category: Social Networking

## Version page

Promotional text:

```text
Share where you are with the people you choose. End-to-end encrypted over Nostr, with no account and no company server in between.
```

Description:

```text
Mismeet shows the people you choose where you are, and you where they are, without an account and without a company server in the middle.

Your phone publishes its location to Nostr relays, encrypted separately for each person you share with. Relays pass it along but can read neither the location nor who it is for, and every location expires after 24 hours.

- Add a contact by scanning the QR code of their invite, or by pasting it.
- Choose for each contact whether you share your location with them.
- See everyone on the map at a glance, with how long ago each position was seen.
- Your location updates in the background as you move.
- No sign-up, no email, no phone number. Your identity is a key that stays on your phone.

Mismeet is free and open source under the GNU Affero General Public License. Its protocol and source code are at github.com/lambdakilo/mismeet.
```

Keywords:

```text
location,sharing,friends,family,map,privacy,encrypted,nostr,find,where,meet
```

- Support URL: https://github.com/lambdakilo/mismeet/issues
- Marketing URL: https://github.com/lambdakilo/mismeet
- Copyright: 2026 Mismeet contributors
- Screenshots: `iphone-6.9/` for the large display, `iphone-6.3/` for the medium one, in file
  order.

## App Review notes

```text
Mismeet needs no account: each install creates its own key on the device.

Location: the app asks for location while in use, then Always. With Always it publishes the user's position, end-to-end encrypted, on significant location changes while the app is closed, so that the contacts the user chose see a recent position. It publishes only for contacts the user added and left sharing on, and nothing when there are none.

To try it, use two devices. On each, open the Me tab to show the invite QR code. On the other, open Contacts, tap +, then Scan QR code, and add it. Within a minute each phone shows the other on the map. With one device, the Me tab shows the invite and the map shows your own position.
```
