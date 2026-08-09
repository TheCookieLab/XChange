package org.knowm.xchange.coinbase.v3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.function.Supplier;
import org.junit.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.coinbase.v3.CoinbaseV3Authentication;
import org.knowm.xchange.coinbase.v3.CoinbaseV3DigestAuthentication;
import org.knowm.xchange.coinbase.v3.CoinbaseExchange;

/** Deterministic tests for the shared typed REST/WebSocket authentication contract. */
public class CoinbaseV3AuthenticationTest {

  @Test
  public void missingCredentialsYieldNoAuthentication() {
    ExchangeSpecification specification = new ExchangeSpecification(CoinbaseExchange.class);
    assertNull(CoinbaseV3Authentication.from(specification));
  }

  @Test
  public void invalidCredentialsFailWithSanitizedMessage() {
    ExchangeSpecification specification = new ExchangeSpecification(CoinbaseExchange.class);
    specification.setApiKey("test-api-key");
    specification.setSecretKey("test-secret-key");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> CoinbaseV3Authentication.from(specification));

    assertFalse(exception.getMessage().contains("test-secret-key"));
    assertFalse(exception.getMessage().contains("BEGIN PRIVATE KEY"));
    assertTrue(exception.getMessage().contains("invalid"));
  }

  @Test
  public void websocketSupplierAndRestDigestShareOneContract() throws Exception {
    ExchangeSpecification specification = new ExchangeSpecification(CoinbaseExchange.class);
    specification.setApiKey("test-api-key");
    specification.setSecretKey(CoinbaseV3DigestTestSupport.validEcPrivateKeyPem());

    CoinbaseV3Authentication authentication = CoinbaseV3Authentication.from(specification);
    assertNotNull(authentication);
    assertNotNull(authentication.restDigest());
    Supplier<String> supplier = authentication.websocketJwtSupplier();
    assertNotNull(supplier);

    String jwt = supplier.get();
    assertNotNull(jwt);
    // WebSocket JWTs carry the CDP claims but no uri claim.
    String payload = new String(
        java.util.Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), java.nio.charset.StandardCharsets.UTF_8);
    assertTrue(payload.contains("\"iss\":\"cdp\""));
    assertTrue(payload.contains("\"sub\":\"test-api-key\""));
    assertFalse(payload.contains("\"uri\""));
  }
}
