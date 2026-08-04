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
and `secretKey` the unencrypted PKCS#8 RSA private key. Without credentials the
connection is anonymous and only the public channels (order book, trades,
ticker, market lifecycle) may be subscribed; the user streams throw
`ExchangeSecurityException`.

## Channels and side semantics

* `getOrderBook` — server snapshot followed by sequenced deltas. YES levels are
  generic bids; NO bids are generic asks at `1 - noPrice` (the dollar form of
  `RULE_NO_BID_COMPLEMENT`). A sequence gap — or a delta before any snapshot —
  terminates the stream with an `ExchangeException` telling you to resync over
  REST; the module never silently continues on uncertain state.
* `getTrades` / `getTicker` — public trades and top-of-book.
* `getMarketLifecycle` — raw `market_lifecycle_v2` status events
  (created/activated/determined/settled/...) exposed unmapped, since no generic
  DTO models settlement.
* `getUserTrades` / `getOrderChanges` — credentialed fills and order-state
  updates. Fill sides follow `RULE_LEGACY_NO_COMPLEMENT`: `buy NO at q` reads as
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
