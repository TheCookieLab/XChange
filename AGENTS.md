# AGENTS Instructions for XChange

**Audience:** AI agents and assistants. Read this when operating in the XChange repo (xchange-parent or any submodule).

**Scope:** This repo is the XChange library (Java, Maven, 100+ exchange adapters). It lives in the ta4j-org workspace; for workspace rules (worktrees, paths, build), see the workspace root `AGENTS.md` and `.cursor/README.md`.

---

## Build and validation

- **Full build:** From repo root: `mvn -B clean install` (or `mvn -B clean test`). No `scripts/run-full-build-quiet.sh` in this repo; use Maven directly.
- **Single module:** `mvn -pl <module> -am compile` (or `test`, `install`) from repo root.
- For any code or `pom.xml` change, run at least the affected module build before considering the task complete.

---

## Inventory: subagents and tools

Agents and skills specific to XChange live under this repo. When working in XChange, prefer these over workspace-level agents for XChange tasks.

### Subagents (`.cursor/agents/`)

| Agent | Purpose |
|-------|--------|
| **xchange-module-worker** | Autonomous per-module worker for large-scale XChange tasks. Performs one assigned task in one submodule using a dedicated worktree/branch from a manager-provided base SHA, chooses its own implementation/verification approach, must report at least one validation in structured `worker-result.json`, and reports unresolved cross-module issues (`unresolved.md` + schema-aligned `unresolved.json`). |
| **xchange-module-manager** | Lightweight dispatcher for **xchange-module-worker** across XChange submodules. Orchestrates worker launch and structured result collection, maintains a resumable run manifest, applies timeout/retry policy, aggregates unresolved issues, and integrates worker commits. Uses local workers by default; cloud workers are enabled only when explicitly requested (for example: `start the xchange module manager with cloud subagents enabled`). |

These agents are for work that needs the same or similar changes in many submodules but is too complex for a single global search/replace. Use full artifactId in path/branch naming (for example worktrees at `<workspace>/worktrees/xchange-<taskslug>-<artifactId>/` and branches at `agent/<taskslug>/<artifactId>`), with main clone `<workspace>/XChange/`. Master unresolved list: `XChange/unresolved.md`.

### Skills (`.cursor/skills/`)

- **xchange-pmd-check** — runs XChange PMD analysis via `scripts/pmd-check` (full project, selected modules, or changed modules), using XChange-specific static-analysis configuration.

### Other tools and conventions

- **Module list:** Submodules are defined in `pom.xml` under `<modules>`. Use that list for batch or fleet operations.
- **Parent POM:** This repo root is `xchange-parent`; do not change the parent POM from a single-module subagent—record such needs in `unresolved.md` for the manager or a follow-up pass.
- **Subagent contracts:** See `.cursor/agents/contracts/` for run-manifest, worker-result, and unresolved-issue schemas.

---

## File reference rules

- When summarizing changes in **global** (cross-repo) work from the ta4j-org workspace, use worktree-rooted absolute paths per workspace `AGENTS.md`.
- When working only inside XChange (this repo), paths relative to repo root or absolute paths under this repo are fine.

---

## Summary for agent decision logic

1. **Build:** Use `mvn` from repo root; at least build the touched module(s) before completion.
2. **Large-scale per-module tasks (e.g. warnings cleanup):** Use **xchange-module-worker** for one module; use **xchange-module-manager** to run the full fleet. Default task for both is resolve warnings when no other task is specified.
3. **Scoped rules:** Deeper `AGENTS.md` in subdirs override this file when present.
