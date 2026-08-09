# XChange Coinbase

Coinbase Advanced Trade v3 (spot, futures, and perpetuals) over the
`/api/v3/brokerage` REST API and the Advanced Trade WebSockets. The legacy v2 REST
surface is deprecated and scheduled for removal in the next major release; migrate
with [docs/coinbase-v2-to-v3-migration.md](../docs/coinbase-v2-to-v3-migration.md).

Authentication uses a typed `CoinbaseV3Authentication` component shared by REST and
WebSocket transports: the API key name is the exchange API key and the EC private key
PEM is the secret key. Invalid or missing key material degrades to public-only usage
with a sanitized warning, never a crash.

## Product identity

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
retry 429/5xx pages with bounded jittered backoff and abort on repeated cursors.

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
