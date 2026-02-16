#!/usr/bin/env bash
# Remove all XChange worker worktrees (manager-runs/*/worktrees/*).
# Run from XChange repo root.
set -e
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"
count=0
while IFS= read -r path; do
  [[ -z "$path" ]] && continue
  if git worktree list --porcelain | grep -q "worktree $path"; then
    git worktree remove --force "$path" 2>/dev/null || true
    ((count++)) || true
  fi
done < <(git worktree list --porcelain | awk '/^worktree /{p=$2} p && index(p,"manager-runs")>0{print p; p=""}')
git worktree prune -v
echo "Removed $count worktrees; pruned."
