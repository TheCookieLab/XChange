package org.knowm.xchange.kucoin.uta;

import com.google.common.base.Strings;
import java.io.IOException;
import java.util.Objects;
import org.knowm.xchange.client.ExchangeRestProxyBuilder;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.kucoin.KucoinApiMode;
import org.knowm.xchange.kucoin.KucoinExchange;
import org.knowm.xchange.kucoin.uta.service.UtaApiException;
import org.knowm.xchange.kucoin.uta.service.UtaExceptionClassifier;
import org.knowm.xchange.kucoin.uta.service.UtaApiException.RetryClassification;
import org.knowm.xchange.service.BaseResilientExchangeService;
import org.knowm.xchange.kucoin.uta.service.UtaCommonAPI;
import org.knowm.xchange.kucoin.uta.service.UtaEndpointPolicy;
import org.knowm.xchange.kucoin.uta.service.UtaConstants;
import org.knowm.xchange.kucoin.uta.service.UtaDigest;
import si.mazi.rescu.SynchronizedValueFactory;

/**
 * Base service for the UTA (Unified Trading Account) implementation.
 *
 * <p>Owns UTA REST proxies, authentication material, mode/domain context, and the server-time
 * drift probe. Classic services and UTA services never share authentication state: UTA signing and
 * passphrase handling follow the UTA generation rules.
 */
public abstract class UtaBaseService extends BaseResilientExchangeService<KucoinExchange> {

  protected final String apiKey;
  protected final UtaDigest digest;
  protected final SynchronizedValueFactory<Long> nonceFactory;
  /** Encrypted passphrase for the {@code KC-API-PASSPHRASE} header. */
  protected final String encryptedPassphrase;

  protected final UtaEndpointPolicy endpointPolicy;

  protected UtaBaseService(KucoinExchange exchange, ResilienceRegistries resilienceRegistries) {
    super(exchange, resilienceRegistries);
    this.endpointPolicy = UtaEndpointPolicy.from(exchange.getExchangeSpecification());
    this.digest = UtaDigest.createInstance(exchange.getExchangeSpecification().getSecretKey());
    this.apiKey = exchange.getExchangeSpecification().getApiKey();
    this.encryptedPassphrase =
        UtaDigest.encryptPassphrase(
            (String)
                exchange
                    .getExchangeSpecification()
                    .getExchangeSpecificParametersItem("passphrase"),
            exchange.getExchangeSpecification().getSecretKey());
    this.nonceFactory = exchange.getNonceFactory();
  }

  protected <T> T service(Class<T> clazz) {
    return ExchangeRestProxyBuilder.forInterface(clazz, exchange.getExchangeSpecification())
        .build();
  }

  protected void checkAuthenticated() {
    if (Strings.isNullOrEmpty(this.apiKey)) {
      throw unauthorized("Missing API key");
    }
    if (this.digest == null) {
      throw unauthorized("Missing secret key");
    }
    if (Strings.isNullOrEmpty(this.encryptedPassphrase)) {
      throw unauthorized("Missing passphrase");
    }
  }

  private UtaApiException unauthorized(String message) {
    return new UtaApiException(
        message,
        null,
        KucoinApiMode.UTA,
        UtaDomains.COMMON,
        null,
        null,
        null,
        null,
        RetryClassification.NON_RETRYABLE);
  }

  /**
   * Probes the server clock and returns the local-to-server offset in milliseconds.
   *
   * <p>UTA signing uses millisecond timestamps; a large positive offset indicates the local clock
   * is behind and requests will be rejected. The probe is read-only and safe to call before trading
   * operations.
   *
   * @return offset = serverTimeMillis - localTimeMillis
   * @throws IOException on transport failure
   */
  public long serverTimeOffsetMillis() throws IOException {
    UtaCommonAPI common = service(UtaCommonAPI.class);
    Long serverTime =
        UtaExceptionClassifier.callOrThrow(
            common::getServerTime, UtaDomains.COMMON, "GET /api/v1/timestamp");
    return serverTime - System.currentTimeMillis();
  }

  @Override
  public String toString() {
    return "UtaBaseService{host='" + endpointPolicy.getHost() + "'}";
  }

  protected static void requireNonNull(Object value, String name) {
    Objects.requireNonNull(value, name);
  }
}
