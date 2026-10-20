package org.knowm.xchange.coinbase.cde;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import si.mazi.rescu.RestProxyFactory;

/**
 * Public website market data for US Coinbase Derivatives Exchange (CDE).
 *
 * <p>This is the website MDA interface observed on 2026-10-20, separate from Advanced Trade,
 * institutional CDE REST and international derivatives. It has no published compatibility or
 * retention guarantee. Its observations have unverified upstream precision, finality and funding
 * boundary meaning; they must not be silently promoted to exact settled funding obligations.
 */
@Path("/api/mda/v1/instruments/CDE")
@Produces(MediaType.APPLICATION_JSON)
public interface CoinbaseCdeMarketData {

  /** Official public website origin; no account credentials are accepted by this client. */
  String WEBSITE_ORIGIN = "https://www.coinbase.com";

  /**
   * Creates a public-only client without using an exchange specification or account credentials.
   *
   * @return client for the website's CDE data source
   */
  static CoinbaseCdeMarketData create() {
    return RestProxyFactory.createProxy(CoinbaseCdeMarketData.class, WEBSITE_ORIGIN);
  }

  /**
   * Reads one page of published funding observations, preserving source precision and metadata.
   *
   * <p>The website requests 2160 rows. Offset pagination was independently observed to work beyond
   * that window. No date filter, maximum retention, total count, stable snapshot or finality flag is
   * established. Offset pages can move as new observations arrive; callers must reconcile overlap
   * by symbol and event time and must not infer zero rates from missing rows. Bound each traversal
   * explicitly and share fetched pages across consumers of the same market.
   *
   * @param symbol CDE symbol from the website catalog, e.g. BIPZ30; not an Advanced Trade product id
   * @param resultLimit requested page size (the website uses 2160)
   * @param resultOffset zero-based offset (the initial page uses 0)
   * @return raw page, without annualization, percent conversion or settlement inference
   * @throws IOException on transport or deserialization failure
   */
  @GET
  @Path("{symbol}/funding")
  CoinbaseCdeFundingPage getFundingHistory(
      @PathParam("symbol") String symbol,
      @QueryParam("result_limit") int resultLimit,
      @QueryParam("result_offset") int resultOffset)
      throws IOException;
}
