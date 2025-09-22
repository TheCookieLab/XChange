package org.knowm.xchange.coinbase.v3.service;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.coinbase.CoinbaseAdapters;
import org.knowm.xchange.coinbase.v3.CoinbaseAuthenticated;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrdersResponse;
import org.knowm.xchange.coinbase.v3.dto.trade.CoinbaseTradeHistoryParams;
// import org.knowm.xchange.service.trade.params.TradeHistoryParams;
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

  public CoinbaseOrdersResponse listFills(CoinbaseTradeHistoryParams params) throws IOException {
    java.util.List<String> productIds = null;
    if (params.getCurrencyPairs() != null && !params.getCurrencyPairs().isEmpty()) {
      List<String> pids = params.getCurrencyPairs().stream()
          .map(CoinbaseAdapters::adaptProductId)
          .collect(Collectors.toList());
      // Only allow at most one product_id
      productIds = java.util.Collections.singletonList(pids.get(0));
    }

    java.util.List<String> orderIds = params.getOrderId() == null ? null
        : java.util.Collections.singletonList(params.getOrderId());
    java.util.List<String> tradeIds = params.getTransactionId() == null ? null
        : java.util.Collections.singletonList(params.getTransactionId());

    String startTs = params.getStartTime() == null ? null
        : params.getStartTime().toInstant().toString();
    String endTs = params.getEndTime() == null ? null : params.getEndTime().toInstant().toString();

    Integer limit = params.getLimit();
    String cursor = params.getNextPageCursor();

    return coinbaseAdvancedTrade.listFills(authTokenCreator, orderIds, tradeIds, productIds,
        startTs, endTs, null, limit, cursor, null);
  }

}
