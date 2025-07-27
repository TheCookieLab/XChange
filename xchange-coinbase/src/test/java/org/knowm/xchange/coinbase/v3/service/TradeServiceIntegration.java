package org.knowm.xchange.coinbase.v3.service;

import static org.junit.Assert.assertNotNull;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.coinbase.v3.CoinbaseExchange;
import org.knowm.xchange.coinbase.v3.dto.accounts.CoinbaseAccount;
import org.knowm.xchange.coinbase.v3.dto.paymentmethods.CoinbasePaymentMethod;
import org.knowm.xchange.coinbase.v3.dto.transactions.CoinbaseFeeTier;
import org.knowm.xchange.coinbase.v3.dto.transactions.CoinbaseTransactionSummaryResponse;
import org.knowm.xchange.utils.AuthUtils;

public class TradeServiceIntegration {

  static CoinbaseExchange exchange;
  static CoinbaseTradeService tradeService;

  @BeforeClass
  public static void beforeClass() {
    ExchangeSpecification exchangeSpecification = ExchangeFactory.INSTANCE.createExchange(
        CoinbaseExchange.class).getDefaultExchangeSpecification();
    AuthUtils.setApiAndSecretKey(exchangeSpecification);
    exchange = (CoinbaseExchange) ExchangeFactory.INSTANCE.createExchange(exchangeSpecification);
    tradeService = (CoinbaseTradeService) exchange.getTradeService();
  }

  @Test
  public void testGetTransactionSummary() throws Exception {
    Assume.assumeNotNull(tradeService.authTokenCreator);

    CoinbaseTransactionSummaryResponse transactionSummary = tradeService.getTransactionSummary();
    CoinbaseFeeTier feeTier = transactionSummary.getFeeTier();

    assertNotNull(feeTier);
  }
}
