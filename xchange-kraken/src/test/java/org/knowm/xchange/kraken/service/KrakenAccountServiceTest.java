package org.knowm.xchange.kraken.service;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.AddressWithTag;
import org.knowm.xchange.exceptions.DepositAddressAmbiguousException;
import org.knowm.xchange.kraken.KrakenExchangeWiremock;
import org.knowm.xchange.service.account.params.DefaultRequestDepositAddressParams;

@Slf4j
public class KrakenAccountServiceTest extends KrakenExchangeWiremock {

  @Test
  void valid_balances() throws IOException {
    AccountInfo accountInfo = exchange.getAccountService().getAccountInfo();
    assertThat(accountInfo.getWallet("spot").getBalances().get(Currency.USDT).getAvailable()).isEqualTo(new BigDecimal("100.00000000"));
  }


  @Test
  public void testRequestDepositAddress() throws IOException {
    DefaultRequestDepositAddressParams params =
        DefaultRequestDepositAddressParams.builder().currency(Currency.TRX).build();

    String address = exchange.getAccountService().requestDepositAddress(params);

    assertThat(address).isEqualTo("TYAnp8VW1aq5Jbtxgoai7BDo3jKSRe6VNR");
  }


  @Test
  public void testRequestDepositAddressUnknownCurrencyMultipleMethods() {
    var params = DefaultRequestDepositAddressParams.builder().currency(Currency.USDT).build();

    assertThatExceptionOfType(DepositAddressAmbiguousException.class)
        .isThrownBy(() -> exchange.getAccountService().requestDepositAddress(params));
  }

  @Test
  public void testRequestDepositAddressCurrencyWithNetwork() throws IOException {
    DefaultRequestDepositAddressParams params =
        DefaultRequestDepositAddressParams.builder().currency(Currency.XRP).build();

    AddressWithTag address = exchange.getAccountService().requestDepositAddressData(params);

    assertThat(address.getAddress()).isEqualTo("testXrpAddress");
    assertThat(address.getAddressTag()).isEqualTo("123");
  }

  @Test
  public void testRequestDepositMethodCaching() throws IOException {
    // cache enabled
    exchange
        .getExchangeSpecification()
        .setExchangeSpecificParametersItem("cacheDepositMethods", true);

    DefaultRequestDepositAddressParams params =
        DefaultRequestDepositAddressParams.builder().currency(Currency.TRX).build();

    wireMockServer.resetRequests();
    exchange.getAccountService().requestDepositAddress(params);
    exchange.getAccountService().requestDepositAddress(params);

    wireMockServer.verify(1, postRequestedFor(urlEqualTo("/0/private/DepositMethods")));

    // cache disabled
    exchange
        .getExchangeSpecification()
        .setExchangeSpecificParametersItem("cacheDepositMethods", false);

    wireMockServer.resetRequests();
    exchange.getAccountService().requestDepositAddress(params);
    exchange.getAccountService().requestDepositAddress(params);

    wireMockServer.verify(2, postRequestedFor(urlEqualTo("/0/private/DepositMethods")));
  }

}
