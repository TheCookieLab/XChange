package org.knowm.xchange.deribit.v2.service.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.deribit.DeribitIntegrationTestParent;
import org.knowm.xchange.deribit.v2.DeribitExchange;
import org.knowm.xchange.deribit.v2.service.DeribitMarketDataService;

public class DeribitHistoricalVolatileIntegration {

  private static DeribitExchange exchange;
  private static DeribitMarketDataService deribitMarketDataService;

  @BeforeAll
  public static void setUp() {
    exchange = DeribitIntegrationTestParent.createSandboxExchangeWithoutRemoteMetadata();
    deribitMarketDataService = (DeribitMarketDataService) exchange.getMarketDataService();
  }

  @BeforeEach
  void exchangeOnline() {
    DeribitIntegrationTestParent.assumeExchangeOnline(exchange);
  }

  @Test
  public void getHistoricalVol() throws Exception {
    List<List<BigDecimal>> historyVolatile =
        deribitMarketDataService.getHistoricalVolatility("BTC");

    assertThat(historyVolatile).isNotNull();
    assertThat(historyVolatile).isNotEmpty();
    assertThat(historyVolatile.get(0)).isNotEmpty();
  }
}
