# XChange Coinbase Stream

Streaming support for the Coinbase Advanced Trade WebSocket API.

## Features

- Market data streams (`ticker`, `ticker_batch`, `market_trades`, `candles`, `level2`,
  `level2_batch`, `status`, `heartbeats`)
- Private streams (`user`, `futures_balance_summary`) with automatic JWT refresh
- Built on the core streaming abstractions (`StreamingExchange`, `StreamingMarketDataService`,
  `StreamingTradeService`)
- Client-side subscription throttling that respects Coinbase limits (8 public and 750 private
  subscriptions per second per IP)
- Heartbeat auto-subscription (can be disabled via exchange-specific parameter)

## Usage

```java
StreamingExchange exchange = StreamingExchangeFactory.INSTANCE.createExchange(
    CoinbaseStreamingExchange.class.getName());

ExchangeSpecification spec = exchange.getExchangeSpecification();
spec.setApiKey("api-key");
spec.setSecretKey("-----BEGIN EC PRIVATE KEY----- ...");
exchange.applySpecification(spec);

exchange.connect(ProductSubscription.create()
        .addTicker(CurrencyPair.BTC_USD)
        .addOrderbook(CurrencyPair.BTC_USD)
        .addTrades(CurrencyPair.BTC_USD)
        .build())
    .blockingAwait();

exchange.getStreamingMarketDataService()
    .getTicker(CurrencyPair.BTC_USD)
    .subscribe(System.out::println);
```

### Exchange-specific parameters

| Parameter key                               | Description                                               |
|---------------------------------------------|-----------------------------------------------------------|
| `Coinbase_Public_Subscriptions_Per_Second`  | Override public subscription throttle (default `8`)       |
| `Coinbase_Private_Subscriptions_Per_Second` | Override private subscription throttle (default `750`)    |
| `Coinbase_Disable_Auto_Heartbeat`           | Skip automatic heartbeat subscription when set to `true`  |
| `Coinbase_Websocket_Jwt_Supplier`          | Custom JWT supplier for WebSocket authentication          |

### WebSocket endpoints

Coinbase Advanced Trade provides two WebSocket endpoints:

- **Market Data Endpoint**: `wss://advanced-trade-ws.coinbase.com`
  - Used for public market data streams (ticker, trades, order book, candles, etc.)
  - Does not require authentication for public channels
  
- **User Order Data Endpoint**: `wss://advanced-trade-ws-user.coinbase.com`
  - Used for private user data streams (user trades, order changes, etc.)
  - Requires authentication via API key and secret key

The default endpoint is the market data endpoint. To use the user order data endpoint, set the
WebSocket URI override on the exchange specification:

```java
spec.setOverrideWebsocketApiUri("wss://advanced-trade-ws-user.coinbase.com");
```

**Note**: There is no sandbox environment for WebSocket connections. All WebSocket endpoints
connect to production.

### Examples

A runnable example is provided under the `xchange-examples` module:

```bash
mvn -pl xchange-examples -am \
  org.codehaus.mojo:exec-maven-plugin:3.1.0:java \
  -Dexec.mainClass=org.knowm.xchange.examples.coinbase.streaming.CoinbaseStreamingMarketDataExample
```

The example subscribes to ticker, trades, and order book updates for BTC/USD and logs events until interrupted.

## Channel details

- **Ticker / ticker_batch** – Emit the most recent price snapshot. Payloads contain `price`,
  `open_24_h`, `high_24_h`, `low_24_h`, `volume_24_h`, `best_bid`, and `best_ask`.
- **Market trades** – Include the executed `price`, `size`, `trade_id`, `side`, and timestamp. The
  adapter maps the `side` to `OrderType.BID`/`ASK`.
- **Candles** – Require a `granularity` argument (`ONE_MINUTE`, `FIVE_MINUTE`, etc.). Configure it
  via `CoinbaseCandleSubscriptionParams`, a default exchange parameter, or by passing a duration to
  `getCandles`.
- **Level2** – Carries snapshot and incremental `updates` arrays along with `sequence` numbers. The
  module enforces sequencing and automatically falls back to REST snapshots when gaps are detected.
  The order book Observable uses replay(1) to ensure new subscribers receive the latest state
  immediately upon subscription.
- **Heartbeats** – Enabled by default to keep connections warm; can be disabled with the
  `Coinbase_Disable_Auto_Heartbeat` parameter.

## Rate limiting & configuration

- Default throttles: 8 public subscribe/unsubscribe messages per second and 750 private per second.
  Override via `Coinbase_Public_Subscriptions_Per_Second` and
  `Coinbase_Private_Subscriptions_Per_Second`.
- The streaming service coalesces duplicate requests and automatically resubscribes after reconnects
  while honouring the configured limits.

## Testing

The module ships with unit tests covering:

- JSON adapters for all public payloads
- Order book merge logic for level2 streams
- Serialization of subscription/unsubscription messages (including JWT handling)
- Trade service projections for user channel events

Run the tests with:

```bash
mvn -pl xchange-stream-coinbase -am test
```

## Order Book Subscription Best Practices

The order book implementation uses `replay(1).refCount()` to cache and replay the last emitted
OrderBook to new subscribers. This ensures that:

1. **New subscribers receive the latest state immediately** - When you subscribe to an order book
   that has already received a snapshot, you'll get the current state right away.

2. **Multiple subscribers share the same stream** - All subscribers to the same currency pair share
   the underlying WebSocket subscription, reducing network overhead.

3. **Automatic cleanup** - When all subscribers unsubscribe, the cached observable is automatically
   cleaned up.

### Recommended Usage Patterns

**Pattern 1: Subscribe after connecting (recommended)**
```java
exchange.connect().blockingAwait();
// Subscribe immediately after connecting to receive the initial snapshot
exchange.getStreamingMarketDataService()
    .getOrderBook(CurrencyPair.BTC_USD)
    .subscribe(orderBook -> System.out.println("Order book: " + orderBook));
```

**Pattern 2: Use ProductSubscription for automatic subscription**
```java
// ProductSubscription will create a subscription, but you still need to subscribe manually
// to receive the data. The ProductSubscription just keeps the channel active.
exchange.connect(ProductSubscription.create()
    .addOrderbook(CurrencyPair.BTC_USD)
    .build())
    .blockingAwait();

// Subscribe to receive the data (will get latest state due to replay)
exchange.getStreamingMarketDataService()
    .getOrderBook(CurrencyPair.BTC_USD)
    .subscribe(orderBook -> System.out.println("Order book: " + orderBook));
```

**Note**: If you use `ProductSubscription` with order books, the subscription created by
`processProductSubscriptions()` is a no-op that just keeps the channel active. You still need to
manually subscribe via `getOrderBook().subscribe()` to receive the data. The replay mechanism
ensures you'll receive the latest state even if the snapshot was already processed.

## Notes

- Private channels require Advanced Trade API credentials. The module reuses `CoinbaseV3Digest` to
  mint WebSocket JWTs and refreshes them every 90 seconds while a private subscription is active.
- API keys and secret keys are automatically applied from the exchange specification, similar to the
  REST API implementation. The module uses `CoinbaseV3Digest.createInstance()` to create JWT tokens
  for authenticated WebSocket connections.
- Only typed observables are exposed today; access to raw JSON can be added in the future without
  API breakage.
