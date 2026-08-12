package org.knowm.xchange.bitget.uta.v3.service;

import org.knowm.xchange.bitget.BitgetExchange;
import org.knowm.xchange.bitget.config.BitgetJacksonObjectMapperFactory;
import org.knowm.xchange.bitget.uta.v3.BitgetUtaV3;
import org.knowm.xchange.bitget.uta.v3.BitgetUtaV3Authenticated;
import org.knowm.xchange.client.ExchangeRestProxyBuilder;
import org.knowm.xchange.service.BaseExchangeService;
import org.knowm.xchange.service.BaseService;

/**
 * Shared wiring for the UTA v3 services: builds the public and authenticated rescu proxies plus the
 * HMAC signer and nonce factory from the exchange specification.
 *
 * <p>The digest requires the secret key; when credentials are absent (public-only usage) the
 * authenticated proxy is still constructed but calls fail fast with a clear error.
 */
public class BitgetUtaV3BaseService extends BaseExchangeService<BitgetExchange>
    implements BaseService {

  protected final String apiKey;
  protected final String passphrase;
  protected final BitgetUtaV3 bitgetUtaV3;
  protected final BitgetUtaV3Authenticated bitgetUtaV3Authenticated;
  protected final BitgetUtaV3Digest bitgetUtaV3Digest;

  public BitgetUtaV3BaseService(BitgetExchange exchange) {
    super(exchange);
    bitgetUtaV3 =
        ExchangeRestProxyBuilder.forInterface(
                BitgetUtaV3.class, exchange.getExchangeSpecification())
            .clientConfigCustomizer(
                clientConfig ->
                    clientConfig.setJacksonObjectMapperFactory(
                        new BitgetJacksonObjectMapperFactory()))
            .build();
    bitgetUtaV3Authenticated =
        ExchangeRestProxyBuilder.forInterface(
                BitgetUtaV3Authenticated.class, exchange.getExchangeSpecification())
            .clientConfigCustomizer(
                clientConfig ->
                    clientConfig.setJacksonObjectMapperFactory(
                        new BitgetJacksonObjectMapperFactory()))
            .build();

    apiKey = exchange.getExchangeSpecification().getApiKey();
    passphrase = exchange.getExchangeSpecification().getPassword();
    bitgetUtaV3Digest =
        BitgetUtaV3Digest.createInstance(exchange.getExchangeSpecification().getSecretKey());
  }
}
