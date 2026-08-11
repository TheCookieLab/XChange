package org.knowm.xchange.kucoin.uta;

import java.util.regex.Pattern;
import org.knowm.xchange.kucoin.uta.dto.UtaTradeType;

/**
 * KuCoin client-order-id validation.
 *
 * <p>Per the official schema, {@code clientOid} is mandatory for futures and margin orders, is
 * optional for spot, and may contain at most 40 characters from the set letters, digits, {@code _}
 * and {@code -}. Validation runs before transmission so an invalid id never reaches the provider.
 */
public final class UtaClientOrderId {

  private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9_-]{1,40}");

  private UtaClientOrderId() {}

  /**
   * @param clientOid the client-supplied order id
   * @param tradeType provider trade type
   * @throws IllegalArgumentException when the id violates the documented constraints
   */
  public static void validate(String clientOid, String tradeType) {
    boolean mandatory =
        UtaTradeType.FUTURES.name().equalsIgnoreCase(tradeType)
            || UtaTradeType.MARGIN.name().equalsIgnoreCase(tradeType);
    if (clientOid == null || clientOid.isEmpty()) {
      if (mandatory) {
        throw new IllegalArgumentException(
            "clientOid is mandatory for " + tradeType + " orders");
      }
      return;
    }
    if (!ALLOWED.matcher(clientOid).matches()) {
      throw new IllegalArgumentException(
          "clientOid '"
              + clientOid
              + "' is invalid: max 40 characters, only letters, digits, '_' and '-' are allowed");
    }
  }
}
