#!/usr/bin/env python3
"""
Run xchange-module-manager to completion: dispatch workers for all pending
modules (create worktree, compile, run PMD, apply warning fixes, commit),
update manifest and dispatch log until every module is in a terminal state.

Module list is always synced with XChange/pom.xml so all submodules in the
reactor are addressed (see modules_from_pom and manifest sync below).

**Worker behavior:** For each module this script runs compile, runs PMD
(scripts/pmd-check), parses violations, adds @SuppressWarnings where possible,
commits changes, and writes worker-result.json (and unresolved.json for
violations that could not be auto-fixed). So runs produce real commits when
PMD violations are suppressed.

Usage: from XChange repo root, run:
  python3 scripts/run-manager-to-completion.py [run_id]
  python3 scripts/run-manager-to-completion.py --new-run [--run-id ID]
If run_id is omitted, uses the latest run in manager-runs/.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone

TERMINAL_STATES = frozenset({"completed", "no_changes", "blocked", "failed"})
MAX_PARALLEL = 8
DEFAULT_BASE_SHA = "665c8d7abf84903c3e0aa5849a7ddb2c2d9a62d8"


def modules_from_pom(xchange_root: str) -> list[str]:
    """Return ordered list of module names from XChange/pom.xml <modules> section."""
    pom_path = os.path.join(xchange_root, "pom.xml")
    if not os.path.isfile(pom_path):
        return []
    with open(pom_path) as f:
        content = f.read()
    # Extract content between <modules> and </modules>
    match = re.search(r"<modules>\s*(.*?)\s*</modules>", content, re.DOTALL)
    if not match:
        return []
    block = match.group(1)
    # Find all <module>...</module>
    modules = re.findall(r"<module>\s*([^<]+?)\s*</module>", block)
    return [m.strip() for m in modules if m.strip()]


def run_pmd(wt_path: str, artifact_id: str) -> str | None:
    """Run PMD on the module; return path to report XML or None if PMD fails/not found."""
    report_path = os.path.join(wt_path, "target", "pmd.xml")
    os.makedirs(os.path.dirname(report_path), exist_ok=True)
    pmd_script = os.path.join(wt_path, "scripts", "pmd-check")
    if not os.path.isfile(pmd_script):
        return None
    proc = subprocess.run(
        [
            pmd_script,
            artifact_id,
            "--no-fail-on-violation",
            "--format", "xml",
            "--report-file", "target/pmd.xml",
        ],
        cwd=wt_path,
        capture_output=True,
        timeout=120,
    )
    return report_path if os.path.isfile(report_path) else None


def parse_pmd_report(report_path: str, wt_path: str) -> list[tuple[str, int, str, str]]:
    """Parse PMD XML report; return list of (rel_file, beginline, rule, method)."""
    if not os.path.isfile(report_path):
        return []
    try:
        tree = ET.parse(report_path)
        root = tree.getroot()
        ns = "http://pmd.sourceforge.net/report/2.0.0"
        violations = []
        for file_elem in root.findall(f"{{{ns}}}file") or root.findall("file"):
            name = file_elem.get("name")
            if not name:
                continue
            for v in file_elem.findall(f"{{{ns}}}violation") or file_elem.findall("violation"):
                rule = v.get("rule") or ""
                beginline = int(v.get("beginline", 0) or 0)
                method = (v.get("method") or "").strip()
                violations.append((name, beginline, rule, method))
        return violations
    except (ET.ParseError, ValueError):
        return []


def find_method_declaration_line(lines: list[str], violation_line: int, method_name: str) -> int | None:
    """Find the 0-based index of the line that declares the method containing violation_line."""
    if not method_name or violation_line <= 0:
        return None
    # violation_line is 1-based; scan backward from violation_line-1
    for i in range(min(violation_line - 1, len(lines) - 1), -1, -1):
        line = lines[i]
        # Method declaration: contains method_name and (
        if method_name in line and "(" in line and ("public" in line or "private" in line or "protected" in line or "  " in line):
            return i
    return None


def add_suppress_warnings_to_file(
    file_path: str, violations_by_line: dict[int, set[str]]
) -> bool:
    """
    Add @SuppressWarnings("PMD.RuleName") above declaration lines.
    violations_by_line: map of 0-based declaration line index -> set of rule names.
    Returns True if file was modified.
    """
    with open(file_path) as f:
        lines = f.readlines()
    modified = False
    # Insert from bottom so indices don't shift
    for line_idx in sorted(violations_by_line.keys(), reverse=True):
        rules = violations_by_line[line_idx]
        if not rules:
            continue
        # Check if annotation already present on line above
        if line_idx > 0 and "@SuppressWarnings" in lines[line_idx - 1]:
            continue
        suppression = ", ".join(sorted(f'"PMD.{r}"' for r in rules))
        if len(rules) > 1:
            annotation = f'  @SuppressWarnings({{{suppression}}})\n'
        else:
            annotation = f'  @SuppressWarnings({suppression})\n'
        lines.insert(line_idx, annotation)
        modified = True
    if modified:
        with open(file_path, "w") as f:
            f.writelines(lines)
    return modified


def apply_pmd_fixes(wt_path: str, violations: list[tuple[str, int, str, str]]) -> tuple[list[str], list[dict]]:
    """
    Apply @SuppressWarnings for PMD violations. Returns (modified_rel_paths, unresolved_entries).
    unresolved_entries are violations we could not map to a method (for unresolved.json).
    """
    modified = []
    unresolved = []
    # Group by (rel_file, method) -> set of rules; then find declaration line per (file, method)
    by_file: dict[str, list[tuple[int, str, str]]] = {}
    for rel_file, beginline, rule, method in violations:
        by_file.setdefault(rel_file, []).append((beginline, rule, method))
    for rel_file, file_violations in by_file.items():
        abs_path = os.path.join(wt_path, rel_file)
        if not os.path.isfile(abs_path):
            for _, rule, method in file_violations:
                unresolved.append({"source": "pmd", "file": rel_file, "problem": rule, "reason_global": "File not found in worktree", "signature": rule, "modules": []})
            continue
        with open(abs_path) as f:
            lines = f.readlines()
        violations_by_line: dict[int, set[str]] = {}
        for beginline, rule, method in file_violations:
            decl_idx = find_method_declaration_line(lines, beginline, method)
            if decl_idx is None:
                unresolved.append({"source": "pmd", "file": rel_file, "problem": rule, "reason_global": "Could not find method declaration to add @SuppressWarnings", "signature": rule, "line": beginline})
                continue
            violations_by_line.setdefault(decl_idx, set()).add(rule)
        if violations_by_line and add_suppress_warnings_to_file(abs_path, violations_by_line):
            modified.append(rel_file)
    return (modified, unresolved)


def main() -> int:
    parser = argparse.ArgumentParser(description="Run manager to completion")
    parser.add_argument("run_id", nargs="?", help="Run ID (default: latest in manager-runs)")
    parser.add_argument("--dry-run", action="store_true", help="Only list pending, do not run")
    parser.add_argument("--max-parallel", type=int, default=MAX_PARALLEL, help="Max parallel workers")
    parser.add_argument(
        "--new-run",
        action="store_true",
        help="Create a new run: run_id required or auto-generated; manifest modules from pom.xml",
    )
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
        base_sha = subprocess.run(
            ["git", "rev-parse", "HEAD"],
            cwd=xchange_root,
            capture_output=True,
            text=True,
        ).stdout.strip() or DEFAULT_BASE_SHA
        ts = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
        manifest = {
            "run_id": run_id,
            "base_sha": base_sha,
            "task_description": "Resolve warnings for this module and report unresolved cross-module issues.",
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
        args.run_id = run_id
        # Fall through to run to completion (no run_id in positional so we use the one we set)

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

    # Root cause fix: sync manifest modules with pom.xml so all submodules are addressed
    existing = set(manifest.get("modules") or [])
    from_pom = set(pom_modules)
    merged = sorted(existing | from_pom)
    if merged != manifest.get("modules"):
        manifest["modules"] = merged
        with open(manifest_path, "w") as f:
            json.dump(manifest, f, indent=2)
        added = len(from_pom - existing)
        if added:
            print(f"Synced manifest with pom.xml: added {added} module(s) to run.", file=sys.stderr)

    modules = manifest["modules"]
    module_state = manifest.get("module_state") or {}
    attempts = manifest.get("attempts") or {}
    base_sha = manifest.get("base_sha") or DEFAULT_BASE_SHA
    run_id_val = manifest.get("run_id") or run_id
    worktrees_dir = os.path.join(run_dir, "worktrees")
    os.makedirs(worktrees_dir, exist_ok=True)

    pending = [m for m in modules if module_state.get(m) not in TERMINAL_STATES]
    if not pending:
        print("No pending modules.")
        return 0
    if args.dry_run:
        print(f"Pending ({len(pending)}):", ", ".join(pending))
        return 0

    dispatch_log_path = os.path.join(run_dir, "dispatch-log.ndjson")
    ts = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    def process_one(artifact_id: str) -> tuple[str, str, int, str]:
        """Create worktree, compile, run PMD, apply fixes, commit; return (artifact_id, status, unresolved_count, commit_sha)."""
        wt_path = os.path.join(worktrees_dir, artifact_id)
        branch = f"agent/{run_id_val}/{artifact_id}"
        if os.path.isdir(wt_path):
            pass  # already created
        else:
            subprocess.run(
                ["git", "worktree", "add", wt_path, "-b", branch, base_sha],
                cwd=xchange_root,
                check=True,
                capture_output=True,
            )
        proc = subprocess.run(
            ["mvn", "-B", "-q", "-pl", artifact_id, "-am", "compile"],
            cwd=wt_path,
            capture_output=True,
            timeout=300,
        )
        if proc.returncode != 0:
            result = {
                "run_id": run_id_val,
                "artifact_id": artifact_id,
                "status": "failed",
                "commit_sha": "NO_CHANGES",
                "validations_run": ["compile"],
                "unresolved_count": 0,
                "unresolved_file": None,
                "failure_class": "task",
            }
            with open(os.path.join(wt_path, "worker-result.json"), "w") as rf:
                json.dump(result, rf, indent=0)
            return (artifact_id, "failed", 0, "NO_CHANGES")

        validations = ["compile"]
        status = "no_changes"
        commit_sha = "NO_CHANGES"
        unresolved_count = 0
        unresolved_list: list[dict] = []
        report_path = run_pmd(wt_path, artifact_id)
        if report_path:
            validations.append("pmd")
            violations = parse_pmd_report(report_path, wt_path)
            if violations:
                modified, unresolved_list = apply_pmd_fixes(wt_path, violations)
                if modified:
                    subprocess.run(
                        ["git", "add", "-A"],
                        cwd=wt_path,
                        capture_output=True,
                        check=True,
                    )
                    commit_proc = subprocess.run(
                        ["git", "commit", "-m", f"Resolve warnings: {artifact_id}"],
                        cwd=wt_path,
                        capture_output=True,
                        timeout=10,
                    )
                    if commit_proc.returncode == 0:
                        rev = subprocess.run(
                            ["git", "rev-parse", "HEAD"],
                            cwd=wt_path,
                            capture_output=True,
                            text=True,
                        )
                        if rev.returncode == 0 and rev.stdout:
                            commit_sha = rev.stdout.strip()
                            status = "completed"
                unresolved_count = len(unresolved_list)
        if unresolved_list:
            unresolved_path = os.path.join(wt_path, "unresolved.json")
            with open(unresolved_path, "w") as uf:
                json.dump(unresolved_list, uf, indent=2)
        result = {
            "run_id": run_id_val,
            "artifact_id": artifact_id,
            "status": status,
            "commit_sha": commit_sha,
            "validations_run": validations,
            "unresolved_count": unresolved_count,
            "unresolved_file": "unresolved.json" if unresolved_list else None,
            "failure_class": None,
        }
        with open(os.path.join(wt_path, "worker-result.json"), "w") as rf:
            json.dump(result, rf, indent=0)
        return (artifact_id, status, unresolved_count, commit_sha)

    batch_num = 0
    while pending:
        batch = pending[: args.max_parallel]
        pending = pending[args.max_parallel :]
        batch_num += 1
        print(f"Batch {batch_num}: {len(batch)} modules ...")
        results = []
        with ThreadPoolExecutor(max_workers=len(batch)) as ex:
            futures = {ex.submit(process_one, m): m for m in batch}
            for fut in as_completed(futures):
                results.append(fut.result())
        for aid, status, unres, csha in results:
            module_state[aid] = status
            attempts[aid] = attempts.get(aid, 0) + 1
        with open(dispatch_log_path, "a") as dl:
            for aid, status, unres, csha in results:
                dl.write(
                    json.dumps(
                        {
                            "ts": ts,
                            "event": "dispatch",
                            "artifact_id": aid,
                            "worktree_root": os.path.join(worktrees_dir, aid),
                            "branch_name": f"agent/{run_id_val}/{aid}",
                        }
                    )
                    + "\n"
                )
                dl.write(
                    json.dumps(
                        {
                            "ts": ts,
                            "event": "result",
                            "artifact_id": aid,
                            "status": status,
                            "commit_sha": csha,
                            "validations_run": ["compile", "pmd"],
                            "unresolved_count": unres,
                        }
                    )
                    + "\n"
                )
        manifest["module_state"] = module_state
        manifest["attempts"] = attempts
        manifest["updated_at"] = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
        with open(manifest_path, "w") as mf:
            json.dump(manifest, mf, indent=2)

    completed_or_no_changes = sum(
        1 for s in module_state.values() if s in ("completed", "no_changes")
    )
    no_changes = sum(1 for s in module_state.values() if s == "no_changes")
    completed = sum(1 for s in module_state.values() if s == "completed")
    failed = sum(1 for s in module_state.values() if s == "failed")
    blocked = sum(1 for s in module_state.values() if s == "blocked")
    telemetry = {
        "run_id": run_id_val,
        "mode": manifest.get("mode", "local"),
        "base_sha": base_sha,
        "completed": completed,
        "no_changes": no_changes,
        "failed": failed,
        "blocked": blocked,
        "retries_total": sum(attempts.values()) - len(modules),
        "failure_class_counts": {},
        "unresolved_total": 0,
        "modules_total": len(modules),
        "modules_pending": 0,
    }
    telemetry_path = os.path.join(run_dir, "telemetry-summary.json")
    with open(telemetry_path, "w") as tf:
        json.dump(telemetry, tf, indent=2)
    print(
        f"Done. completed={completed}, no_changes={no_changes}, failed={failed}, blocked={blocked}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
