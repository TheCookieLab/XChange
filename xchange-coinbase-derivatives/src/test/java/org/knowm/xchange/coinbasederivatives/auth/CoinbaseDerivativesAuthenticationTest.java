package org.knowm.xchange.coinbasederivatives.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.auth0.jwt.JWT;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.coinbasederivatives.TestKeys;

class CoinbaseDerivativesAuthenticationTest {
  @Test
  void jwtIsShortLivedAndFreshForEveryExchange() throws GeneralSecurityException {
    CoinbaseDerivativesJwtGenerator generator =
        new CoinbaseDerivativesJwtGenerator(
            "organizations/test/apiKeys/key", TestKeys.newEcPrivateKeyPem());

    String first = generator.generate();
    String second = generator.generate();

    assertNotEquals(first, second);
    var decoded = JWT.decode(first);
    assertEquals("cdp", decoded.getIssuer());
    assertEquals("organizations/test/apiKeys/key", decoded.getSubject());
    assertEquals("organizations/test/apiKeys/key", decoded.getKeyId());
    assertTrue(
        decoded.getExpiresAt().toInstant().getEpochSecond()
                - decoded.getNotBefore().toInstant().getEpochSecond()
            <= CoinbaseDerivativesJwtGenerator.JWT_LIFETIME_SECONDS);
  }

  @Test
  void concurrentCallersShareOneTokenRefresh() throws Exception {
    AtomicInteger authentications = new AtomicInteger();
    CoinbaseDerivativesAccessTokenProvider provider =
        new CoinbaseDerivativesAccessTokenProvider(
            new CoinbaseDerivativesJwtGenerator("key", TestKeys.newEcPrivateKeyPem()),
            jwt -> {
              authentications.incrementAndGet();
              try {
                Thread.sleep(25);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              return new AccessToken("access-token", "bearer", 900, "trade");
            });

    List<CompletableFuture<String>> futures = new ArrayList<>();
    for (int i = 0; i < 16; i++) {
      futures.add(
          CompletableFuture.supplyAsync(
              () -> {
                try {
                  return provider.getToken();
                } catch (IOException e) {
                  throw new IllegalStateException(e);
                }
              }));
    }
    for (CompletableFuture<String> future : futures) {
      assertEquals("access-token", future.get());
    }
    assertEquals(1, authentications.get());
  }
}
