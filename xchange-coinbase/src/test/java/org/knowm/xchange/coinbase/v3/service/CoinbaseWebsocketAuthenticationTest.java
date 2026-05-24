package org.knowm.xchange.coinbase.v3.service;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.function.Supplier;
import org.junit.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.coinbase.v3.CoinbaseExchange;

public class CoinbaseWebsocketAuthenticationTest {

  @Test
  public void websocketJwtSupplierReturnsNullWhenCredentialsAreInvalid() {
    ExchangeSpecification specification = new ExchangeSpecification(CoinbaseExchange.class);
    specification.setApiKey("test-api-key");
    specification.setSecretKey("test-secret-key");

    Supplier<String> supplier =
        CoinbaseWebsocketAuthentication.websocketJwtSupplier(specification);

    assertNotNull(supplier);
    assertNull(supplier.get());
  }
}
