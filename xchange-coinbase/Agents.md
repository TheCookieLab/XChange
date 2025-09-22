## Coinbase Advanced Trade (v3) implementation guide

This module integrates Coinbase Advanced Trade v3 REST via a JAX‑RS proxy and the XChange service APIs. Use this guide to quickly add new endpoints in a consistent, tested way. Coinbase's Advanced Trade API documentation can be found at https://docs.cdp.coinbase.com/api-reference/advanced-trade-api/rest-api

### Architecture at a glance
- **HTTP interface**: `org.knowm.xchange.coinbase.v3.CoinbaseAuthenticated`
  - Base path: `/api/v3/brokerage`
  - Auth header: `Authorization: Bearer <jwt>` provided by `ParamsDigest` (`CoinbaseV3Digest`)
  - Methods use JAX‑RS annotations (`@GET`, `@POST`, `@Path`, `@QueryParam`, `@Consumes`)
- **Base service**: `org.knowm.xchange.coinbase.v3.service.CoinbaseBaseService`
  - Builds the JAX‑RS proxy via `ExchangeRestProxyBuilder`
  - Holds `coinbaseAdvancedTrade` and `authTokenCreator`
- **Raw services**: `...service/*.Raw`
  - Thin wrappers that call `CoinbaseAuthenticated` and return endpoint‑specific DTOs
- **User services**: `...service/*Service`
  - Map raw DTOs to XChange core DTOs (`Ticker`, `OrderBook`, `UserTrades`, etc.)
- **Adapters**: `org.knowm.xchange.coinbase.CoinbaseAdapters`
  - Utilities: `adaptProductId(Instrument)`, `adaptInstrument(productId)`, `adaptOrderType(side)`

### Pattern to add a new endpoint
1) Define precise DTOs (JSON → Java)
- Create DTOs under `org.knowm.xchange.coinbase.v3.dto.<area>`
- Use Jackson annotations:
  - `@JsonIgnoreProperties(ignoreUnknown = true)` for forward compatibility
  - `@JsonCreator` + `@JsonProperty("json_name")` on constructor params
  - Parse timestamps as ISO‑8601 strings into `java.util.Date` when needed
- Prefer immutable DTOs with `final` fields and Lombok `@Getter`

2) Declare the HTTP method in `CoinbaseAuthenticated`
- Add a method annotated with the correct `@Path` and `@QueryParam`s from docs
- Always include `@HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest`
- Return a response DTO that reflects the response shape (create a new one if needed)

3) Implement the Raw service method
- In the relevant `*ServiceRaw` class, add a method that
  - Translates XChange params to query params (use `CoinbaseAdapters.adaptProductId` for instruments)
  - Calls the `coinbaseAdvancedTrade` proxy with `authTokenCreator`
  - Returns the response DTO (and, if paged, exposes `cursor` as needed)

4) Implement the high‑level service method
- Map raw DTOs to XChange DTOs, using `CoinbaseAdapters` helpers for side/instrument
- Respect XChange expectations (e.g., `UserTrades` should set `Trades.TradeSortType.SortByTimestamp`)
- Handle pagination as appropriate (see below)

5) Add/extend params class if needed
- Prefer a dedicated params class implementing the relevant XChange param interfaces
  (e.g., `TradeHistoryParamMultiCurrencyPair`, `TradeHistoryParamLimit`, `TradeHistoryParamNextPageCursor`, `TradeHistoryParamsTimeSpan`)
- Example: `org.knowm.xchange.coinbase.v3.dto.trade.CoinbaseTradeHistoryParams`

6) Write an integration test
- Pattern:
  - Create exchange via `ExchangeFactory` and `ExchangeSpecification`
  - Inject keys with `AuthUtils.setApiAndSecretKey(...)`
  - `Assume.assumeNotNull(service.authTokenCreator)` to skip if no auth
  - Exercise the new method with minimal, stable assertions
    (avoid strict counts; assert non‑nulls and basic invariants)

### Example: List Fills → UserTrades

Implemented files (reference when adding similar endpoints):
- `dto/orders/CoinbaseFill.java`: models a fill item (price, size, side, product_id, trade_time, commission, etc.)
- `dto/orders/CoinbaseOrdersResponse.java`: wraps `fills` and `cursor`
- `CoinbaseAuthenticated.listFills(...)`: adds query params like `order_ids`, `trade_ids`, `product_ids`, `start_sequence_timestamp`, `end_sequence_timestamp`, `limit`, `cursor`, `sort_by`
- `service/CoinbaseTradeServiceRaw.listFills(...)`: maps `CoinbaseTradeHistoryParams` → query params; calls proxy
- `service/CoinbaseTradeService.getTradeHistory(...)`: pages through fills and adapts to `UserTrades`

Key mapping notes:
- Side → `OrderType`: `CoinbaseAdapters.adaptOrderType(side)` ("BUY"→BID, "SELL"→ASK)
- Instrument: `CoinbaseAdapters.adaptInstrument(productId)` where productId looks like `BTC-USD`
- Fee currency: for spot fills, commission is assumed to be in quote currency (right token of product ID)

### Pagination (cursor) guidelines
- Many v3 endpoints return a `cursor` for pagination
- Pass the cursor back on subsequent requests to fetch the next page
- Stop when cursor is null/empty, page has no results, or your requested `limit` is satisfied

### Testing conventions
- Integration tests live under `xchange-coinbase/src/test/java/.../service`
- Use `Assume.assumeNotNull(service.authTokenCreator)` to skip when no creds
- Favor assertions that are robust across accounts/time (e.g., non‑null and basic structure)

### Coding conventions and style
- Use existing indentation and formatting (no reflows of unrelated code)
- Keep imports minimal; prefer utility methods in `CoinbaseAdapters`
- DTOs: avoid business logic; small helpers (e.g., converting side to `OrderType`) are fine
- Prefer `final` fields and immutability for DTOs
- Prefer adding imports over using fully qualified class names to keep the code concise
- Order imports, declarations, etc first on logical grouping and then in reverse christmas tree order

### Gotchas
- Do not reuse response DTOs across endpoints if the JSON shape differs; define dedicated response types
- Times are ISO‑8601 strings; parse into `Date` using `DateTimeFormatter.ISO_INSTANT`
- For market‑data vs user endpoints, map to the correct XChange DTOs (`Trade` vs `UserTrade`)

### Checklist for a new endpoint
- [ ] Read docs, list required/optional query params and response fields
- [ ] Add DTOs with Jackson annotations (response + items)
- [ ] Extend `CoinbaseAuthenticated` with method signature and `@QueryParam`s
- [ ] Implement Raw service method; wire params; return DTO
- [ ] Implement high‑level service mapping to XChange DTOs
- [ ] Handle pagination if applicable
- [ ] Add integration test with minimal stable assertions
- [ ] Run lints; keep imports clean
- [ ] Keep his Agents.md file up to date with any new changes, references, and information that would be useful for future reference / agentic work. 


