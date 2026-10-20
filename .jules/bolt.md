## 2026-10-20 - Fast path for Currency.getInstance lookup
**Learning:** `Currency.getInstance(String)` and `getInstanceNoCreate(String)` were invoking `toUpperCase()` unconditionally on every single lookup, allocating a new uppercase `String` on the hot deserialization path even when the symbol was already standard uppercase (e.g., "BTC", "USD").
**Action:** Use a fast-path direct `Map.get(code)` lookup first before falling back to `toUpperCase()` when looking up static cached entities.
