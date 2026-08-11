package org.knowm.xchange.kucoin.uta.service;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import org.knowm.xchange.kucoin.uta.dto.UtaInstrumentList;
import org.knowm.xchange.kucoin.uta.dto.UtaKlineList;
import org.knowm.xchange.kucoin.uta.dto.UtaOrderBook;
import org.knowm.xchange.kucoin.uta.dto.UtaTickerList;
import org.knowm.xchange.kucoin.uta.dto.UtaTradeList;

/** UTA public market-data REST endpoints. */
@Path("api/ua/v1")
@Produces(MediaType.APPLICATION_JSON)
public interface UtaMarketAPI {

  /** Instrument catalog for SPOT or FUTURES; no authentication required. */
  @GET
  @Path("market/instrument")
  UtaResponse<UtaInstrumentList> getInstruments(
      @QueryParam("tradeType") String tradeType, @QueryParam("symbol") String symbol)
      throws IOException;

  /** Tickers for all symbols or a single symbol. */
  @GET
  @Path("market/ticker")
  UtaResponse<UtaTickerList> getTickers(
      @QueryParam("tradeType") String tradeType, @QueryParam("symbol") String symbol)
      throws IOException;

  /** Aggregated order book; {@code limit} is 20, 100, or FULL. */
  @GET
  @Path("market/orderbook")
  UtaResponse<UtaOrderBook> getOrderBook(
      @QueryParam("tradeType") String tradeType,
      @QueryParam("symbol") String symbol,
      @QueryParam("limit") String limit,
      @QueryParam("rpiFilter") Integer rpiFilter)
      throws IOException;

  /** Candlesticks; {@code startAt}/{@code endAt} are seconds. */
  @GET
  @Path("market/kline")
  UtaResponse<UtaKlineList> getKlines(
      @QueryParam("tradeType") String tradeType,
      @QueryParam("symbol") String symbol,
      @QueryParam("interval") String interval,
      @QueryParam("startAt") Long startAt,
      @QueryParam("endAt") Long endAt)
      throws IOException;

  /** Latest 100 public trades. */
  @GET
  @Path("market/trade")
  UtaResponse<UtaTradeList> getTrades(
      @QueryParam("tradeType") String tradeType, @QueryParam("symbol") String symbol)
      throws IOException;
}
