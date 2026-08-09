# PRD: CF-452 — XChange: modernize Kraken Spot/Futures REST and WebSocket support

*Project:* XChange
*Source issue:* CF-452
*Author:* Codex (automated)
*Status:* Ready
*Last updated:* 2026-08-09

---

## Status

* Lifecycle: `Ready`
* Blocking state: `None`
- Active phase: `Phase 5 — Futures WS sequence recovery`
- Active task: `task 12 — validate seq continuity, gap detection with snapshot rebuild, private event dedup`
- Overall: `11/26` checklist tasks complete

## Execution Status

* Last updated: `2026-08-09 15:45 UTC`
* Codebase access: `confirmed`
* Repo / module path: `xchange-kraken`, `xchange-krakenfutures`, `xchange-stream-kraken`, `xchange-stream-kraken-v2`, `xchange-stream-krakenfutures` (repo root: XChange)
* Fresh inputs integrated:
  * `CF-452 issue description (2026-08-09T14:23:59Z)`
  * `draft v1 PRD document (created 2026-08-09T14:25:16Z) — restructured to canonical shape in this revision; content carried over and evidence re-verified`
  * `no PRD Answers rounds received to date (issue has no comments)`
* Evidence reviewed:
  * `xchange-kraken/src/main/java/org/knowm/xchange/kraken/KrakenExchange.java:46-47` — "Note: CurrencyPair Metadata will not contain accurate maker/taker fees; Note: Currency Metadata will only contain price scale"
  * `xchange-kraken/src/main/java/org/knowm/xchange/kraken/KrakenAdapters.java:433` — `adaptFeeTiers` maker/taker tier assembly
  * `xchange-kraken/src/main/java/org/knowm/xchange/kraken/KrakenAuthenticated.java:126-152` — order placement with `timeinforce` and `cl_ord_id`
  * `xchange-kraken/src/main/java/org/knowm/xchange/kraken/service/KrakenTradeServiceRaw.java:77,136` and `KrakenAccountServiceRaw.java:282,324` — offset-based (`ofs`) pagination for trades and ledgers
  * `xchange-kraken/src/main/java/org/knowm/xchange/kraken/service/KrakenBaseService.java:75-87` — `checkResult` error-code mapping to typed exceptions (NonceException, FrequencyLimitExceededException, ...)
  * `xchange-kraken/src/main/java/org/knowm/xchange/kraken/service/` — raw/high-level pairs KrakenMarketDataService(+Raw), KrakenAccountService(+Raw), KrakenTradeService(+Raw), KrakenDigest
  * `xchange-kraken/src/main/java/org/knowm/xchange/kraken/dto/account/KrakenEarn*.java` — earn allocation DTOs already present
  * `xchange-stream-kraken-v2/.../KrakenStreamingExchange.java:38-63` — `connect()` constructs and connects the private service unconditionally (`krakenPrivateStreamingService.connect().blockingAwait()`); `disconnect()` disconnects only the public service; `isAlive()` checks only the public socket
  * `xchange-stream-kraken-v2/.../KrakenPrivateStreamingService.java:43-51` — WebSocket token fetched via REST `getWebsocketToken` inside each subscribe path (no single-flight refresh)
  * `xchange-stream-kraken-v2/.../KrakenStreamingMarketDataService.java` — ticker and trades only; no order book, OHLC, or status channels
  * `xchange-stream-kraken-v2/.../dto/common/ChannelType.java` — declared channels: ticker, trade, balances, executions only
  * `xchange-stream-kraken-v2/.../KrakenStreamingService.java:24-27` — extends `NettyStreamingService`; no module-level reconnect/resubscribe/backoff overrides
  * `xchange-stream-kraken/.../KrakenStreamingMarketDataService.java:40-105` and `KrakenStreamingChecksum.java` — legacy v1 order book/ticker/trades with checksum support; no `@Deprecated` markers, no README
  * `xchange-stream-krakenfutures/.../KrakenFuturesStreamingMarketDataService.java:37-80` — book snapshot replace + delta update keyed by instrument; `seq` fields present on snapshot/delta/fills DTOs but never validated (no gap detection); crossed-book IOException only
  * `xchange-krakenfutures/.../service/` — KrakenMarketData/Account/Trade service(+Raw) pairs, KrakenFuturesDigest; `KrakenFuturesTradeServiceRaw.java:145` `sendKrakenFuturesBatchOrder` (batchorder endpoint); `KrakenFuturesBaseService.java:38` `getKrakenFuturesOpenPositions`; `KrakenFuturesMarketDataService.java:42` `getFundingRates`
  * `xchange-krakenfutures/.../dto/` — marketData (instrument(s), order, order book, ticker(s), public fills, order status), account (account/info), trade DTOs
  * no `deadman`/`CancelAllAfter`/`EditOrder` matches in `xchange-kraken` or `xchange-krakenfutures` main sources
  * `xchange-coinbase` (v2/ v3 packages) and `xchange-coinbase-derivatives` modules — convention references for Advanced Trade v3 and Derivatives
  * test surface: unit tests plus Failsafe `*Integration` classes in all five modules (`KrakenExchangeIntegration`, `KrakenMarketDataServiceIntegration`, `KrakenFuturesPrivateDataIntegration`, `KrakenFuturesPublicDataIntegration`, `KrakenStreaming*Integration` for v2 and futures)
* Notes:
  * Kraken ranked #10 by centralized spot-exchange trading volume in CoinGecko 2026 Q2 report (issue statement).
  * Repo build gates: module `mvn -B -pl <modules> -am test`; repository-root `mvn -B clean install`; PMD via `scripts/pmd-check`.
  * No live credentials in this environment; fee and private-endpoint behavior must be validated with credential-gated live smokes in CI.

## Summary

* The Kraken family currently spans five modules (`xchange-kraken`, `xchange-krakenfutures`, `xchange-stream-kraken`, `xchange-stream-kraken-v2`, `xchange-stream-krakenfutures`) with fragmented, partially outdated behavior: Spot REST metadata explicitly disclaims accurate maker/taker fees (`KrakenExchange.java:46`), Spot WS v2 connects a private socket unconditionally and never disconnects it or includes it in `isAlive()`, Futures WS carries `seq` on book/fills DTOs without any gap detection, and neither Spot nor Futures implements dead-man/cancel-all-after, atomic amend, or modern post-trade pagination. The PRD defines one documented family architecture (canonical ownership per protocol, raw/high-level separation, immutable DTOs, typed errors, redaction, bounded pagination, explicit replay safety), a full lifecycle/sequence/checksum recovery model for the streaming modules, and a deliberate deprecation path for legacy v1 streaming — following the conventions already established by the Coinbase Advanced Trade v3 and Coinbase Derivatives modules in this repo. No blocking decisions remain; the non-blocking open questions are sequencing preferences (v1 removal timeline, channel rollout order, fee-endpoint credential gating).

## 1) Context

* XChange is a Java/Maven multi-exchange library; this work touches the Kraken exchange family: Spot REST (`xchange-kraken`), Spot WebSocket v2 (`xchange-stream-kraken-v2`), Futures REST (`xchange-krakenfutures`), Futures WebSocket (`xchange-stream-krakenfutures`), and legacy Spot WebSocket v1 (`xchange-stream-kraken`).
* Kraken ranked #10 by centralized spot-exchange trading volume in CoinGecko's 2026 Q2 market report, so the adapter family is high-traffic and worth production-grade treatment.
* The repo already establishes the target conventions: `xchange-coinbase` (Advanced Trade, with `v2`/`v3` packages) and `xchange-coinbase-derivatives` define canonical versioned domains, exact DTOs, raw/high-level separation, structured errors, and dead-man/replay semantics that this PRD mirrors.
* Constraints: module and root builds gate (`mvn -B -pl <modules> -am test`, root `mvn -B clean install`, PMD via `scripts/pmd-check`); live tests are Failsafe `*Integration` classes run with `-DskipIntegrationTests=false` and must stay out of the default unit-test surface; no live Kraken credentials are available to this environment.
* Existing systems involved: all classes named in `Execution Status -> Evidence reviewed`; streaming modules extend `NettyStreamingService` from `xchange-stream-service-netty`.

## 2) Problem / Opportunity

* Current behavior with evidence:
  * Spot metadata is knowingly inaccurate on fees: `KrakenExchange.java:46-47` — "Note: CurrencyPair Metadata will not contain accurate maker/taker fees; Note: Currency Metadata will only contain price scale". `KrakenAdapters.adaptFeeTiers` (`KrakenAdapters.java:433`) builds tiers from pair-level `fees`/`fees_maker` lists (`KrakenAssetPair.java:48-49`).
  * Spot WS v2 (`KrakenStreamingExchange.connect()`, lines 38-52) constructs and connects the private service unconditionally even for public-only subscriptions; `disconnect()` (lines 51-58) disconnects only the public service; `isAlive()` (lines 61-63) checks only the public socket. Private auth is wired per-subscribe via REST token fetch (`KrakenPrivateStreamingService.java:43-51`) with no single-flight refresh or lifecycle correlation.
  * Spot WS v2 channels are thin: `ChannelType` declares only ticker, trade, balances, executions; the market data service exposes ticker and trades only — no order book, OHLC, status, or order channel, and no checksum/sequence recovery (legacy v1 actually has `KrakenStreamingChecksum`).
  * Futures WS (`KrakenFuturesStreamingMarketDataService.java:37-80`) replaces the book on `book_snapshot`, applies deltas with no `seq` validation even though snapshot/delta/fills DTOs carry `seq`, and only raises on a crossed book.
  * Spot and Futures REST both lack atomic amend, dead-man/cancel-all-after, and (Spot) batch operations — no `EditOrder`, `CancelAllAfter`, or `deadman` matches in either module. Spot does support `cl_ord_id` and `timeinforce` (`KrakenAuthenticated.java:126-152`) and offset pagination (`KrakenTradeServiceRaw.java:77,136`, `KrakenAccountServiceRaw.java:282,324`). Futures supports batch orders (`KrakenFuturesTradeServiceRaw.sendKrakenFuturesBatchOrder`, line 145) and open positions (`KrakenFuturesBaseService.getKrakenFuturesOpenPositions`, line 38).
  * Legacy v1 streaming (`xchange-stream-kraken`) has no `@Deprecated` markers and no README, so there is no visible migration path to v2.
* Pain points: fee-blind spot metadata misleads integrators; private WS state is opaque (connect/disconnect/alive semantics diverge from reality); Futures books can silently corrupt on dropped updates; order workflows cannot express amend, dead-man, or modern post-trade semantics; v1/v2 coexistence is undocumented.
* Why now: 2026 Q2 #10 spot volume ranking; the Coinbase family already demonstrates the target architecture in-repo; the fragmented state is the last major exchange family without a documented architecture.
* What becomes possible: one coherent, documented Kraken family with correct metadata, safe streaming, explicit replay/dead-man controls, and a tested migration path off legacy v1.

## 3) Goals

* G1. Establish canonical ownership and package/version boundaries for Spot REST v0, Spot WebSocket v2, Futures REST, and Futures WebSocket; document the family architecture.
* G2. Deprecate legacy Spot WebSocket v1 (`xchange-stream-kraken`) with a tested migration path to v2.
* G3. Make instrument, currency, trading-rule, and fee metadata authoritative and non-misleading (remove the `KrakenExchange.java:46` inaccuracy caveat).
* G4. Add current Spot order capabilities: atomic amend, batch operations where supported, client-order identity consistency, cancel-all-after/dead-man, and modern post-trade history.
* G5. Complete Futures REST: instruments, positions, funding, order/trade history, and explicit dead-man semantics.
* G6. Fix Spot WS v2 lifecycle: conditional private socket, full dual-socket disconnect/alive semantics, generation-aware reconnect/resubscribe, and sequence/checksum recovery.
* G7. Add explicit sequence gap detection and snapshot rebuild for Futures WS books and other sequenced channels.
* G8. Implement structured authentication, errors, pagination, rate limits, redaction, and replay safety across the family; publish one capability matrix and maintainers' guide.

## 4) Non-goals

* NG1. Merging Spot and Futures wire protocols into one client.
* NG2. Rewriting stable Spot v0 endpoints solely because the path version is old.
* NG3. Keeping two equivalent Spot streaming implementations indefinitely.
* NG4. Enabling dead-man or cancel-on-disconnect behavior implicitly.
* NG5. Blindly retrying order placement after an ambiguous result.
* NG6. Forcing Kraken-specific order, post-trade, or risk fields into XChange core without cross-exchange evidence.
* NG7. Adding live-trading or withdrawal actions to default CI (canaries only, opt-in).

## 5) Users & Use cases

### Current state / evidence

* Spot REST is a legacy form-encoded API v0 interface (`Kraken.java`, `KrakenAuthenticated.java`) with raw/high-level service pairs in `service/` and DTOs split into `dto/{account,marketdata,trade}`; earn DTOs already exist under `dto/account/KrakenEarn*`.
* Spot WS v2 (`xchange-stream-kraken-v2`, package `info.bitrich.xchangestream.kraken`) has one public `KrakenStreamingService` and one private `KrakenPrivateStreamingService`, both extending `NettyStreamingService`; services are constructed unconditionally in `KrakenStreamingExchange.connect()`, private auth is REST-token-based, and `disconnect()`/`isAlive()` only touch the public service.
* Legacy v1 (`xchange-stream-kraken`) exposes order book, ticker, and trades with `KrakenStreamingChecksum`; no deprecation markers or migration docs exist.
* Futures REST (`xchange-krakenfutures`, package `org.knowm.xchange.krakenfutures`) has account/marketdata/trade service pairs with `KrakenFuturesDigest` auth; batch orders, open positions, and funding rates (adapted from tickers) exist; no amend, no dead-man, no positions-history or account-log support beyond open positions.
* Futures WS (`xchange-stream-krakenfutures`) has market data (book/ticker/trades/funding) and trade services; book snapshot/delta DTOs carry `seq` but the service never validates it; no gap rebuild.
* Tests: unit tests plus Failsafe `*Integration` classes in all five modules (listed under Evidence reviewed). Module poms follow the standard XChange reactor layout; the canonical module list is the root `pom.xml`.

### Primary user

* XChange integrators building trading or market-data systems on Kraken Spot and Futures: they need correct metadata/fees, safe streaming state, explicit replay/dead-man control, and a documented path from legacy v1 to v2.

### Key use cases

* UC1. Discover authoritative Spot/Futures instruments, trading rules, and fees without parsing symbols or trusting stale metadata.
* UC2. Place, validate, amend, batch, and cancel orders with client-order identity; recover safely from ambiguous outcomes.
* UC3. Run Spot WS v2 with public-only or private subscriptions and observe correct lifecycle state (alive, reconnected, resubscribed, generation-correct).
* UC4. Consume Futures and Spot books with explicit sequence/checksum gap detection and automatic snapshot rebuild.
* UC5. Enable dead-man/cancel-all-after deliberately, with visible health and fail-safe behavior.
* UC6. Migrate from legacy `xchange-stream-kraken` to v2 with documented channel parity and examples.

### Existing touchpoints

* `xchange-kraken/.../KrakenExchange.java:46-49` — fee/scale metadata caveat to remove; metadata init to harden
* `xchange-kraken/.../KrakenAdapters.java:433` — `adaptFeeTiers` tier assembly to make accurate
* `xchange-kraken/.../KrakenAuthenticated.java:126-152` — order placement with `cl_ord_id`/`timeinforce`; add amend/batch/dead-man endpoints
* `xchange-kraken/.../service/KrakenTradeServiceRaw.java:77,136`, `KrakenAccountServiceRaw.java:282,324` — offset pagination to extend
* `xchange-kraken/.../service/KrakenBaseService.java:75-87` — `checkResult` error mapping to extend
* `xchange-stream-kraken-v2/.../KrakenStreamingExchange.java:38-63` — `connect`/`disconnect`/`isAlive` to fix
* `xchange-stream-kraken-v2/.../KrakenPrivateStreamingService.java:43-51` — token acquisition to single-flight
* `xchange-stream-kraken-v2/.../KrakenStreamingMarketDataService.java` + `dto/common/ChannelType.java` — channels to extend
* `xchange-stream-krakenfutures/.../KrakenFuturesStreamingMarketDataService.java:37-80` — seq validation/gap rebuild to add
* `xchange-krakenfutures/.../KrakenFuturesTradeServiceRaw.java:145` — batch order to preserve/extend
* `xchange-krakenfutures/.../KrakenFuturesBaseService.java:38` — open positions to extend into full positions/history
* `xchange-stream-kraken/.../KrakenStreamingMarketDataService.java:40-105`, `KrakenStreamingChecksum.java` — v1 to deprecate
* test touchpoints: `KrakenExchangeIntegration`, `KrakenMarketDataServiceIntegration`, `KrakenFuturesPrivateDataIntegration`, `KrakenFuturesPublicDataIntegration`, `KrakenStreaming*Integration` (v2/futures), plus unit tests per module

## 6) Proposed solution

### Summary

* Keep the five artifacts, give each protocol a canonical boundary and one shared set of XChange design rules, and close the capability gaps: accurate metadata/fees, typed order workflows (amend/batch/dead-man/client-ID), bounded pagination, structured errors/redaction, generation-aware streaming with sequence/checksum recovery, and a documented v1-to-v2 migration. Spot REST stays canonical for Spot REST; `xchange-stream-kraken-v2` becomes canonical Spot streaming; `xchange-krakenfutures` + `xchange-stream-krakenfutures` remain canonical Futures.

### Fixed decisions

* D1. Spot REST v0 (`xchange-kraken`) remains the canonical Spot REST artifact; internal packages are reorganized (without breaking public compatibility) into `publicdata`, `account`, `trade`, `funding`, `earn`, `posttrade`.
* D2. `xchange-stream-kraken-v2` is the canonical Spot streaming artifact; `xchange-stream-kraken` is deprecated with a tested migration path and removed only after the repository's compatibility grace period and migration evidence.
* D3. Futures REST/WS stay in `xchange-krakenfutures`/`xchange-stream-krakenfutures` with aligned instrument identity but no shared wire DTOs with Spot.
* D4. Every protocol/domain gets the layered shape: wire interface/transport → immutable endpoint DTOs → auth/endpoint-policy/structured errors → thin raw service → high-level adapters → exchange-specific results for lossless behavior.
* D5. Dead-man/cancel-all-after is disabled by default, opt-in via typed configuration, refreshed only while the exchange lifecycle is healthy, and fail safe on refresh/auth/rate-limit failure.
* D6. After an ambiguous transport result, placement/amend is never blindly replayed; reconcile by client/exchange identity and throw a structured unknown-outcome exception if proof is unavailable.
* D7. `isAlive()` is true only when all required sockets are open and private auth is complete; disconnect clears every service idempotently.

### Implementation touchpoints

* `xchange-kraken/.../KrakenExchange.java:46-49` — replace the "will not contain accurate maker/taker fees" caveat with authoritative metadata loading (fee schedules where credentials/endpoints permit; explicit failure when a catalog is incomplete or ambiguous).
* `xchange-kraken/.../KrakenAdapters.java:433` — rework `adaptFeeTiers` to current fee schedule data and exact decimals; keep tier invariants.
* `xchange-kraken/.../KrakenAuthenticated.java` — add current endpoint bindings: atomic amend, batch operations, cancel-all-after/dead-man, modern post-trade/history endpoints; keep nonce/signature (`KrakenDigest`) separate and deterministic.
* `xchange-kraken/.../service/KrakenTradeServiceRaw.java:77,136` and `KrakenAccountServiceRaw.java:282,324` — introduce typed continuation pagination (provider `ofs`/cursor) with repeated/no-progress cursor detection and documented inclusive-boundary semantics; add `KrakenTradeHistoryParams` alignment.
* `xchange-kraken/.../service/KrakenBaseService.java:75-87` — extend `checkResult` into structured Spot exceptions carrying domain, operation, request/order identity, retry class, and sanitized details; model rate tiers via immutable endpoint policy.
* `xchange-kraken/.../dto/account/KrakenEarn*.java` — keep; fold into the `earn` package boundary.
* `xchange-stream-kraken-v2/.../KrakenStreamingExchange.java:38-63` — construct/connect the private socket only when private subscriptions exist and credentials are complete; disconnect both sockets; `isAlive()` over the aggregate lifecycle; separate generation IDs per socket under one aggregate lifecycle; expose per-socket and aggregate observables.
* `xchange-stream-kraken-v2/.../KrakenPrivateStreamingService.java:43-51` — single-flight token fetch/refresh with retry; correlate auth/subscription/trading responses by generation + request/channel ID; reject stale generations.
* `xchange-stream-kraken-v2/.../KrakenStreamingMarketDataService.java` and `dto/common/ChannelType.java` — add book, OHLC, status/instrument channels (and balances/executions/order channels on the private side) with stable contracts; apply official snapshot/update/checksum rules with a dedicated gap/checksum exception and fresh-snapshot rebuild.
* `xchange-stream-kraken-v2/.../KrakenStreamingService.java:24-27` — add reconnect with bounded backoff, reauthentication, and full resubscription of active channels on top of `NettyStreamingService`.
* `xchange-stream-krakenfutures/.../KrakenFuturesStreamingMarketDataService.java:37-80` — validate snapshot/delta `seq` continuity, reject stale/duplicate updates, expose gaps, and rebuild from a new snapshot; deduplicate private fills/orders/positions/account events; preserve instrument/position identity.
* `xchange-krakenfutures/.../service/KrakenFuturesTradeServiceRaw.java:145` — keep batch orders; add edit/amend, cancel-all, order/trade history, fills, client order IDs, reduce-only, trigger/stop/take-profit, and position-specific fields.
* `xchange-krakenfutures/.../service/KrakenFuturesAccountServiceRaw.java` and `KrakenFuturesBaseService.java:38` — extend open positions into full positions (leverage/margin/risk, PnL, liquidation), wallets/collateral, account logs, and transfer history.
* `xchange-krakenfutures/.../service/KrakenFuturesMarketDataService.java:42` — keep funding rates; add historical funding, mark/index prices.
* `xchange-stream-kraken/...` — add `@Deprecated` markers and a migration guide; keep the module compiling through the grace period with a v1 compatibility suite.
* Docs — new Kraken family guide + module READMEs with capability matrix, migration, aliases, auth, fees, dead-man safety, streaming recovery, and raw/generic examples.

### UX / workflow

* Integrator selects the canonical artifact per protocol (Spot REST, Spot WS v2, Futures REST, Futures WS) from the capability matrix.
* Metadata: exchange startup loads authoritative instruments/fees; incomplete or ambiguous catalogs fail explicitly instead of silently misinforming.
* Ordering: place/validate with client-ID; amend atomically; batch where supported; enable dead-man only through explicit typed config; on ambiguous outcomes the library reconciles by ID and reports a structured unknown-outcome exception rather than replaying.
* Streaming: subscribe; the library builds only the sockets needed, tracks generation, reconnects with backoff, reauthenticates, resubscribes, and surfaces continuity gaps; `isAlive()` reflects the true aggregate state.
* Legacy migration: v1 consumers follow the guide's channel parity table and swap the artifact; v1 remains functional during the grace period.

### Requirements

**MVP (must have)**

* R1. Authoritative Spot/Futures instrument, currency, trading-rule, and fee metadata; the `KrakenExchange.java:46` caveat removed or backed by verified schedules.
* R2. Spot: validate-only, create, query, open/closed, cancel, cancel-all, batch, atomic amend, cancel-all-after/dead-man where officially supported; one documented mapping for `Order.userReference`, Kraken user reference, and `cl_ord_id`; typed raw placement/amend results.
* R3. Spot WS v2: conditional private socket, dual-socket disconnect, aggregate `isAlive()`, generation correlation, heartbeat/idle detection, bounded-backoff reconnect with token reacquisition and full resubscription, book checksum/gap recovery.
* R4. Futures: complete instruments/positions/funding/orders/history with client IDs, reduce-only, triggers, dead-man; REST plus WS with `seq` gap detection and snapshot rebuild.
* R5. Structured errors, rate limits, redaction (keys, tokens, addresses, private data), bounded pagination with cursor protection, no blind replay.
* R6. Legacy v1 deprecation markers, migration guide, and v1 compatibility tests.

**VNext (nice to have)**

* N1. Spot WS v2 trading channel operations (order placement over WS) with the same non-replayable placement policy.
* N2. Post-trade reporting endpoints beyond MVP (high-precision/cursor-based reports) where officially available.
* N3. Fee-tier caching with explicit TTL and refresh.

### Concrete acceptance criteria

* AC1. `grep` for the `KrakenExchange.java:46` caveat returns nothing; metadata tests assert exact maker/taker tiers and decimals from fixtures.
* AC2. Unit tests prove public-only Spot WS v2 builds no private socket; combined builds both; `disconnect()` closes both; `isAlive()` false while private auth is incomplete.
* AC3. Book tests prove checksum/seq mismatch raises the dedicated exception and rebuilds from a fresh snapshot (Spot v2 and Futures WS).
* AC4. Placement tests prove ambiguous outcomes never replay; they reconcile by `cl_ord_id`/exchange ID or throw the structured unknown-outcome exception.
* AC5. Dead-man tests (virtual time, deterministic transport) prove default-off, opt-in config, healthy-only refresh, and fail-safe on refresh/auth failure.
* AC6. Legacy v1 deprecation/migration tests compile and pass; the migration guide documents channel parity.
* AC7. Targeted module tests and root `mvn -B clean install` pass; PMD and formatting checks pass.

### Out of scope

* OOS1. Removing `xchange-stream-kraken` in this change (deprecate and document only).
* OOS2. Rewriting Spot v0 endpoints for cosmetic version reasons.
* OOS3. Live order/withdrawal actions in default CI.
* OOS4. Unifying Spot/Futures wire DTOs.

## 7) New algorithms

* Not needed for this feature. The design work is structured application of provider contracts (snapshot/update/checksum rules, sequence continuity, dead-man timer refresh, offset/cursor pagination) rather than new algorithmic research; the PRD pins the required behavior and failure semantics so implementation is deterministic wiring plus fixtures.

## 8) Success metrics

* User-visible outcome: one documented Kraken family guide and capability matrix; accurate metadata/fees; typed amend/batch/dead-man workflows; streaming that reports true state and recovers explicitly.
* Operational / reliability signal: zero silent book corruption paths (checksum/seq failures surface as typed exceptions and rebuild); no blind placement replays in tests or code review; private socket lifecycle matches `isAlive()`.
* Validation / regression signal: deterministic unit suites for signatures, metadata, orders, dead-man (virtual time), streaming lifecycle, and book recovery pass on every module build; credential-gated live smokes for read paths and opt-in canaries pass in CI without default-CI trading actions.

## 9) Rollout plan & Implementation Checklist

### Phase 1: family architecture and metadata

1. [x] Publish the Kraken family architecture doc and capability matrix (canonical artifacts per protocol, layered service shape, legacy v1 migration plan).
   Verification: `docs/` guide reviewed; module READMEs updated.
   Evidence: `docs/kraken-family.md` (artifact ownership table, layered architecture, capability matrix, v1→v2 migration, dead-man safety, build gates); READMEs added for xchange-kraken, xchange-krakenfutures, xchange-stream-kraken, xchange-stream-krakenfutures; v2 README kept.
2. [x] Rework Spot/Futures metadata: authoritative instruments, currencies, trading rules, and fee schedules; remove the `KrakenExchange.java:46-49` caveat; make `KrakenAdapters.adaptFeeTiers` accurate.
   Verification: metadata unit tests with exact decimals/aliases; targeted `mvn -B -pl xchange-kraken,xchange-krakenfutures -am test`.
   Evidence: caveat removed (`KrakenExchange.java`); `adaptPair` keeps tradingFee null when fee data is absent (no misleading zero), falls back to first maker tier; `adaptFeeTiers` copies before sorting and fails explicitly on one-sided tier data; `adaptPair` tolerates missing ordermin/lot_multiplier. New tests `testAdaptToExchangeMetaData_AccurateFeesAndDecimals`, `_SkipsDarkMarkets`, `testAdaptPair_NoMisleadingFeeWhenUnavailable`. xchange-kraken unit suite 57/57 green.
3. [x] Add typed continuation pagination for ledgers, orders, trades, deposits/withdrawals (Spot) and histories/account logs (Futures) with cursor protection.
   Verification: pagination tests incl. repeated/no-progress cursors.
   Evidence: `getKrakenLedgerInfo` full-history iteration now bounded — page ceiling (`MAX_LEDGER_PAGES=1000`, throws when exceeded) and repeated-page no-progress detection (throws `ExchangeException`); single-duplicate-entry exhaustion edge preserved. Raw `ofs` continuation documented as inclusive/chronological. New wiremock tests `full_fetch_stops_at_empty_page` (exactly 2 requests) and `repeated_page_without_progress_fails`. Futures history endpoints are absent from the wire API and land in task 7.
4. [x] Extend structured errors/rate limits/redaction across Spot and Futures REST (`KrakenBaseService.checkResult` and futures equivalent).
   Verification: error-classification and redaction fixture tests.
   Evidence: new `KrakenException`/`KrakenFuturesException` carrying domain, operation, retry class (NON_RETRYABLE / RETRYABLE_RATE_LIMIT / RETRYABLE_TRANSIENT / UNKNOWN), and sanitized error arrays; `KrakenRedactor`/`KrakenFuturesRedactor` redact api keys/secrets/signatures/nonces/OTPs/tokens/bearers/JWTs/addresses; `checkResult` keeps typed exceptions with redacted messages and falls back to the structured exception; all futures raw error sites (orders, cancel, batch, fills, positions, tickers, instruments, order book, history) now throw `KrakenFuturesException`; fixed copy-paste "limit order" message in market/stop order paths. New tests `KrakenErrorHandlingTest` (5), `KrakenFuturesRedactorTest` (3); junit-jupiter + assertj test deps added to xchange-krakenfutures pom.

### Phase 2: Spot order and post-trade capabilities

5. [x] Add atomic amend, batch, cancel-all-after/dead-man, and client-ID consistency to `xchange-kraken` (REST bindings in `KrakenAuthenticated`, raw/high-level services, DTOs).
   Verification: order workflow tests incl. typed raw results and no-blind-replay reconciliation tests.
   Evidence: commit `13938bd990` — `AmendOrder` binding + `KrakenAmendOrderResponse` (amendid, order_id, cl_ord_id, new_order_id, new_cl_ord_id, status, reject_reason, event_errors); raw `amendKrakenOrder` requires exactly one of `order_id`/`cl_ord_id`; `KrakenTradeService.changeOrder` overrides the cancel+re-place default with an atomic amend and returns the amended id; `AddOrderBatch` binding with manually serialized per-order payloads (userref/ordertype/type/pair/price/price2/volume/leverage/oflags/timeinforce/starttm/expiretm/cl_ord_id) and typed per-order txid/description results; `CancelAllOrdersAfter` binding + typed currentTime/triggerTime result, high-level convenience documented as deliberately opt-in dead-man; placement client-ID consistency (`PlaceOrderParams.CLIENT_ORDER_ID` → `cl_ord_id`, `userReference` → `userref`); no blind replay — failed placement surfaces typed exception with exactly one HTTP request. New `KrakenOrderWorkflowsTest` (10 wiremock tests) + 5 fixtures.
6. [x] Add modern post-trade/history endpoints with bounded pagination.
   Verification: post-trade tests with cursor boundaries.
   Evidence: commit `7c5ffa0385` — `TradesHistory` binding gains `consolidate_trades` (modern Kraken flag consolidating trades by txid); `KrakenTradeHistoryParams` exposes `includeTrades`/`consolidateTrades`; raw `getKrakenTradeHistoryAll` pages the `ofs` cursor until provider `count` is reached, an empty page is returned, or `MAX_TRADE_HISTORY_PAGES` (1000) is exceeded, throwing `ExchangeException` on a repeated page without progress; high-level `getTradeHistory` performs the bounded full fetch when no explicit offset is given (explicit offset keeps single-page semantics). New `KrakenTradeHistoryPaginationTest` (6 tests) covering count-reached stop, offset following, empty-page stop, no-progress guard, flag transmission, and single-page explicit offset.

### Phase 3: Futures REST parity

7. [x] Complete Futures instruments, positions (leverage/margin/PnL/liquidation), wallets/collateral, account logs, funding history, order/trade history, triggers, reduce-only, and dead-man.
   Verification: futures unit tests; extend `KrakenFuturesPrivateDataIntegration` for credential-gated smokes.
   Evidence: commit `047afd780a` — `KrakenFuturesOpenPosition` extended with markPrice/limitPrice/liqPrice/unrealized+realized PnL and funding/collateral/leverage/margin/initialMargin/maintMargin/indexPrice/value and the real v3 `instrument` field (symbol kept as legacy alias); `adaptAccounts` entry-price fallback to limitPrice (fixes NPE on real v3 payloads); new `POST /api/v3/accountlog` (since/max_count/before/after) with typed entries carrying the stable id cursor and wallet/balance/change; new `POST /api/v3/fundinghistory` (lastFundingTime) with typed payments; raw + high-level service methods, structured `KrakenFuturesException` on errors; wallets/collateral, triggers (stopPrice/triggerSignal), reduce-only, amend, batch, fills/orders-status and cancel-all-after were already present and verified. New `KrakenFuturesAccountJSONTest` (3 parse tests) + v3 fixtures.

### Phase 4: Spot WS v2 lifecycle and channels

 8. [x] Fix `KrakenStreamingExchange` connect/disconnect/`isAlive()`: conditional private socket, dual-socket disconnect, aggregate lifecycle with per-socket generations.
    Verification: lifecycle unit tests (public-only, private-only, combined; stale-generation rejection).
    Evidence: commit `c25f50254e` — private socket created/connected only when API credentials are configured (`privateSocketRequired`); trade/account streaming services null and documented without credentials; per-socket `AtomicLong` generations bumped on each connect; previous-generation sockets disconnected on re-connect so stale generations cannot deliver; `disconnect()` closes both sockets; `isAlive()` requires every required socket open; `createPublicService`/`createPrivateService` factory hooks for deterministic tests. `KrakenStreamingExchangeLifecycleTest` (5 tests): public-only, combined, private-drop alive, dual disconnect, stale-generation rejection.
 9. [x] Single-flight token refresh in `KrakenPrivateStreamingService`; generation-correlated auth/subscription responses.
    Verification: token/auth tests with deterministic transports.
    Evidence: commit `c25f50254e` — websocket token cached per private-service instance with a 10-minute TTL (under the provider's 15-minute lifetime); refresh is synchronized single-flight so N concurrent subscribers share one REST call; per-instance caching means each connect generation re-authenticates with a fresh token; package-private constructor for mock injection. `KrakenPrivateStreamingServiceTokenTest` (3 tests): concurrent single-flight (4 subscribers, 1 REST call), no token for public channels, TTL expiry refresh.
10. [x] Reconnect with bounded backoff, reauth, and full resubscription on `KrakenStreamingService`.
    Verification: reconnect/resubscribe tests.
    Evidence: commit `b304b9fbae` — `NettyStreamingService.scheduleReconnect` made overridable with protected `isAutoReconnect`/`getWebSocketChannel` accessors (behavior unchanged); `KrakenStreamingService` schedules reconnects with bounded exponential backoff (1s, 2s, 4s, ... capped at 30s, reset on connection success via `subscribeConnectionSuccess`); full resubscription on reconnect uses the base `doOnComplete -> resubscribeChannels` path; `KrakenPrivateStreamingService` invalidates its token cache on every connection success so each reconnect re-authenticates before private-channel resubscription. `KrakenStreamingServiceReconnectTest` (3 tests): exponential growth, cap, reset-on-success.
11. [x] Add book, OHLC, status/instrument, balances/executions/order channels; checksum/gap recovery for books.
    Verification: book snapshot/update/checksum gap and rebuild tests.
    Evidence: commit `271a0ccaf3` — `ChannelType` gains BOOK/OHLC/STATUS/ORDERS (orders added to `PRIVATE_CHANNELS`; balances/executions were already present); new `KrakenBookMessage`/`KrakenOhlcMessage` DTOs routed by `channel_symbol` and registered in the message subtype map; status messages routed to a `status` subscription; `getOrderBook` maintains incremental state (snapshot replace, delta apply, zero-qty removal) with checksum validation per the documented algorithm (CRC32 of top-10 bids descending then asks ascending, `price:qty` at 8 decimals joined by commas); on checksum mismatch the state is dropped, the channel resubscribed via new `resubscribeChannel` (same emitter, fresh snapshot), and emissions resume on the rebuilt book; `getOHLC` raw candles; `getSystemStatus`. `KrakenStreamingBookChannelTest` (6 tests) with fixtures using independently computed reference checksums.

### Phase 5: Futures WS sequence recovery

12. [ ] Validate `seq` continuity on book snapshots/deltas and fills in `KrakenFuturesStreamingMarketDataService`; gap detection with fresh-snapshot rebuild; deduplicate private events; dead-man integration.
    Verification: sequence gap/rebuild and dedup tests.

### Phase 6: legacy migration and validation

13. [ ] Deprecate `xchange-stream-kraken` (markers, guide, v1 compatibility suite, v2 parity table).
    Verification: migration/compilation tests; v1 suite passes.
14. [ ] Run full validation: `mvn -B -pl xchange-kraken,xchange-krakenfutures,xchange-stream-kraken,xchange-stream-kraken-v2,xchange-stream-krakenfutures -am test`, targeted PMD/formatting, root `mvn -B clean install`.
    Verification: green module, PMD, and root builds.

## 10) Risks, dependencies, and edge cases

### Dependencies

* Dependency: Kraken API contract stability — amend/batch/dead-man and modern post-trade endpoints must exist for the supported account tiers; verified against official docs (`docs.kraken.com/api`) during implementation.
* Dependency: `xchange-stream-service-netty` `NettyStreamingService` lifecycle capabilities (reconnect hooks, compression, backoff seams).
* Dependency: credentials for fee-schedule and private-endpoint verification (CI credential-gated smokes only).
* Dependency: Coinbase convention modules (`xchange-coinbase` v3, `xchange-coinbase-derivatives`) as in-repo reference implementations.

### Risks

* Risk: fee schedules are account-dependent; hardcoding tiers would re-introduce the misleading-metadata bug.
* Risk: dead-man/cancel-all-after misconfiguration can cancel all open orders.
* Risk: reconnect/resubscribe logic can drop subscriptions or replay private events if generation correlation is wrong.
* Risk: removing the `KrakenExchange.java:46` caveat before fee data is verified would ship wrong fees.
* Risk: v1 removal before migration evidence would strand existing integrators.

### Edge cases

* Edge case: ambiguous transport outcome on placement/amend (timeout, connection reset after send) — reconcile by identity, never replay.
* Edge case: book checksum/seq mismatch mid-stream — raise dedicated exception, rebuild from snapshot, resume without reporting false continuity.
* Edge case: private socket token expiry during idle — single-flight refresh, reauth on the same generation, reject stale responses.
* Edge case: dead-man refresh racing graceful shutdown — distinguish deliberate shutdown (disable only when explicitly configured and acknowledged) from transport loss (fail safe).
* Edge case: pagination loops with repeated/no-progress cursors — bounded iteration with explicit protection.

### Mitigation / rollback

* Mitigation: fee data loaded from provider schedules with explicit incomplete/ambiguous-catalog failure; deterministic fixtures per tier.
* Mitigation: dead-man default-off, typed opt-in config, virtual-time tests for refresh/failure/graceful-shutdown; docs warn it cancels all open orders.
* Mitigation: generation-scoped correlation for all streaming responses; stale responses rejected by test.
* Mitigation: v1 kept through the grace period behind the compatibility suite; removal is a separate later change.
* Rollback: each slice lands independently (sections 1-6 of the checklist); a failing slice reverts without blocking the rest; metadata and dead-man changes are additive until verified.

## 11) Open Questions

* No `PRD Answers:` rounds have been received yet. Once received, integrate decisions into `Status`, `Execution Status`, `Summary`, and sections 1-10, record the answer round under `Execution Status -> Fresh inputs integrated`, and remove resolved questions below.

**Blocking**

* None.

**Non-blocking**

1. Legacy v1 removal: what compatibility grace period should `xchange-stream-kraken` keep after the v2 migration path ships (current draft: keep through the grace period, remove in a later scoped change)?
2. Channel rollout order for Spot WS v2: should book/checksum recovery ship before or with OHLC/status channels in the same slice?
3. Should fee-schedule discovery require credentials (more accurate, private) or stay public-endpoint-only (no credentials, coarser tiers)?