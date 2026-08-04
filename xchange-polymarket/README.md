# XChange Polymarket

Polymarket prediction-market support over the CLOB, Gamma, and Data REST
surfaces. Instruments are `PredictionMarketContract`s on a single outcome token
of a CLOB market, quoted in USD; the generic `Exchange`, `MarketDataService`,
`TradeService`, and `AccountService` entry points work unchanged.

The default endpoints are `https://clob.polymarket.com` (CLOB),
`https://gamma-api.polymarket.com` (market catalog), and
`https://data-api.polymarket.com` (account data). Override them with the
exchange-specific parameters `polymarket.clob.uri`, `polymarket.gamma.uri`, and
`polymarket.data.uri`.

## Credentials

* `userName` — wallet address owning the positions.
* `apiKey` / `secretKey` / `password` — the L2 API credential triplet (API key,
  secret, passphrase) used for HMAC request signing.
* `polymarket.private.key` (`PolymarketExchange.PARAM_PRIVATE_KEY`) — EOA
  private key (hex) used for L1 EIP-712 order signing.

Keys, signatures, and passphrases never appear in exceptions or logs.

## Instrument identity

```java
PredictionMarketContract contract =
    new PredictionMarketContract(
        "polymarket", null, conditionId, clobTokenId, Currency.USD);
```

The wire form is `PRED/polymarket/<conditionId>/<clobTokenId>/USD`. One CLOB
market yields one contract per outcome token; `remoteInit()` discovers them from
the Gamma catalog. `PolymarketAdapters.tokenId(instrument)` /
`conditionId(instrument)` recover the native ids. Generic `CurrencyPair` calls
are rejected with `InstrumentNotValidException` before any request is made.

## Side semantics

The CLOB quotes prices in dollars per share of the outcome token; the adapters
apply three named provider rules (see `PolymarketAdapters`):

* `RULE_TOKEN_DIRECT` — BUY on the contract's token maps to generic BID, SELL
  to ASK, at the quoted price in dollars per share.
* `RULE_AMOUNT_ENCODING` — maker/taker amounts are 6-decimal fixed-point
  micro-units: BUY posts USDC notional (`size x price`) as makerAmount with
  shares as takerAmount; SELL posts shares as makerAmount with USDC notional as
  takerAmount; half-up rounding.
* `RULE_NO_COMPLEMENT` — outcome tokens are never silently complemented: a CLOB
  record adapts to the contract whose outcomeId is the record's `asset_id`. To
  trade the complement outcome, address its token id explicitly.

## Order placement and safety

Only limit orders are supported; `placeMarketOrder` throws
`NotAvailableFromExchangeException`. A rejected placement surfaces the
provider's reason as an `ExchangeException`. Polymarket offers no verified
idempotency key, so the module never retries an ambiguous placement — recover
via `getOrder` / `getTradeHistory` before re-submitting. Cancellation requires
`CancelOrderByIdParams` with the provider order id.

## Sample

```java
ExchangeSpecification spec = new ExchangeSpecification(PolymarketExchange.class);
spec.setUserName("<wallet address>");
spec.setApiKey("<L2 api key>");
spec.setSecretKey("<L2 secret>");
spec.setPassword("<L2 passphrase>");
spec.setExchangeSpecificParametersItem(
    PolymarketExchange.PARAM_PRIVATE_KEY, "<EOA private key hex>");
Exchange exchange = ExchangeFactory.INSTANCE.createExchange(spec);
exchange.remoteInit(); // populates PredictionMarketContract instruments

Instrument contract =
    new PredictionMarketContract(
        "polymarket", null, conditionId, clobTokenId, Currency.USD);
OrderBook book = exchange.getMarketDataService().getOrderBook(contract);
String orderId =
    exchange
        .getTradeService()
        .placeLimitOrder(
            new LimitOrder.Builder(Order.OrderType.BID, contract)
                .originalAmount(new BigDecimal("10"))
                .limitPrice(new BigDecimal("0.56"))
                .build());
```

## Unsupported operations

* Market orders (CLOB placement requires a limit price).
* `CurrencyPair`-typed calls on every service.
* The RTDS crypto-oracle and sports WebSocket surfaces (CLOB streams live in
  `xchange-stream-polymarket`).
* Funding-rate, margin, and leverage surfaces (not prediction-market concepts).
