
# XChange Binance Stream

Reactive streaming for the Binance product families (Spot, USDⓈ-M/COIN-M futures, Portfolio
Margin), built on the XChange streaming core.

## Subscriptions

Channels are fixed at connection time: pass a `ProductSubscription` (and optionally a
`KlineSubscription`) to `connect(...)`. Binance limits live subscription messages to 5/second,
so live subscribe/unsubscribe is opt-in via `enableLiveSubscription()` and defaults to disabled.

## Lifecycle and reconnect

* Public market streams are connected with a single URL that encodes every subscribed channel;
  on reconnect the service reconnects to the same URL, so all public channels are resubscribed
  without per-channel messages.
* The authenticated WS API trading service (`BinanceUserTradeStreamingService`) re-runs
  `session.logon` on every reconnect with a fresh timestamp/signature and a monotonic request id;
  `isAuthorized()` reflects the current session (reset on disconnect, re-armed on re-login).
  `getConnectionGeneration()` increments on every (re)connect for generation-scoped correlation.
* Spot/futures user-data streams are keyed by listen key: `BinanceUserDataChannel` requests the
  key, sends a keepalive every 30 minutes, and rotates the key (notifying the exchange) when it
  expires; the user-data socket reconnects with the new key.
* `isAlive()` is null-safe for every credential/product combination: services that were never
  created (for example the WS API trading service with HMAC keys, or the spot user-data service
  in futures mode) do not count against liveness. The connection-state observables
  (`connectionStateObservableUserData()`, `connectionStateObservableUserTrade()`) complete
  immediately when the corresponding service does not exist.

## Order-book recovery

Order books are recovered with a deterministic snapshot-plus-delta state machine:

* Spot: the first delta triggers a REST snapshot; deltas whose `u <= lastUpdateId` are dropped,
  the first applied delta must satisfy `U <= lastUpdateId + 1 <= u`, and any sequence mismatch
  re-syncs from a fresh snapshot (documented Binance behavior where snapshot and delta windows
  may briefly overlap).
* Futures: deltas are buffered until a snapshot is fetched (repeated while the snapshot is older
  than the first buffered delta), then applied in `pu`-verified order; a broken `pu` chain
  discards the book and re-syncs from a fresh snapshot. Consumers never observe a book built
  from unverified continuity.

A failed snapshot fetch keeps the subscription retrying; a rate-limited snapshot fetch surfaces
the rate-limit error with guidance.

## Live subscription examples

### Live Subscription/Unsubscription
The feature to support Live Subscribe/Unsubcribe to streams has been added on 2021-02-24.
This allow to subscribe new currency pairs without disconnecting the streams.

To use this feature, follow these steps:
```java
ExchangeSpecification spec = StreamingExchangeFactory.INSTANCE.createExchange(BinanceStreamingExchange.class)
    .getDefaultExchangeSpecification();
BinanceStreamingExchange exchange = (BinanceStreamingExchange) StreamingExchangeFactory.INSTANCE.createExchange(spec);

// First, we need to subscribe to at least one currency pair at connection time
// Note: at connection time, the live subscription is disabled
ProductSubscription subscription =
    ProductSubscription.create().addTrades(CurrencyPair.BTC_USDT).addOrderbook(CurrencyPair.BTC_USDT).build();
exchange.connect(subscription).blockingAwait();

// We subscribe to trades update for the currency pair subscribed at connection time (BTC)
// For live unsubscription, you need to add a doOnDispose that will call the method unsubscribe in BinanceStreamingMarketDataService
Disposable tradesBtc = exchange.getStreamingMarketDataService()
    .getTrades(CurrencyPair.BTC_USDT)
    .doOnDispose(
        () -> exchange.getStreamingMarketDataService().unsubscribe(CurrencyPair.BTC_USDT, BinanceSubscriptionType.TRADE))
    .subscribe(trade -> { LOG.info("Trade: {}", trade); });

// Now we enable the live subscription/unsubscription to add new currencies to the streams
exchange.enableLiveSubscription();

// We live subscribe a new currency pair to the trades update
Disposable tradesEth = exchange.getStreamingMarketDataService()
    .getTrades(CurrencyPair.ETH_USDT)
    .doOnDispose(
        () -> exchange.getStreamingMarketDataService().unsubscribe(CurrencyPair.ETH_USDT, BinanceSubscriptionType.TRADE))
    .subscribe(trade -> { LOG.info("Trade: {}", trade); });

Thread.sleep(30000);

// We unsubscribe from the streams
tradesBtc.dispose();
tradesEth.dispose();
```

### IMPORTANT NOTE
When using Live Subscription/Unsubscription, Binance has a websocket limit of 5 incoming messages per second. If you bypass this limit, the websocket will be disconnected.
See https://github.com/binance/binance-spot-api-docs/blob/master/web-socket-streams.md#websocket-limits for more details.

If you plan to subscribe/unsubscribe more than 5 currency pairs at a time, use a rate limiter or keep the live subscription feature disabled and connect your pairs at connection time only (default value).


 