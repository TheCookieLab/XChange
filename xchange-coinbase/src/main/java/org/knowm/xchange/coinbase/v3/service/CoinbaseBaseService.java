package org.knowm.xchange.coinbase.v3.service;

import java.util.Set;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.client.ExchangeRestProxyBuilder;
import org.knowm.xchange.coinbase.v3.Coinbase;
import org.knowm.xchange.coinbase.v3.CoinbaseAuthenticated;
import org.knowm.xchange.coinbase.v3.CoinbaseV3Authentication;
import org.knowm.xchange.coinbase.v3.CoinbaseV3Digest;
import org.knowm.xchange.service.BaseExchangeService;
import org.knowm.xchange.service.BaseService;
import si.mazi.rescu.ParamsDigest;

public class CoinbaseBaseService extends BaseExchangeService implements BaseService {

  protected final CoinbaseAuthenticated coinbaseAdvancedTrade;
  protected final Coinbase coinbasePublic;
  protected final ParamsDigest authTokenCreator;

  protected CoinbaseBaseService(Exchange exchange) {
    this(exchange, ExchangeRestProxyBuilder.forInterface(CoinbaseAuthenticated.class,
            exchange.getExchangeSpecification()).build(),
        CoinbaseV3Digest.createInstance(exchange.getExchangeSpecification().getApiKey(),
            exchange.getExchangeSpecification().getSecretKey()),
        createPublicClient(exchange));
  }

  public CoinbaseBaseService(Exchange exchange, CoinbaseAuthenticated coinbaseAdvancedTrade) {
    this(exchange, coinbaseAdvancedTrade,
        CoinbaseV3Digest.createInstance(exchange.getExchangeSpecification().getApiKey(),
            exchange.getExchangeSpecification().getSecretKey()),
        createPublicClient(exchange));
  }

  public CoinbaseBaseService(Exchange exchange, CoinbaseAuthenticated coinbaseAdvancedTrade,
      ParamsDigest authTokenCreator) {
    this(exchange, coinbaseAdvancedTrade, authTokenCreator, createPublicClient(exchange));
  }

  /**
   * Constructs the base service from a shared typed authentication component, so REST and
   * WebSocket transports use one validated key-material contract.
   */
  public CoinbaseBaseService(Exchange exchange, CoinbaseAuthenticated coinbaseAdvancedTrade,
      CoinbaseV3Authentication authentication) {
    this(exchange, coinbaseAdvancedTrade, authentication == null ? null : authentication.restDigest(),
        createPublicClient(exchange));
  }

  public CoinbaseBaseService(Exchange exchange, CoinbaseAuthenticated coinbaseAdvancedTrade,
      ParamsDigest authTokenCreator, Coinbase coinbasePublic) {
    super(exchange);
    this.coinbaseAdvancedTrade = coinbaseAdvancedTrade;
    this.authTokenCreator = authTokenCreator;
    this.coinbasePublic = coinbasePublic;
  }

  protected boolean hasAuthentication() {
    return authTokenCreator != null;
  }

  /** Hard safety bound on cursor-paginated loops (accounts, fills, orders). */
  protected static final int MAX_PAGINATION_PAGES = 200;

  /**
   * Guards a cursor-paginated loop against repeated cursors and runaway page counts, so high-level
   * iteration stays bounded and loop-safe instead of silently duplicating or spinning forever.
   *
   * @param cursor the cursor returned by the previous page (null/empty terminates)
   * @param seenCursors cursors already consumed by this iteration
   * @param page zero-based page index of the request that produced {@code cursor}
   * @param maxPages hard page bound
   * @param resource resource name for error messages (e.g. "accounts")
   * @return the cursor to use for the next request, or null when iteration must stop
   * @throws org.knowm.xchange.exceptions.ExchangeException when the server does not advance
   */
  protected static String advanceCursor(
      String cursor, Set<String> seenCursors, int page, int maxPages, String resource) {
    if (cursor == null || cursor.isEmpty()) {
      return null;
    }
    if (!seenCursors.add(cursor)) {
      throw new org.knowm.xchange.exceptions.ExchangeException(
          "Coinbase " + resource + " pagination returned a repeated cursor '" + cursor
              + "'; aborting to avoid an infinite loop");
    }
    if (page >= maxPages) {
      throw new org.knowm.xchange.exceptions.ExchangeException(
          "Coinbase " + resource + " pagination exceeded " + maxPages + " pages; aborting");
    }
    return cursor;
  }

  private static Coinbase createPublicClient(Exchange exchange) {
    if (exchange == null || exchange.getExchangeSpecification() == null) {
      return null;
    }
    return ExchangeRestProxyBuilder.forInterface(Coinbase.class, exchange.getExchangeSpecification())
        .build();
  }

}
