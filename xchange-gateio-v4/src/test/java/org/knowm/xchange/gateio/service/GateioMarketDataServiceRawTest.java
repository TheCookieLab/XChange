package org.knowm.xchange.gateio.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.gateio.GateioExchangeWiremock;
import org.knowm.xchange.gateio.dto.marketdata.GateioCurrencyChain;
import org.knowm.xchange.gateio.dto.marketdata.GateioCurrencyInfo;
import org.knowm.xchange.gateio.dto.marketdata.GateioCurrencyInfo.Chain;
import org.knowm.xchange.gateio.dto.marketdata.GateioCurrencyPairDetails;
import org.knowm.xchange.gateio.dto.marketdata.GateioOrderBook;
import org.knowm.xchange.gateio.dto.marketdata.GateioOrderBook.PriceSizeEntry;
import org.knowm.xchange.gateio.dto.marketdata.GateioCandleStick;
import org.knowm.xchange.gateio.dto.marketdata.GateioTrade;

public class GateioMarketDataServiceRawTest extends GateioExchangeWiremock {

  GateioMarketDataServiceRaw gateioMarketDataServiceRaw =
      (GateioMarketDataServiceRaw) exchange.getMarketDataService();

  @Test
  public void getCurrencies_valid() throws IOException {
    List<GateioCurrencyInfo> actual = gateioMarketDataServiceRaw.getGateioCurrencyInfos();

    assertThat(actual).hasSize(2);

    GateioCurrencyInfo actualBtc = actual.get(0);

    GateioCurrencyInfo expectedBtc =
        GateioCurrencyInfo.builder()
            .currency(Currency.BTC)
            .delisted(false)
            .withdrawDisabled(false)
            .withdrawDelayed(false)
            .depositDisabled(false)
            .tradeDisabled(false)
            .mainChain("BTC")
            .chains(
                Stream.of(
                        Chain.builder()
                            .name("BTC")
                            .address("")
                            .depositDisabled(false)
                            .withdrawDelayed(false)
                            .withdrawDisabled(false)
                            .build(),
                        Chain.builder()
                            .name("BSC")
                            .address("0x7130d2a12b9bcbfae4f2634d864a1ee1ce3ead9c")
                            .depositDisabled(false)
                            .withdrawDelayed(false)
                            .withdrawDisabled(false)
                            .build())
                    .collect(Collectors.toList()))
            .build();

    assertThat(actualBtc).usingRecursiveComparison().isEqualTo(expectedBtc);
  }

  @Test
  public void getGateioOrderBook_valid() throws IOException {
    List<PriceSizeEntry> expectedAsks = new ArrayList<>();
    expectedAsks.add(
        PriceSizeEntry.builder().price(new BigDecimal("200")).size(BigDecimal.ONE).build());
    expectedAsks.add(
        PriceSizeEntry.builder().price(new BigDecimal("250")).size(BigDecimal.TEN).build());

    List<PriceSizeEntry> expectedBids = new ArrayList<>();
    expectedBids.add(
        PriceSizeEntry.builder().price(new BigDecimal("150")).size(BigDecimal.ONE).build());
    expectedBids.add(
        PriceSizeEntry.builder().price(new BigDecimal("100")).size(BigDecimal.TEN).build());

    GateioOrderBook expected =
        GateioOrderBook.builder()
            .generatedAt(Instant.parse("2023-05-14T22:10:10.493Z"))
            .updatedAt(Instant.parse("2023-05-14T22:10:10.263Z"))
            .asks(expectedAsks)
            .bids(expectedBids)
            .build();

    GateioOrderBook actual = gateioMarketDataServiceRaw.getGateioOrderBook(CurrencyPair.BTC_USDT);

    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
  }

  @Test
  public void getCurrencyChains_valid_result() throws IOException {
    List<GateioCurrencyChain> expected =
        Arrays.asList(
            GateioCurrencyChain.builder()
                .chain("BTC")
                .chainNameCN("比特币 BRC20/Ordinals")
                .chainNameEN("Bitcoin BRC20/Ordinals")
                .disabled(false)
                .depositDisabled(false)
                .withdrawDisabled(false)
                .contractAddress("")
                .build(),
            GateioCurrencyChain.builder()
                .chain("HT")
                .chainNameCN("Heco")
                .chainNameEN("Heco")
                .disabled(true)
                .depositDisabled(true)
                .withdrawDisabled(true)
                .contractAddress("0x66a79d23e58475d2738179ca52cd0b41d73f0bea")
                .build());

    List<GateioCurrencyChain> actual = gateioMarketDataServiceRaw.getCurrencyChains(Currency.BTC);

    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
  }

  @Test
  void valid_currencypairs_details() throws IOException {
    List<GateioCurrencyPairDetails> details = gateioMarketDataServiceRaw.getCurrencyPairDetails();
    assertThat(details).hasSize(3);
    GateioCurrencyPairDetails expectedChz =
        GateioCurrencyPairDetails.builder()
            .id("CHZ_USDT")
            .asset("CHZ")
            .quote("USDT")
            .fee(new BigDecimal("0.2"))
            .minQuoteAmount(BigDecimal.ONE)
            .assetScale(2)
            .quoteScale(5)
            .tradeStatus("tradable")
            .startOfSells(Instant.parse("2020-12-24T04:00:00.000Z"))
            .startOfBuys(Instant.parse("2020-12-24T06:00:00.000Z"))
            .build();

    GateioCurrencyPairDetails actualChz = details.get(1);

    assertThat(actualChz).isEqualTo(expectedChz);
  }

  @Test
  void valid_single_currencypair_details() throws IOException {
    GateioCurrencyPairDetails actualChz =
        gateioMarketDataServiceRaw.getCurrencyPairDetails(new CurrencyPair("CHZ/USDT"));
    GateioCurrencyPairDetails expectedChz =
        GateioCurrencyPairDetails.builder()
            .id("CHZ_USDT")
            .asset("CHZ")
            .quote("USDT")
            .fee(new BigDecimal("0.2"))
            .minQuoteAmount(BigDecimal.ONE)
            .assetScale(2)
            .quoteScale(5)
            .tradeStatus("tradable")
            .startOfSells(Instant.parse("2020-12-24T04:00:00.000Z"))
            .startOfBuys(Instant.parse("2020-12-24T06:00:00.000Z"))
            .build();

    assertThat(actualChz).isEqualTo(expectedChz);
  }

  @Test
  void getCurrencyInfo_valid() throws IOException {
    GateioCurrencyInfo actual = gateioMarketDataServiceRaw.getGateioCurrencyInfo(Currency.BTC);

    assertThat(actual.getCurrency()).isEqualTo(Currency.BTC);
    assertThat(actual.getDelisted()).isFalse();
    assertThat(actual.getMainChain()).isEqualTo("BTC");
    assertThat(actual.getChains()).hasSize(2);
    assertThat(actual.getChains().get(1).getName()).isEqualTo("BSC");
  }

  @Test
  void getTrades_valid() throws IOException {
    List<GateioTrade> actual =
        gateioMarketDataServiceRaw.getGateioTrades(CurrencyPair.BTC_USDT, null, null, null, null);

    assertThat(actual).hasSize(2);

    GateioTrade maker = actual.get(0);
    assertThat(maker.getId()).isEqualTo("6068816979");
    assertThat(maker.getSide()).isEqualTo("buy");
    assertThat(maker.getRole()).isEqualTo("maker");
    assertThat(maker.getAmount()).isEqualByComparingTo("0.00003");
    assertThat(maker.getPrice()).isEqualByComparingTo("29454.6");
    assertThat(maker.getOrderId()).isEqualTo("381068734893");
    assertThat(maker.getCreateTimeMs()).isEqualByComparingTo("1691702924010.071000");

    GateioTrade taker = actual.get(1);
    assertThat(taker.getSide()).isEqualTo("sell");
    assertThat(taker.getRole()).isEqualTo("taker");
    assertThat(taker.getCurrencyPair()).isEqualTo(CurrencyPair.BTC_USDT);
  }

  @Test
  void getCandlesticks_valid() throws IOException {
    List<GateioCandleStick> actual =
        gateioMarketDataServiceRaw.getGateioCandlesticks(
            CurrencyPair.BTC_USDT, "1h", null, null, null);

    assertThat(actual).hasSize(2);

    GateioCandleStick first = actual.get(0);
    assertThat(first.getTime()).isEqualTo(1691700000L);
    assertThat(first.getOpen()).isEqualByComparingTo("29450.0");
    assertThat(first.getClose()).isEqualByComparingTo("29454.6");
    assertThat(first.getHigh()).isEqualByComparingTo("29460.0");
    assertThat(first.getLow()).isEqualByComparingTo("29440.0");
    assertThat(first.getQuoteVolume()).isEqualByComparingTo("12345.67");
    assertThat(first.getBaseVolume()).isEqualByComparingTo("0.42");
    assertThat(first.isClosed()).isTrue();

    GateioCandleStick second = actual.get(1);
    assertThat(second.getTime()).isEqualTo(1691703600L);
    assertThat(second.isClosed()).isFalse();
  }
}
