#!/usr/bin/env python3

from __future__ import annotations

import re
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
WORKFLOW_DIR = REPO_ROOT / ".github" / "workflows"
IT_TEMPLATE_CALL = re.compile(r"^(?P<indent>\s*)uses:\s*\./\.github/workflows/it-template\.yaml\s*$")
KEY_VALUE = re.compile(r"^(?P<indent>\s*)(?P<key>[A-Za-z0-9_-]+):\s*(?P<value>.*?)\s*(?:#.*)?$")
REQUIRED_PERMISSIONS = {"actions": {"read", "write"}, "contents": {"read", "write"}}


def workflow_paths() -> list[Path]:
    return sorted({*WORKFLOW_DIR.glob("*.yaml"), *WORKFLOW_DIR.glob("*.yml")})


def leading_spaces(line: str) -> int:
    return len(line) - len(line.lstrip(" "))


def non_comment_line(line: str) -> bool:
    stripped = line.strip()
    return bool(stripped and not stripped.startswith("#"))


def job_bounds(lines: list[str], call_index: int, call_indent: int) -> tuple[int, int]:
    job_start = 0
    job_indent = 0
    for index in range(call_index - 1, -1, -1):
        line = lines[index]
        if non_comment_line(line) and leading_spaces(line) < call_indent:
            job_start = index
            job_indent = leading_spaces(line)
            break

    job_end = len(lines)
    for index in range(job_start + 1, len(lines)):
        line = lines[index]
        if non_comment_line(line) and leading_spaces(line) <= job_indent:
            job_end = index
            break

    return job_start, job_end


def parse_inline_permissions(value: str) -> dict[str, str]:
    stripped = value.strip()
    if stripped in {"read-all", "write-all"}:
        return {"*": stripped.removesuffix("-all")}
    if not (stripped.startswith("{") and stripped.endswith("}")):
        return {}

    permissions = {}
    for entry in stripped.removeprefix("{").removesuffix("}").split(","):
        key, separator, permission = entry.partition(":")
        if separator:
            permissions[key.strip()] = permission.strip()
    return permissions


def parse_permissions(lines: list[str], call_index: int, call_indent: int) -> dict[str, str]:
    job_start, job_end = job_bounds(lines, call_index, call_indent)
    for index in range(job_start + 1, job_end):
        match = KEY_VALUE.match(lines[index])
        if not match or len(match.group("indent")) != call_indent or match.group("key") != "permissions":
            continue

        value = match.group("value")
        if value:
            return parse_inline_permissions(value)

        permissions = {}
        for permission_line in lines[index + 1:job_end]:
            if not non_comment_line(permission_line):
                continue
            if leading_spaces(permission_line) <= call_indent:
                break
            permission_match = KEY_VALUE.match(permission_line)
            if permission_match:
                permissions[permission_match.group("key")] = permission_match.group("value").strip()
        return permissions

    return {}


def grants_required_permissions(permissions: dict[str, str]) -> bool:
    default_permission = permissions.get("*")
    return all(
            permissions.get(permission, default_permission) in allowed_values
            for permission, allowed_values in REQUIRED_PERMISSIONS.items()
    )


class IntegrationWorkflowPermissionsTest(unittest.TestCase):

    def test_it_template_callers_grant_reusable_workflow_permissions(self) -> None:
        missing_permissions: list[str] = []
        for workflow in workflow_paths():
            lines = workflow.read_text(encoding="utf-8").splitlines()
            for index, line in enumerate(lines):
                call = IT_TEMPLATE_CALL.match(line)
                if call and not grants_required_permissions(
                        parse_permissions(lines, index, len(call.group("indent")))
                ):
                    missing_permissions.append(workflow.name)

        self.assertEqual([], missing_permissions)


if __name__ == "__main__":
    unittest.main()
