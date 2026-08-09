package org.knowm.xchange.krakenfutures.service;

import java.io.IOException;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.client.ExchangeRestProxyBuilder;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.krakenfutures.KrakenFuturesAuthenticated;
import org.knowm.xchange.krakenfutures.dto.KrakenFuturesResult;
import org.knowm.xchange.krakenfutures.dto.trade.KrakenFuturesOpenPositions;
import org.knowm.xchange.service.BaseExchangeService;
import org.knowm.xchange.service.BaseService;
import si.mazi.rescu.ParamsDigest;

/**
 * @author Jean-Christophe Laruelle
 */
public class KrakenFuturesBaseService extends BaseExchangeService implements BaseService {

  protected KrakenFuturesAuthenticated krakenFuturesAuthenticated;
  protected ParamsDigest signatureCreator;

  /**
   * Constructor
   *
   * @param exchange of KrakenFutures
   */
  public KrakenFuturesBaseService(Exchange exchange) {

    super(exchange);

    krakenFuturesAuthenticated =
        ExchangeRestProxyBuilder.forInterface(
                KrakenFuturesAuthenticated.class, exchange.getExchangeSpecification())
            .build();
    signatureCreator =
        KrakenFuturesDigest.createInstance(exchange.getExchangeSpecification().getSecretKey());
  }

  public KrakenFuturesOpenPositions getKrakenFuturesOpenPositions() throws IOException {
    KrakenFuturesOpenPositions openPositions =
        krakenFuturesAuthenticated.openPositions(
            exchange.getExchangeSpecification().getApiKey(),
            signatureCreator,
            exchange.getNonceFactory());
    checkSuccess(openPositions, "getKrakenFuturesOpenPositions");
    return openPositions;
  }

  /**
   * Verifies a provider result and throws a structured {@link KrakenFuturesException} carrying
   * domain, operation, retry classification, and redacted error details when it is unsuccessful.
   *
   * @param result provider result, may be {@code null}
   * @param operation failing operation
   */
  protected void checkSuccess(KrakenFuturesResult result, String operation) {
    if (result != null && result.isSuccess()) {
      return;
    }
    String error = result == null ? "missing result" : result.getError();
    throw new KrakenFuturesException(
        "futures", operation, KrakenFuturesException.classify(error), new String[] {error});
  }
}
