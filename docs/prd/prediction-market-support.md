# PRD: CF-375 - XChange: prediction market support

*Project:* XChange
*Source issue:* [CF-375](https://linear.app/cookiefactory/issue/CF-375/xchange-prediction-market-support)
*Author:* Codex (automated)
*Status:* Ready
*Last updated:* 2026-07-29

---

## Status

* Lifecycle: `Ready`
* Blocking state: `None`
* Active phase: `Phase 1 - core and REST modules`
* Active task: `Add xchange-kalshi REST module`
* Overall: `3/11` checklist tasks complete

### Delivery log

* 2026-08-03 (feature/deliver-prd-cf-375-20260803-164858):
  * Task 1 done: locked `PredictionMarketContract` wire form `PRED/<provider>/[<eventId>/]<marketId>/<outcomeId>/<quoteCurrency>`; prefix dispatch added to `InstrumentDeserializer`/`InstrumentMapDeserializer` ahead of slash-count conventions. Round-trip tests cover currency pair, futures, options, and prediction-market forms.
  * Task 3 done: dependency decision requires no new managed versions. Kalshi RSA-PSS uses JDK `java.security` + managed `bcpkix-jdk18on` 1.84 for PEM/PKCS#8; Polymarket L2 HMAC uses JDK `javax.crypto`; Polymarket L1 EIP-712 uses managed `bcprov-jdk18on` 1.84 (Keccak-256, secp256k1). No Web3j.
  * Task 4 done: `PredictionMarketContract`/`PredictionOutcomeSide` added with Jackson support; metadata support is via provider metadata companions in provider modules (no core `InstrumentMetaData` change, per PRD's alternative). Gate: `mvn -B -pl xchange-core -am test` — 89 tests, 0 failures, 1 pre-existing skip.

## Execution Status

* Last updated: `2026-07-29 04:10 EDT`
* Codebase access: `confirmed`
* Repo / module path: `/Users/david/Workspace/XChange` (`main`, `b6a6fbc20f`)
* Fresh inputs integrated:
  * Linear issue `CF-375` description: evaluate whether prediction markets fit existing XChange exchange abstractions and whether a separate project is needed.
  * Linear pre-publish readback on 2026-07-29: issue in `Backlog`, project `XChange`, no comments, no linked PRD documents, no same-title PRD documents.
  * Official API docs checked on 2026-07-29: Polymarket API/auth/order surfaces and Kalshi auth/order/WebSocket surfaces.
* Evidence reviewed:
  * `README.md`
  * `pom.xml`
  * `xchange-core/src/main/java/org/knowm/xchange/Exchange.java`
  * `xchange-core/src/main/java/org/knowm/xchange/BaseExchange.java`
  * `xchange-core/src/main/java/org/knowm/xchange/ExchangeSpecification.java`
  * `xchange-core/src/main/java/org/knowm/xchange/instrument/Instrument.java`
  * `xchange-core/src/main/java/org/knowm/xchange/currency/CurrencyPair.java`
  * `xchange-core/src/main/java/org/knowm/xchange/derivative/FuturesContract.java`
  * `xchange-core/src/main/java/org/knowm/xchange/derivative/OptionsContract.java`
  * `xchange-core/src/main/java/org/knowm/xchange/utils/jackson/InstrumentDeserializer.java`
  * `xchange-core/src/main/java/org/knowm/xchange/utils/jackson/InstrumentMapDeserializer.java`
  * `xchange-core/src/main/java/org/knowm/xchange/service/marketdata/MarketDataService.java`
  * `xchange-core/src/main/java/org/knowm/xchange/service/trade/TradeService.java`
  * `xchange-core/src/main/java/org/knowm/xchange/service/account/AccountService.java`
  * `xchange-core/src/main/java/org/knowm/xchange/dto/Order.java`
  * `xchange-core/src/main/java/org/knowm/xchange/dto/trade/LimitOrder.java`
  * `xchange-core/src/main/java/org/knowm/xchange/dto/trade/MarketOrder.java`
  * `xchange-core/src/main/java/org/knowm/xchange/dto/marketdata/OrderBook.java`
  * `xchange-core/src/main/java/org/knowm/xchange/dto/meta/InstrumentMetaData.java`
  * `xchange-core/src/main/java/org/knowm/xchange/dto/meta/ExchangeMetaData.java`
  * `xchange-coinbase-derivatives/src/main/java/org/knowm/xchange/coinbasederivatives/CoinbaseDerivativesExchange.java`
  * `xchange-coinbase-derivatives/src/main/java/org/knowm/xchange/coinbasederivatives/CoinbaseDerivativesAdapters.java`
  * `xchange-coinbase-derivatives/src/main/java/org/knowm/xchange/coinbasederivatives/client/CoinbaseDerivativesJsonRpcTransport.java`
  * `xchange-stream-core/src/main/java/info/bitrich/xchangestream/core/StreamingExchange.java`
  * `xchange-stream-core/src/main/java/info/bitrich/xchangestream/core/StreamingMarketDataService.java`
  * `xchange-stream-core/src/main/java/info/bitrich/xchangestream/core/StreamingTradeService.java`
  * `docs/prd/coinbase-derivatives-gateway.md`
  * `scripts/ci/run-integration-module.py`
  * `scripts/ci/check-integration-health.py`
* Notes:
  * SDL/OpenViking repo tools were not exposed in this runtime; Java semantic search and exact file reads were used instead.
  * XChange already supports non-spot instruments via `Instrument`, `FuturesContract`, `OptionsContract`, and service methods that accept `Instrument`.
  * The gap is not `Exchange` itself. The gap is prediction-market-specific instrument identity, outcome semantics, and yes/no side-price conversion.

## Summary

Add first-class prediction-market support inside the XChange repository, not as a new project, by introducing a minimal core `PredictionMarketContract`/metadata model and two provider integrations: `xchange-kalshi`/`xchange-stream-kalshi` and `xchange-polymarket`/`xchange-stream-polymarket`. The generic `Exchange`, `MarketDataService`, `TradeService`, `AccountService`, and `StreamingExchange` shapes should remain the entrypoint, while provider-specific raw services and placement results preserve outcome tokens, event/market IDs, client order IDs, wallet/signature details, and regulatory or custodial constraints that do not fit the generic DTOs without loss.

## 1) Context

XChange is a Java library for consistent trading and market-data access across many cryptocurrency exchanges. The maintained fork publishes Maven artifacts under `com.github.thecookielab.xchange`, uses a multi-module parent reactor, and supports REST plus streaming modules where exchanges expose WebSocket APIs. Prediction markets matter now because Polymarket and Kalshi expose exchange-like order books, positions, and orders, but their instruments are event outcomes rather than base/counter spot pairs or derivatives on base assets.

The current `Exchange` contract is broad enough for the top-level integration: it returns `MarketDataService`, `TradeService`, and `AccountService`, and it supports `remoteInit()` metadata discovery. Existing generic service methods already prefer `Instrument` overloads while retaining deprecated `CurrencyPair` overloads. Existing derivatives work proves that provider modules can use generic contracts plus exchange-specific raw services when generic DTOs cannot preserve all provider semantics.

External API context:

* Polymarket exposes Gamma, CLOB, Data, Relayer, market/user WebSocket, RTDS, and sports WebSocket surfaces. Public market data can be read without credentials; private CLOB requests use wallet-controlled L1 typed-data credentials and L2 HMAC headers. Source: <https://docs.polymarket.com/getting-started/api>
* Polymarket order placement is CLOB-based and requires signed order payloads with fields such as owner/order type, side, token/outcome, price, size, and time-in-force. Source: <https://docs.polymarket.com/api-reference/trade/post-a-new-order>
* Kalshi authenticated requests use `KALSHI-ACCESS-KEY`, `KALSHI-ACCESS-TIMESTAMP`, and RSA-PSS request signatures. Source: <https://docs.kalshi.com/getting_started/quick_start_authenticated_requests>
* Kalshi event-market orders are posted to `/trade-api/v2/portfolio/events/orders` with ticker, side, count, fixed-point dollar price, time-in-force, self-trade prevention, and optional client order ID. Source: <https://docs.kalshi.com/api-reference/orders/create-order-v2>
* Kalshi WebSockets provide real-time order book changes, trades, market status, and fills behind an authenticated connection. Source: <https://docs.kalshi.com/getting_started/quick_start_websockets>

## 2) Problem / Opportunity

The existing abstraction can model the mechanical parts of prediction-market trading, but it cannot currently model the domain cleanly. Treating every prediction outcome as a fake `CurrencyPair` would make prices, sides, settlement, and market identity ambiguous. For example, Kalshi quotes event-market orders from the YES side; selling YES is economically related to buying NO but is not the same API operation. Polymarket trades outcome tokens through CLOB markets and wallet-signature flows. Both require preserving provider-native market IDs, outcome IDs, settlement status, and order-side transformations.

The opportunity is to add prediction-market support without fragmenting the project. XChange should remain the Java library home because this is still exchange connectivity, market data, order management, account state, and streaming. The right design is a small core prediction instrument plus provider-specific modules, not a separate project or a broad new base exchange hierarchy.

## 3) Goals

* G1. Keep prediction-market support in the XChange repository and Maven reactor.
* G2. Add a minimal, stable core instrument model for event/outcome contracts without breaking existing `CurrencyPair`, `FuturesContract`, or `OptionsContract` behavior.
* G3. Implement Kalshi REST and streaming modules with market data, account state, orders, fills, positions, authenticated signing, and deterministic fixtures.
* G4. Implement Polymarket REST and streaming modules with market discovery, CLOB order book/trading, positions, wallet/L2 auth, user channels, and deterministic fixtures.
* G5. Preserve provider-native identifiers and semantics through exchange-specific raw DTOs and result types wherever generic XChange DTOs would lose information.
* G6. Protect dependency convergence, Java 25 build health, vulnerability posture, and existing modules.

## 4) Non-goals

* NG1. Do not create a new top-level project outside XChange for the MVP.
* NG2. Do not replace the generic `Exchange` or service interfaces with a new prediction-only base exchange.
* NG3. Do not fake prediction markets as ordinary spot currency pairs in public API examples or metadata.
* NG4. Do not implement strategy logic, market-making logic, risk sizing, or opinionated event selection.
* NG5. Do not require live exchange credentials for unit tests or the normal Maven reactor.
* NG6. Do not add broad dependency upgrades such as Jackson 3 or RxJava 2 compatibility work unless the dependency gate explicitly approves it.

## 5) Users & Use cases

### Current state / evidence

* `Exchange` and `BaseExchange` already provide the correct lifecycle: specification, metadata, service initialization, `remoteInit()`, and service getters.
* `MarketDataService`, `TradeService`, and `AccountService` already have `Instrument`-based methods for ticker, order book, trades, order placement, order status, open orders, user trades, positions, and account state.
* `Instrument` is abstract and currently has concrete `CurrencyPair`, `FuturesContract`, and `OptionsContract` shapes. Prediction outcomes do not have a concrete instrument type today.
* `InstrumentDeserializer` and `InstrumentMapDeserializer` parse instruments by slash-count conventions; a prediction-market instrument must extend this safely without changing existing wire strings.
* `InstrumentMetaData` can carry fee, min/max amount, price/volume scale, steps, market-order availability, and contract value, but needs an extension path or provider-specific metadata for outcome IDs, event IDs, settlement states, and YES/NO constraints.
* `CoinbaseDerivativesExchange`, `CoinbaseDerivativesAdapters`, and `CoinbaseDerivativesTradeService` show the preferred module pattern: keep core types generic, register provider-discovered instruments, map provider names to `Instrument`, and expose raw placement details where needed.
* `StreamingExchange`, `StreamingMarketDataService`, and `StreamingTradeService` already support `Instrument` overloads, reconnect events, order-book streams, order changes, user trades, and positions.
* The parent `pom.xml` centralizes dependency versions and enforces dependency convergence, duplicate POM dependency bans, and reactor module convergence.
* `docs/prd/coinbase-derivatives-gateway.md` is the nearest delivered precedent for a provider module with strict auth, transport, instrument mapping, streaming lifecycle, deterministic tests, dependency gates, and docs.

### Primary user

Java trading-system developers and CF operators who want prediction-market market data and order lifecycle support through the same XChange patterns they already use for crypto and derivatives integrations.

### Key use cases

* UC1. Discover active prediction markets/events and load stable `Instrument` metadata without guessing from ticker text.
* UC2. Subscribe to live order books, trades, market lifecycle changes, user fills, and user orders for a prediction-market outcome.
* UC3. Place, cancel, query, and reconcile orders through generic `TradeService` where safe and provider-specific raw services where generic DTOs would lose outcome or idempotency semantics.
* UC4. Fetch account balances, positions, open orders, fills, and settled outcomes for downstream reconciliation.
* UC5. Run deterministic module tests and integration-health checks without live credentials.

### Existing touchpoints

* `org.knowm.xchange.Exchange`
* `org.knowm.xchange.BaseExchange#applySpecification`
* `org.knowm.xchange.BaseExchange#remoteInit`
* `org.knowm.xchange.ExchangeSpecification#setExchangeSpecificParametersItem`
* `org.knowm.xchange.instrument.Instrument`
* `org.knowm.xchange.derivative.FuturesContract`
* `org.knowm.xchange.derivative.OptionsContract`
* `org.knowm.xchange.utils.jackson.InstrumentDeserializer`
* `org.knowm.xchange.utils.jackson.InstrumentMapDeserializer`
* `org.knowm.xchange.dto.meta.InstrumentMetaData`
* `org.knowm.xchange.service.marketdata.MarketDataService`
* `org.knowm.xchange.service.trade.TradeService`
* `org.knowm.xchange.service.account.AccountService`
* `info.bitrich.xchangestream.core.StreamingExchange`
* `info.bitrich.xchangestream.core.StreamingMarketDataService`
* `info.bitrich.xchangestream.core.StreamingTradeService`
* `org.knowm.xchange.coinbasederivatives.CoinbaseDerivativesAdapters`
* `scripts/ci/run-integration-module.py`
* `scripts/ci/check-integration-health.py`

## 6) Proposed solution

### Summary

Keep the work inside XChange and add a narrow core `PredictionMarketContract` instrument plus provider modules. The generic service contracts remain the top-level user workflow, while Kalshi and Polymarket modules own their auth, transport, wire DTOs, adapter logic, and raw escape hatches. The implementation should prove that generic calls work for market data, order books, trades, account info, open orders, order placement, cancellation, user trades, positions, and streaming; provider-specific methods must expose native identifiers and side/outcome semantics that generic XChange cannot safely encode.

### Fixed decisions

* D1. Prediction-market support belongs in XChange for this MVP because it is exchange connectivity and shares XChange's module, service, DTO, streaming, build, and release model.
* D2. Do not create a new prediction-market base exchange hierarchy. Add provider modules that extend `BaseExchange` and implement the normal service interfaces.
* D3. Add one minimal core `PredictionMarketContract extends Instrument` or equivalently named type for event/outcome contracts. It should carry event identifier, market identifier, outcome identifier/name, quote currency, and provider symbol where needed.
* D4. Use provider-specific adapters to map YES/NO side, outcome token, price complement, settlement, and native IDs. Do not make `CurrencyPair` pretend to be a prediction outcome.
* D5. Build Kalshi as the first executable provider because API-key/RSA-PSS auth and event-market order semantics fit XChange's existing account/order/service pattern cleanly.
* D6. Build Polymarket with a stricter trading gate: public discovery and CLOB market data can land first, while private trading requires deterministic wallet typed-data signing, L2 HMAC signing, and secret redaction tests.
* D7. Add streaming modules only after the REST adapters and instrument identity are stable enough for reconnect/resubscribe logic.
* D8. Keep all new dependency versions in the root parent POM and run convergence and vulnerability gates before delivery.

### Implementation touchpoints

* `xchange-core/src/main/java/org/knowm/xchange/prediction/PredictionMarketContract.java` - new instrument type for event/outcome contracts.
* `xchange-core/src/main/java/org/knowm/xchange/prediction/PredictionOutcomeSide.java` - typed YES/NO or outcome-side enum used by adapters and optional params.
* `xchange-core/src/main/java/org/knowm/xchange/dto/meta/InstrumentMetaData.java` or a provider metadata companion - preserve min/max, price/amount steps, and provider-specific outcome metadata without breaking existing constructors.
* `xchange-core/src/main/java/org/knowm/xchange/utils/jackson/InstrumentDeserializer.java` - parse a documented prediction-market instrument string only when the new format is unambiguous.
* `xchange-core/src/main/java/org/knowm/xchange/utils/jackson/InstrumentMapDeserializer.java` - support metadata map keys for the same instrument string.
* `pom.xml` - register modules and centralize any new versions; do not add module-local unmanaged versions.
* `xchange-kalshi/pom.xml` and `xchange-polymarket/pom.xml` - REST modules depending on `xchange-core` and only approved auth/transport dependencies.
* `xchange-stream-kalshi/pom.xml` and `xchange-stream-polymarket/pom.xml` - streaming modules depending on their REST module, `xchange-stream-core`, and stream service modules.
* `KalshiExchange`, `KalshiAdapters`, `KalshiMarketDataService`, `KalshiTradeService`, `KalshiAccountService` - provider-owned REST implementation.
* `PolymarketExchange`, `PolymarketAdapters`, `PolymarketMarketDataService`, `PolymarketTradeService`, `PolymarketAccountService` - provider-owned REST implementation.
* `KalshiStreamingExchange`, `KalshiStreamingMarketDataService`, `KalshiStreamingTradeService` - authenticated WebSocket subscriptions and lifecycle.
* `PolymarketStreamingExchange`, `PolymarketStreamingMarketDataService`, `PolymarketStreamingTradeService` - CLOB market/user channel subscriptions and lifecycle.
* `scripts/ci/run-integration-module.py`, `scripts/ci/check-integration-health.py`, `.github/workflows/*.yaml` - add integration-health coverage only where the repo's exchange workflow pattern requires it.
* Module README/docs under `xchange-kalshi/`, `xchange-polymarket/`, and `docs/prd/` - document credentials, side semantics, settlement, and unsupported operations.

### UX / workflow

1. A developer adds `xchange-core` and the desired `xchange-kalshi` or `xchange-polymarket` module dependency.
2. They instantiate an exchange with `ExchangeFactory` and configure the normal `ExchangeSpecification` fields plus provider-specific auth parameters.
3. They call `remoteInit()` or provider discovery to populate `PredictionMarketContract` instruments.
4. They call generic `MarketDataService` methods with the discovered `PredictionMarketContract` for ticker/order book/trades.
5. They call generic `TradeService` for safe order placement/cancel/query flows and provider-specific raw methods for side/outcome/idempotency details.
6. If streaming is enabled, they add the stream module and subscribe with the same `PredictionMarketContract` identity.
7. Operators validate with deterministic module tests first, optional credentialed sandbox/demo integration tests second, and full reactor/dependency gates before release.

### Requirements

**MVP (must have)**

* R1. Core must include a prediction-market instrument identity that is not a fake `CurrencyPair` and can round-trip through Jackson metadata where supported.
* R2. Kalshi REST must support market discovery, order book/ticker/trades, account balance/positions, open orders, create/cancel/query orders, and user fills.
* R3. Polymarket REST must support market/event discovery, CLOB order book/prices/trades, user positions, authenticated order management, and raw order placement results after signing proof passes.
* R4. Provider adapters must map YES/NO side, price complement, count/size, outcome token/native ticker, fees, status, and settlement fields deterministically.
* R5. Generic XChange services must be usable for common read/order flows; raw services must expose provider-specific identifiers and unsupported semantics.
* R6. Streaming modules must support public order books/trades/tickers and authenticated user orders/fills with reconnect/resubscribe behavior.
* R7. All secrets, wallet private keys, API keys, HMAC secrets, RSA private keys, request signatures, and authorization headers must be redacted from exceptions/logs.
* R8. Tests must be deterministic and not require live credentials in the default Maven test path.

**VNext (nice to have)**

* N1. Multivariate/event-bundle support beyond simple binary YES/NO outcomes.
* N2. Provider-specific batch-order helpers for Kalshi event orders and Polymarket batch CLOB orders.
* N3. Historical settlement/result downloads and tax/accounting export helpers.
* N4. Optional `xchange-simulated` prediction-market matching engine for downstream strategy tests.

### Concrete acceptance criteria

* AC1. A `PredictionMarketContract` discovered from Kalshi and Polymarket can be used with generic `MarketDataService#getOrderBook(Instrument)`, `getTicker(Instrument)`, and `getTrades(Instrument)`.
* AC2. Generic order placement returns the primary provider order ID, while raw placement results expose provider-native market/outcome/order IDs and side conversions.
* AC3. Kalshi order placement maps `Order.OrderType.BID`/`ASK` into YES-side API semantics without silently treating NO exposure as a spot sell.
* AC4. Polymarket authenticated trading proves both L1 wallet credential derivation/signing and L2 HMAC request signing in deterministic tests.
* AC5. Streaming order-book tests prove snapshot/update application, reconnect, and resubscribe behavior without live WebSocket dependency.
* AC6. Parent POM convergence, duplicate dependency, and Java 25 compilation gates pass for the affected modules.
* AC7. Module docs clearly state unsupported operations, credential setup, side/outcome semantics, and settlement caveats.

### Out of scope

* OOS1. Trading strategy, pricing models, or automated market selection.
* OOS2. Exchange compliance onboarding beyond documenting required credentials and availability constraints.
* OOS3. Provider SDK wrapping as the primary implementation if it introduces dependency convergence or runtime-control risk.
* OOS4. A new top-level non-XChange library unless later evidence shows XChange consumers cannot use prediction-market contracts ergonomically.

## 7) New algorithms

### Why algorithm work is needed

Prediction-market providers represent event outcomes differently. Kalshi event markets quote from the YES side with bid/ask semantics, while Polymarket CLOB markets trade outcome tokens. A deterministic mapping is needed so generic XChange orders and order books remain correct and do not invert exposure, price, size, or settlement state.

### Problem framing

The mapping algorithm must translate between provider-native market/outcome/order representations and XChange generic DTOs while preserving enough raw metadata for exact reconciliation. It must answer: given a provider event/market/outcome plus generic order side, what is the XChange `Instrument`, displayed side, native side, native price, native size, and raw result?

### Inputs / outputs / invariants

* Inputs: provider event ID, market ID/ticker, outcome ID/token/name, YES/NO or outcome side, provider price/size/count fields, order type, time-in-force, client order ID/user reference, settlement and lifecycle fields.
* Outputs: `PredictionMarketContract`, `Ticker`, `OrderBook`, `Trade`, `LimitOrder`/`MarketOrder`, `OpenPosition`, `UserTrade`, raw provider result DTOs, and provider-native order requests.
* Hard invariants / constraints:
  * Do not create a `CurrencyPair` for an event outcome.
  * Preserve provider-native market and outcome IDs in raw DTOs and metadata.
  * Preserve `BigDecimal` precision for probability prices, fixed-point dollars, and fees.
  * Never silently complement price or flip side without an adapter test that names the provider rule.
  * Never retry ambiguous order placement unless the provider offers a verified idempotency key.
  * Secret material and signatures must not appear in logs, exceptions, or fixture snapshots.

### Candidate approach / research direction

* Add `PredictionMarketContract` with a stable string form such as `PRED/<provider>/<marketId>/<outcomeId>/<quoteCurrency>` or another explicitly documented format that cannot collide with current slash-count parsing.
* Keep provider display symbols/tickers in metadata and raw DTOs rather than overloading `Currency.getInstance` with event text.
* Add `PredictionMarketOrderParams` or provider-specific order flags only where generic `LimitOrder` fields are insufficient.
* Implement adapter truth tables for Kalshi YES-side bid/ask and Polymarket outcome-token buy/sell semantics.
* Use provider-specific raw services for batch orders, cancel-on-pause, post-only, self-trade prevention, wallet/relayer fields, and any settlement or order-group features.

### Failure modes / edge cases

* Incorrectly treating NO-side exposure as a generic ASK and reversing profit/loss.
* Losing provider market/outcome IDs during metadata serialization.
* Rounding probability prices incorrectly between cents, fixed-point dollars, and decimal probability formats.
* Failing to distinguish resting, partially filled, canceled, executed, settled, resolved, paused, and expired states.
* Retrying an ambiguous Polymarket or Kalshi placement when `client_order_id`/user reference semantics are not proven idempotent.
* WebSocket gap or reconnect replay causing stale order-book state or duplicate user fills.
* Adding provider SDKs that drag incompatible Jackson/RxJava/transitive dependencies into the reactor.

### Evaluation plan

* Unit-test every side/price/outcome mapping with table-driven fixtures for Kalshi and Polymarket.
* Round-trip `PredictionMarketContract` through Jackson value and metadata map deserialization.
* Replay deterministic order-book snapshot/update fixtures and assert sorted bid/ask books.
* Simulate ambiguous placement transport failures and prove the generic trade service does not auto-retry unsafe placements.
* Run `mvn -B -pl xchange-kalshi,xchange-polymarket -am test` during REST delivery, then add stream-module tests and full dependency/convergence gates before release.

### Open algorithm questions

* Q1. None.

## 8) Success metrics

* User-visible outcome / adoption signal: A Java user can discover Kalshi and Polymarket prediction-market instruments and use generic market-data/order APIs without custom provider glue for common workflows.
* Operational / reliability signal: No secret leakage, no ambiguous placement replay, no WebSocket silent-gap continuation, and deterministic fixtures cover REST plus streaming lifecycle.
* Validation / regression signal: Affected module tests, adapter truth tables, parent POM convergence, dependency audit, and full reactor gates pass before release.

## 9) Rollout plan & Implementation Checklist

### Phase 0: evidence and design lock

1. [x] Confirm `PredictionMarketContract` string identity, fields, and Jackson behavior in `xchange-core/src/main/java/org/knowm/xchange/prediction/PredictionMarketContract.java`, `InstrumentDeserializer`, and `InstrumentMapDeserializer`. Verification: focused unit tests cover currency pair, futures, options, and prediction-market round trips. (done 2026-08-03: `PredictionMarketContractTest`, `InstrumentDeserializerTest`)
2. [ ] Lock the provider side/price mapping truth tables for Kalshi and Polymarket in `KalshiAdaptersTest` and `PolymarketAdaptersTest`. Verification: tests name native side, generic side, outcome, input price, output price, and expected exposure.
3. [x] Decide the dependency set in root `pom.xml` for RSA/HMAC/wallet signing, refusing unmanaged module-local versions. Verification: `mvn -B -pl xchange-core -am validate` and dependency convergence rule pass. (done 2026-08-03: JDK crypto + already-managed BouncyCastle 1.84; no new versions)

### Phase 1: core and REST modules

4. [x] Add `PredictionMarketContract`, `PredictionOutcomeSide`, metadata support, and Jackson tests in `xchange-core`. Verification: `mvn -B -pl xchange-core test` passes with existing instrument regressions. (done 2026-08-03: metadata support via provider companions per PRD alternative; 89 tests green)
5. [ ] Add `xchange-kalshi` to the root reactor with `KalshiExchange`, auth signer, raw client, DTOs, adapters, `KalshiMarketDataService`, `KalshiTradeService`, and `KalshiAccountService`. Verification: `mvn -B -pl xchange-kalshi -am test` passes against deterministic fixtures.
6. [ ] Add `xchange-polymarket` to the root reactor with `PolymarketExchange`, Gamma/CLOB/Data/Relayer clients, wallet/L2 auth, DTOs, adapters, and services. Verification: `mvn -B -pl xchange-polymarket -am test` passes against deterministic fixtures.
7. [ ] Add provider metadata resources such as `xchange-kalshi/src/main/resources/kalshi.json` and `xchange-polymarket/src/main/resources/polymarket.json`. Verification: `remoteInit()` tests prove discovery populates instruments and currencies without fake currency pairs.

### Phase 2: streaming and lifecycle

 8. [ ] Add `xchange-stream-kalshi` with authenticated connection setup, public order-book/trade/status channels, user fills/orders, reconnect, and resubscribe. Verification: `mvn -B -pl xchange-stream-kalshi -am test` covers snapshot/update, auth headers, reconnect, and user fill mapping.
 9. [ ] Add `xchange-stream-polymarket` with CLOB market/user channels, RTDS where applicable, authenticated user updates, reconnect, and resubscribe. Verification: `mvn -B -pl xchange-stream-polymarket -am test` covers public market updates, authenticated order/trade updates, and gap handling.
10. [ ] Update module READMEs and examples for `xchange-kalshi`, `xchange-polymarket`, stream modules, and any relevant wiki/docs links. Verification: docs show credential setup, instrument identity, side semantics, unsupported operations, and sample generic service calls.

### Phase 3: validation, release readiness, and hardening

11. [ ] Run affected module tests, dependency convergence, vulnerability audit, integration-health checks, and the final root build path required by XChange release policy. Verification: commands and outputs are recorded in the delivery PR and no unrelated modules regress.

## 10) Risks, dependencies, and edge cases

### Dependencies

* Dependency: Kalshi RSA-PSS signing can use Java security APIs and/or existing BouncyCastle dependencies if needed; keep the selected path documented and tested.
* Dependency: Polymarket wallet EIP-712 signing may require Web3j or a narrowly scoped signing implementation. This must pass dependency convergence and vulnerability review before landing trading support.
* Dependency: Streaming modules depend on existing RxJava3 and stream-service Netty patterns; do not introduce RxJava2.
* Dependency: Provider public APIs and auth docs can change; implementation must pin fixture examples and include doc-reference dates.

### Risks

* Risk: A too-generic abstraction hides YES/NO semantics and causes incorrect exposure. Mitigation: add explicit prediction-market instrument and adapter truth tables.
* Risk: Provider SDK dependencies could drag incompatible Jackson or reactive versions into the reactor. Mitigation: prefer local HTTP/WebSocket clients or tightly audited dependencies in parent POM.
* Risk: Polymarket wallet/private-key handling creates secret leakage risk. Mitigation: redaction tests and no raw secret material in exception messages.
* Risk: WebSocket reconnect gaps silently corrupt order books. Mitigation: surface gap exceptions and require REST resync after sequence uncertainty.
* Risk: Legal/geographic availability differs by provider and user jurisdiction. Mitigation: docs must state provider availability constraints and leave compliance decisions to the application/operator.

### Edge cases

* Edge case: Multi-outcome markets with more than YES/NO require outcome IDs rather than boolean-only modeling.
* Edge case: Kalshi NO exposure may need a provider-specific helper rather than relying on generic ASK semantics alone.
* Edge case: Polymarket outcome tokens can have market lifecycle states that differ from CLOB order status.
* Edge case: Partial fills, settlement, canceled/paused markets, and resolved events must be represented without flattening into ordinary crypto balance changes.
* Edge case: `Order.userReference`/client IDs must not be assumed idempotent until proven provider by provider.

### Mitigation / rollback

* Mitigation: land core prediction instrument support behind additive APIs and tests before provider modules.
* Mitigation: keep all provider-native fields in raw DTOs and raw placement/query result types.
* Mitigation: require deterministic fixtures for every signed request and streaming update path.
* Rollback: remove new modules from the root reactor and published docs if provider implementation is not ready; keep core `PredictionMarketContract` only if it remains fully tested and unused behavior is harmless.

## 11) Open Questions

**Blocking**

* Blocking: None

**Non-blocking**

* Non-blocking: None
