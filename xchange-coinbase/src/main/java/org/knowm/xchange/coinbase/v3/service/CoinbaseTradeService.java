package org.knowm.xchange.coinbase.v3.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.coinbase.v3.CoinbaseAuthenticated;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseFill;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrdersResponse;
import org.knowm.xchange.coinbase.v3.dto.trade.CoinbaseTradeHistoryParams;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;

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
}
