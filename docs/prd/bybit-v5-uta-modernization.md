# PRD: CF-444 — XChange: modernize Bybit V5 unified trading support

**Project:** XChange
**Source issue:** https://linear.app/cookiefactory/issue/CF-444/xchange-modernize-bybit-v5-unified-trading-support
**Status:** Ready
**Last updated:** 2026-08-12
**Repository/module/exact SHA:** `TheCookieLab/XChange@0bbdb34a096b61c823167afae10780acc8b0d4c5`; `xchange-bybit`, `xchange-stream-bybit`

## Status

* **Lifecycle:** Ready for implementation
* **Blocking state:** None
* **Active phase:** Phase 9 — compatibility/ship
* **Overall checklist progress:** 8/9 phases complete

## Execution Status

* **Last updated:** 2026-08-12
* **Codebase access:** Available
* **Repository/module/exact SHA:** `TheCookieLab/XChange@0bbdb34a096b61c823167afae10780acc8b0d4c5`; `xchange-bybit`, `xchange-stream-bybit`
* **Fresh inputs integrated:** None
* **Evidence reviewed:** `xchange-bybit/src/main/java/org/knowm/xchange/bybit/BybitExchange.java`; `xchange-stream-bybit/src/main/java/info/bitrich/xchangestream/bybit/BybitStreamingExchange.java`; existing issue-linked PRD; issue audit and attached official Bybit V5 documentation.
* **Operational/rollout notes:** Preserve existing V5 module coordinates and common call paths. Keep trading canaries opt-in and credential-gated. No blind replay of ambiguous placements.

## Summary

Modernize the existing Bybit V5 integration into a complete, category-aware Unified Trading Account implementation across Spot, Linear, Inverse, and Options while retaining XChange's existing generic service model where it is lossless. The work must make catalog pagination, account mode, environment selection, provider identity, retry/reconciliation behavior, and WebSocket recovery explicit rather than adding endpoint breadth on top of implicit lifecycle assumptions.

Current repository evidence confirms two concrete architectural gaps that shape the first slices: `BybitExchange.remoteInit()` performs one `getInstrumentsInfo(...)` call per category without cursor completion, while `BybitStreamingExchange` resolves REST/public/private/trade endpoints through partially independent sandbox/testnet rules and does not resubscribe the trade transport in `resubscribeChannels()`.

## 1 Context

The implementation already targets Bybit V5 and has useful Spot/Linear/Inverse/Option primitives. The goal is not a rewrite or a parallel exchange abstraction. It is to complete the current modules using the conventions already established elsewhere in XChange: endpoint-specific wire DTOs, thin raw services, high-level adapters, exact numerics, structured failures, explicit replay safety, deterministic fixtures, and reconnect-safe streaming.

The current `BybitExchange` owns account type, REST environment switching, service initialization, and four-category remote metadata loading. `BybitStreamingExchange` owns three transport classes—public, private, and trade—but presently mixes `USE_SANDBOX` and `SPECIFIC_PARAM_TESTNET` differently across them. These are the existing seams to strengthen rather than bypass.

## 2 Problem / Opportunity

The module's service breadth and lifecycle contracts lag the current V5/UTA surface. A production consumer can encounter incomplete instrument catalogs, category-specific semantics represented too loosely, incomplete account/position/execution reconciliation, environment mismatches across transports, and reconnects where request/order-book continuity is not proven.

Expanding endpoint count without first fixing these contracts would increase ambiguity. The modernization should therefore establish product/environment/pagination/replay foundations first, then add breadth behind those foundations.

## 3 Goals

1. Complete authoritative, bounded instrument discovery for Spot, Linear, Inverse, and Option categories.
2. Add deliberate V5/UTA coverage for market data, accounts/assets, collateral/borrowing, positions/risk, orders/executions, transfers, and Options/RFQ where useful.
3. Preserve category-specific and provider-specific semantics when generic XChange DTOs are lossy.
4. Resolve production/demo/testnet and REST/public/private/trade endpoints from one validated configuration contract.
5. Make order identity, ambiguous-outcome reconciliation, error classification, rate limits, and retry safety explicit.
6. Make all streaming transports generation-aware, reconnect-safe, observable, and sequence-safe.
7. Preserve existing consumers through compatibility adapters and documented migration.

## 4 Non-goals

* Do not introduce a new XChange-wide abstraction for Bybit-only semantics.
* Do not force RFQ, multi-leg Option, risk, or collateral fields into generic core DTOs when information would be lost.
* Do not support deprecated pre-V5 APIs.
* Do not infer environment from credentials.
* Do not blindly retry order placement after an unknown transport result.
* Do not make live trading canaries part of default CI.

## 5 Users & Use cases

* **Existing XChange users:** continue using ordinary market/account/trade services with better completeness and deterministic behavior.
* **Advanced Bybit users:** access provider-specific raw results for UTA, derivatives, Options/RFQ, risk, collateral, and batch workflows that do not map losslessly to core APIs.
* **Streaming users:** subscribe across supported categories and private/trade channels with explicit reconnect, resubscription, request-correlation, deduplication, and sequence-gap recovery.
* **Maintainers:** compare implemented capabilities against a documented matrix and deterministic fixtures rather than rediscovering transport semantics during each change.

## 6 Proposed solution

### 6.1 Capability and configuration foundation

Keep `xchange-bybit` and `xchange-stream-bybit`. Add a checked-in capability matrix covering category, REST/stream transport, authentication, generic/raw exposure, pagination, environment support, and known unsupported operations.

Replace the current split environment logic with one module-local typed environment/configuration model consumed by `BybitExchange.applySpecification(...)` and every transport created by `BybitStreamingExchange`. Validate conflicting production/demo/testnet combinations before service construction. Preserve existing specific parameters through a documented compatibility period rather than making a new builder mandatory.

### 6.2 Complete bounded catalog and history pagination

Extend the existing instruments-info response/cursor model so `BybitExchange.remoteInit()` follows `nextPageCursor` to completion per category. The shared iteration behavior must have a page ceiling, repeated-cursor detection, and no-progress protection. Apply the same bounded pattern to history surfaces as they are added; raw methods retain page/cursor metadata while convenience methods honor caller limits.

### 6.3 Product-aware REST layering

Retain the existing service layering and organize new wire DTOs/raw methods by stable domain: market, account/asset, position/risk, order/execution, transfer/loan/collateral, and option/RFQ. Prefer endpoint-specific immutable DTOs. Preserve `retCode`, `retMsg`, response time, cursor, extended info, category, and provider IDs through raw boundaries.

Add deliberate coverage for public trades and advanced market data; UTA wallet/account state, liabilities/collateral/borrowing/transfers; positions and risk; order/batch/history/execution reconciliation; and lossless Option/RFQ raw workflows. Map into generic XChange services only where semantics remain intact.

### 6.4 Order identity, failures, and retry safety

Treat `orderLinkId` as the caller-visible correlation identity and document its format/uniqueness constraints. Preserve exchange order ID, category, trigger/related identity, rejection data, and partial batch results. Classify structured provider/transport failures and rate-limit telemetry. Retry only replay-safe operations under bounded policy. When placement outcome is ambiguous, reconcile by client/exchange identity; if the result cannot be proven, surface an explicit unknown outcome rather than replaying.

### 6.5 Streaming lifecycle

Keep public, private, and trade sockets separate, but assign each connection a generation and correlate requests/responses to that generation. Authenticate private/trade transports explicitly, use bounded reconnect/backoff, and resubscribe all three transports. Make disconnect and liveness/observable access null-safe. Deduplicate replayed private events. For order books, enforce Bybit snapshot/delta sequence semantics; on an unprovable gap, surface a dedicated failure and rebuild from a fresh snapshot.

## 7 New algorithms

Not needed for this feature.

## 8 Success metrics

* Remote initialization demonstrably consumes every cursor page for each supported category and fails explicitly on repeated/no-progress cursors.
* REST and all WebSocket transports resolve the same requested environment in a complete production/demo/testnet test matrix.
* Supported UTA account/position/order/execution/asset/transfer workflows can be reconciled without silently dropping provider identity.
* Ambiguous placement tests prove zero blind replays and cover successful, absent, and inconclusive reconciliation.
* Sequence-gap tests prove the order-book stream cannot silently continue across broken continuity.
* Existing Spot and derivative consumer regression suites remain green.
* Targeted module tests, formatting/PMD gates, and repository-root build pass.

## 9 Rollout plan & Implementation Checklist

1. [x] **Foundation:** inventory current V5 endpoints and channels; publish the capability/environment matrix; introduce validated configuration used by REST/public/private/trade transports. **Touchpoints:** `BybitExchange`, `BybitStreamingExchange`. **Verification:** exhaustive environment/config tests and compatibility fixtures.
2. [x] **Catalog/pagination:** make Spot/Linear/Inverse/Option discovery cursor-complete with repeated-cursor/no-progress/page-ceiling guards. **Touchpoint:** `BybitExchange.remoteInit()` plus existing raw instruments-info surface. **Verification:** multi-page and malformed-cursor fixtures.
3. [x] **Market/account assets:** complete public market data plus UTA wallet, account, fee, asset, transfer, collateral, borrowing/liability surfaces. **Verification:** exact-decimal DTO/adaptor and pagination fixtures.
4. [x] **Positions/risk:** add category-aware position, leverage, margin-mode, risk-tier and PnL operations without discarding hedge/subposition identity. **Verification:** one-way/hedge and linear/inverse/option fixtures.
5. [x] **Orders/executions:** complete create/amend/cancel/batch/pre-check/open/history/fill/transaction-log coverage and `orderLinkId` reconciliation. **Verification:** order-form matrix, partial batch results, unknown-outcome tests.
6. [x] **Options/RFQ:** expose lossless raw Option/RFQ workflows; map only common lossless data to XChange core. **Verification:** multi-leg/quote identity fixtures and explicit unsupported cases. Note: V5 options are single-leg; OTC RFQ trading (`/v5/otc/rfq/*`) is deprecated/absent from current V5 docs and explicitly unsupported (guarded by test).
7. [x] **Public streaming recovery:** implement category-aware subscription identity, heartbeat, sequence validation, snapshot rebuild, and gap signaling. **Verification:** deterministic snapshot/delta/gap/reconnect tests.
8. [x] **Private/trade lifecycle:** add generation-scoped request correlation, auth/reauth, bounded reconnect, resubscription of the trade socket, private-event deduplication, and null-safe lifecycle observables. **Touchpoint:** `BybitStreamingExchange`. **Verification:** stale-generation, reconnect, resubscribe and no-credential tests.
9. [ ] **Compatibility/ship:** document migration and capability matrix, run demo/testnet/read-only smoke tests where stable, then targeted and root build gates. **Verification:** `mvn -B -pl xchange-bybit,xchange-stream-bybit -am test`, repository quality checks, root `mvn -B clean install`.

## 10 Risks, dependencies, and edge cases

* Bybit category/account-mode differences can make apparently shared DTO fields semantically incompatible; preserve raw domain contracts when uncertain.
* Demo and testnet availability differ by transport/capability; configuration must reject unsupported combinations rather than silently reroute.
* Large catalogs/history require bounded pagination and caller-visible ceilings.
* Hedge mode, trigger orders, Options/RFQ, batch partial success, and collateral/risk data can be lossy in generic core models.
* WebSocket reconnect may replay data or produce late responses from a prior connection generation.
* Provider rate-limit/error changes should degrade through structured unknown codes rather than parsing fragile messages.
* Public API expansion must remain module-local unless cross-exchange evidence justifies a core abstraction.

## 11 Open Questions

### Blocking

None.

### Non-blocking

None.
