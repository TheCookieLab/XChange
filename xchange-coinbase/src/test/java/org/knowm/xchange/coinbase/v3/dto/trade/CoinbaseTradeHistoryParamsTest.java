package org.knowm.xchange.coinbase.v3.dto.trade;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashSet;
import org.junit.Test;

public class CoinbaseTradeHistoryParamsTest {

  @Test
  public void addProductIdWorksAfterNullProductIds() {
    CoinbaseTradeHistoryParams params = new CoinbaseTradeHistoryParams();

    params.setProductIds(null);

    assertTrue(params.getProductIds().isEmpty());

    params.addProductId(" BTC-PERP ");

    assertEquals(Collections.singleton("BTC-PERP"), new HashSet<>(params.getProductIds()));
  }

  @Test
  public void partialPageContinuationPreservesCursorAndClearsOnCallerReset() {
    CoinbaseTradeHistoryParams params = new CoinbaseTradeHistoryParams();

    params.setNextPageCursorContinuation("remote-cursor", 2);

    assertEquals("remote-cursor", params.getNextPageCursor());
    assertEquals(2, params.getNextPageCursorFillOffset());

    params.setNextPageCursor("caller-cursor");

    assertEquals("caller-cursor", params.getNextPageCursor());
    assertEquals(0, params.getNextPageCursorFillOffset());
    assertThrows(
        IllegalArgumentException.class,
        () -> params.setNextPageCursorContinuation("invalid-cursor", -1));
  }
}
