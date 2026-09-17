# Releasing

Release preparation and release execution are separate authorities. Ordinary development, merge,
or public-repository approval does not authorize a tag, GitHub release, Central upload, deployment
publication, or drop.

For an authorized version `X.Y.Z`:

1. Confirm clean `main`, exact remote-head parity, and green CI.
2. Run `xvfb-run -a ./gradlew clean check javadoc apiCompatibility
   verifyPublicationArchives verifyPublishedPoms --warning-mode=fail`.
3. Run `./scripts/verify-maven-local.sh`; it publishes the property-driven `1.0.0-SNAPSHOT` to a
   disposable repository and compiles/runs an external consumer against all five coordinates.
4. If a released baseline exists, repeat with `-PapiBaselineVersion=<previous-version>`.
5. Refresh strict verification metadata after dependency/build-tool changes and review every
   binary, metadata, source, and IDEA-tooling addition.
6. Only with explicit release authorization, create the exact semantic `vX.Y.Z` release. The
   staging workflow checks, signs, uploads, and transfers with `publishing_type=user_managed`.
   The local equivalent is `./scripts/stage-central.sh`: it reads the protected credentials from
   `~/.maven-central.env` (chmod 600, never committed), stages and signs the five modules,
   transfers the upload, polls validation, verifies the exact coordinates, and asks for the
   single final publish confirmation.
7. Inspect the Central deployment. Publication requires a second deliberate `publish` operation
   whose validated PURLs exactly match all five coordinates. A failed candidate may be dropped only
   by the manual management workflow.

Required protected-environment values are Central username/password, armored in-memory signing
key/password, and Central namespace. Credentials, signing material, and authorization headers must
never be committed, logged, included in protocol data, or stored in build artifacts.

For 1.6.0, use `-PreleaseVersion=1.6.0 -PapiBaselineVersion=1.5.0 --no-configuration-cache`.
All five modules compare against real published 1.5.0 binaries, including Bullet.
