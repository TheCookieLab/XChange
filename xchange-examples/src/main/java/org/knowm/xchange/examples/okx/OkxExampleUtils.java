package org.knowm.xchange.examples.okx;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.okx.OkxExchange;

public class OkxExampleUtils {

  private OkxExampleUtils() {}

  public static Exchange createTestExchange() {

    Exchange okcoinExchange = ExchangeFactory.INSTANCE.createExchange(OkxExchange.class);
    okcoinExchange.getExchangeSpecification().setApiKey("");
    okcoinExchange.getExchangeSpecification().setSecretKey("");
    okcoinExchange.getExchangeSpecification().setUserName("");
    okcoinExchange.applySpecification(okcoinExchange.getExchangeSpecification());
    return okcoinExchange;
  }
}
