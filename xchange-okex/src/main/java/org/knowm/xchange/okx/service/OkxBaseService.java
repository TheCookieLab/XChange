package org.knowm.xchange.okx.service;

import static org.knowm.xchange.okx.OkxExchange.PARAM_PASSPHRASE;
import static org.knowm.xchange.okx.OkxExchange.PARAM_SIMULATED;

import java.util.Date;
import org.knowm.xchange.client.ExchangeRestProxyBuilder;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.InternalServerException;
import org.knowm.xchange.exceptions.RateLimitExceededException;
import org.knowm.xchange.okx.Okx;
import org.knowm.xchange.okx.OkxAuthenticated;
import org.knowm.xchange.okx.OkxExchange;
import org.knowm.xchange.okx.OkxRedaction;
import org.knowm.xchange.okx.dto.OkxException;
import org.knowm.xchange.service.BaseResilientExchangeService;
import org.knowm.xchange.service.BaseService;
import org.knowm.xchange.utils.DateUtils;
import si.mazi.rescu.ParamsDigest;

/** Author: Max Gao (gaamox@tutanota.com) Created: 08-06-2021 */
public class OkxBaseService extends BaseResilientExchangeService<OkxExchange>
    implements BaseService {

  protected final Okx okx;
  protected final OkxAuthenticated okxAuthenticated;
  protected final ParamsDigest signatureCreator;

  protected final String apiKey;
  protected final String passphrase;
  protected final String secretKey;

  public OkxBaseService(OkxExchange exchange, ResilienceRegistries resilienceRegistries) {
    super(exchange, resilienceRegistries);

    okx =
        ExchangeRestProxyBuilder.forInterface(Okx.class, exchange.getExchangeSpecification())
            .build();
    okxAuthenticated =
        ExchangeRestProxyBuilder.forInterface(
                OkxAuthenticated.class, exchange.getExchangeSpecification())
            .build();
    signatureCreator = OkxDigest.createInstance(exchange.getExchangeSpecification().getSecretKey());
    apiKey = exchange.getExchangeSpecification().getApiKey();
    secretKey = exchange.getExchangeSpecification().getSecretKey();
    passphrase =
        (String)
            exchange.getExchangeSpecification().getExchangeSpecificParametersItem(PARAM_PASSPHRASE);
  }

  /**
   * Immutable snapshot of the authentication parameters shared by every authenticated OKX v5 REST
   * call: the API key, the HMAC signature creator, the ISO-8601 request timestamp, the passphrase,
   * and the demo-trading flag.
   *
   * @param apiKey the configured API key ({@code OK-ACCESS-KEY})
   * @param signature the HMAC-SHA-256 signature creator ({@code OK-ACCESS-SIGN})
   * @param timestamp the ISO-8601 request timestamp ({@code OK-ACCESS-TIMESTAMP})
   * @param passphrase the configured API passphrase ({@code OK-ACCESS-PASSPHRASE})
   * @param simulatedTrading the demo-trading flag ({@code X-SIMULATED-TRADING}), or {@code null}
   */
  protected record OkxAuthParams(
      String apiKey,
      ParamsDigest signature,
      String timestamp,
      String passphrase,
      String simulatedTrading) {}

  /**
   * Builds a fresh authentication snapshot for one request. A single {@link Date} is minted per
   * call so the {@code OK-ACCESS-TIMESTAMP} used for signing always equals the timestamp sent to
   * the API.
   *
   * @return the authentication parameters for exactly one request
   */
  protected OkxAuthParams authParams() {
    return new OkxAuthParams(
        apiKey,
        signatureCreator,
        DateUtils.toUTCISODateString(new Date()),
        passphrase,
        simulatedTrading());
  }

  /**
   * Returns the configured demo-trading flag ({@code X-SIMULATED-TRADING}), or {@code null} when
   * not configured.
   */
  protected String simulatedTrading() {
    return (String)
        exchange.getExchangeSpecification().getExchangeSpecificParametersItem(PARAM_SIMULATED);
  }

  /**
   * Redacts the configured API key, secret key, and passphrase from a string destined for logs or
   * exception messages.
   *
   * @param value the text to redact; may be {@code null}
   * @return the redacted text, or {@code null} when {@code value} is {@code null}
   */
  protected String redact(String value) {
    return OkxRedaction.mask(value, apiKey, secretKey, passphrase);
  }

  /** <a href="https://www.okx.com/docs-v5/en/#error-code">...</a> * */
  protected ExchangeException handleError(OkxException exception) {
    OkxException sanitized = exception.withRedactedMessage(apiKey, secretKey, passphrase);
    if (sanitized.getMessage().contains("Requests too frequent")) {
      return new RateLimitExceededException(sanitized);
    } else if (sanitized.getMessage().contains("System error")) {
      return new InternalServerException(sanitized);
    } else {
      return new ExchangeException(sanitized);
    }
  }
}
