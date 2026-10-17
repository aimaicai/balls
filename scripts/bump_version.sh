#!/usr/bin/env bash
set -euo pipefail

# Bumps versionCode (+1) and versionName in app/build.gradle.kts, ready for the "Release AAB"
# workflow (see .github/workflows/release-aab.yml and docs/RELEASE_CHECKLIST.md). Play Console
# rejects any upload that doesn't increase versionCode over the last one it has, so this always
# bumps it by exactly 1 - versionName is whatever you pass in, free-form.
#
# Usage: scripts/bump_version.sh <new-version-name>
# Example: scripts/bump_version.sh 1.1.0

if [ "$#" -ne 1 ]; then
  echo "Usage: $0 <new-version-name>" >&2
  echo "Example: $0 1.1.0" >&2
  exit 1
fi

NEW_VERSION_NAME="$1"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GRADLE_FILE="$SCRIPT_DIR/../app/build.gradle.kts"

CURRENT_VERSION_CODE="$(grep -oE 'versionCode = [0-9]+' "$GRADLE_FILE" | grep -oE '[0-9]+')"
CURRENT_VERSION_NAME="$(grep -oE 'versionName = "[^"]+"' "$GRADLE_FILE" | tr -d '"' | sed 's/versionName = //')"

if [ -z "$CURRENT_VERSION_CODE" ] || [ -z "$CURRENT_VERSION_NAME" ]; then
  echo "Could not find versionCode/versionName in $GRADLE_FILE" >&2
  exit 1
fi

NEW_VERSION_CODE=$((CURRENT_VERSION_CODE + 1))

# -i.bak works identically on GNU sed (Linux) and BSD sed (macOS), unlike bare -i.
sed -i.bak \
  -e "s/versionCode = $CURRENT_VERSION_CODE/versionCode = $NEW_VERSION_CODE/" \
  -e "s/versionName = \"$CURRENT_VERSION_NAME\"/versionName = \"$NEW_VERSION_NAME\"/" \
  "$GRADLE_FILE"
rm -f "$GRADLE_FILE.bak"

echo "Bumped versionCode $CURRENT_VERSION_CODE -> $NEW_VERSION_CODE"
echo "Bumped versionName $CURRENT_VERSION_NAME -> $NEW_VERSION_NAME"
echo
echo "Next steps:"
echo "  git add app/build.gradle.kts"
echo "  git commit -m \"Bump version to $NEW_VERSION_NAME ($NEW_VERSION_CODE)\""
echo "  git tag v$NEW_VERSION_NAME"
echo "  git push && git push --tags   # pushing the tag triggers the Release AAB workflow"
