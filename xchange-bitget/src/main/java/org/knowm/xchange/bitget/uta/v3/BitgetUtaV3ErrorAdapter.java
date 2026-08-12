package org.knowm.xchange.bitget.uta.v3;

import lombok.experimental.UtilityClass;
import org.knowm.xchange.bitget.uta.v3.common.BitgetUtaV3Category;
import org.knowm.xchange.bitget.uta.v3.common.BitgetUtaV3Exception;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.FundsExceededException;
import org.knowm.xchange.exceptions.InstrumentNotValidException;
import org.knowm.xchange.exceptions.OrderAmountUnderMinimumException;
import org.knowm.xchange.exceptions.RateLimitExceededException;

/**
 * Converts a {@link BitgetUtaV3Exception} (provider error body) into a structured {@link
 * ExchangeException} subclass.
 *
 * <p>The provider code is kept verbatim in the message so diagnostics remain actionable; no
 * provider-specific type leaks into core. Codes map to core semantics only where the v3 docs give
 * an unambiguous meaning, everything else becomes a plain {@link ExchangeException} carrying the
 * provider code/message.
 */
@UtilityClass
public class BitgetUtaV3ErrorAdapter {

  /** Invalid ACCESS_KEY / credential mismatch. */
  public final String INVALID_ACCESS_KEY = "40006";

  /** Rate limit (HTTP 429 is also mapped by the transport layer). */
  public final String RATE_LIMIT = "429";

  /** Insufficient balance. */
  public final String INSUFFICIENT_BALANCE = "43012";

  /** Minimum order amount/qty not met. */
  public final String MIN_ORDER_AMOUNT = "45110";

  public final String MIN_ORDER_QTY = "45111";

  /**
   * Adapts a provider error, adding API mode context.
   *
   * @param category the product category involved, or {@code null} when not applicable.
   */
  public ExchangeException adapt(BitgetUtaV3Exception e, BitgetUtaV3Category category) {
    String code = e.getCode();
    switch (code == null ? "" : code) {
      case INVALID_ACCESS_KEY:
        return new ExchangeException(
            "Bitget UTA v3 rejected the credentials (code 40006): API mode, key, passphrase or "
                + "signature mismatch. Classic-v2 and UTA-v3 credentials are not interchangeable.",
            e);
      case RATE_LIMIT:
        return new RateLimitExceededException(
            "Bitget UTA v3 rate limit exceeded (code 429): "
                + (category == null ? "" : "category=" + category.getWireName() + "; ")
                + e.getMessage(),
            e);
      case INSUFFICIENT_BALANCE:
        return new FundsExceededException(e.getMessage(), e);
      case MIN_ORDER_AMOUNT:
      case MIN_ORDER_QTY:
        return new OrderAmountUnderMinimumException(e.getMessage(), e);
      default:
        if (category != null && (code == null || code.isEmpty())) {
          return new ExchangeException(e.getMessage(), e);
        }
        return new ExchangeException("Bitget UTA v3 error code " + code + ": " + e.getMessage(), e);
    }
  }

  /** Adapts a provider error without category context. */
  public ExchangeException adapt(BitgetUtaV3Exception e) {
    return adapt(e, null);
  }

  /** Whether the code describes a placement whose final outcome is unknown after transmission. */
  public boolean isAmbiguousPlacementOutcome(String code) {
    // Docs: 40010 order partially placed/matched; 40725 order may be placed; 45001 order may be
    // matched. These must never be blindly replayed; reconcile by client/exchange order id.
    return "40010".equals(code) || "40725".equals(code) || "45001".equals(code);
  }

  /** Helper kept for callers that already know the instrument is invalid. */
  public InstrumentNotValidException invalidInstrument(String message, Throwable cause) {
    return new InstrumentNotValidException(message, cause);
  }
}
