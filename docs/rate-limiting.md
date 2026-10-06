# REST Rate Limiting Adoption

XChange rate limiting for REST/JSON-RPC transports is owned by one universal limiter in the
core module (package `org.knowm.xchange.client.ratelimit`, delivered by CF-681). Each wire attempt
has exactly **one** rate-admission and feedback owner: the core boundary. Adapters declare a
`RateLimitPolicy` (budgets, operation classification, feedback interpretation); they do not
implement limiting, cooldowns or replay of rate rejections themselves.

This page is the adoption inventory (CF-683, AC1) and the one integration path for new adapters.

## Adoption table

Generated from the root `pom.xml` `<modules>` list (103 modules: 67 non-stream below,
36 stream modules in the next section). Every module appears in exactly one row or in
the stream line. Source traversal date: 2026-10-06.

Dispositions:

- `adopted` - the module's default `ExchangeSpecification` sets a universal `RateLimitPolicy` and
  `rateLimiterEnabled(true)`; its remote path goes through the core boundary. The Coinbase
  modules were adopted before CF-683; the modules marked "(CF-683)" were migrated by CF-683 and
  no longer apply a resilience4j rate limiter on any REST path. Evidence cites the transport
  construction site and the module's policy class.
- `pending` - the module has a reachable remote REST/JSON-RPC path (rescu proxies built through
  `ExchangeRestProxyBuilder`) but no universal policy yet. None of these modules contains a
  `RateLimiter` or `RateLimitPolicy` reference in `src/main` (verified by grep), so they currently
  have no client-side limiter at all; each is migrated by the integration path below.
- `no-remote` - the module owns no remote transport (proof cited).
- `platform` - the core module itself: it hosts the enforcement layer and is not an adapter.

Evidence paths are relative to the row's module directory unless stated otherwise.

| Module | Remote transport owner | Disposition | Evidence |
|---|---|---|---|
| `xchange-ascendex` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | pending | `src/main/java/org/knowm/xchange/ascendex/service/AscendexBaseService.java` |
| `xchange-bibox` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | pending | `src/main/java/org/knowm/xchange/bibox/service/BiboxBaseService.java` |
| `xchange-binance` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | adopted (CF-683) | `src/main/java/org/knowm/xchange/binance/service/BinanceBaseService.java`; `src/main/java/org/knowm/xchange/binance/BinanceRateLimitPolicy.java` |
| `xchange-bitcoinaverage` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | pending | `src/main/java/org/knowm/xchange/bitcoinaverage/service/BitcoinAverageMarketDataServiceRaw.java` |
| `xchange-bitcoincore` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | pending | `src/main/java/org/knowm/xchange/bitcoincore/service/BitcoinCoreAccountServiceRaw.java` |
| `xchange-bitcoinde` | rescu proxy via `ExchangeRestProxyBuilder` (2 files) | pending | `src/main/java/org/knowm/xchange/bitcoinde/service/BitcoindeBaseService.java` |
| `xchange-bitfinex` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | adopted (CF-683) | `src/main/java/org/knowm/xchange/bitfinex/service/BitfinexBaseService.java`; `src/main/java/org/knowm/xchange/bitfinex/BitfinexRateLimitPolicy.java` |
| `xchange-bitflyer` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | pending | `src/main/java/org/knowm/xchange/bitflyer/service/BitflyerBaseService.java` |
| `xchange-bitget` | rescu proxy via `ExchangeRestProxyBuilder` (2 files) | pending | `src/main/java/org/knowm/xchange/bitget/service/BitgetBaseService.java` |
| `xchange-bitget-futures` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | pending | `src/main/java/org/knowm/xchange/bitget/service/BitgetFuturesBaseService.java` |
| `xchange-bithumb` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | pending | `src/main/java/org/knowm/xchange/bithumb/service/BithumbBaseService.java` |
| `xchange-bitmex` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | pending | `src/main/java/org/knowm/xchange/bitmex/service/BitmexBaseService.java` |
| `xchange-bitso` | rescu proxy via `ExchangeRestProxyBuilder` (3 files) | pending | `src/main/java/org/knowm/xchange/bitso/service/BitsoAccountServiceRaw.java` |
| `xchange-bitstamp` | rescu proxy via `ExchangeRestProxyBuilder` (3 files) | pending | `src/main/java/org/knowm/xchange/bitstamp/service/BitstampAccountServiceRaw.java` |
| `xchange-bity` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | pending | `src/main/java/org/knowm/xchange/bity/service/BityBaseService.java` |
| `xchange-blockchain` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | adopted (CF-683) | `src/main/java/org/knowm/xchange/blockchain/BlockchainExchange.java`; `src/main/java/org/knowm/xchange/blockchain/BlockchainRateLimitPolicy.java` |
| `xchange-btcc` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | pending | `src/main/java/org/knowm/xchange/btcc/service/BTCCBaseService.java` |
| `xchange-btcmarkets` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | pending | `src/main/java/org/knowm/xchange/btcmarkets/service/BTCMarketsBaseService.java` |
| `xchange-btcturk` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | pending | `src/main/java/org/knowm/xchange/btcturk/service/BTCTurkBaseService.java` |
| `xchange-bybit` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | adopted (CF-683) | `src/main/java/org/knowm/xchange/bybit/service/BybitBaseService.java`; `src/main/java/org/knowm/xchange/bybit/BybitRateLimitPolicy.java` |
| `xchange-cexio` | rescu proxy via `ExchangeRestProxyBuilder` (2 files) | pending | `src/main/java/org/knowm/xchange/cexio/service/CexIOBaseService.java` |
| `xchange-coinbase` | rescu proxy via `ExchangeRestProxyBuilder` (2 files) | adopted | `src/main/java/org/knowm/xchange/coinbase/v3/service/CoinbaseAccountServiceRaw.java`; `src/main/java/org/knowm/xchange/coinbase/v3/CoinbaseRateLimitPolicy.java` |
| `xchange-coinbase-derivatives` | direct: JSON-RPC over `java.net.http.HttpClient`, admitted by `RateLimitContext.execute` | adopted | `src/main/java/org/knowm/xchange/coinbasederivatives/client/CoinbaseDerivativesJsonRpcTransport.java`; `src/main/java/org/knowm/xchange/coinbasederivatives/client/CoinbaseDerivativesRateLimitPolicy.java` |
| `xchange-coincheck` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | pending | `src/main/java/org/knowm/xchange/coincheck/service/CoincheckMarketDataServiceRaw.java` |
| `xchange-coinex` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | pending | `src/main/java/org/knowm/xchange/coinex/service/CoinexBaseService.java` |
| `xchange-coinjar` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | pending | `src/main/java/org/knowm/xchange/coinjar/service/CoinjarBaseService.java` |
| `xchange-coinmarketcap` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | pending | `src/main/java/org/knowm/xchange/coinmarketcap/pro/v1/service/CmcBaseService.java` |
| `xchange-coinmate` | rescu proxy via `ExchangeRestProxyBuilder` (4 files) | pending | `src/main/java/org/knowm/xchange/coinmate/service/CoinmateAccountServiceRaw.java` |
| `xchange-coinone` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | pending | `src/main/java/org/knowm/xchange/coinone/service/CoinoneBaseService.java` |
| `xchange-coinsph` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | adopted (CF-683) | `src/main/java/org/knowm/xchange/coinsph/CoinsphExchange.java`; `src/main/java/org/knowm/xchange/coinsph/CoinsphRateLimitPolicy.java` |
| `xchange-core` | owns the enforcement layer: `ExchangeRestProxyBuilder` builds rescu proxies; `si.mazi.rescu.SingleAttemptConnection` is the `java.net.http.HttpClient` wire attempt | platform | `src/main/java/org/knowm/xchange/client/ExchangeRestProxyBuilder.java`; `src/main/java/si/mazi/rescu/SingleAttemptConnection.java` |
| `xchange-cryptocom` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | adopted (CF-683) | `src/main/java/org/knowm/xchange/cryptocom/CryptoComExchange.java`; `src/main/java/org/knowm/xchange/cryptocom/CryptoComRateLimitPolicy.java` |
| `xchange-dase` | rescu proxy via `ExchangeRestProxyBuilder` (3 files) | pending | `src/main/java/org/knowm/xchange/dase/service/DaseBaseService.java` |
| `xchange-deribit` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | pending | `src/main/java/org/knowm/xchange/deribit/v2/service/DeribitBaseService.java` |
| `xchange-dvchain` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | pending | `src/main/java/org/knowm/xchange/dvchain/service/DVChainBaseService.java` |
| `xchange-dydx` | none: `dydxExchange.initServices()` is empty, the module has no other class, no service, no HTTP/rescu/web3j import | no-remote | `src/main/java/org/knowm/xchange/dydx/dydxExchange.java` (sole source file; `initServices()` is empty) |
| `xchange-enigma` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | pending | `src/main/java/org/knowm/xchange/enigma/service/EnigmaBaseService.java` |
| `xchange-examples` | none of its own: demos call adapters through `ExchangeFactory`; its one direct proxy (`CoinbaseMarketDataDemo`) already goes through `ExchangeRestProxyBuilder` with the Coinbase v3 spec | no-remote | `src/main/java/org/knowm/xchange/examples/coinbase/marketdata/CoinbaseMarketDataDemo.java` line 76 (the only proxy construction in `src/main`) |
| `xchange-exmo` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | pending | `src/main/java/org/knowm/xchange/exmo/service/BaseExmoService.java` |
| `xchange-gateio-v4` | rescu proxy via `ExchangeRestProxyBuilder` (1 file); the custom `restProxyFactory` was removed and the `apache` connection type is applied only when rate limiting is disabled | adopted (CF-683) | `src/main/java/org/knowm/xchange/gateio/service/GateioBaseService.java`; `src/main/java/org/knowm/xchange/gateio/GateioRateLimitPolicy.java` |
| `xchange-gemini` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | pending | `src/main/java/org/knowm/xchange/gemini/v1/service/GeminiBaseService.java` |
| `xchange-hitbtc` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | pending | `src/main/java/org/knowm/xchange/hitbtc/v2/service/HitbtcBaseService.java` |
| `xchange-huobi` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | pending | `src/main/java/org/knowm/xchange/huobi/service/HuobiBaseService.java` |
| `xchange-independentreserve` | rescu proxy via `ExchangeRestProxyBuilder` (3 files) | pending | `src/main/java/org/knowm/xchange/independentreserve/service/IndependentReserveAccountServiceRaw.java` |
| `xchange-itbit` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | pending | `src/main/java/org/knowm/xchange/itbit/service/ItBitBaseService.java` |
| `xchange-kalshi` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | pending | `src/main/java/org/knowm/xchange/kalshi/service/KalshiBaseService.java` |
| `xchange-kraken` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | pending | `src/main/java/org/knowm/xchange/kraken/service/KrakenBaseService.java` |
| `xchange-krakenfutures` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | pending | `src/main/java/org/knowm/xchange/krakenfutures/service/KrakenFuturesBaseService.java` |
| `xchange-kucoin` | rescu proxy via `ExchangeRestProxyBuilder` (2 files) | adopted (CF-683) | `src/main/java/org/knowm/xchange/kucoin/KucoinBaseService.java`; `src/main/java/org/knowm/xchange/kucoin/KucoinRateLimitPolicy.java` |
| `xchange-latoken` | rescu proxy via `ExchangeRestProxyBuilder` (2 files) | pending | `src/main/java/org/knowm/xchange/latoken/LatokenExchange.java` |
| `xchange-luno` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | pending | `src/main/java/org/knowm/xchange/luno/LunoAPIImpl.java` |
| `xchange-mercadobitcoin` | rescu proxy via `ExchangeRestProxyBuilder` (3 files) | pending | `src/main/java/org/knowm/xchange/mercadobitcoin/service/MercadoBitcoinAccountServiceRaw.java` |
| `xchange-mexc` | rescu proxy via `ExchangeRestProxyBuilder` (2 files) | pending | `src/main/java/org/knowm/xchange/mexc/service/MEXCBaseService.java` |
| `xchange-okex` | rescu proxy via `ExchangeRestProxyBuilder` (2 files) | adopted (CF-683) | `src/main/java/org/knowm/xchange/okex/service/OkexBaseService.java`; `src/main/java/org/knowm/xchange/okx/OkxRateLimitPolicy.java` |
| `xchange-openexchangerates` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | pending | `src/main/java/org/knowm/xchange/oer/service/OERMarketDataServiceRaw.java` |
| `xchange-paribu` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | pending | `src/main/java/org/knowm/xchange/paribu/service/ParibuMarketDataServiceRaw.java` |
| `xchange-paymium` | rescu proxy via `ExchangeRestProxyBuilder` (3 files) | pending | `src/main/java/org/knowm/xchange/paymium/service/PaymiumAccountServiceRaw.java` |
| `xchange-poloniex` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | pending | `src/main/java/org/knowm/xchange/poloniex/service/PoloniexBaseService.java` |
| `xchange-polymarket` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | pending | `src/main/java/org/knowm/xchange/polymarket/service/PolymarketBaseService.java` |
| `xchange-ripple` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | pending | `src/main/java/org/knowm/xchange/ripple/service/RippleBaseService.java` |
| `xchange-simulated` | none: in-memory matching engine; only imports `java.net.SocketTimeoutException` (to throw it) and Guava `RateLimiter` (in-process fault injector, not a remote path) | no-remote | `src/main/java/org/knowm/xchange/simulated/SimulatedExchange.java` (no transport; `initServices` builds in-memory services); `src/main/java/org/knowm/xchange/simulated/RandomExceptionThrower.java` (Guava `RateLimiter` is an in-process fault injector) |
| `xchange-truefx` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | pending | `src/main/java/org/knowm/xchange/truefx/service/TrueFxMarketDataServiceRaw.java` |
| `xchange-uniswap` | direct: web3j JSON-RPC over OkHttp through `MeteredHttpService`, which admits every wire attempt with `RateLimitContext.execute` (13 classified `eth_*` methods); no rescu | adopted (CF-683) | `src/main/java/org/knowm/xchange/uniswap/client/MeteredHttpService.java`; `src/main/java/org/knowm/xchange/uniswap/client/UniswapRateLimitPolicy.java` |
| `xchange-upbit` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | pending | `src/main/java/org/knowm/xchange/upbit/service/UpbitBaseService.java` |
| `xchange-vaultoro` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | pending | `src/main/java/org/knowm/xchange/vaultoro/service/VaultoroBaseService.java` |
| `xchange-yobit` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | pending | `src/main/java/org/knowm/xchange/yobit/service/YoBitBaseService.java` |
| `xchange-zaif` | rescu proxy via `ExchangeRestProxyBuilder` (1 file) | pending | `src/main/java/org/knowm/xchange/zaif/service/ZaifBaseService.java` |

Totals: 12 adopted (10 of them CF-683 migrations), 51 pending,
3 no-remote, 1 platform.

Notes on the `no-remote` proofs (read from source, not inferred from grep):

- The dYdX module has exactly one Java source file; `dydxExchange.initServices()` is an empty body, so
  no market-data, trade or account service exists and nothing can reach the network.
- The simulated-exchange module is an in-memory matching engine. Its only network-adjacent imports
  are `java.net.SocketTimeoutException` (thrown on purpose by the fault injector) and Guava
  `RateLimiter` (throttles the injected fault rate to mimic exchange behaviour); it has no proxy,
  HTTP client or JSON-RPC client, and Guava's limiter is not a transport limiter.
- The examples module owns no adapter transport; its demos obtain services through
  `ExchangeFactory`, so limiting is whatever the adapter's own specification enables.

Notes on direct (non-rescu) transports found by source traversal: the Coinbase derivatives
JSON-RPC transport (`java.net.http.HttpClient`, admitted by `RateLimitContext.execute`) and the
Uniswap node client (web3j over OkHttp via `MeteredHttpService`). A handful of other modules import
`java.net.HttpURLConnection` only for status-code constants in their error adapters (the
Bitso, HitBTC and Latoken modules); they do not open connections.

## Stream modules

WebSocket transports are out of scope of the REST/JSON-RPC limiter (36 modules). The WebSocket
order APIs of `xchange-stream-binance`, `xchange-stream-bybit` and `xchange-stream-okex` still use
the resilience4j limiters kept for them in `BinanceResilience`, `BybitResilience`/`BybitBaseService`
and `OkxResilience`; no REST path uses those limiters. Stream modules (none
changed by CF-683): `xchange-stream-binance`, `xchange-stream-bitfinex`, `xchange-stream-bitflyer`, `xchange-stream-bitget`, `xchange-stream-bitmex`, `xchange-stream-bitstamp`, `xchange-stream-btcmarkets`, `xchange-stream-bybit`, `xchange-stream-cexio`, `xchange-stream-coinbase`, `xchange-stream-coinbase-derivatives`, `xchange-stream-coincheck`, `xchange-stream-coinjar`, `xchange-stream-coinmate`, `xchange-stream-coinsph`, `xchange-stream-core`, `xchange-stream-cryptocom`, `xchange-stream-deribit`, `xchange-stream-dydx`, `xchange-stream-gateio`, `xchange-stream-gemini`, `xchange-stream-gemini-v2`, `xchange-stream-hitbtc`, `xchange-stream-huobi`, `xchange-stream-kalshi`, `xchange-stream-kraken`, `xchange-stream-kraken-v2`, `xchange-stream-krakenfutures`, `xchange-stream-kucoin`, `xchange-stream-mexc`, `xchange-stream-okex`, `xchange-stream-poloniex2`, `xchange-stream-polymarket`, `xchange-stream-service-core`, `xchange-stream-service-netty`, `xchange-stream-service-pubnub`.

## Integration path for a new adapter

There is one normal path. Copy the shape of `CoinbaseRateLimitPolicy`
(`org.knowm.xchange.coinbase.v3`):

1. **Policy class.** A final, non-instantiable `<Exchange>RateLimitPolicy` with `NAMESPACE` and
   `VERSION` constants, a dated `SOURCE` string (provider doc URLs, retrieval date, and an explicit
   label distinguishing client-side pacing from provider-published quota), the budgets
   (`RateLimitBudget.tokenBucket/fixedWindow/rollingWindow`, one budget id per shared provider
   bucket, `ScopeKind.USER` for authenticated and `ScopeKind.EGRESS` for public), a
   `defaultPolicy()`/`create()` factory and a package-private `classify`. Never invent limits;
   when documentation is unreachable keep the module's existing values and label their provenance.
2. **Default specification.** `getDefaultExchangeSpecification()` calls
   `getResilience().setRateLimitPolicy(<Exchange>RateLimitPolicy.defaultPolicy())` and
   `getResilience().setRateLimiterEnabled(true)`. `BaseExchange` registers the policy in the shared
   `RateLimitContext`.
3. **REST proxies.** Build every rescu proxy with
   `ExchangeRestProxyBuilder.forInterface(Api.class, exchangeSpecification)...build()`. Rate
   limiting needs the default `java` connection type and the builder's default proxy factory:
   `build()` throws `IllegalStateException` for a custom `restProxyFactory(...)` or the `apache`
   connection type because neither can be held to one wire attempt per admission.
4. **Direct transports** (JSON-RPC, web3j, hand-rolled `HttpClient`). Wrap each wire attempt in
   `RateLimitContext.execute(policy, request, userScope, attempt)`; the attempt reports HTTP
   status and headers to the `RateLimitAttemptObserver`. See
   `CoinbaseDerivativesJsonRpcTransport.admitted` for the reference shape.
5. **Classification.** The classifier maps `RateLimitRequest.getOperationKey()`
   (`"<HTTP METHOD> <interface @Path>/<method @Path>"`, no leading slash, placeholders
   unexpanded) to a `RateLimitOperation` with the documented cost. Weight that depends on a
   parameter reads it through `RateLimitRequest.getParameter(name)` (non-null
   `@QueryParam`/`@FormParam`/`@PathParam` values by wire name). `isAuthenticated()` selects the
   user scope. Every reachable REST method must classify: an unclassified operation fails
   terminally with `UNCLASSIFIED_OPERATION`. Non-idempotent economic mutations (order
   placement, edit, withdraw) MUST set `replayOnRateLimit=false`.
6. **No module-owned limiter.** Do not use resilience4j `RateLimiter`
   (`withRateLimiter`, `rateLimiter(...)` lookups, `RateLimiterConfig`), permit draining on 429, or
   custom cooldowns. The core owns cooldown, bounded replay of replay-safe operations with fresh
   admission, and the terminal `RateLimitTerminatedException`.
7. **Retries.** `withRetry` and retry configuration may stay for non-rate failures but must not
   retry rate rejections (HTTP 429/418 or `RateLimitTerminatedException`); a retry layer around the
   core boundary would otherwise double-replay. Non-idempotent mutation retry semantics stay as
   documented in the module's own guide.
8. **Tests.** A reflective classification test that enumerates every rescu interface method key (so
   a new method cannot slip through), cheap versus weighted cost, authenticated versus public
   scope and `replayOnRateLimit=false` for mutations; plus a service-level test proving one
   admission per wire attempt and 429 handling (replay-safe read replays with fresh admission;
   order placement does not blind-replay). Reference tests: `CoinbaseRateLimitPolicyTest` and
   `CoinbaseRateLimitQuotaTest`.
