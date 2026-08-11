package org.knowm.xchange.kucoin.uta;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class UtaClientOrderIdTest {

  @Test
  void acceptsValidIds() {
    assertDoesNotThrow(() -> UtaClientOrderId.validate("ord-123_456", "SPOT"));
    assertDoesNotThrow(() -> UtaClientOrderId.validate("a", "FUTURES"));
    assertDoesNotThrow(() -> UtaClientOrderId.validate("A1-b_2", "MARGIN"));
  }

  @Test
  void rejectsMissingIdForFuturesAndMargin() {
    assertDoesNotThrow(() -> UtaClientOrderId.validate(null, "SPOT"));
    assertThrows(
        IllegalArgumentException.class, () -> UtaClientOrderId.validate(null, "FUTURES"));
    assertThrows(
        IllegalArgumentException.class, () -> UtaClientOrderId.validate("", "MARGIN"));
  }

  @Test
  void rejectsTooLongAndIllegalCharacters() {
    assertThrows(
        IllegalArgumentException.class,
        () -> UtaClientOrderId.validate("a".repeat(41), "SPOT"));
    assertThrows(
        IllegalArgumentException.class,
        () -> UtaClientOrderId.validate("has space", "SPOT"));
    assertThrows(
        IllegalArgumentException.class,
        () -> UtaClientOrderId.validate("has.dot", "SPOT"));
  }
}
