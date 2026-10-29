## 2026-10-27 - Atomic POSIX File Permission Creation for Sensitive Keystores
**Vulnerability:** Creating sensitive files via `Files.write(..., CREATE_NEW)` followed by `Files.setPosixFilePermissions` leaves a race condition window where the file exists under default umask permissions (world-readable) before permissions are restricted.
**Learning:** `Files.write` in Java standard library does not accept `FileAttribute` parameters, so calling `Files.setPosixFilePermissions` after creation creates a transient window of vulnerability for keystores or private key files.
**Prevention:** Use `Files.createFile(path, PosixFilePermissions.asFileAttribute(Set.of(OWNER_READ, OWNER_WRITE)))` to ensure atomic creation with `0600` permissions before writing sensitive contents.
