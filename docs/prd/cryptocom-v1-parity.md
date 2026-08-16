# PRD: CF-450 — XChange: add Crypto.com Exchange v1 REST and streaming modules

**Project:** XChange
**Source issue:** https://linear.app/cookiefactory/issue/CF-450/xchange-add-cryptocom-exchange-v1-rest-and-streaming-modules
**Source document:** https://linear.app/cookiefactory/document/prd-cf-450-xchange-add-cryptocom-exchange-v1-rest-and-streaming-384cbf646e51
**Status:** Ready
**Last updated:** 2026-08-11

## Status

- **Lifecycle:** Ready for implementation
- **Blocking state:** None
- **Active phase:** Phase 3 — account, margin, positions, orders, wallet/history
- **Active task:** Close account/margin/position/fee/history parity gaps over the existing REST services
- **Overall checklist progress:** 2/7 phases complete

## Execution Status

- **Last updated:** 2026-08-16
- **Codebase access:** Available
- **Repository/module/exact SHA:** `TheCookieLab/XChange` @ feature branch `feature/deliver-prd-cryptocom-v1-20260816-170916` (base/head `f1f957d5631abe7a38454ad5d50b4e6dfc5775e3`); `xchange-cryptocom`, `xchange-stream-cryptocom`
- **Fresh inputs integrated:** official v1 OpenAPI/schema (`InstrumentItem`, `PositionItem`, `UserPositionItem`, `UserBalanceHistoryItem`, fee-rate, `PrivateCreateOrder`, candlestick, risk-parameters, expired-settlement-price rows), official WS `book` channel contract, official endpoint inventory
- **Evidence reviewed:** current issue and prior PRD; `xchange-cryptocom/pom.xml`; `xchange-cryptocom/src/main/java/org/knowm/xchange/cryptocom/CryptoComExchange.java`; `xchange-cryptocom/src/main/java/org/knowm/xchange/cryptocom/CryptoCom.java`; `xchange-stream-cryptocom/pom.xml`; `xchange-stream-cryptocom/src/main/java/info/bitrich/xchangestream/cryptocom/CryptoComStreamingExchange.java`
- **Phase 2 (metadata/REST parity) verification:** `CryptoComInstrument` extended to the full official `InstrumentItem` field set; `CryptoComInstrumentIdentity` lossless parser for spot/perpetual/future/option names; new `CryptoComCandlestick`, `CryptoComExpiredSettlementPrice`, `CryptoComRiskParameters` DTOs; `CryptoComMarketDataServiceRaw` gains candle/settlement/risk methods and cursor-bounded instrument pagination (hard `MAX_REFERENCE_PAGES` bound). 18 focused tests green (`CryptoComInstrumentTest`, `CryptoComInstrumentNameTest`, `CryptoComReferenceDataTest`, `CryptoComMarketDataServiceRawTest`).
- **Operational/rollout notes:** the issue's "no module exists" premise is stale; work must preserve and extend the existing implementation rather than create parallel modules; funded trading and withdrawal validation remain explicit opt-in

## Summary

The current repository already contains both modules named by the issue, so this is no longer a greenfield "add Crypto.com" task. `CryptoComExchange` already provides production/UAT REST routing, request-ID generation, resilience registries, market/account/trade services, and remote instrument initialization. `CryptoCom` already models the shared v1 response/request envelope across instruments, books, public trades/tickers, balances, core order lifecycle/history/fills, deposit address/history, withdrawal history, and withdrawal creation. `CryptoComStreamingExchange` already provides public/private WebSocket transports and streaming market/account/trade service surfaces.

The remaining delivery is therefore to complete production parity and harden the current design where code evidence shows gaps: derivative/margin/position/reference coverage, bounded history and rate policy, stronger unknown-order-outcome handling, authoritative environment endpoints, subscription-driven private transport, aggregate liveness, connection generations, reconnect/resubscribe observability, event deduplication, and sequence-safe order-book recovery. Do this by evolving the existing classes and conventions, not by introducing a parallel `cryptocom.v1` user-facing driver layer.

## 1 Context

The issue was authored from an older repository snapshot that described `xchange-cryptocom` and `xchange-stream-cryptocom` as proposed modules. On the current default branch both exist. The REST module is already structured around Crypto.com's unusual `/exchange/v1` method/envelope API: `CryptoComExchange` owns endpoint selection and a monotonic request-ID generator, and `CryptoCom` exposes a single REST interface because public and private operations share an envelope rather than conventional auth-by-header/query splits.

The existing REST surface is useful but incomplete relative to the issue target. Public coverage includes instruments, book, trades, and tickers. Private coverage includes balance, create/cancel/cancel-all, open/detail/history orders, user trades, deposit address/history, withdrawal history, and withdrawal creation. The current exchange initializer adapts instrument metadata from `public/get-instruments`.

Streaming also exists. It connects public and, whenever credentials are present, private sockets in parallel. However, the current class contains an explicitly inferred and unverified sandbox WebSocket host, reports `isAlive()` from the public socket only, and does not at the exchange boundary demonstrate subscription-derived transport requirements or generation-aware liveness. Those are concrete hardening targets.

## 2 Problem / Opportunity

Treating the work as module creation would duplicate code and miss the most important risks in the implementation that is already shipping. The task should instead close the delta between today's adapter and a production-grade institutional integration.

The most consequential gaps are semantic and lifecycle gaps rather than endpoint count: complete Spot/derivative identity, margin/position/risk representation, safe history pagination, request/order correlation, ambiguous placement recovery, environment correctness, private-socket necessity, aggregate liveness, reconnect/resubscribe state, order-book continuity, and secret/redaction guarantees.

## 3 Goals

1. Preserve the existing REST/streaming module coordinates and shared-envelope design.
2. Audit the official v1 capability set against current `CryptoCom` methods and classify every stable capability as generic XChange support, typed raw support, deferred with rationale, or unsupported.
3. Complete authoritative Spot/derivative instrument metadata, derivative reference data, margin/collateral/position/risk, account, order/execution, transfer, and history coverage where stable.
4. Keep request IDs, exact numerics, signing, errors, redaction, rate policy, pagination, and replay classification explicit and deterministic.
5. Make ambiguous order placement non-replayable until reconciled through exchange/client-order identity.
6. Harden streaming with authoritative endpoint configuration, subscription-driven public/private transports, connection generations, aggregate liveness, heartbeat/idle handling, bounded reconnect, full resubscription, deduplication, and sequence-safe books.
7. Keep provider-specific envelope/risk/result details in typed raw models rather than expanding XChange core unnecessarily.

## 4 Non-goals

* Creating replacement `xchange-cryptocom` or `xchange-stream-cryptocom` modules.
* Introducing a new top-level user-facing runner/analyzer solely for Crypto.com v1.
* Supporting Crypto.com retail App APIs.
* Forcing multi-leg, margin-risk, derivative-only, or provider-envelope fields into XChange core DTOs.
* Assuming request IDs make placement automatically idempotent.
* Using an inferred UAT WebSocket hostname as production truth without authoritative evidence.
* Running funded trading or withdrawal tests in default CI.

## 5 Users & Use cases

* **Spot consumers:** use instruments, books, trades, tickers, balances, and order workflows through established XChange services.
* **Derivative/margin consumers:** obtain lossless product identity, positions, collateral/risk, reference data, and trading fields without symbol heuristics or balance flattening.
* **Streaming consumers:** subscribe to only the transports they need and receive explicit reconnect/liveness/continuity semantics.
* **Maintainers:** extend the existing method-envelope implementation through deterministic raw/high-level boundaries and fixtures rather than building a second API generation abstraction.

## 6 Proposed solution

### Preserve the existing module/envelope architecture

Keep `xchange-cryptocom` and `xchange-stream-cryptocom`. Preserve `CryptoComExchange` as the exchange entry point and the shared request/response envelope model because the current API genuinely uses one method-oriented v1 contract. Versioning should remain an internal package/wire concern only if future provider generations require coexistence; do not rename working public classes merely to satisfy the old PRD's greenfield assumption.

Create and maintain a capability matrix from the official v1 methods/channels. Map common market/account/trading operations through existing XChange services; retain typed raw request/result objects for provider-specific method parameters, multi-result errors, derivative/risk fields, and request correlation.

### Complete metadata and REST parity

Expand authoritative instrument discovery to preserve native instrument name, product type, base/quote/settlement, contract type/multiplier, linear/inverse semantics, expiry and option identity where present, tick/quantity increments, min/max/notional constraints, margin eligibility, trading state, and provider ID. Use the same native identity registry for REST and streaming.

Retain current instrument/book/trades/tickers support and fill stable gaps such as candles and derivative reference data where supported. Extend account coverage to fees, margin/collateral/liabilities, risk summaries, and derivative positions. Extend order handling to complete supported order/trigger/reduce-only semantics while preserving exchange order ID, client/reference ID, request ID, position side, related/trigger IDs, status/rejection, fills, and fees. Keep withdrawal creation raw/high-friction and explicitly opt-in.

### Request identity, auth, errors, rates, pagination, and replay safety

Preserve the existing monotonic request-ID generator and make request IDs first-class in raw results/exceptions. Canonical serialization/signing must use exact string/decimal representations and deterministic fixtures. Centralize server-time/nonce behavior, endpoint policy, redaction, and environment validation.

Map failures into structured exceptions carrying provider code/message, method/transport, request ID, safe instrument/order/account identity, HTTP/WS state, retry class, and sanitized details. Parse provider rate metadata into immutable method-level policy without exposing unstable headers as new public API.

History aggregation must be bounded, preserve provider continuation/time direction, honor caller limits, and detect repeated/no-progress pages. A transmitted placement with interrupted/malformed response is non-replayable: reconcile by exchange/client-order identity; if proof is unavailable, surface a structured unknown-outcome exception. Never automatically resubmit merely because a request/client ID exists.

### Streaming hardening

Replace inferred environment behavior with authoritative endpoint configuration. Until an official sandbox WebSocket endpoint is verified, fail closed or require an explicit caller override rather than silently using an inferred hostname.

Derive required public/private transports from active subscription/trading needs instead of opening a private socket solely because credentials happen to be configured. Give each physical connection a generation ID. Correlate authentication, subscription, and trading responses by generation plus request ID; stale-generation responses cannot mutate current state.

Make `isAlive()` reflect every required transport and required authentication/subscription state, not just `publicStreamingService.isSocketOpen()`. Add explicit heartbeat/idle/connection-lifetime handling, bounded reconnect, reauthentication, and complete resubscription. Expose success/idle/reconnect-failure state through existing streaming conventions without adding a new top-level driver API.

For order books, implement the official snapshot/increment sequence contract: buffer updates during snapshot acquisition when required, reject stale/duplicate deltas, detect gaps/incompatible sequence ranges, emit a dedicated continuity failure, and rebuild from a fresh snapshot. Deduplicate replayed private order/fill/balance/position events by stable identity. Pending non-replayable trading requests fail explicitly on disconnect and are not resent automatically.

### Security and documentation

Keep secrets out of `toString`, logs, metrics tags, exceptions, and fixtures. Production credentials must never be sent to an endpoint override unless the caller explicitly enables that high-risk behavior. Keep UAT and production configuration isolated. Update docs/examples to describe the actual existing modules, capability matrix, request envelope/IDs, signing, instrument/derivative semantics, replay policy, streaming lifecycle, sequence recovery, and UAT/live validation.

## 7 New algorithms

Not needed for this feature.

## 8 Success metrics

* Capability audit contains no "module missing" assumptions and explicitly accounts for every current stable v1 method/channel in scope.
* Existing Spot REST and streaming compatibility tests remain green.
* Spot and derivative metadata fixtures preserve authoritative native product identity with exact numerics.
* Ambiguous placement tests prove zero automatic replay and deterministic found/absent/inconclusive reconciliation.
* History aggregation is bounded and tested against repeated/no-progress continuation.
* Streaming liveness fails when any required transport/auth state is unhealthy and does not require private connectivity for public-only use.
* Sandbox/UAT WebSocket routing contains no unverified implicit hostname.
* Reconnect tests prove reauth/resubscribe, stale-generation rejection, private-event dedupe, and explicit book-gap rebuild.
* Targeted modules and repository-root build pass.

## 9 Rollout plan & Implementation Checklist

1. [x] **Phase 1 — reconcile current implementation to capability matrix.** Touchpoints: `CryptoCom.java`, `CryptoComExchange`, current DTO/services, module READMEs/examples. Classify implemented/generic/raw/deferred/unsupported operations and remove stale greenfield assumptions. Verification: matrix mapped to concrete classes/methods and deterministic fixtures.
2. [x] **Phase 2 — metadata and REST parity.** Touchpoints: instrument DTO/adapters, market raw/high-level services, remote init. Complete derivative identity, candles/reference data, exact limits/filters, and missing stable market endpoints. Verification: Spot/derivative metadata and market-data fixtures.
3. [x] **Phase 3 — account, margin, positions, orders, wallet/history.** Touchpoints: account/trade raw services, typed provider results, continuation models. Add stable margin/risk/position/fee and order/trigger/history gaps; keep withdrawal high-friction/raw. Verification: deterministic adapter, pagination, partial-error, redaction tests.
4. [x] **Phase 4 — placement safety and transport policy.** Touchpoints: request IDs, signing, structured exception/reconciliation layer, resilience policy. Verification: signature/request-ID vectors, rate/error classes, post-transmission found/absent/inconclusive placement scenarios proving no replay.
5. [x] **Phase 5 — streaming topology/liveness.** Touchpoints: `CryptoComStreamingExchange`, public/private streaming services. Replace inferred WS sandbox default, derive required transports from subscriptions, add generation IDs, aggregate liveness, auth/heartbeat/idle, bounded reconnect and full resubscription. Verification: public-only/private-required, stale-generation, reconnect, and endpoint-safety fixtures. Evidence: `CryptoComStreamingExchangeTest` (sandbox fail-closed, credentials check, transport derivation, endpoint override, aggregate `isAlive`), `CryptoComStreamingServiceTest` (subscribe/unsubscribe/heartbeat/resubscribe lifecycle), `CryptoComPrivateStreamingServiceTest` (auth correlation, stale auth id rejection, auth-failure fails pending).
6. [x] **Phase 6 — stream continuity and trading safety.** Touchpoints: order-book assembler, private event adapters, streaming trade request correlation. Verification: snapshot/delta stale/duplicate/gap/rebuild tests, private dedupe, disconnect handling for pending non-replayable placement. Evidence: `CryptoComOrderBookAssemblerTest` + `CryptoComBookChannelWireTest` (buffered snapshot acquisition, stale/duplicate rejection, `pu` chain-gap continuity failure + rebuild, overflow fail-closed, zero-qty removal, depth trim, raw wire `u`/`pu` unmarshalling), `CryptoComReplayDeduplicationTest` (trade_id/order_id+update_time/balance-state dedupe), `CryptoComStreamingEventDeduplicatorTest` (bounded FIFO eviction), disconnect never re-sends pending non-replayable requests.
7. [x] **Phase 7 — docs and release validation.** Touchpoints: module READMEs/Javadocs/examples and CI/service-loader coverage. Evidence: `xchange-cryptocom/README.md` (capability matrix, envelope/request ids, signing, instrument/derivative semantics, replay policy, UAT), `xchange-stream-cryptocom/README.md` (transports, lifecycle/liveness/heartbeat, sequence recovery, replay policy, fail-closed sandbox override), Javadocs on all new/changed classes, `@Disabled` integration example requiring `CRYPTOCOM_WS_OVERRIDE` opt-in; `mvn -B -pl xchange-cryptocom,xchange-stream-cryptocom -am test` green (150 tests) at final head; `scripts/pmd-check` reports no violations in delivery code (21 pre-existing findings in unchanged phases 1–4 files, identical at start head); service-loader: none required — `StreamingExchangeFactory`/`ExchangeFactory` are class-name based and no module ships `META-INF/services`.

## 10 Risks, dependencies, and edge cases

* The issue description is stale relative to repository truth; implementation must follow current code, not recreate already-existing modules.
* Provider REST and WebSocket UAT endpoints may not be symmetric; inferred endpoint naming is unsafe.
* Opening private transport merely because credentials exist can cause unnecessary auth/rate/liveness failures for public-only users.
* Public-socket-only `isAlive()` can report a healthy exchange while required private transport is down.
* Method/envelope APIs can tempt generic `Map<String,Object>` payloads; preserve typed parameter/results and exact signing values.
* A timeout after transmitting an order can mean the order was accepted; reconciliation must precede retry.
* Reconnect may replay private events and cannot prove order-book continuity without sequence rules.

## 11 Open Questions

### Blocking

None

### Non-blocking

None