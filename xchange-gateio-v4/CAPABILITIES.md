# Gate.io (Gate API v4) — capability matrix

Pinned protocol contract: `src/main/resources/protocol/gate-api-v4-2026-08-13.json`
(227 endpoints, extracted 2026-08-13 from the official docs pages
`https://www.gate.com/docs/developers/apiv4/en/<domain>/`; `GET /spot/accounts`
is pinned from the official `gateapi-go` SDK with provenance because the docs
consolidate it under `/unified/accounts`).

Implemented-surface manifest: `src/main/resources/protocol/implemented-endpoints.json`.
Drift gate: `scripts/check-gate-api-drift.py` (offline in CI; online with fresh
extractions) plus `GateApiProtocolFixtureTest` in the unit-test suite.

Support levels per operation: **generic** — lossless mapping into XChange core
DTOs; **raw** — module-local DTO/service because Gate-specific semantics (identity,
risk, collateral, triggers, partial success) cannot be represented safely in core;
**unsupported** — deliberately not exposed.

## Product/domain ownership

New wire interfaces, DTOs, raw services, adapters, and tests are partitioned by
provider domain (Spot, Margin, Unified, Futures, Delivery, Options, Wallet) plus a
narrowly shared common layer (auth/envelopes/endpoint policy/numeric codecs). The
authenticated interface is not a dumping ground for unrelated product surfaces.

## Spot

| Operation | Endpoint | Level | Notes |
|---|---|---|---|
| Server time | `GET /spot/time` | generic | |
| Currencies and currency pairs (list, one) | `GET /spot/currencies`, `GET /spot/currencies/{currency}`, `GET /spot/currency_pairs`, `GET /spot/currency_pairs/{currency_pair}` | generic | |
| Tickers | `GET /spot/tickers` | generic | |
| Order book | `GET /spot/order_book` | generic | |
| Account balances | `GET /spot/accounts` | raw | extra endpoint (docs-consolidated into `/unified/accounts`) |
| Account book | `GET /spot/account_book` | raw | bounded pagination (page/limit) |
| Orders (list/get/cancel/create/amend/cancel-all) | `GET /spot/orders`, `GET /spot/orders/{order_id}`, `DELETE /spot/orders/{order_id}`, `POST /spot/orders`, `PATCH /spot/orders/{order_id}`, `DELETE /spot/orders` | raw | client `text` identity, partial-fill fields |
| Batch and open-order management | `GET /spot/open_orders`, `POST /spot/batch_orders`, `POST /spot/cancel_batch_orders`, `POST /spot/countdown_cancel_all` | raw | Gate-specific partial-success and countdown semantics |
| Market trades | `GET /spot/trades` | generic | lossless mapping into XChange core DTOs |
| Candlesticks | `GET /spot/candlesticks` | raw | raw-only; no generic `CandleStickData` mapping |

## Margin + Unified Account

| Operation | Endpoint | Level | Notes |
|---|---|---|---|
| Unified accounts, borrow/repay/transfer/risk, mode, leverage | `/unified/*` | unsupported | planned (Phase 3); provider collateral/haircut/risk semantics must survive raw |
| Cross/isolated margin | margin domain endpoints | unsupported | planned (Phase 3) |

## Futures + Delivery

| Operation | Endpoint | Level | Notes |
|---|---|---|---|
| Contracts, tickers, order books, candles, funding, positions, orders/fills, account book, settlement | `/futures/{settle}/*`, `/delivery/{settle}/*` | unsupported | planned (Phase 4); linear/inverse/settlement identity preserved raw |

## Options

| Operation | Endpoint | Level | Notes |
|---|---|---|---|
| Underlying, contracts, tickers, order books, candles, positions, orders/fills, account book | `/options/*` | unsupported | planned (Phase 5); strike/type/expiry/multi-leg identity preserved raw |

## Wallet

| Operation | Endpoint | Level | Notes |
|---|---|---|---|
| Currency chains | `GET /wallet/currency_chains` | generic | |
| Deposit address, withdraw status | `GET /wallet/deposit_address`, `GET /wallet/withdraw_status` | raw | |
| Saved addresses | `GET /wallet/saved_address` | raw | |
| Sub-account transfers | `GET /wallet/sub_account_transfers` | raw | bounded pagination (offset/limit) |
| Withdrawals/deposits | `GET /wallet/withdrawals`, `GET /wallet/deposits` | raw | bounded pagination (offset/limit) |
| Withdraw | `POST /withdrawals` | raw | explicit opt-in only; never in default CI |
| Withdrawal cancel/push, transfer, sub-account balances | wallet domain endpoints | unsupported | planned (Phase 6) |

## Streaming (`xchange-stream-gateio`)

| Surface | Level | Notes |
|---|---|---|
| Public/private spot/derivatives channels | unsupported (basic transport only) | planned (Phases 7–8): product-aware subscriptions, heartbeat, generation-correlated reconnect/resubscribe, dedup, order-book gap rebuild |

## Common infrastructure

| Policy | Status |
|---|---|
| Signing (HMAC-SHA512 over `METHOD\n/path\nquery\nsha512hex(body)\ntimestamp`) | existing `GateioV4Digest`; centralization + expiry/clock-skew handling planned (Phase 6) |
| Bounded pagination | `GateioPageCursor`/`GateioPage`/`GateioContinuation`/`GateioPagination` (Phase 1) — ceilings, repeated-cursor and no-progress detection |
| Errors/rate limits/replay | structured/redacted errors, no blind retry of ambiguous placement — planned (Phase 6) |
