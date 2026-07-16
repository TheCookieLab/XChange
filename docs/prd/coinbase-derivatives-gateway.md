# Coinbase Derivatives Gateway for XChange

- Status: Delivered
- Priority: Urgent
- Target approval: July 17, 2026
- Hard external deadline: September 9, 2026

## Summary

Add dedicated `xchange-coinbase-derivatives` and
`xchange-stream-coinbase-derivatives` modules for Coinbase Advanced international
derivatives on the new Deribit-powered Starbase gateway. The implementation uses
XChange core contracts but owns Coinbase-specific JSON-RPC 2.0 transport,
authentication, wire DTOs, error handling, and streaming lifecycle.

This is a new integration, not a modification of `xchange-coinbase` spot or
`xchange-deribit`. Coinbase's gateway has Coinbase CDP authentication and
Coinbase-managed account semantics even though its protocol resembles Deribit.
Reusing Deribit's authenticated transport would create incorrect trust and
compatibility assumptions.

## Delivery Checklist

- [x] Register the REST and streaming modules in the root reactor.
- [x] Implement the Coinbase-owned JSON-RPC HTTP transport and authentication lifecycle.
- [x] Implement market data, account, position, order, and fill adapters and services.
- [x] Implement the raw placement result contract and non-replay placement semantics.
- [x] Implement the JSON-RPC WebSocket lifecycle and public/private streaming services.
- [x] Add deterministic transport, numeric, service, authentication, and lifecycle tests.
- [x] Document configuration, label semantics, cancel-on-disconnect, and recovery behavior.
- [x] Run affected-module builds and the repository-root `mvn -B clean install` gate.
- [x] Complete final QA, exact-head PR monitoring, merge, and post-merge verification.

Implementation evidence (July 16, 2026): REST module tests 19/19, streaming
module tests 15/15, targeted PMD 0 violations across production and test sources,
and the 109-project root `mvn -B clean install` reactor completed successfully.

## Context

Coinbase will hard-cut international derivatives from the existing INTX APIs to
the new gateway on September 9, 2026. There is no parallel production window.
At cutover, resting orders are cancelled, open positions are settled and
recreated, realized PnL and funding are crystallized, and average entry prices
reset. Existing integrations that have not migrated stop trading.

Official references:

- [International Derivatives Overview](https://docs.cdp.coinbase.com/coinbase-app/advanced-trade-apis/guides/derivatives/overview)
- [Technical Migration Guide](https://docs.cdp.coinbase.com/coinbase-app/advanced-trade-apis/guides/derivatives/technical)
- [Advanced Trade API reference](https://docs.cdp.coinbase.com/api-reference/advanced-trade-api/rest-api)

## Goals

1. Provide first-class REST/private parity for perpetual futures through
   XChange's `MarketDataService`, `AccountService`, and `TradeService` contracts.
2. Provide public and private WebSocket services with correct authentication,
   heartbeat, reconnect, subscription, and rate-credit behavior.
3. Preserve decimal precision by mapping every wire numeric to `BigDecimal`.
4. Expose exchange-specific results where the generic XChange contract cannot
   represent Coinbase semantics without data loss.
5. Keep Coinbase spot and existing Deribit behavior unchanged.
6. Give downstream systems enough correlation data to reconcile orders and
   fills without treating Coinbase's `label` as idempotent.

## Non-Goals

- Changing `xchange-coinbase`, `xchange-stream-coinbase`, `xchange-deribit`, or
  `xchange-stream-deribit` behavior.
- Providing application-level portfolio ownership, risk controls, or strategy
  reconciliation.
- Hiding the hard cutover by carrying live exposure through September 9.
- Treating `Order.userReference` or the wire `label` as unique or idempotent.
- Shipping options or dated-futures trading in the initial CF migration path.
  DTOs and instrument modeling should remain extensible for those products.

## Module Layout

### `xchange-coinbase-derivatives`

- Exchange specification and metadata
- JSON-RPC 2.0 HTTP transport
- CDP JWT and gateway access-token lifecycle
- Coinbase-namespaced request, result, notification, and error DTOs
- Market data, account, and trade services
- Adapters between wire DTOs and XChange core types
- Exchange-specific raw services and placement results

### `xchange-stream-coinbase-derivatives`

- JSON-RPC 2.0 WebSocket transport
- Public/private subscription services
- Session authentication and proactive reauthentication
- Heartbeat request handling
- Reconnect, resubscribe, request correlation, and credit-aware backoff
- Market data and user-stream adapters

The modules may share narrowly scoped package-private utilities through the REST
module where normal XChange module dependency patterns permit it. Do not move
Coinbase wire DTOs into `xchange-core`.

## Endpoints and Protocol

- HTTP: `https://drb.coinbase.com/api/v2`
- WebSocket: `wss://drb.coinbase.com/ws/api/v2`
- Envelope: JSON-RPC 2.0 with `jsonrpc`, unique request `id`, `method`, and
  `params`; responses contain either `result` or a structured `error` associated
  with the request ID.

Every request must have a correlation ID generated by the transport. The
transport rejects missing IDs, mismatched IDs, simultaneous result/error
payloads, malformed errors, and type-incompatible results as exchange failures.
WebSocket request IDs must remain unique within a connection generation so a
late response from a prior connection cannot complete a new request.

## Authentication

1. Generate a fresh CDP JWT for each `public/auth` exchange. JWTs are short
   lived and must never be logged.
2. Send `grant_type=coinbase_cdp` and the signed JWT in the POST body.
3. For HTTP, retain the returned access token in memory and send it as a bearer
   token on private calls.
4. Refresh the HTTP access token proactively before the documented 15-minute
   lifetime, with single-flight refresh so concurrent requests do not stampede.
5. For WebSocket, call `public/auth` after connect and reauthenticate the same
   session proactively before its approximately 50-minute expiry.
6. On an authentication rejection, invalidate the token/session exactly once,
   reauthenticate, and retry only operations whose replay semantics permit it.

Secrets, JWTs, access tokens, authorization headers, private request bodies, and
CDP key identifiers must be redacted from exceptions and logs.

## Instrument and Numeric Mapping

- Map names such as `BTC_USDC-PERPETUAL` to XChange `FuturesContract` instances.
- Discover instruments from `public/get_instruments`; do not synthesize an
  authoritative catalog from symbol text alone.
- Preserve the native instrument name in exchange-specific metadata.
- Map price, amount, contracts, fees, PnL, margin, collateral, funding, greeks,
  and rate-credit values to `BigDecimal` without intermediate `double` parsing.
- Serialize wire numerics as JSON numbers while retaining the source decimal's
  exact value and scale where the XChange DTO permits it.
- Reject non-finite values and malformed numeric payloads.

## Service Scope

### Market Data

- Instruments and contract metadata
- Ticker
- Full and incremental order book
- Recent and streaming trades
- Candles/charts with supported periods
- Funding, index, and mark data needed by futures consumers

### Account

- Account/portfolio summary and collateral
- Margin model and available funds
- Open positions with signed size, direction, average entry, mark, liquidation,
  realized/unrealized PnL, and funding where available
- Private position and portfolio notifications

### Trading

- Market and limit orders
- Stop-market and stop-limit orders
- Reduce-only placement
- Cancellation by order ID and cancel-all by instrument
- Open-order and order-history lookup
- User trades/fills
- Private order and fill notifications

Order placement maps `Order.userReference` to Coinbase's `label`. Javadoc and
the module guide must state that labels are neither unique nor idempotent. The
generic `TradeService` must not automatically replay an ambiguous placement
solely because the same label can be reused.

## Placement Result Contract

The generic `TradeService` returns the primary exchange order ID. An
exchange-specific raw placement method returns an immutable result containing:

- Primary order ID
- Related order IDs
- Request correlation ID
- Accepted instrument, side, type, amount, price, reduce-only flag, and label
- Raw provider status needed for diagnostics

This is required because a triggered stop-limit flow creates distinct pre- and
post-trigger order IDs. The primary ID follows the provider's documented parent
or initial-order semantics; related IDs retain the complete relationship.

## Error and Retry Semantics

- Map JSON-RPC errors into a structured Coinbase derivatives exception carrying
  code, message, request ID, method, retry classification, and sanitized details.
- Authentication expiry may trigger one single-flight reauthentication attempt.
- Public reads may retry bounded transient transport failures with jittered
  backoff.
- Private reads and idempotent cancellations may retry only when provider
  semantics make replay safe.
- Placement must not blindly retry after an ambiguous transport failure because
  `label` is not an idempotency key.
- The gateway can disconnect clients when rate credits are exhausted instead of
  returning HTTP 429. Parse available-credit metadata, pace requests before
  exhaustion, classify credit disconnects, and reconnect only after bounded
  credit-aware backoff.
- Preserve enough sanitized request fingerprint data for downstream recovery
  without logging secrets.

## WebSocket Lifecycle

1. Connect and establish a connection-generation ID.
2. Authenticate before private requests or subscriptions.
3. Configure and answer protocol heartbeat/test requests.
4. Track request futures by generation and JSON-RPC ID.
5. Subscribe to requested public/private channels.
6. On disconnect, fail or retain pending requests according to replay safety,
   apply credit-aware backoff, reconnect, reauthenticate, and resubscribe.
7. Deduplicate replayed notifications using stable provider event/order/trade IDs.
8. Surface unrecoverable gaps to subscribers instead of silently continuing from
   an unknown sequence.

Cancel on Disconnect should be exposed as an explicit exchange-specific option,
disabled by default. Enabling it must be visible in the exchange specification
and connection logs because it changes order side effects.

## Compatibility

- No changes to existing Coinbase spot endpoints, DTOs, authentication, or
  services.
- No changes to existing Deribit endpoints, authentication, DTOs, or services.
- Use XChange core types and established service interfaces without adding core
  API solely for Coinbase. Add exchange-specific contracts inside the new module
  when generic contracts cannot preserve provider data.
- Root POM and module-list changes are limited to registering the two modules and
  shared dependency management required by them.

## Test Plan

Use deterministic HTTP and WebSocket fixtures; tests must not depend on live
Coinbase availability.

- JSON-RPC request/result/error correlation, including late and mismatched IDs
- CDP JWT generation boundary, redaction, access-token single-flight refresh,
  HTTP expiry retry, and WebSocket reauthentication
- Exact `BigDecimal` round trips for small sizes, high prices, fees, rates, and
  scientific-notation inputs where accepted
- Instrument discovery and `BTC_USDC-PERPETUAL` `FuturesContract` mapping
- Label round trips and explicit proof that duplicate labels are not deduplicated
- Market, limit, trigger, and reduce-only request mapping
- Stop-limit placement with primary and related order IDs
- Open/history order lookup and user-trade/fill adaptation
- Heartbeat response, disconnect, resubscription, event deduplication, stale
  connection-generation response rejection, and rate-credit recovery
- Coinbase spot and Deribit module non-regression tests

Required gates:

1. Affected-module unit and integration builds while iterating.
2. `mvn -B clean install` from the repository root before handoff.

## Delivery Milestones

| Milestone | Target | Exit criteria |
| --- | --- | --- |
| PRD approval | July 17, 2026 | API ownership, module boundaries, raw placement contract, and retry policy accepted |
| REST/private parity | August 7, 2026 | Instruments, market data, accounts, positions, orders, history, and fills pass deterministic fixtures |
| Streaming parity | August 14, 2026 | Public/private channels, auth refresh, heartbeat, reconnect, and credit recovery pass fixtures |
| CF adapter integration | August 21, 2026 | CF consumes the new modules without changing spot or Deribit paths |
| Shadow/canary validation | August 24-28, 2026 | Read-only shadow plus supervised minimum-size canary proves order/fill/position reconciliation |
| Voluntary old-gateway exit | September 1, 2026 | Old-gateway orders cancelled, CF exposure flat, archived evidence complete |
| Coinbase hard cutover | September 9, 2026 | No CF exposure or resting orders depend on forced migration semantics |

## Acceptance Criteria

1. Both modules build and install in the root reactor.
2. All listed REST/private and streaming capabilities have deterministic fixture
   coverage.
3. Authentication refresh is proactive, single-flight, redacted, and tested.
4. Wire numerics reach XChange consumers as exact `BigDecimal` values.
5. `Order.userReference` round-trips through `label` with prominent
   non-idempotency documentation.
6. Generic placement returns the primary ID and raw placement retains every
   related ID.
7. Ambiguous placements are surfaced without blind retry.
8. Credit exhaustion, heartbeat failure, reconnect, and notification gaps are
   explicit and tested.
9. Existing Coinbase spot and Deribit suites remain green.
10. CF completes shadow/canary validation and voluntarily cancels/flattens the
    old gateway by September 1; it does not carry exposure through forced
    cutover.

## Risks and Mitigations

- **No parallel production window:** finish the adapter early and use fixtures,
  shadow reads, and a supervised canary before September.
- **Label ambiguity:** retain exchange order/trade IDs and request correlation;
  never use label alone for deduplication or retry.
- **Authentication drift:** isolate auth behind tested token providers and keep
  expiry policy configurable within documented bounds.
- **Rate-credit disconnects:** pace proactively and make reconnect backoff aware
  of provider credit state.
- **Stop-limit identity changes:** expose related order IDs and test trigger
  transitions.
- **Cutover accounting reset:** require CF to flatten voluntarily before the
  deadline and establish a fresh post-cutover reconciliation baseline.

## Open Decisions for Approval

1. Confirm the provider's primary-ID convention for stop-limit placement and
   triggering.
2. Confirm which order-history pagination cursor/sequence guarantees are stable
   enough for loss detection.
3. Confirm rate-credit fields and replenishment timing for HTTP and WebSocket.
4. Confirm whether Cancel on Disconnect can be scoped per connection,
   subaccount, or portfolio.
5. Confirm sandbox or certification-environment availability and production
   entitlement timing.
