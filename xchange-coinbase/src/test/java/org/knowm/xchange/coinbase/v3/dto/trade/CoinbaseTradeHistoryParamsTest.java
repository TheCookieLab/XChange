package org.knowm.xchange.coinbase.v3.dto.trade;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

    params.setFillContinuation(null, true);

    assertEquals(null, params.getNextPageCursor());
    assertTrue(params.isFillContinuationPending());

    params.setNextPageCursor("caller-cursor");

    assertEquals("caller-cursor", params.getNextPageCursor());
    assertFalse(params.isFillContinuationPending());
  }
}
