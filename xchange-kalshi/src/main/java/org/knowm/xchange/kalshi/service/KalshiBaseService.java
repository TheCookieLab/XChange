package org.knowm.xchange.kalshi.service;

import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.client.ExchangeRestProxyBuilder;
import org.knowm.xchange.kalshi.KalshiExchange;
import org.knowm.xchange.kalshi.client.KalshiAuthenticated;
import org.knowm.xchange.kalshi.client.KalshiDigest;
import org.knowm.xchange.kalshi.client.KalshiPublic;
import org.knowm.xchange.service.BaseExchangeService;
import org.knowm.xchange.service.BaseService;
import si.mazi.rescu.SynchronizedValueFactory;

/** Base service wiring the public and authenticated Kalshi REST proxies. */
public abstract class KalshiBaseService extends BaseExchangeService<KalshiExchange>
    implements BaseService {

  protected final KalshiPublic kalshiPublic;
  protected final KalshiAuthenticated kalshiAuthenticated;
  protected final String apiKey;
  protected final KalshiDigest digest;

  protected KalshiBaseService(KalshiExchange exchange) {
    super(exchange);
    ExchangeSpecification spec = exchange.getExchangeSpecification();
    kalshiPublic = ExchangeRestProxyBuilder.forInterface(KalshiPublic.class, spec).build();
    kalshiAuthenticated =
        ExchangeRestProxyBuilder.forInterface(KalshiAuthenticated.class, spec).build();
    apiKey = spec.getApiKey();
    digest = KalshiDigest.createInstance(spec.getSecretKey());
  }

  /** Millisecond timestamp factory backing the signed {@code KALSHI-ACCESS-TIMESTAMP} header. */
  protected SynchronizedValueFactory<Long> timestampFactory() {
    return exchange.getNonceFactory();
  }
}
