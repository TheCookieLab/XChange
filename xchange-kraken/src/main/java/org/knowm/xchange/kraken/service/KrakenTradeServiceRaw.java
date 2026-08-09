package org.knowm.xchange.kraken.service;

import static org.knowm.xchange.service.trade.params.orders.PlaceOrderKnownParams.CLIENT_ORDER_ID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.kraken.KrakenUtils;
import org.knowm.xchange.kraken.dto.account.KrakenTradeVolume;
import org.knowm.xchange.kraken.dto.account.results.KrakenTradeVolumeResult;
import org.knowm.xchange.kraken.dto.trade.KrakenAddOrderBatchResponse;
import org.knowm.xchange.kraken.dto.trade.KrakenAmendOrderResponse;
import org.knowm.xchange.kraken.dto.trade.KrakenCancelAllOrdersAfterResponse;
import org.knowm.xchange.kraken.dto.trade.KrakenOpenPosition;
import org.knowm.xchange.kraken.dto.trade.KrakenOrder;
import org.knowm.xchange.kraken.dto.trade.KrakenOrderResponse;
import org.knowm.xchange.kraken.dto.trade.KrakenStandardOrder;
import org.knowm.xchange.kraken.dto.trade.KrakenStandardOrder.KrakenOrderBuilder;
import org.knowm.xchange.kraken.dto.trade.KrakenTrade;
import org.knowm.xchange.kraken.dto.trade.KrakenType;
import org.knowm.xchange.kraken.dto.trade.TimeInForce;
import org.knowm.xchange.kraken.dto.trade.results.KrakenAddOrderBatchResult;
import org.knowm.xchange.kraken.dto.trade.results.KrakenAmendOrderResult;
import org.knowm.xchange.kraken.dto.trade.results.KrakenCancelAllOrdersAfterResult;
import org.knowm.xchange.kraken.dto.trade.results.KrakenCancelOrderResult;
import org.knowm.xchange.kraken.dto.trade.results.KrakenCancelOrderResult.KrakenCancelOrderResponse;
import org.knowm.xchange.kraken.dto.trade.results.KrakenClosedOrdersResult;
import org.knowm.xchange.kraken.dto.trade.results.KrakenOpenOrdersResult;
import org.knowm.xchange.kraken.dto.trade.results.KrakenOpenPositionsResult;
import org.knowm.xchange.kraken.dto.trade.results.KrakenOrderResult;
import org.knowm.xchange.kraken.dto.trade.results.KrakenQueryOrderResult;
import org.knowm.xchange.kraken.dto.trade.results.KrakenQueryTradeResult;
import org.knowm.xchange.kraken.dto.trade.results.KrakenTradeHistoryResult;
import org.knowm.xchange.kraken.dto.trade.results.KrakenTradeHistoryResult.KrakenTradeHistory;
import org.knowm.xchange.service.trade.params.orders.PlaceOrderParams;

public class KrakenTradeServiceRaw extends KrakenBaseService {

  private static final ObjectMapper BATCH_MAPPER = new ObjectMapper();

  /**
   * Constructor
   *
   * @param exchange
   */
  public KrakenTradeServiceRaw(Exchange exchange) {

    super(exchange);
  }

  public Map<String, KrakenOrder> getKrakenOpenOrders() throws IOException {

    return getKrakenOpenOrders(false, null);
  }

  public Map<String, KrakenOrder> getKrakenOpenOrders(boolean includeTrades, String userRef)
      throws IOException {

    KrakenOpenOrdersResult result =
        kraken.openOrders(
            includeTrades,
            userRef,
            exchange.getExchangeSpecification().getApiKey(),
            signatureCreator,
            exchange.getNonceFactory());

    return checkResult(result).getOrders();
  }

  public Map<String, KrakenOrder> getKrakenClosedOrders() throws IOException {

    return getKrakenClosedOrders(false, null, null, null, null, null);
  }

  public Map<String, KrakenOrder> getKrakenClosedOrders(
      boolean includeTrades,
      String userRef,
      String start,
      String end,
      String offset,
      String closeTime)
      throws IOException {

    KrakenClosedOrdersResult result =
        kraken.closedOrders(
            includeTrades,
            userRef,
            start,
            end,
            offset,
            closeTime,
            exchange.getExchangeSpecification().getApiKey(),
            signatureCreator,
            exchange.getNonceFactory());

    return checkResult(result).getOrders();
  }

  public Map<String, KrakenOrder> queryKrakenOrders(String... transactionIds) throws IOException {

    return queryKrakenOrders(false, null, transactionIds);
  }

  public KrakenQueryOrderResult queryKrakenOrdersResult(
      boolean includeTrades, String userRef, String... transactionIds) throws IOException {

    KrakenQueryOrderResult krakenQueryOrderResult =
        kraken.queryOrders(
            includeTrades,
            userRef,
            createDelimitedString(transactionIds),
            exchange.getExchangeSpecification().getApiKey(),
            signatureCreator,
            exchange.getNonceFactory());

    return krakenQueryOrderResult;
  }

  public Map<String, KrakenOrder> queryKrakenOrders(
      boolean includeTrades, String userRef, String... transactionIds) throws IOException {

    KrakenQueryOrderResult result =
        kraken.queryOrders(
            includeTrades,
            userRef,
            createDelimitedString(transactionIds),
            exchange.getExchangeSpecification().getApiKey(),
            signatureCreator,
            exchange.getNonceFactory());

    return checkResult(result);
  }

  public KrakenTradeHistory getKrakenTradeHistory() throws IOException {
    return getKrakenTradeHistory(null, false, null, null, null);
  }

  public KrakenTradeHistory getKrakenTradeHistory(
      String type, boolean includeTrades, String start, String end, Long offset)
      throws IOException {

    KrakenTradeHistoryResult result =
        kraken.tradeHistory(
            type,
            includeTrades,
            start,
            end,
            offset,
            exchange.getExchangeSpecification().getApiKey(),
            signatureCreator,
            exchange.getNonceFactory());

    return checkResult(result);
  }

  public Map<String, KrakenTrade> queryKrakenTrades(String... transactionIds) throws IOException {

    return queryKrakenTrades(false, transactionIds);
  }

  public Map<String, KrakenTrade> queryKrakenTrades(boolean includeTrades, String... transactionIds)
      throws IOException {

    KrakenQueryTradeResult result =
        kraken.queryTrades(
            includeTrades,
            createDelimitedString(transactionIds),
            exchange.getExchangeSpecification().getApiKey(),
            signatureCreator,
            exchange.getNonceFactory());

    return checkResult(result);
  }

  public Map<String, KrakenOpenPosition> getKrakenOpenPositions() throws IOException {

    return getKrakenOpenPositions(false);
  }

  public Map<String, KrakenOpenPosition> getKrakenOpenPositions(
      boolean doCalcs, String... transactionIds) throws IOException {

    KrakenOpenPositionsResult result =
        kraken.openPositions(
            createDelimitedString(transactionIds),
            doCalcs,
            exchange.getExchangeSpecification().getApiKey(),
            signatureCreator,
            exchange.getNonceFactory());

    return checkResult(result);
  }

  public KrakenOrderResponse placeKrakenMarketOrder(MarketOrder marketOrder) throws IOException {

    KrakenType type = KrakenType.fromOrderType(marketOrder.getType());
    KrakenOrderBuilder orderBuilder =
        KrakenStandardOrder.getMarketOrderBuilder(
                marketOrder.getCurrencyPair(), type, marketOrder.getOriginalAmount())
            .withUserRefId(marketOrder.getUserReference())
            .withOrderFlags(marketOrder.getOrderFlags())
            .withLeverage(marketOrder.getLeverage());

    getClientOrderId(marketOrder).ifPresent(orderBuilder::withClientOrderId);

    return placeKrakenOrder(orderBuilder.buildOrder());
  }

  public KrakenOrderResponse placeKrakenSettlePositionOrder(MarketOrder marketOrder)
      throws IOException {

    KrakenType type = KrakenType.fromOrderType(marketOrder.getType());
    KrakenOrderBuilder orderBuilder =
        KrakenStandardOrder.getSettlePositionOrderBuilder(
                marketOrder.getCurrencyPair(), type, marketOrder.getOriginalAmount())
            .withUserRefId(marketOrder.getUserReference());

    getClientOrderId(marketOrder).ifPresent(orderBuilder::withClientOrderId);

    return placeKrakenOrder(orderBuilder.buildOrder());
  }

  public KrakenOrderResponse placeKrakenLimitOrder(LimitOrder limitOrder) throws IOException {
    KrakenType type = KrakenType.fromOrderType(limitOrder.getType());
    KrakenOrderBuilder krakenOrderBuilder =
        KrakenStandardOrder.getLimitOrderBuilder(
                limitOrder.getCurrencyPair(),
                type,
                limitOrder.getLimitPrice().toPlainString(),
                limitOrder.getOriginalAmount())
            .withOrderFlags(limitOrder.getOrderFlags())
            .withLeverage(limitOrder.getLeverage())
            .withTimeInForce(timeInForceFromOrder(limitOrder).orElse(null));

    Optional<String> clientOrderId = getClientOrderId(limitOrder);
    if (clientOrderId.isPresent()) {
      krakenOrderBuilder.withClientOrderId(clientOrderId.get());
    } else {
      krakenOrderBuilder.withUserRefId(limitOrder.getUserReference());
    }

    return placeKrakenOrder(krakenOrderBuilder.buildOrder());
  }

  protected Optional<String> getClientOrderId(Order order) {
    if (order instanceof PlaceOrderParams) {
      return Optional.ofNullable(
          ((PlaceOrderParams) order).getOrderParam(CLIENT_ORDER_ID, String.class));
    }
    return Optional.empty();
  }

  private Optional<TimeInForce> timeInForceFromOrder(Order order) {
    return order.getOrderFlags().stream()
        .filter(flag -> flag instanceof TimeInForce)
        .map(flag -> (TimeInForce) flag)
        .findFirst();
  }

  public KrakenOrderResponse placeKrakenOrder(KrakenStandardOrder krakenStandardOrder)
      throws IOException {

    KrakenOrderResult result = null;
    if (!krakenStandardOrder.isValidateOnly()) {
      result =
          kraken.addOrder(
              KrakenUtils.createKrakenCurrencyPair(krakenStandardOrder.getAssetPair()),
              krakenStandardOrder.getType().toString(),
              krakenStandardOrder.getOrderType().toApiFormat(),
              krakenStandardOrder.getPrice(),
              krakenStandardOrder.getSecondaryPrice(),
              krakenStandardOrder.getVolume().toPlainString(),
              krakenStandardOrder.getLeverage(),
              krakenStandardOrder.getPositionTxId(),
              delimitSet(krakenStandardOrder.getOrderFlags()),
              krakenStandardOrder.getStartTime(),
              krakenStandardOrder.getExpireTime(),
              krakenStandardOrder.getUserRefId(),
              krakenStandardOrder.getCloseOrder(),
              nullSafeToString(krakenStandardOrder.getTimeInForce()),
              krakenStandardOrder.getClientOrderId(),
              exchange.getExchangeSpecification().getApiKey(),
              signatureCreator,
              exchange.getNonceFactory());
    } else {
      result =
          kraken.addOrderValidateOnly(
              KrakenUtils.createKrakenCurrencyPair(krakenStandardOrder.getAssetPair()),
              krakenStandardOrder.getType().toString(),
              krakenStandardOrder.getOrderType().toApiFormat(),
              krakenStandardOrder.getPrice(),
              krakenStandardOrder.getSecondaryPrice(),
              krakenStandardOrder.getVolume().toPlainString(),
              krakenStandardOrder.getLeverage(),
              krakenStandardOrder.getPositionTxId(),
              delimitSet(krakenStandardOrder.getOrderFlags()),
              krakenStandardOrder.getStartTime(),
              krakenStandardOrder.getExpireTime(),
              krakenStandardOrder.getUserRefId(),
              true,
              krakenStandardOrder.getCloseOrder(),
              nullSafeToString(krakenStandardOrder.getTimeInForce()),
              krakenStandardOrder.getClientOrderId(),
              exchange.getExchangeSpecification().getApiKey(),
              signatureCreator,
              exchange.getNonceFactory());
    }

    return checkResult(result);
  }

  /**
   * Atomically amends a live order via the AmendOrder endpoint.
   *
   * <p>Exactly one of {@code orderId} and {@code clientOrderId} must be provided. The order is
   * modified in place, preserving the Kraken and client identifiers where possible. An
   * ambiguous transport outcome is never replayed: the caller reconciles by the returned order
   * identifiers or by {@code clientOrderId}.
   *
   * @param orderId Kraken order id (txid) of the order to amend, or {@code null}
   * @param clientOrderId client order id ({@code cl_ord_id}) of the order to amend, or {@code null}
   * @param orderQty new order quantity in base asset, or {@code null} to keep the current volume
   * @param limitPrice new limit price (relative values supported by the provider), or {@code null}
   * @param triggerPrice new trigger price for triggered order types, or {@code null}
   * @param postOnly cancel the order if it would take liquidity on arrival, or {@code null}
   * @param validate validate only without submitting, or {@code null}
   * @return typed amend result with amend id and order identities
   */
  public KrakenAmendOrderResponse amendKrakenOrder(
      String orderId,
      String clientOrderId,
      BigDecimal orderQty,
      String limitPrice,
      String triggerPrice,
      Boolean postOnly,
      Boolean validate)
      throws IOException {

    if (orderId == null && clientOrderId == null) {
      throw new ExchangeException("AmendOrder requires either order_id or cl_ord_id");
    }
    KrakenAmendOrderResult result =
        kraken.amendOrder(
            orderId,
            clientOrderId,
            orderQty,
            limitPrice,
            triggerPrice,
            postOnly,
            validate,
            exchange.getExchangeSpecification().getApiKey(),
            signatureCreator,
            exchange.getNonceFactory());
    return checkResult(result, "amendKrakenOrder");
  }

  /**
   * Places multiple orders in one AddOrderBatch request.
   *
   * <p>The provider returns one entry per submitted order in request order. Partial failures are
   * surfaced through the provider error array; reconcile per order by the returned transaction
   * ids rather than replaying the batch blindly.
   *
   * @param orders orders to place, at least one
   * @return typed batch result with per-order transaction ids and descriptions
   */
  public KrakenAddOrderBatchResponse placeKrakenOrdersBatch(List<KrakenStandardOrder> orders)
      throws IOException {

    if (orders == null || orders.isEmpty()) {
      throw new ExchangeException("AddOrderBatch requires at least one order");
    }
    List<Map<String, String>> orderPayloads = new ArrayList<>();
    for (KrakenStandardOrder order : orders) {
      orderPayloads.add(batchOrderPayload(order));
    }
    String ordersJson;
    try {
      ordersJson = BATCH_MAPPER.writeValueAsString(orderPayloads);
    } catch (JsonProcessingException e) {
      throw new ExchangeException("Could not serialize AddOrderBatch payload", e);
    }
    KrakenAddOrderBatchResult result =
        kraken.addOrderBatch(
            ordersJson,
            exchange.getExchangeSpecification().getApiKey(),
            signatureCreator,
            exchange.getNonceFactory());
    return checkResult(result, "placeKrakenOrdersBatch");
  }

  /**
   * Arms or disarms the cancel-all-after (dead-man) timer.
   *
   * <p>All open orders are cancelled when the timer expires unless it is re-armed with a new
   * request. A timeout of zero disables the timer. This endpoint can cancel all open orders;
   * enable it deliberately.
   *
   * @param timeoutSeconds timer length in seconds ({@code 0} disables, max 86400)
   * @return typed result with the current and trigger times
   */
  public KrakenCancelAllOrdersAfterResponse cancelAllKrakenOrdersAfter(long timeoutSeconds)
      throws IOException {

    KrakenCancelAllOrdersAfterResult result =
        kraken.cancelAllOrdersAfter(
            timeoutSeconds,
            exchange.getExchangeSpecification().getApiKey(),
            signatureCreator,
            exchange.getNonceFactory());
    return checkResult(result, "cancelAllKrakenOrdersAfter");
  }

  private Map<String, String> batchOrderPayload(KrakenStandardOrder order) {
    Map<String, String> payload = new HashMap<>();
    payload.put("pair", KrakenUtils.createKrakenCurrencyPair(order.getAssetPair()));
    payload.put("type", order.getType().toString());
    payload.put("ordertype", order.getOrderType().toApiFormat());
    if (order.getPrice() != null) {
      payload.put("price", order.getPrice());
    }
    if (order.getSecondaryPrice() != null) {
      payload.put("price2", order.getSecondaryPrice());
    }
    payload.put("volume", order.getVolume().toPlainString());
    if (order.getLeverage() != null) {
      payload.put("leverage", order.getLeverage());
    }
    if (order.getUserRefId() != null) {
      payload.put("userref", order.getUserRefId());
    }
    if (order.getStartTime() != null) {
      payload.put("starttm", order.getStartTime());
    }
    if (order.getExpireTime() != null) {
      payload.put("expiretm", order.getExpireTime());
    }
    if (order.getClientOrderId() != null) {
      payload.put("cl_ord_id", order.getClientOrderId());
    }
    String flags = delimitSet(order.getOrderFlags());
    if (flags != null) {
      payload.put("oflags", flags);
    }
    if (order.getTimeInForce() != null) {
      payload.put("timeinforce", order.getTimeInForce().toString());
    }
    return payload;
  }

  public KrakenCancelOrderResponse cancelKrakenOrder(String orderId) throws IOException {

    KrakenCancelOrderResult result =
        kraken.cancelOrder(
            exchange.getExchangeSpecification().getApiKey(),
            signatureCreator,
            exchange.getNonceFactory(),
            orderId);

    return checkResult(result);
  }

  public KrakenCancelOrderResponse cancelAllKrakenOrders() {
    KrakenCancelOrderResult result =
        kraken.cancelAllOrders(
            exchange.getExchangeSpecification().getApiKey(),
            signatureCreator,
            exchange.getNonceFactory());
    return checkResult(result);
  }

  protected KrakenTradeVolume getTradeVolume(CurrencyPair... currencyPairs) throws IOException {

    KrakenTradeVolumeResult result =
        kraken.tradeVolume(
            delimitAssetPairs(currencyPairs),
            exchange.getExchangeSpecification().getApiKey(),
            signatureCreator,
            exchange.getNonceFactory());

    return checkResult(result);
  }

  public Map<String, KrakenOrder> getOrders(String... orderIds) throws IOException {

    String orderIdsString = String.join(",", orderIds);

    KrakenQueryOrderResult krakenOrderResult =
        kraken.queryOrders(
            false,
            null,
            orderIdsString,
            exchange.getExchangeSpecification().getApiKey(),
            signatureCreator,
            exchange.getNonceFactory());

    return checkResult(krakenOrderResult);
  }

  private String nullSafeToString(Object value) {
    return value == null ? null : value.toString();
  }
}
