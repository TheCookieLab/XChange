package org.knowm.xchange.coinbase.v3.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.coinbase.v3.CoinbaseAuthenticated;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrderDetail;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrderDetailResponse;
import org.mockito.Mockito;
import si.mazi.rescu.ParamsDigest;

public class CoinbaseTradeServiceGetOrderUnitTest {

  private CoinbaseTradeServiceRaw raw;
  private CoinbaseAuthenticated api;
  private ParamsDigest digest;
  private Exchange exchange;

  @Before
  public void setUp() {
    exchange = Mockito.mock(Exchange.class);
    api = Mockito.mock(CoinbaseAuthenticated.class);
    digest = Mockito.mock(ParamsDigest.class);
    raw = new CoinbaseTradeServiceRaw(exchange, api, digest);
  }

  @Test
  public void testGetOrderDelegatesToApi() throws IOException {
    CoinbaseOrderDetail detail = new CoinbaseOrderDetail(
        "id1", "cid", "BUY", "BTC-USD", "FILLED", null, null, null, null, null, "2024-01-01T00:00:00Z");
    CoinbaseOrderDetailResponse response = new CoinbaseOrderDetailResponse(detail);

    when(api.getOrder(digest, "id1")).thenReturn(response);

    CoinbaseOrderDetailResponse got = raw.getOrder("id1");

    assertNotNull(got);
    assertEquals("id1", got.getOrder().getOrderId());
  }
}


