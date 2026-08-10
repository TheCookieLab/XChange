package org.knowm.xchange.binance.ratelimit;

import java.util.List;
import java.util.Map;
import org.knowm.xchange.binance.dto.BinanceException;

/**
 * Rate-limit telemetry parsed from Binance REST response headers.
 *
 * <p>Binance reports per-request weight consumption and order-count usage through the {@code
 * x-mbx-*} response headers, and {@code Retry-After} on ban/limit responses. Telemetry is
 * available to raw callers and resilience policies; it contains no secrets.
 *
 * @param usedWeight1m request-weight units consumed in the rolling 1-minute window
 * @param orderCount10s order-count units consumed in the rolling 10-second window
 * @param retryAfterMillis seconds to wait before retrying, from {@code Retry-After}
 * @param banned whether the response indicated an IP/API-key ban (HTTP 418)
 */
public record BinanceRateLimitTelemetry(
    Integer usedWeight1m, Integer orderCount10s, Long retryAfterMillis, boolean banned) {

  private static final String HEADER_USED_WEIGHT_1M = "x-mbx-used-weight-1m";
  private static final String HEADER_ORDER_COUNT_10S = "x-mbx-order-count-10s";
  private static final String HEADER_RETRY_AFTER = "retry-after";

  /** Parses telemetry from raw response headers (header names are case-insensitive). */
  public static BinanceRateLimitTelemetry fromHeaders(Map<String, List<String>> headers) {
    if (headers == null) {
      return new BinanceRateLimitTelemetry(null, null, null, false);
    }
    Integer usedWeight = firstInt(headers, HEADER_USED_WEIGHT_1M);
    Integer orderCount = firstInt(headers, HEADER_ORDER_COUNT_10S);
    Long retryAfter = firstLong(headers, HEADER_RETRY_AFTER);
    return new BinanceRateLimitTelemetry(usedWeight, orderCount, retryAfter, false);
  }

  /** Parses telemetry from a failed response's captured headers, if any. */
  public static BinanceRateLimitTelemetry from(BinanceException exception) {
    if (exception == null || exception.getResponseHeaders() == null) {
      return new BinanceRateLimitTelemetry(null, null, null, false);
    }
    return fromHeaders(exception.getResponseHeaders());
  }

  private static Integer firstInt(Map<String, List<String>> headers, String name) {
    Long value = firstLong(headers, name);
    return value == null ? null : value.intValue();
  }

  private static Long firstLong(Map<String, List<String>> headers, String name) {
    for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
      if (entry.getKey().equalsIgnoreCase(name) && entry.getValue() != null) {
        for (String value : entry.getValue()) {
          try {
            return Long.parseLong(value.trim());
          } catch (NumberFormatException ignored) {
            // Non-numeric header value; treat as absent.
          }
        }
      }
    }
    return null;
  }
}
