package org.knowm.xchange.polymarket.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.knowm.xchange.polymarket.PolymarketExchange;
import org.knowm.xchange.polymarket.dto.data.PolymarketDataPosition;
import org.knowm.xchange.polymarket.dto.data.PolymarketDataTrade;
import org.knowm.xchange.polymarket.dto.gamma.PolymarketGammaMarket;
import org.knowm.xchange.polymarket.dto.gamma.PolymarketGammaMarketsKeysetResponse;
import org.knowm.xchange.polymarket.dto.marketdata.PolymarketBookResponse;
import org.knowm.xchange.polymarket.dto.marketdata.PolymarketPriceResponse;
import si.mazi.rescu.HttpStatusIOException;

/** Raw Polymarket market-data access returning provider DTOs. */
public class PolymarketMarketDataServiceRaw extends PolymarketBaseService {

  /** Page size used when walking Gamma markets; the provider clamps {@code limit} to 100. */
  static final int GAMMA_PAGE_SIZE = 100;

  /** Default max age of a persisted catalog cache before the catalog is swept from the start. */
  static final long GAMMA_CACHE_TTL_SECONDS = 86_400L;

  protected PolymarketMarketDataServiceRaw(PolymarketExchange exchange) {
    super(exchange);
  }

  /** Single page of Gamma markets, read through the deprecated offset endpoint. */
  public List<PolymarketGammaMarket> getGammaMarkets(
      Integer limit, Integer offset, Boolean active, Boolean closed) throws IOException {
    return gammaPublic.getMarkets(limit, offset, active, closed);
  }

  /** Single keyset page of Gamma markets. */
  public PolymarketGammaMarketsKeysetResponse getGammaMarketsKeyset(
      Integer limit, String afterCursor, Boolean closed) throws IOException {
    return gammaPublic.getMarketsKeyset(limit, afterCursor, closed);
  }

  /**
   * All active, non-closed Gamma markets, read with keyset pagination. The walk is unbounded by
   * default, so the catalog is never silently truncated; {@code polymarket.gamma.discovery.pages}
   * bounds it explicitly (a bounded walk returns the first N pages of markets as they are ordered
   * by market id, and neither reads nor writes the catalog cache).
   *
   * <p>With {@code polymarket.gamma.discovery.cache} set to a file path, the catalog is persisted
   * and a later call within the cache max age resumes from the stored cursor instead of re-reading
   * every page; the age is measured from the last completed from-the-start sweep, so state changes
   * on older rows (a market closing) are still reconciled.
   */
  public List<PolymarketGammaMarket> getAllActiveGammaMarkets() throws IOException {
    Integer maxPages = configuredMaxPages();
    String cachePath = configuredCachePath();
    if (maxPages != null || cachePath == null || cachePath.isBlank()) {
      return walkGammaMarkets(null, maxPages).markets();
    }
    long ttlSeconds = configuredCacheTtlSeconds();
    PolymarketGammaCatalogCache cache =
        PolymarketGammaCatalogCache.load(Path.of(cachePath), gammaUri, Boolean.FALSE);
    String startCursor = cache.isFresh(ttlSeconds) ? cache.cursor() : null;
    GammaWalk walk;
    try {
      walk = walkGammaMarkets(startCursor, null);
    } catch (HttpStatusIOException e) {
      if (startCursor == null || e.getHttpStatusCode() != 422) {
        throw e;
      }
      // The provider rejected the stored cursor ("invalid cursor"); sweep the catalog again.
      startCursor = null;
      walk = walkGammaMarkets(null, null);
    }
    cache.merge(walk.markets());
    cache.advanceCursor(walk.checkpointCursor());
    if (startCursor == null) {
      cache.markFullWalk();
    }
    cache.save();
    return cache.markets();
  }

  /**
   * Walks Gamma keyset pages, keeping only active, non-closed rows: the provider honours {@code
   * closed} but ignores {@code active} on the keyset endpoint, so the active filter is applied
   * here.
   *
   * @param startCursor cursor to resume from, or {@code null} to start at the first market
   * @param maxPages page bound, or {@code null} for the whole catalog
   */
  private GammaWalk walkGammaMarkets(String startCursor, Integer maxPages) throws IOException {
    List<PolymarketGammaMarket> markets = new ArrayList<>();
    String cursor = startCursor;
    String checkpoint = startCursor;
    for (int page = 0; maxPages == null || page < maxPages; page++) {
      PolymarketGammaMarketsKeysetResponse response =
          gammaPublic.getMarketsKeyset(GAMMA_PAGE_SIZE, cursor, Boolean.FALSE);
      List<PolymarketGammaMarket> batch = response == null ? null : response.markets();
      if (batch != null) {
        for (PolymarketGammaMarket market : batch) {
          if (!Boolean.TRUE.equals(market.closed()) && !Boolean.FALSE.equals(market.active())) {
            markets.add(market);
          }
        }
      }
      String next = response == null ? null : response.nextCursor();
      if (next == null || next.isBlank()) {
        // Last page: the provider returns no cursor past it, so the resume point is the cursor
        // that produced this page (re-reading it next time is one request, deduplicated on merge).
        checkpoint = cursor;
        break;
      }
      checkpoint = next;
      cursor = next;
    }
    return new GammaWalk(markets, checkpoint);
  }

  /** Rows read by one walk plus the cursor a later walk resumes from. */
  private record GammaWalk(List<PolymarketGammaMarket> markets, String checkpointCursor) {}

  private Integer configuredMaxPages() {
    Object value =
        exchange
            .getExchangeSpecification()
            .getExchangeSpecificParametersItem(PolymarketExchange.PARAM_GAMMA_DISCOVERY_PAGES);
    if (value == null) {
      return null;
    }
    int pages = Integer.parseInt(value.toString().trim());
    if (pages < 1) {
      throw new IllegalArgumentException(
          PolymarketExchange.PARAM_GAMMA_DISCOVERY_PAGES + " must be at least 1: " + pages);
    }
    return pages;
  }

  private String configuredCachePath() {
    Object value =
        exchange
            .getExchangeSpecification()
            .getExchangeSpecificParametersItem(PolymarketExchange.PARAM_GAMMA_DISCOVERY_CACHE);
    return value == null ? null : value.toString();
  }

  private long configuredCacheTtlSeconds() {
    Object value =
        exchange
            .getExchangeSpecification()
            .getExchangeSpecificParametersItem(
                PolymarketExchange.PARAM_GAMMA_DISCOVERY_CACHE_TTL);
    return value == null ? GAMMA_CACHE_TTL_SECONDS : Long.parseLong(value.toString().trim());
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
