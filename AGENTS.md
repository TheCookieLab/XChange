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
| **xchange-module-warnings** | Fixes IDE/compiler warnings in **one** XChange module. Creates an isolated worktree on `main` (no branch), fixes local warnings, writes global issues to `unresolved.md` in that worktree. Invoke with a module name (e.g. “Use the xchange-module-warnings subagent for module xchange-coinbase”). |
| **xchange-module-warnings-manager** | Orchestrates **xchange-module-warnings** across all submodules: batches dispatch, verifies and merges each worktree into the main clone, aggregates and deduplicates `unresolved.md` at repo root, then addresses global issues and raises decisions to the user. Use to clear all Problems across XChange in one run. |

Path convention for the warnings workflow: worktrees live at `<workspace>/worktrees/xchange-warnings-<module>/`; main clone is `<workspace>/XChange/`. Master unresolved list: `XChange/unresolved.md`.

### Skills (`.cursor/skills/`)

- *(None yet. Add project-specific skills here as they are created.)*

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
2. **Warnings cleanup:** Use **xchange-module-warnings** for one module; use **xchange-module-warnings-manager** to run the full fleet and then resolve global issues.
3. **Scoped rules:** Deeper `AGENTS.md` in subdirs override this file when present.
