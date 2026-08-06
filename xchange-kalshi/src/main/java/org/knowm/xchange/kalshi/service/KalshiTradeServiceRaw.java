package org.knowm.xchange.kalshi.service;

import java.io.IOException;
import org.knowm.xchange.kalshi.KalshiExchange;
import org.knowm.xchange.kalshi.dto.trade.KalshiCancelResponse;
import org.knowm.xchange.kalshi.dto.trade.KalshiCreateOrderResponse;
import org.knowm.xchange.kalshi.dto.trade.KalshiFillsResponse;
import org.knowm.xchange.kalshi.dto.trade.KalshiOrderRequest;
import org.knowm.xchange.kalshi.dto.trade.KalshiOrderResponse;
import org.knowm.xchange.kalshi.dto.trade.KalshiOrdersResponse;

/**
 * Raw Kalshi trading access returning provider DTOs, including the native V2 create response with
 * its provider order id and fill counters.
 */
public class KalshiTradeServiceRaw extends KalshiBaseService {

  protected KalshiTradeServiceRaw(KalshiExchange exchange) {
    super(exchange);
  }

  /** Lists orders, optionally filtered by ticker and lifecycle status. */
  public KalshiOrdersResponse getKalshiOrders(
      String ticker, String status, Integer limit, String cursor) throws IOException {
    return kalshiAuthenticated.getOrders(
        apiKey, timestampFactory(), digest, ticker, status, limit, cursor);
  }

  /** Single order by provider order id. */
  public KalshiOrderResponse getKalshiOrder(String orderId) throws IOException {
    return kalshiAuthenticated.getOrder(apiKey, timestampFactory(), digest, orderId);
  }

  /** Places a V2 event-market order; the response carries the provider and client order ids. */
  public KalshiCreateOrderResponse placeKalshiOrder(KalshiOrderRequest request)
      throws IOException {
    return kalshiAuthenticated.createOrder(apiKey, timestampFactory(), digest, request);
  }

  /** Cancels an open order by provider order id. */
  public KalshiCancelResponse cancelKalshiOrder(String orderId) throws IOException {
    return kalshiAuthenticated.cancelOrder(apiKey, timestampFactory(), digest, orderId);
  }

  /** Lists user fills, optionally filtered by ticker or order id. */
  public KalshiFillsResponse getKalshiFills(
      String ticker, String orderId, Integer limit, String cursor) throws IOException {
    return kalshiAuthenticated.getFills(
        apiKey, timestampFactory(), digest, ticker, orderId, limit, cursor);
  }
}
