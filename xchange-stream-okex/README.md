# XChange OKX Stream

WebSocket streaming for OKX API v5 (`info.bitrich.xchangestream.okx`), REST counterpart
`xchange-okex` (package `org.knowm.xchange.okx`). Legacy `OkexStreamingExchange` remains as
a thin `@Deprecated` shim delegating to the canonical implementation.

## Three-socket lifecycle

OKX serves three WebSocket transports, each with its own URI:

| Transport | URI (prod) | Channels |
|---|---|---|
| Public | `wss://ws.okx.com:8443/ws/v5/public` | `tickers`, `trades`, `books`, `candles`, `funding-rate` |
| Private | `wss://ws.okx.com:8443/ws/v5/private` | `orders`, `positions`, plus WebSocket trading ops (`order`, `amend-order`, `cancel-order`) |
| Business | `wss://ws.okx.com:8443/ws/v5/business` | user-supplied channel subscriptions (for example candle-stick channels) via the custom channel handler |

`OkxStreamingExchange.connect()` establishes one socket per required transport, driven by
`TransportRole`:

- **Default model** — public + business, plus private when API credentials are configured.
- `setRequiredTransports(Set<TransportRole>)` overrides the default. `null` restores the
  default model; an **empty set** means public-only (suitable for market data without
  credentials); a non-empty set enables exactly those transports (private is only effective
  with credentials).

```java
OkxStreamingExchange exchange = (OkxStreamingExchange) ExchangeFactory.INSTANCE.createExchange(
    OkxStreamingExchange.class);
exchange.setRequiredTransports(EnumSet.of(TransportRole.PUBLIC, TransportRole.PRIVATE));
```

## Connection generations

Every `connect()` establishes a new **connection generation**. Events tagged with an older
generation are dropped at the boundary, so a reconnect can never leak stale state into a
fresh subscription. `disconnect()` increments the generation and tears down all sockets.

## Liveness and reconnect

`isAlive()` is **aggregate**: it is true only when every required transport socket is alive,
including the business socket. A dead business socket is not masked by healthy public/private
sockets. Reconnect applies bounded backoff per transport and, after re-establishment,
**resubscribes every active channel on every transport** (`resubscribeChannels()`), so
recovery is complete even when only one socket dropped.

## Order-book continuity

The `books` channel is validated through `OkxBookContinuity`:

- **Sequence gate** — per-instrument `seqId`: messages with `seqId <= last` are dropped
  (duplicates/stale); a gap triggers a full book rebuild.
- **Checksum** — OKX CRC32 checksum over the raw `price:size` levels (bids descending, asks
  ascending, no separator, unsigned) verified before the book update is committed; a
  mismatch triggers a rebuild (`resubscribeChannel`), never a corrupted book. Checksums of
  `0` or absent are skipped.

Rebuilds re-request the snapshot and re-apply the delta stream from the new sequence.

## Private-event deduplication

Private channels (`orders`, `positions`) deduplicate per channel with bounded
memory, so reconnect replays cannot double-report an order or position change. Fills surface
the OKX `tradeId` on `UserTrade`.

## WebSocket trading

`OkxStreamingTradeService` implements core `StreamingTradeService`: `placeLimitOrder`,
`placeMarketOrder`, `changeOrder`, and `cancelOrder`, all routed through one
`submitOrderRequest` path that correlates the response with the requested client order id
and raises typed exceptions (`OrderNotValidException` on rejection or clOrdId mismatch,
`ExchangeException` before login) instead of silent failure. The REST service remains the
source of truth for order placement reconciliation.

## Migration from `OkexStreamingExchange`

Replace `info.bitrich.xchangestream.okex` imports with `info.bitrich.xchangestream.okx` and
use `OkxStreamingExchange` directly; `OkexStreamingExchange` and its `OkexStreaming*Service`
getters delegate to the canonical implementation for source compatibility during the grace
period.
