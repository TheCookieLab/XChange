# XChange Polymarket Stream

Polymarket streaming over the two CLOB WebSocket channels. Subscriptions take
the same `PredictionMarketContract` identity as the REST module
(`PRED/polymarket/<conditionId>/<clobTokenId>/USD`); generic `CurrencyPair`
calls are rejected before any subscription is attempted.

The default endpoints are
`wss://ws-subscriptions-clob.polymarket.com/ws/market` (public) and
`wss://ws-subscriptions-clob.polymarket.com/ws/user` (authenticated). Override
them with `polymarket.ws.market.uri` and `polymarket.ws.user.uri`.

## Credentials

The market channel connects anonymously. The user channel shares the REST L2
credential triplet: `apiKey`, `secretKey`, and `password` (passphrase). Without
all three, `getUserTrades` / `getOrderChanges` throw `ExchangeSecurityException`
before any subscription is attempted.

## Channels and side semantics

All prices are dollars per share of the subscribed outcome token, per
`RULE_TOKEN_DIRECT` (BUY reads as BID, SELL as ASK) and `RULE_NO_COMPLEMENT`
(events adapt to the token they actually reference — complements are never
substituted).

* `getOrderBook` — a full `book` snapshot anchors the state, then
  `price_change` events apply the *absolute* new size of each touched level
  (zero removes the level), so updates are idempotent and a fresh snapshot
  re-anchors after a reconnect. A price change before any snapshot, one naming
  an unexpected asset, or one with an unrecognized side terminates the stream
  with an `ExchangeException` telling you to resync over REST.
* `getTrades` — public `last_trade_price` events; a SELL aggressor reads as
  ask-side.
* `getTicker` — top-of-book from snapshots and from the best-bid/best-ask
  fields of price changes.
* `getUserTrades` — one fill per matched user order leg: a `TAKER` trade event
  yields the single taker fill, a `MAKER` event yields one fill per matched
  maker order. An unrecognized `trader_side` surfaces as an error, never a
  guess.
* `getOrderChanges` — full order state on placement, update, and cancellation,
  with the REST status truth table (live with fills reads as partially filled).

Every market event for one token shares a single memoized subscription, as do
the user events for one condition id; disposing the last reference unsubscribes
the channel, and reconnects resubscribe automatically. The heartbeat is the
documented application-level text `PING`/`PONG` on reader idle.

The RTDS crypto-oracle stream is intentionally out of scope: it carries no
order-book or user data, so it does not fit the generic streaming surface.

## Sample

```java
ExchangeSpecification spec =
    new ExchangeSpecification(PolymarketStreamingExchange.class);
spec.setApiKey("<L2 api key>");
spec.setSecretKey("<L2 secret>");
spec.setPassword("<L2 passphrase>");
StreamingExchange exchange = StreamingExchangeFactory.INSTANCE.createExchange(spec);
exchange.connect().blockingAwait();

Instrument contract =
    new PredictionMarketContract(
        "polymarket", null, conditionId, clobTokenId, Currency.USD);
Disposable subscription =
    exchange
        .getStreamingMarketDataService()
        .getOrderBook(contract)
        .subscribe(book -> System.out.println(book));
// ... exchange.disconnect().blockingAwait();
```

## Unsupported operations

* `CurrencyPair`-typed calls on every streaming service.
* Placement/cancellation over the socket (use the REST `xchange-polymarket`
  trade service).
* The RTDS and sports WebSocket surfaces.
