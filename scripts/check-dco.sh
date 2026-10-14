#!/usr/bin/env bash
# Fails when a commit in the range has no Developer Certificate of Origin sign-off by its own
# author, as CONTRIBUTING.md requires. Merge commits are skipped.
#
#   scripts/check-dco.sh <base>..<head>
set -euo pipefail

range="${1:?usage: scripts/check-dco.sh <base>..<head>}"
missing=0
for commit in $(git rev-list --no-merges "$range"); do
  author="$(git log -1 --format='%an <%ae>' "$commit")"
  if git log -1 --format='%(trailers:key=Signed-off-by,valueonly)' "$commit" | grep -qxF "$author"; then
    continue
  fi
  echo "$(git log -1 --format='%h %s' "$commit"): no Signed-off-by: $author"
  missing=1
done

if [ "$missing" -ne 0 ]; then
  echo "Sign off each commit with git commit -s, or the whole branch with git rebase --signoff, see CONTRIBUTING.md."
  exit 1
fi
echo "Every commit in $range is signed off by its author."
