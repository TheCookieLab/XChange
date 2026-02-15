# XChange Subagent Contracts

This directory defines machine-readable contracts for the XChange module manager/worker flow.

## Files

- `run-manifest.schema.json`: manager run state and resume model
- `worker-result.schema.json`: worker completion payload
- `unresolved-issue.schema.json`: unresolved issue record schema for rollups

## Usage

- Manager writes/maintains run manifests and telemetry under `manager-runs/<run_id>/`.
- Workers emit `worker-result.json` in their worktree.
- Workers emit unresolved issues in `unresolved.json` (matching unresolved schema) and `unresolved.md` (human-readable).
