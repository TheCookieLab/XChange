package org.knowm.xchange.gateio.service;

import jakarta.ws.rs.HeaderParam;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.client.ExchangeRestProxyBuilder;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.gateio.Gateio;
import org.knowm.xchange.gateio.GateioExchange;
import org.knowm.xchange.gateio.GateioV4Authenticated;
import org.knowm.xchange.gateio.config.GateioJacksonObjectMapperFactory;
import org.knowm.xchange.service.BaseResilientExchangeService;
import org.knowm.xchange.service.BaseService;
import si.mazi.rescu.ParamsDigest;
import si.mazi.rescu.clients.HttpConnectionType;

public class GateioBaseService extends BaseResilientExchangeService<GateioExchange> implements BaseService {

  protected final String apiKey;
  protected final Gateio gateio;
  protected final GateioV4Authenticated gateioV4Authenticated;
  protected final ParamsDigest gateioV4ParamsDigest;

  public GateioBaseService(GateioExchange exchange, ResilienceRegistries resilienceRegistries) {
    super(exchange, resilienceRegistries);
    ExchangeSpecification.ResilienceSpecification resilience =
        exchange.getExchangeSpecification().getResilience();
    boolean rateLimited =
        resilience != null
            && resilience.isRateLimiterEnabled()
            && resilience.getRateLimitPolicy() != null;
    gateio =
        ExchangeRestProxyBuilder.forInterface(Gateio.class, exchange.getExchangeSpecification())
            .clientConfigCustomizer(
                clientConfig -> {
                  clientConfig.setJacksonObjectMapperFactory(
                      new GateioJacksonObjectMapperFactory());
                  clientConfig.addDefaultParam(HeaderParam.class, "X-Gate-Size-Decimal", "1");
                }
            )
            .build();
    apiKey = exchange.getExchangeSpecification().getApiKey();

    gateioV4Authenticated =
        ExchangeRestProxyBuilder.forInterface(
                GateioV4Authenticated.class, exchange.getExchangeSpecification())
            .clientConfigCustomizer(
                clientConfig -> {
                  clientConfig.setJacksonObjectMapperFactory(
                      new GateioJacksonObjectMapperFactory());
                  // The amend-order endpoint uses PATCH, which the default HttpURLConnection
                  // transport rejects. With the core rate limiter enabled the core transport
                  // carries it; only a limiter-less specification needs rescu's Apache client
                  // (org.apache.httpcomponents:httpclient), which the core boundary refuses.
                  if (!rateLimited) {
                    clientConfig.setConnectionType(HttpConnectionType.apache);
                  }
                  clientConfig.addDefaultParam(HeaderParam.class, "X-Gate-Size-Decimal", "1");
                })
            .build();

    gateioV4ParamsDigest =
        GateioV4Digest.createInstance(exchange.getExchangeSpecification().getSecretKey());
  }
}
