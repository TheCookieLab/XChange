# XChange OKX

OKX API v5 integration (Spot, Margin, Swap, Futures, Options) with a canonical `Okx*` API and
deprecated `Okex*` compatibility shims. Maven coordinates are unchanged: `xchange-okex`
(REST) and `xchange-stream-okex` (WebSocket streaming).

## Products

`OkxExchange.remoteInit()` discovers instruments for every OKX instrument family without
credentials: Spot, Margin, Swap, Futures, and Options (Options are fetched per underlying,
as required by the public API). Instrument metadata preserves the native fields —
`instId`, `instType`, `instFamily`, `uly` (underlying), `ctVal`, `ctValCcy`, `expTime`,
`stk`, `listTime`, and friends — via `OkxInstrument`.

| Family | REST | Streaming | remoteInit |
|---|---|---|---|
| Spot | ✅ | ✅ | ✅ |
| Margin | ✅ | ✅ | ✅ |
| Swap | ✅ | ✅ | ✅ |
| Futures | ✅ | ✅ | ✅ |
| Options | ✅ | ✅ | ✅ (per underlying) |

## Capability matrix

Legend: ✅ implemented.

### Market data (public)

| Capability | Endpoint |
|---|---|
| Instruments | `/api/v5/public/instruments` ✅ |
| Underlyings | `/api/v5/public/underlying` ✅ |
| Ticker (single / all) | `/api/v5/market/ticker(s)` ✅ |
| Order book | `/api/v5/market/books` ✅ |
| Trades | `/api/v5/market/trades` ✅ |
| Candles (recent / history) | `/api/v5/market/candles` / `history-candles` ✅ |
| Funding rate (current / history) | `/api/v5/public/funding-rate(-history)` ✅ |

### Account, asset, trading (authenticated)

| Area | Endpoints |
|---|---|
| Account | balance, trade-fee, config, bills, bills-archive ✅ |
| Positions | positions, positions-history, account-position-risk, set-leverage, position margin-balance ✅ |
| Assets | currencies, balances, withdrawal, deposit-address, transfer, piggy-balance ✅ |
| Orders | place, place-batch, cancel, cancel-batch, amend, amend-batch, order, orders-pending, orders-history ✅ |
| Fills | fills, fills-history ✅ |
| Algo orders | order-algo, cancel-algos, amend-algos, orders-algo-pending, orders-algo-history, attached TP/SL ✅ |
| Sub-accounts | subaccount list, subaccount balances ✅ |

## Configuration

```java
ExchangeSpecification spec = new ExchangeSpecification(OkxExchange.class);
spec.setApiKey("...");
spec.setSecretKey("...");
spec.setExchangeSpecificParametersItem(OkxExchange.PARAM_PASSPHRASE, "...");
Exchange exchange = ExchangeFactory.INSTANCE.createExchange(spec);
```

| Parameter | Meaning |
|---|---|
| `OkxExchange.PARAM_PASSPHRASE` (`"passphrase"`) | OKX API passphrase (required for authenticated calls) |
| `OkxExchange.PARAM_SIMULATED` (`"simulated"`) | Demo trading: send `X-SIMULATED-TRADING: 1` on every request |
| `BaseExchange.USE_SANDBOX` | Use the OKX demo environment hosts |
| `OkxExchange.PARAM_USE_AWS` (`"Use_AWS"`) | **Deprecated** — ignored, retained for source compatibility |

Demo trading (see OKX "Demo Trading" docs):

```java
spec.setExchangeSpecificParametersItem(OkxExchange.PARAM_SIMULATED, "1");
```

## Endpoint policy and resilience

Rate limits are a typed, immutable policy (`OkxRateLimitPolicy`) instead of mutable statics:
`Okx.publicPathRateLimits` and `OkxAuthenticated.privatePathRateLimits` map each endpoint
path to a request budget, and `OkxResilience.createRegistries()` derives rate limiters from
them. Extension point for new endpoints:

```java
OkxResilience.registerRateLimiter(OkxResilience.createRegistries(), path, requests, seconds);
```

## Structured errors

API failures surface as `OkxException` with structured fields: provider error `code`,
`message`, the failing `domain` (public/account/asset/trade/…) and `endpoint`, a `requestId`
when the provider returns one, the `TransportState` at failure, and a `RetryClassification`
(replay-safe / no-retry / reconcile / rate-limited / transient / auth) so callers can decide
whether retrying is safe. Secrets are redacted from error messages and logs
(`OkxRedaction.mask` covers API keys, signatures, and passphrases).

## Order placement and replay safety

Order placement is **non-replayable** after an ambiguous transport result (timeout or 5xx).
`OkxTradeService` never blindly resubmits a placement: recovery is a bounded reconciliation
query by client order id (`clOrdId`), and a placed order found by that id is returned instead
of a duplicate placement. Reads and cancellations may retry because their request identity
makes replay safe. Batch placement reconciles per-order the same way.

## Typed pagination

Paged endpoints use `OkxPageParams` (limit clamped to the provider ceiling of 100) with
`OkxPageIterator` — a typed `before`/`after` cursor iterator that stops on empty, partial,
or repeated pages, unextractable cursors, and a configurable max-page ceiling.

## Streaming

WebSocket streaming lives in `xchange-stream-okex` (package
`info.bitrich.xchangestream.okx`); see its README for the three-socket lifecycle, order-book
continuity (sequence + checksum), private-event deduplication, and WebSocket trading.

## Migration from `Okex*`

The canonical implementation is `org.knowm.xchange.okx.*`. The legacy
`org.knowm.xchange.okex.*` types remain as thin `@Deprecated` shims that delegate to the
canonical implementation, so existing client code keeps compiling:

- `OkexExchange extends OkxExchange` (services delegate to canonical `Okx*Service`s).
- `Okex*Dto` wrappers delegate getters to their `Okx*` counterparts.
- `OkexException extends OkxException`.
- `OkexStreamingExchange extends OkxStreamingExchange`.
- `META-INF/services/org.knowm.xchange.Exchange` registers `OkxExchange` first, then
  `OkexExchange`, so `ExchangeFactory` picks the canonical implementation by default.

Migrate new code by replacing `okex` with `okx` in imports and using the `Okx*` types
directly; the shims will be removed after the documented grace period.
