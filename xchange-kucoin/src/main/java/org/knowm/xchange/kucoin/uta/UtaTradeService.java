package org.knowm.xchange.kucoin.uta;

import static org.knowm.xchange.kucoin.uta.UtaResilience.UTA_PRIVATE_REST_ENDPOINT_RATE_LIMITER;
import static org.knowm.xchange.kucoin.uta.service.UtaConstants.KEY_VERSION;
import static org.knowm.xchange.kucoin.uta.service.UtaExceptionClassifier.callOrThrow;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.kucoin.KucoinExchange;
import org.knowm.xchange.kucoin.uta.dto.UtaAmendOrderRequest;
import org.knowm.xchange.kucoin.uta.dto.UtaBatchCancelRequest;
import org.knowm.xchange.kucoin.uta.dto.UtaBatchCancelResult;
import org.knowm.xchange.kucoin.uta.dto.UtaExecution;
import org.knowm.xchange.kucoin.uta.dto.UtaExecutionHistory;
import org.knowm.xchange.kucoin.uta.dto.UtaOrder;
import org.knowm.xchange.kucoin.uta.dto.UtaOrderCancelRequest;
import org.knowm.xchange.kucoin.uta.dto.UtaOrderHistory;
import org.knowm.xchange.kucoin.uta.dto.UtaOrderPlaceRequest;
import org.knowm.xchange.kucoin.uta.dto.UtaOrderResult;
import org.knowm.xchange.kucoin.uta.dto.UtaMarginMode;
import org.knowm.xchange.kucoin.uta.dto.UtaPosition;
import org.knowm.xchange.kucoin.uta.dto.UtaPositionHistory;
import org.knowm.xchange.kucoin.uta.dto.UtaTradeType;
import org.knowm.xchange.kucoin.uta.service.UtaApiException;
import org.knowm.xchange.kucoin.uta.service.UtaExceptionClassifier;
import org.knowm.xchange.kucoin.uta.service.UtaApiException.RetryClassification;
import org.knowm.xchange.kucoin.uta.service.UtaPositionAPI;
import org.knowm.xchange.kucoin.uta.service.UtaTradeAPI;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.CancelOrderByIdParams;
import org.knowm.xchange.service.trade.params.CancelOrderParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParamCurrencyPair;
import org.knowm.xchange.service.trade.params.TradeHistoryParamNextPageCursor;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParamsTimeSpan;
import org.knowm.xchange.service.trade.params.orders.DefaultOpenOrdersParamCurrencyPair;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParamCurrencyPair;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParams;

/**
 * UTA unified trading service.
 *
 * <p>Implements the no-blind-replay contract of CF-449: a placement whose transmission outcome is
 * unknown is reconciled by {@code clientOid} and never automatically resubmitted. Validation of
 * {@code clientOid} happens before transmission.
 */
public class UtaTradeService extends UtaBaseService implements TradeService {

  private static final int MAX_PAGES = 100;
  private static final int PAGE_SIZE = 200;

  private final UtaTradeAPI tradeApi;
  private final UtaPositionAPI positionApi;

  public UtaTradeService(KucoinExchange exchange, ResilienceRegistries registries) {
    super(exchange, registries);
    this.tradeApi = service(UtaTradeAPI.class);
    this.positionApi = service(UtaPositionAPI.class);
  }

  // ---- raw API ---------------------------------------------------------------

  /**
   * Transmits a UTA order placement.
   *
   * <p>Placement is non-idempotent: no automatic retry is applied, and transport failures after
   * transmission are reclassified to {@link RetryClassification#UNKNOWN_OUTCOME} so callers must
   * reconcile instead of resubmitting.
   */
  public UtaOrderResult placeOrder(UtaOrderPlaceRequest request) throws IOException {
    checkAuthenticated();
    UtaClientOrderId.validate(request.getClientOid(), request.getTradeType());
    try {
      return UtaExceptionClassifier.classifyingExceptions(
          () ->
              decorateApiCall(
                      () ->
                          tradeApi.placeOrder(
                              apiKey,
                              digest,
                              nonceFactory,
                              encryptedPassphrase,
                              KEY_VERSION,
                              request))
                  .withRateLimiter(rateLimiter(UTA_PRIVATE_REST_ENDPOINT_RATE_LIMITER))
                  .call(),
          UtaDomains.TRADE,
          "POST /api/ua/v1/unified/order/place",
          request.getClientOid(),
          null);
    } catch (UtaApiException e) {
      throw reclassifyPlacement(e, request.getClientOid());
    }
  }

  /**
   * Placement-safe order submission: reconciles unknown outcomes by client order id and never
   * replays a transmitted placement.
   *
   * @return the placement result; when reconciliation proves the order exists, the result carries
   *     the provider order id
   * @throws UtaApiException with {@link RetryClassification#UNKNOWN_OUTCOME} when the order's
   *     existence cannot be proven
   */
  public UtaOrderResult placeOrderSafe(
      UtaOrderPlaceRequest request, Instrument instrument) throws IOException {
    checkAuthenticated();
    UtaClientOrderId.validate(request.getClientOid(), request.getTradeType());
    try {
      return placeOrder(request);
    } catch (UtaApiException e) {
      if (e.getRetryClassification() != RetryClassification.UNKNOWN_OUTCOME) {
        throw e;
      }
      // Reconcile by client order id; never resubmit.
      String symbol = exchange.getUtaProviderSymbol(instrument);
      UtaOrder existing = reconcileByClientOid(request.getTradeType(), symbol, request.getClientOid());
      if (existing != null && existing.getOrderId() != null) {
        UtaOrderResult reconciled = new UtaOrderResult();
        reconciled.setTradeType(request.getTradeType());
        reconciled.setOrderId(existing.getOrderId());
        reconciled.setClientOid(request.getClientOid());
        return reconciled;
      }
      throw e;
    }
  }

  private static UtaApiException reclassifyPlacement(UtaApiException e, String clientOid) {
    // Envelope-level duplicate: 116151 proves a prior order with this clientOid exists, so the
    // outcome is resolvable by reconciliation (the caller must query by clientOid, never resubmit).
    if ("116151".equals(e.getCode())) {
      return new UtaApiException(
          e.getMessage(),
          e.getCode(),
          e.getMode(),
          e.getDomain(),
          e.getEndpoint(),
          e.getHttpStatus(),
          clientOid,
          null,
          RetryClassification.UNKNOWN_OUTCOME);
    }
    // Transport-origin failures (no provider code, no HTTP status) or server-side errors after
    // the request reached the engine mean the outcome is unknown: never retryable, reconcile only.
    if ((e.getCode() == null && e.getHttpStatus() == null)
        || (e.getHttpStatus() != null && e.getHttpStatus() >= 500)) {
      return new UtaApiException(
          e.getMessage(),
          null,
          e.getMode(),
          e.getDomain(),
          e.getEndpoint(),
          e.getHttpStatus(),
          clientOid,
          null,
          RetryClassification.UNKNOWN_OUTCOME);
    }
    return e;
  }

  private UtaOrder reconcileByClientOid(String tradeType, String symbol, String clientOid)
      throws IOException {
    try {
      UtaOrder order = getOrderDetail(tradeType, symbol, null, clientOid);
      return order;
    } catch (UtaApiException reconcileFailure) {
      String code = reconcileFailure.getCode();
      if ("116052".equals(code) || "116101".equals(code) || "116102".equals(code)) {
        return null;
      }
      throw reconcileFailure;
    }
  }

  public UtaOrderResult cancelOrder(UtaOrderCancelRequest request) throws IOException {
    checkAuthenticated();
    return UtaExceptionClassifier.classifyingExceptions(
        () ->
            decorateApiCall(
                    () ->
                        tradeApi.cancelOrder(
                            apiKey,
                            digest,
                            nonceFactory,
                            encryptedPassphrase,
                            KEY_VERSION,
                            request))
                .withRetry(retry("utaCancelOrder"))
                .withRateLimiter(rateLimiter(UTA_PRIVATE_REST_ENDPOINT_RATE_LIMITER))
                .call(),
        UtaDomains.TRADE,
        "POST /api/ua/v1/unified/order/cancel",
        request.getClientOid(),
        request.getOrderId());
  }

  public UtaOrder getOrderDetail(
      String tradeType, String symbol, String orderId, String clientOid) throws IOException {
    checkAuthenticated();
    return UtaExceptionClassifier.classifyingExceptions(
        () ->
            decorateApiCall(
                    () ->
                        tradeApi.getOrderDetail(
                            apiKey,
                            digest,
                            nonceFactory,
                            encryptedPassphrase,
                            KEY_VERSION,
                            tradeType,
                            symbol,
                            orderId,
                            clientOid))
                .withRetry(retry("utaOrderDetail"))
                .withRateLimiter(rateLimiter(UTA_PRIVATE_REST_ENDPOINT_RATE_LIMITER))
                .call(),
        UtaDomains.TRADE,
        "GET /api/ua/v1/unified/order/detail",
        clientOid,
        orderId);
  }

  public UtaOrderHistory getOrderHistory(
      String tradeType,
      String symbol,
      String side,
      String orderFilter,
      Long startAt,
      Long endAt,
      Long lastId,
      Integer pageSize)
      throws IOException {
    checkAuthenticated();
    return callOrThrow(
        () ->
            decorateApiCall(
                    () ->
                        tradeApi.getOrderHistory(
                            apiKey,
                            digest,
                            nonceFactory,
                            encryptedPassphrase,
                            KEY_VERSION,
                            tradeType,
                            symbol,
                            side,
                            orderFilter,
                            startAt,
                            endAt,
                            lastId,
                            pageSize))
                .withRetry(retry("utaOrderHistory"))
                .withRateLimiter(rateLimiter(UTA_PRIVATE_REST_ENDPOINT_RATE_LIMITER))
                .call(),
        UtaDomains.TRADE,
        "GET /api/ua/v1/unified/order/history");
  }

  /**
   * Bounded cursor walk over order history with no-progress detection.
   *
   * @param maxPages upper bound on pages fetched; callers must not silently truncate catalogs
   */
  public List<UtaOrder> getAllOrderHistory(
      String tradeType,
      String symbol,
      Long startAt,
      Long endAt,
      int maxPages)
      throws IOException {
    checkAuthenticated();
    java.util.List<UtaOrder> collected = new java.util.ArrayList<>();
    Long lastId = null;
    for (int page = 0; page < maxPages; page++) {
      UtaOrderHistory history =
          getOrderHistory(tradeType, symbol, null, null, startAt, endAt, lastId, PAGE_SIZE);
      if (history == null || history.getItems() == null || history.getItems().isEmpty()) {
        break;
      }
      collected.addAll(history.getItems());
      Long next = history.getLastId();
      if (next == null || next.equals(lastId)) {
        break; // no-progress continuation detected
      }
      lastId = next;
    }
    return collected;
  }

  public UtaExecutionHistory getExecutions(
      String tradeType,
      String symbol,
      String orderId,
      String side,
      Long startAt,
      Long endAt,
      Long lastId,
      Integer pageSize)
      throws IOException {
    checkAuthenticated();
    return callOrThrow(
        () ->
            decorateApiCall(
                    () ->
                        tradeApi.getExecutions(
                            apiKey,
                            digest,
                            nonceFactory,
                            encryptedPassphrase,
                            KEY_VERSION,
                            tradeType,
                            symbol,
                            orderId,
                            side,
                            startAt,
                            endAt,
                            lastId,
                            pageSize))
                .withRetry(retry("utaExecutions"))
                .withRateLimiter(rateLimiter(UTA_PRIVATE_REST_ENDPOINT_RATE_LIMITER))
                .call(),
        UtaDomains.TRADE,
        "GET /api/ua/v1/unified/order/execution");
  }

  public List<UtaPosition> getOpenPositionsRaw(String symbol) throws IOException {
    checkAuthenticated();
    return callOrThrow(
        () ->
            decorateApiCall(
                    () ->
                        positionApi.getOpenPositions(
                            apiKey,
                            digest,
                            nonceFactory,
                            encryptedPassphrase,
                            KEY_VERSION,
                            symbol,
                            1,
                            PAGE_SIZE))
                .withRetry(retry("utaOpenPositions"))
                .withRateLimiter(rateLimiter(UTA_PRIVATE_REST_ENDPOINT_RATE_LIMITER))
                .call(),
        UtaDomains.POSITION,
        "GET /api/ua/v1/unified/position/open-list");
  }

  /** Current futures margin mode (CROSS/ISOLATED) for one or all symbols. */
  public UtaMarginMode getUtaMarginMode(String symbol) throws IOException {
    checkAuthenticated();
    return callOrThrow(
        () ->
            decorateApiCall(
                    () ->
                        positionApi.getMarginMode(
                            apiKey,
                            digest,
                            nonceFactory,
                            encryptedPassphrase,
                            KEY_VERSION,
                            symbol))
                .withRetry(retry("utaMarginMode"))
                .withRateLimiter(rateLimiter(UTA_PRIVATE_REST_ENDPOINT_RATE_LIMITER))
                .call(),
        UtaDomains.POSITION,
        "GET /api/ua/v1/unified/position/margin-mode");
  }

  /** Closed-position history; cursor paginated, each query bounded to 7x24 hours. */
  public UtaPositionHistory getPositionHistory(
      String symbol, Long startAt, Long endAt, Long lastId, Integer pageSize)
      throws IOException {
    checkAuthenticated();
    return callOrThrow(
        () ->
            decorateApiCall(
                    () ->
                        positionApi.getPositionHistory(
                            apiKey,
                            digest,
                            nonceFactory,
                            encryptedPassphrase,
                            KEY_VERSION,
                            symbol,
                            startAt,
                            endAt,
                            lastId,
                            pageSize))
                .withRetry(retry("utaPositionHistory"))
                .withRateLimiter(rateLimiter(UTA_PRIVATE_REST_ENDPOINT_RATE_LIMITER))
                .call(),
        UtaDomains.POSITION,
        "GET /api/ua/v1/position/history");
  }

  /**
   * Batch cancellation with per-item outcomes; partial failures are preserved in the returned
   * items and never flattened into a single error.
   */
  public UtaBatchCancelResult batchCancel(UtaBatchCancelRequest request) throws IOException {
    checkAuthenticated();
    return UtaExceptionClassifier.classifyingExceptions(
        () ->
            decorateApiCall(
                    () ->
                        tradeApi.batchCancelOrders(
                            apiKey,
                            digest,
                            nonceFactory,
                            encryptedPassphrase,
                            KEY_VERSION,
                            request))
                .withRetry(retry("utaBatchCancel"))
                .withRateLimiter(rateLimiter(UTA_PRIVATE_REST_ENDPOINT_RATE_LIMITER))
                .call(),
        UtaDomains.TRADE,
        "POST /api/ua/v1/unified/order/cancel-batch");
  }

  /** Amends an existing order (currently futures only). */
  public UtaOrderResult amendOrder(UtaAmendOrderRequest request) throws IOException {
    checkAuthenticated();
    return UtaExceptionClassifier.classifyingExceptions(
        () ->
            decorateApiCall(
                    () ->
                        tradeApi.amendOrder(
                            apiKey,
                            digest,
                            nonceFactory,
                            encryptedPassphrase,
                            KEY_VERSION,
                            request))
                .withRetry(retry("utaAmendOrder"))
                .withRateLimiter(rateLimiter(UTA_PRIVATE_REST_ENDPOINT_RATE_LIMITER))
                .call(),
        UtaDomains.TRADE,
        "POST /api/ua/v1/unified/order/amend",
        request.getClientOid(),
        request.getOrderId());
  }

  // ---- high-level XChange API ------------------------------------------------

  @Override
  public String placeLimitOrder(LimitOrder limitOrder) throws IOException {
    return placeOrderSafe(adaptOrder(limitOrder), limitOrder.getInstrument()).getOrderId();
  }

  @Override
  public String placeMarketOrder(MarketOrder marketOrder) throws IOException {
    return placeOrderSafe(adaptOrder(marketOrder), marketOrder.getInstrument()).getOrderId();
  }

  private UtaOrderPlaceRequest adaptOrder(Order order) {
    String tradeType = UtaTradeTypes.of(order.getInstrument());
    String symbol = exchange.getUtaProviderSymbol(order.getInstrument());
    String clientOid =
        order.getUserReference() != null && !order.getUserReference().isEmpty()
            ? order.getUserReference()
            : UUID.randomUUID().toString().replace("-", "");
    String side = order.getType() == Order.OrderType.ASK ? "SELL" : "BUY";

    UtaOrderPlaceRequest.UtaOrderPlaceRequestBuilder builder =
        UtaOrderPlaceRequest.builder()
            .tradeType(tradeType)
            .symbol(symbol)
            .side(side)
            .clientOid(clientOid);

    if (order instanceof LimitOrder) {
      LimitOrder limit = (LimitOrder) order;
      builder
          .orderType("LIMIT")
          .size(UtaOrderPlaceRequest.toWire(limit.getOriginalAmount()))
          .sizeUnit("BASECCY")
          .price(UtaOrderPlaceRequest.toWire(limit.getLimitPrice()))
          .timeInForce("GTC");
    } else {
      MarketOrder market = (MarketOrder) order;
      builder
          .orderType("MARKET")
          .size(UtaOrderPlaceRequest.toWire(market.getOriginalAmount()))
          .sizeUnit("BASECCY");
    }
    return builder.build();
  }


  @Override
  public boolean cancelOrder(String orderId) throws IOException {
    UtaOrderResult result = cancelOrderByProviderId(orderId);
    return result != null && result.getOrderId() != null;
  }

  private UtaOrderResult cancelOrderByProviderId(String orderId) throws IOException {
    checkAuthenticated();
    // Provider requires tradeType and symbol; resolve through order detail when unknown.
    UtaOrder order = null;
    for (UtaTradeType tradeType : UtaTradeType.values()) {
      try {
        order = getOrderDetail(tradeType.name(), null, orderId, null);
        if (order != null) {
          break;
        }
      } catch (UtaApiException e) {
        if ("116052".equals(e.getCode()) || "116101".equals(e.getCode())) {
          continue;
        }
        throw e;
      }
    }
    if (order == null || order.getSymbol() == null) {
      throw new UtaApiException(
          "Cannot cancel order " + orderId + ": trade type and symbol are required by the provider",
          null,
          org.knowm.xchange.kucoin.KucoinApiMode.UTA,
          UtaDomains.TRADE,
          "POST /api/ua/v1/unified/order/cancel",
          null,
          null,
          orderId,
          RetryClassification.NON_RETRYABLE);
    }
    String tradeType =
        order.getSymbol().contains("-") ? UtaTradeType.SPOT.name() : UtaTradeType.FUTURES.name();
    return cancelOrder(
        UtaOrderCancelRequest.builder()
            .tradeType(tradeType)
            .symbol(order.getSymbol())
            .orderId(orderId)
            .build());
  }

  @Override
  public boolean cancelOrder(CancelOrderParams genericParams) throws IOException {
    if (!(genericParams instanceof CancelOrderByIdParams)) {
      throw new IllegalArgumentException(
          "Only order id parameters are currently supported for UTA cancellation");
    }
    return cancelOrder(((CancelOrderByIdParams) genericParams).getOrderId());
  }

  @Override
  public OpenOrders getOpenOrders() throws IOException {
    return convertOpenOrders(null);
  }

  @Override
  public OpenOrders getOpenOrders(OpenOrdersParams params) throws IOException {
    String symbol = null;
    if (params instanceof OpenOrdersParamCurrencyPair) {
      symbol =
          exchange.getUtaProviderSymbol(
              ((OpenOrdersParamCurrencyPair) params).getCurrencyPair());
    }
    return convertOpenOrders(symbol);
  }

  private OpenOrders convertOpenOrders(String symbol) throws IOException {
    List<UtaOrder> history =
        getAllOrderHistory(UtaTradeType.SPOT.name(), symbol, null, null, MAX_PAGES);
    List<UtaOrder> open =
        history.stream()
            .filter(o -> o.getStatus() != null && (o.getStatus() == 2 || o.getStatus() == 4))
            .collect(Collectors.toList());
    return new OpenOrders(
        open.stream()
            .map(this::adaptOrderDto)
            .filter(o -> o instanceof LimitOrder)
            .map(o -> (LimitOrder) o)
            .collect(Collectors.toList()),
        open.stream()
            .map(this::adaptOrderDto)
            .filter(o -> !(o instanceof LimitOrder))
            .collect(Collectors.toList()));
  }

  @Override
  public UserTrades getTradeHistory(TradeHistoryParams params) throws IOException {
    String symbol = null;
    Long startAt = null;
    Long endAt = null;
    Long lastId = null;
    String side = null;

    if (params instanceof TradeHistoryParamCurrencyPair) {
      symbol =
          exchange.getUtaProviderSymbol(
              ((TradeHistoryParamCurrencyPair) params).getCurrencyPair());
    }
    if (params instanceof TradeHistoryParamsTimeSpan) {
      TradeHistoryParamsTimeSpan span = (TradeHistoryParamsTimeSpan) params;
      if (span.getStartTime() != null) {
        startAt = span.getStartTime().getTime();
      }
      if (span.getEndTime() != null) {
        endAt = span.getEndTime().getTime();
      }
    }
    if (params instanceof TradeHistoryParamNextPageCursor) {
      String cursor = ((TradeHistoryParamNextPageCursor) params).getNextPageCursor();
      if (cursor != null && !cursor.isEmpty()) {
        try {
          lastId = Long.parseLong(cursor);
        } catch (NumberFormatException e) {
          throw new IllegalArgumentException("Invalid UTA history cursor: " + cursor, e);
        }
      }
    }
    if (params instanceof UtaTradeHistoryParams) {
      side = ((UtaTradeHistoryParams) params).getSide();
    }

    UtaExecutionHistory history =
        getExecutions(UtaTradeType.SPOT.name(), symbol, null, side, startAt, endAt, lastId, PAGE_SIZE);
    List<UtaExecution> items =
        history == null || history.getItems() == null
            ? Collections.emptyList()
            : history.getItems();
    String nextCursor = history != null && history.getLastId() != null
        ? String.valueOf(history.getLastId())
        : null;
    List<UserTrade> trades =
        items.stream().map(UtaAdapters::adaptUserTrade).collect(Collectors.toList());
    return new UserTrades(
        trades, 0, org.knowm.xchange.dto.marketdata.Trades.TradeSortType.SortByTimestamp, nextCursor);
  }

  private Order adaptOrderDto(UtaOrder order) {
    return UtaAdapters.adaptOrder(order);
  }

  @Override
  public org.knowm.xchange.dto.account.OpenPositions getOpenPositions() throws IOException {
    List<UtaPosition> positions = getOpenPositionsRaw(null);
    return new org.knowm.xchange.dto.account.OpenPositions(
        positions.stream().map(UtaAdapters::adaptPosition).collect(Collectors.toList()));
  }

  @Override
  public OpenOrdersParamCurrencyPair createOpenOrdersParams() {
    return new DefaultOpenOrdersParamCurrencyPair();
  }

  @Override
  public TradeHistoryParams createTradeHistoryParams() {
    return new UtaTradeHistoryParams();
  }


  @Override
  public void verifyOrder(LimitOrder limitOrder) {
    UtaClientOrderId.validate(
        limitOrder.getUserReference(), UtaTradeTypes.of(limitOrder.getInstrument()));
    if (limitOrder.getLimitPrice() == null || limitOrder.getOriginalAmount() == null) {
      throw new IllegalArgumentException("Limit price and amount are required for UTA limit orders");
    }
  }
}
