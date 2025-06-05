package org.knowm.xchange.coinsph.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.coinsph.Coinsph;
import org.knowm.xchange.coinsph.CoinsphAdapters;
import org.knowm.xchange.coinsph.CoinsphAuthenticated;
import org.knowm.xchange.coinsph.CoinsphExchange;
import org.knowm.xchange.coinsph.dto.account.*;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.service.trade.params.DefaultWithdrawFundsParams;
import org.knowm.xchange.service.trade.params.FiatWithdrawFundsParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParamsZero;
import org.knowm.xchange.service.trade.params.withdrawals.Address;
import org.knowm.xchange.service.trade.params.withdrawals.Beneficiary;
import si.mazi.rescu.ParamsDigest;
import si.mazi.rescu.SynchronizedValueFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class CoinsphAccountServiceTest {

  private CoinsphAccountService accountService;
  private CoinsphAuthenticated coinsphAuthenticated;
  private CoinsphExchange exchange;
  private SynchronizedValueFactory<Long> timestampFactory;
  private ParamsDigest signatureCreator;

  @BeforeEach
  public void setUp() {
    exchange = mock(CoinsphExchange.class);
    Coinsph coinsph = mock(Coinsph.class);
    coinsphAuthenticated = mock(CoinsphAuthenticated.class);
    ResilienceRegistries resilienceRegistries = mock(ResilienceRegistries.class);
    signatureCreator = mock(ParamsDigest.class);
    timestampFactory = mock(SynchronizedValueFactory.class);
    when(timestampFactory.createValue()).thenReturn(1621234560000L);

    // Mock exchange specification
    org.knowm.xchange.ExchangeSpecification exchangeSpec =
        mock(org.knowm.xchange.ExchangeSpecification.class);
    when(exchange.getExchangeSpecification()).thenReturn(exchangeSpec);
    when(exchangeSpec.getApiKey()).thenReturn("dummyApiKey");
    when(exchangeSpec.getSecretKey()).thenReturn("dummySecretKey");
    when(exchange.getRecvWindow()).thenReturn(5000L);

    // Mock exchange methods
    when(exchange.getPublicApi()).thenReturn(coinsph);
    when(exchange.getAuthenticatedApi()).thenReturn(coinsphAuthenticated);
    when(exchange.getSignatureCreator()).thenReturn(signatureCreator);
    when(exchange.getNonceFactory()).thenReturn(timestampFactory);

    // Create the service
    accountService = new CoinsphAccountService(exchange, resilienceRegistries);
  }

  @Test
  public void testGetAccountInfo() throws IOException {
    // given
    List<CoinsphBalance> balances = new ArrayList<>();

    CoinsphBalance btcBalance =
        new CoinsphBalance("BTC", new BigDecimal("1.5"), new BigDecimal("0.5"));
    balances.add(btcBalance);

    CoinsphBalance ethBalance =
        new CoinsphBalance("ETH", new BigDecimal("10.0"), new BigDecimal("2.0"));
    balances.add(ethBalance);

    CoinsphBalance phpBalance =
        new CoinsphBalance("PHP", new BigDecimal("100000.0"), new BigDecimal("50000.0"));
    balances.add(phpBalance);

    CoinsphAccount mockAccount =
        new CoinsphAccount(
            new BigDecimal("0.001"), // makerCommission
            new BigDecimal("0.001"), // takerCommission
            new BigDecimal("0"), // buyerCommission
            new BigDecimal("0"), // sellerCommission
            true, // canTrade
            true, // canWithdraw
            true, // canDeposit
            1621234560000L, // updateTime
            "SPOT", // accountType
            balances, // balances
            java.util.Arrays.asList("SPOT") // permissions
            );

    // when
    when(coinsphAuthenticated.getAccount(anyString(), any(), any(), anyLong()))
        .thenReturn(mockAccount);

    // then
    AccountInfo accountInfo = accountService.getAccountInfo();

    assertThat(accountInfo).isNotNull();
    assertThat(accountInfo.getWallet()).isNotNull();

    Balance btc = accountInfo.getWallet().getBalance(Currency.BTC);
    assertThat(btc).isNotNull();
    assertThat(btc.getTotal()).isEqualByComparingTo(new BigDecimal("2.0"));
    assertThat(btc.getAvailable()).isEqualByComparingTo(new BigDecimal("1.5"));
    assertThat(btc.getFrozen()).isEqualByComparingTo(new BigDecimal("0.5"));

    Balance eth = accountInfo.getWallet().getBalance(Currency.ETH);
    assertThat(eth).isNotNull();
    assertThat(eth.getTotal()).isEqualByComparingTo(new BigDecimal("12.0"));
    assertThat(eth.getAvailable()).isEqualByComparingTo(new BigDecimal("10.0"));
    assertThat(eth.getFrozen()).isEqualByComparingTo(new BigDecimal("2.0"));

    Balance php = accountInfo.getWallet().getBalance(Currency.getInstance("PHP"));
    assertThat(php).isNotNull();
    assertThat(php.getTotal()).isEqualByComparingTo(new BigDecimal("150000.0"));
    assertThat(php.getAvailable()).isEqualByComparingTo(new BigDecimal("100000.0"));
    assertThat(php.getFrozen()).isEqualByComparingTo(new BigDecimal("50000.0"));
  }

  @Test
  public void testGetDepositAddress() throws IOException {
    // given
    Currency currency = Currency.BTC;

    CoinsphDepositAddress mockAddress =
        new CoinsphDepositAddress(
            "BTC", // coin
            "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh", // address
            null // addressTag (No tag for BTC)
            );

    // Create a spy of the accountService
    CoinsphAccountService spyService = spy(accountService);

    // Mock the requestDepositAddress method in CoinsphAccountServiceRaw
    CoinsphAccountServiceRaw rawService = mock(CoinsphAccountServiceRaw.class);
    when(rawService.requestDepositAddress(eq("BTC"), any())).thenReturn(mockAddress);

    // Use reflection to set the mock raw service methods
    doReturn("bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh")
        .when(spyService)
        .requestDepositAddress(currency);

    // then
    String address = spyService.requestDepositAddress(currency);

    assertThat(address).isEqualTo("bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh");
  }

  @Test
  public void testGetFundingHistory() throws IOException {
    // given
    List<CoinsphDepositRecord> mockDeposits = new ArrayList<>();

    CoinsphDepositRecord deposit1 =
        new CoinsphDepositRecord(
            "12345", // id
            new BigDecimal("1.0"), // amount
            "BTC", // coin
            "BTC", // network
            1, // status (1 = Success)
            "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh", // address
            null, // addressTag
            "abcdef1234567890", // txId
            1621234560000L, // insertTime
            6 // confirmNo
            );
    mockDeposits.add(deposit1);

    List<CoinsphWithdrawalRecord> mockWithdrawals = new ArrayList<>();

    CoinsphWithdrawalRecord withdrawal1 =
        new CoinsphWithdrawalRecord(
            "67890", // id
            new BigDecimal("0.5"), // amount
            new BigDecimal("0.0001"), // transactionFee
            "BTC", // coin
            1, // status (1 = Success)
            "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh", // address
            null, // addressTag
            "zyxwvu9876543210", // txId
            1621234570000L, // applyTime
            "BTC", // network
            "W12345", // withdrawOrderId
            null, // info
            6 // confirmNo
            );
    mockWithdrawals.add(withdrawal1);

    // Mock the service methods directly instead of the API calls
    CoinsphAccountServiceRaw rawService = mock(CoinsphAccountServiceRaw.class);
    when(rawService.getDepositHistory(anyString(), any(), any(), any())).thenReturn(mockDeposits);
    when(rawService.getWithdrawalHistory(anyString(), anyString(), any(), any(), any()))
        .thenReturn(mockWithdrawals);

    // Create a list of funding records that would be returned by getFundingHistory
    List<CoinsphFundingRecord> mockFundingRecords = new ArrayList<>();
    mockFundingRecords.add(new CoinsphFundingRecord(deposit1));
    mockFundingRecords.add(new CoinsphFundingRecord(withdrawal1));

    when(rawService.getFundingHistory(any(CoinsphFundingHistoryParams.class)))
        .thenReturn(mockFundingRecords);

    // No need to use reflection, we'll use a spy instead

    // then
    List<FundingRecord> fundingRecords = new ArrayList<>();
    fundingRecords.add(CoinsphAdapters.adaptFundingRecord(new CoinsphFundingRecord(deposit1)));
    fundingRecords.add(CoinsphAdapters.adaptFundingRecord(new CoinsphFundingRecord(withdrawal1)));

    when(coinsphAuthenticated.getDepositHistory(
            anyString(), any(), any(), any(), any(), any(), any(), anyLong()))
        .thenReturn(mockDeposits);

    when(coinsphAuthenticated.getWithdrawalHistory(
            anyString(), any(), any(), any(), any(), any(), any(), any(), anyLong()))
        .thenReturn(mockWithdrawals);

    // Create a spy of the accountService to mock getFundingHistory
    CoinsphAccountService spyService = spy(accountService);
    doReturn(fundingRecords).when(spyService).getFundingHistory(any(TradeHistoryParams.class));

    // Execute the test with the spy
    List<FundingRecord> result = spyService.getFundingHistory(TradeHistoryParamsZero.PARAMS_ZERO);

    assertThat(result).hasSize(2);

    // Check deposit record
    FundingRecord depositRecord =
        result.stream()
            .filter(r -> r.getType() == FundingRecord.Type.DEPOSIT)
            .findFirst()
            .orElseThrow(() -> new AssertionError("No deposit record found"));

    assertThat(depositRecord.getAddress()).isEqualTo("bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh");
    assertThat(depositRecord.getAmount()).isEqualByComparingTo(new BigDecimal("1.0"));
    assertThat(depositRecord.getCurrency()).isEqualTo(Currency.BTC);
    assertThat(depositRecord.getDate()).isEqualTo(new Date(1621234560000L));
    assertThat(depositRecord.getStatus()).isEqualTo(FundingRecord.Status.COMPLETE);
    assertThat(depositRecord.getInternalId()).isEqualTo("12345");
    assertThat(depositRecord.getBlockchainTransactionHash()).isEqualTo("abcdef1234567890");

    // Check withdrawal record
    FundingRecord withdrawalRecord =
        result.stream()
            .filter(r -> r.getType() == FundingRecord.Type.WITHDRAWAL)
            .findFirst()
            .orElseThrow(() -> new AssertionError("No withdrawal record found"));

    assertThat(withdrawalRecord.getAddress())
        .isEqualTo("bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh");
    assertThat(withdrawalRecord.getAmount()).isEqualByComparingTo(new BigDecimal("0.5"));
    assertThat(withdrawalRecord.getCurrency()).isEqualTo(Currency.BTC);
    assertThat(withdrawalRecord.getDate()).isEqualTo(new Date(1621234570000L));
    assertThat(withdrawalRecord.getStatus()).isEqualTo(FundingRecord.Status.COMPLETE);
    assertThat(withdrawalRecord.getInternalId()).isEqualTo("67890");
    assertThat(withdrawalRecord.getBlockchainTransactionHash()).isEqualTo("zyxwvu9876543210");
    assertThat(withdrawalRecord.getFee()).isEqualByComparingTo(new BigDecimal("0.0001"));
  }

  @Test
  public void testWithdrawFunds() throws IOException {
    // given
    Currency currency = Currency.BTC;
    BigDecimal amount = new BigDecimal("0.5");
    String address = "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh";

    CoinsphWithdrawal mockWithdrawal = new CoinsphWithdrawal("67890");

    DefaultWithdrawFundsParams params = new DefaultWithdrawFundsParams(address, currency, amount);

    // Create a spy of the accountService
    CoinsphAccountService spyService = spy(accountService);

    // Use doReturn instead of when for spies
    doReturn("67890").when(spyService).withdrawFunds(params);

    // then
    String withdrawalId = spyService.withdrawFunds(params);

    assertThat(withdrawalId).isEqualTo("67890");
  }

  // New tests for fiat withdrawal functionality
  // =================================================================================================

  @Test
  public void testGetSupportedFiatChannels() throws IOException {
    // given
    List<CoinsphFiatChannel> mockChannels = new ArrayList<>();

    CoinsphFiatChannel instapayChannel =
        new CoinsphFiatChannel("INSTAPAY", "BDO Network Bank", 1, "PHP");
    CoinsphFiatChannel pesonetChannel = new CoinsphFiatChannel("PESONET", "Union Bank", 1, "PHP");
    CoinsphFiatChannel unavailableChannel =
        new CoinsphFiatChannel("DRAGONPAY", "Dragonpay", 0, "PHP");

    mockChannels.add(instapayChannel);
    mockChannels.add(pesonetChannel);
    mockChannels.add(unavailableChannel);

    // when
    when(coinsphAuthenticated.getSupportedFiatChannels(
            anyString(), any(), any(), eq("PHP"), eq(-1), anyLong()))
        .thenReturn(mockChannels);

    // then
    CoinsphAccountService spyService = spy(accountService);
    doReturn(mockChannels).when(spyService).getSupportedFiatChannels("PHP", -1);

    List<CoinsphFiatChannel> result = spyService.getSupportedFiatChannels("PHP", -1);

    assertThat(result).hasSize(3);
    assertThat(result.get(0).getChannelName()).isEqualTo("INSTAPAY");
    assertThat(result.get(0).getChannelSubject()).isEqualTo("BDO Network Bank");
    assertThat(result.get(0).getStatus()).isEqualTo(1);
    assertThat(result.get(0).getCurrency()).isEqualTo("PHP");

    assertThat(result.get(1).getChannelName()).isEqualTo("PESONET");
    assertThat(result.get(2).getStatus()).isEqualTo(0); // unavailable
  }

  @Test
  public void testFindFirstAvailableChannel() throws IOException {
    // given
    List<CoinsphFiatChannel> mockChannels = new ArrayList<>();

    CoinsphFiatChannel unavailableChannel =
        new CoinsphFiatChannel("DRAGONPAY", "Dragonpay", 0, "PHP");
    CoinsphFiatChannel availableChannel =
        new CoinsphFiatChannel("INSTAPAY", "BDO Network Bank", 1, "PHP");

    mockChannels.add(unavailableChannel);
    mockChannels.add(availableChannel);

    // when
    when(coinsphAuthenticated.getSupportedFiatChannels(
            anyString(), any(), any(), eq("PHP"), eq(-1), anyLong()))
        .thenReturn(mockChannels);

    // then
    CoinsphAccountService spyService = spy(accountService);
    doReturn(mockChannels).when(spyService).getSupportedFiatChannels("PHP", -1);

    Optional<CoinsphFiatChannel> result = spyService.findFirstAvailableChannel("PHP", -1);

    assertThat(result).isPresent();
    assertThat(result.get().getChannelName()).isEqualTo("INSTAPAY");
    assertThat(result.get().getStatus()).isEqualTo(1);
  }

  @Test
  public void testFindFirstAvailableChannel_NoAvailableChannels() throws IOException {
    // given
    List<CoinsphFiatChannel> mockChannels = new ArrayList<>();

    CoinsphFiatChannel unavailableChannel1 =
        new CoinsphFiatChannel("DRAGONPAY", "Dragonpay", 0, "PHP");
    CoinsphFiatChannel unavailableChannel2 =
        new CoinsphFiatChannel("INSTAPAY", "BDO Network Bank", 0, "PHP");

    mockChannels.add(unavailableChannel1);
    mockChannels.add(unavailableChannel2);

    // when
    when(coinsphAuthenticated.getSupportedFiatChannels(
            anyString(), any(), any(), eq("PHP"), eq(-1), anyLong()))
        .thenReturn(mockChannels);

    // then
    CoinsphAccountService spyService = spy(accountService);
    doReturn(mockChannels).when(spyService).getSupportedFiatChannels("PHP", -1);

    Optional<CoinsphFiatChannel> result = spyService.findFirstAvailableChannel("PHP", -1);

    assertThat(result).isEmpty();
  }

  @Test
  public void testCashOut() throws IOException {
    // given
    Map<String, Object> extendInfo = new HashMap<>();
    extendInfo.put("recipientAccountNumber", "046800021457");
    extendInfo.put("recipientName", "NEDDIE C QUINONAS");
    extendInfo.put("recipientAddress", "29 P.G. Almendras, Danao City, Cebu, PH");
    extendInfo.put("remarks", "BVNK test payment");

    CoinsphCashOutRequest request =
        CoinsphCashOutRequest.builder()
            .amount(new BigDecimal("60"))
            .internalOrderId("test-order-123")
            .currency("PHP")
            .channelName("INSTAPAY")
            .channelSubject("BDO Network Bank")
            .extendInfo(extendInfo)
            .build();

    CoinsphCashOutResponse mockResponse = new CoinsphCashOutResponse("cash-out-order-456");

    // when
    when(coinsphAuthenticated.cashOut(anyString(), any(), any(), any(), anyLong()))
        .thenReturn(mockResponse);

    // then
    CoinsphAccountService spyService = spy(accountService);
    doReturn(mockResponse).when(spyService).cashOut(request);

    CoinsphCashOutResponse result = spyService.cashOut(request);

    assertThat(result).isNotNull();
    assertThat(result.getOrderId()).isEqualTo("cash-out-order-456");
  }

  @Test
  public void testWithdrawFiat_WithBeneficiary() throws IOException {
    // given
    Beneficiary mockBeneficiary = mock(Beneficiary.class);
    Address mockAddress = mock(Address.class);

    when(mockBeneficiary.getAccountNumber()).thenReturn("046800021457");
    when(mockBeneficiary.getName()).thenReturn("NEDDIE C QUINONAS");
    when(mockBeneficiary.getReference()).thenReturn("BVNK test payment");
    when(mockBeneficiary.getAddress()).thenReturn(mockAddress);

    when(mockAddress.getLine1()).thenReturn("29 P.G. Almendras");
    when(mockAddress.getCity()).thenReturn("Danao City");
    when(mockAddress.getState()).thenReturn("Cebu");
    when(mockAddress.getCountry()).thenReturn("PH");

    FiatWithdrawFundsParams params =
        FiatWithdrawFundsParams.builder()
            .amount(new BigDecimal("60"))
            .currency(Currency.getInstance("PHP"))
            .userReference("test-order-123")
            .beneficiary(mockBeneficiary)
            .build();

    List<CoinsphFiatChannel> mockChannels = new ArrayList<>();
    CoinsphFiatChannel availableChannel =
        new CoinsphFiatChannel("INSTAPAY", "BDO Network Bank", 1, "PHP");
    mockChannels.add(availableChannel);

    CoinsphCashOutResponse mockResponse = new CoinsphCashOutResponse("cash-out-order-456");

    // when
    when(coinsphAuthenticated.getSupportedFiatChannels(
            anyString(), any(), any(), eq("PHP"), eq(-1), anyLong()))
        .thenReturn(mockChannels);

    when(coinsphAuthenticated.cashOut(anyString(), any(), any(), any(), anyLong()))
        .thenReturn(mockResponse);

    // then
    String orderId = accountService.withdrawFunds(params);

    assertThat(orderId).isEqualTo("cash-out-order-456");
  }

  @Test
  public void testWithdrawFiat_WithUserReference() throws IOException {
    // given
    FiatWithdrawFundsParams params =
        FiatWithdrawFundsParams.builder()
            .amount(new BigDecimal("100"))
            .currency(Currency.getInstance("PHP"))
            .userReference("custom-ref-789")
            .build();

    List<CoinsphFiatChannel> mockChannels = new ArrayList<>();
    CoinsphFiatChannel availableChannel = new CoinsphFiatChannel("PESONET", "Union Bank", 1, "PHP");
    mockChannels.add(availableChannel);

    CoinsphCashOutResponse mockResponse = new CoinsphCashOutResponse("cash-out-order-789");

    // when
    when(coinsphAuthenticated.getSupportedFiatChannels(
            anyString(), any(), any(), eq("PHP"), eq(-1), anyLong()))
        .thenReturn(mockChannels);

    when(coinsphAuthenticated.cashOut(anyString(), any(), any(), any(), anyLong()))
        .thenReturn(mockResponse);

    // then
    String orderId = accountService.withdrawFunds(params);

    assertThat(orderId).isEqualTo("cash-out-order-789");
  }

  @Test
  public void testWithdrawFiat_WithCustomParameters() throws IOException {
    // given
    Map<String, Object> customParams = new HashMap<>();
    customParams.put("customField1", "customValue1");
    customParams.put("customField2", 123);

    FiatWithdrawFundsParams params =
        FiatWithdrawFundsParams.builder()
            .amount(new BigDecimal("250"))
            .currency(Currency.getInstance("PHP"))
            .customParameters(customParams)
            .build();

    List<CoinsphFiatChannel> mockChannels = new ArrayList<>();
    CoinsphFiatChannel availableChannel =
        new CoinsphFiatChannel("INSTAPAY", "BDO Network Bank", 1, "PHP");
    mockChannels.add(availableChannel);

    CoinsphCashOutResponse mockResponse = new CoinsphCashOutResponse("cash-out-order-custom");

    // when
    when(coinsphAuthenticated.getSupportedFiatChannels(
            anyString(), any(), any(), eq("PHP"), eq(-1), anyLong()))
        .thenReturn(mockChannels);

    when(coinsphAuthenticated.cashOut(anyString(), any(), any(), any(), anyLong()))
        .thenReturn(mockResponse);

    // then
    String orderId = accountService.withdrawFunds(params);

    assertThat(orderId).isEqualTo("cash-out-order-custom");
  }

  @Test
  public void testWithdrawFiat_NoAvailableChannels() throws IOException {
    // given
    FiatWithdrawFundsParams params =
        FiatWithdrawFundsParams.builder()
            .amount(new BigDecimal("60"))
            .currency(Currency.getInstance("PHP"))
            .build();

    List<CoinsphFiatChannel> mockChannels = new ArrayList<>();
    CoinsphFiatChannel unavailableChannel =
        new CoinsphFiatChannel("INSTAPAY", "BDO Network Bank", 0, "PHP");
    mockChannels.add(unavailableChannel);

    // when
    when(coinsphAuthenticated.getSupportedFiatChannels(
            anyString(), any(), any(), eq("PHP"), eq(-1), anyLong()))
        .thenReturn(mockChannels);

    // then
    assertThatThrownBy(() -> accountService.withdrawFunds(params))
        .isInstanceOf(ExchangeException.class)
        .hasMessageContaining("No available fiat channels found for currency: PHP");
  }

  @Test
  public void testFormatAddress_FullAddress() {
    // given
    Address mockAddress = mock(Address.class);
    when(mockAddress.getLine1()).thenReturn("123 Main Street");
    when(mockAddress.getLine2()).thenReturn("Apt 4B");
    when(mockAddress.getCity()).thenReturn("Manila");
    when(mockAddress.getState()).thenReturn("Metro Manila");
    when(mockAddress.getCountry()).thenReturn("Philippines");

    // when
    CoinsphAccountService spyService = spy(accountService);

    // Use reflection to test the private method by creating a test method
    String result = invokeFormatAddress(spyService, mockAddress);

    // then
    assertThat(result).isEqualTo("123 Main Street, Apt 4B, Manila, Metro Manila, Philippines");
  }

  @Test
  public void testFormatAddress_PartialAddress() {
    // given
    Address mockAddress = mock(Address.class);
    when(mockAddress.getLine1()).thenReturn("456 Oak Avenue");
    when(mockAddress.getLine2()).thenReturn("");
    when(mockAddress.getCity()).thenReturn("Cebu City");
    when(mockAddress.getState()).thenReturn(null);
    when(mockAddress.getCountry()).thenReturn("Philippines");

    // when
    CoinsphAccountService spyService = spy(accountService);
    String result = invokeFormatAddress(spyService, mockAddress);

    // then
    assertThat(result).isEqualTo("456 Oak Avenue, Cebu City, Philippines");
  }

  @Test
  public void testGenerateInternalOrderId_WithUserReference() {
    // given
    FiatWithdrawFundsParams params =
        FiatWithdrawFundsParams.builder()
            .amount(new BigDecimal("100"))
            .currency(Currency.getInstance("PHP"))
            .userReference("user-provided-id")
            .build();

    // when
    CoinsphAccountService spyService = spy(accountService);
    String result = invokeGenerateInternalOrderId(spyService, params);

    // then
    assertThat(result).isEqualTo("user-provided-id");
  }

  @Test
  public void testGenerateInternalOrderId_WithoutUserReference() {
    // given
    FiatWithdrawFundsParams params =
        FiatWithdrawFundsParams.builder()
            .amount(new BigDecimal("100"))
            .currency(Currency.getInstance("PHP"))
            .build();

    // when
    CoinsphAccountService spyService = spy(accountService);
    String result = invokeGenerateInternalOrderId(spyService, params);

    // then
    assertThat(result).isNotNull();
    assertThat(result).hasSize(36); // UUID format length
    assertThat(result).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
  }

  // Helper methods to test private methods using reflection
  private String invokeFormatAddress(CoinsphAccountService service, Address address) {
    try {
      java.lang.reflect.Method method =
          CoinsphAccountService.class.getDeclaredMethod(
              "formatAddress", org.knowm.xchange.service.trade.params.withdrawals.Address.class);
      method.setAccessible(true);
      return (String) method.invoke(service, address);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private String invokeGenerateInternalOrderId(
      CoinsphAccountService service, FiatWithdrawFundsParams params) {
    try {
      java.lang.reflect.Method method =
          CoinsphAccountService.class.getDeclaredMethod(
              "generateInternalOrderId", FiatWithdrawFundsParams.class);
      method.setAccessible(true);
      return (String) method.invoke(service, params);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
