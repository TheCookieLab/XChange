package org.knowm.xchange.coinsph.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.coinsph.CoinsphAuthenticated;
import org.knowm.xchange.coinsph.CoinsphExchange;
import org.knowm.xchange.coinsph.dto.account.CoinsphCashOutRequest;
import org.knowm.xchange.coinsph.dto.account.CoinsphCashOutResponse;
import org.knowm.xchange.coinsph.dto.account.CoinsphFiatChannel;
import si.mazi.rescu.ParamsDigest;
import si.mazi.rescu.SynchronizedValueFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CoinsphAccountServiceRawTest {

  private CoinsphAccountServiceRaw accountServiceRaw;
  private CoinsphAuthenticated coinsphAuthenticated;
  private CoinsphExchange exchange;
  private SynchronizedValueFactory<Long> timestampFactory;
  private ParamsDigest signatureCreator;

  @BeforeEach
  public void setUp() {
    exchange = mock(CoinsphExchange.class);
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
    when(exchange.getAuthenticatedApi()).thenReturn(coinsphAuthenticated);
    when(exchange.getSignatureCreator()).thenReturn(signatureCreator);
    when(exchange.getNonceFactory()).thenReturn(timestampFactory);

    // Create the raw service
    accountServiceRaw = new CoinsphAccountServiceRaw(exchange, resilienceRegistries);
  }

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
            eq("dummyApiKey"),
            eq(timestampFactory),
            eq(signatureCreator),
            eq("PHP"),
            eq(-1),
            eq(5000L)))
        .thenReturn(mockChannels);

    // then
    List<CoinsphFiatChannel> result = accountServiceRaw.getSupportedFiatChannels("PHP", -1);

    assertThat(result).hasSize(3);
    assertThat(result.get(0).getChannelName()).isEqualTo("INSTAPAY");
    assertThat(result.get(0).getChannelSubject()).isEqualTo("BDO Network Bank");
    assertThat(result.get(0).getStatus()).isEqualTo(1);
    assertThat(result.get(0).getCurrency()).isEqualTo("PHP");

    assertThat(result.get(1).getChannelName()).isEqualTo("PESONET");
    assertThat(result.get(1).getChannelSubject()).isEqualTo("Union Bank");
    assertThat(result.get(1).getStatus()).isEqualTo(1);

    assertThat(result.get(2).getChannelName()).isEqualTo("DRAGONPAY");
    assertThat(result.get(2).getStatus()).isEqualTo(0); // unavailable
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
    when(coinsphAuthenticated.cashOut(
            eq("dummyApiKey"), eq(timestampFactory), eq(signatureCreator), eq(request), eq(5000L)))
        .thenReturn(mockResponse);

    // then
    CoinsphCashOutResponse result = accountServiceRaw.cashOut(request);

    assertThat(result).isNotNull();
    assertThat(result.getOrderId()).isEqualTo("cash-out-order-456");
  }

  @Test
  public void testFindFirstAvailableChannel_Found() throws IOException {
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
            eq("dummyApiKey"),
            eq(timestampFactory),
            eq(signatureCreator),
            eq("PHP"),
            eq(-1),
            eq(5000L)))
        .thenReturn(mockChannels);

    // then
    Optional<CoinsphFiatChannel> result = accountServiceRaw.findFirstAvailableChannel("PHP", -1);

    assertThat(result).isPresent();
    assertThat(result.get().getChannelName()).isEqualTo("INSTAPAY");
    assertThat(result.get().getChannelSubject()).isEqualTo("BDO Network Bank");
    assertThat(result.get().getStatus()).isEqualTo(1);
    assertThat(result.get().getCurrency()).isEqualTo("PHP");
  }

  @Test
  public void testFindFirstAvailableChannel_NotFound() throws IOException {
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
            eq("dummyApiKey"),
            eq(timestampFactory),
            eq(signatureCreator),
            eq("PHP"),
            eq(-1),
            eq(5000L)))
        .thenReturn(mockChannels);

    // then
    Optional<CoinsphFiatChannel> result = accountServiceRaw.findFirstAvailableChannel("PHP", -1);

    assertThat(result).isEmpty();
  }

  @Test
  public void testFindFirstAvailableChannel_EmptyList() throws IOException {
    // given
    List<CoinsphFiatChannel> mockChannels = new ArrayList<>();

    // when
    when(coinsphAuthenticated.getSupportedFiatChannels(
            eq("dummyApiKey"),
            eq(timestampFactory),
            eq(signatureCreator),
            eq("PHP"),
            eq(-1),
            eq(5000L)))
        .thenReturn(mockChannels);

    // then
    Optional<CoinsphFiatChannel> result = accountServiceRaw.findFirstAvailableChannel("PHP", -1);

    assertThat(result).isEmpty();
  }

  @Test
  public void testGetSupportedFiatChannels_DifferentCurrency() throws IOException {
    // given
    List<CoinsphFiatChannel> mockChannels = new ArrayList<>();

    CoinsphFiatChannel usdChannel =
        new CoinsphFiatChannel("SWIFT", "SWIFT Bank Transfer", 1, "USD");

    mockChannels.add(usdChannel);

    // when
    when(coinsphAuthenticated.getSupportedFiatChannels(
            eq("dummyApiKey"),
            eq(timestampFactory),
            eq(signatureCreator),
            eq("USD"),
            eq(-1),
            eq(5000L)))
        .thenReturn(mockChannels);

    // then
    List<CoinsphFiatChannel> result = accountServiceRaw.getSupportedFiatChannels("USD", -1);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getChannelName()).isEqualTo("SWIFT");
    assertThat(result.get(0).getChannelSubject()).isEqualTo("SWIFT Bank Transfer");
    assertThat(result.get(0).getStatus()).isEqualTo(1);
    assertThat(result.get(0).getCurrency()).isEqualTo("USD");
  }

  @Test
  public void testCashOut_MinimalRequest() throws IOException {
    // given
    CoinsphCashOutRequest request =
        CoinsphCashOutRequest.builder()
            .amount(new BigDecimal("100"))
            .internalOrderId("minimal-order-id")
            .currency("PHP")
            .channelName("PESONET")
            .channelSubject("Union Bank")
            .extendInfo(new HashMap<>())
            .build();

    CoinsphCashOutResponse mockResponse = new CoinsphCashOutResponse("minimal-cash-out-order");

    // when
    when(coinsphAuthenticated.cashOut(
            eq("dummyApiKey"), eq(timestampFactory), eq(signatureCreator), eq(request), eq(5000L)))
        .thenReturn(mockResponse);

    // then
    CoinsphCashOutResponse result = accountServiceRaw.cashOut(request);

    assertThat(result).isNotNull();
    assertThat(result.getOrderId()).isEqualTo("minimal-cash-out-order");
  }

  @Test
  public void testCashOut_ComplexExtendInfo() throws IOException {
    // given
    Map<String, Object> extendInfo = new HashMap<>();
    extendInfo.put("recipientAccountNumber", "098765432109");
    extendInfo.put("recipientName", "JUAN DELA CRUZ");
    extendInfo.put("recipientAddress", "123 Rizal Street, Makati City, Metro Manila, Philippines");
    extendInfo.put("remarks", "Monthly allowance");
    extendInfo.put("recipientMobileNumber", "+639123456789");
    extendInfo.put("purposeOfTransfer", "Family support");

    CoinsphCashOutRequest request =
        CoinsphCashOutRequest.builder()
            .amount(new BigDecimal("5000"))
            .internalOrderId("complex-order-xyz")
            .currency("PHP")
            .channelName("INSTAPAY")
            .channelSubject("BPI")
            .extendInfo(extendInfo)
            .build();

    CoinsphCashOutResponse mockResponse = new CoinsphCashOutResponse("complex-cash-out-order");

    // when
    when(coinsphAuthenticated.cashOut(
            eq("dummyApiKey"), eq(timestampFactory), eq(signatureCreator), eq(request), eq(5000L)))
        .thenReturn(mockResponse);

    // then
    CoinsphCashOutResponse result = accountServiceRaw.cashOut(request);

    assertThat(result).isNotNull();
    assertThat(result.getOrderId()).isEqualTo("complex-cash-out-order");
  }
}
