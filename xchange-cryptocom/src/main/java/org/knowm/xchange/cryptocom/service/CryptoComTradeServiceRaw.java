package org.knowm.xchange.cryptocom.service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.cryptocom.CryptoComExchange;
import org.knowm.xchange.cryptocom.dto.CryptoComException;
import org.knowm.xchange.cryptocom.dto.CryptoComRequest;
import org.knowm.xchange.cryptocom.dto.CryptoComResponse;
import org.knowm.xchange.cryptocom.dto.CryptoComUnknownOrderOutcomeException;
import org.knowm.xchange.cryptocom.dto.trade.CryptoComOrder;
import org.knowm.xchange.cryptocom.dto.trade.CryptoComOrderAck;
import org.knowm.xchange.cryptocom.dto.trade.CryptoComOrderPlacementResult;
import org.knowm.xchange.cryptocom.dto.trade.CryptoComOrderSide;
import org.knowm.xchange.cryptocom.dto.trade.CryptoComOrderType;
import org.knowm.xchange.cryptocom.dto.trade.CryptoComPlacementOutcome;
import org.knowm.xchange.cryptocom.dto.trade.CryptoComTimeInForce;
import org.knowm.xchange.cryptocom.dto.trade.CryptoComUserTrade;

/**
 * Raw trading operations. Placement methods keep the provider order id, client/reference id and
 * request id available to callers; history methods page through the provider continuation model
 * with bounded, no-progress-aware collection.
 */
public class CryptoComTradeServiceRaw extends CryptoComBaseService {

  /** Maximum provider pages fetched for one history call regardless of caller limits. */
  public static final int MAX_HISTORY_PAGES = 10;

  /** Default rows per history page when the caller does not specify a page size. */
  public static final int DEFAULT_HISTORY_PAGE_SIZE = 100;

  /** History window searched for a lost placement during reconciliation (milliseconds). */
  static final long RECONCILE_HISTORY_WINDOW_MILLIS = 3_600_000L;

  protected CryptoComTradeServiceRaw(
      CryptoComExchange exchange, ResilienceRegistries resilienceRegistries) {
    super(exchange, resilienceRegistries);
  }

  /**
   * Creates an order. {@code execInst} carries execution instructions such as {@code POST_ONLY} or
   * {@code REDUCE_ONLY} (reduce-only semantics for derivatives) and is transmitted verbatim when
   * non-null.
   *
   * <p>The returned {@link CryptoComOrderPlacementResult} makes the envelope request id
   * first-class and records how the outcome was established. Placement is never decorated with
   * retry policies: a transport failure after transmission triggers reconciliation (open orders,
   * then bounded recent order history) instead of an automatic re-send. When reconciliation itself
   * fails the outcome is ambiguous and a {@link
   * org.knowm.xchange.cryptocom.dto.CryptoComUnknownOrderOutcomeException} is raised.
   */
  public CryptoComOrderPlacementResult createCryptoComOrder(
      String instrumentName,
      CryptoComOrderSide side,
      CryptoComOrderType type,
      String price,
      String quantity,
      CryptoComTimeInForce timeInForce,
      String clientOid,
      String execInst)
      throws IOException, CryptoComException {
    Map<String, Object> params = new HashMap<>();
    params.put("instrument_name", instrumentName);
    params.put("side", side.name());
    params.put("type", type.name());
    if (price != null) {
      params.put("price", price);
    }
    // Crypto.com requires the spend amount for MARKET BUY orders under "notional" (quote
    // currency) rather than "quantity" (base currency); every other combination uses "quantity".
    if (type == CryptoComOrderType.MARKET && side == CryptoComOrderSide.BUY) {
      params.put("notional", quantity);
    } else {
      params.put("quantity", quantity);
    }
    if (timeInForce != null) {
      params.put("time_in_force", timeInForce.name());
    }
    if (clientOid != null) {
      params.put("client_oid", clientOid);
    }
    if (execInst != null) {
      params.put("exec_inst", execInst);
    }
    CryptoComRequest request = buildRequest("private/create-order", params);
    long requestId = request.getId();
    try {
      // Deliberately NOT wrapped in resilience decorators: an automatic retry of a
      // non-idempotent placement could double-fill after an ambiguous transport failure.
      CryptoComResponse response = cryptoCom.createOrder(request);
      CryptoComOrderAck ack = toObject(response.getResult(), CryptoComOrderAck.class);
      return new CryptoComOrderPlacementResult(
          CryptoComPlacementOutcome.ACKED, requestId, ack == null ? null : ack.getOrderId(), clientOid);
    } catch (IOException e) {
      return reconcile(requestId, "private/create-order", instrumentName, clientOid, e);
    }
  }

  /**
   * Reconciles an order whose placement crossed the network without a provider answer. Returns
   * {@link CryptoComPlacementOutcome#RECONCILED} when the order surfaced in open orders or recent
   * order history, {@link CryptoComPlacementOutcome#NOT_FOUND} when those queries complete within
   * the bounded window without surfacing it (a deterministic absent outcome, not an authoritative
   * rejection - the order may still exist), and raises {@link CryptoComUnknownOrderOutcomeException}
   * when reconciliation itself fails or the placement carried no client reference to match on.
   * ZERO automatic replay: the order is never re-sent from this path.
   */
  private CryptoComOrderPlacementResult reconcile(
      long requestId,
      String method,
      String instrumentName,
      String clientOid,
      IOException transportFailure)
      throws IOException {
    if (clientOid == null || clientOid.isEmpty()) {
      throw new CryptoComUnknownOrderOutcomeException(
          requestId, method, null, instrumentName, transportFailure);
    }
    try {
      for (CryptoComOrder candidate : getCryptoComOpenOrders(null)) {
        if (clientOid.equals(candidate.getClientOid())) {
          return new CryptoComOrderPlacementResult(
              CryptoComPlacementOutcome.RECONCILED, requestId, candidate.getOrderId(), clientOid);
        }
      }
      long now = System.currentTimeMillis();
      for (CryptoComOrder candidate :
          getCryptoComOrderHistory(null, now - RECONCILE_HISTORY_WINDOW_MILLIS, now, null)) {
        if (clientOid.equals(candidate.getClientOid())) {
          return new CryptoComOrderPlacementResult(
              CryptoComPlacementOutcome.RECONCILED, requestId, candidate.getOrderId(), clientOid);
        }
      }
      return new CryptoComOrderPlacementResult(
          CryptoComPlacementOutcome.NOT_FOUND, requestId, null, clientOid);
    } catch (IOException reconcileFailure) {
      throw new CryptoComUnknownOrderOutcomeException(
          requestId, method, clientOid, instrumentName, reconcileFailure);
    }
  }

  /** Creates an order without execution instructions. */
  public CryptoComOrderPlacementResult createCryptoComOrder(
      String instrumentName,
      CryptoComOrderSide side,
      CryptoComOrderType type,
      String price,
      String quantity,
      CryptoComTimeInForce timeInForce,
      String clientOid)
      throws IOException, CryptoComException {
    return createCryptoComOrder(
        instrumentName, side, type, price, quantity, timeInForce, clientOid, null);
  }

  /**
   * Creates a trigger order (STOP_LOSS, STOP_LIMIT, TAKE_PROFIT, TAKE_PROFIT_LIMIT) through the
   * advanced order endpoint. The raw trigger price and trigger time-in-force are round-tripped
   * verbatim; the acknowledged {@code order_id} and {@code client_oid} stay available on the ack.
   */
  public CryptoComOrderPlacementResult createCryptoComAdvancedOrder(
      String instrumentName,
      CryptoComOrderSide side,
      CryptoComOrderType type,
      String price,
      String quantity,
      CryptoComTimeInForce timeInForce,
      String triggerPrice,
      CryptoComTimeInForce triggerTimeInForce,
      String clientOid)
      throws IOException, CryptoComException {
    if (type != CryptoComOrderType.STOP_LOSS
        && type != CryptoComOrderType.STOP_LIMIT
        && type != CryptoComOrderType.TAKE_PROFIT
        && type != CryptoComOrderType.TAKE_PROFIT_LIMIT) {
      throw new IllegalArgumentException(
          "Advanced order type must be STOP_LOSS, STOP_LIMIT, TAKE_PROFIT or TAKE_PROFIT_LIMIT: "
              + type);
    }
    if (triggerPrice == null || triggerPrice.isEmpty()) {
      throw new IllegalArgumentException("triggerPrice is required for advanced orders");
    }
    Map<String, Object> params = new HashMap<>();
    params.put("instrument_name", instrumentName);
    params.put("side", side.name());
    params.put("type", type.name());
    if (price != null) {
      params.put("price", price);
    }
    params.put("quantity", quantity);
    if (timeInForce != null) {
      params.put("time_in_force", timeInForce.name());
    }
    Map<String, Object> trigger = new HashMap<>();
    trigger.put("trigger_price", triggerPrice);
    if (triggerTimeInForce != null) {
      trigger.put("time_in_force", triggerTimeInForce.name());
    }
    params.put("trigger", trigger);
    if (clientOid != null) {
      params.put("client_oid", clientOid);
    }
    CryptoComRequest request = buildRequest("private/advanced/create-order", params);
    long requestId = request.getId();
    try {
// Placement is never automatically retried; reconcile on ambiguous transport failure.
    CryptoComResponse response =
        apiCall("private/advanced/create-order", () -> cryptoCom.createAdvancedOrder(request));
    CryptoComOrderAck ack = toObject(response.getResult(), CryptoComOrderAck.class);
    return new CryptoComOrderPlacementResult(
        CryptoComPlacementOutcome.ACKED, requestId, ack == null ? null : ack.getOrderId(), clientOid);
    } catch (IOException e) {
      return reconcile(requestId, "private/advanced/create-order", instrumentName, clientOid, e);
    }
  }

  public CryptoComOrderAck cancelCryptoComOrder(String orderId)
      throws IOException, CryptoComException {
    Map<String, Object> params = new HashMap<>();
    params.put("order_id", orderId);
    CryptoComRequest request = buildRequest("private/cancel-order", params);
    CryptoComResponse response =
        apiCall("private/cancel-order", () -> cryptoCom.cancelOrder(request));
    return toObject(response.getResult(), CryptoComOrderAck.class);
  }

  public void cancelAllCryptoComOrders(String instrumentName)
      throws IOException, CryptoComException {
    Map<String, Object> params = new HashMap<>();
    if (instrumentName != null) {
      params.put("instrument_name", instrumentName);
    }
    CryptoComRequest request = buildRequest("private/cancel-all-orders", params);
    apiCall("private/cancel-all-orders", () -> cryptoCom.cancelAllOrders(request));
  }

  public List<CryptoComOrder> getCryptoComOpenOrders(String instrumentName)
      throws IOException, CryptoComException {
    Map<String, Object> params = new HashMap<>();
    if (instrumentName != null) {
      params.put("instrument_name", instrumentName);
    }
    CryptoComRequest request = buildRequest("private/get-open-orders", params);
    CryptoComResponse response =
        apiCall("private/get-open-orders", () -> cryptoCom.getOpenOrders(request));
    return getDataList(response, CryptoComOrder.class);
  }

  public CryptoComOrder getCryptoComOrderDetail(String orderId)
      throws IOException, CryptoComException {
    Map<String, Object> params = new HashMap<>();
    params.put("order_id", orderId);
    CryptoComRequest request = buildRequest("private/get-order-detail", params);
    CryptoComResponse response =
        apiCall("private/get-order-detail", () -> cryptoCom.getOrderDetail(request));
    return toObject(response.getResult(), CryptoComOrder.class);
  }

  /**
   * Order history with bounded pagination: pages of {@link #DEFAULT_HISTORY_PAGE_SIZE} are fetched
   * without over-running the caller limit, repeated/empty pages or {@link #MAX_HISTORY_PAGES}.
   *
   * @param limit caller cap; {@code null} collects until the provider is exhausted or the page
   *     bound is reached
   */
  public List<CryptoComOrder> getCryptoComOrderHistory(
      String instrumentName, Long startTime, Long endTime, Integer limit)
      throws IOException, CryptoComException {
    return orEmpty(
        fetchPagesBounded(
            MAX_HISTORY_PAGES,
            DEFAULT_HISTORY_PAGE_SIZE,
            limit,
            (page, pageSize) -> orderHistoryPage(instrumentName, startTime, endTime, page, pageSize)));
  }

  private List<CryptoComOrder> orderHistoryPage(
      String instrumentName, Long startTime, Long endTime, Integer page, Integer pageSize)
      throws IOException {
    CryptoComRequest request =
        buildRequest(
            "private/get-order-history",
            historyParams(instrumentName, startTime, endTime, page, pageSize));
    CryptoComResponse response =
        apiCall("private/get-order-history", () -> cryptoCom.getOrderHistory(request));
    return getDataList(response, CryptoComOrder.class);
  }

  /**
   * User/fill history with bounded pagination (see {@link #getCryptoComOrderHistory(String, Long,
   * Long, Integer)}).
   */
  public List<CryptoComUserTrade> getCryptoComUserTrades(
      String instrumentName, Long startTime, Long endTime, Integer limit)
      throws IOException, CryptoComException {
    return orEmpty(
        fetchPagesBounded(
            MAX_HISTORY_PAGES,
            DEFAULT_HISTORY_PAGE_SIZE,
            limit,
            (page, pageSize) -> userTradesPage(instrumentName, startTime, endTime, page, pageSize)));
  }

  private List<CryptoComUserTrade> userTradesPage(
      String instrumentName, Long startTime, Long endTime, Integer page, Integer pageSize)
      throws IOException {
    CryptoComRequest request =
        buildRequest(
            "private/get-trades",
            historyParams(instrumentName, startTime, endTime, page, pageSize));
    CryptoComResponse response =
        apiCall("private/get-trades", () -> cryptoCom.getUserTrades(request));
    return getDataList(response, CryptoComUserTrade.class);
  }

  private Map<String, Object> historyParams(
      String instrumentName, Long startTime, Long endTime, Integer page, Integer pageSize) {
    Map<String, Object> params = new HashMap<>();
    if (instrumentName != null) {
      params.put("instrument_name", instrumentName);
    }
    if (startTime != null) {
      params.put("start_time", startTime);
    }
    if (endTime != null) {
      params.put("end_time", endTime);
    }
    if (page != null) {
      params.put("page", page);
    }
    if (pageSize != null) {
      params.put("page_size", pageSize);
    }
    return params;
  }
}