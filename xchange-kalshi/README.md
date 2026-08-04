# XChange Kalshi

Kalshi prediction-market support over the trade-api v2 REST surface. Instruments
are `PredictionMarketContract`s on the YES leg of an event market, quoted in USD;
the generic `Exchange`, `MarketDataService`, `TradeService`, and `AccountService`
entry points work unchanged.

The default endpoint is `https://api.elections.kalshi.com`. Override
`KalshiExchange.SSL_URI_PARAMETER` (`SslUri`) to target the demo environment.

## Credentials

* `apiKey` — Kalshi API key id.
* `secretKey` — unencrypted PKCS#8 RSA private key (PEM) for that key id.

Authenticated requests are signed RSA-PSS and sent with the
`KALSHI-ACCESS-KEY` / `KALSHI-ACCESS-TIMESTAMP` / `KALSHI-ACCESS-SIGNATURE`
headers. Keys never appear in exceptions or logs.

## Instrument identity

```java
PredictionMarketContract contract =
    new PredictionMarketContract("kalshi", null, "KXSB-26", "YES", Currency.USD);
```

The wire form is `PRED/kalshi/[<eventTicker>/]<marketTicker>/YES/USD`.
`remoteInit()` populates contracts from the provider catalog with the event
ticker segment filled in; `KalshiAdapters.contractForTicker(ticker)` builds the
bare form. Generic `CurrencyPair` calls are rejected with
`InstrumentNotValidException` before any request is made.

## Side semantics

Kalshi quotes event markets from the YES side; the adapters apply four named
provider rules (see `KalshiAdapters`):

* `RULE_YES_LEG_ONLY` — generic BID places native `bid` (buy YES); generic ASK
  places native `ask` (sell YES).
* `RULE_NO_BID_COMPLEMENT` — NO-side book bids are exposed as YES asks at the
  complement price `1 - noPrice` dollars.
* `RULE_LEGACY_NO_COMPLEMENT` — legacy order/fill reads convert `buy NO at q` to
  ASK YES at `1 - q` and `sell NO` to BID YES at `1 - q`.
* `RULE_SIDE_NO_REJECTED` — the explicit `KalshiOrderFlags.SIDE_NO` flag is
  rejected with `NotAvailableFromExchangeException`; NO-leg placement is never
  silently complemented into a YES order.

## Order placement and safety

Only limit orders are supported; `placeMarketOrder` throws
`NotAvailableFromExchangeException`. `Order.userReference` is passed through as
the provider `client_order_id`; Kalshi offers no verified idempotency guarantee
beyond it, and the module never retries an ambiguous placement for you — retry
identity is the caller's. `KalshiOrderFlags` maps to native fields:
`FILL_OR_KILL` / `IMMEDIATE_OR_CANCEL` select the time-in-force, and
`POST_ONLY`, `CANCEL_ON_PAUSE`, `REDUCE_ONLY` pass through. Cancellation
requires `CancelOrderByIdParams` with the provider order id.

## Sample

```java
ExchangeSpecification spec = new ExchangeSpecification(KalshiExchange.class);
spec.setApiKey("<api key id>");
spec.setSecretKey("<unencrypted PKCS#8 RSA private key PEM>");
Exchange exchange = ExchangeFactory.INSTANCE.createExchange(spec);
exchange.remoteInit(); // populates PredictionMarketContract instruments

Instrument contract =
    new PredictionMarketContract("kalshi", null, "KXSB-26", "YES", Currency.USD);
OrderBook book = exchange.getMarketDataService().getOrderBook(contract);
String orderId =
    exchange
        .getTradeService()
        .placeLimitOrder(
            new LimitOrder.Builder(Order.OrderType.BID, contract)
                .originalAmount(new BigDecimal("10"))
                .limitPrice(new BigDecimal("0.56"))
                .userReference("my-client-order-id")
                .build());
```

## Unsupported operations

* Market orders (Kalshi V2 event orders require a limit price).
* Explicit NO-leg placement (`SIDE_NO` is rejected, never complemented).
* `CurrencyPair`-typed calls on every service.
* Funding-rate, margin, and leverage surfaces (not prediction-market concepts).
