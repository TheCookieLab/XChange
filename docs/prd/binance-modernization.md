# Binance module modernization

* **Status:** In delivery (PRD draft v1 — Linear CF-443)
* **Priority:** High
* **Repository:** `TheCookieLab/XChange`
* **Affected modules:** `xchange-binance`, `xchange-stream-binance`
* **Activity rank:** #1 in CoinGecko's 2026 Q2 centralized spot-exchange volume table
* **Linear issue:** [CF-443](https://linear.app/cookiefactory/issue/CF-443/xchange-modernize-binance-spot-margin-futures-and-portfolio-margin)
* **Linear PRD source:** [PRD: CF-443 — Binance module modernization](https://linear.app/cookiefactory/document/prd-cf-443-binance-module-modernization-98aa8c8f14f9)

## Summary

Modernize XChange's Binance integration into an explicit, versioned, product-aware family that covers the current Binance API surface without preserving the present monolithic coupling between Spot, SAPI/Wallet, Margin, USDⓈ-M Futures, COIN-M Futures, Options, and Portfolio Margin.

The implementation must keep the generic XChange APIs simple while preserving Binance-specific semantics through typed raw services where the generic contracts would lose information. It must adopt the strongest patterns established by Coinbase Advanced Trade v3 and Coinbase Derivatives: precise wire DTOs, thin raw clients, high-level adapters, instrument discovery, exact decimal handling, explicit replay-safety rules, structured and redacted failures, deterministic protocol fixtures, and reconnect-safe streaming state machines.

## Problem

The existing implementation has useful breadth but its architecture no longer clearly communicates which Binance product, API generation, authentication scheme, endpoint budget, or retry contract a call belongs to. A single exchange class switches among several exchange modes, public and authenticated interfaces mix unrelated product families, and the streaming module coordinates multiple connection mechanisms through nullable services and configuration flags.

This makes it difficult to answer basic production questions:

* Which Binance products and endpoints are supported, partially supported, or unsupported?
* Which key types work for each product and transport?
* Which failures are safe to retry?
* How are client order IDs used to reconcile an unknown placement outcome?
* Which rate-limit bucket did a call consume, and when can the caller safely resume?
* How does an order book recover after a sequence discontinuity or reconnect?
* Can a caller run Spot and derivatives clients without accidental cross-product configuration?

## Goals

1. Establish explicit package/client boundaries for current Binance product families.
2. Provide complete, typed Spot support and deliberate coverage for Margin, Wallet/SAPI, USDⓈ-M Futures, COIN-M Futures, Options, and Portfolio Margin.
3. Make authentication, timestamp precision, request signing, rate limiting, retry classification, and redaction first-class contracts.
4. Prevent blind replay of non-idempotent order placement after an ambiguous result.
5. Make REST pagination and streaming recovery deterministic and testable.
6. Preserve source compatibility where practical and provide an intentional deprecation path where it is not.
7. Publish a capability matrix that accurately describes generic and raw-service support.

## Non-goals

* Hiding every Binance-specific product behind new XChange core abstractions.
* Treating all Binance products as interchangeable `CurrencyPair` markets.
* Automatically retrying order placement merely because a client order ID was supplied.
* Supporting undocumented or region-restricted endpoints without a stable official contract.
* Rewriting unrelated XChange core or streaming infrastructure unless a narrowly reusable primitive is required.

## Current-state audit

### REST

* `BinanceExchange` selects Spot, Futures, Inverse Futures, and Portfolio Margin through exchange-specific parameters and URL branching.
* Public Spot endpoints are concentrated in `Binance`; the authenticated interface mixes Spot, Wallet/SAPI, account, transfer, and derivatives concerns.
* Endpoint documentation and rate-limit assumptions are inconsistent and partly static.
* Product metadata initialization is not expressed as one authoritative catalog per product family.
* Request timestamp precision, receive-window behavior, key algorithm support, and server-time drift need a single documented policy.

### Streaming

* Market streams, listen-key user data, and authenticated WebSocket API sessions coexist in one exchange implementation.
* Service existence depends on exchange mode, credentials, and key type; lifecycle and liveness checks are not consistently null-safe.
* The module needs explicit generation-scoped request correlation, complete resubscription coverage, and deterministic order-book snapshot/delta recovery.
* The public API does not clearly distinguish a recoverable reconnect from an unrecoverable data gap.

## Proposed architecture

### Product-family boundaries

Retain the published Maven artifacts, but introduce versioned internal/public packages with narrow clients:

* `binance.spot.v3`
* `binance.wallet` / `binance.sapi`
* `binance.margin`
* `binance.usdm`
* `binance.coinm`
* `binance.options`
* `binance.portfoliomargin`

The exact naming may be refined during implementation, but product ownership must be explicit. A compatibility facade may continue to expose the existing `BinanceExchange` modes while delegating to the new clients.

Each family should follow the same layering:

1. JAX-RS wire interface.
2. Authentication/signing and endpoint policy.
3. Immutable request/response DTOs.
4. Thin raw service.
5. High-level XChange service/adapters.
6. Exchange-specific raw contracts for unsupported generic semantics.

### Typed configuration

Replace new uses of magic strings and loosely typed values with documented constants and typed configuration accessors for:

* Product family and account mode.
* Sandbox/testnet environment.
* Key algorithm.
* Timestamp unit and receive window.
* REST and WebSocket endpoint overrides.
* Optional SBE/raw-market-data mode.
* Order-book depth/update cadence.

Invalid combinations must fail during specification application with an actionable message.

## Functional requirements

### Instrument and metadata discovery

1. Discover complete product catalogs separately for Spot, Margin-enabled symbols, USDⓈ-M, COIN-M, Options, and Portfolio Margin as supported.
2. Preserve exchange symbol, contract type, expiry, settlement asset, tick/step sizes, min/max order values, leverage/risk filters, trading status, and permissions.
3. Use `FuturesContract`, option instruments, or exchange-specific metadata rather than collapsing derivatives into ambiguous currency pairs.
4. Detect incomplete paginated discovery and fail rather than silently publishing a partial catalog.
5. Populate accurate fee information when authenticated endpoints permit it; clearly mark unavailable values.

### Market data

Support and test, per applicable product family:

* Ticker and best bid/ask.
* Full order-book snapshot.
* Recent, aggregate, and historical trades.
* Candles/klines with typed intervals and time ranges.
* Mark, index, funding, open-interest, and risk data for derivatives.
* Current exchange status and server time.

Raw APIs may expose exchange-specific fields not representable by XChange DTOs.

### Account and wallet

Provide typed support for:

* Balances and account configuration.
* Margin balances, liabilities, interest, collateral, and transfers.
* Deposit/withdrawal addresses, networks, fees, status, and histories.
* Futures/portfolio balances, positions, margin mode, leverage, and risk.
* Subaccount and internal-transfer operations that have stable official contracts.

Secrets, authorization material, withdrawal addresses, signed payloads, and private response data must be redacted from logs and sanitized exceptions.

### Trading

1. Support current market, limit, stop/trigger, trailing, iceberg, post-only, time-in-force, reduce-only, close-position, and product-specific order semantics where officially available.
2. Support query, open/history, cancel, cancel-all, amend/cancel-replace, and batch operations by exchange ID and client order ID.
3. Map `Order.userReference` to a documented Binance client-order identity field without claiming uniqueness or idempotency beyond Binance's contract.
4. Add exchange-specific placement/amend results when the generic return type would lose response mode, related IDs, prevented quantity, self-trade prevention, or reconciliation metadata.
5. Classify placement as **non-replayable after an ambiguous transport result**. The library may perform a bounded reconciliation query by client order ID; it must not blindly resubmit.
6. Reads and cancellations may retry only when their provider semantics and request identity make replay safe.

### Authentication and time

* Support the current official key algorithms by product and transport, with deterministic signature fixtures.
* Centralize server-time synchronization, receive-window validation, millisecond/microsecond timestamp selection, and drift diagnostics.
* Reject unsupported key/product combinations before the first network call.
* Never log secret material, private keys, signatures, authorization headers, or complete signed query strings.

### Rate limiting and errors

* Introduce a Binance endpoint-policy registry describing product family, operation class, weight/order-count dimensions, and retry safety.
* Parse response headers into exchange-specific rate-limit telemetry available to raw callers and resilience policies.
* Respect `Retry-After` and ban/limit responses; use bounded jittered backoff for replay-safe operations.
* Map Binance error payloads into structured exceptions carrying product, endpoint, code, retry classification, request/client order identity, and sanitized details.
* Distinguish provider rejection, authentication failure, rate exhaustion, transient transport failure, malformed response, and unknown execution state.

## Streaming requirements

1. Separate public market streams, legacy/listen-key user streams where required, and authenticated WebSocket API/trading sessions into explicit transports.
2. Give every socket a connection-generation identifier; pending request correlation must include generation plus request ID.
3. Authenticate or create/refresh listen keys with single-flight lifecycle control.
4. Handle protocol ping/pong and provider connection-duration limits.
5. Reconnect with bounded backoff, reauthenticate, and resubscribe every applicable public/private/trading channel.
6. Deduplicate replayed private events by stable exchange identifiers.
7. For order books, obtain a REST snapshot, buffer deltas, apply documented sequence rules, and surface a dedicated gap exception when continuity cannot be proved.
8. Do not silently continue from unknown state. Consumers must receive a terminal signal and a fresh recovered stream.
9. Make `connect`, `disconnect`, `isAlive`, connection observables, and service access null-safe for every credential/product combination.
10. Classify WebSocket order operations with the same replay-safety rules as REST.

## Compatibility and migration

* Keep existing artifacts and primary exchange class loadable during a documented grace period.
* Mark ambiguous or obsolete exchange-specific parameters deprecated and provide typed replacements.
* Preserve generic behavior where correct; document intentional changes to instrument identity, metadata completeness, failure classification, and order placement.
* Add migration examples for Spot-only, Futures, Portfolio Margin, HMAC, RSA, and Ed25519 configurations.
* Do not change XChange core solely to mirror a Binance-specific field; use raw contracts unless a reusable abstraction is demonstrated across exchanges.

## Documentation

Add module READMEs and an `AGENTS.md`/maintainer guide covering:

* Capability matrix by product and transport.
* Configuration and supported key algorithms.
* Sandbox/testnet limitations.
* Instrument mapping.
* Client order ID and ambiguous-placement recovery.
* Rate-limit telemetry.
* Streaming sequencing, reconnect, and gap behavior.
* Examples for generic and raw APIs.

## Test plan

### Deterministic tests

* Signature vectors for all supported key algorithms and timestamp units.
* Exact `BigDecimal` parsing/serialization, including small quantities and scientific notation where accepted.
* DTO fixtures for every supported endpoint family and representative error shape.
* Instrument/filter mapping for Spot, linear, inverse, and options products.
* Pagination completeness and loop termination.
* Order request mapping, response modes, client IDs, amend/cancel-replace, batch results, and self-trade prevention.
* Unknown placement outcome: prove no blind replay and successful/unsuccessful reconciliation branches.
* Rate-limit header parsing and retry classification.
* Secret redaction.
* WebSocket generation correlation, auth refresh, reconnect, resubscription, event deduplication, and stale-response rejection.
* Snapshot-plus-delta order-book recovery and explicit gap termination.

### Environment tests

* Spot testnet/sandbox smoke tests for stable public and authenticated endpoints.
* Applicable derivatives testnet smokes.
* Credential-gated production read-only tests with robust assertions.
* Minimum-size trading canaries must remain opt-in and excluded from default CI.

### Build gates

* Affected-module unit/integration tests.
* PMD and formatting checks for production and tests.
* `mvn -B -pl xchange-binance,xchange-stream-binance -am test`.
* Repository-root `mvn -B clean install` before handoff.
* Non-regression tests for consumers using existing Spot and Futures configurations.

## Delivery slices

1. Capability matrix, package design, typed configuration, and deprecation plan.
2. Shared authentication/time/error/rate-limit foundations.
3. Spot REST parity and deterministic fixtures.
4. Wallet and Margin APIs.
5. USDⓈ-M and COIN-M REST parity.
6. Options and Portfolio Margin coverage.
7. Public market-stream recovery.
8. Private/trading WebSocket lifecycle and replay safety.
9. Compatibility facade, examples, documentation, and full validation.

Each slice must remain independently reviewable and keep the reactor green.

## Acceptance criteria

1. Every supported endpoint is owned by one explicit product/API family.
2. The published capability matrix matches implemented generic and raw APIs.
3. Instrument discovery is complete, typed, and tested for each supported family.
4. Supported key algorithms, timestamp precision, and receive-window behavior are deterministic and documented.
5. Rate-limit metadata and structured retry classifications are available without leaking secrets.
6. No ambiguous order placement is blindly replayed; reconciliation uses stable provider identity.
7. Streaming reconnects, reauthenticates, resubscribes, rejects stale responses, and exposes sequence gaps.
8. Existing supported configurations have a tested compatibility or migration path.
9. Deterministic fixtures cover success, error, numeric, lifecycle, and recovery boundaries.
10. Targeted and repository-root build gates pass.

## References

* XChange Binance modules: `xchange-binance`, `xchange-stream-binance`
* Coinbase Advanced Trade v3 implementation and maintainer guide
* Coinbase Derivatives PRD and REST/streaming recovery conventions
* Official Binance developer documentation: https://developers.binance.com/docs

---

## Delivery checklist (local tracking copy)

- [x] **S1 — Capability matrix, package design, typed configuration, deprecation plan** — DONE:
  - `config/` package: `BinanceProductFamily`, `BinanceKeyAlgorithm`, `BinanceTimestampUnit`, `BinanceConfiguration` (typed params, validation, legacy fallbacks).
  - `BinanceExchange` typed accessors; legacy `EXCHANGE_TYPE`, `isFuturesEnabled()`, `isSpotEnabled()`, `isPortfolioMarginEnabled()` deprecated.
  - `xchange-binance/README.md` capability matrix; design doc = this file.
- [x] **S2 — Shared authentication/time/error/rate-limit foundations** — DONE:
  - `auth/`: `BinanceSigning` (canonical payload + digest factory), `BinanceRsaDigest` (new RSA support), HMAC/Ed25519 digests deduplicated onto the shared payload.
  - `time/`: `BinanceTimePolicy` (ms/µs, recvWindow validation); `BinanceTimestampFactory` unit-aware.
  - `error/`: `BinanceRetryClassification`, `BinanceErrorClassifier` (~60 codes + HTTP status), `BinanceStructuredException`, `BinanceRedaction`.
  - `ratelimit/`: `BinanceEndpointPolicy` + `BinanceEndpointPolicies` registry, `BinanceRateLimitTelemetry` (header parsing).
- [ ] **S3 — Spot REST parity and deterministic fixtures** — PARTIAL (family split + proxy migration done; margin/options parity, extra fixtures pending):
  - Family wire interfaces: `spot/BinanceSpotApi`, `spot/BinanceSpotAuthApi`, `wallet/BinanceWalletApi`, `usdm/BinanceUsdmApi`, `usdm/BinanceUsdmAuthApi`, `coinm/BinanceCoinmAuthApi`, `portfoliomargin/BinancePortfolioMarginApi`.
  - Legacy `Binance`, `BinanceAuthenticated`, `BinanceFutures`, `BinanceFuturesAuthenticated` reduced to deprecated facades.
  - `BinanceBaseService` builds one narrow proxy per family; `BinanceMarketDataServiceRaw`, `BinanceAccountServiceRaw`, `BinanceTradeServiceRaw` migrated off the wide facades.
  - Fixed trailing-space bug in `papi/v1/um/openOrders` path.
- [ ] **S4 — Wallet and Margin APIs** — PARTIAL: wallet wire surface owned (`BinanceWalletApi`); margin family has no wire surface yet (capability matrix marks it).
- [ ] **S5 — USDⓈ-M and COIN-M REST parity** — PARTIAL: USDM public+auth owned (`BinanceUsdmApi`/`BinanceUsdmAuthApi`); COINM auth owned (`BinanceCoinmAuthApi`); COINM public market data not implemented.
- [ ] **S6 — Options and Portfolio Margin coverage** — PARTIAL: Portfolio Margin owned (`BinancePortfolioMarginApi`); Options family enum present, selection fails fast (not implemented).
- [ ] **S7 — Public market-stream recovery** — PARTIAL: snapshot+delta recovery exists and is now documented (spot + futures state machines, resync on sequence breaks, rate-limit surfaced); stream README covers reconnect/resubscribe/order-book recovery.
- [ ] **S8 — Private/trading WebSocket lifecycle and replay safety** — PARTIAL: fixed `isAlive()` NPE for every credential/product combination; connection-state observables null-safe (regression test `BinanceStreamingExchangeLifecycleTest`); WS API trading service: monotonic request ids (no wall-clock ids), persistent re-login listener so `authorized` re-arms after reconnect, connection-generation counter, login disposed on manual disconnect; listen-key lifecycle already rotates + reconnects; `BinanceUserDataChannel` migrated to family clients.
- [ ] **S9 — Compatibility facade, examples, documentation, full validation** — PARTIAL: facades + README capability matrix + migration examples + `xchange-binance/AGENTS.md` maintainer guide done; stream README lifecycle/recovery docs done; root-repo `clean install` final gate deferred to qa-and-ship.
  - PMD: `xchange-binance` clean; `xchange-stream-binance` has 41 pre-existing violations (all in files untouched by this PRD; fixing them is a separate warning-cleanup pass).

### Verification ledger

| Slice | Check | Result | Evidence |
|---|---|---|---|
| S1 | `BinanceConfigurationTest` (9 tests: defaults, family URLs, sandbox, overrides, rejections) | ✅ | `mvn -B -pl xchange-binance -am test` — 74 tests, 0 failures |
| S2 | `BinanceRsaDigestTest` (OpenSSL vector), `BinanceSigningTest` (HMAC/Ed25519 vectors, payload assembly), `BinanceErrorClassifierTest`, `BinanceRedactionTest`, `BinanceRateLimitTelemetryTest`, `BinanceTimePolicyTest` | ✅ | same build |
| S3 | Family-split refactor compiles; all pre-existing WireMock/resilience tests green on family proxies | ✅ | same build |
| S8 | `BinanceStreamingExchangeLifecycleTest` (4 tests: isAlive null-safety, observables empty, disconnect before connect) | ✅ | `mvn -B -pl xchange-stream-binance -am test` — 20 tests, 0 failures |
| S7/S8 | Stream module + upstream reactor green after fixes | ✅ | same build |
| — | Full reactor spot build | ✅ | parent + core + binance SUCCESS |
