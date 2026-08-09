# XChange Kraken Futures (Futures REST)

Canonical XChange adapter for the Kraken Futures REST API. See the
[Kraken family guide](../docs/kraken-family.md) for the full architecture and capability matrix.

## Capabilities

- Instruments and trading rules; tickers, order books, trades, funding rates
- Accounts/wallets, open positions
- Orders: market, limit, stop/take-profit, reduce-only, client order reference, batch orders,
  atomic amend (`changeOrder`), cancel, cancel-all, cancel-all-by-instrument
- Fills and order statuses
- Cancel-all-after / dead-man switch (`cancelAllOrdersAfter`) — opt-in; cancels all open
  orders when the timer expires
- Structured redacted errors (`KrakenFuturesException`) with retry classification

## Authentication

Set `apiKey` and `secretKey` on the `ExchangeSpecification`. Requests are signed per call
(`KrakenFuturesDigest`).

## Dead-man switch safety

`cancelAllOrdersAfter` disables all open orders when the timeout elapses. It is disabled by
default; enable it deliberately and refresh the timer while the lifecycle is healthy.
