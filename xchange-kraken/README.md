# XChange Kraken (Spot REST)

Canonical XChange adapter for the Kraken Spot REST API (API v0). See the
[Kraken family guide](../docs/kraken-family.md) for the full architecture and capability matrix.

## Capabilities

- Live instrument, currency, trading-rule, and fee-tier metadata via `remoteInit()`
- Ticker, OHLC, order book, trades, spreads, server time, assets, asset pairs
- Balances, extended balances, trade balance, open positions, ledgers, earn allocations
- Deposits (methods, addresses, status) and withdrawals (info, submit, status)
- Orders: market, limit, stop-loss, take-profit, trailing, settle-position; validate-only,
  cancel, cancel-all; client order id (`cl_ord_id`), time-in-force
- Bounded offset pagination for ledgers, trades, and funding history
- Structured redacted errors (`KrakenException`) with retry classification

## Authentication

Set `apiKey` and `secretKey` on the `ExchangeSpecification`. Requests are signed with a
per-call nonce (`KrakenDigest`); private metadata and fee schedules require credentials.

## Notes

- `remoteInit()` requires network access to `https://api.kraken.com`.
- Full ledger iteration is bounded and fails on no-progress pages instead of looping.
- Diagnostic text is redacted; never log raw provider error arrays with credentials.
