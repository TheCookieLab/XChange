## 2026-10-16 - Hex Encoding Performance in Java 17+
**Learning:** Manual char array loops with static char lookup tables for byte-to-hex conversion perform slower on modern JVMs (Java 17+) than `java.util.HexFormat.of().formatHex(byte[])`. `HexFormat` leverages JIT intrinsics and compact string representations, yielding ~60% speedups.
**Action:** Always prefer JDK standard `java.util.HexFormat` for hex formatting and parsing in Java 17+ codebases instead of custom lookup arrays or manual `toCharArray()` loops.
