package org.knowm.xchange.coinbasederivatives.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.knowm.xchange.coinbasederivatives.CoinbaseDerivativesExchange;
import org.knowm.xchange.coinbasederivatives.CoinbaseDerivativesPlacementResult;
import org.knowm.xchange.coinbasederivatives.client.ReplaySafety;
import org.knowm.xchange.coinbasederivatives.client.RpcResult;
import org.knowm.xchange.coinbasederivatives.dto.trade.CoinbaseDerivativesOrder;
import org.knowm.xchange.coinbasederivatives.dto.trade.CoinbaseDerivativesPlacementResponse;
import org.knowm.xchange.coinbasederivatives.dto.trade.CoinbaseDerivativesUserTrades;
import org.knowm.xchange.exceptions.ExchangeException;

/** Exchange-specific private order and fill operations. */
public class CoinbaseDerivativesTradeServiceRaw extends CoinbaseDerivativesBaseService {
  public CoinbaseDerivativesTradeServiceRaw(CoinbaseDerivativesExchange exchange) {
    super(exchange);
  }

  /**
   * Places an order exactly once and returns all provider identities.
   *
   * <p>{@code label} is not an idempotency key. An ambiguous transport outcome is surfaced and is
   * never retried by this method.
   */
  public CoinbaseDerivativesPlacementResult placeOrder(
      String side,
      String instrumentName,
      BigDecimal amount,
      String type,
      String label,
      BigDecimal price,
      boolean reduceOnly,
      BigDecimal triggerPrice)
      throws IOException {
    Map<String, Object> params = new ConcurrentHashMap<>();
    params.put("instrument_name", instrumentName);
    params.put("amount", amount);
    params.put("type", type);
    put(params, "label", label);
    put(params, "price", price);
    if (reduceOnly) {
      params.put("reduce_only", true);
    }
    put(params, "trigger_price", triggerPrice);
    if (triggerPrice != null) {
      params.put("trigger", "last_price");
    }
    RpcResult<CoinbaseDerivativesPlacementResponse> response =
        transport.callPrivateWithId(
            "private/" + side,
            params,
            CoinbaseDerivativesPlacementResponse.class,
            ReplaySafety.PLACEMENT);
    CoinbaseDerivativesOrder order = response.value().order();
    if (order == null || order.orderId() == null) {
      throw new ExchangeException("Coinbase derivatives placement returned no order ID");
    }
    List<String> related = new ArrayList<>();
    if (order.primaryOrderId() != null && !order.primaryOrderId().equals(order.orderId())) {
      related.add(order.primaryOrderId());
    }
    if (order.otoOrderIds() != null) {
      related.addAll(order.otoOrderIds());
    }
    return new CoinbaseDerivativesPlacementResult(
        order.orderId(),
        related,
        response.requestId(),
        order.instrumentName() == null ? instrumentName : order.instrumentName(),
        order.direction() == null ? side : order.direction(),
        order.orderType() == null ? type : order.orderType(),
        order.amount() == null ? amount : order.amount(),
        order.price() == null ? price : order.price(),
        order.reduceOnly() == null ? reduceOnly : order.reduceOnly(),
        order.label() == null ? label : order.label(),
        order.orderState());
  }

  public CoinbaseDerivativesOrder cancel(String orderId) throws IOException {
    return transport.callPrivate(
        "private/cancel",
        Map.of("order_id", orderId),
        CoinbaseDerivativesOrder.class,
        ReplaySafety.IDEMPOTENT_CANCELLATION);
  }

  public JsonNode cancelAllByInstrument(String instrumentName) throws IOException {
    return transport.callPrivate(
        "private/cancel_all_by_instrument",
        Map.of("instrument_name", instrumentName),
        JsonNode.class,
        ReplaySafety.IDEMPOTENT_CANCELLATION);
  }

  public List<CoinbaseDerivativesOrder> getOpenOrders(String instrumentName) throws IOException {
    String method =
        instrumentName == null
            ? "private/get_open_orders"
            : "private/get_open_orders_by_instrument";
    Map<String, Object> params =
        instrumentName == null ? Map.of() : Map.of("instrument_name", instrumentName);
    return Arrays.asList(
        transport.callPrivate(method, params, CoinbaseDerivativesOrder[].class, ReplaySafety.READ));
  }

  public List<CoinbaseDerivativesOrder> getOrderHistory(
      String instrumentName, Integer count, Integer offset) throws IOException {
    Map<String, Object> params = new ConcurrentHashMap<>();
    params.put("instrument_name", instrumentName);
    put(params, "count", count);
    put(params, "offset", offset);
    return Arrays.asList(
        transport.callPrivate(
            "private/get_order_history_by_instrument",
            params,
            CoinbaseDerivativesOrder[].class,
            ReplaySafety.READ));
  }

  public CoinbaseDerivativesOrder getOrderState(String orderId) throws IOException {
    return transport.callPrivate(
        "private/get_order_state",
        Map.of("order_id", orderId),
        CoinbaseDerivativesOrder.class,
        ReplaySafety.READ);
  }

  public CoinbaseDerivativesUserTrades getUserTrades(
      String instrumentName, String currency, Integer count) throws IOException {
    String method;
    Map<String, Object> params = new ConcurrentHashMap<>();
    if (instrumentName != null) {
      method = "private/get_user_trades_by_instrument";
      params.put("instrument_name", instrumentName);
    } else {
      method = "private/get_user_trades_by_currency";
      params.put("currency", currency);
    }
    put(params, "count", count);
    return transport.callPrivate(
        method, params, CoinbaseDerivativesUserTrades.class, ReplaySafety.READ);
  }

  private static void put(Map<String, Object> params, String name, Object value) {
    if (value != null) {
      params.put(name, value);
    }
  }
}
