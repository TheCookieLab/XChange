# Coinbase Advanced Trade v3 Capability Matrix

Status of the Coinbase Advanced Trade REST and WebSocket implementation in
`xchange-coinbase` / `xchange-stream-coinbase` as of 2026-08-09 (PRD CF-447).

Legend: `Raw` = exchange-specific method on the raw service; `Generic` = XChange
DTO through the high-level service; `Fixtures` = deterministic JSON fixtures in
`src/test/resources`; `Sandbox` = covered by `*SandboxIntegration` /
`CoinbaseSandboxEndpointMatrixIntegration`; `Smoke` = covered by credential-gated
`*Integration` tests; `Gap` = known limitation tracked by PRD CF-447.

## REST endpoints (authenticated, `CoinbaseAuthenticated`, `/api/v3/brokerage`)

| Capability | Endpoint | Raw method | Generic | Pagination | Fixtures | Sandbox | Smoke | Notes |
|---|---|---|---|---|---|---|---|---|
| List accounts | `GET accounts` | `CoinbaseAccountServiceRaw#getCoinbaseAccounts` | `CoinbaseAccountService#getAccountInfo` | cursor loop, limit 250 | example-accounts-response.json | yes | yes | loop lacks repeated-cursor guard (Gap) |
| Account by id | `GET accounts/{account_id}` | `#getCoinbaseAccount` | — | — | — | yes | yes | |
| Create order | `POST orders` | `CoinbaseTradeServiceRaw#createOrder` | `placeLimitOrder`/`placeMarketOrder`/`placeStopOrder` | — | order fixtures | yes | yes | `client_order_id` from `Order.userReference`; ambiguous transport not classified (Gap) |
| Edit order | `POST orders/edit` | `#editOrder` | — | — | order-config fixtures | yes | yes | |
| Preview order | `POST orders/preview` | `#previewOrder` | `verifyOrder`-style | — | — | yes | yes | |
| Preview edit | `POST orders/edit_preview` | `#previewEditOrder` | — | — | — | yes | yes | |
| Batch cancel | `POST orders/batch_cancel` | `#cancelOrders`/`#cancelOrderById` | `cancelOrder` | — | — | yes | yes | request body built from ids |
| Order history | `GET orders/historical/batch` | `#listOrders` | — | single page only (Gap) | example-list-orders | yes | yes | no high-level iteration (Gap) |
| Fills | `GET orders/historical/fills` | `#listFills` | `getTradeHistory` | cursor loop, limit-aware | example-fills-response.json | yes | yes | loop lacks repeated-cursor guard (Gap) |
| Order detail | `GET orders/historical/{order_id}` | `#getOrder` | `getOrder` | — | example-order-detail | yes | yes | |
| Close position | `POST orders/close_position` | `#closePosition` | — | — | futures request fixtures | yes | yes | |
| Best bid/ask | `GET best_bid_ask` | `CoinbaseMarketDataServiceRaw#getBestBidAsk` | — | — | example-best-bid-asks-response.json | yes | yes | public fallback requires product id |
| Product book | `GET product_book` | `#getProductBook` | `getOrderBook` | — | pricebook fixtures | yes | yes | |
| List products | `GET products` | `#listProducts` | — | single page only (Gap) | example-product-response.json | yes | yes | identity catalog source (CF-447) |
| Product detail | `GET products/{product_id}` | `#getProduct` | — | — | example-product-response.json | yes | yes | |
| Candles | `GET products/{product_id}/candles` | `#getProductCandles` | `getCandleStickData` | limit ≤ 350 | example-candles-response.json | yes | yes | |
| Market trades / ticker | `GET products/{product_id}/ticker` | `#getMarketTrades` | `getTrades` | — | — | yes | yes | |
| Transaction summary / fees | `GET transaction_summary` | `CoinbaseAccountServiceRaw#getTransactionSummary` | `CoinbaseAccountService#getFee` | — | example-time-response + fee fixtures | yes | yes | product type/venue filters |
| Payment methods | `GET payment_methods` + `GET payment_methods/{id}` | `#getCoinbasePaymentMethods`/`#getCoinbasePaymentMethod` | — | — | — | yes | yes | |
| List portfolios | `GET portfolios` | `#listPortfolios` | — | — | portfolio fixtures | yes | yes | |
| Create/edit/delete portfolio | `POST/PUT/DELETE portfolios[/{uuid}]` | `#createPortfolio`/`#editPortfolio`/`#deletePortfolio` | — | — | portfolio fixtures | yes | yes | |
| Portfolio breakdown | `GET portfolios/{portfolio_uuid}` | `#getPortfolioBreakdown` | — | — | portfolio fixtures | yes | yes | |
| Move funds | `POST portfolios/move_funds` | `#movePortfolioFunds` | — | — | move-funds fixtures | yes | yes | |
| Convert quote | `POST convert/quote` | `CoinbaseTradeServiceRaw#createConvertQuote` | — | — | convert fixtures | yes | yes | |
| Convert trade | `POST convert/trade/{trade_id}` / `GET convert/trade/{trade_id}` | `#commitConvertTrade`/`#getConvertTrade` | — | — | convert fixtures | yes | yes | |
| CFM balance summary | `GET cfm/balance_summary` | `CoinbaseAccountServiceRaw#getFuturesBalanceSummary` | `CoinbaseAdapters#adaptFuturesBalanceSummary` | — | futures fixtures | yes | yes | |
| CFM positions | `GET cfm/positions` + `GET cfm/positions/{product_id}` | `CoinbaseTradeServiceRaw#listFuturesPositions`/`#getFuturesPosition` | `OpenPositions` | — | futures fixtures | yes | yes | |
| CFM sweeps | `POST cfm/sweeps/schedule`, `GET cfm/sweeps`, `DELETE cfm/sweeps` | `#scheduleFuturesSweep`/`#listFuturesSweeps`/`#cancelFuturesSweep` | — | — | futures fixtures | yes | yes | |
| CFM intraday margin | `GET/POST cfm/intraday/margin_setting`, `GET cfm/intraday/current_margin_window` | `#getIntradayMarginSetting`/`#setIntradayMarginSetting`/`#getCurrentMarginWindow` | — | — | futures fixtures | yes | yes | |
| INTX portfolio summary | `GET intx/portfolio/{portfolio_uuid}` | `#getPerpetualsPortfolioSummary` | — | — | perpetual fixtures | yes | yes | |
| INTX balances | `GET intx/balances/{portfolio_uuid}` | `#getPerpetualsPortfolioBalances` | `CoinbaseAdapters` balance mapping | — | perpetual fixtures | yes | yes | |
| INTX positions | `GET intx/positions/{portfolio_uuid}[/{symbol}]` | `#listPerpetualsPositions`/`#getPerpetualsPosition` | `OpenPositions` | — | perpetual fixtures | yes | yes | |
| INTX multi-asset collateral | `POST intx/multi_asset_collateral` | `#optInMultiAssetCollateral` | — | — | perpetual fixtures | yes | yes | |
| INTX allocate | `POST intx/allocate` | `#allocatePortfolio` | — | — | perpetual fixtures | yes | yes | |
| Key permissions | `GET key_permissions` | `CoinbaseAccountServiceRaw#getKeyPermissions` | — | — | — | yes | yes | |

## REST endpoints (public, `Coinbase`, 1 s cache)

| Capability | Endpoint | Raw method | Notes |
|---|---|---|---|
| Server time | `GET time` | `Coinbase#getTime` | public |
| Public product book | `GET market/product_book` | `#getPublicProductBook` | used as unauthenticated fallback |
| Public products | `GET market/products` | `#listPublicProducts` | offset/limit paged, single call |
| Public product | `GET market/products/{product_id}` | `#getPublicProduct` | |
| Public candles | `GET market/products/{product_id}/candles` | `#getPublicProductCandles` | 1 s cache |
| Public market trades | `GET market/products/{product_id}/ticker` | `#getPublicMarketTrades` | 1 s cache |

## WebSocket channels (`CoinbaseChannel`, `xchange-stream-coinbase`)

| Channel | Auth | Service | Fixtures/tests | Notes |
|---|---|---|---|---|
| `ticker` | no | `CoinbaseStreamingMarketDataService#getTicker` | CoinbaseStreamingMarketDataServiceTest | |
| `ticker_batch` | no | — (enum only) | — | not wired (Gap) |
| `market_trades` | no | `#getTrades` | CoinbaseStreamingMarketDataServiceTest | |
| `candles` | no | `#getCandles` | CoinbaseStreamingMarketDataServiceTest | granularity + product type params |
| `level2` / `level2_batch` | no | `#getOrderBook`/`#getOrderBookBatch` | CoinbaseStreamingMarketDataServiceTest | snapshot+delta, sequence-gap REST recovery (no gap event to subscribers, Gap) |
| `l2_data` | no | — (channel-name normalization only) | CoinbaseStreamingServiceTest | mapped to level2/level2_batch in `getChannelNameFromMessage` |
| `status` | no | — (enum only) | — | not wired (Gap) |
| `heartbeats` | no | `#getHeartbeats` + auto-subscribe | CoinbaseStreamingServiceTest | disabled via `PARAM_MANUAL_HEARTBEAT` |
| `user` | yes | `CoinbaseStreamingTradeService#getUserOrderEvents`/`getOrderChanges`/`getUserTrades` | CoinbaseStreamingTradeServiceTest | fill-delta dedup, terminal-order TTL; rides the market-data socket (Gap: dual-endpoint lifecycle) |
| `futures_balance_summary` | yes | `#getFuturesBalanceSummary` | CoinbaseStreamingTradeServiceTest | |

## Cross-cutting gaps (tracked by PRD CF-447)

1. No checked-in matrix before this document; drift between interface annotations and implementation is unverified by CI.
2. Global `PARAM_PRODUCT_ID_OVERRIDE` replaces product identity for the whole exchange instance; futures/perpetual ids are lossy through `CoinbaseProductIds` / `CoinbaseStreamingAdapters#toCurrencyPair` (2-token split).
3. Single resolved WebSocket endpoint per exchange instance; `USER_ORDER_DATA_WS_URI` unused in production; no dual-socket lifecycle.
4. Reflective WS-JWT helper fallback (`Class.forName` on `CoinbaseWebsocketAuthentication`); REST `ParamsDigest` and WS `Supplier<String>` are separate untyped contracts.
5. No request correlation, connection-generation tracking, redaction, or replay classification in the spot stream; INFO logging emits full subscribe payloads incl. JWTs.
6. Cursor pagination loops (`getCoinbaseAccounts`, `getTradeHistory`) lack repeated-cursor/no-progress guards; `listOrders` and `listProducts` are single-page.
7. `CoinbaseException` carries HTTP status only; no provider code/type, correlation id, or retry classification.
8. Sandbox coverage is partial and treats 4xx as reachable (`CoinbaseSandboxEndpointMatrixIntegration` with synthetic ids); no WebSocket sandbox exists.
