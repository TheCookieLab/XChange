package org.knowm.xchange.bitget.uta.v3;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.List;
import org.knowm.xchange.bitget.uta.v3.common.BitgetUtaV3Exception;
import org.knowm.xchange.bitget.uta.v3.common.BitgetUtaV3Response;
import org.knowm.xchange.bitget.uta.v3.market.BitgetUtaV3Candle;
import org.knowm.xchange.bitget.uta.v3.market.BitgetUtaV3Instrument;
import org.knowm.xchange.bitget.uta.v3.market.BitgetUtaV3OrderBook;
import org.knowm.xchange.bitget.uta.v3.market.BitgetUtaV3ServerTime;
import org.knowm.xchange.bitget.uta.v3.market.BitgetUtaV3Ticker;

/**
 * Bitget UTA v3 public market data REST interface (no authentication).
 *
 * <p>All methods hit {@code https://api.bitget.com}. Public trade history is not exposed by v3 (the
 * {@code /api/v3/market/trades} path does not exist); the WebSocket publicTrade channel is the
 * documented source for recent trades.
 */
@Path("")
@Produces(MediaType.APPLICATION_JSON)
public interface BitgetUtaV3 {

  @GET
  @Path("api/v3/market/time")
  BitgetUtaV3Response<BitgetUtaV3ServerTime> serverTime() throws IOException, BitgetUtaV3Exception;

  @GET
  @Path("api/v3/market/instruments")
  BitgetUtaV3Response<List<BitgetUtaV3Instrument>> instruments(
      @QueryParam("category") String category, @QueryParam("symbol") String symbol)
      throws IOException, BitgetUtaV3Exception;

  @GET
  @Path("api/v3/market/orderbook")
  BitgetUtaV3Response<BitgetUtaV3OrderBook> orderbook(
      @QueryParam("symbol") String symbol,
      @QueryParam("category") String category,
      @QueryParam("limit") Integer limit)
      throws IOException, BitgetUtaV3Exception;

  @GET
  @Path("api/v3/market/tickers")
  BitgetUtaV3Response<List<BitgetUtaV3Ticker>> tickers(
      @QueryParam("category") String category, @QueryParam("symbol") String symbol)
      throws IOException, BitgetUtaV3Exception;

  @GET
  @Path("api/v3/market/candles")
  BitgetUtaV3Response<List<BitgetUtaV3Candle>> candles(
      @QueryParam("symbol") String symbol,
      @QueryParam("category") String category,
      @QueryParam("interval") String interval,
      @QueryParam("limit") Integer limit,
      @QueryParam("startTime") String startTime,
      @QueryParam("endTime") String endTime)
      throws IOException, BitgetUtaV3Exception;
}
