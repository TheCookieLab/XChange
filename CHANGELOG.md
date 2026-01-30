# Temporary CHANGELOG (PR Draft)

- Coinbase Advanced Trade v3 REST: Introduced the complete v3 REST stack (JWT auth, DTOs, raw + service layers) covering accounts, products, price books, orders, fills, conversions, payment methods, portfolios, futures/perpetuals, and fee/transaction summaries, with updated adapters and exchange wiring for v3 market data and trading flows.
- Coinbase Advanced Trade streaming: Added a new v3 streaming implementation with WebSocket subscription types (ticker, level2, candles, user orders), robust adapters/DTOs, price normalization, concurrent-safe order handling, and JWT refresh reuse; removed unsupported sandbox streaming tests.
- Coinbase examples and docs: Expanded examples to cover v3 auth checks, portfolio/futures workflows, order preview/convert/trade operations, and streaming market data, plus clarified streaming configuration guidance.
- Auth utilities and dependencies: Improved `AuthUtils` key resolution (environment variable precedence and host sanitization) with updated tests, and aligned module versions/dependencies for the new Coinbase stack.
- Repo hygiene: Removed local AI assistant workflow metadata (AGENTS.md) to match upstream conventions.
