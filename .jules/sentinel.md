## 2026-11-09 - Global TrustManager and HostnameVerifier Overrides
**Vulnerability:** Calling helper methods like `CertHelper.trustAllCerts()` globally disables TLS certificate and hostname verification on `HttpsURLConnection` across the entire JVM process.
**Learning:** Example and manual test scripts in client adapter modules may inadvertently include blanket trust-all-certs calls, which disables TLS security across any HTTP/WebSocket connections established in the same JVM session and exposes API keys to MITM attacks.
**Prevention:** Never call global SSLContext or HostnameVerifier overrides in example or production code. Always rely on the JVM's standard certificate validation and PKI infrastructure.
