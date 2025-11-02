package org.knowm.xchange.examples.coinbase.v2;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.coinbase.v2.CoinbaseExchange;
import org.knowm.xchange.utils.AuthUtils;

/**
 * @deprecated This example class is deprecated. Coinbase v2 API is deprecated.
 * For code examples and usage, refer to:
 * <ul>
 *   <li>{@link org.knowm.xchange.coinbase.v2.service.BaseServiceIntegration BaseServiceIntegration}</li>
 *   <li>{@link org.knowm.xchange.coinbase.v2.service.MarketDataServiceIntegration MarketDataServiceIntegration}</li>
 * </ul>
 * For v3 API examples, see the integration tests in {@link org.knowm.xchange.coinbase.v3.service}.
 */
@Deprecated
public class CoinbaseDemoUtils {

  public static Exchange createExchange() {
    Exchange exchange = ExchangeFactory.INSTANCE.createExchange(CoinbaseExchange.class);
    AuthUtils.setApiAndSecretKey(exchange.getExchangeSpecification(), "coinbase");
    return exchange;
  }
}
