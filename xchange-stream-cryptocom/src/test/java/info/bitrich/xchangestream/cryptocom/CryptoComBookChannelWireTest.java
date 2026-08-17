package info.bitrich.xchangestream.cryptocom;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.cryptocom.dto.CryptoComOrderBookContinuityException;
import io.reactivex.rxjava3.observers.TestObserver;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.cryptocom.dto.marketdata.CryptoComOrderBookData;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;

/**
 * End-to-end wire-shape test for the book channel: the official {@code u}/{@code pu} fields
 * unmarshal onto the DTO and the assembler drives the {@code SNAPSHOT_AND_UPDATE} flow from raw
 * server JSON (snapshot, then increments chained via {@code pu}) without any network.
 */
public class CryptoComBookChannelWireTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  public void testSnapshotAndChainedIncrementsFromRawWireJson() throws IOException {
    // given: the official book channel envelope carrying a full snapshot (u, no pu)
    JsonNode snapshot =
        objectMapper.readTree(
            "{\"id\":-1,\"method\":\"book.BTC_USDT.10\",\"code\":0,"
                + "\"result\":{\"channel\":\"book.BTC_USDT.10\",\"subscription\":\"book.BTC_USDT.10\","
                + "\"data\":[{\"t\":1785085000000,\"u\":100,\"bids\":[[\"100.0\",\"1.5\"]],"
                + "\"asks\":[[\"101.0\",\"0.5\"]]}]}}");

    List<CryptoComOrderBookData> snapshotData =
        new CryptoComStreamingService("wss://x").extractData(snapshot, CryptoComOrderBookData.class);
    assertThat(snapshotData).hasSize(1);
    assertThat(snapshotData.get(0).getSequence()).isEqualTo(100L);
    assertThat(snapshotData.get(0).getPreviousSequence()).isNull();

    // and a chained increment (u and pu)
    JsonNode update =
        objectMapper.readTree(
            "{\"id\":-1,\"method\":\"book.BTC_USDT.10\",\"code\":0,"
                + "\"result\":{\"channel\":\"book.BTC_USDT.10\",\"subscription\":\"book.BTC_USDT.10\","
                + "\"data\":[{\"u\":101,\"pu\":100,\"bids\":[[\"100.0\",\"2.0\"]],"
                + "\"asks\":[[\"101.0\",\"0.5\"]]}]}}");
    List<CryptoComOrderBookData> updateData =
        new CryptoComStreamingService("wss://x").extractData(update, CryptoComOrderBookData.class);
    assertThat(updateData.get(0).getSequence()).isEqualTo(101L);
    assertThat(updateData.get(0).getPreviousSequence()).isEqualTo(100L);

    // when: fed through the assembler exactly as the market data service does
    CryptoComOrderBookAssembler assembler =
        new CryptoComOrderBookAssembler("book.BTC_USDT.10", CurrencyPair.BTC_USDT, 10);
    TestObserver<CryptoComOrderBookContinuityException> failures =
        assembler.continuityFailures().test();

    // then: the snapshot builds the book and the chained increment applies cleanly
    List<OrderBook> emitted = assembler.apply(snapshotData.get(0));
    assertThat(emitted).hasSize(1);
    assertThat(emitted.get(0).getBids().get(0).getOriginalAmount()).isEqualByComparingTo("1.5");

    emitted = assembler.apply(updateData.get(0));
    assertThat(emitted).hasSize(1);
    assertThat(emitted.get(0).getBids().get(0).getOriginalAmount()).isEqualByComparingTo("2.0");
    assertThat(assembler.lastAppliedSequence()).isEqualTo(101L);
    failures.assertValueCount(0);
  }
}