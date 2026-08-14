# XChange MEXC Spot v3 Stream

Streaming support for MEXC Spot v3 (`xchange-stream-mexc`), using the official
protobuf WebSocket protocol. Companion module to the `xchange-mexc` Spot v3
REST adapter; the legacy `org.knowm.xchange.mexc` v2 adapter is frozen and
deprecated.

## Usage

```java
MexcV3StreamingExchange exchange = new MexcV3StreamingExchange();
ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
spec.setApiKey("...");
spec.setSecretKey("...");
// private streams (account, orders, deals) require API credentials;
// public streams work without them
exchange.applySpecification(spec);

ProductSubscription subscription =
    ProductSubscription.create()
        .addOrderbook(CurrencyPair.BTC_USDT)
        .addTicker(CurrencyPair.BTC_USDT)
        .addOrders(CurrencyPair.BTC_USDT)   // private; requires API credentials
        .build();
exchange.connect(subscription).blockingAwait();

exchange.getStreamingMarketDataService().getOrderBook(CurrencyPair.BTC_USDT).subscribe(System.out::println);
exchange.getStreamingTradeService().getOrderChanges(CurrencyPair.BTC_USDT).subscribe(System.out::println);
exchange.getStreamingAccountService().getBalanceChanges().subscribe(System.out::println);
```

## Protocol and guarantees

* **Endpoints** — public and private share `wss://wbs-api.mexc.com/ws`; with
  API credentials the exchange creates a listenKey first and connects to
  `wss://wbs-api.mexc.com/ws?listenKey=<key>`. Override the URI with the
  `WebsocketUri` exchange-specific parameter.
* **Frames** — binary protobuf pushes (`PushDataV3ApiWrapper`), encoded and
  decoded through the pinned schema in `MexcV3ProtoCodec`; channel
  subscriptions use the documented `spot@public.<topic>.v3.api.pb@…@<symbol>`
  names.
* **listenKey lifecycle** — created on connect (POST `/api/v3/userDataStream`),
  kept alive with a keepalive every 30 minutes (PUT), and closed on disconnect
  (DELETE); the key self-expires after 60 minutes if the connection dies.
* **Sequence-safe depth** — `getOrderBook` subscribes to
  `spot@public.aggre.depth.v3.api.pb@100ms@<symbol>` and reconciles with a REST
  `/api/v3/depth?limit=5000` snapshot on the first push or any version gap.
  Deltas apply only when `fromVersion == lastUpdateId + 1`; stale and
  overlapping pushes are dropped, a gap refetches the authoritative snapshot,
  and a quantity of `0` removes the price level. No path continues on unproved
  sequence state.
* **Private channels** — account (`spot@private.account.v3.api.pb`), orders
  (`spot@private.orders.v3.api.pb`), and deals (`spot@private.deals.v3.api.pb`)
  map to `Balance`, `Order`, and `UserTrade`; order status codes 1–5 map to
  NEW/FILLED/PARTIALLY_FILLED/CANCELED/PARTIALLY_CANCELED and `orderType == 5`
  to market orders.

## References

* MEXC Spot WebSocket introduction: https://mexcdevelop.github.io/apidocs/spot_v3_en/#spot-websocket-api
* Incremental depth: https://mexcdevelop.github.io/apidocs/spot_v3_en/#incremental-depth
* ListenKey/user data streams: https://mexcdevelop.github.io/apidocs/spot_v3_en/#listen-key
* Spot v3 REST (snapshot source): https://mexcdevelop.github.io/apidocs/spot_v3_en/#order-book
