# XChange Bitget Streaming

WebSocket streaming for Bitget, with two API generations selected through
`BitgetConfiguration.API_MODE` (see the [xchange-bitget README](../xchange-bitget/README.md)):

| Mode | Public socket | Private socket | Account model |
|---|---|---|---|
| `CLASSIC_V2` (default) | `wss://ws.bitget.com/v2/ws/public` | `wss://ws.bitget.com/v2/ws/private` | Legacy Spot/Futures |
| `UTA_V3` | `wss://ws.bitget.com/v3/ws/public` | `wss://ws.bitget.com/v3/ws/private` | Unified Trading Account |

UTA v3 services live in `info.bitrich.xchangestream.bitget.uta.v3.*` and are wired by
`BitgetStreamingExchange` when the exchange-specific parameter selects `BitgetApiMode.UTA_V3`.
Without credentials only the public market-data service is exposed; trade and account services
require `apiKey`, `secretKey`, and `passphrase`.

## Selecting UTA v3

```java
ExchangeSpecification spec =
    StreamingExchangeFactory.INSTANCE
        .createExchangeWithoutSpecification(BitgetStreamingExchange.class)
        .getDefaultExchangeSpecification();
spec.setApiKey(apiKey);
spec.setSecretKey(secretKey);
spec.setPassword(passphrase);
spec.setExchangeSpecificParametersItem(BitgetConfiguration.API_MODE, BitgetApiMode.UTA_V3);
StreamingExchange exchange = StreamingExchangeFactory.INSTANCE.createExchange(spec);
exchange.connect().blockingAwait();
```

## UTA v3 streaming lifecycle

- **Login**: the private socket authenticates with a v3 login frame (`op=login`) carrying
  `apiKey`, `passphrase`, an epoch-seconds `timestamp` (from a configurable clock), and the
  HMAC-SHA256 `sign` produced by `BitgetStreamingAuthHelper`. Subscriptions issued before login
  complete are re-sent after the login acknowledgment.
- **Heartbeat**: the client sends ping frames and tracks socket liveness.
- **Connection generations**: every (re)connect gets a generation ID. Login/subscription/trading
  responses are correlated by generation plus subscription id; stale responses from a previous
  generation cannot mutate current state (`getConnectionGeneration()` / `isCurrentGeneration(long)`).
- **Reconnect and resubscribe**: on reconnect the public and private sockets re-establish and the
  full subscription set is re-sent (private after re-login).
- **Aggregate liveness**: `isAlive()` is the AND of all required sockets (public only when no
  credentials were supplied).
- **Idempotent disconnect**: `disconnect()` is null-safe and can be called repeatedly; lifecycle
  accessors never throw on disconnected state.

## Channels

| Channel | Service | Notes |
|---|---|---|
| `books` / `books1` / `books5` / `books50` | `getOrderBook(instrument, depth)` | Depth is the topic suffix (`1`/`5`/`50`); `books` is incremental, the rest are replace-only snapshots. No checksum field; continuity is verified via `seq`/`pseq`. |
| `ticker` | `getTicker(instrument)` | v3 field names; envelope timestamp. |
| `publicTrade` | `getTrades(instrument)` | v3 public trades. |
| `kline` | `getCandleStick(instrument, interval, args)` | Candle sticks. |
| `UTA_order` | `getOrderChanges(instrument)` | Account-wide order stream; filtered by the subscribed instrument, deduplicated by order id. |
| `UTA_fill` | `getUserTrades(instrument)` | Account-wide fill stream (`execTime`-based); deduplicated by `execId`. |
| `UTA_position` | `getPositionChanges(instrument)` | Account-wide position stream; pushes carry no category, instrument comes from the caller. |
| `UTA_account` | `getBalanceChanges(currency)` | Account-wide balance stream; filtered by the subscribed currency. |

## Order-book recovery

- The assembler buffers nothing: updates before a snapshot are dropped (the provider contract sends
  the snapshot first); snapshots replace the book.
- Continuity is enforced: the first update after a snapshot must satisfy `P ≤ S ≤ U` (pseq,
  snapshot seq, update seq); later updates must have `pseq == lastSeq`. Stale/duplicate updates are
  dropped.
- On a sequence gap or a `pseq=0` provider sequence-space reset, the book is reset and the channel
  resubscribed for a fresh snapshot; the failure is emitted on
  `subscribeOrderBookContinuityFailures()`.

## Placement and unknown outcomes

- Placements go through REST (`placeMarketOrderRest` / `placeLimitOrderRest`), inject the `clientOid`
  as the order user reference, and return the exchange order id.
- If the private socket disconnects while a placement is pending, the outcome is unknown: each
  pending placement fails with `BitgetUtaV3UnknownOutcomeException` on
  `subscribePlacementFailures()` (never on the placement stream), carrying its `clientOid`.
- Pending placements are **never silently resent** on reconnect; reconcile by order id via REST
  before retrying.

## Using IntelliJ Idea HTTP client

There are *.http files stored in `src/test/resources/rest` that can be used with IntelliJ Idea HTTP Client.

Some requests need authorization, so the api credentials have to be stored in `http-client.private.env.json` in module's root. Sample content can be found in `example.http-client.private.env.json`

> [!CAUTION]
> Never commit your api credentials to the repository!


[HTTP Client documentation](https://www.jetbrains.com/help/idea/http-client-in-product-editor.html)

## Running integration tests that require API keys

Integration tests that require API keys read them from environment variables. They can be defined in `integration-test.env.properties`. Sample content can be found in `example.integration-test.env.properties`.

If no keys are provided the integration tests that need them are skipped.

> [!CAUTION]
> Never commit your api credentials to the repository!
