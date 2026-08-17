package org.knowm.xchange.cryptocom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.cryptocom.CryptoCom;
import org.knowm.xchange.cryptocom.CryptoComExchange;
import org.knowm.xchange.cryptocom.dto.CryptoComRequest;
import org.knowm.xchange.cryptocom.dto.CryptoComResponse;
import org.knowm.xchange.exceptions.DepositAddressAmbiguousException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.service.trade.params.DefaultWithdrawFundsParams;
import org.knowm.xchange.service.trade.params.NetworkWithdrawFundsParams;
import org.knowm.xchange.service.trade.params.WithdrawFundsParams;

public class CryptoComAccountServiceTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  public void singleAddress_returnedDirectly() throws Exception {
    CryptoComAccountService service = newService(address("eth", "0xabc"));

    String address = service.requestDepositAddress(Currency.USDT);

    assertThat(address).isEqualTo("0xabc");
  }

  @Test
  public void multipleAddressesWithoutNetwork_throwsAmbiguousException() throws Exception {
    CryptoComAccountService service =
        newService(address("eth", "0xabc"), address("sol", "SoLAddr"));

    assertThatThrownBy(() -> service.requestDepositAddress(Currency.USDT))
        .isInstanceOf(DepositAddressAmbiguousException.class);
  }

  @Test
  public void multipleAddressesWithNetwork_returnsMatchingAddress() throws Exception {
    CryptoComAccountService service =
        newService(address("eth", "0xabc"), address("sol", "SoLAddr"));

    String address = service.requestDepositAddress(Currency.USDT, "sol");

    assertThat(address).isEqualTo("SoLAddr");
  }

  @Test
  public void multipleAddressesWithUnknownNetwork_throws() throws Exception {
    CryptoComAccountService service =
        newService(address("eth", "0xabc"), address("sol", "SoLAddr"));

    assertThatThrownBy(() -> service.requestDepositAddress(Currency.USDT, "trx"))
        .isInstanceOf(NotAvailableFromExchangeException.class);
  }

  @Test
  public void withdrawFunds_acceptsPlainDefaultParams_omitsNetwork() throws Exception {
    CryptoComRequest[] captured = new CryptoComRequest[1];
    CryptoComAccountService service = newWithdrawService(captured);

    DefaultWithdrawFundsParams params =
        new DefaultWithdrawFundsParams("0xabc", Currency.USDT, new BigDecimal("10"));

    String id = service.withdrawFunds(params);

    assertThat(id).isEqualTo("wid-1");
    assertThat(captured[0].getParams()).doesNotContainKey("network_id");
  }

  @Test
  public void withdrawFunds_withNetworkParams_includesNetwork() throws Exception {
    CryptoComRequest[] captured = new CryptoComRequest[1];
    CryptoComAccountService service = newWithdrawService(captured);

    NetworkWithdrawFundsParams params =
        NetworkWithdrawFundsParams.builder()
            .address("0xabc")
            .currency(Currency.USDT)
            .amount(new BigDecimal("10"))
            .network("eth")
            .build();

    service.withdrawFunds(params);

    assertThat(captured[0].getParams()).containsEntry("network_id", "eth");
  }

  @Test
  public void withdrawFunds_rejectsUnsupportedParamsType() throws Exception {
    CryptoComAccountService service = newWithdrawService(new CryptoComRequest[1]);
    WithdrawFundsParams unsupported = mock(WithdrawFundsParams.class);

    assertThatThrownBy(() -> service.withdrawFunds(unsupported))
        .isInstanceOf(NotAvailableFromExchangeException.class);
  }

  @Test
  public void getAccountInfo_mapsBalancesIncludingMarginFields() throws Exception {
    ObjectNode balance = mapper.createObjectNode();
    balance.put("instrument_name", "BTC_USDT");
    balance.put("total_available_balance", "0.5");
    balance.put("total_margin_balance", "1.5");
    balance.put("total_cash_balance", "1.0");
    balance.put("total_effective_balance", "1.75");
    balance.put("total_initial_margin", "0.25");
    balance.put("total_maintenance_margin", "0.1");
    balance.put("total_position_margin", "0.2");
    balance.put("total_collateral", "2.0");
    ObjectNode positionBalance = mapper.createObjectNode();
    positionBalance.put("instrument_name", "BTCUSD-PERP");
    positionBalance.put("quantity", "0.5");
    positionBalance.put("market_value", "20000.0");
    positionBalance.put("reserved_qty", "0.1");
    balance.set("position_balances", mapper.createArrayNode().add(positionBalance));

    CryptoComAccountService service = newJsonService(mapper.createArrayNode().add(balance), null);

    org.knowm.xchange.dto.account.Balance xBalance =
        service
            .getAccountInfo()
            .getWallet()
            .getBalance(org.knowm.xchange.currency.Currency.BTC);

    assertThat(xBalance.getTotal()).isEqualByComparingTo("0.5");
    // margin/liability fields round-trip through the raw DTO in the request envelope only; the
    // XChange wallet exposes the same numbers as total/available/held.
    assertThat(service.getCryptoComBalances().get(0).getTotalEffectiveBalance())
        .isEqualTo("1.75");
    assertThat(service.getCryptoComBalances().get(0).getTotalInitialMargin()).isEqualTo("0.25");
    assertThat(service.getCryptoComBalances().get(0).getTotalMaintenanceMargin()).isEqualTo("0.1");
    assertThat(service.getCryptoComBalances().get(0).getTotalPositionMargin()).isEqualTo("0.2");
    assertThat(service.getCryptoComBalances().get(0).getTotalCollateral()).isEqualTo("2.0");
  }

  @Test
  public void getCryptoComFeeRate_returnsTieredSchedule() throws Exception {
    CryptoComAccountService service =
        accountServiceWith("fee-rate.json", "private/get-fee-rate");

    org.knowm.xchange.cryptocom.dto.account.CryptoComFeeRate rate =
        service.getCryptoComFeeRate("BTC_USDT").get(0);

    assertThat(rate.getInstrumentName()).isEqualTo("BTC_USDT");
    assertThat(rate.getEffectiveFeeTier()).isEqualTo(2);
    assertThat(rate.getFeeTiers()).hasSize(2);
  }

  @Test
  public void getCryptoComPositions_returnsDerivativeRows() throws Exception {
    CryptoComAccountService service =
        accountServiceWith("position.json", "private/get-positions");

    org.knowm.xchange.cryptocom.dto.account.CryptoComPosition position =
        service.getCryptoComPositions("USD").get(0);

    assertThat(position.getInstrumentName()).isEqualTo("BTCUSD-PERP");
    assertThat(position.getQuantity()).isEqualTo("-0.5");
    assertThat(position.getUpl()).isEqualTo("250.25");
  }

  @Test
  public void getCryptoComAccounts_returnsRiskModelRows() throws Exception {
    CryptoComAccountService service = accountServiceWith("account.json", "private/get-accounts");

    org.knowm.xchange.cryptocom.dto.account.CryptoComAccount account =
        service.getCryptoComAccounts().get(0);

    assertThat(account.getMarginRiskModel()).isEqualTo("PORTFOLIO_MARGIN");
    assertThat(account.getAccountType()).isEqualTo("ACCOUNT_TYPE_MARGIN");
  }

  @Test
  public void userBalanceHistory_stopsAtCallerLimitWithoutOverFetch() throws Exception {
    CryptoComRequest[] captured = new CryptoComRequest[1];
    CryptoComAccountService service = balanceHistoryService(captured, 10);

    java.util.List<org.knowm.xchange.cryptocom.dto.account.CryptoComUserBalanceHistoryRecord>
        records = service.getCryptoComUserBalanceHistory(null, 1L, 2L, 3);

    assertThat(records).hasSize(3);
    assertThat(captured[0].getParams()).containsEntry("page", 1).containsEntry("page_size", 100);
  }

  @Test
  public void userBalanceHistory_stopsOnRepeatedPages() throws Exception {
    CryptoComRequest[] captured = new CryptoComRequest[2];
    CryptoComAccountService service = balanceHistoryService(captured, 100);

    java.util.List<org.knowm.xchange.cryptocom.dto.account.CryptoComUserBalanceHistoryRecord>
        records = service.getCryptoComUserBalanceHistory(null, 1L, 2L, null);

    // page 2 repeats page 1 -> stop; each page carried exactly 100 rows
    assertThat(records).hasSize(100);
    assertThat(captured[1]).isNotNull();
  }

  private CryptoComAccountService balanceHistoryService(
      CryptoComRequest[] captured, int rowsPerPage) throws Exception {
    CryptoCom cryptoCom = mock(CryptoCom.class);
    when(cryptoCom.getUserBalanceHistory(any()))
        .thenAnswer(
            invocation -> {
              CryptoComRequest request = invocation.getArgument(0);
              if (captured[0] == null) {
                captured[0] = request;
              } else {
                captured[1] = request;
              }
              ObjectNode row = mapper.createObjectNode();
              row.put("account_id", "a1");
              row.put("event_type", "TRADE");
              row.put("amount", "0.001");
              row.put("balance", "1.001");
              ArrayNode data = mapper.createArrayNode();
              for (int i = 0; i < rowsPerPage; i++) {
                data.add(row);
              }
              ObjectNode result = mapper.createObjectNode();
              result.set("data", data);
              CryptoComResponse response = new CryptoComResponse();
              response.setResult(result);
              return response;
            });
    return new CryptoComAccountService(mockExchange(cryptoCom), new ResilienceRegistries());
  }

  private CryptoComAccountService accountServiceWith(String fixture, String method)
      throws Exception {
    CryptoCom cryptoCom = mock(CryptoCom.class);
    java.io.InputStream is =
        getClass()
            .getResourceAsStream(
                "/org/knowm/xchange/cryptocom/dto/account/" + fixture);
    CryptoComResponse payload = mapper.readValue(is, CryptoComResponse.class);
    when(cryptoCom.getFeeRate(any())).thenReturn(payload);
    when(cryptoCom.getPositions(any())).thenReturn(payload);
    when(cryptoCom.getAccounts(any())).thenReturn(payload);
    return new CryptoComAccountService(mockExchange(cryptoCom), new ResilienceRegistries());
  }

  private CryptoComAccountService newJsonService(ArrayNode list, String unused) throws Exception {
    CryptoCom cryptoCom = mock(CryptoCom.class);
    when(cryptoCom.userBalance(any()))
        .thenAnswer(
            invocation -> {
              ObjectNode result = mapper.createObjectNode();
              result.set("data", list);
              CryptoComResponse response = new CryptoComResponse();
              response.setResult(result);
              return response;
            });
    return new CryptoComAccountService(mockExchange(cryptoCom), new ResilienceRegistries());
  }

  private CryptoComAccountService newWithdrawService(CryptoComRequest[] captured)
      throws Exception {
    CryptoCom cryptoCom = mock(CryptoCom.class);
    when(cryptoCom.createWithdrawal(any()))
        .thenAnswer(
            invocation -> {
              captured[0] = invocation.getArgument(0);
              ObjectNode result = mapper.createObjectNode();
              result.put("id", "wid-1");
              CryptoComResponse response = new CryptoComResponse();
              response.setResult(result);
              return response;
            });

    return new CryptoComAccountService(mockExchange(cryptoCom), new ResilienceRegistries());
  }

  private ObjectNode address(String networkId, String addr) {
    ObjectNode node = mapper.createObjectNode();
    node.put("network_id", networkId);
    node.put("address", addr);
    return node;
  }

  private CryptoComAccountService newService(ObjectNode... addresses) throws Exception {
    ArrayNode list = mapper.createArrayNode();
    for (ObjectNode a : addresses) {
      list.add(a);
    }
    ObjectNode result = mapper.createObjectNode();
    result.set("deposit_address_list", list);

    CryptoCom cryptoCom = mock(CryptoCom.class);
    when(cryptoCom.getDepositAddress(any()))
        .thenAnswer(
            invocation -> {
              CryptoComResponse response = new CryptoComResponse();
              response.setResult(result);
              return response;
            });

    return new CryptoComAccountService(mockExchange(cryptoCom), new ResilienceRegistries());
  }

  private CryptoComExchange mockExchange(CryptoCom cryptoCom) {
    CryptoComExchange exchange = mock(CryptoComExchange.class);
    ExchangeSpecification spec = new ExchangeSpecification(CryptoComExchange.class);
    spec.setApiKey("key");
    spec.setSecretKey("secret");
    when(exchange.getExchangeSpecification()).thenReturn(spec);
    when(exchange.getCryptoCom()).thenReturn(cryptoCom);
    when(exchange.nextRequestId()).thenReturn(1L);
    return exchange;
  }
}
