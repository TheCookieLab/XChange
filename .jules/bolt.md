## 2026-10-17 - Reuse Mac Instances in BaseParamsDigest via ThreadLocal

**Learning:** `javax.crypto.Mac.clone()` allocates new SPI instances on every invocation during API signature computation. Caching a `Mac` instance per thread using `ThreadLocal<Mac>` and invoking `mac.reset()` before reuse eliminates allocation and cloning overhead on hot REST request signing paths across exchange adapters.

**Action:** When working with cryptographic digests or signatures called on every API request, prefer thread-local cached instances reset before reuse over cloning per request.
