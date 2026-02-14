# XChange Static Analysis Profiles

This directory contains analyzer definitions tailored for XChange.

## PMD

- Ruleset: `pmd/xchange-ruleset.xml`
- Default module exclusions: `pmd/excluded-modules.txt`

Run via:

```bash
scripts/pmd-check
```

## SpotBugs

- Inclusion filter: `spotbugs/include-filter.xml`
- Exclusion filter: `spotbugs/exclude-filter.xml`

The root `pom.xml` points SpotBugs to these filter files.
