package org.knowm.xchange.mexc;

import java.util.Optional;

/**
 * @deprecated MEXC Spot v2 ({@code /open/api/v2}) is frozen for compatibility; use the Spot v3
 *     implementation in {@code org.knowm.xchange.mexc.v3} instead. See the xchange-mexc README
 *     migration notes for the removal policy.
 */
@Deprecated
public class MEXCErrorUtils {

  public static Optional<String> getOptionalErrorMessage(int errorCode) {
    return Optional.ofNullable(getErrorMessage(errorCode));
  }

  private static String getErrorMessage(int errorCode) {
    switch (errorCode) {
      case (400):
        return "Invalid parameter";
      case (401):
        return "Invalid signature, fail to pass the validation";
      case (429):
        return "Too many requests, rate limit rule is violated";
      case (10072):
        return "Invalid access key";
      case (10073):
        return "Invalid request time";
      case (30000):
        return "Trading is suspended for the requested symbol";
      case (30001):
        return "Current trading type (bid or ask) is not allowed";
      case (30002):
        return "Invalid trading amount, smaller than the symbol minimum trading amount";
      case (30003):
        return "Invalid trading amount, greater than the symbol maximum trading amount";
      case (30004):
        return "Insufficient balance";
      case (30005):
        return "Oversell error";
      case (30010):
        return "Price out of allowed range";
      case (30016):
        return "Market is closed";
      case (30019):
        return "Orders count over limit for batch processing";
      case (30020):
        return "Restricted symbol, API access is not allowed for the time being";
      case (30021):
        return "Invalid symbol";
      default:
        return null;
    }
  }
}
