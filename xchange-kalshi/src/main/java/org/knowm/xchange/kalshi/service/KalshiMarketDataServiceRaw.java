package org.knowm.xchange.kalshi.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.knowm.xchange.kalshi.KalshiExchange;
import org.knowm.xchange.kalshi.dto.marketdata.KalshiMarket;
import org.knowm.xchange.kalshi.dto.marketdata.KalshiMarketResponse;
import org.knowm.xchange.kalshi.dto.marketdata.KalshiMarketsResponse;
import org.knowm.xchange.kalshi.dto.marketdata.KalshiOrderBookResponse;
import org.knowm.xchange.kalshi.dto.marketdata.KalshiTradesResponse;

/** Raw Kalshi market-data access returning provider DTOs. */
public class KalshiMarketDataServiceRaw extends KalshiBaseService {

  /** Page size used when walking Kalshi markets; the provider caps {@code limit} at 1000. */
  static final int KALSHI_PAGE_SIZE = 1000;

  /** Default catalog bound: 100 pages x 1000 markets. */
  static final int DEFAULT_MAX_PAGES = 100;

  protected KalshiMarketDataServiceRaw(KalshiExchange exchange) {
    super(exchange);
  }

  /** Single page of markets. */
  public KalshiMarketsResponse getKalshiMarkets(String status, String cursor, Integer limit)
      throws IOException {
    return kalshiPublic.getMarkets(limit, cursor, status, null);
  }

  /**
   * The first {@code kalshi.markets.pages} pages (default {@link #DEFAULT_MAX_PAGES}) of {@code
   * open} markets, de-duplicated by ticker. The bound is deliberate rather than an error: the
   * provider's active catalog is far larger than any caller wants materialised by {@code
   * createExchange()}, and the raw {@link #getKalshiMarkets(String, String, Integer)} accessor is
   * the complete-crawl path. The walk also stops when the provider repeats a cursor, so a
   * non-advancing cursor cannot spin.
   *
   * @throws IllegalArgumentException when the configured bound is below 1
   */
  public List<KalshiMarket> getAllOpenKalshiMarkets() throws IOException {
    int maxPages = configuredMaxPages();
    List<KalshiMarket> markets = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    String cursor = null;
    for (int page = 0; page < maxPages; page++) {
      KalshiMarketsResponse response =
          kalshiPublic.getMarkets(KALSHI_PAGE_SIZE, cursor, "open", null);
      List<KalshiMarket> batch = response == null ? null : response.markets();
      if (batch != null) {
        for (KalshiMarket market : batch) {
          if (market.ticker() != null && seen.add(market.ticker())) {
            markets.add(market);
          }
        }
      }
      String next = response == null ? null : response.cursor();
      if (next == null || next.isBlank() || next.equals(cursor)) {
        break;
      }
      cursor = next;
    }
    return markets;
  }

  private int configuredMaxPages() {
    Object value =
        exchange
            .getExchangeSpecification()
            .getExchangeSpecificParametersItem(KalshiExchange.MARKETS_PAGES_PARAMETER);
    if (value == null) {
      return DEFAULT_MAX_PAGES;
    }
    int pages = Integer.parseInt(value.toString().trim());
    if (pages < 1) {
      throw new IllegalArgumentException(
          KalshiExchange.MARKETS_PAGES_PARAMETER + " must be at least 1: " + pages);
    }
    return pages;
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
