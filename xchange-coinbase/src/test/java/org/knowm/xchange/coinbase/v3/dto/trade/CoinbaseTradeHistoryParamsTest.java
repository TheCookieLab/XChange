package org.knowm.xchange.coinbase.v3.dto.trade;

import static org.junit.Assert.assertEquals;
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
}
