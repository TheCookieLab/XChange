# Unresolved items

## 2026-08-08 — xchange-uniswap delivery (CF-374)

1. **Unfunded node smoke and funded mainnet canary** — blocked on the hardened
   Geth/Prysm node (loopback-only APIs, firewall audit, freezer errors
   resolved), wallet funding, and separate canary approval. Runbook in
   `xchange-uniswap/README.md`; tracked as Linear CF-436. Code hashes for
   `uniswap.deployments.codeHashes` must be pinned from the node or the
   official Uniswap deployment registry before any funded use.
2. **OWASP dependency-check NVD scan** — the plugin requires an NVD API key
   (`nvdApiKey`), which is not configured in this environment; the scan fails
   with "Invalid API Key". The OSV audit of the full resolved compile graph
   (46 packages) reports zero findings as a substitute, but the NVD-based scan
   should be re-run when a key is available.
