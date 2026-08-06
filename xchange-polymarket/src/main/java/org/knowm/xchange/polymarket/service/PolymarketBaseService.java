package org.knowm.xchange.polymarket.service;

import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.client.ExchangeRestProxyBuilder;
import org.knowm.xchange.polymarket.PolymarketExchange;
import org.knowm.xchange.polymarket.client.PolymarketClobAuthenticated;
import org.knowm.xchange.polymarket.client.PolymarketClobPublic;
import org.knowm.xchange.polymarket.client.PolymarketDataPublic;
import org.knowm.xchange.polymarket.client.PolymarketEip712Signer;
import org.knowm.xchange.polymarket.client.PolymarketGammaPublic;
import org.knowm.xchange.polymarket.client.PolymarketL1Digest;
import org.knowm.xchange.polymarket.client.PolymarketL2Digest;
import org.knowm.xchange.service.BaseExchangeService;
import org.knowm.xchange.service.BaseService;
import si.mazi.rescu.SynchronizedValueFactory;

/**
 * Base service wiring the CLOB, Gamma, and Data REST proxies plus the resolved credentials.
 *
 * <p>The CLOB proxies ride the exchange's real specification (its {@code sslUri} is the CLOB
 * host); Gamma and Data are separate hosts, so they are proxied from minimal host-only
 * specifications resolved through {@link PolymarketExchange#resolveUri}.
 */
public abstract class PolymarketBaseService extends BaseExchangeService<PolymarketExchange>
    implements BaseService {

  protected final PolymarketClobPublic clobPublic;
  protected final PolymarketClobAuthenticated clobAuthenticated;
  protected final PolymarketGammaPublic gammaPublic;
  protected final PolymarketDataPublic dataPublic;

  protected final String walletAddress;
  protected final String apiKey;
  protected final String passphrase;
  protected final PolymarketL1Digest l1Digest;
  protected final PolymarketL2Digest l2Digest;
  protected final PolymarketEip712Signer orderSigner;

  protected PolymarketBaseService(PolymarketExchange exchange) {
    super(exchange);
    ExchangeSpecification spec = exchange.getExchangeSpecification();
    clobPublic = ExchangeRestProxyBuilder.forInterface(PolymarketClobPublic.class, spec).build();
    clobAuthenticated =
        ExchangeRestProxyBuilder.forInterface(PolymarketClobAuthenticated.class, spec).build();
    gammaPublic =
        ExchangeRestProxyBuilder.forInterface(
                PolymarketGammaPublic.class,
                hostSpec(
                    spec,
                    exchange.resolveUri(
                        PolymarketExchange.PARAM_GAMMA_URI, PolymarketExchange.GAMMA_URI)))
            .build();
    dataPublic =
        ExchangeRestProxyBuilder.forInterface(
                PolymarketDataPublic.class,
                hostSpec(
                    spec,
                    exchange.resolveUri(
                        PolymarketExchange.PARAM_DATA_URI, PolymarketExchange.DATA_URI)))
            .build();

    Object privateKey =
        spec.getExchangeSpecificParametersItem(PolymarketExchange.PARAM_PRIVATE_KEY);
    String privateKeyHex = privateKey == null ? null : privateKey.toString();
    l1Digest = PolymarketL1Digest.createInstance(privateKeyHex);
    l2Digest = PolymarketL2Digest.createInstance(spec.getSecretKey());
    orderSigner =
        privateKeyHex == null || privateKeyHex.isBlank()
            ? null
            : PolymarketEip712Signer.fromPrivateKeyHex(privateKeyHex);

    String configuredAddress = spec.getUserName();
    walletAddress =
        configuredAddress == null || configuredAddress.isBlank()
            ? (orderSigner == null ? null : orderSigner.getAddress())
            : configuredAddress;
    apiKey = spec.getApiKey();
    passphrase = spec.getPassword();
  }

  private static ExchangeSpecification hostSpec(ExchangeSpecification base, String sslUri) {
    ExchangeSpecification spec = new ExchangeSpecification(base.getExchangeClass());
    spec.setSslUri(sslUri);
    return spec;
  }

  /**
   * Seconds-resolution timestamp factory backing the signed {@code POLY_TIMESTAMP} header.
   * Polymarket signs unix seconds, unlike the millisecond nonces used elsewhere.
   */
  protected SynchronizedValueFactory<Long> timestampSecondsFactory() {
    return () -> System.currentTimeMillis() / 1000L;
  }
}
