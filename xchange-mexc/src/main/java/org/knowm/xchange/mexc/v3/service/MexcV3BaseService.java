package org.knowm.xchange.mexc.v3.service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.client.ExchangeRestProxyBuilder;
import org.knowm.xchange.mexc.v3.MexcV3Authenticated;
import org.knowm.xchange.mexc.v3.MexcV3Exchange;
import org.knowm.xchange.mexc.v3.MexcV3MarketDataRaw;
import org.knowm.xchange.mexc.v3.auth.MexcV3Signing;
import org.knowm.xchange.mexc.v3.client.MexcV3Exception;
import org.knowm.xchange.mexc.v3.client.MexcV3Redactor;
import org.knowm.xchange.mexc.v3.client.ReplaySafety;
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

  /**
   * Executes a provider call under an explicit replay-safety policy.
   *
   * <p>Provider error envelopes are adapted to the XChange exception hierarchy. Transport failures
   * ({@link IOException} that never produced a provider response) are rethrown as-is for {@link
   * ReplaySafety#READ} and {@link ReplaySafety#IDEMPOTENT_CANCELLATION} calls, where a retry cannot
   * double-apply the operation. For {@link ReplaySafety#PLACEMENT} calls the outcome is unknown —
   * the exchange may have accepted the order despite the failed transport round-trip — so the
   * failure surfaces as an explicitly ambiguous {@link MexcV3Exception} (classified {@link
   * org.knowm.xchange.mexc.v3.client.RetryClassification#AMBIGUOUS}) that instructs callers to
   * reconcile by order id rather than replay the placement.
   */
  protected <T> T execute(MexcV3Call<T> call, ReplaySafety replaySafety) throws IOException {
    return execute(call, replaySafety, null);
  }

  /**
   * Executes a provider call with replay-safety classification, enriching ambiguous placement
   * failures with the client order id the placement was sent under.
   *
   * @param clientOrderId the {@code newClientOrderId} the placement carried, or {@code null} for
   *     non-placement calls; surfaced in the ambiguous failure so callers can reconcile.
   */
  protected <T> T execute(MexcV3Call<T> call, ReplaySafety replaySafety, String clientOrderId)
      throws IOException {
    try {
      return call.call();
    } catch (MexcV3Exception e) {
      throw e.adapt();
    } catch (IOException e) {
      if (replaySafety == ReplaySafety.PLACEMENT) {
        throw MexcV3Exception.ambiguous(
            "MEXC Spot v3 placement outcome is ambiguous after transport failure ("
                + MexcV3Redactor.sanitize(e.getMessage())
                + "); reconcile by client order id "
                + clientOrderId
                + " or exchange order id, never replay blindly.");
      }
      throw e;
    }
  }

  /** A provider call that may fail at the transport or provider layer. */
  @FunctionalInterface
  protected interface MexcV3Call<T> {
    T call() throws IOException;
  }
}
