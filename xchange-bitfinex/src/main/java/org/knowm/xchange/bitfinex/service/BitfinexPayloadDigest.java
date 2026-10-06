package org.knowm.xchange.bitfinex.service;

import java.util.Base64;
import org.knowm.xchange.bitfinex.v1.dto.BitfinexAuthenticatedRequest;
import si.mazi.rescu.ParamsDigest;
import si.mazi.rescu.RestInvocation;
import si.mazi.rescu.SynchronizedValueFactory;

/**
 * Computes the {@code X-BFX-PAYLOAD} header of a v1 authenticated call and, in doing so, creates
 * the request nonce.
 *
 * <p>rescu builds one {@link RestInvocation} per wire attempt, i.e. after the rate limiter has
 * admitted it, and evaluates the header digests in declaration order ({@code X-BFX-APIKEY}, {@code
 * X-BFX-PAYLOAD}, {@code X-BFX-SIGNATURE}) before the body is serialized for the wire. Taking the
 * nonce here, instead of when the request object is built, keeps it strictly increasing per API key
 * even when the call waited in admission while other calls (v1 or v2) consumed newer nonces from
 * the shared factory, and gives a replayed attempt a fresh nonce. The payload digest MUST stay
 * ahead of the signature digest so that both cover the body that carries the stamped nonce.
 */
public class BitfinexPayloadDigest implements ParamsDigest {

  private final SynchronizedValueFactory<Long> nonceFactory;

  public BitfinexPayloadDigest(SynchronizedValueFactory<Long> nonceFactory) {
    this.nonceFactory = nonceFactory;
  }

  @Override
  public synchronized String digestParams(RestInvocation restInvocation) {

    for (Object param : restInvocation.getUnannanotatedParams()) {
      if (param instanceof BitfinexAuthenticatedRequest request) {
        request.setNonce(String.valueOf(nonceFactory.createValue()));
      }
    }
    String postBody = restInvocation.getRequestBody();
    return Base64.getEncoder().encodeToString(postBody.getBytes());
  }
}
