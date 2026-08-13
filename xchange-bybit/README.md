# XChange Bybit

XChange adapter for the [Bybit V5 unified trading API](https://bybit-exchange.github.io/docs/v5/intro).

## Environments

Bybit exposes three environments. The environment is resolved from the exchange
specification at `applySpecification()` time and determines the REST base URL
(`sslUri`), the WebSocket endpoints used by `xchange-stream-bybit`, and the
default account type.

| Flag | Environment | REST base URL | WebSocket base URL | Order-entry (trade) WS |
|---|---|---|---|---|
| *(none)* | Production | `https://api.bybit.com` | `wss://stream.bybit.com` | `wss://stream.bybit.com/v5/trade` |
| `Exchange.USE_SANDBOX=true` | Demo | `https://api-demo.bybit.com` | `wss://stream-demo.bybit.com` (private only) | not supported |
| `BybitExchange.SPECIFIC_PARAM_TESTNET=true` (`test_net`) | Testnet | `https://api-testnet.bybit.com` | `wss://stream-testnet.bybit.com` | `wss://stream-testnet.bybit.com/v5/trade` |

Selection rules:

- Setting both `USE_SANDBOX=true` and `test_net=true` fails fast with an
  `IllegalArgumentException` instead of silently preferring one environment.
- Demo public market data is served from the mainnet host; only private and
  order-entry streams have a dedicated demo host.
- Demo trading has no WebSocket order-entry transport. `xchange-stream-bybit`
  does not construct the trade transport in the demo environment and order
  operations fail with a clear `IllegalStateException`; use the REST trade
  service for order operations in demo.
- An explicitly configured `sslUri` is preserved (custom endpoint, test proxy);
  the environment flags still select the WebSocket endpoints and validate the
  configuration.

## Configuration

| Parameter | Default | Meaning |
|---|---|---|
| `BybitExchange.SPECIFIC_PARAM_ACCOUNT_TYPE` (`accountType`) | `UNIFIED` | Account type for V5 endpoints; must be a value of `BybitAccountType` (`UNIFIED`, `CONTRACT`, `SPOT`, `OPTION`, `INVESTMENT`, `FUND`) |
| `BybitExchange.SPECIFIC_PARAM_TESTNET` (`test_net`) | `false` | Select the testnet environment |
| `Exchange.USE_SANDBOX` | `false` | Select the demo environment |
| `BybitConfiguration.EXCHANGE_TYPE` (`Exchange_Type`) | `LINEAR` | Default stream category used by `xchange-stream-bybit` when subscribing without an explicit category |

`BybitConfiguration` and `BybitEnvironment` in `org.knowm.xchange.bybit.config`
own the environment/account-type contract shared by the REST and streaming
modules.

## Capability Matrix

REST services implement the XChange interfaces listed below. Categories:
`spot`, `linear`, `inverse`, `option`.

| Area | Service | Coverage |
|---|---|---|
| Market data | `BybitMarketDataService` / `Raw` | Tickers, order books, klines, instruments, funding rate, server time, meta, option/linear/inverse delivery price, open interest, public risk-limit tiers |
| Account | `BybitAccountService` / `Raw` | Balances, wallet, deposit/withdraw info, fee rate, API key info, option delivery/settlement records |
| Trade | `BybitTradeService` / `Raw` | Place/cancel/amend orders (single and batch), pre-check, open/closed orders, order history, executions, positions (enumerated per settle coin), risk/margin mutations |
| Streaming | `xchange-stream-bybit` | Public order book/trade/ticker/klines streams; private order/position/execution streams; WebSocket order-entry |

Options/RFQ: V5 options are single-leg; raw option workflows (catalog,
tickers, trades, order entry, delivery price, delivery records) are lossless
with string-preserved numerics. The OTC RFQ trading protocol
(`/v5/otc/rfq/*`) is deprecated and absent from the current V5 docs, so it is
explicitly unsupported; the V5 `otc` namespace (margin-loan helpers) is not
exposed.

Pagination: `BybitMarketDataServiceRaw.getInstrumentsInfo(...)` follows the
V5 `nextPageCursor` contract. Position/order history endpoints accept `limit`
and cursor parameters.

## Compatibility

Legacy parameters are preserved for a compatibility period: `USE_SANDBOX`,
`test_net`, `accountType`, `Exchange_Type`, and the `BASE_URL` constant. No new
builder is required; an `ExchangeSpecification` with no environment flags
targets production exactly as before.
