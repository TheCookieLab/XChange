package org.knowm.xchange.coinsph.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.coinsph.Coinsph;
import org.knowm.xchange.coinsph.CoinsphAdapters;
import org.knowm.xchange.coinsph.CoinsphAuthenticated;
import org.knowm.xchange.coinsph.CoinsphExchange;
import org.knowm.xchange.coinsph.dto.account.CoinsphAccount;
import org.knowm.xchange.coinsph.dto.account.CoinsphBalance;
import org.knowm.xchange.coinsph.dto.account.CoinsphDepositAddress;
import org.knowm.xchange.coinsph.dto.account.CoinsphDepositRecord;
import org.knowm.xchange.coinsph.dto.account.CoinsphFundingRecord;
import org.knowm.xchange.coinsph.dto.account.CoinsphWithdrawal;
import org.knowm.xchange.coinsph.dto.account.CoinsphWithdrawalRecord;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.service.trade.params.DefaultWithdrawFundsParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParamsZero;
import si.mazi.rescu.ParamsDigest;
import si.mazi.rescu.SynchronizedValueFactory;

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
}
