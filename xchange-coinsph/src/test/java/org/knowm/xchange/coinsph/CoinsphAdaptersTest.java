package org.knowm.xchange.coinsph;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.coinsph.dto.account.CoinsphWithdrawalRecord;
import org.knowm.xchange.coinsph.dto.meta.CoinsphExchangeInfo;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderStatus;
import org.knowm.xchange.dto.marketdata.Trades.TradeSortType;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.instrument.Instrument;

class CoinsphAdaptersTest {

  @Test
  void symbolBoundariesAndOverlappingUsdtAreHandled() {
    assertThat(CoinsphAdapters.toCurrencyPair(null)).isNull();
    assertThat(CoinsphAdapters.toCurrencyPair("")).isNull();
    assertThat(CoinsphAdapters.toCurrencyPair("BTC")).isNull();
    assertThat(CoinsphAdapters.toCurrencyPair("BTCPHP")).isEqualTo(CurrencyPair.BTC_PHP);
    assertThat(CoinsphAdapters.toCurrencyPair("BTCUSDT"))
        .isEqualTo(new CurrencyPair("BTC", "USDT"));
    assertThat(CoinsphAdapters.toCurrencyPair("USDTUSDT"))
        .isEqualTo(new CurrencyPair("USDT", "USDT"));
    assertThat(CoinsphAdapters.toSymbol((CurrencyPair) null)).isNull();
    assertThat(CoinsphAdapters.toSymbol((Instrument) null)).isNull();
  }

  @Test
  void metadataUsesExchangeAssetsAndBothQuotePrecisionSpellings() throws Exception {
    String json =
        "{\"symbols\":["
            + "{\"symbol\":\"BTCPHP\",\"baseAsset\":\"BTC\",\"baseAssetPrecision\":8,"
            + "\"quoteAsset\":\"PHP\",\"quoteAssetPrecision\":3},"
            + "{\"symbol\":\"SHIBUSDT\",\"baseAsset\":\"SHIB\",\"baseAssetPrecision\":2,"
            + "\"quoteAsset\":\"USDT\",\"quotePrecision\":6},"
            + "{\"symbol\":\"BTCUSDC\",\"baseAsset\":\"BTC\",\"baseAssetPrecision\":8,"
            + "\"quoteAsset\":\"USDC\",\"quoteAssetPrecision\":5},"
            + "{\"symbol\":\"ETHUSD\",\"baseAssetPrecision\":8,\"quoteAssetPrecision\":2},"
            + "{\"symbol\":\"bad\"}]}";
    CoinsphExchangeInfo info = new ObjectMapper().readValue(json, CoinsphExchangeInfo.class);

    ExchangeMetaData metadata = CoinsphAdapters.adaptExchangeMetaData(info);

    assertThat(metadata.getInstruments()).hasSize(4);
    assertThat(metadata.getInstruments().get(CurrencyPair.BTC_PHP).getPriceScale()).isEqualTo(3);
    assertThat(metadata.getInstruments().get(new CurrencyPair("SHIB", "USDT")).getPriceScale())
        .isEqualTo(6);
    assertThat(metadata.getInstruments()).containsKey(new CurrencyPair("BTC", "USDC"));
    assertThat(metadata.getInstruments()).containsKey(new CurrencyPair("ETH", "USD"));
    assertThat(metadata.getCurrencies().get(Currency.getInstance("USDT")).getScale()).isEqualTo(6);
  }

  @Test
  void nullInputsAndUnknownOrderStatusHaveStableResults() {
    assertThat(CoinsphAdapters.adaptTicker(null)).isNull();
    assertThat(CoinsphAdapters.adaptTickers(null)).isEmpty();
    assertThat(CoinsphAdapters.adaptTickers(Collections.emptyList())).isEmpty();
    assertThat(CoinsphAdapters.adaptOrderBook(null, CurrencyPair.BTC_PHP)).isNull();
    assertThat(CoinsphAdapters.adaptOrder(null)).isNull();
    assertThat(CoinsphAdapters.adaptOrderStatus(null)).isEqualTo(OrderStatus.UNKNOWN);
    assertThat(CoinsphAdapters.adaptOrderStatus("unexpected")).isEqualTo(OrderStatus.UNKNOWN);
    assertThat(CoinsphAdapters.adaptOrderStatus("filled")).isEqualTo(OrderStatus.FILLED);
    assertThat(CoinsphAdapters.adaptUserTrade(null)).isNull();
    assertThat(CoinsphAdapters.adaptUserTrades(null).getUserTrades()).isEmpty();
    assertThat(CoinsphAdapters.adaptUserTrades(null).getTradeSortType())
        .isEqualTo(TradeSortType.SortByTimestamp);
    assertThat(CoinsphAdapters.adaptFundingRecords(null)).isEmpty();
    assertThat(CoinsphAdapters.adaptFundingRecords(Collections.emptyList())).isEmpty();
  }

  @Test
  void withdrawalDescriptionPreservesInfoOrFallsBackToNetwork() {
    assertThat(CoinsphAdapters.adaptWithdrawalRecord(withdrawal(null)).getDescription())
        .isEqualTo("Withdrawal via BTC");
    assertThat(CoinsphAdapters.adaptWithdrawalRecord(withdrawal("")).getDescription())
        .isEqualTo("Withdrawal via BTC");
    assertThat(CoinsphAdapters.adaptWithdrawalRecord(withdrawal("approved")).getDescription())
        .isEqualTo("approved");
  }

  private CoinsphWithdrawalRecord withdrawal(String info) {
    return new CoinsphWithdrawalRecord(
        "id", BigDecimal.ONE, BigDecimal.ZERO, "BTC", 1, "address", null, "tx", 1L,
        "BTC", null, info, 0);
  }

  @Test
  void receiveWindowAcceptsNumbersAndNumericStringsOrLeavesDefaultToServer() {
    TestExchange exchange = new TestExchange();
    ExchangeSpecification spec = new ExchangeSpecification(CoinsphExchange.class);
    exchange.setTestSpecification(spec);
    assertThat(exchange.getRecvWindow()).isNull();
    spec.setExchangeSpecificParametersItem(CoinsphExchange.PARAM_RECV_WINDOW, 1234);
    assertThat(exchange.getRecvWindow()).isEqualTo(1234L);
    spec.setExchangeSpecificParametersItem(CoinsphExchange.PARAM_RECV_WINDOW, "4567");
    assertThat(exchange.getRecvWindow()).isEqualTo(4567L);
    spec.setExchangeSpecificParametersItem(CoinsphExchange.PARAM_RECV_WINDOW, "invalid");
    assertThat(exchange.getRecvWindow()).isNull();
  }

  @Test
  void concurrentExchangeInitializationSharesOneResilienceRegistry() throws Exception {
    ResilienceRegistries original = TestExchange.replaceRegistry(null);
    ExecutorService executor = Executors.newFixedThreadPool(12);
    CountDownLatch ready = new CountDownLatch(12);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<ResilienceRegistries>> results = new ArrayList<>();
      for (int i = 0; i < 12; i++) {
        results.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  start.await();
                  return new TestExchange().getResilienceRegistries();
                }));
      }
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      ResilienceRegistries first = results.get(0).get(5, TimeUnit.SECONDS);
      assertThat(first).isNotNull();
      for (Future<ResilienceRegistries> result : results) {
        assertThat(result.get(5, TimeUnit.SECONDS)).isSameAs(first);
      }
    } finally {
      start.countDown();
      executor.shutdownNow();
      executor.awaitTermination(5, TimeUnit.SECONDS);
      TestExchange.replaceRegistry(original);
    }
  }

  private static class TestExchange extends CoinsphExchange {
    static ResilienceRegistries replaceRegistry(ResilienceRegistries replacement) {
      ResilienceRegistries previous = RESILIENCE_REGISTRIES;
      RESILIENCE_REGISTRIES = replacement;
      return previous;
    }

    void setTestSpecification(ExchangeSpecification spec) {
      this.exchangeSpecification = spec;
    }
  }
}
