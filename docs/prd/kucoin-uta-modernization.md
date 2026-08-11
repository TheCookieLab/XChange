# PRD: CF-449 — XChange: add KuCoin UTA API support and modernize streaming

**Project:** XChange
**Source issue:** https://linear.app/cookiefactory/issue/CF-449/xchange-add-kucoin-uta-api-support-and-modernize-streaming
**Status:** Ready
**Last updated:** 2026-08-11
**Repository/module/exact SHA:** `TheCookieLab/XChange@0bbdb34a096b61c823167afae10780acc8b0d4c5`; `xchange-kucoin`, `xchange-stream-kucoin`

## Status

* **Lifecycle:** Ready for implementation
* **Blocking state:** None
* **Active phase:** Phase 1 — compatibility boundary and capability contract
* **Overall checklist progress:** 3/8 phases complete (1, 2, 3, 5 checked; 4 and 6-8 in progress)

## Summary

Modernize KuCoin support around an explicit classic-versus-UTA boundary rather than silently changing the semantics of the existing exchange. Keep the existing Maven coordinates, add a versioned UTA implementation for current Spot/Futures account and trading capabilities, and modernize streaming so reconnect, subscription restoration, request correlation, and order-book continuity are explicit and testable.

## 1 Context

KuCoin now has a materially different unified-account/API generation while XChange's current KuCoin integration is organized around classic Spot-era services. The existing `KucoinExchange` initializes `KucoinMarketDataService`, `KucoinAccountService`, and `KucoinTradeService`, uses `https://api.kucoin.com` / sandbox host selection, populates metadata from classic symbol/currency APIs, and asks the account service for public/private WebSocket connection details. `xchange-stream-kucoin` is a separate module layered on `xchange-kucoin` plus streaming core/netty.

## 2 Problem / Opportunity

Without a deliberate boundary, adding UTA creates several high-risk failure modes: UTA credentials can be sent to classic endpoints; unified collateral/liabilities can be flattened into classic balances; Spot and derivative identities can collide; classic and current WebSocket semantics can be mixed; reconnect can resume from unproved sequence state; and an ambiguous order placement can be replayed accidentally.

## 3 Goals

1. Add a first-class, versioned KuCoin UTA implementation for stable Spot and Futures capabilities.
2. Preserve classic behavior behind an explicit compatibility boundary and documented migration path.
3. Make API/account mode selection and credential incompatibility fail early and actionably.
4. Cover authoritative instruments, unified balances, liabilities, margin, positions, transfers, orders, executions, and histories without lossy flattening.
5. Modernize public/private streaming for current domains, token/auth behavior, frame format, heartbeat, connection generation, reconnect/resubscribe, and sequence-safe depth.
6. Define bounded pagination, structured/redacted failures, endpoint/rate policy, client-order identity, and no-blind-replay semantics.
7. Keep common XChange APIs simple; expose provider-specific raw results only where generic DTOs would discard material semantics.

## 4 Non-goals

* Silently redirect existing classic APIs to UTA endpoints.
* Remove classic support in the release that first introduces UTA.
* Force UTA-specific account/position/risk fields into XChange core when an exchange-specific raw model is more appropriate.
* Require private credentials for public metadata initialization.
* Blindly replay order placement after an unknown transport outcome.
* Treat reconnect as proof of event or order-book continuity.

## 5 Users & Use cases

* Existing classic KuCoin consumers: upgrade without involuntary account/API-mode migration.
* UTA consumers: configure UTA explicitly and use current unified Spot/Futures account, risk, transfer, order, and execution capabilities.
* Streaming consumers: subscribe to public/private channels with observable, generation-aware reconnect behavior and explicit sequence-gap recovery.
* Maintainers: extend endpoints through predictable raw/high-level service boundaries with deterministic fixtures and compatibility tests.

## 6 Proposed solution

### API mode and compatibility

Introduce a small typed API-mode setting with `CLASSIC` and `UTA` semantics. Preserve classic as the compatibility-period default unless an existing repository-wide breaking-change policy explicitly dictates otherwise. The selected mode owns REST clients, auth validation, metadata loaders, account/trade services, and streaming transport selection. A credential/account-mode probe may validate compatibility, but it must never silently switch modes.

Keep `xchange-kucoin` and `xchange-stream-kucoin` coordinates. Add explicit UTA implementation packages (for example `org.knowm.xchange.kucoin.uta`) and isolate classic-only DTOs/services where semantics diverge. Share only demonstrably identical signing/envelope/utility code.

### REST and domain model

Partition UTA raw APIs by market, account, positions/margin, trade, asset/transfer, and common transport/policy concerns. Each domain owns endpoint-specific immutable DTOs, a thin raw service, high-level adapters, and deterministic fixtures.

Instrument discovery must preserve native symbol/product identity, Spot versus derivative type, base/quote/settlement, contract multiplier, linear/inverse semantics, expiry, tick/lot/minimums, leverage/risk fields, status, and permissions. Spot maps naturally to `CurrencyPair`; derivatives retain distinct derivative identity rather than being inferred from symbol text.

Implement stable UTA market-data, account, margin, position, transfer/wallet, fee, order, amend/cancel, fill/execution, and history APIs. Keep raw typed results for partial batch outcomes, trigger/related-order fields, unified-risk summaries, and any response that generic XChange DTOs cannot represent losslessly.

### Auth, pagination, errors, and replay safety

Implement UTA auth/signature/timestamp/passphrase/key-version rules independently from classic auth. Centralize endpoint resolution, time-drift diagnostics, sanitization, and immutable endpoint policy. Pagination must be typed and bounded, preserve direction/boundaries, honor caller limits, and detect repeated/no-progress continuations.

Map provider failures into structured exceptions carrying mode, domain/endpoint, provider code, HTTP status, sanitized request/order identity, and retry classification. Secrets, signatures, WebSocket tokens, private payloads, and withdrawal/transfer details must never appear in logs or exception text.

Map `Order.userReference` to the documented KuCoin client-order identity with validated constraints. A transmitted placement whose result is unknown is non-replayable: reconcile by client/exchange order ID; if proof remains unavailable, surface an explicit unknown-outcome exception rather than resubmit.

### Streaming

Treat classic and UTA streaming as separate generations. Resolve public/private transports from the selected mode, derive required sockets from active subscriptions, and assign every physical connection a generation ID. Login/subscription/trading request correlation is generation + request ID; stale-generation responses cannot mutate current state.

Implement current heartbeat/token lifetime behavior, bounded reconnect, token reacquisition when required, complete resubscription, aggregate liveness across every required transport, and idempotent/null-safe disconnect/service access. For depth, use provider-defined snapshot-plus-increment rules: buffer deltas during snapshot acquisition, reject stale/duplicate data, detect sequence/version gaps, emit a dedicated gap failure, and rebuild from a fresh authoritative snapshot. Private order/execution/balance/position events are deduplicated by stable provider identity.

WebSocket placement/cancel uses the same typed request/result and unknown-outcome policy as REST. A disconnect must fail non-replayable pending placements explicitly; it must not silently resend them.

### Compatibility and documentation

Inventory existing public classes, examples, service interfaces, exchange parameters, and defaults. Mark classic-only APIs clearly and provide migration examples for metadata, balances, positions, orders/history, transfers, and streaming. Publish a classic-versus-UTA capability matrix and a later evidence-based decision point for long-term classic support/deprecation.

## 8 Success metrics

* Existing representative classic consumer fixtures continue to pass without changing configuration.
* UTA credential/account mismatch is detected before a trading operation with an actionable error.
* Full configured Spot/Futures catalogs are retrieved without ambiguous identity or silent partial pagination.
* Ambiguous order-placement tests prove zero automatic replay and deterministic reconciliation outcomes.
* Streaming tests prove full reconnect/resubscribe and reject stale-generation events.
* Order-book tests prove continuity or explicit gap/rebuild; no path continues on unproved sequence state.
* Secrets/redacted fields are absent from deterministic failure/log fixtures.
* Targeted KuCoin modules and the repository root build are green.

## 9 Rollout plan & Implementation Checklist

1. [x] **Phase 1 — compatibility contract.** Touchpoints: `KucoinExchange`, existing exchange-specific parameters, public classic service interfaces, module docs. Define API-mode ownership, compatibility default, capability matrix, package boundary, and migration policy. Verification: compile/runtime classic compatibility fixture plus mode-mismatch tests.
2. [x] **Phase 2 — UTA transport/auth foundation.** Touchpoints: new UTA common/auth/endpoint-policy packages in `xchange-kucoin`. Add deterministic signatures, endpoint overrides, time-drift diagnostics, redaction, structured errors, and retry classes. Verification: deterministic wire/error/auth fixtures.
3. [x] **Phase 3 — metadata and public market data.** Touchpoints: UTA market raw service, metadata adapters, `remoteInit`/mode-specific initializer. Implement complete Spot/Futures product discovery and stable market APIs. Verification: multi-page catalog fixtures, exact numeric/filter mapping, no-private-credentials public init.
4. [ ] **Phase 4 — unified account and trading.** Touchpoints: UTA account/position/margin/asset/trade raw services and high-level adapters. Implement balances/liabilities/collateral, positions/risk, transfers, orders/amends/cancels, fills/history, typed continuation. Verification: DTO/adaptor fixtures, pagination no-progress tests, partial-batch tests.
5. [x] **Phase 5 — placement safety.** Touchpoints: trade adapter, structured exception/reconciliation types. Validate client IDs and enforce unknown-outcome reconciliation with no blind replay. Verification: found/absent/inconclusive post-transmission scenarios.
6. [ ] **Phase 6 — streaming transport modernization.** Touchpoints: `xchange-stream-kucoin` connection/auth/subscription lifecycle. Add mode-specific endpoints, generation IDs, heartbeat/token lifecycle, aggregate liveness, bounded reconnect, and full resubscription. Verification: stale-generation, reconnect, token refresh, idempotent disconnect fixtures.
7. [ ] **Phase 7 — sequence-safe streams and WebSocket trading.** Touchpoints: depth assembler, private event adapters, WS trading request correlation. Add snapshot/delta continuity, gap rebuild, private dedupe, and disconnect replay classification. Verification: deterministic gap/duplicate/reconnect and pending-placement tests.
8. [ ] **Phase 8 — migration and release validation.** Touchpoints: module READMEs/examples/Javadocs and compatibility tests. Verification: `mvn -B -pl xchange-kucoin,xchange-stream-kucoin -am test`, formatting/PMD gates, repository-root build, public read-only smoke; private/trading canary remains explicit opt-in.

## 10 Risks, dependencies, and edge cases

* Provider behavior can differ by classic/UTA account eligibility; never infer a mode switch from one endpoint failure.
* Equal symbol text across product families can collide unless native product identity remains first-class.
* Pagination or catalog truncation must fail closed rather than silently publish partial metadata.
* A network timeout after request transmission is not evidence that an order failed; reconciliation is mandatory before retry.
* Reconnect can replay or omit private events; stable IDs/generation boundaries are required.
* Sequence semantics may vary by channel; each order-book channel must implement its documented continuity rule rather than a generic approximation.
* Compatibility shims must not convert responses where classic and UTA semantics materially differ.
