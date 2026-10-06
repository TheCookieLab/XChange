# AGENTS Instructions for XChange

Read this before operating anywhere in the XChange repo, including `xchange-parent`
and any exchange submodule.

## Scope

XChange is a Java/Maven library with many exchange adapters. This file covers the
repo root and all child directories unless a deeper `AGENTS.md` overrides it.

For workspace-level rules such as worktree handling, pull requests, GitHub
comments, and PRD delivery, follow the workspace root instructions first.

## Default Workflow

1. Work from the repo root unless a module-specific command requires otherwise.
2. Keep changes scoped to the requested module or cross-module concern.
3. Reuse existing APIs and patterns; avoid new public API unless it is required.
4. Fix warnings and static-analysis findings at root cause using a red-green refactor approach. Do not add `@SuppressWarnings`, disable rules, widen exclusions, or hide diagnostics.
5. If a blocker cannot be resolved in scope, report it in the PR description and final summary; do not commit blocker notes to the repo.

## Build and Validation

- Full build: `mvn -B clean install`
- Unit tests: `mvn -B clean test`
- Unit and integration tests: `mvn -B clean verify -DskipIntegrationTests=false`
- Single module: `mvn -B -pl <module> -am test`
- Compile-only quick check: `mvn -B -pl <module> -am compile`
- PMD: `scripts/pmd-check`

Scheduled exchange integration workflows run through
`scripts/ci/run-integration-module.py`, which preserves the Maven command while
classifying allowlisted external/setup failures as transient warnings. Assertion
failures, compilation errors, Jackson mapping errors, and repo-owned null
pointer failures remain hard failures. If the same transient fingerprint repeats
for three consecutive classified runs, treat it as a real failure and investigate
the exchange adapter or CI setup. CI artifacts include `classification.json` and
`maven.redacted.log`; keep raw Maven logs on the runner only.

For any code or `pom.xml` change, run at least the affected module build before
completion. Use Maven directly; this repo does not provide
`scripts/run-full-build-quiet.sh`.

## Dependency Maintenance

- "Latest" means the latest stable Maven Central release.
- Do not adopt alpha, beta, milestone, RC, preview, early-access, snapshot, or
  classifier-specific variants unless a security advisory has no stable fix.
- Run update reports with:
  `mvn -B versions:display-dependency-updates versions:display-plugin-updates versions:display-property-updates`
- The Maven Versions plugin uses `config/dependency-updates/version-rules.xml` to
  reject prerelease candidates from normal reports.
- Run vulnerability audits with Dependabot alert review plus:
  `mvn -B org.owasp:dependency-check-maven:check -DskipIntegrationTests=true`
- Centralize shared dependency and plugin versions in the root parent POM.
- Keep module-local versions or plugin configuration only when behavior
  intentionally differs, and document the reason in that module.
- Do not add or retain Maven Enforcer dependency-convergence skips. Fix
  convergence or report the blocker as described in Default Workflow.

## Repo Conventions

- The canonical module list is the root `pom.xml` `<modules>` section.
- Lombok `@Data`/`@Getter`/`@Setter`/`@Value` DTOs: never rely on accessor-derived
  property names for fields whose name has an uppercase letter in the first two
  characters (e.g. `T`, `O`, `oT`, `qU`, `bT`, `fC`). Lombok generates
  `getT()`/`getOT()`-style accessors, and Jackson's default legacy bean mangling
  lowercases the leading uppercase run, so the derived property (`T` → `t`,
  `oT` → `ot`) never matches the exchange's wire key. The field is silently
  dropped during deserialization — a runtime-only failure with no compile error.
  Put an explicit `@JsonProperty` naming the exact wire/JSON key — the
  exchange's key, not the Java field name (case matters: field `bT` has wire key
  `BT`) — on the field and on its getter and setter so they merge into one
  property. Do not use Lombok's `onMethod_`/`onMethod` attributes for this:
  they are processor-only aliases that the maven-javadoc-plugin doclet cannot
  resolve; declare the accessors manually with the annotation instead (Lombok
  then skips its generated versions). Never declare two fields that differ only
  by case (e.g. `T` and `t`): Lombok silently generates one `getT()`/`setT()`
  pair and the other field is never bound.
- The root project is `xchange-parent`.
- A single-module change must not change the parent POM. Report parent-POM needs
  as a blocker for a follow-up pass.
- For XChange-only summaries, repo-relative paths are acceptable. For cross-repo
  workspace summaries, use worktree-rooted absolute paths.

## Test Runtime Discipline

- Keep unit tests isolated from live exchanges. Mock HTTP and WebSocket collaborators for service behavior; use a dynamic-port local server only when request encoding, transport handling, or protocol behavior is the assertion.
- Keep live external calls in Failsafe `*Integration.java` tests run with `-DskipIntegrationTests=false`; do not move them into the default unit-test surface.
- Use minimal canned payloads that cover nominal, boundary, invalid, and exchange-specific interaction cases. Keep one exhaustive owner for a meaningful protocol or data matrix instead of repeating it across raw, service, and DTO tests.
- During iteration, run focused module/class tests without `clean`, for example `mvn -B -pl <module> -am -Dtest=<TestClass> test`. Reserve clean reactor commands for final or release validation.
- Replace sleeps and retry delays with deterministic signals or default-preserving test seams. Do not increase timeouts as the first response to a slow or flaky test.
- Bound concurrency at isolated module or workflow lanes. Do not enable blanket JUnit parallelism across adapters that may share static clients, process state, ports, or mutable fixtures.
- Before optimizing, record the Surefire/Failsafe slowest five and retain comparable before/after evidence. Keep local and hosted Maven goals, integration filters, reports, and quality gates behaviorally equivalent.
