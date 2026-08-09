# Coinbase v2 → Advanced Trade v3 migration guide

Status: **v2 surface deprecated; removal scheduled for the next major release.**

## Why migrate

Coinbase's `api.coinbase.com/v2` REST surface is legacy. The XChange `xchange-coinbase`
module is now built entirely on the Coinbase Advanced Trade API (`/api/v3/brokerage`)
for trading, market data, and account balance discovery. The only remaining v2 code is a
small compatibility surface used for deposit/withdrawal discovery.

## Inventory of the remaining v2 surface

| Symbol | Purpose | v3 replacement |
|---|---|---|
| `org.knowm.xchange.coinbase.v2.CoinbaseV2Authenticated` | v2 REST client | `CoinbaseAuthenticated` (v3) |
| `CoinbaseAccountServiceRaw#listV2Accounts` | discover v2 account ids | `getCoinbaseAccounts()` for balances; no v3 account-id discovery needed for trading |
| `CoinbaseAccountServiceRaw#listV2AccountTransactions` | deposit/withdrawal/transfer history | none yet in Advanced Trade v3; track provider parity |
| `org.knowm.xchange.coinbase.v2.dto.*` (9 DTOs) | v2 wire types | `org.knowm.xchange.coinbase.v3.dto.*` |

All v2 symbols are `@Deprecated` and will be removed in the next major release. Do not
introduce new uses.

## Credentials

v2 and v3 use the same API key/secret pair from the Coinbase CDP console
(Advanced Trade API access). The v3 path validates the key material once through
`CoinbaseV3Authentication`; WebSocket channels reuse the same typed component.

```java
ExchangeSpecification spec = new ExchangeSpecification(CoinbaseExchange.class);
spec.setApiKey(apiKeyName);
spec.setSecretKey(ecPrivateKeyPem);
Exchange exchange = ExchangeFactory.INSTANCE.createExchange(spec);
```

## Product mapping

v3 resolves product ids losslessly: spot `BTC-USD` → `CurrencyPair.BTC/USD`,
futures (`BTC-CFMF`-style) → `FuturesContract`, perpetuals (`BTC-PERP`-style) →
`PerpetualContract`. Configure a `CoinbaseProductIdentity` catalog (built from
`listProducts`) as `PARAM_PRODUCT_IDENTITY` on the exchange specification; ambiguous
mappings are rejected rather than guessed. The old global `PARAM_PRODUCT_ID_OVERRIDE`
is deprecated and only retained one release as an escape hatch.

## Order placement and replay safety

- `Order.userReference` maps to `client_order_id`. It identifies an order; it is not
  an idempotency key by itself.
- Create/edit/convert/allocate requests are **never blind-replayed**: a transport
  failure after the request may have reached the provider raises
  `CoinbaseUnknownOutcomeException` (`RetryClassification.AMBIGUOUS`); reconcile by
  `client_order_id`/order id before retrying.
- Read operations (accounts, fills, order history) are retried by `CoinbaseRetry`
  with bounded jittered backoff, and only for `RATE_CREDIT`/`TRANSIENT`
  classifications.

## History pagination

All cursor loops are guarded: a repeated cursor or a runaway page count aborts with
`ExchangeException` instead of looping or silently truncating. High-level iteration:
`getTradeHistory(params)` (fills) and `listOrdersBounded(limit)` (orders).

## Streaming

`CoinbaseStreamingExchange` opens the market-data socket and, only when credentials
are usable, the private user socket (`USER_ORDER_DATA_WS_URI`). Public-only usage
opens no user socket. Reconnects reauthenticate (fresh JWT) and resubscribe all
active channels; pending user-channel requests fail with an `AMBIGUOUS`-classified
exception on generation change instead of hanging; level2 sequence gaps emit
`CoinbaseOrderBookGap` events (recovered or not) instead of being silently swallowed.

## Capability matrix

Every REST/WS endpoint, its auth mode, fixtures, sandbox status, and limitations is
recorded in [docs/coinbase-advanced-trade-capability-matrix.md](coinbase-advanced-trade-capability-matrix.md).
Gaps are implement-or-record-unsupported; the matrix is the source of truth for parity.
