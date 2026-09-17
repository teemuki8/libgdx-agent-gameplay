#!/usr/bin/env bash
# Local Maven Central staging for an authorized gameplay release. Reads the protected
# credentials from $MAVEN_CENTRAL_ENV (default ~/.maven-central.env, chmod 600, never
# committed). The only interactive step is the final publish confirmation.
#
# Expected env file contents:
#   export MAVEN_CENTRAL_USERNAME=...
#   export MAVEN_CENTRAL_PASSWORD=...
#   export MAVEN_CENTRAL_NAMESPACE=...
#   export MAVEN_SIGNING_KEY='...armored private key block...'
#   export MAVEN_SIGNING_PASSWORD=...
set -euo pipefail

env_file="${MAVEN_CENTRAL_ENV:-$HOME/.maven-central.env}"
if [[ ! -r "$env_file" ]]; then
  echo "Missing $env_file. Create it with the five exports (see the script header)." >&2
  exit 1
fi
# shellcheck source=/dev/null
source "$env_file"
for name in \
  MAVEN_CENTRAL_USERNAME MAVEN_CENTRAL_PASSWORD MAVEN_CENTRAL_NAMESPACE \
  MAVEN_SIGNING_KEY MAVEN_SIGNING_PASSWORD
do
  if [[ -z "${!name:-}" ]]; then
    echo "Missing $name in $env_file" >&2
    exit 1
  fi
done

version="$(git describe --tags --exact-match 2>/dev/null || true)"
if [[ ! "$version" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Run from an exact semantic release tag; git describe says: ${version:-no tag}." >&2
  exit 1
fi
release_version="${version#v}"
baseline="$(git tag --sort=-v:refname | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' \
  | grep -v "^$version\$" | head -1 || true)"
if [[ -z "$baseline" ]]; then
  echo "No previous release tag found for the API baseline." >&2
  exit 1
fi
echo "Staging $release_version against the $baseline API baseline."

xvfb-run -a ./gradlew \
  -PreleaseVersion="$release_version" -PapiBaselineVersion="${baseline#v}" \
  publishAllPublicationsToMavenCentralRepository \
  --warning-mode=fail --no-configuration-cache --no-daemon

authorization="$(printf '%s:%s' "$MAVEN_CENTRAL_USERNAME" "$MAVEN_CENTRAL_PASSWORD" | base64 | tr -d '\n')"
central_api="https://central.sonatype.com/api/v1/publisher"

echo "Transferring the upload to the Central Portal."
curl --fail-with-body --silent --show-error --request POST \
  --header "Authorization: Bearer $authorization" \
  "https://ossrh-staging-api.central.sonatype.com/manual/upload/defaultRepository/${MAVEN_CENTRAL_NAMESPACE}?publishing_type=user_managed"

deployment_id="$(
  curl --fail-with-body --silent --show-error \
    --header "Authorization: Bearer $authorization" \
    "https://ossrh-staging-api.central.sonatype.com/manual/search/repositories?ip=any&profile_id=$MAVEN_CENTRAL_NAMESPACE" \
    | jq -r '[.repositories[].portal_deployment_id // empty] | .[0]'
)"
if [[ -z "$deployment_id" || "$deployment_id" == "null" ]]; then
  echo "No deployment found; check https://central.sonatype.com/publishing/deployments" >&2
  exit 1
fi
echo "Deployment: $deployment_id"

for attempt in $(seq 1 40); do
  status="$(
    curl --fail-with-body --silent --show-error --request POST \
      --header "Authorization: Bearer $authorization" \
      "$central_api/status?id=$deployment_id"
  )"
  state="$(jq -r '.deploymentState' <<<"$status")"
  echo "Validation state: $state (attempt $attempt)"
  if [[ "$state" == "VALIDATED" ]]; then
    break
  fi
  if [[ "$state" == "FAILED" ]]; then
    echo "Deployment failed validation:" >&2
    jq '.errors' <<<"$status" >&2
    exit 1
  fi
  sleep 15
done
if [[ "$state" != "VALIDATED" ]]; then
  echo "Deployment did not validate within the polling window." >&2
  exit 1
fi

expected_purls="$(printf '%s\n' \
  "pkg:maven/io.github.teemuki8/gameplay-box2d@$release_version" \
  "pkg:maven/io.github.teemuki8/gameplay-bullet@$release_version" \
  "pkg:maven/io.github.teemuki8/gameplay-core@$release_version" \
  "pkg:maven/io.github.teemuki8/gameplay-libgdx@$release_version" \
  "pkg:maven/io.github.teemuki8/gameplay-runtime@$release_version" | sort)"
actual_purls="$(jq -r '.purls[]' <<<"$status" | sort)"
if [[ "$actual_purls" != "$expected_purls" ]]; then
  echo "Deployment coordinates do not exactly match $version." >&2
  echo "Expected: $expected_purls" >&2
  echo "Actual:   $actual_purls" >&2
  exit 1
fi
echo "Validated deployment $deployment_id with the exact $version coordinates."

read -r -p "Publish $version to Maven Central? This is irreversible. [y/N] " answer
if [[ "$answer" != "y" && "$answer" != "Y" ]]; then
  echo "Aborted; the validated deployment remains droppable via the Central portal."
  exit 1
fi

curl --fail-with-body --silent --show-error --request POST \
  --header "Authorization: Bearer $authorization" \
  "$central_api/deployment/$deployment_id"
echo
echo "Published $version (deployment $deployment_id)."
