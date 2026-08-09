package info.bitrich.xchangestream.kraken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.kraken.config.Config;
import info.bitrich.xchangestream.kraken.dto.common.ChannelType;
import info.bitrich.xchangestream.kraken.dto.response.KrakenBookMessage;
import info.bitrich.xchangestream.kraken.dto.response.KrakenMessage;
import info.bitrich.xchangestream.kraken.dto.response.KrakenOhlcMessage;
import io.reactivex.rxjava3.core.Observable;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Book snapshot/update/checksum gap recovery plus OHLC and status channels. */
@ExtendWith(MockitoExtension.class)
class KrakenStreamingBookChannelTest {

  @Mock KrakenStreamingService krakenStreamingService;

  KrakenStreamingMarketDataService service;

  ObjectMapper objectMapper = Config.getInstance().getObjectMapper();

  @BeforeEach
  void init() {
    service = new KrakenStreamingMarketDataService(krakenStreamingService);
  }

  private <T extends KrakenMessage> T readMessage(String resource, Class<T> type)
      throws IOException {
    try (InputStream in = getClass().getResourceAsStream("/" + resource)) {
      return objectMapper.readValue(in, type);
    }
  }

  @Test
  void book_snapshot_emits_order_book() throws Exception {
    KrakenBookMessage snapshot = readMessage("sample-messages/book-snapshot.json", KrakenBookMessage.class);

    when(krakenStreamingService.subscribeChannel(eq("book"), eq(CurrencyPair.BTC_USD)))
        .thenReturn(Observable.just(snapshot));

    var observer = service.getOrderBook(CurrencyPair.BTC_USD).test();
    observer.awaitCount(1);
    observer.dispose();

    OrderBook book = observer.values().get(0);
    assertThat(book.getBids()).hasSize(3);
    assertThat(book.getAsks()).hasSize(3);
    assertThat(book.getBids().get(0).getLimitPrice()).isEqualByComparingTo("66500.1");
    assertThat(book.getBids().get(0).getOriginalAmount()).isEqualByComparingTo("1.5");
    assertThat(book.getAsks().get(0).getLimitPrice()).isEqualByComparingTo("66500.2");
    assertThat(book.getBids().get(0).getType()).isEqualTo(Order.OrderType.BID);
    verify(krakenStreamingService, never()).resubscribeChannel(eq("book"), eq(CurrencyPair.BTC_USD));
  }

  @Test
  void book_update_merges_levels_and_applies_removals() throws Exception {
    KrakenBookMessage snapshot = readMessage("sample-messages/book-snapshot.json", KrakenBookMessage.class);
    KrakenBookMessage update = readMessage("sample-messages/book-update.json", KrakenBookMessage.class);

    when(krakenStreamingService.subscribeChannel(eq("book"), eq(CurrencyPair.BTC_USD)))
        .thenReturn(Observable.just(snapshot, update));

    var observer = service.getOrderBook(CurrencyPair.BTC_USD).test();
    observer.awaitCount(2);
    observer.dispose();

    OrderBook merged = observer.values().get(1);
    assertThat(merged.getBids()).hasSize(3);
    assertThat(merged.getBids())
        .extracting(b -> b.getLimitPrice())
        .containsExactly(
            new BigDecimal("66501.0"), new BigDecimal("66500.1"), new BigDecimal("66490.0"));
    verify(krakenStreamingService, never()).resubscribeChannel(eq("book"), eq(CurrencyPair.BTC_USD));
  }

  @Test
  void checksum_mismatch_triggers_gap_recovery_and_rebuild() throws Exception {
    KrakenBookMessage snapshot = readMessage("sample-messages/book-snapshot.json", KrakenBookMessage.class);
    // corrupt the checksum: the computed value will not match
    KrakenBookMessage badSnapshot = snapshot.toBuilder().build();
    badSnapshot.getPayload().setChecksum(123456789L);
    KrakenBookMessage goodSnapshot = readMessage("sample-messages/book-snapshot.json", KrakenBookMessage.class);

    when(krakenStreamingService.subscribeChannel(eq("book"), eq(CurrencyPair.BTC_USD)))
        .thenReturn(Observable.just(badSnapshot, goodSnapshot));

    var observer = service.getOrderBook(CurrencyPair.BTC_USD).test();
    observer.awaitCount(1);
    observer.dispose();

    // gap recovery: resubscribed, and only the rebuilt (valid) snapshot was emitted
    verify(krakenStreamingService).resubscribeChannel(eq("book"), eq(CurrencyPair.BTC_USD));
    OrderBook rebuilt = observer.values().get(0);
    assertThat(rebuilt.getBids()).hasSize(3);
  }

  @Test
  void ohlc_channel_exposes_candles() throws Exception {
    KrakenOhlcMessage ohlc = readMessage("sample-messages/ohlc.json", KrakenOhlcMessage.class);
    assertThat(ohlc.getChannelId()).isEqualTo("ohlc_BTC/USD");

    when(krakenStreamingService.subscribeChannel(eq("ohlc"), eq(CurrencyPair.BTC_USD)))
        .thenReturn(Observable.just(ohlc));

    var observer = service.getOHLC(CurrencyPair.BTC_USD).test();
    observer.awaitCount(1);
    observer.dispose();

    var candle = observer.values().get(0);
    assertThat(candle.getSymbol()).isEqualTo("BTC/USD");
    assertThat(candle.getOpen()).isEqualByComparingTo("66500.1");
    assertThat(candle.getClose()).isEqualByComparingTo("66520.7");
    assertThat(candle.getVolume()).isEqualByComparingTo("12.345");
  }

  @Test
  void book_message_routes_by_symbol() throws Exception {
    KrakenBookMessage snapshot = readMessage("sample-messages/book-snapshot.json", KrakenBookMessage.class);

    assertThat(snapshot.getChannelId()).isEqualTo("book_BTC/USD");
  }

  @Test
  void status_channel_exposes_system_status() throws Exception {
    info.bitrich.xchangestream.kraken.dto.response.KrakenStatusMessage status =
        readMessage(
            "sample-messages/status.json",
            info.bitrich.xchangestream.kraken.dto.response.KrakenStatusMessage.class);
    assertThat(status.getChannelId()).isEqualTo("status");

    when(krakenStreamingService.subscribeChannel(eq("status")))
        .thenReturn(Observable.just(status));

    var observer = service.getSystemStatus().test();
    observer.awaitCount(1);
    observer.dispose();

    assertThat(observer.values().get(0).getStatus())
        .isEqualTo(info.bitrich.xchangestream.kraken.dto.response.KrakenStatusMessage.Status.ONLINE);
  }
}
