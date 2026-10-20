package org.knowm.xchange.coinbase.cde;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.List;

/**
 * Documented institutional US CDE REST history, separate from Advanced Trade and website MDA.
 *
 * <p>Authentication uses separately provisioned CDE DCC credentials and caller-supplied request
 * headers. This binding does not discover credentials, sign requests, or substitute CDP JWTs.
 *
 * <p>No convenience transport factory is provided. Use a caller-owned authenticated transport with
 * credential-safe diagnostic logging: stock Rescu TRACE logging exposes request header values,
 * including the CDE key, passphrase and signature. The annotations here describe the wire contract;
 * they do not install header redaction in a transport supplied by the caller.
 */
@Path("/rest")
@Produces(MediaType.APPLICATION_JSON)
public interface CoinbaseCdeRest {

  /** Official CDE REST origin, independent of the Advanced Trade brokerage API host. */
  String REST_ORIGIN = "https://api.exchange.fairx.net";

  /**
   * Reads funding history for one CDE instrument and optional trading session date.
   *
   * <p>The official schema defines no pagination, finality discriminator, retention or funding-rate
   * unit description. Preserve its raw values rather than inferring those contracts from examples
   * or from the website source. A trading session date is not assumed to be a UTC calendar day.
   *
   * @param apiKey caller's DCC API key (not a CDP key name)
   * @param passphrase caller's DCC passphrase
   * @param signature caller-generated Base64 request signature; follow CDE authentication docs
   * @param timestamp caller's integer Unix timestamp in seconds, used in the signature
   * @param symbol CDE symbol, e.g. BIPZ30 (required)
   * @param tradingSessionDate YYYY-MM-DD trading session date; null uses the provider's today
   * @return raw historical observations including mark prices and event time
   * @throws IOException on transport or deserialization failure
   */
  @GET
  @Path("funding-rate")
  List<CoinbaseCdeHistoricalFundingRate> getHistoricalFundingRates(
      @HeaderParam("CB-ACCESS-KEY") String apiKey,
      @HeaderParam("CB-ACCESS-PASSPHRASE") String passphrase,
      @HeaderParam("CB-ACCESS-SIGN") String signature,
      @HeaderParam("CB-ACCESS-TIMESTAMP") long timestamp,
      @QueryParam("symbol") String symbol,
      @QueryParam("trading_session_date") String tradingSessionDate)
      throws IOException;
}
