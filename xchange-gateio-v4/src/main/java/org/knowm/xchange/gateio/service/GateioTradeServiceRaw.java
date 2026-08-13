package org.knowm.xchange.gateio.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.Validate;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderStatus;
import org.knowm.xchange.gateio.GateioAdapters;
import org.knowm.xchange.gateio.GateioExchange;
import org.knowm.xchange.gateio.dto.GateioContinuation;
import org.knowm.xchange.gateio.dto.GateioIterationStop;
import org.knowm.xchange.gateio.dto.GateioPage;
import org.knowm.xchange.gateio.dto.GateioPageCursor;
import org.knowm.xchange.gateio.dto.account.GateioAmendOrderRequest;
import org.knowm.xchange.gateio.dto.account.GateioBatchOrderResult;
import org.knowm.xchange.gateio.dto.account.GateioCancelBatchRequest;
import org.knowm.xchange.gateio.dto.account.GateioCancelOrderResult;
import org.knowm.xchange.gateio.dto.account.GateioCountdownCancelRequest;
import org.knowm.xchange.gateio.dto.account.GateioTriggerTime;
import org.knowm.xchange.gateio.dto.account.GateioOrder;
import org.knowm.xchange.gateio.dto.account.GateioOpenOrders;
import org.knowm.xchange.gateio.dto.trade.GateioUserTradeRaw;
import java.util.stream.Collectors;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.trade.params.CurrencyPairParam;
import org.knowm.xchange.service.trade.params.TradeHistoryParamCurrencyPair;
import org.knowm.xchange.service.trade.params.TradeHistoryParamPaging;
import org.knowm.xchange.service.trade.params.TradeHistoryParamTransactionId;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParamsTimeSpan;

public class GateioTradeServiceRaw extends GateioBaseService {

  /**
   * Result ceiling for the no-paging convenience of {@link #getGateioUserTrades(TradeHistoryParams)}.
   * History is never fetched unboundedly; callers needing more use {@link
   * #getGateioUserTradesBounded(TradeHistoryParams, int)} with an explicit ceiling.
   */
  public static final int DEFAULT_HISTORY_CEILING = 1000;

  /** Query arguments shared by the paged and bounded user-trade accessors. */
  private static final class TradeHistoryArgs {
    final String currencyPair;
    final String orderId;
    final Long from;
    final Long to;

    TradeHistoryArgs(TradeHistoryParams params) {
      this.currencyPair =
          params instanceof TradeHistoryParamCurrencyPair
              ? GateioAdapters.toString(((CurrencyPairParam) params).getCurrencyPair())
              : null;
      this.orderId =
          params instanceof TradeHistoryParamTransactionId
              ? ((TradeHistoryParamTransactionId) params).getTransactionId()
              : null;
      Long start = null;
      Long end = null;
      if (params instanceof TradeHistoryParamsTimeSpan) {
        TradeHistoryParamsTimeSpan timeSpan = (TradeHistoryParamsTimeSpan) params;
        start =
            timeSpan.getStartTime() != null ? timeSpan.getStartTime().getTime() / 1000 : null;
        end = timeSpan.getEndTime() != null ? timeSpan.getEndTime().getTime() / 1000 : null;
      }
      this.from = start;
      this.to = end;
    }
  }

  public GateioTradeServiceRaw(GateioExchange exchange) {
    super(exchange);
  }

  public List<GateioOrder> listOrders(Instrument instrument, OrderStatus orderStatus)
      throws IOException {
    // validate arguments
    Objects.requireNonNull(orderStatus);
    Set<OrderStatus> allowedOrderStatuses = EnumSet.of(OrderStatus.OPEN, OrderStatus.CLOSED);
    Validate.validState(
        allowedOrderStatuses.contains(orderStatus),
        "Allowed order statuses are: {}",
        allowedOrderStatuses);
    Objects.requireNonNull(instrument);

    return gateioV4Authenticated.listOrders(
        apiKey,
        exchange.getNonceFactory(),
        gateioV4ParamsDigest,
        GateioAdapters.toString(instrument),
        GateioAdapters.toString(orderStatus));
  }

  public List<GateioUserTradeRaw> getGateioUserTrades(TradeHistoryParams params)
      throws IOException {
    // get arguments
    Integer pageLength =
        params instanceof TradeHistoryParamPaging
            ? ((TradeHistoryParamPaging) params).getPageLength()
            : null;
    Integer pageNumber =
        params instanceof TradeHistoryParamPaging
            ? ((TradeHistoryParamPaging) params).getPageNumber()
            : null;

    if (ObjectUtils.allNull(pageLength, pageNumber)) {
      GateioContinuation<GateioUserTradeRaw> continuation =
          getGateioUserTradesBounded(params, DEFAULT_HISTORY_CEILING);
      if (continuation.getStop() != GateioIterationStop.COMPLETED) {
        throw new IllegalStateException(
            "Trade history exceeds the default result ceiling; use bounded pagination");
      }
      return continuation.getItems();
    }

    TradeHistoryArgs args = new TradeHistoryArgs(params);
    return gateioV4Authenticated.getTradingHistory(
        apiKey,
        exchange.getNonceFactory(),
        gateioV4ParamsDigest,
        args.currencyPair,
        pageLength,
        pageNumber,
        args.orderId,
        null,
        args.from,
        args.to);
  }

  /** Fetches one page of the user's spot trade history; {@code null} cursor = first page. */
  public GateioPage<GateioUserTradeRaw> getGateioUserTradesPage(
      GateioPageCursor cursor, TradeHistoryParams params) throws IOException {
    return fetchUserTradesPage(cursor, params, 1000);
  }

  private GateioPage<GateioUserTradeRaw> fetchUserTradesPage(
      GateioPageCursor cursor, TradeHistoryParams params, int limit) throws IOException {
    TradeHistoryArgs args = new TradeHistoryArgs(params);
    int page = cursor == null ? 1 : cursor.getPage();
    List<GateioUserTradeRaw> items =
        gateioV4Authenticated.getTradingHistory(
            apiKey,
            exchange.getNonceFactory(),
            gateioV4ParamsDigest,
            args.currencyPair,
            limit,
            page,
            args.orderId,
            null,
            args.from,
            args.to);
    GateioPageCursor next = items.size() < limit ? null : GateioPageCursor.page(page + 1);
    return GateioPage.<GateioUserTradeRaw>builder().items(items).nextCursor(next).build();
  }

  /**
   * Iterates the user's spot trade history up to {@code maxResults}; see {@link
   * GateioPagination#iterate} for stop semantics.
   */
  public GateioContinuation<GateioUserTradeRaw> getGateioUserTradesBounded(
      TradeHistoryParams params, int maxResults) throws IOException {
    return GateioPagination.iterate(
        (cursor, remaining) -> fetchUserTradesPage(cursor, params, Math.min(1000, remaining)),
        maxResults);
  }

  /** Fetches one page of open spot orders; {@code null} cursor = first page. */
  public GateioPage<GateioOrder> getOpenOrdersPage(GateioPageCursor cursor, Integer limit)
      throws IOException {
    int page = cursor == null ? 1 : cursor.getPage();
    List<GateioOpenOrders> groups =
        gateioV4Authenticated.getOpenOrders(
            apiKey, exchange.getNonceFactory(), gateioV4ParamsDigest, page, limit, null);
    List<GateioOrder> items =
        groups.stream()
            .filter(group -> group.getOrders() != null)
            .flatMap(group -> group.getOrders().stream())
            .collect(Collectors.toList());
    int pageLimit = limit == null ? 100 : limit;
    boolean hasNext =
        groups.stream()
            .filter(group -> group.getOrders() != null)
            .anyMatch(group -> group.getOrders().size() >= pageLimit);
    GateioPageCursor next = hasNext ? GateioPageCursor.page(page + 1) : null;
    return GateioPage.<GateioOrder>builder().items(items).nextCursor(next).build();
  }

  /**
   * Iterates open spot orders up to {@code maxResults}; see {@link GateioPagination#iterate} for
   * stop semantics.
   */
  public GateioContinuation<GateioOrder> getOpenOrdersBounded(Integer limit, int maxResults)
      throws IOException {
    return GateioPagination.iterate(
        (cursor, remaining) -> getOpenOrdersPage(cursor, limit), maxResults);
  }

  public GateioOrder amendOrder(
      String orderId, CurrencyPair currencyPair, GateioAmendOrderRequest amendRequest)
      throws IOException {
    Objects.requireNonNull(orderId);
    Objects.requireNonNull(amendRequest);
    return gateioV4Authenticated.amendOrder(
        apiKey,
        exchange.getNonceFactory(),
        gateioV4ParamsDigest,
        orderId,
        GateioAdapters.toString(currencyPair),
        null,
        amendRequest);
  }

  public List<GateioOrder> cancelAllOrders(CurrencyPair currencyPair) throws IOException {
    return gateioV4Authenticated.cancelAllOrders(
        apiKey,
        exchange.getNonceFactory(),
        gateioV4ParamsDigest,
        GateioAdapters.toString(currencyPair),
        null,
        null,
        null);
  }

  public List<GateioBatchOrderResult> createBatchOrders(List<GateioOrder> gateioOrders)
      throws IOException {
    Objects.requireNonNull(gateioOrders);
    Validate.validState(!gateioOrders.isEmpty(), "batch must not be empty");
    return gateioV4Authenticated.createBatchOrders(
        apiKey, exchange.getNonceFactory(), gateioV4ParamsDigest, gateioOrders);
  }

  public List<GateioCancelOrderResult> cancelBatchOrders(
      List<GateioCancelBatchRequest> cancelRequests) throws IOException {
    Objects.requireNonNull(cancelRequests);
    Validate.validState(!cancelRequests.isEmpty(), "batch must not be empty");
    return gateioV4Authenticated.cancelBatchOrders(
        apiKey, exchange.getNonceFactory(), gateioV4ParamsDigest, cancelRequests);
  }

  public GateioTriggerTime countdownCancelAll(GateioCountdownCancelRequest request)
      throws IOException {
    Objects.requireNonNull(request);
    return gateioV4Authenticated.countdownCancelAll(
        apiKey, exchange.getNonceFactory(), gateioV4ParamsDigest, request);
  }

  public GateioOrder createOrder(GateioOrder gateioOrder) throws IOException {
    return gateioV4Authenticated.createOrder(
        apiKey, exchange.getNonceFactory(), gateioV4ParamsDigest, gateioOrder);
  }

  public GateioOrder getOrder(String orderId, Instrument instrument) throws IOException {
    return gateioV4Authenticated.getOrder(
        apiKey,
        exchange.getNonceFactory(),
        gateioV4ParamsDigest,
        orderId,
        GateioAdapters.toString(instrument));
  }

  public GateioOrder cancelOrderRaw(String orderId, Instrument instrument) throws IOException {
    return gateioV4Authenticated.cancelOrder(
        apiKey,
        exchange.getNonceFactory(),
        gateioV4ParamsDigest,
        orderId,
        GateioAdapters.toString(instrument));
  }
}
