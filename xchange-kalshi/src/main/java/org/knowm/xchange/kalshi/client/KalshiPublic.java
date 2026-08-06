package org.knowm.xchange.kalshi.client;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import org.knowm.xchange.kalshi.dto.marketdata.KalshiExchangeStatusResponse;
import org.knowm.xchange.kalshi.dto.marketdata.KalshiMarketResponse;
import org.knowm.xchange.kalshi.dto.marketdata.KalshiMarketsResponse;
import org.knowm.xchange.kalshi.dto.marketdata.KalshiOrderBookResponse;
import org.knowm.xchange.kalshi.dto.marketdata.KalshiTradesResponse;

/** Public Kalshi v2 REST endpoints. Prices and counts are fixed-point strings. */
@Path("trade-api/v2")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface KalshiPublic {

  /**
   * Lists markets with cursor pagination.
   *
   * @param limit page size (provider maximum applies)
   * @param cursor pagination cursor from a previous response, or {@code null}
   * @param status lifecycle filter such as {@code open}, or {@code null}
   * @param eventTicker event ticker filter, or {@code null}
   */
  @GET
  @Path("markets")
  KalshiMarketsResponse getMarkets(
      @QueryParam("limit") Integer limit,
      @QueryParam("cursor") String cursor,
      @QueryParam("status") String status,
      @QueryParam("event_ticker") String eventTicker)
      throws IOException;

  /** Returns a single market by ticker. */
  @GET
  @Path("markets/{ticker}")
  KalshiMarketResponse getMarket(@PathParam("ticker") String ticker) throws IOException;

  /**
   * Returns the YES/NO order book for a market. Levels are fixed-point
   * {@code [dollars, count_fp]} string pairs; only bids are returned (NO bids are YES asks at
   * the complement price).
   */
  @GET
  @Path("markets/{ticker}/orderbook")
  KalshiOrderBookResponse getOrderBook(
      @PathParam("ticker") String ticker, @QueryParam("depth") Integer depth) throws IOException;

  /** Returns public trades for a market. */
  @GET
  @Path("markets/trades")
  KalshiTradesResponse getTrades(
      @QueryParam("ticker") String ticker,
      @QueryParam("limit") Integer limit,
      @QueryParam("cursor") String cursor)
      throws IOException;

  /** Returns the exchange trading status. */
  @GET
  @Path("exchange/status")
  KalshiExchangeStatusResponse getExchangeStatus() throws IOException;
}
