# XChange Bitget

Modernized Bitget integration with two explicit API/account generations, selected through a typed
exchange-specific parameter:

| Mode | Account model | API generation | Default |
|---|---|---|---|
| `CLASSIC_V2` | Legacy Spot/Futures accounts | v2 REST/WebSocket | ✅ (unchanged behavior) |
| `UTA_V3` | Unified Trading Account (UTA) | v3 REST/WebSocket | — (opt-in) |

The mode is selected via `BitgetConfiguration.API_MODE` on the exchange specification and must be a
`BitgetApiMode` value:

```java
ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
spec.setExchangeSpecificParametersItem(BitgetConfiguration.API_MODE, BitgetApiMode.UTA_V3);
```

`CLASSIC_V2` remains the default so existing `xchange-bitget`/`xchange-bitget-futures` consumers
keep their current behavior unless they explicitly opt into UTA v3.

## Mode and credential rules

- Classic and UTA credentials are **not interchangeable**. Routing UTA credentials through classic
  mode (or vice versa) fails before any trading request with an actionable diagnostic.
- Credential validation is typed and fails fast (`IllegalArgumentException` from
  `BitgetConfiguration.from`) before any network call for unknown parameter values.
- In UTA v3 mode the REST host is `https://api.bitget.com` with `/api/v3/...` paths; classic mode
  keeps the historical v2 endpoints.
- UTA v3 authentication requires `apiKey`, `secretKey`, and `passphrase` (the API passphrase set at
  creation, not the login password).

## Package and artifact decision

| Concern | Classic v2 | UTA v3 |
|---|---|---|
| REST wire clients | `org.knowm.xchange.bitget.Bitget*` | `org.knowm.xchange.bitget.uta.v3.*` |
| Streaming services | `info.bitrich.xchangestream.bitget.*` (v2 sockets) | `info.bitrich.xchangestream.bitget.uta.v3.*` |
| Configuration | `org.knowm.xchange.bitget.config.BitgetConfiguration` / `BitgetApiMode` | same |

UTA v3 lives in dedicated `uta.v3` packages in both `xchange-bitget` and `xchange-stream-bitget`,
keeping classic code untouched and source-compatible.

## Capability matrix

Legend: ✅ implemented · ⚠️ partial · ❌ not implemented.

### Market data (REST)

| Capability | Classic v2 | UTA v3 |
|---|---|---|
| Server time | ✅ | ✅ |
| Instruments / metadata | ✅ | ✅ (`buildExchangeMetaData`) |
| Order book depth | ✅ | ✅ |
| Ticker (single) | ✅ | ✅ |
| Tickers (multiple) | ✅ | ✅ |
| Public trades | ✅ | ❌ v3 has no `market/trades` REST path; use the WebSocket `publicTrade` channel |

### Account and trading (REST)

| Capability | Classic v2 | UTA v3 |
|---|---|---|
| Unified account info (equity, risk, coins) | — | ✅ `getAccountInfo` |
| Transfers (UTA) | — | ✅ `transfer` |
| Place market order | ✅ | ✅ |
| Place limit order | ✅ | ✅ |
| Open orders | ✅ | ✅ |
| Order query / history | ✅ | ✅ (`getOrder` / `getTradeHistory`) |
| Open positions | ✅ | ✅ |

### Streaming (WebSocket)

| Capability | Classic v2 | UTA v3 |
|---|---|---|
| Order book | ✅ (v2 `books`) | ✅ (`books`/`books1`/`books5`/`books50`, topic-suffix depth) |
| Tickers | ✅ | ✅ |
| Trades | ✅ (`publicTrade`) | ✅ |
| Klines | — | ✅ (`kline`) |
| Order changes | ✅ | ✅ (`UTA_order`, account-wide) |
| User trades / fills | ✅ | ✅ (`UTA_fill`, account-wide) |
| Position changes | ✅ | ✅ (`UTA_position`) |
| Balance changes | — | ✅ (`UTA_account`, account-wide) |
| Pending-placement failure signaling | — | ✅ `subscribePlacementFailures()` |

## Auth and endpoint matrix

| Concern | Classic v2 | UTA v3 |
|---|---|---|
| REST base | `https://api.bitget.com` (v2 paths) | `https://api.bitget.com` (`/api/v3/...`) |
| Public WS | `wss://ws.bitget.com/v2/ws/public` | `wss://ws.bitget.com/v3/ws/public` |
| Private WS | `wss://ws.bitget.com/v2/ws/private` | `wss://ws.bitget.com/v3/ws/private` |
| Private WS login | v2 login frame | v3 login frame: `op=login`, `args[0]` with `apiKey`, `passphrase`, epoch-seconds `timestamp`, HMAC-SHA256 `sign` |
| REST signing | v2 digest | v3 digest (`BitgetUtaV3Digest`) |

## Instrument mapping

- Spot instruments map to `CurrencyPair` (e.g. `BTCUSDT` → `CurrencyPair.BTC/USDT`).
- Derivative instruments map to `FuturesContract(pair, "PERP")`.
- Category wire names map to `BitgetUtaV3Category` (`spot`, `usdt-futures`, `usdc-futures`,
  `coin-futures`); unknown categories fall back to symbol-only spot identity, mirroring the REST
  adapters.
- Positions pushes carry no `category`; the instrument is resolved from the caller's subscription.

## Unified-account semantics

UTA v3 collapses spot and derivatives into one margin account:

- `getAccountInfo` returns total equity, effective equity, margin (MMR/IMR), margin ratio, position
  margin ratio, unrealised PnL, and per-coin balances (balance, locked, available, equity, USD
  value, borrow, debts, bonus).
- Positions carry side, margin mode, leverage, entry/liquidation prices, and unrealised PnL.
- Account balance and position/fill/order streams are account-wide channels (no symbol filter on the
  wire); the services filter by the subscribed instrument/currency.

## Client-order and unknown-outcome policy

- Placements inject `clientOid` as the XChange order user reference; the returned `String` order id
  is the exchange order id, and the pending `clientOid` is tracked.
- If the private WebSocket disconnects while a placement is pending, the outcome is **unknown**: the
  placement fails with `BitgetUtaV3UnknownOutcomeException` on `subscribePlacementFailures()`
  (never on the placement stream), carrying the `clientOid` for reconciliation.
- Pending placements are **never silently resent** on reconnect. Reconcile by order id via REST
  (`trade/order-info`) before any retry.
- Order-book continuity violations (sequence gap, `pseq=0` reset) emit
  `BitgetUtaV3OrderBookContinuityException` on `subscribeOrderBookContinuityFailures()` and
  resubscribe for a fresh snapshot.

## Pagination and rate behavior

- Order history and trade history use cursor/limit continuation (`TradeHistoryParams`); repeated
  cursors are protected and partial pages are not silently dropped.
- Exchange metadata discovery follows v3 paging; product identity collisions are surfaced rather
  than silently overwritten.

## Migration examples

Select UTA v3 (REST):

```java
ExchangeSpecification spec =
    ExchangeFactory.INSTANCE
        .createExchangeWithoutSpecification(BitgetExchange.class)
        .getDefaultExchangeSpecification();
spec.setApiKey(apiKey);
spec.setSecretKey(secretKey);
spec.setPassword(passphrase);
spec.setExchangeSpecificParametersItem(BitgetConfiguration.API_MODE, BitgetApiMode.UTA_V3);
Exchange exchange = ExchangeFactory.INSTANCE.createExchange(spec);
```

Balances (unified account):

```java
AccountInfo info = exchange.getAccountService().getAccountInfo();
info.getWallet().getBalances().forEach(b -> System.out.println(b.getCurrency() + " = " + b.getTotal()));
```

Positions:

```java
OpenPositions positions = exchange.getTradeService().getOpenPositions();
positions.getOpenPositions().forEach(p -> System.out.println(p.getInstrument() + " size=" + p.getSize()));
```

Orders and history:

```java
// open orders (use the service-created params to scope by instrument)
OpenOrders open = exchange.getTradeService().getOpenOrders(exchange.getTradeService().createOpenOrdersParams());
// order query by exchange order id (or pair+clientOid via OrderQueryParams)
Collection<Order> orders = exchange.getTradeService().getOrder(new DefaultQueryOrderParam(orderId));
// fill history
UserTrades trades = exchange.getTradeService().getTradeHistory(exchange.getTradeService().createTradeHistoryParams());
```

Transfers between account types (e.g. UTA ↔ spot):

```java
BitgetUtaV3AccountService accountService = (BitgetUtaV3AccountService) exchange.getAccountService();
accountService.transfer(
    BitgetUtaV3TransferRequest.builder()
        .fromType("uta")
        .toType("spot")
        .coin("USDT")
        .amount(new BigDecimal("100"))
        .build());
```

Streaming:

```java
spec.setExchangeSpecificParametersItem(BitgetConfiguration.API_MODE, BitgetApiMode.UTA_V3);
StreamingExchange exchange = StreamingExchangeFactory.INSTANCE.createExchange(spec);
exchange.connect().blockingAwait();
exchange.getStreamingMarketDataService().getOrderBook(CurrencyPair.BTC_USDT).subscribe(...);
exchange.getStreamingTradeService().getOrderChanges(CurrencyPair.BTC_USDT).subscribe(...);
exchange.getStreamingTradeService().getUserTrades(CurrencyPair.BTC_USDT).subscribe(...);
exchange.getStreamingAccountService().getBalanceChanges(Currency.BTC).subscribe(...);
```

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
