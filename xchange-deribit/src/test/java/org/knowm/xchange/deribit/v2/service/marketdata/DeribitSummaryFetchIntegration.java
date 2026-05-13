package org.knowm.xchange.deribit.v2.service.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.deribit.DeribitIntegrationTestParent;
import org.knowm.xchange.deribit.v2.DeribitExchange;
import org.knowm.xchange.deribit.v2.dto.marketdata.DeribitSummary;
import org.knowm.xchange.deribit.v2.service.DeribitMarketDataService;

public class DeribitSummaryFetchIntegration {

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
  public void getDeribitSummaryTest() throws Exception {
    List<DeribitSummary> summary = deribitMarketDataService.getSummaryByInstrument("BTC-PERPETUAL");

    assertThat(summary).isNotEmpty();
    assertThat(summary.get(0).getInstrumentName()).isEqualTo("BTC-PERPETUAL");
  }
}
