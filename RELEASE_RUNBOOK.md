# Release Runbook

This runbook covers publishing a release for `com.github.thecookielab.xchange`.

## Prerequisites

1. `main` is green in GitHub Actions.
2. Release credentials are configured in repository secrets:
   - `MAVEN_CENTRAL_TOKEN_USER`
   - `MAVEN_CENTRAL_TOKEN_PASS`
   - `GPG_PRIVATE_KEY`
   - `GPG_PASSPHRASE`
3. You have selected:
   - `release_version`: a stable `X.Y.Z` version matching the reactor's current
     `X.Y.Z-SNAPSHOT`.
   - `next_snapshot_version`: a numerically newer `X.Y.Z-SNAPSHOT`.
4. Merge the README version updates before dispatch: its latest-release prose and
   dependency example must name the target release, and its snapshot instructions
   must name the requested next snapshot.

## Read-Only Preflight

Before the Maven dry run or release dispatch, run:

```bash
python3 scripts/ci/release-check.py preflight \
  --release-version 1.0.5 \
  --next-snapshot-version 1.0.6-SNAPSHOT
```

Substitute the intended versions. Preflight validates every module declared by the
reactor, including nested modules, parent versions and README examples. It rejects
an existing remote release tag or any existing canonical module POM in Central.
Only HTTP 404 establishes artifact absence; permission errors, throttling, server
errors and network failures stop the release rather than treating it as unused.

Preflight does not modify files or Git, import keys, invoke Maven, or publish
anything. It needs Python 3.10+ and read access to `origin` and Central, not release
credentials. The publishing workflow runs it before JDK setup and GPG import.

## Local Safety Check (Dry Run)

Use this command from repo root to verify `maven-release-plugin` flow without publishing:

```bash
mvn -B --no-transfer-progress \
  --settings etc/settings.xml \
  -Prelease-sign-artifacts,central-release-publish \
  -Dgpg.passphrase="${GPG_PASSPHRASE}" \
  -DreleaseVersion="0.1.0" \
  -DdevelopmentVersion="0.1.1-SNAPSHOT" \
  -DlocalCheckout=true \
  -Darguments="--settings etc/settings.xml -Prelease-sign-artifacts,central-release-publish -Dgpg.passphrase=${GPG_PASSPHRASE} -DskipIntegrationTests=true" \
  -DdryRun=true \
  -DpreparationGoals=validate \
  release:clean release:prepare
```

Then clean generated release files:

```bash
mvn -B --no-transfer-progress release:clean
```

## GitHub Release Publish

1. Open Actions and select `Maven Release Publish (Disabled by Default)`.
2. Click `Run workflow`.
3. Set:
   - `enable_release`: `true`
   - `release_version`: target release version
   - `next_snapshot_version`: next snapshot version
   - `release_notes`: notes following `/RELEASE_NOTES_TEMPLATE.md`, including **Upstream Credits**
4. Run from `main`.
5. Confirm workflow success. Publishing runs are serialized. The existing Maven
   prepare/perform build gates remain in place. After Central publication, the
   workflow verifies the release artifacts and an isolated consumer before creating
   the GitHub release at `xchange-parent-<release_version>`. It then requires the
   exact requested next snapshot version, deploys it and verifies it independently.

## Post-Release Validation

Stable releases use the reactor manifest from their fetched immutable release tag,
so modules added later on `main` do not create false failures for older releases.
Snapshots use the current checkout's reactor manifest.

The workflow checks all canonical reactor projects, not just `xchange-core`:

- Published POM coordinates, packaging and reactor parent versions match.
- Releases contain no snapshot dependency versions or snapshot version properties.
- POMs, binaries, sources and Javadocs are accessible. Releases also require every
  detached `.asc` signature and its PGP signature envelope. This is availability
  checking, not local GPG key-trust or cryptographic signature verification.
- Snapshot metadata resolves a consistent timestamped POM, binary, sources and
  Javadoc set for each module. Unsigned snapshots do not require `.asc` files.
- A separate temporary Maven project, with a new empty local repository, compiles
  and runs against published core and Bitstamp dependencies. It exercises a public
  currency-pair API and checks that both classes came from the requested JAR
  version. It never connects to an exchange.

Also confirm that the GitHub release and tag use the same release version and the
next-development commit is on `main`.

### Recheck an Existing Publication Without Publishing

Use the read-only **Release Safeguards** workflow in Actions, or dispatch it:

```bash
gh workflow run release_checks.yml --ref main \
  -f release_version=1.0.4 \
  -f snapshot_version=1.0.5-SNAPSHOT
```

This workflow needs only read permission. It runs the offline CI-helper regression
suite and verifies both existing versions, including their isolated consumers.
The same offline suite runs on relevant pull requests and pushes to `main`.

For a local check, with Git, Python, Maven and JDK 25 on `PATH`, fetch release
tags first (`git fetch origin --tags`). The verifier does not fetch or mutate Git:

```bash
python3 scripts/ci/release-check.py verify --version 1.0.4
python3 scripts/ci/release-check.py verify --version 1.0.5-SNAPSHOT
```

Verification retries only missing publications (404), throttling (429), server
errors and network failures, with a default 600-second propagation budget and
30-second individual HTTP timeouts. `--wait-seconds 0` disables propagation retries.
Wrong coordinates, malformed metadata, mixed snapshot builds, invalid signature
envelopes and permission errors fail immediately. Failures identify the affected
artifact URL. Temporary consumer projects and caches are removed on exit.

## Failure Recovery

Do not blindly rerun a failed release after `release:prepare`: that goal pushes the
release tag and next-development commit before publication. Inspect the existing
tag and Central deployment first; published versions and tags must not be replaced.

If only publication verification failed, rerun **Release Safeguards**, not the
publishing workflow. This can confirm propagation and consumer resolution without
rebuilding or republishing. A failed consumer or incorrect published metadata needs
investigation; do not conceal it with a retry of release preparation. Preserve
existing tags and published artifacts and use a new version for corrected artifacts.

## Known Metadata Follow-up (Not Required To Publish)

Many module POMs still point to `http://knowm.org/open-source/xchange/` in `<url>` and should be migrated in a separate metadata cleanup pass.
