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
5. If a blocker cannot be resolved in scope, record it in `unresolved.md` and schema-aligned `unresolved.json` and raise attention to it during summary/inbox.

## Build and Validation

- Full build: `mvn -B clean install`
- Unit tests: `mvn -B clean test`
- Unit and integration tests: `mvn -B clean verify -DskipIntegrationTests=false`
- Single module: `mvn -B -pl <module> -am test`
- Compile-only quick check: `mvn -B -pl <module> -am compile`
- PMD: use the `xchange-pmd-check` skill or `scripts/pmd-check`

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
  convergence or record the blocker in `unresolved.md` and `unresolved.json`.

## XChange Agents and Skills

Use repo-local agents and skills for XChange work before broader workspace tools.

### Agents

| Agent | Use |
| --- | --- |
| `xchange-module-worker` | One autonomous task in one module, using a dedicated worktree and branch. The worker must inventory, fix, revalidate, produce `worker-result.json`, and report unresolved issues in `unresolved.md` plus `unresolved.json`. |
| `xchange-module-manager` | Dispatches `xchange-module-worker` across many modules, manages retries/timeouts, validates worker results, integrates green worker commits, and rolls up unresolved issues. |

Use the manager for repeated module work such as warning cleanup, migration passes,
or build failures that are too broad for one direct edit. Use full artifact IDs in
worker path and branch names, for example
`<workspace>/worktrees/xchange-<taskslug>-<artifactId>/` and
`agent/<taskslug>/<artifactId>`.

### Skills

- `xchange-pmd-check`: runs XChange PMD analysis through `scripts/pmd-check`.
- `xchange-manager-run`: documents `scripts/run-manager-to-completion.py` setup,
  probe, integration, and cleanup modes. The script prepares and integrates work;
  agents perform the fixes.

## Repo Conventions

- The canonical module list is the root `pom.xml` `<modules>` section.
- The root project is `xchange-parent`.
- A single-module worker must not change the parent POM. Record parent-POM needs
  as unresolved for the manager or a follow-up pass.
- Subagent contracts live in `.cursor/agents/contracts/`.
- For XChange-only summaries, repo-relative paths are acceptable. For cross-repo
  workspace summaries, use worktree-rooted absolute paths.
