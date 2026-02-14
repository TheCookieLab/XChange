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
| **xchange-module-worker** | Per-module worker for large-scale XChange tasks. Performs one assigned task in one submodule in isolation using a dedicated worktree and dedicated branch from a manager-provided base SHA. **Default task** (when none specified): resolve compiler/linter warnings from deterministic Maven verification commands, and write cross-module issues to `unresolved.md` in that worktree. For other tasks, the manager or user supplies task details and verification commands. Invoke with a module artifactId (e.g. “Use the xchange-module-worker subagent for module xchange-coinbase”). |
| **xchange-module-manager** | Orchestrates **xchange-module-worker** across all submodules for large-scale, near-global tasks. Batches dispatch, verifies each module with explicit commands, merges worker commits into the main clone via `cherry-pick -x`, aggregates and deduplicates `unresolved.md`, then addresses global issues and raises decisions to the user. **Default task** (when none specified): resolve warnings across the codebase. For other tasks, specify the task when invoking; manager passes task-specific instructions and verification criteria to workers. |

These agents are for work that needs the same or similar changes in many submodules but is too complex for a single global search/replace. Default path/branch convention uses full artifactId everywhere: worktrees at `<workspace>/worktrees/xchange-warnings-<artifactId>/`, branches at `agent/warnings/<artifactId>`, main clone `<workspace>/XChange/`. Master unresolved list: `XChange/unresolved.md`.

### Skills (`.cursor/skills/`)

- **xchange-pmd-check** — runs XChange PMD analysis via `scripts/pmd-check` (full project, selected modules, or changed modules), using XChange-specific static-analysis configuration.

### Other tools and conventions

- **Module list:** Submodules are defined in `pom.xml` under `<modules>`. Use that list for batch or fleet operations.
- **Parent POM:** This repo root is `xchange-parent`; do not change the parent POM from a single-module subagent—record such needs in `unresolved.md` for the manager or a follow-up pass.

---

## File reference rules

- When summarizing changes in **global** (cross-repo) work from the ta4j-org workspace, use worktree-rooted absolute paths per workspace `AGENTS.md`.
- When working only inside XChange (this repo), paths relative to repo root or absolute paths under this repo are fine.

---

## Summary for agent decision logic

1. **Build:** Use `mvn` from repo root; at least build the touched module(s) before completion.
2. **Large-scale per-module tasks (e.g. warnings cleanup):** Use **xchange-module-worker** for one module; use **xchange-module-manager** to run the full fleet. Default task for both is resolve warnings when no other task is specified.
3. **Scoped rules:** Deeper `AGENTS.md` in subdirs override this file when present.
