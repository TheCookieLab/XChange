# PRD: CF-447 — Coinbase Advanced Trade v3 parity and hardening

*Project:* XChange  
*Source issue:* [CF-447](https://linear.app/cookiefactory/issue/CF-447)  
*Author:* Codex (automated)  
*Status:* Ready  
*Last updated:* 2026-08-09

---

## Status

* Lifecycle: `Ready`
* Blocking state: `None`
* Active phase: `Phase 0 — implementation queue`
* Active task: none (awaiting `prd:ready` delivery)
* Overall: `0/22` checklist tasks complete

## Execution Status

* Last updated: `2026-08-09`
* Codebase access: `confirmed`
* Repo / module path: `xchange-coinbase`, `xchange-stream-coinbase`, `xchange-coinbase-derivatives`, `xchange-stream-coinbase-derivatives` (all registered in root `pom.xml`; module list lines 61-62, 101-110)
* Fresh inputs integrated:
  * CF-447 issue description (2026-08-09) — audit findings and target outcome
  * Codebase evidence pass (2026-08-09) — every touchpoint below verified against live sources
* Evidence reviewed:
  * REST: `CoinbaseAuthenticated.java`, `Coinbase.java`, `CoinbaseExchange.java`, `CoinbaseV3Digest.java`, `CoinbaseBaseService.java`, `CoinbaseAccountService(Raw).java`, `CoinbaseTradeService(Raw).java`, `CoinbaseMarketDataService(Raw).java`, `CoinbaseWebsocketAuthentication.java`, `CoinbaseAdapters.java`, `CoinbaseV3OrderRequests.java`, `v2/CoinbaseV2Authenticated.java`
  * Stream: `CoinbaseStreamingExchange.java`, `CoinbaseStreamingService.java`, `CoinbaseStreamingMarketDataService.java`, `CoinbaseStreamingTradeService.java`, `CoinbaseProductIds.java`, `CoinbaseChannel.java`, `adapters/CoinbaseStreamingAdapters.java`
  * Reference: `coinbasederivatives/client/*` (ReplaySafety, RetryClassification, RateCreditMetadata, RpcResult, CoinbaseDerivativesException, CoinbaseDerivativesRedactor, JsonRpcTransport), `auth/*`, `CoinbaseDerivativesStreamingService.java`, `CreditAwareBackoff.java`, `CoinbaseDerivativesStreamGapException.java`
  * Tests/fixtures: unit + DTO JSON tests, `*Integration.java`, `*SandboxIntegration.java`, `CoinbaseSandboxEndpointMatrixIntegration.java`, `CoinbaseStreamingServiceTest.java`, `CoinbaseStreamingMarketDataServiceTest.java`, `CoinbaseStreamingTradeServiceTest.java`
* Notes:
  * No WebSocket sandbox exists (`CoinbaseStreamingExchange` comment: "There is no sandbox environment for WebSocket connections"); REST sandbox coverage is partial and validated with synthetic IDs.
  * Stream services log full subscribe payloads and messages at INFO (`CoinbaseStreamingService#messageHandler`, `sendChannelCommand`) — includes JWTs; redaction work must fix this.

## Summary

Finish the Coinbase Advanced Trade v3 modernization and qualify it as XChange's reference implementation for future exchange work. The REST module already establishes the desired versioned-package, endpoint-specific DTO, raw/high-level service, JWT, pagination, and integration-test patterns; the stream module adds channel ref-counting, rate limiting, order-book sequence recovery, and fill-delta deduplication. The remaining work is a bounded parity and hardening pass: publish a checked-in endpoint/capability matrix, replace the global `PARAM_PRODUCT_ID_OVERRIDE` workaround with a first-class instrument/product identity mapper, run market-data and user-order sockets as two generation-scoped transports owned by one lifecycle, replace the reflective WS-JWT fallback with one shared typed authentication contract, port the derivatives modules' request-correlation, redaction, replay-classification, structured-error, and generation-aware reconnect conventions into the spot/Advanced Trade stream, and formalize the legacy v2 compatibility path. Not a rewrite; the Coinbase Derivatives gateway modules remain the dedicated futures/perpetuals integration.

## 1) Context

Four modules in the root reactor: `xchange-coinbase` (REST), `xchange-stream-coinbase` (WS), and the reference siblings `xchange-coinbase-derivatives` / `xchange-stream-coinbase-derivatives`. Coinbase ranked #5 by centralized spot-exchange volume in CoinGecko's 2026 Q2 report, and the v3 module is already the strongest modernized exchange implementation in the repo — this issue is the parity/lifecycle/cleanup pass that makes it the template.

Current state (verified):

* REST v3 lives in `org.knowm.xchange.coinbase.v3`: `CoinbaseExchange` (registers `CoinbaseMarketDataService`, `CoinbaseAccountService`, `CoinbaseTradeService`; `https://api.coinbase.com`), `CoinbaseAuthenticated` (`/api/v3/brokerage`, ~30 endpoints), `Coinbase` (public `/market/*` + `time`).
* Stream lives in `info.bitrich.xchangestream.coinbase`: `CoinbaseStreamingExchange` (extends `CoinbaseExchange`), one `CoinbaseStreamingService` socket shared by `CoinbaseStreamingMarketDataService` and `CoinbaseStreamingTradeService`.
* Derivatives reference conventions live in `org.knowm.xchange.coinbasederivatives.client` + `auth` and `info.bitrich.xchangestream.coinbasederivatives`.

Constraints: no WebSocket sandbox; REST sandbox has partial endpoint coverage; root `mvn -B clean install` and PMD gates apply; `xchange-stream-*` modules depend on `xchange-coinbase` (artifact `xchange-stream-coinbase` pom depends on `xchange-coinbase`), so the typed auth contract must be shared from the REST module without a new public API surface beyond what is required.

## 2) Problem / Opportunity

Audit findings with code evidence:

1. **No capability matrix.** No checked-in artifact maps official Advanced Trade REST/WS capabilities to implementation status. `CoinbaseAuthenticated.java` covers accounts, orders (create/edit/preview/previewEdit/batch_cancel/historical/fills/close_position), products/pricebook/candles/ticker, transaction_summary (fees), payment_methods, portfolios (+move_funds), convert (quote/commit/get), cfm futures (balance_summary, positions, sweeps, intraday margin, margin window), intx perpetuals (portfolio summary, balances, positions, multi_asset_collateral, allocate), key_permissions. Gaps and staleness are unverifiable without a matrix.
2. **Global product-ID override.** `CoinbaseStreamingExchange.PARAM_PRODUCT_ID_OVERRIDE` (`"Coinbase_Product_Id_Override"`) forces every market-data subscription in the instance to one product id (`CoinbaseStreamingMarketDataService#resolveProductId`), with manual `expectedProductId` filtering in `adaptTickers/adaptTrades/adaptCandles`. `CoinbaseProductIds.productId` only emits `BASE-COUNTER`; `CoinbaseStreamingAdapters.toCurrencyPair` splits on `-` and requires exactly two tokens, so futures (`BTC-CFMF`) and perpetuals (`BTC-PERP`) are lossy or silently dropped. Javadoc on the parameter itself admits: "The override is global for the exchange instance."
3. **Single resolved endpoint per instance.** `CoinbaseStreamingExchange` resolves ONE URL (`resolveWebsocketUrl`: override or `PROD_WS_URI`, which equals `MARKET_DATA_WS_URI`). `USER_ORDER_DATA_WS_URI` exists as a constant but is only reachable by overriding the same URI that market data uses. Both services share one socket; there is no lifecycle that opens both endpoints and routes channels per endpoint.
4. **Reflective auth fallback.** `CoinbaseStreamingExchange#attemptHelperJwtSupplier` uses `Class.forName("org.knowm.xchange.coinbase.v3.service.CoinbaseWebsocketAuthentication")` + `getMethod("websocketJwtSupplier")` + `invoke`, with two fallbacks (injected `Supplier` param, inline `CoinbaseV3Digest` supplier). No typed contract shared between REST (`ParamsDigest`) and WS (`Supplier<String>`).
5. **No correlation/replay/redaction conventions in the spot stream.** `CoinbaseStreamingService#getSubscriptionUniqueId` returns the channel name only; subscribe messages carry no request id; no connection generation exists; `messageHandler`/`sendChannelCommand` log full messages and subscribe payloads (including `jwt`) at INFO. No replay classification: JWT refresh is a re-subscribe; user-order events are deduplicated by cumulative-quantity delta + 10-minute terminal-order TTL, not by replay policy. Compare derivatives: `RpcResult(requestId, value)`, `CoinbaseDerivativesException(code, requestId, method, retryClassification, sanitizedDetails)`, `CoinbaseDerivativesRedactor`, `ReplaySafety`, `RetryClassification`, generation-scoped `request(method, params, replaySafe)`.
6. **Pagination gaps.** `CoinbaseAccountServiceRaw#getCoinbaseAccounts` and `CoinbaseTradeService#getTradeHistory` loop cursors (limit-aware), but `CoinbaseTradeServiceRaw#listOrders` returns a single page with a cursor and no high-level iteration; no repeated-cursor/no-progress detection anywhere.
7. **Legacy v2 state.** Full v2 services are already gone; what remains is `org.knowm.xchange.coinbase.v2.*` DTOs + `CoinbaseV2Authenticated` used by `CoinbaseAccountServiceRaw#listV2Accounts/listV2AccountTransactions` (deposit/withdrawal discovery) and the `CoinbaseAdapters` name at package root (now v3 adapters). No `@Deprecated` markers, no migration doc, no removal window.
8. **Sandbox limitations.** `CoinbaseSandboxEndpointMatrixIntegration` probes endpoints with synthetic IDs (`sandbox-account-123`, `sandbox-order-456`, …) and treats 4xx as "reachable"; tests must distinguish fixtures, sandbox responses, and production smoke.

What becomes possible: maintainers get a reference module with lossless instrument identity, typed auth, explicit replay safety, dual-endpoint streaming, deterministic fixtures, and a tested v2 migration path — directly reusable by future exchange modernizations.

## 3) Goals

* G1. Checked-in endpoint/capability matrix for every stable Advanced Trade REST endpoint and WS channel in scope.
* G2. Lossless Coinbase product identity (spot, US futures, Advanced Trade perpetuals) without a global override.
* G3. One typed JWT/authentication contract shared by REST and both WS transports; reflective fallback removed.
* G4. Market-data and user-order sockets managed as separate generation-scoped transports under one `CoinbaseStreamingExchange` lifecycle.
* G5. Derivatives-grade conventions in the spot stream: request correlation, redaction, replay classification, structured errors, stale-generation rejection.
* G6. Bounded, loop-safe cursor pagination for all pageable high-level operations.
* G7. Deterministic tests and a precise fixture/sandbox/production validation model.
* G8. Documented, tested legacy v2 compatibility path; maintainers' reference checklist published.

## 4) Non-goals

* NG1. Moving Coinbase Derivatives gateway behavior into `xchange-coinbase`/`xchange-stream-coinbase`.
* NG2. Replacing correct v3 DTOs/services for stylistic uniformity.
* NG3. Treating all Coinbase products as `CurrencyPair` instruments.
* NG4. Claiming the static sandbox validates live matching, auth, streaming, or rate limits.
* NG5. Blindly retrying order placement after an ambiguous transport result.
* NG6. Removing v2 code in this issue unless the repository compatibility policy and usage evidence permit it.

## 5) Users & Use cases

### Current state / evidence

* REST: `CoinbaseExchange#initServices` → `CoinbaseMarketDataService`, `CoinbaseAccountService`, `CoinbaseTradeService`; raw layer via `CoinbaseBaseService` (`CoinbaseAuthenticated` + `Coinbase` proxies, `authTokenCreator` = `CoinbaseV3Digest`); adapters in `CoinbaseAdapters` (package root, historical name); order request builders in `CoinbaseV3OrderRequests` (`commonOrderRequest` maps `Order.getUserReference()` → `client_order_id` when `includeClientOrderId`).
* Auth: `CoinbaseV3Digest` — ES256 JWT (`kid` = key name, `iss` = `"cdp"`, `sub`, `nbf`, `exp` = 120 s, `uri` claim for REST, none for WS, random 16-byte `nonce` header); `createInstance` returns null on missing keys, throws `IllegalStateException` on parse failure; supports `PEMKeyPair` and `PrivateKeyInfo` PEM forms. `CoinbaseWebsocketAuthentication#websocketJwtSupplier` is the reflective helper.
* Stream: `CoinbaseStreamingExchange` constants `MARKET_DATA_WS_URI`, `USER_ORDER_DATA_WS_URI`, `PROD_WS_URI = MARKET_DATA_WS_URI`, `PARAM_PUBLIC_RATE_LIMIT` (default 8/s), `PARAM_PRIVATE_RATE_LIMIT` (default 750/s), `PARAM_MANUAL_HEARTBEAT`, `PARAM_DEFAULT_CANDLE_GRANULARITY`, `PARAM_DEFAULT_CANDLE_PRODUCT_TYPE`, `PARAM_PRODUCT_ID_OVERRIDE`, `PARAM_WEBSOCKET_JWT_SUPPLIER`. Channels: `TICKER`, `TICKER_BATCH`, `MARKET_TRADES`, `CANDLES`, `LEVEL2`, `LEVEL2_BATCH`, `L2_DATA`, `STATUS`, `HEARTBEATS`, `USER`, `FUTURES_BALANCE_SUMMARY` (auth: `USER`, `FUTURES_BALANCE_SUMMARY`). Order book: `CoinbaseStreamingMarketDataService.OrderBookState` with snapshot/delta, sequence-gap → REST snapshot recovery (`recoverFromSnapshot`), stale-update skip; gap recovery failure is only a log line — no subscriber-visible gap event. Trade stream: `CoinbaseStreamingTradeService` with cumulative-quantity delta (`calculateAndUpdateDelta`) + `processedTerminalOrders` TTL map, `ensureAuthenticated` (key/secret presence check only).
* Derivatives reference: `client/ReplaySafety` (READ, IDEMPOTENT_CANCELLATION, PLACEMENT); `client/RetryClassification` (AUTHENTICATION, TRANSIENT, RATE_CREDIT, PERMANENT, AMBIGUOUS); `client/RateCreditMetadata`; `client/RpcResult<T>(requestId, value)`; `client/CoinbaseDerivativesException(code, message, requestId, method, retryClassification, sanitizedDetails)`; `client/CoinbaseDerivativesRedactor` (Authorization bearer, `eyJ…` JWTs, token-like fields); `auth/CoinbaseDerivativesJwtGenerator`, `AccessTokenProvider`, `AccessToken` (typed injectable); `CoinbaseDerivativesStreamingService` — `connectionGeneration` + `beginConnectionGeneration()` (fails stale pending with "Connection generation changed before response"), `request(method, params, replaySafe)` correlated by JSON-RPC id, `public/auth` reauth with scheduled refresh, `public/set_heartbeat` + `public/test`, per-channel `change_id`/`prev_change_id` gap detection → `CoinbaseDerivativesStreamGapException`, LRU event dedup keyed `channel:id:version`, `CreditAwareBackoff` + `creditBlockedUntilNanos`, `protocolErrors()` subject, redactor on all error messages.

### Primary user

Exchange integrators and maintainers who build on XChange's Coinbase modules; operations engineers running production spot/futures/perpetual streams and order management.

### Key use cases

* UC1. Subscribe to market data for spot, US futures, and Advanced Trade perpetual products with correct instrument identity and no cross-product leakage.
* UC2. Run authenticated user-order streaming (fills, order changes, futures balance summary) on the user endpoint while market data runs on the market-data endpoint, with automatic reconnect/reauthenticate/resubscribe.
* UC3. Place, preview, edit, cancel, and reconcile orders with `client_order_id` round-tripping and no blind replay after ambiguous outcomes.
* UC4. Diagnose failures from structured, sanitized errors carrying correlation ids and retry classification.
* UC5. Migrate from legacy v2 account-transaction flows with documented, tested guidance.
* UC6. Use the Coinbase module as the template for the next exchange modernization.

### Existing touchpoints

* `CoinbaseAuthenticated` / `Coinbase` (JAX-RS endpoint interfaces) — capability matrix source
* `CoinbaseBaseService` — shared auth digest wiring
* `CoinbaseV3Digest#createInstance`, `#digestParams`, `#generateWebsocketJwt` — JWT contract
* `CoinbaseWebsocketAuthentication#websocketJwtSupplier` — reflective helper to replace
* `CoinbaseStreamingExchange#resolveWebsocketUrl`, `#resolveJwtSupplier`, `#attemptHelperJwtSupplier`, `#createStreamingService`, `#createStreamingTradeService`, `#connect`, `#disconnect`, `#processProductSubscriptions` — lifecycle seams
* `CoinbaseStreamingService#subscribeChannel`, `#sendChannelCommand`, `#getSubscriptionUniqueId`, `#messageHandler`, `ChannelState` — correlation/redaction seams
* `CoinbaseStreamingMarketDataService#resolveProductId`, `#getOrderBook`, `OrderBookState#process/#recoverFromSnapshot`, `#ensureHeartbeatsSubscription`, `#resubscribe` — identity + order-book seams
* `CoinbaseStreamingTradeService#getUserOrderEvents`, `#toUserTrade`, `#calculateAndUpdateDelta`, `#ensureAuthenticated` — replay/dedup seams
* `CoinbaseProductIds#productId`, `CoinbaseStreamingAdapters#toCurrencyPair` — identity mapper seams
* `CoinbaseAccountServiceRaw#getCoinbaseAccounts`, `CoinbaseTradeService#getTradeHistory`, `CoinbaseTradeServiceRaw#listOrders`, `CoinbaseTradeHistoryParams` — pagination seams
* `CoinbaseSandboxEndpointMatrixIntegration`, `*SandboxIntegration.java`, `secret.keys_` — validation model
* Tests: `CoinbaseV3DigestTest`, `CoinbaseWebsocketAuthenticationTest`, `CoinbaseStreamingServiceTest`, `CoinbaseStreamingMarketDataServiceTest`, `CoinbaseStreamingTradeServiceTest`, DTO JSON tests under `src/test/resources/.../dto/v3/`

## 6) Proposed solution

### Summary

Keep the existing REST architecture. Publish the capability matrix as a checked-in doc + test-enforced inventory; replace the override with a Coinbase product-identity catalog built from product discovery; introduce one typed `CoinbaseV3Authentication` contract (digest + WS JWT supplier, injectable) used by REST and both sockets; split the stream into two generation-scoped `CoinbaseStreamingService` transports owned by `CoinbaseStreamingExchange`; port correlation, redaction, replay classification, structured errors, and stale-generation rejection from the derivatives modules; complete bounded pagination; formalize the v2 path; and validate deterministically with fixtures plus credential-gated smoke tests.

### Fixed decisions

* D1. Product identity: authoritative catalog from `GET /api/v3/brokerage/products` (with `product_type`, `contract_expiry_type`, `venue` preserved); map spot → `CurrencyPair`, futures → `FuturesContract`, perpetuals → `PerpetualContract` (XChange `org.knowm.xchange.derivative`); direct instrument↔product_id registry; explicit raw product-id escape hatch scoped per subscription, never global; ambiguous mappings rejected, not silently resolved.
* D2. Authentication: one non-reflective typed component in `xchange-coinbase` (e.g. `CoinbaseV3Authentication`) exposing both REST `ParamsDigest` and WS `Supplier<String>` from the same key material and claims logic; key validation at service init with sanitized errors; `CoinbaseWebsocketAuthentication` reflective path removed; stream accepts the typed component (or injectable supplier for tests) instead of `Class.forName`.
* D3. Dual sockets: `CoinbaseStreamingExchange` owns one market-data transport and one user-order transport, each with its own connection-generation counter; channel routing per endpoint; public-only usage never opens the user socket or requires credentials.
* D4. Replay policy: create/edit/convert/allocate operations are non-replayable after ambiguous transport results; reads and cancellations may retry only under documented replay-safe semantics; `client_order_id` preserved end-to-end (`Order.userReference` → `client_order_id`); structured unknown-outcome exception when reconciliation is inconclusive.
* D5. Error contract: a structured Coinbase v3 exception carrying provider code/message, HTTP status or WS operation, correlation id, retry classification, and sanitized details; rate-limit metadata parsed when provided; bounded jittered backoff for replay-safe operations only; no undocumented refill formulas.
* D6. Order book: on sequence gap, emit a dedicated gap signal and rebuild from REST snapshot; if rebuild fails, surface the gap to subscribers instead of continuing silently.
* D7. Validation model: deterministic fixture tests for everything; sandbox tests only for officially supported shapes, labeled as response validation; production smoke tests credential-gated, read-only, invariant-based, excluded from default CI.

### Implementation touchpoints

* `CoinbaseV3Digest` — refactor claims into shared builder; add typed WS JWT + REST digest under one interface; keep `JWT_EXPIRY_SECONDS = 120`.
* New `CoinbaseV3Authentication` (or equivalent in `v3/service`) — replaces `CoinbaseWebsocketAuthentication`; validated at construction; used by `CoinbaseBaseService` and both streaming transports.
* `CoinbaseStreamingExchange` — remove `attemptHelperJwtSupplier` reflection; open two `CoinbaseStreamingService` instances (market data + user) with per-endpoint URI constants; route `processProductSubscriptions` user channels to the user socket; resubscribe both on `connectionSuccess`.
* `CoinbaseStreamingService` — add connection generation, per-message correlation ids, stale-generation rejection, redacted logging (remove INFO full-payload logging incl. `Full subscribe payload`), JWT refresh tied to the user transport's generation; port `CoinbaseDerivativesStreamingService` patterns (replaySafe requests, protocol error subject, heartbeat/test).
* `CoinbaseStreamingMarketDataService` — replace `productIdOverride` with `CoinbaseProductIdentity` resolution; per-subscription product scoping; gap event emission in `OrderBookState#process`; keep `recoverFromSnapshot`.
* `CoinbaseStreamingTradeService` — keep delta dedup; add replay classification and structured unknown-outcome handling for WS-triggered operations; authenticate via typed contract.
* New `CoinbaseProductIdentity` (+ tests) — catalog from `listProducts`; instrument↔product_id registry; `CoinbaseProductIds` and `CoinbaseStreamingAdapters#toCurrencyPair` replaced/absorbed.
* `CoinbaseAccountServiceRaw#getCoinbaseAccounts`, `CoinbaseTradeService#getTradeHistory` — add no-progress/repeated-cursor guard; `CoinbaseTradeServiceRaw#listOrders` — add bounded high-level iteration honoring caller limits.
* `CoinbaseException` — extend to structured contract (code/type/message/correlation/retry classification) without breaking existing consumers; wire redactor.
* `CoinbaseSandboxEndpointMatrixIntegration` + fixtures — keep as sandbox validation; add deterministic WS fixtures for lifecycle tests.
* Docs: module README, `docs/` capability matrix, reference checklist for future exchange modules; `xchange-examples` coinbase demos updated.

### UX / workflow

1. Consumer builds a `CoinbaseStreamingExchange` with CDP credentials; connects.
2. Market-data subscriptions resolve product ids through the identity catalog; futures/perpetuals subscribe with native ids and emit XChange derivative instruments.
3. Private channels subscribe on the user socket; JWT refresh and reconnects are generation-scoped; stale events from a previous generation are rejected.
4. Ambiguous order placement surfaces a structured exception with correlation id and reconciliation guidance; nothing is replayed blindly.
5. Operator reads sanitized logs/errors with correlation ids; sandbox runs are distinguishable from production smoke runs.

### Requirements

**MVP (must have)**

* R1. Capability matrix checked in, covering every stable Advanced Trade REST endpoint + WS channel in scope, with implementation status, auth, pagination, fixtures, sandbox, smoke, and known limitations per row.
* R2. Product identity catalog with lossless spot/futures/perpetual mapping; global override removed; ambiguous mappings rejected.
* R3. Single typed REST+WS authentication contract; reflective fallback deleted.
* R4. Dual-socket lifecycle with generation-scoped reconnect/reauthenticate/resubscribe; public-only usage needs no credentials.
* R5. Request correlation, redaction, structured errors, replay classification ported to the stream; INFO logging no longer contains JWTs or full payloads.
* R6. Bounded, loop-safe pagination (accounts, fills, orders) with repeated-cursor detection.
* R7. Order-book gap signal + snapshot rebuild; dedup of user-order events by stable provider ids.
* R8. Documented + tested v2 migration path; reference checklist published.

**VNext (nice to have)**

* N1. REST rate-limit/retry metadata parsing with bounded backoff for replay-safe reads.
* N2. `STATUS`/`TICKER_BATCH`/`L2_DATA` channels in the matrix and lifecycle where stable.
* N3. Automated matrix drift check (endpoint inventory vs interface annotations) in CI.

### Concrete acceptance criteria

* AC1. Matrix accounts for every stable Advanced Trade REST endpoint and WS channel in scope and is reviewed against `CoinbaseAuthenticated`/`Coinbase`/`CoinbaseChannel`.
* AC2. `PARAM_PRODUCT_ID_OVERRIDE` has no production callers; futures/perpetual subscriptions preserve native product ids with no `CurrencyPair` coercion.
* AC3. REST and both WS transports authenticate through one typed component; no `Class.forName`/reflection in production code paths.
* AC4. `getCoinbaseAccounts`, `getTradeHistory`, and order-history iteration are bounded, complete, and loop-safe (repeated-cursor guard tested).
* AC5. Ambiguous placements are never replayed; reconciliation and structured unknown-outcome exceptions are tested.
* AC6. Error/log output is sanitized (JWT/bearer/key tests) and structured (code/type/correlation/retry classification).
* AC7. Dual-socket lifecycle test proves: separate endpoints, generation rejection of stale events, reconnect → reauth → resubscribe, heartbeat, order-book gap rebuild, event dedup.
* AC8. Advanced Trade vs derivatives module selection documented; v2 consumers have a documented, tested migration path.
* AC9. Targeted module build and repository-root `mvn -B clean install` pass.

### Out of scope

* OOS1. Derivatives gateway wire transport/auth reuse where the provider contract differs.
* OOS2. New XChange public API beyond the shared auth + product-identity components required here.
* OOS3. Implementing endpoints that the matrix records as unsupported with rationale.
* OOS4. v2 removal (decision deferred; see Open Questions).

## 7) New algorithms

Not needed for this feature. The product-identity mapper is a catalog/registry with explicit rejection of ambiguous entries, not a new algorithm; order-book recovery reuses the existing snapshot/delta pattern with an added gap signal; event dedup reuses the derivatives LRU + delta patterns already proven in this repo.

## 8) Success metrics

* User-visible outcome / adoption signal: futures/perpetual streaming works without the override param; new exchange module work references the Coinbase module as template.
* Operational / reliability signal: no JWT/key material in logs (redaction tests), stale-generation events rejected (tests), order-book gap rebuilds visible to subscribers, zero blind order replays.
* Validation / regression signal: deterministic fixture suites for auth, identity, pagination, replay, redaction, dual-socket lifecycle; sandbox vs production smoke classification documented; full root build green.

## 9) Rollout plan & Implementation Checklist

### Phase 1: capability matrix and product identity

1. [x] Publish the endpoint/capability matrix (from `CoinbaseAuthenticated`, `Coinbase`, `CoinbaseChannel`) as a checked-in doc; record status, auth, pagination, fixtures, sandbox, smoke, limitations per row. Verification: matrix reviewed against interface annotations; gaps flagged as implement-or-document-unsupported.
2. [x] Add `CoinbaseProductIdentity` catalog built from `CoinbaseMarketDataServiceRaw#listProducts` (product_type/contract_expiry_type/venue preserved); map spot → `CurrencyPair`, futures → `FuturesContract`, perpetuals → `PerpetualContract`; direct registry + per-subscription raw escape hatch; reject ambiguous mappings. Verification: unit tests for `BTC-USD`, `BTC-CFMF`, `BTC-PERP`-style ids, equal base/quote across venues, ambiguous rejection.
3. [x] Remove `PARAM_PRODUCT_ID_OVERRIDE` from `CoinbaseStreamingMarketDataService#resolveProductId` and `adaptTickers/adaptTrades/adaptCandles`; route through the identity mapper; retire `CoinbaseProductIds`/`CoinbaseStreamingAdapters#toCurrencyPair` 2-token logic. Verification: `CoinbaseStreamingMarketDataServiceTest` updated; override-based tests migrated.

### Phase 2: typed authentication and redaction

4. [x] Introduce the shared typed auth component in `xchange-coinbase` (REST `ParamsDigest` + WS JWT supplier from one key-validation path); replace `CoinbaseWebsocketAuthentication` usage. Verification: `CoinbaseV3DigestTest` + new auth-contract tests (key formats, invalid keys, injectable token creation).
5. [x] Delete `CoinbaseStreamingExchange#attemptHelperJwtSupplier` reflection; wire both sockets to the typed component. Verification: grep shows no `Class.forName`/reflective auth in production code; `CoinbaseStreamingExchangeTest` passes.
6. [x] Redact logs: remove INFO full-payload logging in `CoinbaseStreamingService#messageHandler` and `#sendChannelCommand`; apply `CoinbaseDerivativesRedactor`-style sanitization to errors. Verification: deterministic redaction tests (JWT/bearer/key fields); log inspection in tests.

### Phase 3: REST parity and pagination

7. [x] Complete bounded pagination: repeated-cursor/no-progress guard in `CoinbaseAccountServiceRaw#getCoinbaseAccounts` and `CoinbaseTradeService#getTradeHistory`; bounded high-level iteration for `CoinbaseTradeServiceRaw#listOrders`. Verification: unit tests with canned cursor sequences (progress, repeated cursor, limit cutoff).
8. [x] Close matrix gaps: implement or explicitly record as unsupported (with rationale) every stable endpoint found by the matrix. Verification: matrix rows match code; `CoinbasePublicEndpointsIntegration`/sandbox matrix reflect the final set.

### Phase 4: trading results, error policy, replay safety

 9. [x] Extend `CoinbaseException`/stream errors to the structured contract (code/type/message, status or WS op, correlation id, retry classification, sanitized details). Verification: error-shape fixture tests.
10. [x] Classify create/edit/convert/allocate as non-replayable after ambiguous transport; add bounded reconciliation by `client_order_id`/order id; surface structured unknown-outcome exception; document `Order.userReference` constraints. Verification: replay-classification unit tests proving no blind retry; integration smoke read-only checks.
11. [x] Parse rate-limit/retry metadata when provided; bounded jittered backoff for replay-safe operations only. Verification: backoff tests with canned rate-limit responses.

### Phase 5: dual WebSocket lifecycle

12. [x] `CoinbaseStreamingExchange` opens market-data and user transports (constants `MARKET_DATA_WS_URI`/`USER_ORDER_DATA_WS_URI`); channel routing per endpoint; public-only usage opens no user socket. Verification: `CoinbaseStreamingExchangeTest` dual-service wiring; connectivity integration.
13. [x] Port generation tracking, correlated requests, stale-generation rejection, and protocol-error subject from `CoinbaseDerivativesStreamingService` into `CoinbaseStreamingService`. Verification: `CoinbaseStreamingServiceTest` generation/stale-response cases.
14. [x] Reconnect → reauth (user transport) → resubscribe all active channels; heartbeat auto-subscribe preserved (`ensureHeartbeatsSubscription`, `PARAM_MANUAL_HEARTBEAT`). Verification: lifecycle tests with recorded reconnect sequences.
15. [x] WS-triggered trading operations carry the same replay classification as REST; pending user-channel requests fail on generation change. Verification: trade-service tests with disconnect mid-request.

### Phase 6: order-book recovery, deduplication, lifecycle hardening

16. [x] Emit a dedicated gap event from `OrderBookState#process` on sequence discontinuity; rebuild from `recoverFromSnapshot`; surface failure to subscribers (no silent continue). Verification: gap/rebuild/failure tests in `CoinbaseStreamingMarketDataServiceTest`.
17. [x] Event dedup by stable provider ids (order/trade/event id + version) bounded like the derivatives LRU; keep fill-delta semantics. Verification: duplicate/late-event tests; bounded-memory assertion.
18. [x] Make disconnect idempotent and liveness observables null-safe (`isAlive`, `connectionStateObservable`, `connectionIdle`). Verification: exchange lifecycle tests.

### Phase 7: legacy v2 migration, docs, reference checklist

19. [ ] Inventory v2 surface (`org.knowm.xchange.coinbase.v2.*`, `CoinbaseV2Authenticated`, `listV2Accounts/listV2AccountTransactions`, examples); mark deprecated entry points with v3 replacements; add migration docs + examples (credentials, product mapping, order placement, history pagination, streaming). Verification: migration doc review; compatibility tests for supported delegation.
20. [ ] Define the v2 removal release/window per repository compatibility policy (decision needed — see Open Questions). Verification: decision recorded in PRD/docs.
21. [ ] Publish the reference-module checklist (this PRD's conventions) in maintainers' docs; update module README. Verification: docs review.

### Phase 8: validation and rollout gates

22. [ ] Full regression: `mvn -B -pl xchange-coinbase,xchange-stream-coinbase,xchange-coinbase-derivatives,xchange-stream-coinbase-derivatives -am test`; targeted PMD; repository-root `mvn -B clean install`; derivatives non-regression suite. Verification: green builds; classification of sandbox vs production smoke per repo CI conventions.

## 10) Risks, dependencies, and edge cases

### Dependencies

* `xchange-stream-coinbase` depends on `xchange-coinbase` — auth/identity components must land in the REST module first.
* Official Advanced Trade API docs (attached to CF-447) for endpoint inventory; public `Coinbase` endpoints are 1s-cache — tests must account for cache semantics.
* No WS sandbox; lifecycle behavior validated via deterministic fixtures only.

### Risks

* Risk: identity mapper churn breaks existing spot consumers (override removal changes subscription behavior).
  Mitigation: catalog defaults preserve `BASE-COUNTER` mapping for spot; override removal is behavior-flagged in release notes.
* Risk: dual-socket split regresses reconnect/resubscribe (single-socket code today).
  Mitigation: port the derivatives generation logic; lifecycle tests before rollout.
* Risk: redaction changes break operator log greps.
  Mitigation: keep structured fields (correlation id, channel, product) visible; redact only secrets.
* Risk: structured `CoinbaseException` changes break existing callers.
  Mitigation: additive fields; keep `HttpStatusExceptionSupport` inheritance and message behavior.
* Risk: sandbox matrix tests misclassify reachability (4xx-as-reachable).
  Mitigation: keep classification documented and deterministic; do not promote sandbox checks to live validation.

### Edge cases

* Equal base/quote across distinct contracts/venues (e.g. same pair on spot vs futures).
* Products whose `product_id` does not split into exactly two tokens.
* Cursor pagination with repeated cursor, empty pages, or limit exactly at page boundary.
* JWT refresh racing with reconnect (generation change mid-refresh).
* Duplicate/late user-order events after terminal status (10-minute TTL today).
* Level2 `l2_data` channel-name normalization (`getChannelNameFromMessage`) vs `level2`/`level2_batch` subscriptions.
* Missing/invalid key material at stream init — must fail fast with sanitized error, not silent null-supplier fallback.

### Mitigation / rollback

* Ship per delivery slice (Phase 1 → Phase 8); each slice is independently testable.
* Keep `PARAM_PRODUCT_ID_OVERRIDE` as a deprecated no-op shim for one release if consumer migration requires it, then delete.
* Structured error additions are additive; rollback to `CoinbaseException`-as-is is a revert of one commit.
* WS lifecycle changes are gated behind the same `connect(ProductSubscription...)` entrypoint; no API change for consumers.

## 11) Open Questions

* Blocking: None

**Non-blocking**

1. Legacy v2 removal release window — which release removes `org.knowm.xchange.coinbase.v2.*` compatibility helpers, per repository compatibility policy and usage evidence? (Implementation-time decision; Phase 7 task 20.)
2. Endpoints found by the matrix pass that are stable but unimplemented (e.g. additional order/reporting or portfolio-hedge endpoints not yet in `CoinbaseAuthenticated`) — implement in this issue or record as unsupported with rationale? (Phase 3 task 8; default: record unsupported unless trivial.)
3. `STATUS`/`TICKER_BATCH`/`L2_DATA` channels — include in the lifecycle matrix now or track as VNext? (Default: include in matrix, wire only if stable and demanded by a use case.)