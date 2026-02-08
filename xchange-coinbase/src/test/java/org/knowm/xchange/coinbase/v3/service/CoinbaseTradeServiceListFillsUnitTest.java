package org.knowm.xchange.coinbase.v3.service;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.coinbase.v3.CoinbaseAuthenticated;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrdersResponse;
import org.knowm.xchange.coinbase.v3.dto.trade.CoinbaseTradeHistoryParams;
import org.knowm.xchange.currency.CurrencyPair;
import org.mockito.Mockito;
import si.mazi.rescu.ParamsDigest;

public class CoinbaseTradeServiceListFillsUnitTest {

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
  public void listFillsPassesRawProductIdsAndRetailPortfolioId() throws IOException {
    CoinbaseTradeHistoryParams params = new CoinbaseTradeHistoryParams();
    params.addProductId("BTC-PERP");
    params.setRetailPortfolioId("portfolio-uuid");
    params.setStartTime(Date.from(Instant.parse("2026-02-08T00:00:00Z")));
    params.setEndTime(Date.from(Instant.parse("2026-02-08T00:01:00Z")));
    params.setLimit(50);

    CoinbaseOrdersResponse response = new CoinbaseOrdersResponse(Collections.emptyList(), null);
    when(api.listFills(eq(digest),
        isNull(),
        isNull(),
        eq(Collections.singletonList("BTC-PERP")),
        eq("2026-02-08T00:00:00Z"),
        eq("2026-02-08T00:01:00Z"),
        eq("portfolio-uuid"),
        eq(50),
        isNull(),
        isNull())).thenReturn(response);

    CoinbaseOrdersResponse got = raw.listFills(params);
    assertNotNull(got);

    verify(api).listFills(eq(digest),
        isNull(),
        isNull(),
        eq(Collections.singletonList("BTC-PERP")),
        eq("2026-02-08T00:00:00Z"),
        eq("2026-02-08T00:01:00Z"),
        eq("portfolio-uuid"),
        eq(50),
        isNull(),
        isNull());
  }

  @Test
  public void listFillsFallsBackToCurrencyPairAdaptation() throws IOException {
    CoinbaseTradeHistoryParams params = new CoinbaseTradeHistoryParams();
    params.setCurrencyPairs(Set.of(CurrencyPair.BTC_USD));
    params.setRetailPortfolioId("portfolio-uuid");

    CoinbaseOrdersResponse response = new CoinbaseOrdersResponse(Collections.emptyList(), null);
    when(api.listFills(eq(digest),
        isNull(),
        isNull(),
        eq(Collections.singletonList("BTC-USD")),
        isNull(),
        isNull(),
        eq("portfolio-uuid"),
        isNull(),
        isNull(),
        isNull())).thenReturn(response);

    CoinbaseOrdersResponse got = raw.listFills(params);
    assertNotNull(got);

    verify(api).listFills(eq(digest),
        isNull(),
        isNull(),
        eq(Collections.singletonList("BTC-USD")),
        isNull(),
        isNull(),
        eq("portfolio-uuid"),
        isNull(),
        isNull(),
        isNull());
  }
}

