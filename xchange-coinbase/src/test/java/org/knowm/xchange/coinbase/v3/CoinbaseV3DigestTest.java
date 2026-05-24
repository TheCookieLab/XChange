package org.knowm.xchange.coinbase.v3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class CoinbaseV3DigestTest {

  @Test
  public void createInstanceWrapsInvalidPemAsIllegalState() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> CoinbaseV3Digest.createInstance("test-api-key", "test-secret-key"));

    assertEquals(IllegalArgumentException.class, exception.getCause().getClass());
    assertEquals("Unknown PEM object: null", exception.getCause().getMessage());
  }
}
