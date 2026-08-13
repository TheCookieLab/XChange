package org.knowm.xchange.bybit;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import org.knowm.xchange.bybit.dto.BybitCategorizedPayload;
import org.knowm.xchange.bybit.dto.BybitResult;
import org.knowm.xchange.bybit.dto.marketdata.BybitFundingRateHistoryRaw;
import org.knowm.xchange.bybit.dto.marketdata.BybitKlines;
import org.knowm.xchange.bybit.dto.marketdata.BybitOpenInterest;
import org.knowm.xchange.bybit.dto.marketdata.BybitOrderbook;
import org.knowm.xchange.bybit.dto.marketdata.BybitPublicTrade;
import org.knowm.xchange.bybit.dto.marketdata.BybitServerTime;
import org.knowm.xchange.bybit.dto.marketdata.instruments.BybitInstrumentInfo;
import org.knowm.xchange.bybit.dto.marketdata.instruments.BybitInstrumentsInfo;
import org.knowm.xchange.bybit.dto.marketdata.tickers.BybitTicker;
import org.knowm.xchange.bybit.dto.marketdata.tickers.BybitTickers;
import org.knowm.xchange.bybit.service.BybitException;

@Path("/v5/market")
@Produces(MediaType.APPLICATION_JSON)
public interface Bybit {

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/market/tickers">API</a>
   */
  @GET
  @Path("/tickers")
  BybitResult<BybitTickers<BybitTicker>> getTicker24h(
      @QueryParam("category") String category, @QueryParam("symbol") String symbol)
      throws IOException, BybitException;

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/market/orderbook">API</a>
   */
  @GET
  @Path("/orderbook")
  BybitResult<BybitOrderbook> getOrderbook(
      @QueryParam("category") String category,
      @QueryParam("symbol") String symbol,
      @QueryParam("limit") String limit)
      throws IOException, BybitException;

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/market/instrument">API</a>
   */
  @GET
  @Path("/instruments-info")
  BybitResult<BybitInstrumentsInfo<BybitInstrumentInfo>> getInstrumentsInfo(
      @QueryParam("category") String category,
      @QueryParam("limit") String limit,
      @QueryParam("cursor") String cursor)
      throws IOException, BybitException;

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/market/tickers">API</a>
   */
  @GET
  @Path("/tickers")
  BybitResult<BybitTickers<BybitTicker>> getTickers(@QueryParam("category") String category)
      throws IOException, BybitException;

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/market/history-fund-rate">API</a>
   */
  @GET
  @Path("/funding/history")
  BybitResult<BybitCategorizedPayload<BybitFundingRateHistoryRaw>> getFundingHistory(
      @QueryParam("category") String category,
      @QueryParam("symbol") String symbol,
      @QueryParam("startTime") Long startTime,
      @QueryParam("endTime") Long endTime,
      @QueryParam("limit") Integer limit)
      throws IOException, BybitException;

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/market/recent-trade">API</a>
   */
  @GET
  @Path("/public-trades")
  BybitResult<BybitCategorizedPayload<BybitPublicTrade>> getPublicTrades(
      @QueryParam("category") String category,
      @QueryParam("symbol") String symbol,
      @QueryParam("limit") String limit)
      throws IOException, BybitException;

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/market/time">API</a>
   */
  @GET
  @Path("/time")
  BybitResult<BybitServerTime> getServerTime() throws IOException, BybitException;

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/market/open-interest">API</a>
   */
  @GET
  @Path("/open-interest")
  BybitResult<BybitOpenInterest> getOpenInterest(
      @QueryParam("category") String category,
      @QueryParam("symbol") String symbol,
      @QueryParam("intervalTime") String intervalTime,
      @QueryParam("limit") String limit)
      throws IOException, BybitException;

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/market/kline">API</a>
   */
  @GET
  @Path("/kline")
  BybitResult<BybitKlines> getKlines(
      @QueryParam("category") String category,
      @QueryParam("symbol") String symbol,
      @QueryParam("interval") String interval,
      @QueryParam("start") Long start,
      @QueryParam("end") Long end,
      @QueryParam("limit") Integer limit)
      throws IOException, BybitException;
}
