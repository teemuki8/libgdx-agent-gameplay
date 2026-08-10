#!/usr/bin/env bash
# Generates candidate checksums; the diff remains a manual trust decision.
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_root"

./gradlew --write-verification-metadata sha256 resolveVerificationArtifacts \
  --warning-mode=fail "$@"

printf 'candidate verification metadata: %s\n' \
  "$project_root/gradle/verification-metadata.xml"
printf 'review before commit: git diff -- gradle/verification-metadata.xml\n'
