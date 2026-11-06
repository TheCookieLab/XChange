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
   - `release_version` (for example `0.1.0`)
   - `next_snapshot_version` (for example `0.1.1-SNAPSHOT`)

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
5. Confirm workflow success. It waits for Central publication, creates the GitHub
   release at `xchange-parent-<release_version>`, and publishes the next snapshot.

## Post-Release Validation

1. Confirm artifacts are visible in Maven Central for group `com.github.thecookielab.xchange`.
2. Confirm the GitHub release, tag, and Central artifacts use the same release version.
3. Confirm the next snapshot version is committed and available in the snapshots repository.
4. Keep the README's stable version, dependency example, and snapshot version aligned.

Do not blindly rerun a failed release after `release:prepare`: that goal pushes the
release tag and next-development commit before publication. Inspect the existing
tag and Central deployment first; published versions and tags must not be replaced.

## Known Metadata Follow-up (Not Required To Publish)

Many module POMs still point to `http://knowm.org/open-source/xchange/` in `<url>` and should be migrated in a separate metadata cleanup pass.
