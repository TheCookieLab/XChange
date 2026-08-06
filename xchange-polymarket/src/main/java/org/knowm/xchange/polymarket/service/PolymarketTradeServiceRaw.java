package org.knowm.xchange.polymarket.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.ExchangeSecurityException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.polymarket.PolymarketAdapters;
import org.knowm.xchange.polymarket.PolymarketExchange;
import org.knowm.xchange.polymarket.client.PolymarketEip712Signer;
import org.knowm.xchange.polymarket.dto.account.PolymarketApiCredentials;
import org.knowm.xchange.polymarket.dto.trade.PolymarketCancelRequest;
import org.knowm.xchange.polymarket.dto.trade.PolymarketCancelResponse;
import org.knowm.xchange.polymarket.dto.trade.PolymarketOpenOrder;
import org.knowm.xchange.polymarket.dto.trade.PolymarketOrderFlags;
import org.knowm.xchange.polymarket.dto.trade.PolymarketOrderRequest;
import org.knowm.xchange.polymarket.dto.trade.PolymarketOrdersResponse;
import org.knowm.xchange.polymarket.dto.trade.PolymarketPostOrderResponse;
import org.knowm.xchange.polymarket.dto.trade.PolymarketSignedOrder;
import org.knowm.xchange.polymarket.dto.trade.PolymarketTradesResponse;
import org.knowm.xchange.polymarket.dto.trade.PolymarketUserTrade;

/**
 * Raw Polymarket trading access returning provider DTOs, including the full create-order response
 * with its provider order id, lifecycle status, and fill identifiers.
 *
 * <p>CLOB V2 pages the authenticated read endpoints with {@code next_cursor}; the raw list
 * methods aggregate every page (loop-safe, deduped by id) so callers see the full order/fill set.
 */
public class PolymarketTradeServiceRaw extends PolymarketBaseService {

  /** Nonce used for L1 API-key derivation; Polymarket accepts {@code 0} for fresh wallets. */
  static final String DERIVE_NONCE = "0";

  /** Sentinel {@code next_cursor} value marking the last page of {@code /data/trades}. */
  static final String END_CURSOR = "LTE=";

  /** Safety bound on cursor-pagination loops. */
  static final int MAX_PAGES = 50;

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
   *
   * <p>The EIP-712 verifying contract is selected from the negative-risk flag recorded for the
   * market at discovery ({@code remoteInit()} or an order-book fetch); an unknown market type
   * fails fast here rather than submitting an order signed for the wrong domain.
   */
  public PolymarketPostOrderResponse placePolymarketOrder(LimitOrder order) throws IOException {
    if (orderSigner == null) {
      throw new ExchangeSecurityException(
          "Polymarket order placement requires the EOA private key exchange parameter");
    }
    String conditionId = PolymarketAdapters.conditionId(order.getInstrument());
    Boolean negRisk = PolymarketAdapters.negRiskForCondition(conditionId);
    if (negRisk == null) {
      throw new NotAvailableFromExchangeException(
          "Cannot determine whether Polymarket market "
              + conditionId
              + " is negative-risk; populate the market catalog with exchange.remoteInit() (or"
              + " fetch the market's order book) before placing orders.");
    }
    BigDecimal salt = new BigDecimal(new BigInteger(255, saltRandom));
    PolymarketSignedOrder unsigned =
        PolymarketAdapters.toSignedOrder(
            order,
            orderSigner.getAddress(),
            salt,
            System.currentTimeMillis(),
            negRisk,
            PolymarketEip712Signer.SIGNATURE_TYPE_EOA);
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

  /**
   * Lists orders, optionally filtered by condition id and/or outcome-token id, following the
   * {@code next_cursor} pagination of {@code /data/orders} to completion (deduped by order id).
   */
  public List<PolymarketOpenOrder> getPolymarketOrders(String conditionId, String tokenId)
      throws IOException {
    Map<String, PolymarketOpenOrder> byId = new ConcurrentHashMap<>();
    String cursor = null;
    for (int page = 0; page < MAX_PAGES; page++) {
      PolymarketOrdersResponse response =
          clobAuthenticated.getOrders(
              walletAddress,
              timestampSecondsFactory(),
              apiKey,
              passphrase,
              l2Digest,
              cursor,
              conditionId,
              tokenId);
      if (response == null || response.data() == null) {
        return new ArrayList<>(byId.values());
      }
      for (PolymarketOpenOrder order : response.data()) {
        if (order.id() != null) {
          byId.putIfAbsent(order.id(), order);
        }
      }
      cursor = response.nextCursor();
      if (cursor == null || cursor.isBlank() || END_CURSOR.equals(cursor)) {
        break;
      }
    }
    return new ArrayList<>(byId.values());
  }

  /** Single order by provider order id. */
  public PolymarketOpenOrder getPolymarketOrder(String orderId) throws IOException {
    return clobAuthenticated.getOrder(
        walletAddress, timestampSecondsFactory(), apiKey, passphrase, l2Digest, orderId);
  }

  /**
   * Lists user fills, optionally filtered by condition id, following the {@code next_cursor}
   * pagination of {@code /data/trades} to completion (deduped by trade id). The endpoint requires
   * the account's maker address, so a configured wallet is mandatory.
   */
  public List<PolymarketUserTrade> getPolymarketUserTrades(String conditionId)
      throws IOException {
    if (walletAddress == null || walletAddress.isBlank()) {
      throw new ExchangeException(
          "Polymarket trade history requires a wallet address (spec userName or private key).");
    }
    Map<String, PolymarketUserTrade> byId = new ConcurrentHashMap<>();
    String cursor = null;
    for (int page = 0; page < MAX_PAGES; page++) {
      PolymarketTradesResponse response =
          clobAuthenticated.getUserTrades(
              walletAddress,
              timestampSecondsFactory(),
              apiKey,
              passphrase,
              l2Digest,
              walletAddress,
              conditionId,
              cursor);
      if (response == null || response.data() == null) {
        return new ArrayList<>(byId.values());
      }
      for (PolymarketUserTrade trade : response.data()) {
        if (trade.id() != null) {
          byId.putIfAbsent(trade.id(), trade);
        }
      }
      cursor = response.nextCursor();
      if (cursor == null || cursor.isBlank() || END_CURSOR.equals(cursor)) {
        break;
      }
    }
    return new ArrayList<>(byId.values());
  }
}
