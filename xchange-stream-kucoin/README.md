# XChange KuCoin Stream

Streaming support for KuCoin, generation-aware:

* **Classic** — the existing token-based protocol (`KucoinStreamingExchange`),
  unchanged for compatibility.
* **UTA** — the current protocol (`UtaStreamingExchange`), for unified Spot and
  Futures public/private channels and WebSocket order placement.

## UTA streaming

Select the UTA generation on the streaming exchange:

```java
UtaStreamingExchange exchange = new UtaStreamingExchange();
ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
spec.setApiKey("...");
spec.setSecretKey("...");
spec.setExchangeSpecificParametersItem("passphrase", "...");
spec.setExchangeSpecificParametersItem(KucoinExchange.API_MODE_PARAMETER, KucoinApiMode.UTA);
exchange.applySpecification(spec);

ProductSubscription subscription =
    ProductSubscription.create()
        .addTicker(CurrencyPair.BTC_USDT)
        .addOrderbook(CurrencyPair.BTC_USDT)
        .addOrders(CurrencyPair.BTC_USDT)   // private; requires API credentials
        .build();
exchange.connect(subscription).blockingAwait();

exchange.getStreamingMarketDataService().getTicker(CurrencyPair.BTC_USDT).subscribe(System.out::println);
exchange.getStreamingMarketDataService().getOrderBook(CurrencyPair.BTC_USDT).subscribe(System.out::println);
exchange.getStreamingTradeService().getOrderChanges(CurrencyPair.BTC_USDT).subscribe(System.out::println);
```

### Protocol and guarantees

* **Endpoints** — public SPOT `wss://x-push-spot.kucoin.com`, public FUTURES
  `wss://x-push-futures.kucoin.com`, private `wss://wsapi-push.kucoin.com` with a
  24-hour token re-acquired on every private reconnect.
* **Frames** — current `{T, P, t, dp, d}` format; `SUBSCRIBE`/`UNSUBSCRIBE`
  actions with acks; `welcome` message drives the JSON ping interval (never more
  than one ping per second).
* **Generation ids** — every physical connection carries a generation; frames
  arriving after a reconnect are routed against the new generation and stale
  pre-reconnect order-book state is discarded before the fresh snapshot.
* **Sequence-safe depth** — order books use `obu` with `depth=increment@10ms`
  (the current channel; plain `increment` is deprecated): the topic pushes a
  snapshot first, then deltas. Deltas apply only under the documented continuity
  rule (`O <= last+1` and `C > last`); stale/overlapping deltas are dropped; a
  sequence gap resets the assembler and resubscribes for a fresh authoritative
  snapshot — no path continues on unproved sequence state.
* **Aggregate liveness** — `isAlive()` requires every subscribed transport to be
  open; a single dead required socket reports the exchange dead.
* **Private events** — order changes are deduplicated by `(orderId, updatedTime)`;
  balance/position/execution channels follow the same provider identities.
* **WebSocket trading** — order placement/cancellation over the Pro WebSocket
  Add/Cancel socket (`wss://wsapi.kucoin.com/v1/private`) with the documented
  `sessionId + timestamp` challenge-response authentication, request correlation
  by id, and the REST no-blind-replay policy: a socket drop fails pending
  placements explicitly with an unknown-outcome exception and never silently
  resends them.

## References

* UTA WebSocket introduction: https://www.kucoin.com/docs-new/websocket-api/base-info/introduction-uta
* Order book channel: https://www.kucoin.com/docs-new/3470221w0
* Private order channel: https://www.kucoin.com/docs-new/3470228w0
* Add/Cancel order (Pro WebSocket): https://www.kucoin.com/docs-new/3470133w0
