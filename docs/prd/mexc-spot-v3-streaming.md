# PRD: CF-448 — XChange: replace MEXC v2 with Spot v3 and add streaming support

**Project:** XChange
**Source issue:** https://linear.app/cookiefactory/issue/CF-448/xchange-replace-mexc-v2-with-spot-v3-and-add-streaming-support
**Source document:** https://linear.app/cookiefactory/document/prd-cf-448-xchange-replace-mexc-v2-with-spot-v3-and-add-streaming-3903c328bfc6 (id `8d7bc25b-c3e4-4532-8cb7-b3d0dfc305fd`)
**Author:** ChatGPT (automated)
**Status:** Ready
**Last updated:** 2026-08-14
**Base:** TheCookieLab/XChange@b56a13fe7e8349e1f2c696224026efe0eabba929 (origin/main)

## Status

* **Lifecycle:** In implementation (prd-deliver)
* **Blocking state:** None
* **Active phase:** Phase 1 — isolate the v2 compatibility surface and establish Spot v3 foundations

## Summary

Replace the current MEXC legacy adapter with a deliberate Spot v3 implementation and add first-class protobuf streaming. Freeze the old `/open/api/v2` behavior behind a compatibility boundary, build new code under an explicit `org.knowm.xchange.mexc.v3` package, and add a separate `xchange-stream-mexc` module whose lifecycle and sequence semantics are testable independently of live MEXC availability.

## Key decisions

* Keep `xchange-mexc` artifact for compatibility; v2 classes isolated and deprecated; new code under `org.knowm.xchange.mexc.v3`.
* v3 REST uses official Spot host `api.mexc.com` and `/api/v3` surface.
* New `xchange-stream-mexc` module: official protobuf WebSocket protocol, pinned schema, generation-aware lifecycle, listen-key private streams, snapshot+delta depth recovery.
* Contract/futures work excluded from this delivery.
* Placement after ambiguous transport result: bounded reconciliation by client/exchange order ID; never blind replay; unknown-outcome exception with sanitized correlation data.

## Non-goals

* Extending `/open/api/v2` with new functionality.
* Mixing unofficial/unstable MEXC futures/contract APIs into this Spot delivery.
* Requiring live MEXC for default test execution.
* Changing XChange core solely to expose provider-specific fields.

## Implementation Checklist

1. [ ] **Phase 1 — v2 isolation and v3 foundation.** Freeze v2 behavior (deprecate, keep endpoint semantics), define `mexc.v3` package boundary, v3 exchange config/host routing. Verification: representative v2 compile/runtime tests plus v3 host-routing tests.
2. [ ] **Phase 2 — auth, endpoint policy, metadata, market data.** v3 common/auth/market raw services and metadata adapters. Signing/time/recvWindow, structured errors/redaction, exchange info, filters, public endpoints. Verification: WireMock signature/error/filter/exact-decimal fixtures and public remote-init test.
3. [ ] **Phase 3 — account, wallet, trade, history.** v3 account/trade/wallet raw and high-level services. Balances, fees, orders/cancels, fills/history, stable wallet ops, typed bounded pagination. Verification: deterministic adapter fixtures and repeated/no-progress pagination tests.
4. [ ] **Phase 4 — placement safety.** Client-order validation, trade service, unknown-outcome exception/reconciliation. Verification: transmitted-timeout found/absent/inconclusive scenarios with assertion that placement is never replayed.
5. [ ] **Phase 5 — streaming module and protocol.** New `xchange-stream-mexc`, protobuf schema/build, connection/subscription lifecycle. Verification: schema-drift, encode/decode, ping/pong, lifetime rotation, subscription-limit, reconnect/resubscribe, stale-generation tests.
6. [ ] **Phase 6 — depth and private streams.** Snapshot/delta assembler, listen-key lifecycle, private event adapters. Verification: stale/duplicate/gap/rebuild fixtures; listen-key expiry/replacement; private deduplication and redaction.
7. [ ] **Phase 7 — migration and release validation.** READMEs/examples/Javadocs, v2 deprecation links, build configuration. Verification: `mvn -B -pl xchange-mexc,xchange-stream-mexc -am test`, PMD/format checks, repository-root build, public read-only smoke; private/trading canary opt-in only.

## Verification log

* (filled per phase)

## Risks

* Provider schema/connection limits change; protocol assumptions isolated behind fixtures and typed policy.
* Protobuf generated classes must not create noisy public API/build drift; generation pinned and checked.
* Malformed exchange-info must fail rather than create guessed filters.
* Post-transmission timeouts may correspond to accepted orders; reconciliation precedes any caller-driven retry.
* Listen-key expiry and connection rotation overlap; lifecycle single-flight and generation-aware.
* Sequence/version rules are channel-specific; implemented exactly per MEXC docs.
