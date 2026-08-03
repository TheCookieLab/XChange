package org.knowm.xchange.polymarket.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.knowm.xchange.polymarket.PolymarketExchange;
import org.knowm.xchange.polymarket.dto.data.PolymarketDataPosition;
import org.knowm.xchange.polymarket.dto.data.PolymarketDataTrade;
import org.knowm.xchange.polymarket.dto.gamma.PolymarketGammaMarket;
import org.knowm.xchange.polymarket.dto.marketdata.PolymarketBookResponse;
import org.knowm.xchange.polymarket.dto.marketdata.PolymarketPriceResponse;

/** Raw Polymarket market-data access returning provider DTOs. */
public class PolymarketMarketDataServiceRaw extends PolymarketBaseService {

  /** Safety bound on offset-pagination loops. */
  static final int MAX_PAGES = 50;

  /** Page size used when walking Gamma markets to completion. */
  static final int GAMMA_PAGE_SIZE = 100;

  protected PolymarketMarketDataServiceRaw(PolymarketExchange exchange) {
    super(exchange);
  }

  /** Single page of Gamma markets. */
  public List<PolymarketGammaMarket> getGammaMarkets(
      Integer limit, Integer offset, Boolean active, Boolean closed) throws IOException {
    return gammaPublic.getMarkets(limit, offset, active, closed);
  }

  /**
   * All active, non-closed Gamma markets, following offset pagination to completion. Records the
   * server would have filtered are dropped defensively so closed markets never reach metadata.
   */
  public List<PolymarketGammaMarket> getAllActiveGammaMarkets() throws IOException {
    List<PolymarketGammaMarket> markets = new ArrayList<>();
    int offset = 0;
    for (int page = 0; page < MAX_PAGES; page++) {
      List<PolymarketGammaMarket> batch =
          gammaPublic.getMarkets(GAMMA_PAGE_SIZE, offset, true, false);
      if (batch == null || batch.isEmpty()) {
        return markets;
      }
      for (PolymarketGammaMarket market : batch) {
        if (!Boolean.TRUE.equals(market.closed()) && !Boolean.FALSE.equals(market.active())) {
          markets.add(market);
        }
      }
      if (batch.size() < GAMMA_PAGE_SIZE) {
        return markets;
      }
      offset += batch.size();
    }
    return markets;
  }

  /** Order book for one outcome token. */
  public PolymarketBookResponse getBook(String tokenId) throws IOException {
    return clobPublic.getBook(tokenId);
  }

  /** Current executable price for one side of one outcome token. */
  public PolymarketPriceResponse getPrice(String tokenId, String side) throws IOException {
    return clobPublic.getPrice(tokenId, side);
  }

  /** Recent public trades for a condition id. */
  public List<PolymarketDataTrade> getDataTrades(String conditionId, Integer limit)
      throws IOException {
    return dataPublic.getTrades(conditionId, limit);
  }

  /** Open outcome-token positions of a wallet (public Data API). */
  public List<PolymarketDataPosition> getDataPositions(
      String userAddress, Integer limit, Integer offset) throws IOException {
    return dataPublic.getPositions(userAddress, limit, offset);
  }
}
