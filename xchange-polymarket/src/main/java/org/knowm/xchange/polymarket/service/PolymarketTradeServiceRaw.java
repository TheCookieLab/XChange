package org.knowm.xchange.polymarket.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.List;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.exceptions.ExchangeSecurityException;
import org.knowm.xchange.polymarket.PolymarketAdapters;
import org.knowm.xchange.polymarket.PolymarketExchange;
import org.knowm.xchange.polymarket.dto.account.PolymarketApiCredentials;
import org.knowm.xchange.polymarket.dto.trade.PolymarketCancelRequest;
import org.knowm.xchange.polymarket.dto.trade.PolymarketCancelResponse;
import org.knowm.xchange.polymarket.dto.trade.PolymarketOpenOrder;
import org.knowm.xchange.polymarket.dto.trade.PolymarketOrderFlags;
import org.knowm.xchange.polymarket.dto.trade.PolymarketOrderRequest;
import org.knowm.xchange.polymarket.dto.trade.PolymarketPostOrderResponse;
import org.knowm.xchange.polymarket.dto.trade.PolymarketSignedOrder;
import org.knowm.xchange.polymarket.dto.trade.PolymarketUserTrade;

/**
 * Raw Polymarket trading access returning provider DTOs, including the full create-order response
 * with its provider order id, lifecycle status, and fill identifiers.
 */
public class PolymarketTradeServiceRaw extends PolymarketBaseService {

  /** Nonce used for L1 API-key derivation; Polymarket accepts {@code 0} for fresh wallets. */
  static final String DERIVE_NONCE = "0";

  private final SecureRandom saltRandom = new SecureRandom();

  protected PolymarketTradeServiceRaw(PolymarketExchange exchange) {
    super(exchange);
  }

  /** Derives (or returns) the L2 API credentials for the signing wallet; L1-signed. */
  public PolymarketApiCredentials deriveApiCredentials() throws IOException {
    if (l1Digest == null) {
      throw new ExchangeSecurityException(
          "Polymarket API-key derivation requires the EOA private key exchange parameter");
    }
    return clobAuthenticated.deriveApiKey(
        l1Digest.getAddress(), timestampSecondsFactory(), DERIVE_NONCE, l1Digest);
  }

  /**
   * Signs and posts a generic limit order, returning the native response. The salt is a fresh
   * random integer, so every placement attempt is a distinct provider order; the caller must not
   * blindly retry ambiguous failures (see the PRD idempotency rule).
   */
  public PolymarketPostOrderResponse placePolymarketOrder(LimitOrder order) throws IOException {
    if (orderSigner == null) {
      throw new ExchangeSecurityException(
          "Polymarket order placement requires the EOA private key exchange parameter");
    }
    BigDecimal salt = new BigDecimal(new BigInteger(255, saltRandom));
    PolymarketSignedOrder unsigned =
        PolymarketAdapters.toSignedOrder(
            order, orderSigner.getAddress(), salt, System.currentTimeMillis());
    PolymarketSignedOrder signed = unsigned.withSignature(orderSigner.signOrder(unsigned));
    PolymarketOrderRequest request =
        new PolymarketOrderRequest(
            signed,
            apiKey,
            PolymarketAdapters.toOrderType(order),
            order.hasFlag(PolymarketOrderFlags.POST_ONLY) ? Boolean.TRUE : null,
            null);
    return clobAuthenticated.postOrder(
        walletAddress, timestampSecondsFactory(), apiKey, passphrase, l2Digest, request);
  }

  /** Cancels one order by provider order id. */
  public PolymarketCancelResponse cancelPolymarketOrder(String orderId) throws IOException {
    return clobAuthenticated.cancelOrder(
        walletAddress,
        timestampSecondsFactory(),
        apiKey,
        passphrase,
        l2Digest,
        new PolymarketCancelRequest(orderId));
  }

  /** Lists orders, optionally filtered by condition id and/or outcome-token id. */
  public List<PolymarketOpenOrder> getPolymarketOrders(String conditionId, String tokenId)
      throws IOException {
    return clobAuthenticated.getOrders(
        walletAddress, timestampSecondsFactory(), apiKey, passphrase, l2Digest, conditionId,
        tokenId);
  }

  /** Single order by provider order id. */
  public PolymarketOpenOrder getPolymarketOrder(String orderId) throws IOException {
    return clobAuthenticated.getOrder(
        walletAddress, timestampSecondsFactory(), apiKey, passphrase, l2Digest, orderId);
  }

  /** Lists user fills, optionally filtered by condition id. */
  public List<PolymarketUserTrade> getPolymarketUserTrades(String conditionId) throws IOException {
    return clobAuthenticated.getUserTrades(
        walletAddress, timestampSecondsFactory(), apiKey, passphrase, l2Digest, conditionId);
  }
}
