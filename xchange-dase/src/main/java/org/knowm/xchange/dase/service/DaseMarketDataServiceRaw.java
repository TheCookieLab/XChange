package org.knowm.xchange.dase.service;

import java.io.IOException;
import java.util.List;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.client.ExchangeRestProxyBuilder;
import org.knowm.xchange.dase.DaseV1;
import org.knowm.xchange.dase.DaseV1.DaseTradesResponse;
import org.knowm.xchange.dase.dto.marketdata.DaseMarketConfig;
import org.knowm.xchange.dase.dto.marketdata.DaseMarketsResponse;
import org.knowm.xchange.dase.dto.marketdata.DaseOrderBookSnapshot;
import org.knowm.xchange.dase.dto.marketdata.DaseTicker;
import org.knowm.xchange.dase.dto.marketdata.DaseTrade;
import org.knowm.xchange.service.BaseExchangeService;
import org.knowm.xchange.service.BaseService;

public class DaseMarketDataServiceRaw extends BaseExchangeService implements BaseService {

  protected final DaseV1 dase;

  public DaseMarketDataServiceRaw(Exchange exchange) {
    super(exchange);
    this.dase =
        ExchangeRestProxyBuilder.forInterface(DaseV1.class, exchange.getExchangeSpecification())
            .build();
  }

  public List<DaseMarketConfig> getMarkets() throws IOException {
    DaseMarketsResponse resp = dase.getMarkets();
    return resp.markets;
  }

  public DaseTicker getTicker(String market) throws IOException {
    return dase.getTicker(market);
  }

  public DaseOrderBookSnapshot getSnapshot(String market) throws IOException {
    return dase.getSnapshot(market);
  }

  public List<DaseTrade> getTrades(String market, Integer limit, String before) throws IOException {
    DaseTradesResponse resp = dase.getTrades(market, limit, before);
    return resp.trades;
  }
}
