package org.knowm.xchange.okx;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import java.io.IOException;
import java.util.List;
import org.knowm.xchange.okx.dto.OkxException;
import org.knowm.xchange.okx.dto.OkxResponse;
import org.knowm.xchange.okx.dto.marketdata.OkxCandleStick;
import org.knowm.xchange.okx.dto.marketdata.OkxFundingRate;
import org.knowm.xchange.okx.dto.marketdata.OkxFundingRateHistory;
import org.knowm.xchange.okx.dto.marketdata.OkxInstrument;
import org.knowm.xchange.okx.dto.marketdata.OkxOrderbook;
import org.knowm.xchange.okx.dto.marketdata.OkxTicker;
import org.knowm.xchange.okx.dto.marketdata.OkxTrade;

@Path("/api/v5")
@Produces(APPLICATION_JSON)
public interface Okx {
  String instrumentsPath = "/public/instruments"; // Stated as 20 req/2 sec
  String underlyingPath = "/public/underlying"; // Stated as 10 req/2 sec
  String tickerPath = "/market/ticker"; // Stated as 20 req/2 sec
  String tickersPath = "/market/tickers"; // Stated as 20 req/2 sec
  String fundingRateHistoryPath = "/public/funding-rate-history"; // Stated as 10 req/2 sec
  String candlesHistoryPath = "/market/history-candles"; // Stated as 20 req/2 sec

  // To avoid 429s, actual req/second may need to be lowered!
  OkxRateLimitPolicy publicPathRateLimits =
      OkxRateLimitPolicy.builder()
          .limit(instrumentsPath, 8, 1)
          .limit(underlyingPath, 10, 2)
          .limit(tickerPath, 8, 1)
          .limit(tickersPath, 8, 1)
          .limit(fundingRateHistoryPath, 4, 1)
          .limit(candlesHistoryPath, 8, 1)
          .build();

  @GET
  @Path(instrumentsPath)
  OkxResponse<List<OkxInstrument>> getInstruments(
      @QueryParam("instType") String instrumentType,
      @QueryParam("uly") String underlying,
      @QueryParam("instId") String instrumentId,
      @HeaderParam("X-SIMULATED-TRADING") String simulatedTrading)
      throws OkxException, IOException;

  @GET
  @Path(underlyingPath)
  OkxResponse<List<List<String>>> getUnderlyings(@QueryParam("instType") String instrumentType)
      throws OkxException, IOException;

  @GET
  @Path("/market/trades")
  OkxResponse<List<OkxTrade>> getTrades(
      @QueryParam("instId") String instrument,
      @QueryParam("limit") int limit,
      @HeaderParam("X-SIMULATED-TRADING") String simulatedTrading)
      throws IOException, OkxException;

  @GET
  @Path(tickerPath)
  OkxResponse<List<OkxTicker>> getTicker(
      @QueryParam("instId") String instrument,
      @HeaderParam("X-SIMULATED-TRADING") String simulatedTrading)
      throws IOException, OkxException;

  @GET
  @Path(tickersPath)
  OkxResponse<List<OkxTicker>> getTickers(
      @QueryParam("instType") String instType,
      @HeaderParam("X-SIMULATED-TRADING") String simulatedTrading)
      throws IOException, OkxException;

  @GET
  @Path("/market/books")
  OkxResponse<List<OkxOrderbook>> getOrderbook(
      @QueryParam("instId") String instrument,
      @QueryParam("sz") int depth,
      @HeaderParam("X-SIMULATED-TRADING") String simulatedTrading)
      throws IOException, OkxException;

  @GET
  @Path(candlesHistoryPath)
  OkxResponse<List<OkxCandleStick>> getHistoryCandles(
      @QueryParam("instId") String instrument,
      @QueryParam("after") String after,
      @QueryParam("before") String before,
      @QueryParam("bar") String bar,
      @QueryParam("limit") String limit,
      @HeaderParam("X-SIMULATED-TRADING") String simulatedTrading)
      throws IOException, OkxException;

  @GET
  @Path("/public/funding-rate")
  OkxResponse<List<OkxFundingRate>> getFundingRate(
      @QueryParam("instId") String instrument,
      @HeaderParam("X-SIMULATED-TRADING") String simulatedTrading)
      throws IOException, OkxException;

  @GET
  @Path("/market/candles")
  OkxResponse<List<OkxCandleStick>> getCandles(
      @QueryParam("instId") String instrument,
      @QueryParam("after") String after,
      @QueryParam("before") String before,
      @QueryParam("bar") String bar,
      @QueryParam("limit") String limit,
      @HeaderParam("X-SIMULATED-TRADING") String simulatedTrading)
      throws IOException, OkxException;

  @GET
  @Path(fundingRateHistoryPath)
  OkxResponse<List<OkxFundingRateHistory>> getFundingRateHistory(
      @QueryParam("instId") String instrument,
      @QueryParam("after") Long after,
      @QueryParam("before") Long before,
      @QueryParam("limit") Integer limit,
      @HeaderParam("X-SIMULATED-TRADING") String simulatedTrading)
      throws IOException, OkxException;
}
