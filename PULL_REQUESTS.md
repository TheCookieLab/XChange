# XChange Pull Request Overrides

These repo-local overrides target the global PR rule IDs for this repository.

All activity must stay within my fork (`origin`). Never open pull requests to any upstream remote unless I explicitly request it. When a branch is pushed for PR work, push it to `origin`, and open the pull request with both base and head in `origin`.

Override PR.RULE.NEW_REMOTE_BRANCH_PUSH:
Allow first pushes that create non-default remote feature branches after required local verification passes.

Override PR.RULE.DEFAULT_BRANCH_MUTATION:
Allow pull requests that target `origin/main`. Do not open or merge pull requests against any upstream default branch unless I explicitly request that action. Direct pushes to `main` remain disallowed unless the user explicitly requests that action.
