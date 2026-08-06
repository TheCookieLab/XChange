# XChange Kalshi Stream

Kalshi streaming over the trade-api v2 WebSocket. Subscriptions take the same
`PredictionMarketContract` identity as the REST module
(`PRED/kalshi/[<eventTicker>/]<marketTicker>/YES/USD`); generic `CurrencyPair`
calls are rejected before any subscription is attempted.

The default endpoint is `wss://external-api-ws.kalshi.com/trade-api/ws/v2`.
Override `KalshiStreamingExchange.PARAM_WS_URI` (`kalshi.ws.uri`) to target the
demo environment.

## Credentials

The handshake reuses the REST RSA-PSS signing rule: `apiKey` is the API key id
and `secretKey` the unencrypted PKCS#8 RSA private key. Kalshi authenticates
every WebSocket session — public market-data channels included — so credentials
are **mandatory**: `KalshiStreamingExchange.applySpecification` (and the
`KalshiStreamingService` constructor) fails fast with an
`ExchangeSecurityException` naming the missing `apiKey` / `secretKey`
parameter. Public channels (order book, trades, ticker, market lifecycle)
remain subscribeable, but only over the authenticated session.

## Channels and side semantics

* `getOrderBook` — server snapshot followed by sequenced deltas. The
  subscription requests `use_yes_price: true`, so both YES and NO levels arrive
  on the unified yes-leg price scale: YES levels are generic bids, NO levels
  are generic asks at their reported yes-leg price (a no-side level that would
  historically arrive at no-leg $0.30 arrives as $0.70). No complement
  conversion is applied; the REST `RULE_NO_BID_COMPLEMENT` applies to the REST
  order-book surface only. A sequence gap — or a delta before any snapshot —
  terminates the stream with an `ExchangeException` telling you to resync over
  REST; the module never silently continues on uncertain state.
* `getTrades` / `getTicker` — public trades and top-of-book.
* `getMarketLifecycle` — raw `market_lifecycle_v2` status events
  (created/activated/determined/settled/...) exposed unmapped, since no generic
  DTO models settlement.
* `getUserTrades` / `getOrderChanges` — credentialed fills and order-state
  updates. Fill sides follow `RULE_BOOK_SIDE_DIRECTION`: `buy NO at q` reads as
  ASK YES at `1 - q`, `sell NO` as BID YES at `1 - q`.

Routing binds each subscription to the server-assigned `sid` from the
subscription acknowledgement, and live subscriptions are re-sent automatically
on reconnect.

## Sample

```java
ExchangeSpecification spec =
    new ExchangeSpecification(KalshiStreamingExchange.class);
spec.setApiKey("<api key id>");
spec.setSecretKey("<unencrypted PKCS#8 RSA private key PEM>");
StreamingExchange exchange = StreamingExchangeFactory.INSTANCE.createExchange(spec);
exchange.connect().blockingAwait();

Instrument contract =
    new PredictionMarketContract("kalshi", null, "KXSB-26", "YES", Currency.USD);
Disposable subscription =
    exchange
        .getStreamingMarketDataService()
        .getOrderBook(contract)
        .subscribe(book -> System.out.println(book));
// ... exchange.disconnect().blockingAwait();
```

## Unsupported operations

* `CurrencyPair`-typed calls on every streaming service.
* Placement/cancellation over the socket (use the REST `xchange-kalshi` trade
  service).
