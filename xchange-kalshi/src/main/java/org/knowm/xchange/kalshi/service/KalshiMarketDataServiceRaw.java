package org.knowm.xchange.kalshi.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.knowm.xchange.kalshi.KalshiExchange;
import org.knowm.xchange.kalshi.dto.marketdata.KalshiMarket;
import org.knowm.xchange.kalshi.dto.marketdata.KalshiMarketResponse;
import org.knowm.xchange.kalshi.dto.marketdata.KalshiMarketsResponse;
import org.knowm.xchange.kalshi.dto.marketdata.KalshiOrderBookResponse;
import org.knowm.xchange.kalshi.dto.marketdata.KalshiTradesResponse;

/** Raw Kalshi market-data access returning provider DTOs. */
public class KalshiMarketDataServiceRaw extends KalshiBaseService {

  /** Safety bound on cursor-following loops. */
  static final int MAX_PAGES = 50;

  protected KalshiMarketDataServiceRaw(KalshiExchange exchange) {
    super(exchange);
  }

  /** Single page of markets. */
  public KalshiMarketsResponse getKalshiMarkets(String status, String cursor, Integer limit)
      throws IOException {
    return kalshiPublic.getMarkets(limit, cursor, status, null);
  }

  /** All {@code open} markets, following the cursor pagination to completion. */
  public List<KalshiMarket> getAllOpenKalshiMarkets() throws IOException {
    List<KalshiMarket> markets = new ArrayList<>();
    String cursor = null;
    for (int page = 0; page < MAX_PAGES; page++) {
      KalshiMarketsResponse response = kalshiPublic.getMarkets(1000, cursor, "open", null);
      if (response.markets() != null) {
        markets.addAll(response.markets());
      }
      cursor = response.cursor();
      if (cursor == null || cursor.isBlank()) {
        return markets;
      }
    }
    return markets;
  }

  /** Single market by ticker. */
  public KalshiMarketResponse getKalshiMarket(String ticker) throws IOException {
    return kalshiPublic.getMarket(ticker);
  }

  /** Order book for a market. */
  public KalshiOrderBookResponse getKalshiOrderBook(String ticker, Integer depth)
      throws IOException {
    return kalshiPublic.getOrderBook(ticker, depth);
  }

  /** Public trades for a market. */
  public KalshiTradesResponse getKalshiTrades(String ticker, Integer limit, String cursor)
      throws IOException {
    return kalshiPublic.getTrades(ticker, limit, cursor);
  }
}
