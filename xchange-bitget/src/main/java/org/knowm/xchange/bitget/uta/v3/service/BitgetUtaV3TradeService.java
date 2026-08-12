package org.knowm.xchange.bitget.uta.v3.service;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.Validate;
import org.knowm.xchange.bitget.BitgetExchange;
import org.knowm.xchange.bitget.uta.v3.BitgetUtaV3Adapters;
import org.knowm.xchange.bitget.uta.v3.common.BitgetUtaV3Category;
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
import org.knowm.xchange.service.trade.params.orders.DefaultQueryOrderParam;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParams;
import org.knowm.xchange.service.trade.params.orders.OrderQueryParams;

/**
 * UTA v3 {@link TradeService}: place/cancel/modify orders, open orders, history, order detail,
 * fills and positions, with cursor-based pagination over the provider's order/fill endpoints.
 */
public class BitgetUtaV3TradeService extends BitgetUtaV3TradeServiceRaw implements TradeService {

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
        fetchAllPages((cursor) -> getUnfilledOrders(null, requestSymbol, null, null, 100, cursor));
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
    Validate.isInstanceOf(DefaultQueryOrderParam.class, orderQueryParams[0]);
    String orderId = ((DefaultQueryOrderParam) orderQueryParams[0]).getOrderId();
    BitgetUtaV3Order dto = getOrderInfo(orderId, null);
    return java.util.Collections.singletonList(toOrder(dto));
  }

  @Override
  public Class getRequiredOrderQueryParamClass() {
    return DefaultQueryOrderParam.class;
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
    BitgetUtaV3Category category = null;
    if (historyParams.getInstrument() != null) {
      category = BitgetUtaV3Adapters.toCategory(historyParams.getInstrument());
    }
    String startTime =
        historyParams.getStartTime() == null
            ? null
            : String.valueOf(historyParams.getStartTime().getTime());
    String endTime =
        historyParams.getEndTime() == null
            ? null
            : String.valueOf(historyParams.getEndTime().getTime());
    Integer limit = historyParams.getLimit();
    final String requestCategory = category == null ? null : category.getWireName();
    final String requestSymbol =
        historyParams.getInstrument() == null
            ? null
            : BitgetUtaV3Adapters.toString(historyParams.getInstrument());
    final String requestStartTime = startTime;
    final String requestEndTime = endTime;
    final int pageSize = limit == null ? 100 : Math.min(limit, 100);
    List<BitgetUtaV3Fill> rows =
        fetchAllPages(
            (cursor) ->
                getFills(
                    requestCategory, null, requestStartTime, requestEndTime, pageSize, cursor),
            limit,
            // the fills endpoint filters by category only, so client-side symbol filtering is
            // required to honor TradeHistoryParamInstrument (PRD CF-451); the filter runs inside
            // the pagination loop so the requested limit counts only rows that survive it
            row -> requestSymbol == null || requestSymbol.equals(row.getSymbol()));
    List<UserTrade> trades = new java.util.ArrayList<>();
    for (BitgetUtaV3Fill row : rows) {
      trades.add(toUserTrade(row));
    }
    return new UserTrades(trades, TradeSortType.SortByID);
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
   * Fetches every cursor page, oldest included, until the provider returns an empty cursor.
   *
   * <p>Guards: aggregation is bounded by {@code maxRows} (counting only rows that pass {@code
   * filter}, when one is supplied) and a provider that repeats the cursor just requested (no
   * progress) raises {@link org.knowm.xchange.exceptions.ExchangeException} instead of looping
   * forever or returning duplicated rows.
   */
  private <T> List<T> fetchAllPages(PageFetcher<T> fetcher) throws IOException {
    return fetchAllPages(fetcher, null, null);
  }

  private <T> List<T> fetchAllPages(
      PageFetcher<T> fetcher, Integer maxRows, java.util.function.Predicate<T> filter)
      throws IOException {
    List<T> rows = new java.util.ArrayList<>();
    String cursor = null;
    while (true) {
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

  /** Fill-history params: optional instrument, time span (at most 30 days), page size. */
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
