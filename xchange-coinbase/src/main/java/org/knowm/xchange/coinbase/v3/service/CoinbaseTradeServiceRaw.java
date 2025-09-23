package org.knowm.xchange.coinbase.v3.service;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.coinbase.CoinbaseAdapters;
import org.knowm.xchange.coinbase.v3.CoinbaseAuthenticated;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrderDetailResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseListOrdersResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrdersResponse;
import org.knowm.xchange.coinbase.v3.dto.trade.CoinbaseTradeHistoryParams;
import si.mazi.rescu.ParamsDigest;

public class CoinbaseTradeServiceRaw extends CoinbaseBaseService {

  public CoinbaseTradeServiceRaw(Exchange exchange) {
    super(exchange);
  }

  public CoinbaseTradeServiceRaw(Exchange exchange, CoinbaseAuthenticated coinbaseAdvancedTrade) {
    super(exchange, coinbaseAdvancedTrade);
  }

  public CoinbaseTradeServiceRaw(Exchange exchange, CoinbaseAuthenticated coinbaseAdvancedTrade,
      ParamsDigest authTokenCreator) {
    super(exchange, coinbaseAdvancedTrade, authTokenCreator);
  }

  /**
   * Lists fills for the authenticated user using Coinbase Advanced Trade.
   *
   * <p>Gotcha: Although the Coinbase endpoint accepts multiple values for filters like product,
   * order, or trade IDs, this implementation forwards at most one value per filter. If
   * {@link CoinbaseTradeHistoryParams} contains multiple currency pairs, only the first is used to
   * derive the {@code product_id}. Likewise, only a single {@code order_id} and a single
   * {@code trade_id} are forwarded if present. This means the returned fills reflect only the first
   * provided values.
   *
   * @param params trade history parameters including optional product/order/trade filters,
   *               pagination cursor, time span, and limit
   * @return a {@link CoinbaseOrdersResponse} containing fills and a cursor for pagination
   * @throws IOException if a network or serialization error occurs
   */
  public CoinbaseOrdersResponse listFills(CoinbaseTradeHistoryParams params) throws IOException {
    List<String> productIds = null;
    if (params.getCurrencyPairs() != null && !params.getCurrencyPairs().isEmpty()) {
      List<String> pids = params.getCurrencyPairs().stream().map(CoinbaseAdapters::adaptProductId)
          .collect(Collectors.toList());
      // Only allow at most one product_id
      productIds = Collections.singletonList(pids.get(0));
    }

    List<String> orderIds =
        params.getOrderId() == null ? null : Collections.singletonList(params.getOrderId());
    List<String> tradeIds = params.getTransactionId() == null ? null
        : Collections.singletonList(params.getTransactionId());

    String startTs =
        params.getStartTime() == null ? null : params.getStartTime().toInstant().toString();
    String endTs = params.getEndTime() == null ? null : params.getEndTime().toInstant().toString();

    Integer limit = params.getLimit();
    String cursor = params.getNextPageCursor();

    return coinbaseAdvancedTrade.listFills(authTokenCreator, orderIds, tradeIds, productIds,
        startTs, endTs, null, limit, cursor, null);
  }

  /**
   * Retrieves a historical order by its id using Coinbase Advanced Trade.
   *
   * @param orderId the Coinbase Advanced Trade order id
   * @return the detailed order response from Coinbase
   * @throws IOException if a network or serialization error occurs
   */
  public CoinbaseOrderDetailResponse getOrder(String orderId) throws IOException {
    return coinbaseAdvancedTrade.getOrder(authTokenCreator, orderId);
  }

  /**
   * Lists historical orders and returns the raw Coinbase response for further mapping.
   * Note: this endpoint returns historical orders; open orders can be derived by filtering status.
   */
  public CoinbaseListOrdersResponse listOrders() throws IOException {
    return coinbaseAdvancedTrade.listOrders(authTokenCreator);
  }

}
