package org.knowm.xchange.mexc.v3.service;

import java.util.concurrent.TimeUnit;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.client.ExchangeRestProxyBuilder;
import org.knowm.xchange.mexc.v3.MexcV3Authenticated;
import org.knowm.xchange.mexc.v3.MexcV3Exchange;
import org.knowm.xchange.mexc.v3.MexcV3MarketDataRaw;
import org.knowm.xchange.mexc.v3.auth.MexcV3Signing;
import org.knowm.xchange.service.BaseExchangeService;
import org.knowm.xchange.service.BaseService;
import org.knowm.xchange.utils.nonce.CurrentTimeIncrementalNonceFactory;
import si.mazi.rescu.ParamsDigest;
import si.mazi.rescu.SynchronizedValueFactory;

/**
 * Shared transport for MEXC Spot v3 services.
 *
 * <p>Creates the rescu proxies (public market data and authenticated) against the configured REST
 * base URL and exposes the signing primitives used by the authenticated raw surface: the API key
 * (sent as {@code X-MEXC-APIKEY}), the HMAC-SHA256 {@link ParamsDigest}, a monotonic millisecond
 * timestamp factory for the signed {@code timestamp} parameter, and the configured
 * {@code recvWindow}.
 */
public class MexcV3BaseService extends BaseExchangeService implements BaseService {

  protected final MexcV3MarketDataRaw mexcV3MarketData;
  protected final MexcV3Authenticated mexcV3Authenticated;
  protected final String apiKey;
  protected final String secretKey;
  protected final ParamsDigest signatureCreator;
  protected final SynchronizedValueFactory<Long> timestampFactory =
      new CurrentTimeIncrementalNonceFactory(TimeUnit.MILLISECONDS);
  protected final long recvWindowMs;

  protected MexcV3BaseService(Exchange exchange) {
    super(exchange);
    this.apiKey = exchange.getExchangeSpecification().getApiKey();
    this.secretKey = exchange.getExchangeSpecification().getSecretKey();
    this.signatureCreator = MexcV3Signing.createDigest(secretKey);
    this.recvWindowMs = ((MexcV3Exchange) exchange).getConfiguration().getRecvWindowMs();
    this.mexcV3MarketData =
        ExchangeRestProxyBuilder.forInterface(
                MexcV3MarketDataRaw.class, exchange.getExchangeSpecification())
            .build();
    this.mexcV3Authenticated =
        ExchangeRestProxyBuilder.forInterface(
                MexcV3Authenticated.class, exchange.getExchangeSpecification())
            .build();
  }
}
