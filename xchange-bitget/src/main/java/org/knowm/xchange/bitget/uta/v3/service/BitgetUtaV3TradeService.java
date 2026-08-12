package org.knowm.xchange.bitget.uta.v3.service;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import org.apache.commons.lang3.Validate;
import org.knowm.xchange.bitget.BitgetExchange;
import org.knowm.xchange.bitget.uta.v3.BitgetUtaV3Adapters;
import org.knowm.xchange.bitget.uta.v3.common.BitgetUtaV3Category;
import org.knowm.xchange.bitget.uta.v3.common.BitgetUtaV3EndpointPolicy;
import org.knowm.xchange.bitget.uta.v3.common.BitgetUtaV3CursorPage;
import org.knowm.xchange.bitget.uta.v3.trade.BitgetUtaV3Fill;
import org.knowm.xchange.bitget.uta.v3.trade.BitgetUtaV3Order;
import org.knowm.xchange.bitget.uta.v3.trade.BitgetUtaV3Position;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.OpenPosition;
import org.knowm.xchange.dto.account.OpenPositions;
import org.knowm.xchange.dto.marketdata.Trades.TradeSortType;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.CancelOrderByIdParams;
import org.knowm.xchange.service.trade.params.CancelOrderParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParamCurrencyPair;
import org.knowm.xchange.service.trade.params.TradeHistoryParamInstrument;
import org.knowm.xchange.service.trade.params.TradeHistoryParamLimit;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParamsTimeSpan;
import org.knowm.xchange.service.trade.params.orders.DefaultOpenOrdersParamInstrument;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParams;
import org.knowm.xchange.service.trade.params.orders.OrderQueryParams;

/**
 * UTA v3 {@link TradeService}: place/cancel/modify orders, open orders, history, order detail,
 * fills and positions, with cursor-based pagination over the provider's order/fill endpoints.
 */
public class BitgetUtaV3TradeService extends BitgetUtaV3TradeServiceRaw implements TradeService {

  private static final long NANOS_PER_SECOND = 1_000_000_000L;

  /** Endpoint policy whose per-second limits are enforced client-side during pagination. */
  private static final BitgetUtaV3EndpointPolicy ENDPOINT_POLICY = BitgetUtaV3EndpointPolicy.defaults();

  /** Endpoint paths of the paginated trade endpoints, keyed for the endpoint limiter. */
  private static final String FILLS_ENDPOINT = "/api/v3/trade/fills";
  private static final String UNFILLED_ORDERS_ENDPOINT = "/api/v3/trade/unfilled-orders";

  /** Last request time per endpoint path (monotonic nanos), for spacing requests at 1/rate. */
  private final ConcurrentMap<String, Long> lastRequestAtNanos = new ConcurrentHashMap<>();

  private final BitgetUtaV3InstrumentRegistry instrumentRegistry =
      new BitgetUtaV3InstrumentRegistry(new BitgetUtaV3MarketDataServiceRaw(exchange));

  public BitgetUtaV3TradeService(BitgetExchange exchange) {
    super(exchange);
  }

  @Override
  public String placeMarketOrder(MarketOrder marketOrder) throws IOException {
    return placeOrder(BitgetUtaV3Adapters.toPlaceOrderRequest(marketOrder)).getOrderId();
  }

  @Override
  public String placeLimitOrder(LimitOrder limitOrder) throws IOException {
    return placeOrder(BitgetUtaV3Adapters.toPlaceOrderRequest(limitOrder)).getOrderId();
  }

  @Override
  public boolean cancelOrder(CancelOrderParams orderParams) throws IOException {
    Validate.isInstanceOf(CancelOrderByIdParams.class, orderParams);
    String orderId = ((CancelOrderByIdParams) orderParams).getOrderId();
    cancelOrder(BitgetUtaV3Adapters.toCancelOrderRequest(orderId));
    return true;
  }

  @Override
  public Class[] getRequiredCancelOrderParamClasses() {
    return new Class[] {CancelOrderByIdParams.class};
  }

  @Override
  public OpenOrders getOpenOrders() throws IOException {
    return getOpenOrders(createOpenOrdersParams());
  }

  @Override
  public OpenOrders getOpenOrders(OpenOrdersParams params) throws IOException {
    String symbol = null;
    if (params instanceof DefaultOpenOrdersParamInstrument) {
      Instrument instrument = ((DefaultOpenOrdersParamInstrument) params).getInstrument();
      if (instrument != null) {
        symbol = BitgetUtaV3Adapters.toString(instrument);
      }
    }
    // unfilled-orders returns both spot and futures; paginate through every page so that
    // nothing beyond the first 100 rows is silently dropped
    final String requestSymbol = symbol;
    List<BitgetUtaV3Order> rows =
        fetchAllPages(
            UNFILLED_ORDERS_ENDPOINT,
            (cursor) -> getUnfilledOrders(null, requestSymbol, null, null, 100, cursor));
    List<LimitOrder> orders = new java.util.ArrayList<>();
    for (BitgetUtaV3Order row : rows) {
      Order order = toOrder(row);
      if (order instanceof LimitOrder && params.accept(order)) {
        orders.add((LimitOrder) order);
      }
    }
    return new OpenOrders(orders);
  }

  @Override
  public OpenOrdersParams createOpenOrdersParams() {
    return new DefaultOpenOrdersParamInstrument();
  }

  @Override
  public Collection<Order> getOrder(OrderQueryParams... orderQueryParams) throws IOException {
    Validate.validState(orderQueryParams.length == 1);
    Validate.isInstanceOf(OrderQueryParams.class, orderQueryParams[0]);
    String orderId = orderQueryParams[0].getOrderId();
    // BitgetUtaV3OrderQueryParams additionally carries the placement clientOid (idempotency key /
    // user reference), which the endpoint accepts in place of orderId; plain OrderQueryParams
    // implementations keep the pre-existing orderId-only behavior.
    String clientOid =
        orderQueryParams[0] instanceof BitgetUtaV3OrderQueryParams
            ? ((BitgetUtaV3OrderQueryParams) orderQueryParams[0]).getClientOid()
            : null;
    BitgetUtaV3Order dto = getOrderInfo(orderId, clientOid);
    return java.util.Collections.singletonList(toOrder(dto));
  }

  @Override
  public Class getRequiredOrderQueryParamClass() {
    return BitgetUtaV3OrderQueryParams.class;
  }

  @Override
  public UserTrades getTradeHistory(TradeHistoryParams params) throws IOException {
    // Read each supported TradeHistoryParam* interface independently, as the classic
    // BitgetTradeService does, so standard core implementations (for example
    // DefaultTradeHistoryParamInstrument) are honored instead of being replaced by empty
    // defaults; null params yield account-wide defaults.
    BitgetUtaV3TradeHistoryParams historyParams = new BitgetUtaV3TradeHistoryParams();
    if (params instanceof TradeHistoryParamInstrument) {
      historyParams.setInstrument(((TradeHistoryParamInstrument) params).getInstrument());
    } else if (params instanceof TradeHistoryParamCurrencyPair) {
      historyParams.setInstrument(((TradeHistoryParamCurrencyPair) params).getCurrencyPair());
    }
    if (params instanceof TradeHistoryParamsTimeSpan) {
      TradeHistoryParamsTimeSpan span = (TradeHistoryParamsTimeSpan) params;
      historyParams.setStartTime(span.getStartTime());
      historyParams.setEndTime(span.getEndTime());
    }
    if (params instanceof TradeHistoryParamLimit) {
      historyParams.setLimit(((TradeHistoryParamLimit) params).getLimit());
    }
    Long startMillis =
        historyParams.getStartTime() == null ? null : historyParams.getStartTime().getTime();
    Long endMillis =
        historyParams.getEndTime() == null ? null : historyParams.getEndTime().getTime();
    Integer limit = historyParams.getLimit();
    // spot and margin fills share the CurrencyPair identity, so an instrument-scoped history must
    // query both categories or every margin execution is silently dropped; the per-category
    // budget is the full requested limit and the merged aggregate is trimmed to the limit by fill
    // time so the most recent fills win across categories
    final List<BitgetUtaV3Category> requestCategories =
        BitgetUtaV3Adapters.toHistoryCategories(historyParams.getInstrument());
    final String requestSymbol =
        historyParams.getInstrument() == null
            ? null
            : BitgetUtaV3Adapters.toString(historyParams.getInstrument());
    final int pageSize = limit == null ? 100 : Math.min(limit, 100);
    // the fills endpoint permits at most a 30-day range per query (last 90 days), so spans beyond
    // that must be split into compliant windows; the newest window is fetched first so a requested
    // limit is consumed by the most recent fills (PRD CF-451)
    final long windowMillis = 30L * 24 * 60 * 60 * 1000;
    // an end time omitted by the caller means "now" to the provider; when the span from an old
    // start to that implicit now exceeds the 30-day window, resolve the end up front so the span
    // is split into compliant windows instead of sending an unbounded range the endpoint rejects
    if (startMillis != null && endMillis == null) {
      long now = System.currentTimeMillis();
      if (now - startMillis > windowMillis) {
        endMillis = now;
      }
    }
    List<BitgetUtaV3Fill> rows = new java.util.ArrayList<>();
    for (BitgetUtaV3Category requestCategory : requestCategories) {
      rows.addAll(
          fetchFills(
              requestCategory == null ? null : requestCategory.getWireName(),
              startMillis,
              endMillis,
              limit,
              requestSymbol,
              pageSize,
              windowMillis));
    }
    if (requestCategories.size() > 1) {
      // newest first across categories (stable: equal fill times keep per-category provider
      // order): without this, an older spot execution could precede a newer margin execution;
      // with a limit, it also selects the most recent executions regardless of category
      rows.sort(
          java.util.Comparator.comparingLong((BitgetUtaV3Fill row) -> fillTimeMillis(row))
              .reversed());
    }
    if (limit != null && rows.size() > limit) {
      rows = new java.util.ArrayList<>(rows.subList(0, limit));
    }
    List<UserTrade> trades = new java.util.ArrayList<>();
    for (BitgetUtaV3Fill row : rows) {
      trades.add(toUserTrade(row));
    }
    return new UserTrades(trades, TradeSortType.SortByID);
  }

  /**
   * Fetches every fill in one category over the requested span, splitting spans beyond the
   * provider's 30-day window into compliant windows, newest first. {@code limit} bounds the rows
   * returned for this category (counting only rows that survive the symbol filter); the symbol
   * filter runs inside the pagination loop so the limit counts only matching rows (PRD CF-451).
   */
  private List<BitgetUtaV3Fill> fetchFills(
      String requestCategory,
      Long startMillis,
      Long endMillis,
      Integer limit,
      String requestSymbol,
      int pageSize,
      long windowMillis)
      throws IOException {
    final String startTime = startMillis == null ? null : String.valueOf(startMillis);
    final String endTime = endMillis == null ? null : String.valueOf(endMillis);
    List<BitgetUtaV3Fill> rows = new java.util.ArrayList<>();
    // the fills endpoint filters by category only, so client-side symbol filtering is required to
    // honor TradeHistoryParamInstrument (PRD CF-451); the filter runs inside the pagination loop
    // so the requested limit counts only rows that survive it
    java.util.function.Predicate<BitgetUtaV3Fill> symbolFilter =
        row -> requestSymbol == null || requestSymbol.equals(row.getSymbol());
    if (startMillis != null && endMillis != null && endMillis - startMillis > windowMillis) {
      long windowEnd = endMillis;
      while (windowEnd >= startMillis) {
        long windowStart = Math.max(startMillis, windowEnd - windowMillis);
        final String windowStartParam = String.valueOf(windowStart);
        final String windowEndParam = String.valueOf(windowEnd);
        Integer remaining = limit == null ? null : limit - rows.size();
        rows.addAll(
            fetchAllPages(
                FILLS_ENDPOINT,
                (cursor) ->
                    getFills(
                        requestCategory,
                        null,
                        windowStartParam,
                        windowEndParam,
                        pageSize,
                        cursor),
                remaining,
                symbolFilter));
        if (remaining != null && rows.size() >= limit) {
          break;
        }
        // the boundary fill belongs to the newer window (inclusive start); the older window's end
        // is advanced one millisecond so inclusive bounds cannot surface it twice. The loop keeps
        // going while windowEnd >= startMillis so a final single-millisecond window [startMillis,
        // startMillis] is still queried when the remainder is exactly one millisecond; the step
        // then moves windowEnd below startMillis and the loop exits.
        windowEnd = windowStart - 1;
      }
    } else {
      rows.addAll(
          fetchAllPages(
              FILLS_ENDPOINT,
              (cursor) -> getFills(requestCategory, null, startTime, endTime, pageSize, cursor),
              limit,
              symbolFilter));
    }
    return rows;
  }

  /** Provider fill timestamp in epoch millis, {@code 0} when absent, for cross-category merge. */
  private static long fillTimeMillis(BitgetUtaV3Fill row) {
    String createdTime = row.getCreatedTime();
    if (createdTime == null) {
      return 0L;
    }
    try {
      return Long.parseLong(createdTime);
    } catch (NumberFormatException e) {
      return 0L;
    }
  }

  @Override
  public TradeHistoryParams createTradeHistoryParams() {
    return new BitgetUtaV3TradeHistoryParams();
  }

  @Override
  public OpenPositions getOpenPositions() throws IOException {
    List<BitgetUtaV3Position> positions = getCurrentPositions(null, null, null);
    List<OpenPosition> open = new java.util.ArrayList<>();
    for (BitgetUtaV3Position position : positions) {
      OpenPosition openPosition =
          BitgetUtaV3Adapters.toOpenPosition(
              position, resolveInstrument(position.getCategory(), position.getSymbol()));
      if (openPosition != null) {
        open.add(openPosition);
      }
    }
    return new OpenPositions(open);
  }

  /**
   * Fetches every cursor page, oldest included, until the provider returns an empty cursor, spacing
   * each request at {@code endpointPath}'s policy rate.
   *
   * <p>Guards: aggregation is bounded by {@code maxRows} (counting only rows that pass {@code
   * filter}, when one is supplied) and a provider that repeats the cursor just requested (no
   * progress) raises {@link org.knowm.xchange.exceptions.ExchangeException} instead of looping
   * forever or returning duplicated rows.
   */
  private <T> List<T> fetchAllPages(String endpointPath, PageFetcher<T> fetcher) throws IOException {
    return fetchAllPages(endpointPath, fetcher, null, null);
  }

  private <T> List<T> fetchAllPages(
      String endpointPath,
      PageFetcher<T> fetcher,
      Integer maxRows,
      java.util.function.Predicate<T> filter)
      throws IOException {
    List<T> rows = new java.util.ArrayList<>();
    String cursor = null;
    while (true) {
      awaitEndpointLimit(endpointPath);
      BitgetUtaV3CursorPage<T> page = fetcher.fetch(cursor);
      String nextCursor = page == null ? null : page.getCursor();
      // no-progress guard, checked against the cursor just requested: a provider that echoes it
      // back would serve the same page again. Reject before accepting the page so a duplicate
      // can never be appended (and never counted toward maxRows).
      if (nextCursor != null && nextCursor.equals(cursor)) {
        throw new org.knowm.xchange.exceptions.ExchangeException(
            "Provider cursor did not advance between pages: " + nextCursor);
      }
      if (page != null && page.getList() != null) {
        for (T row : page.getList()) {
          if (filter == null || filter.test(row)) {
            rows.add(row);
          }
        }
      }
      if (maxRows != null && rows.size() >= maxRows) {
        // the provider returns full pages, so the final page can overshoot the requested limit;
        // trim the aggregate so callers can rely on TradeHistoryParamLimit (PRD CF-451)
        return new java.util.ArrayList<>(rows.subList(0, maxRows));
      }
      if (nextCursor == null || nextCursor.isEmpty()) {
        return rows;
      }
      cursor = nextCursor;
    }
  }

  /**
   * Enforces the endpoint policy client-side: spaces consecutive requests to {@code endpointPath}
   * at the policy's per-second rate (50 ms between requests at 20/s) so a low-latency pagination
   * run cannot trip the provider's limiter partway through. The first request for a path passes
   * immediately; concurrent callers queue on the same per-path slot.
   */
  private void awaitEndpointLimit(String endpointPath) throws IOException {
    long intervalNanos = NANOS_PER_SECOND / ENDPOINT_POLICY.limitFor(endpointPath).getPerSecond();
    long now = System.nanoTime();
    long next =
        lastRequestAtNanos.compute(
            endpointPath, (path, prev) -> prev == null ? now : Math.max(now, prev + intervalNanos));
    long delayNanos = next - now;
    if (delayNanos > 0) {
      try {
        Thread.sleep(delayNanos / 1_000_000L, (int) (delayNanos % 1_000_000L));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException(
            "Interrupted while waiting on the Bitget v3 rate limit for " + endpointPath, e);
      }
    }
  }

  @FunctionalInterface
  private interface PageFetcher<T> {
    BitgetUtaV3CursorPage<T> fetch(String cursor) throws IOException;
  }

  private Order toOrder(BitgetUtaV3Order dto) throws IOException {
    return BitgetUtaV3Adapters.toOrder(dto, resolveInstrument(dto.getCategory(), dto.getSymbol()));
  }

  private UserTrade toUserTrade(BitgetUtaV3Fill dto) throws IOException {
    return BitgetUtaV3Adapters.toUserTrade(
        dto, resolveInstrument(dto.getCategory(), dto.getSymbol()));
  }

  private Instrument resolveInstrument(String category, String symbol) throws IOException {
    if (category == null || symbol == null) {
      return null;
    }
    BitgetUtaV3Category parsed;
    try {
      parsed = BitgetUtaV3Category.fromWireName(category);
    } catch (IllegalArgumentException e) {
      return null;
    }
    return instrumentRegistry.resolve(parsed, symbol);
  }

  /**
   * Fill-history params: optional instrument, time span (split into ≤30-day windows by {@link
   * #getTradeHistory(TradeHistoryParams)} per the fills endpoint limit), page size.
   */
  public static class BitgetUtaV3TradeHistoryParams
      implements TradeHistoryParams,
          TradeHistoryParamInstrument,
          TradeHistoryParamsTimeSpan,
          TradeHistoryParamLimit {

    private Instrument instrument;
    private Date startTime;
    private Date endTime;
    private Integer limit;

    @Override
    public Instrument getInstrument() {
      return instrument;
    }

    @Override
    public void setInstrument(Instrument instrument) {
      this.instrument = instrument;
    }

    @Override
    public Date getStartTime() {
      return startTime;
    }

    @Override
    public void setStartTime(Date startTime) {
      this.startTime = startTime;
    }

    @Override
    public Date getEndTime() {
      return endTime;
    }

    @Override
    public void setEndTime(Date endTime) {
      this.endTime = endTime;
    }

    @Override
    public Integer getLimit() {
      return limit;
    }

    @Override
    public void setLimit(Integer limit) {
      this.limit = limit;
    }
  }
}
