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
| `Use_Sandbox_Websocket`                     | Attempt to connect to the sandbox endpoint                |
| `Coinbase_Public_Subscriptions_Per_Second`  | Override public subscription throttle (default `8`)       |
| `Coinbase_Private_Subscriptions_Per_Second` | Override private subscription throttle (default `750`)    |
| `Coinbase_Disable_Auto_Heartbeat`           | Skip automatic heartbeat subscription when set to `true`  |

### Sandbox endpoint

Coinbase's documentation lists `wss://advanced-trade-ws.sandbox.coinbase.com` as the sandbox host.
An opt-in smoke test (`CoinbaseSandboxConnectivityTest`) attempts a real connection when enabled via
`-Dcoinbase.sandbox.smoke=true` or the `COINBASE_SANDBOX_SMOKE=true` environment variable:

```bash
mvn -pl xchange-stream-coinbase -am test -DskipITs=true -Dcoinbase.sandbox.smoke=true
```

As of 2025-11-05 the hostname fails DNS resolution (`UnknownHostException`), so the smoke test will
report that error when enabled. By default the test is skipped and the implementation continues to
use the production host unless the sandbox flag is set explicitly.

### Examples

A runnable example is provided under the `xchange-examples` module. Run it against production:

```bash
mvn -pl xchange-examples -am \
  org.codehaus.mojo:exec-maven-plugin:3.1.0:java \
  -Dexec.mainClass=org.knowm.xchange.examples.coinbase.streaming.CoinbaseStreamingMarketDataExample
```

To target the sandbox (if reachable), add `-Dcoinbase.streaming.sandbox=true` or set the
`COINBASE_STREAMING_SANDBOX=true` environment variable before running the command. The example
subscribes to ticker, trades, and order book updates for BTC/USD and logs events until interrupted.

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

## Notes

- Private channels require Advanced Trade API credentials. The module reuses `CoinbaseV3Digest` to
  mint WebSocket JWTs and refreshes them every 90 seconds while a private subscription is active.
- Sandbox connectivity is currently unreliable: `advanced-trade-ws.sandbox.coinbase.com` does not
  resolve as of 2025-11-05. Use the smoke test to verify the latest behaviour.
- Only typed observables are exposed today; access to raw JSON can be added in the future without
  API breakage.***
