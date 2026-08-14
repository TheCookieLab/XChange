package org.knowm.xchange.mexc.v3;

import org.knowm.xchange.BaseExchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.mexc.v3.config.MexcV3Configuration;

/**
 * Common base for MEXC Spot v3 exchanges.
 *
 * <p>Host parameters are concluded from the configured REST base URL so overrides such as
 * {@code MexcV3_RestBaseUrl} automatically keep {@link ExchangeSpecification#getHost()} and
 * {@link ExchangeSpecification#getPort()} coherent with the resolved endpoint.
 */
public abstract class MexcV3BaseExchange extends BaseExchange {

  @Override
  public ExchangeSpecification getDefaultExchangeSpecification() {
    ExchangeSpecification spec = new ExchangeSpecification(this.getClass());
    spec.setSslUri(MexcV3Configuration.REST_BASE_URL);
    spec.setHost("api.mexc.com");
    spec.setPort(443);
    spec.setExchangeName("MEXC");
    return spec;
  }

  /** Adjust host parameters depending on the configured REST base URL. */
  protected void concludeHostParams(ExchangeSpecification exchangeSpecification) {
    try {
      java.net.URI uri = new java.net.URI(exchangeSpecification.getSslUri());
      exchangeSpecification.setHost(uri.getHost());
      int port = uri.getPort();
      exchangeSpecification.setPort(port == -1 ? (uri.getScheme().equals("https") ? 443 : 80) : port);
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Invalid SSL URI for MEXC Spot v3: " + exchangeSpecification.getSslUri(), e);
    }
  }
}
