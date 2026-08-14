package org.knowm.xchange.okx.service;

import org.knowm.xchange.client.ExchangeRestProxyBuilder;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.InternalServerException;
import org.knowm.xchange.exceptions.RateLimitExceededException;
import org.knowm.xchange.okx.Okx;
import org.knowm.xchange.okx.OkxAuthenticated;
import org.knowm.xchange.okx.OkxExchange;
import org.knowm.xchange.okx.dto.OkxException;
import org.knowm.xchange.service.BaseResilientExchangeService;
import org.knowm.xchange.service.BaseService;
import si.mazi.rescu.ParamsDigest;

/** Author: Max Gao (gaamox@tutanota.com) Created: 08-06-2021 */
public class OkxBaseService extends BaseResilientExchangeService<OkxExchange>
    implements BaseService {

  protected final Okx okx;
  protected final OkxAuthenticated okxAuthenticated;
  protected final ParamsDigest signatureCreator;

  protected final String apiKey;
  protected final String passphrase;

  public OkxBaseService(OkxExchange exchange, ResilienceRegistries resilienceRegistries) {
    super(exchange, resilienceRegistries);

    okx =
        ExchangeRestProxyBuilder.forInterface(Okx.class, exchange.getExchangeSpecification())
            .build();
    okxAuthenticated =
        ExchangeRestProxyBuilder.forInterface(
                OkxAuthenticated.class, exchange.getExchangeSpecification())
            .build();
    signatureCreator =
        OkxDigest.createInstance(exchange.getExchangeSpecification().getSecretKey());
    apiKey = exchange.getExchangeSpecification().getApiKey();
    passphrase =
        (String)
            exchange.getExchangeSpecification().getExchangeSpecificParametersItem("passphrase");
  }

  /** <a href="https://www.okx.com/docs-v5/en/#error-code">...</a> * */
  protected ExchangeException handleError(OkxException exception) {
    if (exception.getMessage().contains("Requests too frequent")) {
      return new RateLimitExceededException(exception);
    } else if (exception.getMessage().contains("System error")) {
      return new InternalServerException(exception);
    } else {
      return new ExchangeException(exception);
    }
  }
}
