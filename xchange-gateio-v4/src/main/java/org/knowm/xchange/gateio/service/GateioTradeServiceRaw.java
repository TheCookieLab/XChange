package org.knowm.xchange.gateio.service;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.Validate;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
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
import org.knowm.xchange.gateio.dto.account.GateioOpenOrders;
import org.knowm.xchange.gateio.dto.account.GateioTriggerTime;
import org.knowm.xchange.gateio.dto.trade.*;
import java.util.stream.Collectors;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.trade.params.*;

import java.io.IOException;
import java.util.*;

import static org.knowm.xchange.gateio.GateioResilience.ORDERS_RATE_LIMITER;

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
              ? GateioAdapters.toGateioInstrument(((CurrencyPairParam) params).getCurrencyPair())
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

  public GateioTradeServiceRaw(GateioExchange exchange, ResilienceRegistries resilienceRegistries) {
    super(exchange, resilienceRegistries);
  }

  public List<GateioSpotOrderResponse> listOrders(Instrument instrument, OrderStatus orderStatus)
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
        GateioAdapters.toGateioInstrument(instrument),
        GateioAdapters.toGateioInstrument(orderStatus));
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
        // a full first page stops the bounded run at MAX_RESULTS even when the
        // history ends exactly at the ceiling; confirm with one more page
        // fetch before rejecting
        GateioPageCursor next = continuation.getNextCursor();
        if (next != null) {
          GateioPage<GateioUserTradeRaw> confirmation = getGateioUserTradesPage(next, params);
          if (confirmation.getItems().isEmpty() && !confirmation.hasNext()) {
            return continuation.getItems();
          }
        }
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
    int skip = cursor == null ? 0 : cursor.getSkip();
    List<GateioUserTradeRaw> providerItems =
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
    // resume state: drop the prefix already consumed by a previous bounded run
    List<GateioUserTradeRaw> items =
        skip == 0
            ? providerItems
            : providerItems.size() <= skip
                ? List.of()
                : new ArrayList<>(providerItems.subList(skip, providerItems.size()));
    GateioPageCursor next = providerItems.size() < limit ? null : GateioPageCursor.page(page + 1);
    return GateioPage.<GateioUserTradeRaw>builder().items(items).nextCursor(next).build();
  }

  /**
   * Iterates the user's spot trade history up to {@code maxResults}; see {@link
   * GateioPagination#iterate} for stop semantics.
   */
  public GateioContinuation<GateioUserTradeRaw> getGateioUserTradesBounded(
      TradeHistoryParams params, int maxResults) throws IOException {
    return GateioPagination.iterate(
        cursor -> fetchUserTradesPage(cursor, params, 1000), maxResults);
  }

  /** Fetches one page of open spot orders; {@code null} cursor = first page. */
  public GateioPage<GateioSpotOrderResponse> getOpenOrdersPage(GateioPageCursor cursor, Integer limit)
      throws IOException {
    int page = cursor == null ? 1 : cursor.getPage();
    int skip = cursor == null ? 0 : cursor.getSkip();
    List<GateioOpenOrders> groups =
        gateioV4Authenticated.getOpenOrders(
            apiKey, exchange.getNonceFactory(), gateioV4ParamsDigest, page, limit, null);
    List<GateioSpotOrderResponse> providerItems =
        groups.stream()
            .filter(group -> group.getOrders() != null)
            .flatMap(group -> group.getOrders().stream())
            .collect(Collectors.toList());
    // resume state: drop the prefix already consumed by a previous bounded run
    List<GateioSpotOrderResponse> items =
        skip == 0
            ? providerItems
            : providerItems.size() <= skip
                ? List.of()
                : new ArrayList<>(providerItems.subList(skip, providerItems.size()));
    int pageLimit = limit == null ? 100 : limit;
    boolean hasNext =
        groups.stream()
            .filter(group -> group.getOrders() != null)
            .anyMatch(group -> group.getOrders().size() >= pageLimit);
    GateioPageCursor next = hasNext ? GateioPageCursor.page(page + 1) : null;
    return GateioPage.<GateioSpotOrderResponse>builder().items(items).nextCursor(next).build();
  }

  /**
   * Iterates open spot orders up to {@code maxResults}; see {@link GateioPagination#iterate} for
   * stop semantics.
   */
  public GateioContinuation<GateioSpotOrderResponse> getOpenOrdersBounded(Integer limit, int maxResults)
      throws IOException {
    return GateioPagination.iterate(cursor -> getOpenOrdersPage(cursor, limit), maxResults);
  }

  public GateioSpotOrderResponse amendOrder(
      String orderId, CurrencyPair currencyPair, GateioAmendOrderRequest amendRequest)
      throws IOException {
    Objects.requireNonNull(orderId);
    Objects.requireNonNull(amendRequest);
    return gateioV4Authenticated.amendOrder(
        apiKey,
        exchange.getNonceFactory(),
        gateioV4ParamsDigest,
        orderId,
        GateioAdapters.toGateioInstrument(currencyPair),
        null,
        amendRequest);
  }

  /**
   * Cancels all matching open spot orders for the pair.
   *
   * <p>Gate's {@code OrderCancel} elements carry per-order outcome fields ({@code succeeded},
   * {@code label}, {@code message}) next to the order fields: bulk cancellation succeeds
   * partially, and callers must inspect {@link GateioCancelOrderResult#getSucceeded()} before
   * treating an order as cancelled.
   */
  public List<GateioCancelOrderResult> cancelAllOrders(CurrencyPair currencyPair)
      throws IOException {
    return gateioV4Authenticated.cancelAllOrders(
        apiKey,
        exchange.getNonceFactory(),
        gateioV4ParamsDigest,
        GateioAdapters.toGateioInstrument(currencyPair),
        null,
        null,
        null);
  }

  public List<GateioBatchOrderResult> createBatchOrders(List<GateioSpotOrderRequest> gateioOrders)
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

  public GateioSpotOrderResponse createOrder(GateioSpotOrderRequest gateioOrder) throws IOException {
    return decorateApiCall(
        () ->
            gateioV4Authenticated.createOrder(
                apiKey, exchange.getNonceFactory(), gateioV4ParamsDigest, gateioOrder))
        .withRateLimiter(rateLimiter(ORDERS_RATE_LIMITER))
        .call();
  }

  public GateioFuturesOrderResponse createFuturesOrder(GateioFuturesOrderRequest gateioFuturesOrder) throws IOException {
    Instrument instrument = GateioAdapters.fromGateioInstrument(gateioFuturesOrder.getContract(), true);
    String settle = (instrument instanceof FuturesContract) ? instrument.getCounter().getCurrencyCode().toLowerCase() : "usdt";
    return decorateApiCall(
        () ->
            gateioV4Authenticated.createFuturesOrder(
                apiKey, exchange.getNonceFactory(), gateioV4ParamsDigest, null, settle, gateioFuturesOrder))
        .withRateLimiter(rateLimiter(ORDERS_RATE_LIMITER))
        .call();
  }

  public GateioSpotOrderResponse getOrder(String orderId, Instrument instrument) throws IOException {
    return gateioV4Authenticated.getOrder(
        apiKey,
        exchange.getNonceFactory(),
        gateioV4ParamsDigest,
        orderId,
        GateioAdapters.toGateioInstrument(instrument));
  }

  public GateioFuturesOrderResponse getFuturesOrder(String orderId, Instrument instrument) throws IOException {
    String settle = (instrument instanceof FuturesContract) ? instrument.getCounter().getCurrencyCode().toLowerCase() : "usdt";
    return gateioV4Authenticated.getFuturesOrder(
        apiKey,
        exchange.getNonceFactory(),
        gateioV4ParamsDigest,
        null,
        settle,
        orderId);
  }

  public GateioSpotOrderResponse cancelOrderRaw(String orderId, Instrument instrument) throws IOException {
    return gateioV4Authenticated.cancelOrder(
        apiKey,
        exchange.getNonceFactory(),
        gateioV4ParamsDigest,
        orderId,
        GateioAdapters.toGateioInstrument(instrument));
  }

  public GateioFuturesOrderResponse cancelFuturesOrderRaw(String orderId, Instrument instrument) throws IOException {
    String settle = (instrument instanceof FuturesContract) ? instrument.getCounter().getCurrencyCode().toLowerCase() : "usdt";
    return gateioV4Authenticated.cancelFuturesOrder(
        apiKey,
        exchange.getNonceFactory(),
        gateioV4ParamsDigest,
        null,
        settle,
        orderId);
  }

  public GateioSpotOrderResponse amendSpotOrder(String orderId, Instrument instrument, Map<String, Object> request) throws IOException {
    return decorateApiCall(
        () ->
            gateioV4Authenticated.amendOrder(
                apiKey,
                exchange.getNonceFactory(),
                gateioV4ParamsDigest,
                orderId,
                GateioAdapters.toGateioInstrument(instrument),
                request))
        .withRateLimiter(rateLimiter(ORDERS_RATE_LIMITER))
        .call();
  }

  public GateioFuturesOrderResponse amendFuturesOrder(String orderId, Instrument instrument, Map<String, Object> request) throws IOException {
    String settle = instrument.getCounter().getCurrencyCode().toLowerCase();
    return decorateApiCall(
        () ->
            gateioV4Authenticated.amendFuturesOrder(
                apiKey,
                exchange.getNonceFactory(),
                gateioV4ParamsDigest,
                null,
                settle,
                orderId,
                request)).withRateLimiter(rateLimiter(ORDERS_RATE_LIMITER))
        .call();
  }
}
