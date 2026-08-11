package org.knowm.xchange.kucoin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class KucoinApiModeTest {

  @Test
  void resolvesNullAndBlankToClassicDefault() {
    assertEquals(KucoinApiMode.CLASSIC, KucoinApiMode.resolve(null));
    assertEquals(KucoinApiMode.CLASSIC, KucoinApiMode.resolve(""));
    assertEquals(KucoinApiMode.CLASSIC, KucoinApiMode.resolve("   "));
  }

  @Test
  void resolvesEnumValuesCaseInsensitively() {
    assertEquals(KucoinApiMode.CLASSIC, KucoinApiMode.resolve(KucoinApiMode.CLASSIC));
    assertEquals(KucoinApiMode.UTA, KucoinApiMode.resolve(KucoinApiMode.UTA));
    assertEquals(KucoinApiMode.CLASSIC, KucoinApiMode.resolve("CLASSIC"));
    assertEquals(KucoinApiMode.CLASSIC, KucoinApiMode.resolve("classic"));
    assertEquals(KucoinApiMode.UTA, KucoinApiMode.resolve("UTA"));
    assertEquals(KucoinApiMode.UTA, KucoinApiMode.resolve("uta"));
  }

  @Test
  void rejectsUnknownModesEarly() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> KucoinApiMode.resolve("FUTURES"));
    assertEquals(
        "Unsupported KuCoin apiMode 'FUTURES'; expected CLASSIC or UTA", thrown.getMessage());
  }
}
