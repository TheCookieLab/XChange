package org.knowm.xchange.examples.coinbase;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.coinbase.v3.CoinbaseExchange;
import org.knowm.xchange.utils.AuthUtils;

/**
 * Utility class for creating Coinbase exchange instances for examples.
 *
 * <p>Provides methods to create exchanges with or without authentication.
 * Public endpoints (market data) don't require authentication, while
 * account and trade operations require API keys.
 *
 * @author jamespedwards42
 */
public class CoinbaseDemoUtils {

  /**
   * Creates an exchange instance with authentication.
   * Use this for account and trade operations that require API keys.
   *
   * @return Exchange instance with API keys configured
   */
  public static Exchange createExchange() {
    ExchangeSpecification exSpec = new CoinbaseExchange().getDefaultExchangeSpecification();
    AuthUtils.setApiAndSecretKey(exSpec);
    return ExchangeFactory.INSTANCE.createExchange(exSpec);
  }

  /**
   * Creates an exchange instance without authentication.
   * Use this for public endpoints (market data) that don't require API keys.
   *
   * @return Exchange instance without API keys
   */
  public static Exchange createExchangeWithoutAuth() {
    ExchangeSpecification exSpec = new CoinbaseExchange().getDefaultExchangeSpecification();
    return ExchangeFactory.INSTANCE.createExchange(exSpec);
  }
}
