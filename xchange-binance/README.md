# XChange Binance

Modernized, product-family-aware Binance integration covering Spot, Wallet/SAPI, USDⓈ-M
Futures, COIN-M Futures, and Portfolio Margin through explicit, versioned API boundaries.

## Product families

Every endpoint is owned by exactly one product-family client. The legacy `Binance`,
`BinanceAuthenticated`, `BinanceFutures`, and `BinanceFuturesAuthenticated` wire interfaces are
deprecated facades retained for source compatibility during the documented grace period; new
code must use the family clients.

| Family | Wire client | REST host | Status |
|---|---|---|---|
| Spot (public) | `org.knowm.xchange.binance.spot.BinanceSpotApi` | `api.binance.com` | Implemented |
| Spot (authenticated) | `org.knowm.xchange.binance.spot.BinanceSpotAuthApi` | `api.binance.com` | Implemented |
| Wallet/SAPI | `org.knowm.xchange.binance.wallet.BinanceWalletApi` | `api.binance.com` | Implemented |
| USDⓈ-M Futures | `org.knowm.xchange.binance.usdm.BinanceUsdmApi` / `BinanceUsdmAuthApi` | `fapi.binance.com` | Implemented |
| COIN-M Futures | `org.knowm.xchange.binance.coinm.BinanceCoinmAuthApi` | `dapi.binance.com` | Trading implemented; public market data not yet |
| Margin | — | `api.binance.com` | Not yet implemented |
| Options | — | `eapi.binance.com` | Not implemented; selecting it fails validation |
| Portfolio Margin | `org.knowm.xchange.binance.portfoliomargin.BinancePortfolioMarginApi` | `papi.binance.com` | Trading implemented |

## Capability matrix

Legend: ✅ implemented · ⚠️ partial · ❌ not implemented.

### Market data (generic + raw)

| Capability | Spot | USDⓈ-M | COIN-M |
|---|---|---|---|
| Ping / server time | ✅ | ⚠️ (via spot) | ❌ |
| Exchange info (instruments/filters) | ✅ | ✅ | ❌ |
| Order book depth | ✅ | ✅ | ❌ |
| Aggregate trades | ✅ | ✅ | ❌ |
| Klines | ✅ | ✅ | ❌ |
| 24h ticker (symbol / all) | ✅ | ✅ | ❌ |
| Ticker price / book ticker | ✅ | ❌ | ❌ |
| Funding rate / info / history | — | ✅ | ❌ |
| System status | ✅ (wallet) | ✅ (wallet) | ✅ (wallet) |

### Account, wallet, trading

| Capability | Spot | Wallet/SAPI | USDⓈ-M | COIN-M | Portfolio Margin |
|---|---|---|---|---|---|
| Balances / account | ✅ | — | ✅ | ❌ | ❌ |
| Orders (place/test/status) | ✅ | — | ✅ | ✅ | ✅ |
| Cancel (single/all) | ✅ | — | ✅ | ✅ | ✅ |
| Amend (cancel-replace) | ❌ | — | ✅ | ❌ | ❌ |
| Open/all orders, my trades | ✅ | — | ✅ | ✅ | ✅ |
| Position risk / leverage / margin mode | — | — | ✅ | ❌ | ❌ |
| Deposit/withdraw/history | — | ✅ | — | — | — |
| Dust, dividends, simple-earn | — | ✅ | — | — | — |
| Sub-account transfers, fiat orders | — | ✅ | — | — | — |
| Trade fees | — | ✅ | ✅ | ❌ | ❌ |

### Authentication, time, errors

| Contract | Support |
|---|---|
| Key algorithms | HMAC-SHA256 ✅ · RSA ✅ · Ed25519 ✅ |
| Timestamp unit | milliseconds (default) ✅ · microseconds (futures) ✅ |
| Receive window | typed, validated to `[0, 60000]` ms ✅ |
| Server-time drift | delta with 10-minute expiry ✅ |
| Structured errors | product family, endpoint, code, retry classification, client order id, redacted ✅ |
| Retry classification | `BinanceErrorClassifier` (replay-safe / no-retry / reconcile / rate-limited / transient / auth) ✅ |
| Endpoint policies | `BinanceEndpointPolicies` registry (weight, order-count, retry safety) ✅ |
| Rate-limit telemetry | `x-mbx-used-weight-1m`, `x-mbx-order-count-10s`, `Retry-After` parsing ✅ |
| Redaction | API keys, signatures, PEM private keys ✅ |

## Configuration

Typed exchange-specific parameters (see `org.knowm.xchange.binance.config.BinanceConfiguration`):

| Parameter | Type | Default |
|---|---|---|
| `Binance_ProductFamily` | `BinanceProductFamily` | `SPOT` |
| `Binance_KeyAlgorithm` | `BinanceKeyAlgorithm` | `HMAC_SHA_256` |
| `Binance_TimestampUnit` | `BinanceTimestampUnit` | `MILLISECONDS` |
| `Binance_RecvWindow` | `Long` (`[0, 60000]`) | Binance default |
| `Binance_RestBaseUrl` | `String` | family base URL |
| `Binance_StreamBaseUrl` | `String` | family stream URL |
| `Binance_OrderBookDepth` | positive `Integer` | `1000` |
| `Binance_OrderBookUpdateCadenceMs` | positive `Integer` | `100` |
| `Use_Sandbox` | `Boolean` | `false` |

Invalid values and unsupported combinations (for example the Options family) fail during
specification application with an actionable message. Legacy parameters (`Exchange_Type`,
`ed25519`, `recvWindow`) remain honored for source compatibility.

```java
ExchangeSpecification spec = new ExchangeSpecification(BinanceExchange.class);
spec.setExchangeSpecificParametersItem(BinanceConfiguration.PRODUCT_FAMILY, BinanceProductFamily.USDM);
spec.setExchangeSpecificParametersItem(BinanceConfiguration.KEY_ALGORITHM, BinanceKeyAlgorithm.RSA);
spec.setExchangeSpecificParametersItem(BinanceConfiguration.TIMESTAMP_UNIT, BinanceTimestampUnit.MICROSECONDS);
```

### Key algorithms

* **HMAC-SHA256** — the default; the shared secret is the `secretKey`.
* **RSA** — `secretKey` holds the PKCS#8 private key as PEM or bare Base64; signatures are
  SHA256withRSA, Base64-encoded.
* **Ed25519** — `secretKey` holds the PKCS#8 Ed25519 private key as Base64.

## Migration examples

Legacy configuration (still honored during the grace period):

```java
ExchangeSpecification spec = new ExchangeSpecification(BinanceExchange.class);
spec.setExchangeSpecificParametersItem(BinanceExchange.EXCHANGE_TYPE, ExchangeType.FUTURES);
spec.setExchangeSpecificParametersItem("ed25519", true);
```

Modern equivalent configurations:

```java
// Spot-only, HMAC
spec.setExchangeSpecificParametersItem(
    BinanceConfiguration.PRODUCT_FAMILY, BinanceProductFamily.SPOT);
spec.setExchangeSpecificParametersItem(
    BinanceConfiguration.KEY_ALGORITHM, BinanceKeyAlgorithm.HMAC_SHA_256);

// USDⓈ-M Futures, RSA, microsecond timestamps
spec.setExchangeSpecificParametersItem(
    BinanceConfiguration.PRODUCT_FAMILY, BinanceProductFamily.USDM);
spec.setExchangeSpecificParametersItem(
    BinanceConfiguration.KEY_ALGORITHM, BinanceKeyAlgorithm.RSA);
spec.setExchangeSpecificParametersItem(
    BinanceConfiguration.TIMESTAMP_UNIT, BinanceTimestampUnit.MICROSECONDS);

// COIN-M Futures, Ed25519
spec.setExchangeSpecificParametersItem(
    BinanceConfiguration.PRODUCT_FAMILY, BinanceProductFamily.COINM);
spec.setExchangeSpecificParametersItem(
    BinanceConfiguration.KEY_ALGORITHM, BinanceKeyAlgorithm.ED25519);

// Portfolio Margin, HMAC
spec.setExchangeSpecificParametersItem(
    BinanceConfiguration.PRODUCT_FAMILY, BinanceProductFamily.PORTFOLIO_MARGIN);
```

Behavioral changes to expect: typed family selection fails fast for unimplemented families
(Options), invalid receive windows are rejected during specification application, and raw
services address their product family through narrow clients — the deprecated wide wire
interfaces remain available but should not be used by new code.

## Order placement and replay safety

Order placement is **non-replayable** after an ambiguous transport result (timeout or 5xx).
The library never blindly resubmits a placement; recovery is a bounded reconciliation query by
client order ID (`Order.userReference` maps to `newClientOrderId`). Reads and cancellations may
retry under the resilience configuration because their request identity makes replay safe.

## Sandbox

| Family | Sandbox URL |
|---|---|
| Spot / Wallet / Margin | `https://testnet.binance.vision` |
| USDⓈ-M / COIN-M | `https://testnet.binancefuture.com` |
| Portfolio Margin | `https://testnet.binancefuture.com` |

Enable with `Use_Sandbox=true`.

## Using IntelliJ Idea HTTP client

There are *.http files stored in `src/test/resources/rest` that can be used with IntelliJ Idea HTTP Client.

Some requests need authorization, so the api credentials have to be stored in `http-client.private.env.json` in module's root. Sample content can be found in `example.http-client.private.env.json`

> [!CAUTION]
> Never commit your api credentials to the repository!

[HTTP Client documentation](https://www.jetbrains.com/help/idea/http-client-in-product-code-editor.html)

## Streaming

Streaming lives in `xchange-stream-binance`; see its README for subscription, reconnect, and
order-book recovery behavior.
