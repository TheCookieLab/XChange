# Kraken Exchange Family Guide

This guide documents the canonical architecture of the Kraken exchange family in XChange:
which artifact owns which protocol, how the layered services are shaped, what capabilities
exist per protocol, and how to migrate from the legacy Spot WebSocket v1 module.

## Canonical artifact ownership

| Protocol | Canonical artifact | Status |
|---|---|---|
| Spot REST (API v0) | `xchange-kraken` | canonical |
| Spot WebSocket v2 | `xchange-stream-kraken-v2` | canonical |
| Futures REST | `xchange-krakenfutures` | canonical |
| Futures WebSocket | `xchange-stream-krakenfutures` | canonical |
| Spot WebSocket v1 (legacy) | `xchange-stream-kraken` | deprecated, migration below |

Spot and Futures keep separate wire protocols and DTOs; they share design rules, not
wire types. `xchange-stream-kraken` (legacy v1) is kept for the compatibility grace
period with a tested migration path and will be removed in a later scoped change.

## Layered service architecture

Every protocol/domain follows the same layered shape:

1. Wire interface/transport (`Kraken`, `KrakenAuthenticated`, `KrakenFutures`,
   `KrakenFuturesAuthenticated`, streaming services).
2. Endpoint-specific immutable DTOs (`dto/…`).
3. Authentication, endpoint policy, and structured errors.
4. Thin raw service (`KrakenMarketDataServiceRaw`, `KrakenAccountServiceRaw`,
   `KrakenTradeServiceRaw`, futures equivalents).
5. High-level XChange adapters (`KrakenMarketDataService`, `KrakenAccountService`,
   `KrakenTradeService`, futures equivalents).
6. Exchange-specific results for lossless behavior.

## Capability matrix

| Capability | Spot REST | Spot WS v2 | Futures REST | Futures WS |
|---|---|---|---|---|
| Instruments / trading rules | yes (live AssetPairs) | — | yes | — |
| Fee schedules (tiers) | yes (live pairs; TradeVolume private) | — | no | — |
| Ticker / trades / OHLC / order book | yes | ticker, trade (book/OHLC planned) | yes | yes |
| Balances / account | yes | balances channel | yes | — |
| Open positions | yes | — | yes | — |
| Ledger / funding history | yes (bounded paging) | — | account logs planned | — |
| Order placement (market/limit/stop) | yes | — | yes | — |
| Atomic amend | no (planned) | — | yes (`changeOrder`) | — |
| Batch orders | no (planned) | — | yes | — |
| Cancel / cancel-all | yes | — | yes / by instrument | — |
| Cancel-all-after / dead-man | no (planned) | — | yes (`cancelAllOrdersAfter`) | — |
| Client order id (`cl_ord_id` / userref) | yes | — | yes | — |
| Order/trade history | yes (offset paging) | — | fills; history endpoints planned | — |
| Book sequence/checksum recovery | — | planned | — | planned (`seq` gap detection) |
| Structured redacted errors | yes | — | yes | — |

## Spot REST (xchange-kraken)

* Metadata: `remoteInit()` loads live AssetPairs/Assets; pair fee tiers and scales are
  authoritative. `tradingFee` defaults to the first taker tier (maker fallback) and stays
  `null` when the provider returns no fee data; incomplete tier data fails explicitly.
* Pagination: raw services accept the provider `ofs` continuation (inclusive, chronological
  ascending). Full-history ledger iteration is bounded by `MAX_LEDGER_PAGES` and fails on
  repeated/no-progress pages instead of looping.
* Errors: `KrakenBaseService.checkResult` maps known codes to typed XChange exceptions
  (nonce, rate limit, funds, unavailable) and falls back to `KrakenException` carrying
  domain, operation, retry class, and sanitized details. All diagnostic text passes through
  `KrakenRedactor` (keys, secrets, signatures, nonces, OTPs, tokens, JWTs, addresses).

## Spot WebSocket v2 (xchange-stream-kraken-v2)

* Public and private sockets; the private socket is created only when private
  subscriptions exist and credentials are complete. `isAlive()` reflects the aggregate
  lifecycle. (Dual-socket lifecycle hardening in progress.)
* Channels: ticker, trade, balances, executions.

## Futures (xchange-krakenfutures, xchange-stream-krakenfutures)

* REST: instruments, tickers, books, trades, funding rates, accounts, open positions,
  order placement/edit/cancel/cancel-all/batch, fills, order statuses, cancel-all-after
  (dead-man switch — opt-in, cancels all open orders when the timer expires).
* WS: book snapshot/delta with provider `seq` values, ticker, trades, funding, private
  fills. `seq` continuity validation is in progress.
* Errors: `KrakenFuturesException` with domain/operation/retry class/redacted details.

## Legacy Spot WS v1 migration (xchange-stream-kraken → v2)

1. Swap the artifact and service classes:
   `KrakenStreamingExchange` (v1) → `KrakenStreamingExchange` (v2, package
   `info.bitrich.xchangestream.kraken`), `KrakenStreamingMarketDataService` (v1) →
   `KrakenStreamingMarketDataService` (v2).
2. v2 authentication uses the WebSocket token from the Spot REST account service; configure
   apiKey/secret for private channels. Without credentials the v2 private socket is not
   connected and the trade/account streaming services are unavailable.
3. Keep v1 through the compatibility grace period; the v1 compatibility suite (`KrakenStreamingChecksumTest`,
   `KrakenStreamingAdaptersTest`) runs in CI. All v1 public entry points carry `@Deprecated`.

### Channel parity v1 → v2

| Capability | v1 (`xchange-stream-kraken`) | v2 (`xchange-stream-kraken-v2`) |
|---|---|---|
| Ticker | `getTicker` | `getTicker` (ticker channel) |
| Trades | `getTrades` | `getTrades` (trade channel) |
| Order book | `getOrderBook` (checksum) | `getOrderBook` (book channel, checksum + gap recovery) |
| OHLC candles | `getOHLC` (`KrakenStreamingOhlc`) | `getOHLC` (ohlc channel) |
| System status | — | `getSystemStatus` (status channel) |
| Order changes | `getOrderChanges` (openOrders channel, private) | `orders` channel registered (token auth) |
| Executions | `getUserTrades` (ownTrades channel, private) | `getUserTrades` (executions channel, token auth) |
| Balances | — | `getBalances` (balances channel, token auth) |
| Reconnect | fixed retry | bounded exponential backoff (1s→30s) + reauth + resubscribe |
| Book consistency | checksum validation | checksum validation + snapshot gap recovery |
| Private event dedup | — | fills/seq-based dedup (Futures WS) |

Channels present in v1 but not consumed by v2 services are still registered on the protocol
level (`book`/`ohlc`/`status`/`orders`); nothing is silently dropped.

## Dead-man / cancel-on-disconnect safety

* Dead-man/cancel-all-after is disabled by default and must be enabled deliberately
  (futures: `cancelAllOrdersAfter`).
* The feature cancels all open orders when the timer expires; documentation warns of this.
* A graceful disconnect must be distinguished from transport loss; the library fails safe.

## Build gates

```text
mvn -B -pl xchange-kraken,xchange-krakenfutures,xchange-stream-kraken,xchange-stream-kraken-v2,xchange-stream-krakenfutures -am test
```

Live credential-gated tests stay in Failsafe `*Integration` classes and are never part of
the default unit-test surface.
