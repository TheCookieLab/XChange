package org.knowm.xchange.coinbasederivatives.auth;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Maintains the short-lived HTTP bearer token with proactive, single-flight refresh. */
public final class CoinbaseDerivativesAccessTokenProvider {
  @FunctionalInterface
  public interface Authenticator {
    AccessToken authenticate(String freshJwt) throws IOException;
  }

  private record CachedToken(String value, Instant refreshAt) {}

  private final CoinbaseDerivativesJwtGenerator jwtGenerator;
  private final Authenticator authenticator;
  private final Clock clock;
  private final Duration refreshSkew;
  private volatile CachedToken cachedToken = new CachedToken("", Instant.MIN);

  public CoinbaseDerivativesAccessTokenProvider(
      CoinbaseDerivativesJwtGenerator jwtGenerator, Authenticator authenticator) {
    this(jwtGenerator, authenticator, Clock.systemUTC(), Duration.ofSeconds(60));
  }

  CoinbaseDerivativesAccessTokenProvider(
      CoinbaseDerivativesJwtGenerator jwtGenerator,
      Authenticator authenticator,
      Clock clock,
      Duration refreshSkew) {
    this.jwtGenerator = Objects.requireNonNull(jwtGenerator);
    this.authenticator = Objects.requireNonNull(authenticator);
    this.clock = Objects.requireNonNull(clock);
    this.refreshSkew = Objects.requireNonNull(refreshSkew);
  }

  /** Returns a valid token, refreshing once for all concurrent callers when necessary. */
  public String getToken() throws IOException {
    CachedToken token = cachedToken;
    Instant now = clock.instant();
    if (now.isBefore(token.refreshAt())) {
      return token.value();
    }
    synchronized (this) {
      token = cachedToken;
      now = clock.instant();
      if (now.isBefore(token.refreshAt())) {
        return token.value();
      }
      AccessToken refreshed = authenticator.authenticate(jwtGenerator.generate());
      if (refreshed == null
          || refreshed.accessToken() == null
          || refreshed.accessToken().isBlank()) {
        throw new IOException("Coinbase derivatives authentication returned no access token");
      }
      long lifetime = Math.max(1L, refreshed.expiresIn());
      Duration effectiveSkew =
          refreshSkew.compareTo(Duration.ofSeconds(lifetime / 2)) > 0
              ? Duration.ofSeconds(lifetime / 2)
              : refreshSkew;
      cachedToken =
          new CachedToken(refreshed.accessToken(), now.plusSeconds(lifetime).minus(effectiveSkew));
      return refreshed.accessToken();
    }
  }

  /** Invalidates the cached token only if it is the token rejected by the gateway. */
  public synchronized void invalidate(String rejectedToken) {
    if (Objects.equals(cachedToken.value(), rejectedToken)) {
      cachedToken = new CachedToken("", Instant.MIN);
    }
  }
}
