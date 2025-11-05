package org.knowm.xchange.examples.coinbase;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.coinbase.v3.CoinbaseExchange;
import org.knowm.xchange.utils.AuthUtils;


/**
 * @deprecated This example class is deprecated. For code examples and usage, refer to:
 * <ul>
 *   <li>{@link org.knowm.xchange.coinbase.v3.service.MarketDataServiceIntegration MarketDataServiceIntegration}</li>
 *   <li>{@link org.knowm.xchange.coinbase.v3.service.MarketDataServiceSandboxIntegration MarketDataServiceSandboxIntegration}</li>
 *   <li>{@link org.knowm.xchange.coinbase.v3.service.TradeServiceIntegration TradeServiceIntegration}</li>
 *   <li>{@link org.knowm.xchange.coinbase.v3.service.TradeServiceSandboxIntegration TradeServiceSandboxIntegration}</li>
 *   <li>{@link org.knowm.xchange.coinbase.v3.service.AccountServiceIntegration AccountServiceIntegration}</li>
 *   <li>{@link org.knowm.xchange.coinbase.v3.service.AccountServiceSandboxIntegration AccountServiceSandboxIntegration}</li>
 * </ul>
 * @author jamespedwards42
 */
@SuppressWarnings("JavadocReference")
@Deprecated
public class CoinbaseDemoUtils {

  public static Exchange createExchange() {
    ExchangeSpecification exSpec = new CoinbaseExchange().getDefaultExchangeSpecification();
    AuthUtils.setApiAndSecretKey(exSpec);
    return ExchangeFactory.INSTANCE.createExchange(exSpec);
  }
}
