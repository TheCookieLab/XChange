package org.knowm.xchange.bitget.uta.v3.common;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Immutable, endpoint-aware rate policy for the Bitget UTA v3 API.
 *
 * <p>Bitget rate limits are dimensioned per endpoint class (requests per second per IP or per UID,
 * sometimes restricted to a permission set) and REST and WebSocket share one overall quota per IP.
 * This policy centralizes the documented limits so services never hard-code provider-specific
 * numbers. It does not expose provider header parsing to core; {@link #limitFor} is used by
 * throttling/backoff logic inside the adapter only.
 *
 * <p>Entries are best-effort documentation values captured from the official v3 docs (see the
 * consolidated reference in the PRD sources). Unknown endpoints default to the most conservative
 * overall bound.
 */
public final class BitgetUtaV3EndpointPolicy {

  /** Overall bound shared by REST and WebSocket per IP (Bitget docs: 6000 requests/IP/min). */
  public static final int OVERALL_PER_MINUTE_PER_IP = 6000;

  private final ConcurrentMap<String, EndpointRateLimit> byPath;

  private BitgetUtaV3EndpointPolicy(ConcurrentMap<String, EndpointRateLimit> byPath) {
    this.byPath = byPath;
  }

  /** A single endpoint's documented rate limit. */
  public static final class EndpointRateLimit {
    private final int perSecond;
    private final boolean perUid;
    private final boolean readOnly;

    private EndpointRateLimit(int perSecond, boolean perUid, boolean readOnly) {
      this.perSecond = perSecond;
      this.perUid = perUid;
      this.readOnly = readOnly;
    }

    public int getPerSecond() {
      return perSecond;
    }

    /** Whether the dimension is per account UID (true) or per IP (false). */
    public boolean isPerUid() {
      return perUid;
    }

    /** Whether the endpoint needs only a read permission (true) or read&amp;write (false). */
    public boolean isReadOnly() {
      return readOnly;
    }
  }

  private static EndpointRateLimit limit(int perSecond, boolean perUid, boolean readOnly) {
    return new EndpointRateLimit(perSecond, perUid, readOnly);
  }

  /**
   * Returns the documented limit for an endpoint path (for example {@code
   * /api/v3/market/orderbook}), or the conservative overall default when unknown.
   */
  public EndpointRateLimit limitFor(String path) {
    return Optional.ofNullable(byPath.get(path))
        .orElse(new EndpointRateLimit(OVERALL_PER_MINUTE_PER_IP / 60, false, true));
  }

  /** The default conservative policy covering the documented v3 endpoints. */
  public static BitgetUtaV3EndpointPolicy defaults() {
    ConcurrentMap<String, EndpointRateLimit> limits = new ConcurrentHashMap<>();
    // Public market data: 10/s per IP per endpoint class.
    limits.put("/api/v3/market/time", limit(10, false, true));
    limits.put("/api/v3/market/instruments", limit(10, false, true));
    limits.put("/api/v3/market/orderbook", limit(10, false, true));
    limits.put("/api/v3/market/tickers", limit(10, false, true));
    limits.put("/api/v3/market/candles", limit(10, false, true));
    // Account.
    limits.put("/api/v3/account/assets", limit(20, true, true));
    limits.put("/api/v3/account/info", limit(5, true, true));
    limits.put("/api/v3/account/transferable-coins", limit(10, true, true));
    limits.put("/api/v3/account/transfer", limit(5, true, false));
    limits.put("/api/v3/account/set-margin", limit(5, true, false));
    limits.put("/api/v3/account/fee-rate", limit(20, true, true));
    // Trade.
    limits.put("/api/v3/trade/place-order", limit(10, true, false));
    limits.put("/api/v3/trade/cancel-order", limit(10, true, false));
    limits.put("/api/v3/trade/modify-order", limit(10, true, false));
    limits.put("/api/v3/trade/place-strategy-order", limit(10, true, false));
    limits.put("/api/v3/trade/unfilled-orders", limit(20, true, true));
    limits.put("/api/v3/trade/history-orders", limit(20, true, true));
    limits.put("/api/v3/trade/order-info", limit(20, true, true));
    limits.put("/api/v3/trade/fills", limit(20, true, true));
    // Position.
    limits.put("/api/v3/position/current-position", limit(20, true, true));
    return new BitgetUtaV3EndpointPolicy(limits);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof BitgetUtaV3EndpointPolicy)) {
      return false;
    }
    return byPath.equals(((BitgetUtaV3EndpointPolicy) o).byPath);
  }

  @Override
  public int hashCode() {
    return Objects.hash(byPath);
  }
}
