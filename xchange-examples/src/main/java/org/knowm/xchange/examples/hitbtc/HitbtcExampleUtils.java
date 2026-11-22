package org.knowm.xchange.examples.hitbtc;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.hitbtc.v2.HitbtcExchange;

public class HitbtcExampleUtils {

  public static Exchange createExchange() {

    ExchangeSpecification exSpec = new HitbtcExchange().getDefaultExchangeSpecification();
    exSpec.setApiKey("");
    exSpec.setSecretKey("");

    // To use the sandbox, select its HTTPS base URL before creating the exchange:
    // exSpec.setSslUri((String) exSpec.getExchangeSpecificParametersItem("demo-api"));

    return ExchangeFactory.INSTANCE.createExchange(exSpec);
  }
}
