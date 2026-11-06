# XChange Bybit Stream

WebSocket streaming support for the Bybit V5 API, sharing the environment and
account-type configuration of the REST module
([`xchange-bybit`](../../xchange-bybit/README.md)).

## Environments

The environment is resolved from the exchange specification at
`applySpecification()` time and selects every transport:

| Flag | Public WS | Private WS | Order-entry (trade) WS |
|---|---|---|---|
| *(none)* | `wss://stream.bybit.com/v5/public/{category}` | `wss://stream.bybit.com/v5/private` | `wss://stream.bybit.com/v5/trade` |
| `USE_SANDBOX=true` (demo) | `wss://stream.bybit.com/v5/public/{category}` (mainnet host) | `wss://stream-demo.bybit.com/v5/private` | **not constructed** |
| `test_net=true` | `wss://stream-testnet.bybit.com/v5/public/{category}` | `wss://stream-testnet.bybit.com/v5/private` | `wss://stream-testnet.bybit.com/v5/trade` |

- `USE_SANDBOX=true` and `test_net=true` together fail fast with an
  `IllegalArgumentException` at `applySpecification()`.
- Demo trading does not support the WebSocket order-entry transport. The trade
  transport is not constructed in demo: order/position-change subscriptions
  keep working through the private user-data stream, order operations
  (`placeMarketOrder`, `placeLimitOrder`, `changeOrder`, `batchChangeOrder`,
  `cancelOrder`, `batchCancelOrder`) throw `IllegalStateException`, and
  `connectionStateObservableTradeChannel()` completes empty.

## Channels

| Service | Channel | Payload |
|---|---|---|
| `BybitStreamingMarketDataService.getOrderBook` / `getOrderBookUpdates` | `orderbook.{depth}.{symbol}` | `OrderBook` / `List<OrderBookUpdate>` |
| `BybitStreamingMarketDataService.getTrades` | `publicTrade.{symbol}` | `Trade` |
| `BybitStreamingMarketDataService.getTicker` / `getFundingRate` | `tickers.{symbol}` | `Ticker` / `FundingRate` |
| `BybitStreamingMarketDataService.getCandleStick` | `kline.{interval}.{symbol}` | `CandleStickData` |
| `BybitStreamingTradeService.getOrderChanges` | `order` / `order.{category}` | `Order` |
| `BybitStreamingTradeService.getPositionChanges` | `position` / `position.{category}` | `OpenPosition` |
| `BybitStreamingTradeService.getBybitPositionChanges` | `position` | `BybitComplexPositionChanges` |
| `BybitStreamingTradeService.getComplexOrderChanges` | `order` | `BybitComplexOrderChanges` |
| Order-entry (prod/testnet only) | `order.create` / `order.create-batch` / `order.amend` / `order.amend-batch` / `order.cancel` / `order.cancel-batch` | `BybitStreamOrderResponse` |

`getOrderBook` defaults to depth 50 and accepts one depth or a comma-separated
list. Subscribers at the same depth share a channel. Across all subscriptions
for a symbol, the deepest active depth governs the book; shallower updates do
not change it or emit continuity-gap events. When the governing depth is
removed, the next deepest depth takes over. If its next update reveals a gap,
`getOrderBookGapEvents()` reports the failure and an open WebSocket is
resubscribed to obtain a fresh snapshot. Governing-channel continuity failures
are reported in the same way.

Order and position changes flow through the authenticated private user-data
stream (`wss://…/v5/private`); order-entry operations flow through the separate
order-entry (trade) transport.

Public streams are keyed by category: spot, linear, inverse, option. The
default category is `LINEAR`; set `Exchange_Type` on the specification to
override it (unset previously raised `NullPointerException` during transport
construction).

## Order Entry

Production and testnet construct the order-entry transport
(`wss://…/v5/trade`); `placeMarketOrder`, `placeLimitOrder`, `changeOrder`,
`batchChangeOrder`, `cancelOrder`, and `batchCancelOrder` are routed through it
and require valid API credentials (`getStreamingTradeService()` throws
`IllegalArgumentException` when unauthenticated). See the REST module for order
operations in the demo environment.
