package org.knowm.xchange.coinbase.v2;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import org.knowm.xchange.coinbase.v2.dto.CoinbaseException;
import org.knowm.xchange.coinbase.v2.dto.marketdata.CoinbaseCurrencyData;
import org.knowm.xchange.coinbase.v2.dto.marketdata.CoinbaseExchangeRateData;
import org.knowm.xchange.coinbase.v2.dto.marketdata.CoinbasePriceData;
import org.knowm.xchange.coinbase.v2.dto.marketdata.CoinbaseTimeData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coinbase API v2 public endpoints.
 *
 * @deprecated The Coinbase v2 API provides retail pricing information (buy/sell quotes) but lacks
 * trading functionality. Authenticated endpoints no longer work due to changes in Coinbase's
 * authentication mechanism. For full trading functionality with order book access, market data, and
 * order placement, use {@link org.knowm.xchange.coinbase.v3.CoinbaseExchange} with the Coinbase
 * Advanced Trade API instead.
 */
@Deprecated
@Path("/v2")
@Produces(MediaType.APPLICATION_JSON)
public interface Coinbase {

  Logger LOG = LoggerFactory.getLogger(Coinbase.class.getPackage().getName());

  /**
   * All API calls should be made with a CB-VERSION header which guarantees that your call is using
   * the correct API version. <a
   * href="https://developers.coinbase.com/api/v2#versioning">developers.coinbase.com/api/v2#versioning</a>
   */
  String CB_VERSION = "CB-VERSION";

  String CB_VERSION_VALUE = "2018-04-08";

  @GET
  @Path("currencies")
  CoinbaseCurrencyData getCurrencies(@HeaderParam(CB_VERSION) String apiVersion)
      throws IOException, CoinbaseException;

  @GET
  @Path("exchange-rates")
  CoinbaseExchangeRateData getCurrencyExchangeRates(@HeaderParam(CB_VERSION) String apiVersion)
      throws IOException, CoinbaseException;

  @GET
  @Path("prices/{pair}/buy")
  CoinbasePriceData getBuyPrice(@HeaderParam(CB_VERSION) String apiVersion,
      @PathParam("pair") String pair) throws IOException, CoinbaseException;

  @GET
  @Path("prices/{pair}/sell")
  CoinbasePriceData getSellPrice(@HeaderParam(CB_VERSION) String apiVersion,
      @PathParam("pair") String pair) throws IOException, CoinbaseException;

  @GET
  @Path("prices/{pair}/spot")
  CoinbasePriceData getSpotRate(@HeaderParam(CB_VERSION) String apiVersion,
      @PathParam("pair") String pair) throws IOException, CoinbaseException;

  @GET
  @Path("prices/{pair}/spot")
  CoinbasePriceData getHistoricalSpotRate(@HeaderParam(CB_VERSION) String apiVersion,
      @PathParam("pair") String pair, @QueryParam("date") String date)
      throws IOException, CoinbaseException;

  @GET
  @Path("time")
  CoinbaseTimeData getTime(@HeaderParam(CB_VERSION) String apiVersion)
      throws IOException, CoinbaseException;
}
