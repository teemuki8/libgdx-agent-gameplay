#!/usr/bin/env bash
set -euo pipefail

repository_directory="$(mktemp -d)"
cleanup() {
  rm -rf -- "$repository_directory"
}
trap cleanup EXIT

repository_uri="$(realpath "$repository_directory")"
./gradlew \
  -PreleaseVersion=0.1.0-SNAPSHOT \
  -PqualificationRepository="$repository_uri" \
  publishAllPublicationsToQualificationRepository \
  --no-daemon \
  --console=plain

./gradlew \
  -p verification/consumer \
  -PqualificationRepository="$repository_uri" \
  clean run \
  --no-daemon \
  --console=plain
