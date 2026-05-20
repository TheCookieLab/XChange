#!/usr/bin/env python3

from __future__ import annotations

import importlib.util
import sys
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path


SCRIPT_PATH = Path(__file__).with_name("check-integration-health.py")
SPEC = importlib.util.spec_from_file_location("check_integration_health", SCRIPT_PATH)
health = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = health
SPEC.loader.exec_module(health)


class IntegrationHealthTest(unittest.TestCase):

    def test_completed_failure_with_started_job_is_not_queued_no_runner(self) -> None:
        def fake_github_json(token: str, repository: str, api_path: str) -> dict:
            if api_path.endswith("/timing"):
                return {"billable": {"UBUNTU": {"total_ms": 0}}}
            if api_path.endswith("/jobs?per_page=100"):
                return {"jobs": [{"started_at": "2026-05-20T05:17:00Z", "runner_name": "GitHub Actions 1"}]}
            raise AssertionError(api_path)

        original = health.github_json
        health.github_json = fake_github_json
        try:
            issue = health.classify_single_run(
                    "TheCookieLab/XChange",
                    "token",
                    {"id": 1, "status": "completed", "conclusion": "failure"},
                    stuck_minutes=15,
            )
        finally:
            health.github_json = original

        self.assertIsNone(issue)

    def test_completed_failure_without_started_job_is_queued_no_runner(self) -> None:
        def fake_github_json(token: str, repository: str, api_path: str) -> dict:
            if api_path.endswith("/timing"):
                return {"billable": {"UBUNTU": {"total_ms": 0}}}
            if api_path.endswith("/jobs?per_page=100"):
                return {"jobs": [{"started_at": None, "runner_name": ""}]}
            raise AssertionError(api_path)

        original = health.github_json
        health.github_json = fake_github_json
        try:
            issue = health.classify_single_run(
                    "TheCookieLab/XChange",
                    "token",
                    {"id": 1, "status": "completed", "conclusion": "failure"},
                    stuck_minutes=15,
            )
        finally:
            health.github_json = original

        self.assertIsNotNone(issue)
        assert issue is not None
        self.assertEqual("queued_no_runner", issue["category"])

    def test_old_queued_run_is_queued_no_runner(self) -> None:
        created_at = datetime.now(timezone.utc) - timedelta(minutes=30)

        issue = health.classify_single_run(
                "TheCookieLab/XChange",
                "token",
                {
                        "id": 1,
                        "status": "queued",
                        "created_at": created_at.isoformat().replace("+00:00", "Z"),
                        "html_url": "https://github.com/TheCookieLab/XChange/actions/runs/1",
                },
                stuck_minutes=15,
        )

        self.assertIsNotNone(issue)
        assert issue is not None
        self.assertEqual("queued_no_runner", issue["category"])


if __name__ == "__main__":
    unittest.main()
