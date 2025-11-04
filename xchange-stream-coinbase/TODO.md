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
- Basic unit tests in place:
  - `CoinbaseStreamingAdaptersTest`
  - `CoinbaseStreamingMarketDataServiceTest`
  - `CoinbaseStreamingTradeServiceTest`
  - `CoinbaseStreamingServiceTest`
- Build/test command `mvn -pl xchange-stream-coinbase -am test` currently passes.
- `README.md` and `AGENTS.md` provide overview and lessons learned.

## Remaining Work

1. **Streaming trade service parity**
   - Confirm `CoinbaseStreamingTradeService` exposes all required public methods from
     `StreamingTradeService` (e.g., `getUserTrades()` overload without instrument argument). Add tests
     verifying the mapped `UserTrade` instances match real payloads.

2. **Market data enhancements**
   - Implement candle stream wiring in `CoinbaseStreamingExchange` (currently `getCandles` helper is
     unused). Decide whether to expose a dedicated method or keep internal for later.
   - Ensure order-book gap handling is aligned with Coinbase’s recommended snapshot + delta flow.
     Write tests for gap detection (e.g., missing sequence numbers should trigger resubscribe/recover).

3. **Private channel JWT refresh**
   - Add logic to automatically resubscribe private channels just before JWT expiry. The
     `refreshJwt` helper replays the subscribe command but does not yet schedule or detect expiry.
     Implement the scheduler (60–90s cadence) only when private channels are active and cancel when
     none remain. Add unit test to ensure refresh attempts occur.

4. **Sandbox verification**
   - Implement an integration-style test (can be disabled/skipped by default) that attempts to connect
     to `wss://advanced-trade-ws.sandbox.coinbase.com`. Record the outcome in `README.md`.
   - If the sandbox is unavailable, adjust default spec parameters accordingly (e.g., disable sandbox
     toggle or emit warning).

5. **End-to-end examples**
   - Add a `examples` class (e.g., under `xchange-examples`) demonstrating connection to Coinbase
     streaming with ticker/order book subscription.
   - Include instructions in `README.md` on running the example against production and sandbox.

6. **Documentation polish**
   - Expand `README.md` with channel-specific notes (payload fields, throttling, heartbeats).
   - Document caveats (missing raw JSON API, JWT refresh behaviour, rate limit tuning via exchange
     params).

7. **Final QA**
   - Re-run `mvn -pl xchange-stream-coinbase -am test` after completing the above.
   - Consider running a quick manual smoke test connecting to production WebSocket (optional if network
     access is constrained).

## How to Resume

1. Pull the latest main branch and ensure the module compiles:  
   `mvn -pl xchange-stream-coinbase -am test`
2. Pick an item from the list above (e.g., JWT refresh scheduling) and implement with unit tests.
3. Update this TODO file as tasks are completed or new issues arise.
4. Keep `AGENTS.md` current with any additional gotchas encountered.

Feel free to refactor the current implementation if a cleaner design emerges, but keep the tests
passing and update documentation accordingly.
