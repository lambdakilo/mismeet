# Contributing to Mismeet

## Licence of contributions

A contribution is licensed under the same terms as the file it changes:

- Code under the GNU Affero General Public License, version 3 or any later version, with the
  app store exception in [`LICENSE-APP-STORE-EXCEPTION`](LICENSE-APP-STORE-EXCEPTION). The
  exception is part of the licence your sign-off refers to. It lets the app be distributed
  through app stores whose terms restrict users, Apple's App Store included, as long as the
  source stays available elsewhere under the AGPL.
- Prose, meaning `spec/SPEC.md`, `README.md`, `docs/`, `CONTRIBUTING.md`, `CHANGELOG.md`,
  `PRIVACY.md` and `CLAUDE.md`, under Creative Commons Attribution-ShareAlike 4.0
  International.

There is no contributor licence agreement and no copyright assignment: you keep the copyright
in your work.

## Sign off every commit

Every commit carries a `Signed-off-by:` line with your name and the address the commit is made
with. By adding it you certify the Developer Certificate of Origin below for that commit. Git
adds the line with `-s`:

```bash
git commit -s
```

To sign off commits already on your branch:

```bash
git rebase --signoff origin/main
```

The sign-off must name the commit's author. A CI job fails a pull request when any of its
commits lacks one, and `main` accepts a pull request only when that job has passed. Run the
same check before pushing:

```bash
scripts/check-dco.sh origin/main..HEAD
```

```
Developer Certificate of Origin
Version 1.1

Copyright (C) 2004, 2006 The Linux Foundation and its contributors.

Everyone is permitted to copy and distribute verbatim copies of this
license document, but changing it is not allowed.


Developer's Certificate of Origin 1.1

By making a contribution to this project, I certify that:

(a) The contribution was created in whole or in part by me and I
    have the right to submit it under the open source license
    indicated in the file; or

(b) The contribution is based upon previous work that, to the best
    of my knowledge, is covered under an appropriate open source
    license and I have the right under that license to submit that
    work with modifications, whether created in whole or in part
    by me, under the same open source license (unless I am
    permitted to submit under a different license), as indicated
    in the file; or

(c) The contribution was provided directly to me by some other
    person who certified (a), (b) or (c) and I have not modified
    it.

(d) I understand and agree that this project and the contribution
    are public and that a record of the contribution (including all
    personal information I submit with it, including my sign-off) is
    maintained indefinitely and may be redistributed consistent with
    this project or the open source license(s) involved.
```

## Dependencies

The App Store build only works while every dependency allows that kind of distribution. Add
dependencies under permissive licences (MIT, Apache-2.0, BSD) or MPL-2.0. A GPL, LGPL or AGPL
dependency without an equivalent exception would block the iOS release.

## Before you open a pull request

Change [`spec/SPEC.md`](spec/SPEC.md) first when the protocol changes, in the same pull
request. Then run the checks for the platform you changed; the setup they need is in
[`docs/development.md`](docs/development.md).

```bash
android/gradlew -p android lint test
```

```bash
set -o pipefail && xcodebuild -project ios/Mismeet.xcodeproj -scheme Mismeet -destination 'platform=iOS Simulator,name=iPhone 17' -derivedDataPath ios/build test | xcbeautify
```
