package org.knowm.xchange.okex;

import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.okex.service.OkexAccountService;
import org.knowm.xchange.okex.service.OkexMarketDataService;
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

  @Override
  public ExchangeSpecification getDefaultExchangeSpecification() {
    ExchangeSpecification exchangeSpecification = super.getDefaultExchangeSpecification();
    // Preserve the legacy exchange name for source compatibility.
    exchangeSpecification.setExchangeName("Okex");
    return exchangeSpecification;
  }
}
