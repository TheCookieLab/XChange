# XChange Crypto.com Stream

WebSocket streaming for Crypto.com Exchange v1 (`info.bitrich.xchangestream.cryptocom`), REST
counterpart `xchange-cryptocom` (`org.knowm.xchange.cryptocom`). Spot, margin and derivatives
market data plus the authenticated user feed.

## Transports and capability matrix

`CryptoComStreamingExchange` (use `StreamingExchangeFactory.INSTANCE.createExchange(...)` or
`ExchangeFactory` with `CryptoComStreamingExchange.class`) owns two WebSocket feeds:

| Transport | URI (production, hard-coded) | Channels |
|---|---|---|
| Public | `wss://stream.crypto.com/exchange/v1/market` | `book.<inst>.<depth>`, `trade.<inst>`, `ticker.<inst>` |
| Private | `wss://stream.crypto.com/exchange/v1/user` | `user.order.<inst>`, `user.trade.<inst>`, `user.balance`; also carries `public/auth` and private trading requests |

The **private socket is created only when the subscription actually needs it** — `connect()`
derives the required transports from the `ProductSubscription`: orders, user trades or balances
require the private feed; a public-only subscription (ticker/trades/order books) works with a
single public socket, credentials or not. Requesting authenticated channels without API
credentials fails the connection explicitly (`ExchangeSecurityException`).

| Service | Method | Maps to |
|---|---|---|
| `getStreamingMarketDataService` | `getTicker` / `getOrderBook` / `getTrades` | `ticker.*`, `book.*`, `trade.*` |
| `getStreamingAccountService` | `getBalanceChanges` | `user.balance` (deduplicated full-state snapshots) |
| `getStreamingTradeService` | `getOrderChanges` / `getUserTrades` | `user.order.*`, `user.trade.*` (deduplicated) |
| `getOrderBookContinuityFailures` | dedicated book-sequence failure stream | see *Sequence recovery* |

Order books default to depth 10 (`book.<inst>.10`; the `channelsFor` derivation is
package-visible for tests).

## Request envelope, ids, and signing

Every outbound frame is the standard v1 envelope `{"id","method","params"}`; the id comes from
the same monotonic request-id generator as the REST module. Subscriptions (`subscribe`/
`unsubscribe` with `params.channels`) and `public/auth` are correlated by request id, and book
channels additionally send `params.book_subscription_type = SNAPSHOT_AND_UPDATE` (the official
combined snapshot/delta feed).

Private authentication signs the canonical auth string with HMAC-SHA256 (`CryptoComDigest`) using
the API secret; the auth confirmation is correlated with the pending auth id. **Stale
confirmations of a superseded connection's auth id are ignored** — only the current connection
generation's auth opens the private plane. An auth rejection fails any in-flight private requests
explicitly.

## Streaming lifecycle and liveness

- **Connection generations** — every physical (re)connection captures a generation id; pushed
  events, auth and subscribe confirmations are correlated against the *current* generation so a
  dead socket can never leak stale state into the fresh session.
- **Aggregate `isAlive()`** — true only when the public socket is open on the current
  generation, an opened private socket is additionally authenticated, and every channel the
  connect subscription asked for has been confirmed by the server.
- **Heartbeat / idle** — the server sends `public/heartbeat`; the client answers
  `public/respond-heartbeat` with the same id, or the server closes the connection.
- **Bounded reconnect** — reconnection is capped (`MAX_RECONNECT_ATTEMPTS`); after the budget is
  exhausted the service stops reconnecting and transports report dead. Every successful
  reconnect **re-authenticates and fully re-subscribes** all requested channels
  (`resubscribeChannels()`), and private-event replay is filtered (below).

## Replay policy

- **Private events deduplicate on stable identity**: fills on `trade_id`, order updates on
  `order_id` + `update_time`, balance pushes on the full state snapshot, through a bounded,
  insertion-ordered `CryptoComStreamingEventDeduplicator`. A reconnect replay can never
  double-report an event.
- **Trading requests are never auto-re-sent.** A pending private request whose confirmation
  never arrives because the socket dropped fails explicitly (`CryptoComRequestException`,
  WebSocket transport, no retry class) with a message stating the request was **not** re-sent.
  Reconnect logic re-authenticates and re-subscribes only — it never replays request methods.
  Read-only requests fail with the same explicit semantics plus re-issue guidance; the caller
  decides whether to re-submit.

## Order-book sequence recovery

`book.*` uses the official `SNAPSHOT_AND_UPDATE` contract: the opening dataframe is a full
snapshot (`u` present, no `pu`), every subsequent dataframe is an incremental update carrying the
chained pair (`u`, `pu`) where `pu` must equal the previous dataframe's `u`:

- **Snapshot acquisition** — increments arriving before the opening snapshot are buffered
  (bounded) and applied in order once the snapshot lands.
- **Stale/duplicate rejection** — an update whose `u` is not newer than the last applied `u` is
  dropped.
- **Gap detection** — an update whose `pu` does not chain off the last applied `u` emits a
  dedicated `CryptoComOrderBookContinuityException` on `getOrderBookContinuityFailures()` and the
  assembler stops trusting the book (the last good book is kept).
- **Rebuild** — a connection loss (reconnect/re-subscription) marks the chain void, and both
  reconnect and a protocol-compliant mid-stream full snapshot rebuild the book from a fresh
  snapshot, resetting the sequence chain. The server may substitute a full snapshot when a delta
  would be too large; that is handled transparently.

Levels are `[price, quantity(, numberOfOrders)]`; a zero quantity removes the level; every
emission is trimmed to the subscribed depth with the best levels kept per side. Books are
defensively copied per emission.

## Environments and validation

Only the production WebSocket hosts are hard-coded. Crypto.com does not publish a verified
sandbox streaming host, so `StreamingExchange.USE_SANDBOX` alone never selects an endpoint:
sandbox without an explicit override **fails closed** at connect time. A caller with its own
verified host (for example a UAT WebSocket base URL from Crypto.com support) must opt in
explicitly with the specification parameter `cryptocom_ws_override`

```java
StreamingExchange exchange = StreamingExchangeFactory.INSTANCE.createExchange(
    CryptoComStreamingExchange.class);
ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
spec.setExchangeSpecificParametersItem(StreamingExchange.USE_SANDBOX, true);
spec.setExchangeSpecificParametersItem(
    "cryptocom_ws_override", "wss://my-verified-host.example.com");
exchange.applySpecification(spec);
```

The market and user paths are appended to the override base. Production credentials are never
sent to an override endpoint unless the caller explicitly enables that high-risk behavior. All
module tests are deterministic offline fixtures; funded trading and withdrawal are explicit
opt-in and never part of default test runs. See `xchange-cryptocom/README.md` for the shared
REST envelope, signing, instrument/derivative semantics and the full capability matrix.