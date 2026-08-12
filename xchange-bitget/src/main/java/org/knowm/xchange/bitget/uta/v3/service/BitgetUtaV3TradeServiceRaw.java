package org.knowm.xchange.bitget.uta.v3.service;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.knowm.xchange.bitget.BitgetExchange;
import org.knowm.xchange.bitget.uta.v3.BitgetUtaV3ErrorAdapter;
import org.knowm.xchange.bitget.uta.v3.BitgetUtaV3UnknownOutcomeException;
import org.knowm.xchange.bitget.uta.v3.common.BitgetUtaV3CursorPage;
import org.knowm.xchange.bitget.uta.v3.common.BitgetUtaV3Exception;
import org.knowm.xchange.bitget.uta.v3.trade.BitgetUtaV3CancelOrderRequest;
import org.knowm.xchange.bitget.uta.v3.trade.BitgetUtaV3Fill;
import org.knowm.xchange.bitget.uta.v3.trade.BitgetUtaV3ModifyOrderRequest;
import org.knowm.xchange.bitget.uta.v3.trade.BitgetUtaV3Order;
import org.knowm.xchange.bitget.uta.v3.trade.BitgetUtaV3OrderId;
import org.knowm.xchange.bitget.uta.v3.trade.BitgetUtaV3PlaceOrderRequest;
import org.knowm.xchange.bitget.uta.v3.trade.BitgetUtaV3Position;
import org.knowm.xchange.bitget.uta.v3.trade.BitgetUtaV3StrategyOrderRequest;

/** Raw UTA v3 trade calls (place, cancel, modify, history, fills, positions). */
public class BitgetUtaV3TradeServiceRaw extends BitgetUtaV3BaseService {

  public BitgetUtaV3TradeServiceRaw(BitgetExchange exchange) {
    super(exchange);
  }

  /**
   * Places an order; returns provider order identity (orderId + echoed clientOid). When the
   * request carries no clientOid, one is generated so every placement is idempotency-keyed.
   */
  public BitgetUtaV3OrderId placeOrder(BitgetUtaV3PlaceOrderRequest request) throws IOException {
    BitgetUtaV3PlaceOrderRequest idempotentRequest = withClientOid(request);
    try {
      return bitgetUtaV3Authenticated
          .placeOrder(
              apiKey,
              bitgetUtaV3Digest,
              passphrase,
              exchange.getNonceFactory(),
              idempotentRequest)
          .getData();
    } catch (BitgetUtaV3Exception e) {
      // 40010/40725/45001: the order may have been placed server-side after transmission. Never
      // replay blindly; surface an explicit unknown-outcome exception so callers reconcile by
      // client/exchange order id through trade/order-info.
      if (BitgetUtaV3ErrorAdapter.isAmbiguousPlacementOutcome(e.getCode())) {
        throw new BitgetUtaV3UnknownOutcomeException(e, idempotentRequest.getClientOid());
      }
      throw BitgetUtaV3ErrorAdapter.adapt(e);
    } catch (IOException e) {
      // Transport failure (read timeout, connection reset): the provider may still have accepted
      // the order. Treat exactly like the ambiguous provider codes — never replay blindly,
      // reconcile by client/exchange order id through trade/order-info.
      throw new BitgetUtaV3UnknownOutcomeException(e, idempotentRequest.getClientOid());
    }
  }

  /**
   * Returns the request with a generated clientOid when none was supplied, so every placement is
   * idempotency-keyed before transmission: if the provider accepts the order but the response is
   * lost, the surfaced {@link BitgetUtaV3UnknownOutcomeException} carries the key and callers can
   * reconcile through trade/order-info without duplicating on retry. Mirror of
   * {@link BitgetUtaV3AccountServiceRaw#withClientOid(BitgetUtaV3TransferRequest)}; the generated
   * key drops hyphens because place-order clientOid must match {@code ^[\.A-Z\:/a-z0-9_-]{1,32}$}.
   */
  private static BitgetUtaV3PlaceOrderRequest withClientOid(BitgetUtaV3PlaceOrderRequest request) {
    if (request.getClientOid() != null && !request.getClientOid().isEmpty()) {
      return request;
    }
    return request.toBuilder().clientOid(UUID.randomUUID().toString().replace("-", "")).build();
  }

  /** Cancels an order; requires orderId or clientOid. */
  public BitgetUtaV3OrderId cancelOrder(BitgetUtaV3CancelOrderRequest request) throws IOException {
    try {
      return bitgetUtaV3Authenticated
          .cancelOrder(apiKey, bitgetUtaV3Digest, passphrase, exchange.getNonceFactory(), request)
          .getData();
    } catch (BitgetUtaV3Exception e) {
      throw BitgetUtaV3ErrorAdapter.adapt(e);
    }
  }

  /** Modifies an open order's price/qty. */
  public BitgetUtaV3OrderId modifyOrder(BitgetUtaV3ModifyOrderRequest request) throws IOException {
    try {
      return bitgetUtaV3Authenticated
          .modifyOrder(apiKey, bitgetUtaV3Digest, passphrase, exchange.getNonceFactory(), request)
          .getData();
    } catch (BitgetUtaV3Exception e) {
      throw BitgetUtaV3ErrorAdapter.adapt(e);
    }
  }

  /** Places a strategy (trigger/TP-SL) order. */
  public BitgetUtaV3OrderId placeStrategyOrder(
      BitgetUtaV3StrategyOrderRequest request, String channelApiCode) throws IOException {
    try {
      return bitgetUtaV3Authenticated
          .placeStrategyOrder(
              apiKey,
              bitgetUtaV3Digest,
              passphrase,
              exchange.getNonceFactory(),
              channelApiCode,
              request)
          .getData();
    } catch (BitgetUtaV3Exception e) {
      throw BitgetUtaV3ErrorAdapter.adapt(e);
    } catch (IOException e) {
      // Transport failure (read timeout, connection reset): the provider may still have accepted
      // the strategy order. Same unknown-outcome contract as placeOrder — never replay blindly.
      // clientOid is only echoed for tpsl orders (trigger orders do not support it), so the
      // exception carries it only when the DTO did.
      throw new BitgetUtaV3UnknownOutcomeException(e, request.getClientOid());
    }
  }

  /**
   * Open/partial orders, newest first; {@code cursor} is the smallest orderId of the previous page
   * (pass to query older orders). Limit default 100, max 100.
   */
  public BitgetUtaV3CursorPage<BitgetUtaV3Order> getUnfilledOrders(
      String category,
      String symbol,
      String startTime,
      String endTime,
      Integer limit,
      String cursor)
      throws IOException {
    try {
      return bitgetUtaV3Authenticated
          .unfilledOrders(
              apiKey,
              bitgetUtaV3Digest,
              passphrase,
              exchange.getNonceFactory(),
              category,
              symbol,
              startTime,
              endTime,
              limit,
              cursor)
          .getData();
    } catch (BitgetUtaV3Exception e) {
      throw BitgetUtaV3ErrorAdapter.adapt(e);
    }
  }

  /** Last-90-day history; each query covers at most a 30-day range. */
  public BitgetUtaV3CursorPage<BitgetUtaV3Order> getHistoryOrders(
      String category,
      String symbol,
      String startTime,
      String endTime,
      Integer limit,
      String cursor)
      throws IOException {
    try {
      return bitgetUtaV3Authenticated
          .historyOrders(
              apiKey,
              bitgetUtaV3Digest,
              passphrase,
              exchange.getNonceFactory(),
              category,
              symbol,
              startTime,
              endTime,
              limit,
              cursor)
          .getData();
    } catch (BitgetUtaV3Exception e) {
      throw BitgetUtaV3ErrorAdapter.adapt(e);
    }
  }

  /**
   * Single order detail. {@code orderId} takes priority when both orderId and clientOid are
   * supplied.
   */
  public BitgetUtaV3Order getOrderInfo(String orderId, String clientOid) throws IOException {
    try {
      return bitgetUtaV3Authenticated
          .orderInfo(
              apiKey, bitgetUtaV3Digest, passphrase, exchange.getNonceFactory(), orderId, clientOid)
          .getData();
    } catch (BitgetUtaV3Exception e) {
      throw BitgetUtaV3ErrorAdapter.adapt(e);
    }
  }

  /** Fill history; last 90 days, at most a 30-day range per query. */
  public BitgetUtaV3CursorPage<BitgetUtaV3Fill> getFills(
      String category,
      String orderId,
      String startTime,
      String endTime,
      Integer limit,
      String cursor)
      throws IOException {
    try {
      return bitgetUtaV3Authenticated
          .fills(
              apiKey,
              bitgetUtaV3Digest,
              passphrase,
              exchange.getNonceFactory(),
              category,
              orderId,
              startTime,
              endTime,
              limit,
              cursor)
          .getData();
    } catch (BitgetUtaV3Exception e) {
      throw BitgetUtaV3ErrorAdapter.adapt(e);
    }
  }

  /** Current positions, optionally filtered by category/symbol/posSide. */
  public List<BitgetUtaV3Position> getCurrentPositions(
      String category, String symbol, String posSide) throws IOException {
    try {
      BitgetUtaV3CursorPage<BitgetUtaV3Position> page =
          bitgetUtaV3Authenticated
              .currentPositions(
                  apiKey,
                  bitgetUtaV3Digest,
                  passphrase,
                  exchange.getNonceFactory(),
                  category,
                  symbol,
                  posSide)
              .getData();
      return page == null ? List.of() : page.getList();
    } catch (BitgetUtaV3Exception e) {
      throw BitgetUtaV3ErrorAdapter.adapt(e);
    }
  }
}
