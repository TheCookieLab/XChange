## 2026-10-25 - Jackson Deserializers and Regex Overhead in Hot Parsing Paths
**Learning:** `String.matches()` and `String.split()` compile new `Pattern` instances on every invocation. In `CurrencyPairDeserializer` and other Jackson deserializers, this happens thousands of times per second on streaming market data, creating unnecessary GC pressure and CPU overhead.
**Action:** Replace `String.matches()` checks for case with zero-allocation character scans, and replace inline `String.split(regex)` with precompiled `static final Pattern` constants.

## 2026-10-28 - Zero-Allocation Parsing in Hot Deserializer Paths
**Learning:** Even with precompiled `Pattern` instances, `Pattern.split()` and `String.split()` allocate internal `Matcher` instances, `ArrayList` buffers, and `String[]` arrays on every call. In `CurrencyPairDeserializer`, `FuturesContract`, and `OptionsContract`, replacing regex splits with single-pass character loops or `indexOf('/')` eliminates allocation churn on high-frequency streaming market data.
**Action:** Use single-pass character loops to track case and separator positions simultaneously, or use `indexOf()` with `substring()` for fixed-delimiter instrument wire strings.

## 2026-10-29 - Regex Compilation Inside Collection Traversal Loops
**Learning:** Placing `Pattern.compile()` or regex operations inside collection filtering loops re-compiles regex patterns and allocates `Matcher` instances on every element iteration. Replacing loop-internal regex digit checks with zero-allocation character scans completely avoids object creation during payload cleanup.
**Action:** Scan character arrays/strings directly for digit checks or static patterns inside collection traversal loops rather than compiling regex patterns per element.