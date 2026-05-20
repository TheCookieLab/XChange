#!/usr/bin/env python3
"""Warn when scheduled integration workflows never receive a runner."""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.parse
import urllib.request
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any


DEFAULT_STREAK_THRESHOLD = 3


def integration_workflow_paths() -> list[str]:
    paths: list[str] = []
    for workflow in sorted(Path(".github/workflows").glob("*.yaml")):
        text = workflow.read_text(encoding="utf-8")
        if "uses: ./.github/workflows/it-template.yaml" in text:
            paths.append(str(workflow))
    return paths


def classify_runs(
        repository: str,
        branch: str,
        token: str,
        paths: list[str],
        lookback_hours: int,
        stuck_minutes: int,
        streak_threshold: int,
) -> dict[str, Any]:
    runs = github_json(
            token,
            repository,
            "/actions/runs?"
            + urllib.parse.urlencode(
                    {"branch": branch, "event": "schedule", "per_page": 100}
            ),
    ).get("workflow_runs", [])
    cutoff = datetime.now(timezone.utc) - timedelta(hours=lookback_hours)
    problems: list[dict[str, Any]] = []

    for workflow_path in paths:
        workflow_runs = [run for run in runs if run.get("path") == workflow_path]
        if not workflow_runs:
            continue
        latest = workflow_runs[0]
        created_at = parse_time(latest.get("created_at"))
        if created_at and created_at < cutoff:
            continue

        issue = classify_single_run(repository, token, latest, stuck_minutes)
        if issue is None:
            continue

        streak = count_streak(repository, token, workflow_runs, issue["category"], streak_threshold)
        issue["streak"] = streak
        issue["workflow_path"] = workflow_path
        problems.append(issue)

    status = "success"
    if any(problem["streak"]["count"] >= streak_threshold for problem in problems):
        status = "persistent_transient_failure"
    elif problems:
        status = "transient_warning"

    return {
            "status": status,
            "branch": branch,
            "problems": problems,
            "streak_threshold": streak_threshold,
    }


def classify_single_run(
        repository: str, token: str, run: dict[str, Any], stuck_minutes: int
) -> dict[str, Any] | None:
    status = run.get("status")
    conclusion = run.get("conclusion")
    run_id = run.get("id")

    if status == "queued":
        created_at = parse_time(run.get("created_at"))
        age_minutes = 0
        if created_at:
            age_minutes = int((datetime.now(timezone.utc) - created_at).total_seconds() / 60)
        if age_minutes >= stuck_minutes:
            return build_issue(run, "queued_no_runner", f"queued for {age_minutes} minutes")
        return None

    if status == "completed" and conclusion == "failure":
        timing = github_json(token, repository, f"/actions/runs/{run_id}/timing")
        total_ms = sum(
                int(platform.get("total_ms") or 0)
                for platform in timing.get("billable", {}).values()
        )
        jobs = github_json(token, repository, f"/actions/runs/{run_id}/jobs?per_page=100").get(
                "jobs", []
        )
        no_job_started = bool(jobs) and all(not job_started(job) for job in jobs)
        if total_ms == 0 and no_job_started:
            return build_issue(run, "queued_no_runner", "completed as failure with zero billable runner time")

    return None


def job_started(job: dict[str, Any]) -> bool:
    return bool(job.get("started_at")) or bool(job.get("runner_name"))


def build_issue(run: dict[str, Any], category: str, reason: str) -> dict[str, Any]:
    return {
            "category": category,
            "reason": reason,
            "run_id": run.get("id"),
            "url": run.get("html_url"),
            "created_at": run.get("created_at"),
            "status": run.get("status"),
            "conclusion": run.get("conclusion"),
    }


def count_streak(
        repository: str,
        token: str,
        workflow_runs: list[dict[str, Any]],
        category: str,
        threshold: int,
) -> dict[str, Any]:
    count = 0
    run_ids: list[int] = []
    for run in workflow_runs:
        issue = classify_single_run(repository, token, run, stuck_minutes=0)
        if issue is None or issue["category"] != category:
            break
        count += 1
        run_ids.append(run["id"])
        if count >= threshold:
            break
    return {"count": count, "run_ids": run_ids}


def parse_time(value: str | None) -> datetime | None:
    if not value:
        return None
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def github_json(token: str, repository: str, api_path: str) -> dict[str, Any]:
    request = urllib.request.Request(
            f"https://api.github.com/repos/{repository}{api_path}",
            headers={
                    "Accept": "application/vnd.github+json",
                    "Authorization": f"Bearer {token}",
                    "X-GitHub-Api-Version": "2022-11-28",
            },
    )
    with urllib.request.urlopen(request, timeout=20) as response:
        return json.loads(response.read().decode("utf-8"))


def write_outputs(classification: dict[str, Any], output_dir: Path) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    output_dir.joinpath("classification.json").write_text(
            json.dumps(classification, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )

    for problem in classification["problems"]:
        message = f"{problem['workflow_path']} {problem['reason']} ({problem['url']})"
        print(f"::warning title=XChange queued integration workflow::{escape_annotation(message)}")

    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with Path(summary_path).open("a", encoding="utf-8") as summary:
            summary.write("### Integration CI health\n\n")
            summary.write(f"- Status: `{classification['status']}`\n")
            summary.write(f"- Problems: {len(classification['problems'])}\n")
            for problem in classification["problems"]:
                summary.write(
                        f"- `{problem['workflow_path']}`: {problem['reason']}, streak "
                        f"{problem['streak']['count']}\n"
                )


def escape_annotation(message: str) -> str:
    return message.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--branch", default=os.environ.get("GITHUB_REF_NAME", "main"))
    parser.add_argument("--lookback-hours", type=int, default=8)
    parser.add_argument("--stuck-minutes", type=int, default=15)
    parser.add_argument("--streak-threshold", type=int, default=DEFAULT_STREAK_THRESHOLD)
    parser.add_argument("--output-dir", type=Path, default=Path("target/it-ci-health"))
    args = parser.parse_args(argv)

    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
    repository = os.environ.get("GITHUB_REPOSITORY")
    if not token or not repository:
        print("GITHUB_TOKEN/GITHUB_REPOSITORY are required for integration CI health checks.", file=sys.stderr)
        return 2

    classification = classify_runs(
            repository,
            args.branch,
            token,
            integration_workflow_paths(),
            args.lookback_hours,
            args.stuck_minutes,
            args.streak_threshold,
    )
    write_outputs(classification, args.output_dir)
    return 1 if classification["status"] == "persistent_transient_failure" else 0


if __name__ == "__main__":
    sys.exit(main())
