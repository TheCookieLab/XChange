package org.knowm.xchange.coinbase.v2.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.coinbase.v2.CoinbaseExchange;
import org.knowm.xchange.coinbase.v2.dto.CoinbaseAmount;
import org.knowm.xchange.coinbase.v2.dto.CoinbasePrice;
import org.knowm.xchange.coinbase.v2.dto.account.CoinbaseAccountData.CoinbaseAccount;
import org.knowm.xchange.coinbase.v2.dto.account.CoinbaseBuyData.CoinbaseBuy;
import org.knowm.xchange.coinbase.v2.dto.account.CoinbaseSellData.CoinbaseSell;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.utils.AuthUtils;

public class TradeServiceIntegration {

  static Exchange exchange;
  static CoinbaseTradeService tradeService;
  static CoinbaseAccountService accountService;

  @BeforeClass
  public static void beforeClass() {
    ExchangeSpecification exchangeSpecification = ExchangeFactory.INSTANCE.createExchange(CoinbaseExchange.class).getDefaultExchangeSpecification();
    AuthUtils.setApiAndSecretKey(exchangeSpecification);
    exchange = ExchangeFactory.INSTANCE.createExchange(exchangeSpecification);
    tradeService = (CoinbaseTradeService) exchange.getTradeService();
    accountService = (CoinbaseAccountService) exchange.getAccountService();
  }

  @Test
  public void buy() throws Exception {

    Assume.assumeNotNull(exchange.getExchangeSpecification().getApiKey());

    Currency currency = Currency.EUR;
    BigDecimal amount = new BigDecimal("10.00");
    BigDecimal total = new BigDecimal("10.00");

    CoinbaseBuy res = tradeService.buy(getAccountId(currency), total, currency, false);
    Assert.assertNotNull(res.getId());
    Assert.assertEquals("created", res.getStatus());
    Assert.assertEquals(new CoinbasePrice(new BigDecimal("1.00"), Currency.EUR), res.getFee());
    Assert.assertEquals(new CoinbaseAmount("BTC", new BigDecimal("0.0001")), res.getAmount());
    Assert.assertEquals(Currency.EUR, res.getSubtotal().getCurrency());
    Assert.assertEquals(Currency.EUR, res.getTotal().getCurrency());
    Assert.assertFalse(res.isCommitted());
  }

  @Test
  public void sell() throws Exception {

    Assume.assumeNotNull(exchange.getExchangeSpecification().getApiKey());

    Currency currency = Currency.BTC;
    BigDecimal amount = new BigDecimal("0.0001");
    BigDecimal total = null;

    CoinbaseSell res = tradeService.sell(getAccountId(currency), total, currency, false);
    Assert.assertNotNull(res.getId());
    Assert.assertEquals("created", res.getStatus());
    Assert.assertEquals(new CoinbasePrice(new BigDecimal("1.00"), Currency.EUR), res.getFee());
    Assert.assertEquals(new CoinbaseAmount("BTC", new BigDecimal("0.0001")), res.getAmount());
    Assert.assertEquals(Currency.EUR, res.getSubtotal().getCurrency());
    Assert.assertEquals(Currency.EUR, res.getTotal().getCurrency());
    Assert.assertFalse(res.isCommitted());
  }

  @Test
  public void quote() throws Exception {

    Assume.assumeNotNull(exchange.getExchangeSpecification().getApiKey());

    Currency currency = Currency.BTC;
    BigDecimal amount = new BigDecimal("0.0001");
    BigDecimal total = null;

    CoinbaseSell res = tradeService.quote(getAccountId(currency), amount, currency);
    Assert.assertNull(res.getId());
    Assert.assertEquals("quote", res.getStatus());
    Assert.assertEquals(new CoinbasePrice(new BigDecimal("1.00"), Currency.EUR), res.getFee());
    Assert.assertEquals(new CoinbaseAmount("BTC", new BigDecimal("0.0001")), res.getAmount());
    Assert.assertEquals(Currency.EUR, res.getSubtotal().getCurrency());
    Assert.assertEquals(Currency.EUR, res.getTotal().getCurrency());
    Assert.assertFalse(res.isCommitted());
  }

  private String getAccountId(Currency currency) throws IOException {
    CoinbaseAccount account = accountService.getCoinbaseAccount(currency);
    return account.getId();
  }
}
