package org.knowm.xchange.coinbase.v3.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.coinbase.v3.CoinbaseAuthenticated;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseFill;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseListOrdersResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrderDetailResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrdersResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseV3OrderRequests;
import org.knowm.xchange.coinbase.v3.dto.trade.CoinbaseTradeHistoryParams;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.coinbase.CoinbaseAdapters;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.StopOrder;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.service.trade.params.orders.DefaultQueryOrderParam;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;

import org.knowm.xchange.service.trade.params.orders.OrderQueryParams;
import si.mazi.rescu.ParamsDigest;

public class CoinbaseTradeService extends CoinbaseTradeServiceRaw implements TradeService {

  public CoinbaseTradeService(Exchange exchange) {
    super(exchange);
  }

  public CoinbaseTradeService(Exchange exchange, CoinbaseAuthenticated coinbaseAdvancedTrade) {
    super(exchange, coinbaseAdvancedTrade);
  }

  public CoinbaseTradeService(Exchange exchange, CoinbaseAuthenticated coinbaseAdvancedTrade,
      ParamsDigest authTokenCreator) {
    super(exchange, coinbaseAdvancedTrade, authTokenCreator);
  }

  @Override
  public TradeHistoryParams createTradeHistoryParams() {
    return new CoinbaseTradeHistoryParams();
  }

  @Override
  public Collection<Order> getOrder(OrderQueryParams... orderQueryParams) throws IOException {
    List<Order> orders = new ArrayList<>(orderQueryParams.length);
    for (OrderQueryParams param : orderQueryParams) {
      String orderId = param.getOrderId();
      if (orderId == null && param instanceof DefaultQueryOrderParam) {
        orderId = param.getOrderId();
      }
      if (orderId == null) continue;
      orders.add(CoinbaseAdapters.adaptOrder(getOrder(orderId).getOrder()));
    }
    return orders;
  }

  /**
   * Retrieves the user's trade history using the Coinbase Advanced Trade API.
   *
   * <p>Gotcha: If {@code params} contains multiple {@link org.knowm.xchange.currency.CurrencyPair}
   * entries, only the first pair is forwarded to the underlying {@code listFills} call. While the
   * Coinbase REST endpoint supports multi-value filters, this implementation currently forwards at
   * most one value per filter to avoid repeated parameter encoding. The resulting history therefore
   * reflects only the first currency pair (and at most one order/trade id) provided.
   *
   * <p>Pagination is handled automatically via the response cursor until it is exhausted or the
   * optional {@code limit} in {@link CoinbaseTradeHistoryParams} is reached.
   *
   * @param params expected to be {@link CoinbaseTradeHistoryParams}; includes optional time span,
   *     limit and next-page cursor
   * @return the user's trades sorted by timestamp
   * @throws IOException if a network or serialization error occurs
   * @throws IllegalArgumentException if {@code params} is not an instance of
   *     {@link CoinbaseTradeHistoryParams}
   */
  @Override
  public UserTrades getTradeHistory(TradeHistoryParams params) throws IOException {
    if (!(params instanceof CoinbaseTradeHistoryParams)) {
      throw new IllegalArgumentException(
          "Expected CoinbaseTradeHistoryParams for Coinbase Advanced Trade history");
    }

    CoinbaseTradeHistoryParams v3Params =
        (CoinbaseTradeHistoryParams) params;

    List<UserTrade> trades = new ArrayList<>();
    String cursor;
    do {
      CoinbaseOrdersResponse response = listFills(
          v3Params);
      for (CoinbaseFill fill : response.getFills()) {
        UserTrade trade = UserTrade.builder()
            .type(fill.getOrderType()).originalAmount(fill.getSize()).instrument(fill.getInstrument())
            .price(fill.getPrice()).timestamp(fill.getTradeTime()).id(fill.getTradeId())
            .orderId(fill.getOrderId()).feeAmount(fill.getCommission())
            .feeCurrency(fill.getFeeCurrency()).build();
        trades.add(trade);
      }
      cursor = response.getCursor();
      v3Params.setNextPageCursor(cursor);
    } while (cursor != null && !cursor.isEmpty() && (v3Params.getLimit() == null
        || trades.size() < v3Params.getLimit()));

    return new UserTrades(trades,
        Trades.TradeSortType.SortByTimestamp);
  }

  /**
   * Returns open orders by listing historical orders and selecting those in an open status.
   * Note: Advanced Trade historical orders include current open ones; we filter accordingly.
   */
  @Override
  public OpenOrders getOpenOrders() throws IOException {
    return CoinbaseAdapters.adaptOpenOrders(super.listOrders());
  }

  /** Convenience delegator to fetch raw list orders response. */
  public CoinbaseListOrdersResponse listOrders()
      throws IOException {
    return super.listOrders();
  }
  /**
   * Retrieves a historical order by its id and adapts it to XChange {@link Order}.
   *
   * @param orderId the Coinbase Advanced Trade order id
   * @return the adapted order
   * @throws IOException if a network or serialization error occurs
   */
  public CoinbaseOrderDetailResponse getOrder(String orderId)
      throws IOException {
    return super.getOrder(orderId);
  }

  @Override
  public String placeMarketOrder(MarketOrder marketOrder) throws IOException {
    Object request = CoinbaseV3OrderRequests.marketOrderRequest(marketOrder);
    return CoinbaseAdapters.adaptCreatedOrderId(super.createOrder(request));
  }

  @Override
  public String placeLimitOrder(LimitOrder limitOrder) throws IOException {
    Object request = CoinbaseV3OrderRequests.limitOrderRequest(limitOrder);
    return CoinbaseAdapters.adaptCreatedOrderId(super.createOrder(request));
  }

  @Override
  public String placeStopOrder(StopOrder stopOrder) throws IOException {
    Object request = CoinbaseV3OrderRequests.stopOrderRequest(stopOrder);
    return CoinbaseAdapters.adaptCreatedOrderId(super.createOrder(request));
  }

}
