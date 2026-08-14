package org.knowm.xchange.mexc.v3;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.List;
import org.knowm.xchange.mexc.v3.client.MexcV3Exception;
import org.knowm.xchange.mexc.v3.dto.marketdata.MexcV3AggTrade;
import org.knowm.xchange.mexc.v3.dto.marketdata.MexcV3AvgPrice;
import org.knowm.xchange.mexc.v3.dto.marketdata.MexcV3BookTicker;
import org.knowm.xchange.mexc.v3.dto.marketdata.MexcV3DefaultSymbols;
import org.knowm.xchange.mexc.v3.dto.marketdata.MexcV3Depth;
import org.knowm.xchange.mexc.v3.dto.marketdata.MexcV3ExchangeInfo;
import org.knowm.xchange.mexc.v3.dto.marketdata.MexcV3Kline;
import org.knowm.xchange.mexc.v3.dto.marketdata.MexcV3KlineInterval;
import org.knowm.xchange.mexc.v3.dto.marketdata.MexcV3PriceTicker;
import org.knowm.xchange.mexc.v3.dto.marketdata.MexcV3ServerTime;
import org.knowm.xchange.mexc.v3.dto.marketdata.MexcV3Ticker24h;
import org.knowm.xchange.mexc.v3.dto.marketdata.MexcV3Trade;

/**
 * Public (unauthenticated) MEXC Spot v3 REST surface.
 *
 * <p>All endpoints live under {@code /api/v3} on {@code https://api.mexc.com} and require no
 * authentication. rescu deserializes provider error envelopes into {@link MexcV3Exception}.
 */
@Path("/api/v3")
@Produces(MediaType.APPLICATION_JSON)
public interface MexcV3MarketDataRaw {

  /** Test connectivity. Response is {@code {}}. */
  @GET
  @Path("/ping")
  String ping() throws IOException, MexcV3Exception;

  /** Check server time. */
  @GET
  @Path("/time")
  MexcV3ServerTime time() throws IOException, MexcV3Exception;

  /**
   * Exchange information.
   *
   * @param symbol single symbol filter, or {@code null} for all.
   * @param symbols comma-separated symbol filter, or {@code null} for all.
   */
  @GET
  @Path("/exchangeInfo")
  MexcV3ExchangeInfo exchangeInfo(
      @QueryParam("symbol") String symbol, @QueryParam("symbols") String symbols)
      throws IOException, MexcV3Exception;

  /** Order book. {@code limit} defaults to 100, max 5000. */
  @GET
  @Path("/depth")
  MexcV3Depth depth(@QueryParam("symbol") String symbol, @QueryParam("limit") Integer limit)
      throws IOException, MexcV3Exception;

  /** Recent trades. {@code limit} defaults to 500, max 1000. */
  @GET
  @Path("/trades")
  List<MexcV3Trade> trades(@QueryParam("symbol") String symbol, @QueryParam("limit") Integer limit)
      throws IOException, MexcV3Exception;

  /**
   * Aggregated trades. {@code startTime} and {@code endTime} are inclusive and must be provided
   * together; {@code limit} defaults to 500, max 1000.
   */
  @GET
  @Path("/aggTrades")
  List<MexcV3AggTrade> aggTrades(
      @QueryParam("symbol") String symbol,
      @QueryParam("startTime") Long startTime,
      @QueryParam("endTime") Long endTime,
      @QueryParam("limit") Integer limit)
      throws IOException, MexcV3Exception;

  /** Klines. {@code limit} defaults to 500, max 500. Pass {@code interval.getWireValue()}. */
  @GET
  @Path("/klines")
  List<MexcV3Kline> klines(
      @QueryParam("symbol") String symbol,
      @QueryParam("interval") String interval,
      @QueryParam("startTime") Long startTime,
      @QueryParam("endTime") Long endTime,
      @QueryParam("limit") Integer limit)
      throws IOException, MexcV3Exception;

  /** Current average price. */
  @GET
  @Path("/avgPrice")
  MexcV3AvgPrice avgPrice(@QueryParam("symbol") String symbol) throws IOException, MexcV3Exception;

  /** 24-hour ticker for one symbol (object). */
  @GET
  @Path("/ticker/24hr")
  MexcV3Ticker24h ticker24h(@QueryParam("symbol") String symbol)
      throws IOException, MexcV3Exception;

  /** 24-hour ticker for every symbol (array). */
  @GET
  @Path("/ticker/24hr")
  List<MexcV3Ticker24h> ticker24hAll() throws IOException, MexcV3Exception;

  /** Symbol price ticker for one symbol (object). */
  @GET
  @Path("/ticker/price")
  MexcV3PriceTicker priceTicker(@QueryParam("symbol") String symbol)
      throws IOException, MexcV3Exception;

  /** Symbol price ticker for every symbol (array). */
  @GET
  @Path("/ticker/price")
  List<MexcV3PriceTicker> priceTickerAll() throws IOException, MexcV3Exception;

  /** Order book ticker for one symbol (object). */
  @GET
  @Path("/ticker/bookTicker")
  MexcV3BookTicker bookTicker(@QueryParam("symbol") String symbol)
      throws IOException, MexcV3Exception;

  /** Order book ticker for every symbol (array). */
  @GET
  @Path("/ticker/bookTicker")
  List<MexcV3BookTicker> bookTickerAll() throws IOException, MexcV3Exception;

  /** API default symbols (envelope {@code {code, data, msg}}). */
  @GET
  @Path("/defaultSymbols")
  MexcV3DefaultSymbols defaultSymbols() throws IOException, MexcV3Exception;
}
