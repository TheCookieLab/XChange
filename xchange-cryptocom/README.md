# XChange Crypto.com

Crypto.com Exchange v1 REST connector (`org.knowm.xchange.cryptocom`): spot, margin and
derivatives through the official method/envelope API. WebSocket streaming lives in the sibling
module `xchange-stream-cryptocom` (`info.bitrich.xchangestream.cryptocom`).

## Request / response envelope

Every v1 call shares one envelope: a POST with a JSON body of the form

```json
{
  "id": 1847836455768,
  "method": "public/get-instruments",
  "params": {},
  "api_key": "…",
  "sig": "…",
  "nonce": 1600000000000000000
}
```

- `id` is the **monotonic request id** (nanosecond-liked generator, `CryptoComRequestIdGenerator`):
  deterministic, never reused, echoed by the server in every response and surfaced on
  `CryptoComRequestException` as `requestId` so responses can be correlated with the originating
  call.
- `method` is the v1 method name (`public/…` for market data, `private/…` for account/trading).
- `params` are typed per method; private methods add `api_key`, `sig` and `nonce`.

## Signing

Private requests are signed with an HMAC-SHA256 over the exact canonical string
`<timestamp><id><method><paramsJson>` using the API secret, hex-encoded as `sig`
(`CryptoComDigest`). Canonicalization uses exact decimal-string values — numerics are never
rounded before signing or parsing. The nonce/timestamp strategy is centralized in
`CryptoComExchange` (server-time aware, with a bounded skew and clock-source policy) so signing
never depends on the caller's wall clock.

## Errors and replay policy

Failures are mapped to `CryptoComRequestException` carrying the provider code/message, method,
transport (HTTP vs WebSocket), request id, retry class (`CryptoComRetryClass`) and sanitized
details. Secrets never appear in exceptions, `toString`, logs or fixtures.

**Order placement is never automatically replayed.** A placement is transmitted once with a
unique request id; if the response is interrupted or malformed the outcome is ambiguous, so the
caller gets a structured `CryptoComUnknownOrderOutcomeException` instead of a blind retry.
Reconciliation happens through exchange order id / client order id: `createCryptoComOrder`
returns a `CryptoComOrderPlacementResult` that pins both identities when known

## Capability matrix

| Area | v1 method | Surface |
|---|---|---|
| Instruments | `public/get-instruments` | `getCryptoComInstruments` (cursor-bounded pagination, `MAX_REFERENCE_PAGES`) → `CryptoComInstrument` |
| Order book | `public/get-book` | `getCryptoComOrderBook` → `CryptoComOrderBookData` (exact decimal strings) |
| Public trades | `public/get-trades` | `getCryptoComTrades` → `CryptoComPublicTrade` |
| Tickers | `public/get-tickers` | `getCryptoComTicker[s]` → `CryptoComTicker` |
| Candles | `public/get-candlestick` | `getCryptoComCandles` (count or ts-window) → `CryptoComCandlestick` |
| Expired settlement | `public/get-expired-settlement-price` | `getCryptoComExpiredSettlementPrices` → `CryptoComExpiredSettlementPrice` |
| Risk parameters | `public/get-risk-parameters` | `getCryptoComRiskParameters` → `CryptoComRiskParameters` |
| Balance | `private/user-balance` | `getCryptoComBalances` → `CryptoComBalance` (spot + position balances) |
| Fee rates | `private/get-fee-rate` | `getCryptoComFeeRate` → `CryptoComFeeRate` |
| Positions | `private/get-positions` | `getCryptoComPositions` → `CryptoComPosition` (derivatives) |
| Accounts/risk | `private/get-accounts` | `getCryptoComAccounts` → `CryptoComAccount` (margin risk model) |
| Balance history | `private/get-user-balance-history` | `getCryptoComUserBalanceHistory` — bounded pagination (`MAX_HISTORY_PAGES`, page-size cap, repeated/empty-page stop) |
| Deposit address | `private/get-deposit-address` | `getCryptoComDepositAddresses` |
| Deposit history | `private/get-deposit-history` | `getCryptoComDepositHistory` |
| Withdrawal history | `private/get-withdrawal-history` | `getCryptoComWithdrawalHistory` |
| Withdrawal creation | `private/create-withdrawal` | `createCryptoComWithdrawal` — raw, high-friction, explicit opt-in; never exercised by default CI |
| Order create | `private/create-order` | `createCryptoComOrder` (spot + margin + derivatives; reduce-only/trigger semantics via advanced variant) |
| Advanced orders | `private/create-order` + trigger | `createCryptoComAdvancedOrder` (stop/take-profit/trigger-price orders) |
| Cancel / cancel-all | `private/cancel-order` / `private/cancel-all-orders` | `cancelCryptoComOrder`, `cancelAllCryptoComOrders` |
| Open orders | `private/get-open-orders` | `getCryptoComOpenOrders` |
| Order detail | `private/get-order-detail` | `getCryptoComOrderDetail` |
| Order history | `private/get-order-history` | `getCryptoComOrderHistory` (bounded pagination) |
| User trades | `private/get-user-trades` | `getCryptoComUserTrades` (bounded pagination) |

Reference data is intentionally exposed as **typed raw results** when there is no lossless XChange
core surface (candles, expired-settlement prices, risk parameters, positions, fee/account rows):
provider decimal strings are preserved exactly.

## Instrument and derivative semantics

`CryptoComInstrumentIdentity` parses native instrument names losslessly
(`BTCUSD-PERP`, `BTCUSD-260625`, `BTCUSD-260625-C`, …) into product type (spot/perpetual/future/
option), base/quote/settlement currency, contract type, expiry and option strike/type — no symbol
heuristics, no lossy regex flattening. `CryptoComInstrument` keeps the full official
`InstrumentItem` field set: contract size/multiplier, linear vs inverse settlement semantics, tick
and quantity increments, min/max/notional constraints, margin eligibility, trading state, and the
provider id. The instrument registry is shared by REST and streaming.

## Environments and UAT validation

`CryptoComExchange` routes to the authoritative production REST host by default; UAT is a
specification parameter (`CryptoComExchange` `USE_SANDBOX` / override) and stays isolated from
production credentials. Production API keys are never sent to an override endpoint unless the
caller explicitly enables that high-risk behavior. Read-only and market-data calls are safe to run
against UAT with test credentials; funded trading and withdrawal are explicit opt-in and are
**never** part of default test runs (all module tests use deterministic offline fixtures).

## Usage

```java
Exchange exchange = ExchangeFactory.INSTANCE.createExchange(CryptoComExchange.class);
ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
spec.setApiKey("…");
spec.setSecretKey("…");
exchange.applySpecification(spec);

Ticker ticker = exchange.getMarketDataService().getTicker(new CurrencyPair("BTC/USDT"));
OrderBook book = exchange.getMarketDataService().getOrderBook(new CurrencyPair("BTC/USDT"));
```

For market data no credentials are required; account/trade calls require a valid API key pair.