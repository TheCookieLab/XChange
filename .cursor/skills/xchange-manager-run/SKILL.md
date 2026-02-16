---
name: xchange-manager-run
description: Run the XChange module-manager setup and integration script (worktrees, optional probe, integrate). Use when setting up a manager run, probing for build failures, or integrating worker commits and cleaning up. Scoped to XChange; agents drive fixing; this skill wraps the script only.
---

# XChange Manager Run Script

Use this skill when you need to **set up** a manager run, **probe** modules for build failures, **integrate** worker commits onto main, or **list modules**. The script does **not** fix issues, warnings, or build errors—the **manager and worker agents** do that. The script only prepares worktrees, optionally gathers failure context, and integrates/cleans up after workers are done.

**Canonical script:** From XChange repo root, run:

```bash
python3 scripts/run-manager-to-completion.py <mode> [run_id]
```

---

## Modes

### Create a new run and worktrees (`--new-run`)

Creates a run directory under `manager-runs/<run_id>/`, a manifest with modules from `pom.xml`, and a worktree per module. Use this **before** the manager dispatches workers.

```bash
python3 scripts/run-manager-to-completion.py --new-run
```

Optional explicit run ID:

```bash
python3 scripts/run-manager-to-completion.py --new-run --run-id my-run-001
```

After this, **dispatch worker agents** to each worktree; workers do all fixing and write `worker-result.json`.

### Ensure worktrees exist (default, no flag)

Given a run ID (or latest run), ensures a worktree exists for every module in the manifest. Does not run Maven or change any code. Use when the manager needs worktrees ready before dispatching.

```bash
python3 scripts/run-manager-to-completion.py
python3 scripts/run-manager-to-completion.py run_20260216_194810
```

With `--dry-run`, only lists pending modules:

```bash
python3 scripts/run-manager-to-completion.py --dry-run
```

### Probe for build failures (`--probe`)

Runs compile and green build per **pending** module; on failure writes **`build-failure.log`** in that module's worktree. No code fixes, no `worker-result.json` from the script. Use so workers have diagnostic context when they run.

```bash
python3 scripts/run-manager-to-completion.py --probe
python3 scripts/run-manager-to-completion.py --probe run_20260216_194810
```

### Integrate and cleanup (`--integrate`)

After **all workers have run** and written `worker-result.json`: for each worktree with `status=completed` and a non–`NO_CHANGES` `commit_sha`, cherry-picks that commit onto main, then runs `scripts/remove-worker-worktrees.sh`. Run this **only after** the manager has collected results and all modules are in a terminal state.

```bash
python3 scripts/run-manager-to-completion.py --integrate
python3 scripts/run-manager-to-completion.py --integrate run_20260216_194810
```

### List modules (`--list-modules`)

Prints module names from `XChange/pom.xml` and exits. Use to get the canonical module list.

```bash
python3 scripts/run-manager-to-completion.py --list-modules
```

---

## Division of responsibility

| Who / what | Responsibility |
|------------|----------------|
| **Script** | Create run + worktrees; optional probe → `build-failure.log`; integrate (cherry-pick + cleanup). No fixing, no commits from the script. |
| **Manager agent** | Dispatch workers, collect `worker-result.json`, update manifest, then run script `--integrate` (or ensure it runs). |
| **Worker agents** | Fix build errors, warnings, PMD, etc.; run green build; commit only when green; write `worker-result.json`. |

---

## Typical flow

1. **Setup:** `python3 scripts/run-manager-to-completion.py --new-run`
2. **Optional probe:** `python3 scripts/run-manager-to-completion.py --probe <run_id>` (so workers see `build-failure.log` where applicable)
3. **Manager** dispatches worker agents to each worktree; workers fix and write `worker-result.json`
4. **Manager** collects results and updates manifest
5. **Integrate:** `python3 scripts/run-manager-to-completion.py --integrate <run_id>`

---

## Agent guidance

- When the user asks to "run the manager," "set up a manager run," or "integrate worker commits," use this skill and the appropriate mode.
- Never use the script to perform fixes; only setup, probe, and integrate.
- Run `--integrate` only after workers have finished and the manager has confirmed all modules are in a terminal state.
