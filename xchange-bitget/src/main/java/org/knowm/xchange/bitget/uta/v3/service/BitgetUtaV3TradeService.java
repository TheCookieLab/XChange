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
  public OpenOrders getOpenOrders(OpenOrdersParams params) throws IOException {
    String symbol = null;
    if (params instanceof DefaultOpenOrdersParamInstrument) {
      Instrument instrument = ((DefaultOpenOrdersParamInstrument) params).getInstrument();
      if (instrument != null) {
        symbol = BitgetUtaV3Adapters.toString(instrument);
      }
    }
    // unfilled-orders returns both spot and futures; use one page (newest 100) without
    // a symbol filter when none was requested
    BitgetUtaV3CursorPage<BitgetUtaV3Order> page =
        getUnfilledOrders(null, symbol, null, null, 100, null);
    List<BitgetUtaV3Order> rows =
        page == null || page.getList() == null ? List.of() : page.getList();
    List<LimitOrder> orders =
        rows.stream()
            .map(this::toOrder)
            .filter(order -> order instanceof LimitOrder)
            .filter(order -> params.accept(order))
            .map(order -> (LimitOrder) order)
            .collect(Collectors.toList());
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
    // The TradeService contract allows null or generic params implementations; fall back to
    // defaults rather than casting blindly (the classic BitgetTradeService behaves the same way).
    BitgetUtaV3TradeHistoryParams historyParams =
        params instanceof BitgetUtaV3TradeHistoryParams
            ? (BitgetUtaV3TradeHistoryParams) params
            : new BitgetUtaV3TradeHistoryParams();
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
    BitgetUtaV3CursorPage<BitgetUtaV3Fill> page =
        getFills(
            category == null ? null : category.getWireName(),
            null,
            startTime,
            endTime,
            limit,
            null);
    List<BitgetUtaV3Fill> rows =
        page == null || page.getList() == null ? List.of() : page.getList();
    List<UserTrade> trades = rows.stream().map(this::toUserTrade).collect(Collectors.toList());
    return new UserTrades(trades, TradeSortType.SortByID);
  }

  @Override
  public TradeHistoryParams createTradeHistoryParams() {
    return new BitgetUtaV3TradeHistoryParams();
  }

  @Override
  public OpenPositions getOpenPositions() throws IOException {
    List<BitgetUtaV3Position> positions = getCurrentPositions(null, null, null);
    List<OpenPosition> open =
        positions.stream()
            .map(
                position ->
                    BitgetUtaV3Adapters.toOpenPosition(
                        position, resolveInstrument(position.getCategory(), position.getSymbol())))
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toList());
    return new OpenPositions(open);
  }

  private Order toOrder(BitgetUtaV3Order dto) {
    return BitgetUtaV3Adapters.toOrder(dto, resolveInstrument(dto.getCategory(), dto.getSymbol()));
  }

  private UserTrade toUserTrade(BitgetUtaV3Fill dto) {
    return BitgetUtaV3Adapters.toUserTrade(
        dto, resolveInstrument(dto.getCategory(), dto.getSymbol()));
  }

  private Instrument resolveInstrument(String category, String symbol) {
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
