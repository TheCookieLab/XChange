# Unresolved items

## 2026-08-08 — xchange-uniswap delivery (CF-374)

1. **Unfunded node smoke and funded mainnet canary** — blocked on the hardened
   Geth/Prysm node (loopback-only APIs, firewall audit, freezer errors
   resolved), wallet funding, and separate canary approval. Runbook in
   `xchange-uniswap/README.md`; tracked as Linear CF-436. Code hashes for
   `uniswap.deployments.codeHashes` must be pinned from the node or the
   official Uniswap deployment registry before any funded use.
## 2026-08-29 — xchange-parent-1.0.1 release workflow re-publish

1. **Perform can never be green for 1.0.1 — do not re-dispatch.** 1.0.1
   components are already published on Maven Central (release run
   33213721880, 2026-08-28); Central rejects re-publishing existing components
   ("Component with package url ... already exists"), so run 33221842148's
   Perform failure is permanent and expected. Release is complete on Central,
   built from commit `e81ab59bae` (pre-fix). Both connection-lifecycle race
   fixes (96 ms `isAlive` flake, double-DELETE in
   `disconnectCancelsAnInFlightPrivateConnectionAttempt`) are on main
   (`143650df16`, 1.0.2-SNAPSHOT) and ship with the 1.0.2 release. Tag
   `xchange-parent-1.0.1` pinned to `e81ab59bae` to match Central artifacts;
   GitHub release published 2026-08-29.
