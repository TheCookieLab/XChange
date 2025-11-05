# Coinbase Streaming Module TODO

This file captures the remaining work for the new `xchange-stream-coinbase` subproject so that a
fresh agent can resume implementation without needing chat history.

## Current Status (2025-11-04)

- Module scaffolded (`pom.xml`, source/test tree, wired into root `pom.xml`).
- `CoinbaseV3Digest` extended with `generateWebsocketJwt()` and a helper (`CoinbaseWebsocketAuthentication`)
  so streaming layer can mint JWTs.
- Core streaming classes drafted: `CoinbaseStreamingExchange`, `CoinbaseStreamingService`,
  `CoinbaseStreamingMarketDataService`, `CoinbaseStreamingTradeService`, adapters, DTOs.
- Rate limiting, JWT refresh, and subscription handling implemented in the streaming service.
- Private channel JWT refresh now schedules/cancels automatically with regression tests covering
  resubscribe behaviour.
- Level2 order book tracking now handles sequence enforcement with automatic REST snapshot recovery
  and regression coverage for gap detection.
- Basic unit tests in place:
  - `CoinbaseStreamingAdaptersTest`
  - `CoinbaseStreamingMarketDataServiceTest`
  - `CoinbaseStreamingTradeServiceTest`
  - `CoinbaseStreamingServiceTest`
- Build/test command `mvn -pl xchange-stream-coinbase -am test` currently passes.
- `README.md` and `AGENTS.md` provide overview and lessons learned.

## Remaining Work

1. **Documentation polish**
   - Expand `README.md` with channel-specific notes (payload fields, throttling, heartbeats).
     - Document caveats (missing raw JSON API, JWT refresh behaviour, rate limit tuning via exchange
       params).
2. **Final QA**
   - Re-run `mvn -pl xchange-stream-coinbase -am test` after completing the above.
   - Consider running a quick manual smoke test connecting to production WebSocket (optional if network
     access is constrained).

## Completed Tasks

- **Streaming trade service parity** (2025-11-04)  
  `CoinbaseStreamingTradeService` now exposes all `StreamingTradeService` overloads, including the
  no-arg `getUserTrades()`, with regression coverage verifying subscription wiring and payload mapping.

- **Market data enhancements: candle wiring** (2025-11-04)  
  Candle subscriptions are wired via `CoinbaseCandleSubscriptionParams`, exchange helpers, and tests
  asserting `granularity` propagation and candle decoding.

- **Sandbox verification** (2025-11-05)  
  Added an opt-in smoke test (`CoinbaseSandboxConnectivityTest`) that attempts to connect to the
  documented sandbox WebSocket. As of 2025-11-05 the hostname `advanced-trade-ws.sandbox.coinbase.com`
  fails DNS resolution (UnknownHost); the test remains skipped unless explicitly enabled so regular
  CI runs stay green.

- **Streaming examples** (2025-11-05)  
  Added `CoinbaseStreamingMarketDataExample` under `xchange-examples` to demonstrate ticker, trade,
  and order book subscriptions. Usage instructions (including sandbox toggle) are documented in the
  module README.

## How to Resume

1. Pull the latest main branch and ensure the module compiles:  
   `mvn -pl xchange-stream-coinbase -am test`
2. Pick an item from the list above (e.g., market data gap handling or sandbox verification) and implement with unit tests.
3. Update this TODO file as tasks are completed or new issues arise.
4. Keep `AGENTS.md` current with any additional gotchas encountered.

Feel free to refactor the current implementation if a cleaner design emerges, but keep the tests
passing and update documentation accordingly.
