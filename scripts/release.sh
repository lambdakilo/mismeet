#!/usr/bin/env bash
# Store builds come from an annotated tag <platform>-<version>-<build>, such as android-0.1.0-1,
# made on a clean tree, so every published binary names the exact source it was built from.
#
#   scripts/release.sh version <android|ios>   print the tag the current version maps to
#   scripts/release.sh tag <android|ios>       tag HEAD with it and push the tag to origin
#   scripts/release.sh check <android|ios>     fail unless HEAD is that tag and the tree is clean
#   scripts/release.sh archive-ios             check, then archive the iOS app
#   scripts/release.sh upload-ios              check, archive and upload to App Store Connect, which
#                                              offers the build in TestFlight and for App Store review
set -euo pipefail

root="$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"

version() {
  case "$1" in
    android)
      local gradle="$root/android/app/build.gradle.kts"
      local name code
      name="$(sed -nE 's/^ *versionName = "([^"]+)".*/\1/p' "$gradle")"
      code="$(sed -nE 's/^ *versionCode = ([0-9]+).*/\1/p' "$gradle")"
      echo "android-$name-$code"
      ;;
    ios)
      local spec="$root/ios/project.yml"
      local name build
      name="$(sed -nE 's/^ *MARKETING_VERSION: "?([^"]+)"?.*/\1/p' "$spec")"
      build="$(sed -nE 's/^ *CURRENT_PROJECT_VERSION: "?([^"]+)"?.*/\1/p' "$spec")"
      echo "ios-$name-$build"
      ;;
    *)
      echo "platform must be android or ios" >&2
      exit 2
      ;;
  esac
}

check() {
  local tag
  tag="$(version "$1")"
  if [ -n "$(git -C "$root" status --porcelain)" ]; then
    echo "The tree has uncommitted changes; a store build must come from a clean tag." >&2
    exit 1
  fi
  if ! git -C "$root" rev-parse -q --verify "refs/tags/$tag" >/dev/null; then
    echo "No tag $tag. Run: scripts/release.sh tag $1" >&2
    exit 1
  fi
  if [ "$(git -C "$root" rev-parse "$tag^{commit}")" != "$(git -C "$root" rev-parse HEAD)" ]; then
    echo "HEAD is not $tag. Run: git switch --detach $tag" >&2
    exit 1
  fi
  echo "$tag"
}

tag() {
  local tag
  tag="$(version "$1")"
  if [ -n "$(git -C "$root" status --porcelain)" ]; then
    echo "Commit or stash the changes first; the tag must name a clean tree." >&2
    exit 1
  fi
  if git -C "$root" rev-parse -q --verify "refs/tags/$tag" >/dev/null; then
    echo "$tag exists already. Raise the version or build number first." >&2
    exit 1
  fi
  git -C "$root" fetch -q origin
  if ! git -C "$root" merge-base --is-ancestor HEAD "origin/$(git -C "$root" rev-parse --abbrev-ref origin/HEAD | cut -d/ -f2)"; then
    echo "HEAD is not on origin's default branch yet. Push it first." >&2
    exit 1
  fi
  git -C "$root" tag -a "$tag" -m "Store build $tag"
  git -C "$root" push -q origin "refs/tags/$tag"
  echo "$tag"
}

archive_ios() {
  local tag
  tag="$(check ios)"
  xcodegen generate --spec "$root/ios/project.yml" >/dev/null
  xcodebuild -project "$root/ios/Mismeet.xcodeproj" -scheme Mismeet -configuration Release \
    -destination 'generic/platform=iOS' -archivePath "$root/ios/build/$tag.xcarchive" \
    -allowProvisioningUpdates DEVELOPMENT_TEAM="$(cat "$root/ios/.team-id")" archive | xcbeautify >&2
  echo "$root/ios/build/$tag.xcarchive"
}

upload_ios() {
  local archive options
  archive="$(archive_ios)"
  options="$root/ios/build/ExportOptions.plist"
  # manageAppVersionAndBuildNumber stays off so the uploaded build keeps the tagged numbers.
  cat > "$options" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>method</key>
  <string>app-store-connect</string>
  <key>destination</key>
  <string>upload</string>
  <key>teamID</key>
  <string>$(cat "$root/ios/.team-id")</string>
  <key>signingStyle</key>
  <string>automatic</string>
  <key>manageAppVersionAndBuildNumber</key>
  <false/>
  <key>uploadSymbols</key>
  <true/>
</dict>
</plist>
PLIST
  xcodebuild -exportArchive -archivePath "$archive" -exportOptionsPlist "$options" \
    -exportPath "$root/ios/build/export" -allowProvisioningUpdates | xcbeautify
  echo "Uploaded $(basename "$archive" .xcarchive). It shows in App Store Connect > TestFlight once processed."
}

case "${1:-}" in
  version) version "${2:-}" ;;
  tag) tag "${2:-}" ;;
  check) check "${2:-}" ;;
  archive-ios) archive_ios ;;
  upload-ios) upload_ios ;;
  *) sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'; exit 2 ;;
esac
