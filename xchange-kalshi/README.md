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

Kalshi quotes event markets from the YES side with fixed-point wire values:
prices are dollar strings with up to 4 decimals (`*_dollars`, e.g. `"0.4217"`)
and contract counts are strings with up to 2 decimals (`*_fp`, e.g. `"13.50"`).
The market's `price_ranges` bands define the valid price grid; the adapters
apply four named provider rules (see `KalshiAdapters`):

* `RULE_YES_LEG_ONLY` — generic BID places native `bid` (buy YES); generic ASK
  places native `ask` (sell YES).
* `RULE_NO_BID_COMPLEMENT` — NO-side book bids are exposed as YES asks at the
  complement price `1 - noPrice` dollars.
* `RULE_BOOK_SIDE_DIRECTION` — order/fill/trade reads derive direction from the
  canonical `book_side`: `bid` maps to generic BID (buy YES), `ask` maps to
  generic ASK (sell YES). Prices are always read from `yes_price_dollars`,
  which the provider quotes on the YES leg for every direction — a `buy NO at
  q` record reads as an ASK YES at `1 - q`.
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

Submitted limit prices and counts are preserved verbatim — never rounded. A
price with more than 4 decimal places (or outside `(0, 1)` dollars) or a count
with more than 2 decimal places (or non-positive) is rejected with
`IllegalArgumentException` before any HTTP call, since rounding would place a
materially different instruction than the user requested.

## Pagination

Generic collection reads — `getOpenOrders`, `getTradeHistory`,
`getOpenPositions`, and `getAllOpenKalshiMarkets` — follow the provider's
`cursor` pagination to exhaustion (bounded to 100 pages, de-duplicated by id)
and fail loudly instead of silently returning a truncated account or catalog.
`remoteInit()` registers only markets whose lifecycle status is `active`.

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
