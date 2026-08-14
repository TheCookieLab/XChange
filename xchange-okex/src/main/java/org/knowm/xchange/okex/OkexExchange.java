package org.knowm.xchange.okex;

import java.io.IOException;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.okex.service.OkexAccountService;
import org.knowm.xchange.okex.service.OkexAccountServiceRaw;
import org.knowm.xchange.okex.service.OkexMarketDataService;
import org.knowm.xchange.okex.service.OkexMarketDataServiceRaw;
import org.knowm.xchange.okex.service.OkexTradeService;
import org.knowm.xchange.okx.OkxExchange;

/**
 * Compatibility shim for the deprecated {@code org.knowm.xchange.okex} exchange. All functionality
 * is delegated to the canonical Okx implementation.
 *
 * @deprecated use {@link org.knowm.xchange.okx.OkxExchange} instead.
 */
@Deprecated
public class OkexExchange extends OkxExchange {

  @Override
  protected void initServices() {
    concludeHostParams(exchangeSpecification);

    this.marketDataService = new OkexMarketDataService(this, getResilienceRegistries());
    this.accountService = new OkexAccountService(this, getResilienceRegistries());
    this.tradeService = new OkexTradeService(this, getResilienceRegistries());
  }

  /**
   * Re-initializes through the canonical raw endpoints behind this shim's delegation services,
   * reusing the canonical {@link OkxExchange#remoteInit(OkxMarketDataServiceRaw,
   * OkxAccountServiceRaw)} logic. The services installed by {@link #initServices()} are the shim
   * types, so the cast must unwrap them to their canonical delegates first.
   */
  @Override
  public void remoteInit() throws IOException {
    remoteInit(
        ((OkexMarketDataServiceRaw) marketDataService).getDelegate(),
        ((OkexAccountServiceRaw) accountService).getDelegate());
  }

  @Override
  public ExchangeSpecification getDefaultExchangeSpecification() {
    ExchangeSpecification exchangeSpecification = super.getDefaultExchangeSpecification();
    // Preserve the legacy exchange name for source compatibility.
    exchangeSpecification.setExchangeName("Okex");
    return exchangeSpecification;
  }
}
