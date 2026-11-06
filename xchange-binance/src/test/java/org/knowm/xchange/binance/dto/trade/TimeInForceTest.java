package org.knowm.xchange.binance.dto.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class TimeInForceTest {

  @Test
  public void nullMessageUsesReadableText() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> TimeInForce.getTimeInForce(null));

    assertEquals("Unknown order time in force null.", exception.getMessage());
  }

  @Test
  public void invalidValueMessageUsesReadableText() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> TimeInForce.getTimeInForce("BAD"));

    assertEquals("Unknown order time in force BAD.", exception.getMessage());
    assertEquals(IllegalArgumentException.class, exception.getCause().getClass());
  }
}
