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
4. Run from `main`.
5. Confirm workflow success and generated tag.

## Post-Release Validation

1. Confirm artifacts are visible in Maven Central for group `com.github.thecookielab.xchange`.
2. Confirm the next snapshot version is committed.
3. Publish release notes using `/RELEASE_NOTES_TEMPLATE.md`.

## Known Metadata Follow-up (Not Required To Publish)

Many module POMs still point to `http://knowm.org/open-source/xchange/` in `<url>` and should be migrated in a separate metadata cleanup pass.
