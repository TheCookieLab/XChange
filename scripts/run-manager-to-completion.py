#!/usr/bin/env python3
"""
Setup and integration helper for xchange-module-manager. This script does
**not** drive fixing of issues, warnings, or build errors—the **agent**
(manager or worker) does. The script only:

  - **Setup:** Create a run, manifest, and worktrees so the manager can
    dispatch workers to each module.
  - **Optional probe:** Run compile/build per module and write
    build-failure.log on failure (diagnostic context for workers; no fixes).
  - **Integrate:** After workers have run and written worker-result.json,
    cherry-pick green-build commits onto main and run worktree cleanup.

Module list is synced with XChange/pom.xml. The manager agent dispatches
worker agents to each worktree with the task; workers do all fixing (build
errors, warnings, PMD, etc.) and write worker-result.json. Only after
workers are done should you run --integrate.

Usage: from XChange repo root:
  python3 scripts/run-manager-to-completion.py --new-run [--run-id ID]
  python3 scripts/run-manager-to-completion.py [run_id]              # ensure worktrees exist, exit
  python3 scripts/run-manager-to-completion.py --probe [run_id]       # optional: write build-failure.log
  python3 scripts/run-manager-to-completion.py --integrate [run_id]   # cherry-pick completed, cleanup
  python3 scripts/run-manager-to-completion.py --list-modules
"""
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
from datetime import datetime, timezone

TERMINAL_STATES = frozenset({"completed", "no_changes", "blocked", "failed"})
DEFAULT_BASE_SHA = "665c8d7abf84903c3e0aa5849a7ddb2c2d9a62d8"


def modules_from_pom(xchange_root: str) -> list[str]:
    """Return ordered list of module names from XChange/pom.xml <modules> section."""
    pom_path = os.path.join(xchange_root, "pom.xml")
    if not os.path.isfile(pom_path):
        return []
    with open(pom_path) as f:
        content = f.read()
    match = re.search(r"<modules>\s*(.*?)\s*</modules>", content, re.DOTALL)
    if not match:
        return []
    block = match.group(1)
    modules = re.findall(r"<module>\s*([^<]+?)\s*</module>", block)
    return [m.strip() for m in modules if m.strip()]


def ensure_worktree(
    xchange_root: str,
    worktrees_dir: str,
    artifact_id: str,
    base_sha: str,
    run_id_val: str,
) -> str:
    """Ensure worktree exists for module; return worktree path."""
    wt_path = os.path.join(worktrees_dir, artifact_id)
    if os.path.isdir(wt_path):
        return wt_path
    branch = f"agent/{run_id_val}/{artifact_id}"
    subprocess.run(
        ["git", "worktree", "add", wt_path, "-b", branch, base_sha],
        cwd=xchange_root,
        check=True,
        capture_output=True,
    )
    return wt_path


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Setup and integration for xchange-module-manager (no fixing; agents drive fixing)."
    )
    parser.add_argument("run_id", nargs="?", help="Run ID (default: latest in manager-runs)")
    parser.add_argument(
        "--new-run",
        action="store_true",
        help="Create a new run and worktrees for all modules from pom.xml",
    )
    parser.add_argument(
        "--integrate",
        action="store_true",
        help="Cherry-pick completed commits from worktrees onto main, then remove worktrees",
    )
    parser.add_argument(
        "--probe",
        action="store_true",
        help="Run compile and green build per pending module; write build-failure.log on failure (no fixes)",
    )
    parser.add_argument("--dry-run", action="store_true", help="List pending modules only")
    parser.add_argument("--list-modules", action="store_true", help="Print modules from pom.xml and exit")
    args = parser.parse_args()

    xchange_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    runs_dir = os.path.join(xchange_root, "manager-runs")
    pom_modules = modules_from_pom(xchange_root)

    if args.list_modules:
        for m in pom_modules:
            print(m)
        return 0

    if args.new_run:
        if not pom_modules:
            print("No modules in pom.xml", file=sys.stderr)
            return 1
        run_id = args.run_id or ("run_" + datetime.now(timezone.utc).strftime("%Y%m%d_%H%M%S"))
        run_dir = os.path.join(runs_dir, run_id)
        if os.path.isdir(run_dir):
            print(f"Run dir already exists: {run_dir}", file=sys.stderr)
            return 1
        os.makedirs(os.path.join(run_dir, "worktrees"), exist_ok=True)
        base_sha = (
            subprocess.run(
                ["git", "rev-parse", "HEAD"],
                cwd=xchange_root,
                capture_output=True,
                text=True,
            ).stdout.strip()
            or DEFAULT_BASE_SHA
        )
        ts = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
        manifest = {
            "run_id": run_id,
            "base_sha": base_sha,
            "task_description": "Fix build errors and resolve warnings for this module; report unresolved cross-module issues.",
            "mode": "local",
            "created_at": ts,
            "updated_at": ts,
            "modules": pom_modules,
            "module_state": {},
            "attempts": {},
            "unresolved_rollup_path": f"manager-runs/{run_id}/unresolved-rollup.md",
        }
        manifest_path = os.path.join(run_dir, "run-manifest.json")
        with open(manifest_path, "w") as f:
            json.dump(manifest, f, indent=2)
        with open(os.path.join(run_dir, "dispatch-log.ndjson"), "w") as f:
            f.write(
                json.dumps(
                    {
                        "ts": ts,
                        "event": "run_started",
                        "run_id": run_id,
                        "base_sha": base_sha,
                        "task": manifest["task_description"],
                        "mode": "local",
                    }
                )
                + "\n"
            )
        print(f"Created run {run_id} with {len(pom_modules)} modules from pom.xml")
        # Create worktrees for all modules
        worktrees_dir = os.path.join(run_dir, "worktrees")
        for artifact_id in pom_modules:
            ensure_worktree(xchange_root, worktrees_dir, artifact_id, base_sha, run_id)
        print("Worktrees ready. Dispatch worker agents to fix; then run --integrate to integrate and cleanup.")
        return 0

    if not os.path.isdir(runs_dir):
        print("manager-runs/ not found", file=sys.stderr)
        return 1

    if args.run_id:
        run_id = args.run_id
        run_dir = os.path.join(runs_dir, run_id)
        if not os.path.isdir(run_dir):
            print(f"Run dir not found: {run_dir}", file=sys.stderr)
            return 1
    else:
        run_ids = [d for d in os.listdir(runs_dir) if os.path.isdir(os.path.join(runs_dir, d))]
        if not run_ids:
            print("No runs in manager-runs/", file=sys.stderr)
            return 1
        run_id = sorted(run_ids)[-1]
        run_dir = os.path.join(runs_dir, run_id)
        print(f"Using latest run: {run_id}")

    manifest_path = os.path.join(run_dir, "run-manifest.json")
    with open(manifest_path) as f:
        manifest = json.load(f)

    # Sync manifest modules with pom.xml
    existing = set(manifest.get("modules") or [])
    from_pom = set(pom_modules)
    merged = sorted(existing | from_pom)
    if merged != manifest.get("modules"):
        manifest["modules"] = merged
        with open(manifest_path, "w") as f:
            json.dump(manifest, f, indent=2)

    modules = manifest["modules"]
    module_state = manifest.get("module_state") or {}
    base_sha = manifest.get("base_sha") or DEFAULT_BASE_SHA
    run_id_val = manifest.get("run_id") or run_id
    worktrees_dir = os.path.join(run_dir, "worktrees")
    os.makedirs(worktrees_dir, exist_ok=True)
    pending = [m for m in modules if module_state.get(m) not in TERMINAL_STATES]

    if args.integrate:
        # Cherry-pick all completed commits from worktrees onto main, then cleanup
        integrated = 0
        for artifact_id in modules:
            wt_path = os.path.join(worktrees_dir, artifact_id)
            result_path = os.path.join(wt_path, "worker-result.json")
            if not os.path.isfile(result_path):
                continue
            with open(result_path) as rf:
                result = json.load(rf)
            status = result.get("status")
            commit_sha = result.get("commit_sha")
            if status != "completed" or not commit_sha or commit_sha == "NO_CHANGES":
                continue
            proc = subprocess.run(
                ["git", "cherry-pick", "-x", commit_sha],
                cwd=xchange_root,
                capture_output=True,
                text=True,
            )
            if proc.returncode == 0:
                integrated += 1
                print(f"Integrated {artifact_id} ({commit_sha[:8]})")
            else:
                print(f"Cherry-pick failed for {artifact_id}: {proc.stderr or proc.stdout}", file=sys.stderr)
        print(f"Integrated {integrated} commits onto main.")
        # Run cleanup
        cleanup_script = os.path.join(xchange_root, "scripts", "remove-worker-worktrees.sh")
        if os.path.isfile(cleanup_script):
            subprocess.run(["bash", cleanup_script], cwd=xchange_root, check=True)
            print("Worktrees removed.")
        return 0

    if args.probe:
        # Run compile and green build per pending module; write build-failure.log on failure (no fixes)
        for artifact_id in pending:
            wt_path = ensure_worktree(xchange_root, worktrees_dir, artifact_id, base_sha, run_id_val)
            proc = subprocess.run(
                ["mvn", "-B", "-q", "-pl", artifact_id, "-am", "compile"],
                cwd=wt_path,
                capture_output=True,
                text=True,
                timeout=300,
            )
            if proc.returncode != 0:
                log_path = os.path.join(wt_path, "build-failure.log")
                with open(log_path, "w") as lf:
                    lf.write("# compile failed\n\n## stdout\n")
                    lf.write(proc.stdout or "")
                    lf.write("\n## stderr\n")
                    lf.write(proc.stderr or "")
                print(f"Probe: {artifact_id} compile failed -> build-failure.log")
                continue
            proc = subprocess.run(
                ["mvn", "-B", "-q", "-pl", artifact_id, "-am", "clean", "test"],
                cwd=wt_path,
                capture_output=True,
                text=True,
                timeout=600,
            )
            if proc.returncode != 0:
                log_path = os.path.join(wt_path, "build-failure.log")
                with open(log_path, "w") as lf:
                    lf.write("# green build (clean test) failed\n\n## stdout\n")
                    lf.write(proc.stdout or "")
                    lf.write("\n## stderr\n")
                    lf.write(proc.stderr or "")
                print(f"Probe: {artifact_id} green build failed -> build-failure.log")
        print("Probe done. Workers can use build-failure.log when present.")
        return 0

    # Default: ensure worktrees exist for all modules, then exit (no fixing)
    for artifact_id in pending:
        ensure_worktree(xchange_root, worktrees_dir, artifact_id, base_sha, run_id_val)
    if args.dry_run:
        print(f"Pending ({len(pending)}):", ", ".join(pending))
        return 0
    print(f"Worktrees ready for {len(modules)} modules. Dispatch worker agents to fix; then run --integrate.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
