# XChange KuCoin

XChange adapter for [KuCoin](https://www.kucoin.com/), covering both API generations:

* **Classic** — the mature Spot-era adapter (`KucoinExchange`, default mode).
* **UTA** — the Unified Trading Account generation (`apiMode=UTA`), for unified
  Spot/Futures account, positions, margin, transfers, trading, and the current
  WebSocket protocol.

The two generations are isolated behind an explicit compatibility boundary. The
classic mode remains the default and its behavior is unchanged; UTA must be
selected explicitly.

## Selecting the API mode

```java
ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
spec.setApiKey("...");
spec.setSecretKey("...");
spec.setExchangeSpecificParametersItem("passphrase", "...");
// Explicitly select the UTA generation:
spec.setExchangeSpecificParametersItem(KucoinExchange.API_MODE_PARAMETER, KucoinApiMode.UTA);

KucoinExchange exchange = ExchangeFactory.INSTANCE.createExchange(spec);
exchange.remoteInit(); // loads Spot + Futures instrument catalogs (public, no credentials needed)

UtaMarketDataService marketData = exchange.getUtaMarketDataService();
UtaAccountService   account   = exchange.getUtaAccountService();
UtaTradeService     trade     = exchange.getUtaTradeService();
```

An invalid or unknown `apiMode` value fails early at specification time. Classic
getters (`getMarketDataService()` etc.) throw an actionable error in UTA mode and
vice versa, so a credential set can never silently hit the wrong generation.

## Capability matrix

| Capability | Classic | UTA |
| --- | --- | --- |
| Spot market data (ticker, order book, trades, klines) | yes | yes |
| Instrument catalog with lossless identity (spot + derivatives) | spot only | spot + futures |
| Unified balances incl. liability / collateral status | no | yes (`/unified/account/balance`) |
| Account risk overview (equity, margins, risk ratio) | no | yes |
| Positions (open list) | no | yes |
| Margin mode / leverage | no | yes |
| Flex transfer between account types / sub-accounts | partial | yes |
| Spot + Futures unified order placement / cancel | spot | yes |
| Order history / executions with cursor pagination | yes | yes |
| No-blind-replay placement (clientOid reconciliation) | no | yes |
| WebSocket streaming (current protocol, sequence-safe depth) | classic protocol | UTA protocol (`increment@10ms`) |
| WebSocket order placement / cancel | no | yes (`uta.order` / `uta.cancel`) |

## Migration guide

### Metadata

Classic `remoteInit()` populates instruments from the classic symbol API. UTA
`remoteInit()` loads the SPOT and FUTURES catalogs from
`GET /api/ua/v1/market/instrument` — public, no credentials required — and maps:

* Spot instruments (`BTC-USDT`) to `CurrencyPair`.
* Futures instruments (`XBTUSDTM`) to `FuturesContract` carrying the provider
  identity (`contractType`, `isInverse`, `expiryTime`, `lotSize`, `unitSize`,
  `maxLeverage`, `settlementCurrency`); identity is never inferred from symbol
  text.

The provider symbol for an instrument is available via
`exchange.getUtaProviderSymbol(instrument)`.

### Balances

Classic balances are per-account-type (`main`/`trade`) with no liability view.
UTA exposes one unified currency-level snapshot:

```java
UtaAccountBalance balance = exchange.getUtaAccountService().getUtaAccountBalance();
// currency -> equity, hold, balance, available, liability, potentialBorrow, collateralStatus
```

`getAccountInfo()` maps to standard XChange `Wallet` balances; liability and
collateral-status data remain available losslessly through the raw DTO.

### Positions / risk

```java
List<UtaPosition> positions = exchange.getUtaTradeService().getOpenPositionsRaw(null);
```

Each position carries margin mode, size in contracts (positive long / negative
short), entry/mark/liquidation prices, leverage, initial/maintenance margin, and
risk ratio.

### Orders and history

UTA orders use `tradeType` (`SPOT`/`FUTURES`/`MARGIN`) plus the provider symbol:

```java
UtaOrderPlaceRequest request = UtaOrderPlaceRequest.builder()
    .tradeType("SPOT")
    .symbol("BTC-USDT")
    .clientOid("my-order-1")          // max 40 chars: letters, digits, '_', '-'
    .side("BUY")
    .orderType("LIMIT")
    .size("0.001")
    .sizeUnit("BASECCY")
    .price("65000")
    .build();
UtaOrderResult result = exchange.getUtaTradeService().placeOrderSafe(request, CurrencyPair.BTC_USDT);
```

* `clientOid` is mandatory for futures and margin orders and validated before
  transmission.
* A placement whose transmission outcome is unknown is **never automatically
  retried**. `placeOrderSafe` reconciles by `clientOid` (provider code `116151`
  or a transport failure) and returns the existing order when found; when the
  order's existence cannot be proven it throws an `UtaApiException` with
  `RetryClassification.UNKNOWN_OUTCOME`. Reconcile manually before deciding.
* History and executions are cursor-paginated (`lastId` + `pageSize`, max 200);
  `getAllOrderHistory` detects no-progress continuations and is bounded.

### Transfers

```java
UtaTransferRequest transfer = new UtaTransferRequest();
transfer.setClientOid("tx-1");
transfer.setCurrency("USDT");
transfer.setAmount(new BigDecimal("10"));
transfer.setType("0");                       // INTERNAL
transfer.setFromAccountType("SPOT");
transfer.setToAccountType("UNIFIED");
UtaTransferResult result = exchange.getUtaAccountService().transfer(transfer);
```

### Streaming

Streaming is generation-aware. In UTA mode the streaming exchange uses the
current protocol:

* Public: `wss://x-push-spot.kucoin.com` (SPOT) and `wss://x-push-futures.kucoin.com`
  (FUTURES).
* Private: `wss://wsapi-push.kucoin.com/?token=...` with a 24-hour token
  re-acquired on every private reconnect.
* Order book depth uses `obu` with `depth=increment@10ms` (snapshot first, then
  deltas) with the documented sequence-continuity rule; a sequence gap emits a
  dedicated failure and triggers a fresh snapshot rebuild — no path continues on
  unproved sequence state.
* Every physical connection carries a generation id; events from stale
  generations are rejected.
* Private order/balance/execution/position events are deduplicated by stable
  provider identity.
* WebSocket order placement/cancel (`uta.order`/`uta.cancel`) shares the REST
  no-blind-replay policy: a disconnect fails pending placements explicitly and
  never silently resends them.

See `xchange-stream-kucoin` for the streaming implementation.

## Authentication and errors

UTA signing follows the current authentication documentation: prehash =
`timestamp(millis) + UPPER(method) + path[+query] + body`, HMAC-SHA256 with the
API secret, Base64; the passphrase header is the passphrase HMAC-SHA256 with the
secret, Base64. Provider failures are mapped to structured `UtaApiException`
instances carrying mode, domain/endpoint, provider code, HTTP status, sanitized
order identity, and retry classification. Secrets, signatures, WebSocket tokens
and private payloads are redacted from logs and exception text.

## References

* Official KuCoin docs: https://www.kucoin.com/docs-new
* UTA REST introduction: https://www.kucoin.com/docs-new/rest/ua/introduction
* UTA WebSocket introduction: https://www.kucoin.com/docs-new/websocket-api/base-info/introduction-uta
