# XChange Binance — Maintainer Guide

Operational notes for the Binance REST (`xchange-binance`) and streaming
(`xchange-stream-binance`) modules. Follow the root `AGENTS.md` first; this file adds
Binance-specific rules.

## Architecture invariants

1. **Endpoint ownership is per product family.** Every wire endpoint is declared exactly once,
   in the family interface that owns it:
   - `org.knowm.xchange.binance.spot.BinanceSpotApi` — public Spot (`/api/v3/*`)
   - `org.knowm.xchange.binance.spot.BinanceSpotAuthApi` — authenticated Spot (`/api/v3/*`)
   - `org.knowm.xchange.binance.wallet.BinanceWalletApi` — Wallet/SAPI (`/sapi/v1/*`)
   - `org.knowm.xchange.binance.usdm.BinanceUsdmApi` / `BinanceUsdmAuthApi` — USDⓈ-M (`/fapi/*`)
   - `org.knowm.xchange.binance.coinm.BinanceCoinmAuthApi` — COIN-M (`/dapi/*`)
   - `org.knowm.xchange.binance.portfoliomargin.BinancePortfolioMarginApi` — Portfolio Margin
     (`/papi/*`)
   New endpoints must be added to their family interface, never to a facade.
2. **Facades are deprecated.** `Binance`, `BinanceAuthenticated`, `BinanceFutures`,
   `BinanceFuturesAuthenticated` are compatibility facades for the documented grace period.
   Do not add methods to them; migrate callers to family clients.
3. **One proxy per family.** `BinanceBaseService` builds a narrow REST proxy per family;
   services address the family they belong to. Do not route a Spot call through the futures
   proxy or vice versa.
4. **Typed configuration.** New exchange-specific parameters must go through
   `BinanceConfiguration` constants with typed accessors and validation. Do not introduce new
   magic-string parameters (`"ed25519"`, `"recvWindow"` are legacy, honored during the grace
   period only).

## Authentication and time

- Signing payload assembly lives in `BinanceSigning.signingPayload` — all key algorithms
  (HMAC-SHA256, RSA, Ed25519) sign the same canonical payload. Never fork payload assembly in a
  digest.
- New digest algorithms must ship a deterministic signature vector test with a committed
  OpenSSL-generated key/payload/signature triple (see `BinanceRsaDigestTest`).
- Timestamp unit selection (`ms`/`µs`) and receive-window validation are centralized in
  `BinanceTimePolicy`; use it, don't inline clock math.
- Secrets never reach logs: run user-supplied text through `BinanceRedaction.redact` when it
  may carry keys, signatures, or PEM material.

## Order placement and replay safety

- Placement is **non-replayable**. Never add a retry that blindly resubmits an order after a
  timeout/5xx; recovery is a bounded reconciliation query by client order ID.
- Reads and cancellations may retry; classify new failure paths in `BinanceErrorClassifier`
  and record endpoint weight/order-count/retry policy in `BinanceEndpointPolicies` so the
  capability matrix stays accurate.

## Streaming

- Order-book recovery is snapshot-plus-delta with explicit sequence rules. Changes to the
  state machines in `BinanceStreamingMarketDataService` must keep: dropped `u <= lastUpdateId`
  deltas, first-applied `U <= lastUpdateId + 1 <= u` (spot) / `pu`-chain verification
  (futures), and re-sync instead of applying unverified deltas.
- `isAlive()` and the connection-state observables must stay null-safe for every
  credential/product combination; add a regression test when the set of created services
  changes.
- The WS API trading service re-authenticates on reconnect; keep the login listener persistent
  and the request ids monotonic.

## Validation

- Focused iteration: `mvn -B -pl xchange-binance,xchange-stream-binance -am -Dtest=<Class> test`
- Affected-module gate: `mvn -B -pl xchange-binance,xchange-stream-binance -am test`
- PMD: `scripts/pmd-check xchange-binance xchange-stream-binance`
- Full gate before handoff: repository-root `mvn -B clean install`
- Update `xchange-binance/README.md` capability matrix when endpoint support changes.
