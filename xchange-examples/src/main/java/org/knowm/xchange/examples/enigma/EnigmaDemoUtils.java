package org.knowm.xchange.examples.enigma;

import java.io.IOException;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.enigma.EnigmaExchange;
import org.knowm.xchange.enigma.service.EnigmaAccountService;

public class EnigmaDemoUtils {

  // SECURITY NOTICE: Do not hardcode credentials in source code.
  // Retrieve username and password from environment variables or system properties instead.
  private static String username =
      System.getProperty("enigma.username", System.getenv().getOrDefault("ENIGMA_USERNAME", ""));
  private static String password =
      System.getProperty("enigma.password", System.getenv().getOrDefault("ENIGMA_PASSWORD", ""));
  private static String infra = "dev";

  public static Exchange createExchange() {

    EnigmaExchange enigmaExchange = new EnigmaExchange();
    ExchangeSpecification exchangeSpec = enigmaExchange.getDefaultExchangeSpecification();
    exchangeSpec.setExchangeSpecificParametersItem("infra", infra);
    exchangeSpec.setUserName(username);
    exchangeSpec.setPassword(password);
    enigmaExchange.applySpecification(exchangeSpec);
    try {
      ((EnigmaAccountService) enigmaExchange.getAccountService()).login();
    } catch (IOException e) {
      throw new RuntimeException("Login exception", e);
    }
    return enigmaExchange;
  }
}
