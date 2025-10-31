package org.knowm.xchange.coinbase.v2;

import org.knowm.xchange.BaseExchange;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.coinbase.v2.service.CoinbaseMarketDataService;

/**
 * Coinbase Exchange v2 API implementation.
 *
 * <p><strong>⚠️ DEPRECATED:</strong> The Coinbase v2 API has severely limited functionality:
 *
 * <ul>
 *   <li>✅ <strong>Working:</strong> Public market data endpoints (exchange rates, buy/sell/spot
 *       prices)
 *   <li>❌ <strong>Broken:</strong> All authenticated endpoints (accounts, trading) - authentication
 *       mechanism changed
 *   <li>❌ <strong>Missing:</strong> Order book access, order placement, advanced trading features
 * </ul>
 *
 * <p><strong>Recommended Alternative:</strong> Use {@link
 * org.knowm.xchange.coinbase.v3.CoinbaseExchange} for the full-featured Coinbase Advanced Trade API
 * with:
 *
 * <ul>
 *   <li>Real-time order book data
 *   <li>Market and limit order placement
 *   <li>Account management
 *   <li>Trade history and fills
 *   <li>Advanced trading features
 * </ul>
 *
 * @deprecated Use {@link org.knowm.xchange.coinbase.v3.CoinbaseExchange} instead
 */
@Deprecated
public class CoinbaseExchange extends BaseExchange implements Exchange {

  @Override
  protected void initServices() {
    this.marketDataService = new CoinbaseMarketDataService(this);
    // Account and Trade services have been removed - authentication no longer works
  }

  @Override
  public ExchangeSpecification getDefaultExchangeSpecification() {

    final ExchangeSpecification exchangeSpecification = new ExchangeSpecification(this.getClass());
    exchangeSpecification.setSslUri("https://api.coinbase.com");
    exchangeSpecification.setHost("api.coinbase.com");
    exchangeSpecification.setExchangeName("Coinbase v2 (Deprecated)");
    exchangeSpecification.setExchangeDescription(
        "Coinbase v2 API - DEPRECATED. Provides retail pricing data only. "
            + "Use org.knowm.xchange.coinbase.v3.CoinbaseExchange for full trading functionality.");
    return exchangeSpecification;
  }
}
