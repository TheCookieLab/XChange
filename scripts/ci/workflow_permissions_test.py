#!/usr/bin/env python3

from __future__ import annotations

import re
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
WORKFLOW_DIR = REPO_ROOT / ".github" / "workflows"
IT_TEMPLATE_CALL = "uses: ./.github/workflows/it-template.yaml"
REQUIRED_CALLER_PERMISSIONS = re.compile(
        r"(?m)^    permissions:\n"
        r"^      actions: read\n"
        r"^      contents: read\n"
        r"^    uses: \./\.github/workflows/it-template\.yaml$"
)


class IntegrationWorkflowPermissionsTest(unittest.TestCase):

    def test_it_template_callers_grant_actions_read_permission(self) -> None:
        missing_permissions: list[str] = []
        for workflow in sorted(WORKFLOW_DIR.glob("*.yaml")):
            text = workflow.read_text(encoding="utf-8")
            if IT_TEMPLATE_CALL not in text:
                continue
            if REQUIRED_CALLER_PERMISSIONS.search(text) is None:
                missing_permissions.append(workflow.name)

        self.assertEqual([], missing_permissions)


if __name__ == "__main__":
    unittest.main()
