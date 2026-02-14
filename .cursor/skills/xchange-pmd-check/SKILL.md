---
name: xchange-pmd-check
description: Run PMD static analysis for XChange using scripts/pmd-check (all modules by default, or selected/changed modules) and produce remediation-ready reports.
---

# XChange PMD Check

Use this skill when the user asks to run PMD, gather PMD warnings, or remediate PMD findings in XChange.

## What it runs

The canonical entrypoint is:

```bash
scripts/pmd-check
```

The script automatically:
- Discovers modules from `pom.xml`
- Uses the XChange PMD ruleset at `config/static-analysis/pmd/xchange-ruleset.xml`
- Writes reports under `target/pmd-reports/`
- Excludes modules listed in `config/static-analysis/pmd/excluded-modules.txt` unless explicitly requested

## Common usage

Full project (default):

```bash
scripts/pmd-check
```

Specific modules:

```bash
scripts/pmd-check xchange-core xchange-coinbase
```

Short module aliases (when unique):

```bash
scripts/pmd-check coinbase
```

Only modules changed vs `origin/main`:

```bash
scripts/pmd-check --changed
```

Do not fail shell exit when violations exist (useful for harvesting):

```bash
scripts/pmd-check --no-fail-on-violation
```

Write a deterministic report path:

```bash
scripts/pmd-check xchange-core --format json --report-file target/pmd-reports/xchange-core.json
```

List valid modules:

```bash
scripts/pmd-check --list-modules
```

## Agent guidance

- Prefer `scripts/pmd-check --no-fail-on-violation --report-file <path>` when collecting a remediation queue.
- For module-specific work, run PMD only on the assigned artifactId first.
- Re-run PMD after each fix cycle so the report file reflects current remaining findings.
