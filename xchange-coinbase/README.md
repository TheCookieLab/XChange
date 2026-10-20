# XChange Coinbase

Coinbase Advanced Trade v3 (spot, futures, and perpetuals) over the
`/api/v3/brokerage` REST API and the Advanced Trade WebSockets. The legacy v2 REST
surface is deprecated and scheduled for removal in the next major release; migrate
with [docs/coinbase-v2-to-v3-migration.md](../docs/coinbase-v2-to-v3-migration.md).

Authentication uses a typed `CoinbaseV3Authentication` component shared by REST and
WebSocket transports: the API key name is the exchange API key and the EC private key
PEM is the secret key. Invalid or missing key material degrades to public-only usage
with a sanitized warning, never a crash.

## US CDE public funding observations

`CoinbaseCdeMarketData.create().getFundingHistory("BIPZ30", 2160, 0)` reads one
public website MDA page without account credentials. This separate raw interface
uses CDE website symbols, such as `BIPZ30`, `ETPZ30`, `SLPZ30` and `XPPZ30`;
resolve them against the website catalog rather than guessing a mapping from an
Advanced Trade product id. It belongs to US CDE, independently of the
`xchange-coinbase-derivatives` international gateway module.

The response retains `symbol`, `funding_rate`, `event_time` and the pagination echo.
`fundingRateDecimal()` preserves delivered decimal precision: `0.000022` means
`0.0022%`. No percent or annualized conversion is applied. Event timestamps are
preserved without shifting them to an assumed funding boundary. Missing rows and
missing metadata remain distinguishable from explicit zero observations.

This is an observed public website interface, **not a documented Advanced Trade
history endpoint or a verified settlement feed**. Source precision, finality,
revision rules, retention and interval-boundary semantics are unverified. Do not
feed it into an exact-settlement source without establishing those contracts.
The chart CSV rounds percentages to four decimal places; this interface reads the
JSON strings before that extra rounding. That does not establish upstream accuracy.
Offset paging beyond 2160 rows was observed; no total count or stable snapshot is
provided. Bound pagination, reconcile overlap by symbol/event time, and share
fetched pages across strategies. A missing hour is not proof of a zero funding rate.

Sources observed on 2026-10-20:

- [Public dashboard](https://www.coinbase.com/market-data/derivatives?funding_venue=cde)
  and its public `c_B4BC1ezr.js` module use
  `https://www.coinbase.com/api/mda/v1/instruments/CDE/{symbol}/funding?result_limit=2160`.
- The website catalog is `https://www.coinbase.com/api/mda/v1/assets/CDE`.
- [CDE REST history](https://docs.cdp.coinbase.com/api-reference/derivatives-api/rest-api/funding-rate/get-historical-funding-rates)
  is a separate `/rest/funding-rate` interface, queried by symbol and trading session date.
  [DCC authentication](https://docs.cdp.coinbase.com/api-reference/derivatives-api/rest-api/authentication)
  requires separate CDE credentials; it is not exposed by this public-only client.
- [CDE funding help](https://help.coinbase.com/en/derivatives/perpetual-style-futures/funding-rate)
  distinguishes hourly calculation from midday/end-of-day cash adjustments and
  says fully closed hours have no published rate.

`CoinbaseCdeRest.create().getHistoricalFundingRates(key, passphrase, signature,
timestampSeconds, "BIPZ30", "2026-10-19")` exposes the separate documented
institutional history route. Authentication headers are supplied explicitly by
the caller; this binding performs no credential lookup or signing. Its response
retains `symbol`, `funding_rate`, `future_mark_price`, `spot_mark_price`,
`fair_value_price` and `event_time` as source strings. A null trading session date
uses the provider's today. The schema defines no pagination or finality field;
do not infer UTC session bounds from the date or copy UDP flags onto these rows.
This route does not make DCC credentials a prerequisite for public MDA history.

For CF integration, published history maps to the separate
`CFPublishedFundingHistorySource` contract: preserve configured product id,
explicit CDE symbol, source URI, decimal rate and unchanged event time. Public
MDA rows, including the newest row, remain `PUBLISHED_UNCLASSIFIED`. Coalesce
strategy ranges per product before bounded pagination and share the result;
reject conflicting duplicates and pagination-budget exhaustion. No database is
required, and missing hours remain absent. This raw adapter does not install CF
runtime wiring or convert published rows into its exact `CFFundingRateSource`.

[CDE timing](https://help.coinbase.com/en/coinbase/derivatives/us-perpetual-futures-overview)
and the [position timing example](https://help.coinbase.com/en/derivatives/perpetual-style-futures/examples)
establish that a verified funding event at T applies to exposure at T, without
prior-hour proration or shifting it to T+1. Cash clearing occurs separately.
[UDP funding fields](https://docs.cdp.coinbase.com/derivatives/udp/incrementals#funding-rate)
distinguish projected and final rates with `IsFinal` and
`FinalFundingRateTimestamp`. Those fields are absent from MDA and REST history;
their equivalence to a final UDP event is not asserted by this client.

## Advanced Trade product identity

`CoinbaseProductIdentity` resolves provider product ids losslessly: spot → `CurrencyPair`,
futures → `FuturesContract`, perpetuals → `PerpetualContract` (suffix `PERP`). Configure
a catalog as `CoinbaseStreamingExchange.PARAM_PRODUCT_IDENTITY` (or
`CoinbaseExchange.PARAM_PRODUCT_IDENTITY` for REST); ambiguous mappings are rejected.
The global `PARAM_PRODUCT_ID_OVERRIDE` is deprecated and retained one release as an
escape hatch.

## Order placement and replay safety

`Order.userReference` maps to `client_order_id`. Create/edit/convert/allocate are never
blind-replayed: transport failures surface `CoinbaseUnknownOutcomeException`
(`RetryClassification.AMBIGUOUS`) carrying the `client_order_id`/order id for
reconciliation. `CoinbaseException` carries the provider error id/message, HTTP status,
and a retry classification (`AUTHENTICATION`/`TRANSIENT`/`RATE_CREDIT`/`PERMANENT`/
`AMBIGUOUS`). Read loops (`getCoinbaseAccounts`, `getTradeHistory`, `listOrdersBounded`)
retry 5xx pages with bounded jittered backoff and abort on repeated cursors.

## Rate limiting

`CoinbaseExchange.getDefaultExchangeSpecification()` enables
`CoinbaseRateLimitPolicy` (Advanced Trade private/public budgets, `EXECUTION` vs
`MARKET_DATA` classification per endpoint) on the core rate-limit context
described in the [root README](../README.md#rate-limiting). HTTP 429 is handled
there, once, for every wire attempt including pagination and metadata
initialization; module read loops do not retry it. Share one `RateLimitContext`
and user scope across every `CoinbaseExchange`/`CoinbaseStreamingExchange`
instance that uses the same Coinbase user. A 429 is treated as rejection; the
response body is not inspected. The rate-limited REST path supports the default
HTTP client only: custom SSL socket factories, hostname verifiers, OAuth and
custom proxy factories are rejected while the limiter is enabled.

## Streaming

`CoinbaseStreamingExchange` opens the market-data socket always and the private user
socket (`USER_ORDER_DATA_WS_URI`, override `Coinbase_User_WS_URI`) only when
credentials are usable. Public-only usage opens no user socket. Reconnects
reauthenticate and resubscribe; pending user-channel requests fail on generation
change; level2 sequence gaps emit `CoinbaseOrderBookGap` events; terminal-order dedup
is bounded by TTL.

## Parity and conventions

- Endpoint/capability matrix: [docs/coinbase-advanced-trade-capability-matrix.md](../docs/coinbase-advanced-trade-capability-matrix.md)
- Migration guide: [docs/coinbase-v2-to-v3-migration.md](../docs/coinbase-v2-to-v3-migration.md)
- Reference checklist and delivery conventions: PRD CF-447
  (`docs/prd/coinbase-advanced-trade-v3-parity.md`) — statuses, verification formats,
  and the implement-or-record-unsupported gap policy.
- Sandbox: REST sandbox probes use synthetic ids and treat 4xx as reachable; there is
  no WebSocket sandbox. Do not promote sandbox checks to production validation.
