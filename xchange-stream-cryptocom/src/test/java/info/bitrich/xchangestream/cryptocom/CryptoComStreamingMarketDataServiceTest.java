package info.bitrich.xchangestream.cryptocom;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.cryptocom.dto.CryptoComOrderBookContinuityException;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.observers.TestObserver;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;

/**
 * Behavioral tests for {@link CryptoComStreamingMarketDataService}: a broken order-book sequence
 * chain is surfaced on the continuity-failure observable and immediately triggers a resubscribe
 * of the exact channel through the transport, so the book rebuilds from a fresh provider snapshot
 * instead of waiting for an unrelated reconnect. Deterministic fixtures only - no network.
 */
public class CryptoComStreamingMarketDataServiceTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  /** Transport stub that records resubscribe calls and feeds raw wire envelopes on demand. */
  static final class RecordingService extends CryptoComStreamingService {

    final List<String> resubscribedChannels = new ArrayList<>();
    final io.reactivex.rxjava3.subjects.PublishSubject<JsonNode> bookEvents =
        io.reactivex.rxjava3.subjects.PublishSubject.create();

    RecordingService() {
      super("wss://stream.crypto.com/exchange/v1/market");
    }

    @Override
    public Observable<JsonNode> subscribeChannel(String channelName, Object... args) {
      return bookEvents;
    }

    @Override
    public void resubscribeChannel(String channelName, Object... args) {
      resubscribedChannels.add(channelName);
    }
  }

  @Test
  public void testBrokenSequenceChainResubscribesTheBookChannel() throws IOException {
    // given: a live order-book subscription
    RecordingService service = new RecordingService();
    CryptoComStreamingMarketDataService marketData = new CryptoComStreamingMarketDataService(service);
    TestObserver<CryptoComOrderBookContinuityException> failures =
        marketData.getOrderBookContinuityFailures().test();
    TestObserver<OrderBook> books = marketData.getOrderBook(CurrencyPair.BTC_USDT).test();

    // when: the official snapshot arrives and builds the book
    service.bookEvents.onNext(
        envelope(
            "{\"id\":-1,\"method\":\"book.BTC_USDT.10\",\"code\":0,"
                + "\"result\":{\"channel\":\"book.BTC_USDT.10\",\"subscription\":\"book.BTC_USDT.10\","
                + "\"data\":[{\"t\":1785085000000,\"u\":100,\"bids\":[[\"100.0\",\"1.5\"]],"
                + "\"asks\":[[\"101.0\",\"0.5\"]]}]}}"));
    assertThat(books.values()).hasSize(1);
    assertThat(failures.values()).isEmpty();

    // then: an increment whose pu no longer matches the applied chain breaks continuity
    service.bookEvents.onNext(
        envelope(
            "{\"id\":-1,\"method\":\"book.BTC_USDT.10\",\"code\":0,"
                + "\"result\":{\"channel\":\"book.BTC_USDT.10\",\"subscription\":\"book.BTC_USDT.10\","
                + "\"data\":[{\"u\":102,\"pu\":99,\"bids\":[[\"100.0\",\"2.0\"]],"
                + "\"asks\":[[\"101.0\",\"0.5\"]]}]}}"));

    // and the failure is surfaced AND the exact channel is resubscribed for a fresh snapshot
    assertThat(failures.values()).hasSize(1);
    assertThat(service.resubscribedChannels).containsExactly("book.BTC_USDT.10");
    books.assertNoErrors();
  }

  private JsonNode envelope(String json) throws IOException {
    return objectMapper.readTree(json);
  }
}