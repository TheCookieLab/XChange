# AGENTS Instructions for XChange

## Scope

These rules apply across the Maven reactor unless a deeper `AGENTS.md` adds
stricter module guidance.

## Test Runtime Discipline

- Keep unit tests isolated from live exchanges. Mock HTTP and WebSocket collaborators for service behavior; use a dynamic-port local server only when request encoding, transport handling, or protocol behavior is the assertion.
- Keep live external calls in Failsafe `*Integration.java` tests run with `-DskipIntegrationTests=false`; do not move them into the default unit-test surface.
- Use minimal canned payloads that cover nominal, boundary, invalid, and exchange-specific interaction cases. Keep one exhaustive owner for a meaningful protocol or data matrix instead of repeating it across raw, service, and DTO tests.
- During iteration, run focused module/class tests without `clean`, for example `mvn -B -pl <module> -am -Dtest=<TestClass> test`. Reserve clean reactor commands for final or release validation.
- Replace sleeps and retry delays with deterministic signals or default-preserving test seams. Do not increase timeouts as the first response to a slow or flaky test.
- Bound concurrency at isolated module or workflow lanes. Do not enable blanket JUnit parallelism across adapters that may share static clients, process state, ports, or mutable fixtures.
- Before optimizing, record the Surefire/Failsafe slowest five and retain comparable before/after evidence. Keep local and hosted Maven goals, integration filters, reports, and quality gates behaviorally equivalent.
