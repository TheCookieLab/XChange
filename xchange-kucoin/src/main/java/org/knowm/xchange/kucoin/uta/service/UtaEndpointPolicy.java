package org.knowm.xchange.kucoin.uta.service;

import java.net.MalformedURLException;
import java.net.URL;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.kucoin.KucoinExchange;

/**
 * Immutable UTA endpoint policy.
 *
 * <p>Centralizes REST base resolution (production versus sandbox), the shared UTA path prefix, and
 * the server-time drift probe used by trading operations. The policy is derived once from the
 * exchange specification and never mutated afterwards.
 */
public final class UtaEndpointPolicy {

  /** Path prefix shared by every UTA REST endpoint. */
  public static final String UTA_API_PREFIX = "api/ua/v1";

  private final String baseUri;
  private final String host;
  private final boolean sandbox;

  private UtaEndpointPolicy(String baseUri, String host, boolean sandbox) {
    this.baseUri = baseUri;
    this.host = host;
    this.sandbox = sandbox;
  }

  public static UtaEndpointPolicy from(ExchangeSpecification specification) {
    String sslUri = specification.getSslUri();
    boolean sandbox =
        specification.getExchangeSpecificParametersItem(KucoinExchange.USE_SANDBOX) != null
            && Boolean.TRUE.equals(
                specification.getExchangeSpecificParametersItem(KucoinExchange.USE_SANDBOX));
    if (sslUri == null || sslUri.isEmpty()) {
      sslUri = sandbox ? KucoinExchange.SANDBOX_URI : KucoinExchange.PROD_URI;
    }
    try {
      URL url = new URL(sslUri);
      return new UtaEndpointPolicy(sslUri, url.getHost(), sandbox);
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException("Invalid KuCoin UTA base URI: " + sslUri, e);
    }
  }

  public String getBaseUri() {
    return baseUri;
  }

  public String getHost() {
    return host;
  }

  public boolean isSandbox() {
    return sandbox;
  }

  /** @return the full path for a UTA endpoint, e.g. {@code api/ua/v1/unified/order/place}. */
  public static String utaPath(String path) {
    return UTA_API_PREFIX + path;
  }
}
