package info.bitrich.xchangestream.cryptocom;

import static org.assertj.core.api.Assertions.assertThat;

import info.bitrich.xchangestream.cryptocom.dto.CryptoComOrderBookContinuityException;
import io.reactivex.rxjava3.observers.TestObserver;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.cryptocom.dto.marketdata.CryptoComOrderBookData;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;

/**
 * Behavioral tests for {@link CryptoComOrderBookAssembler} against the official snapshot/increment
 * contract: buffer increments until the snapshot arrives, apply increments only when {@code pu}
 * chains off the last applied {@code u}, reject stale/duplicate deliveries, emit a dedicated
 * continuity failure on a gap, and rebuild from a fresh snapshot after the connection was lost.
 * Deterministic fixtures only - no network or live calls.
 */
public class CryptoComOrderBookAssemblerTest {

  private static final CurrencyPair PAIR = CurrencyPair.BTC_USDT;
  private static final String CHANNEL = "book.BTC_USDT.10";

  private CryptoComOrderBookAssembler assembler;
  private TestObserver<CryptoComOrderBookContinuityException> failures;

  @BeforeEach
  public void setUp() {
    assembler = new CryptoComOrderBookAssembler(CHANNEL, PAIR, 10);
    failures = assembler.continuityFailures().test();
  }

  /** Official snapshot: carries {@code u} and no {@code pu}. */
  private static CryptoComOrderBookData snapshot() {
    CryptoComOrderBookData data = new CryptoComOrderBookData();
    data.setSequence(100L);
    data.setBids(rows("100.0,1.5", "99.0,2.0"));
    data.setAsks(rows("101.0,0.5", "102.0,0.25"));
    return data;
  }

  /** Official increment: carries both {@code u} and {@code pu}. */
  private static CryptoComOrderBookData increment(long u, long pu, String... rows) {
    CryptoComOrderBookData data = new CryptoComOrderBookData();
    data.setSequence(u);
    data.setPreviousSequence(pu);
    data.setBids(rows(rows.length > 0 ? rows[0] : "100.0,1.5"));
    data.setAsks(rows("101.0,0.5"));
    return data;
  }

  private static List<List<String>> rows(String... levels) {
    List<List<String>> result = new ArrayList<>();
    for (String level : levels) {
      result.add(Arrays.asList(level.split(",")));
    }
    return result;
  }

  private static BigDecimal amount(OrderBook book, boolean bid, String price) {
    for (LimitOrder order : bid ? book.getBids() : book.getAsks()) {
      if (order.getLimitPrice().compareTo(new BigDecimal(price)) == 0) {
        return order.getOriginalAmount();
      }
    }
    return null;
  }

  @Test
  public void testIncrementsAreBufferedUntilTheSnapshotArrives() {
    // when: increments arrive before the opening snapshot
    assertThat(assembler.apply(increment(101, 100, "100.0,2.0"))).isEmpty();
    assertThat(assembler.apply(increment(102, 101, "99.0,0.0"))).isEmpty();
    assertThat(assembler.awaitingSnapshot()).isTrue();
    assertThat(assembler.currentBook()).isNull();

    // and the snapshot then establishes the book and the buffered increments are flushed in order
    List<OrderBook> emitted = assembler.apply(snapshot());
    assertThat(emitted).hasSize(3);
    assertThat(amount(emitted.get(0), true, "100.0")).isEqualByComparingTo("1.5");
    // increment 101: bid 100.0 -> 2.0
    assertThat(amount(emitted.get(1), true, "100.0")).isEqualByComparingTo("2.0");
    // increment 102: bid 99.0 quantity 0 -> level removed
    assertThat(amount(emitted.get(2), true, "99.0")).isNull();
    assertThat(assembler.awaitingSnapshot()).isFalse();
    assertThat(assembler.lastAppliedSequence()).isEqualTo(102L);
  }

  @Test
  public void testStaleOrDuplicateIncrementsAreRejected() {
    assembler.apply(snapshot());
    assertThat(assembler.apply(increment(101, 100, "100.0,2.0"))).hasSize(1);
    assertThat(assembler.lastAppliedSequence()).isEqualTo(101L);

    // duplicate of an applied increment (u == last u)
    List<OrderBook> emitted = assembler.apply(increment(101, 100, "100.0,9.9"));
    assertThat(emitted).isEmpty();
    assertThat(amount(assembler.currentBook(), true, "100.0")).isEqualByComparingTo("2.0");

    // stale increment from earlier in the chain (u < last u)
    assertThat(assembler.apply(increment(100, 99, "100.0,9.9"))).isEmpty();
    assertThat(assembler.lastAppliedSequence()).isEqualTo(101L);
    assertThat(failures.values()).isEmpty();
  }

  @Test
  public void testGapInSequenceChainEmitsContinuityFailureAndStopsApplying() {
    assembler.apply(snapshot());
    assertThat(assembler.apply(increment(101, 100, "100.0,2.0"))).hasSize(1);

    // when: the chain jumps (pu != last applied u)
    List<OrderBook> emitted = assembler.apply(increment(200, 199, "100.0,3.0"));

    // then: nothing is applied and a dedicated continuity failure is emitted
    assertThat(emitted).isEmpty();
    assertThat(assembler.lastAppliedSequence()).isEqualTo(101L);
    assertThat(amount(assembler.currentBook(), true, "100.0")).isEqualByComparingTo("2.0");
    failures.assertValueCount(1);
    CryptoComOrderBookContinuityException failure = failures.values().get(0);
    assertThat(failure.getChannel()).isEqualTo(CHANNEL);
    assertThat(failure.getLastAppliedSequence()).isEqualTo(101L);
    assertThat(failure.getSequence()).isEqualTo(200L);
    assertThat(failure.getPreviousSequence()).isEqualTo(199L);
    assertThat(assembler.needsRebuild()).isTrue();

    // and further increments of the broken chain are dropped until a fresh snapshot
    assertThat(assembler.apply(increment(201, 200, "100.0,4.0"))).isEmpty();
    assertThat(amount(assembler.currentBook(), true, "100.0")).isEqualByComparingTo("2.0");
  }

  @Test
  public void testGapInBufferedIncrementsFailsAndDropsTheRestOfTheBuffer() {
    // increments before the snapshot chain onto ids that no snapshot will satisfy
    assertThat(assembler.apply(increment(1001, 1000, "100.0,9.0"))).isEmpty();
    assertThat(assembler.apply(increment(1002, 1001, "100.0,8.0"))).isEmpty();

    // when: the snapshot finally arrives with an incompatible u
    List<OrderBook> emitted = assembler.apply(snapshot());

    // then: the first flushed increment breaks the chain -> failure, rest of buffer dropped
    assertThat(emitted).hasSize(1);
    failures.assertValueCount(1);
    assertThat(failures.values().get(0).getLastAppliedSequence()).isEqualTo(100L);
    assertThat(assembler.needsRebuild()).isTrue();
    assertThat(amount(assembler.currentBook(), true, "100.0")).isEqualByComparingTo("1.5");
  }

  @Test
  public void testSnapshotBufferOverflowFailsClosed() {
    for (int i = 0; i < CryptoComOrderBookAssembler.MAX_SNAPSHOT_BUFFER; i++) {
      assertThat(assembler.apply(increment(1000 + i, 999 + i, "100.0,9.0"))).isEmpty();
    }
    // when: one more increment arrives while still waiting for the snapshot
    List<OrderBook> emitted = assembler.apply(increment(2000, 1999, "100.0,9.0"));

    // then: fail closed instead of unbounded buffering
    assertThat(emitted).isEmpty();
    failures.assertValueCount(1);
    assertThat(assembler.needsRebuild()).isTrue();
    assertThat(assembler.currentBook()).isNull();
  }

  @Test
  public void testConnectionLossRebuildsFromFreshSnapshotOnly() {
    assembler.apply(snapshot());
    assertThat(assembler.apply(increment(101, 100, "100.0,2.0"))).hasSize(1);

    // when: the socket drops - the old session's sequence chain is void
    assembler.markConnectionLost();
    assertThat(assembler.needsRebuild()).isTrue();

    // increments of the superseded session chain are not trusted
    assertThat(assembler.apply(increment(102, 101, "100.0,3.0"))).isEmpty();
    assertThat(failures.values()).isEmpty();

    // and the fresh snapshot rebuilds the book and resumes increment application
    CryptoComOrderBookData fresh = new CryptoComOrderBookData();
    fresh.setSequence(500L);
    fresh.setBids(rows("100.0,7.0"));
    fresh.setAsks(rows("101.0,0.5"));
    List<OrderBook> rebuilt = assembler.apply(fresh);
    assertThat(rebuilt).hasSize(1);
    assertThat(amount(rebuilt.get(0), true, "100.0")).isEqualByComparingTo("7.0");
    assertThat(assembler.needsRebuild()).isFalse();
    assertThat(assembler.apply(increment(501, 500, "100.0,8.0"))).hasSize(1);
    assertThat(amount(assembler.currentBook(), true, "100.0")).isEqualByComparingTo("8.0");
  }

  @Test
  public void testFullSnapshotWhileHealthyReplacesTheBook() {
    assembler.apply(snapshot());
    assertThat(assembler.apply(increment(101, 100, "100.0,2.0"))).hasSize(1);

    // the server substitutes a full snapshot when an update would be too large
    CryptoComOrderBookData replacement = new CryptoComOrderBookData();
    replacement.setSequence(300L);
    replacement.setBids(rows("100.0,0.75"));
    replacement.setAsks(rows("101.0,0.5"));
    List<OrderBook> emitted = assembler.apply(replacement);

    assertThat(emitted).hasSize(1);
    assertThat(amount(emitted.get(0), true, "100.0")).isEqualByComparingTo("0.75");
    assertThat(assembler.lastAppliedSequence()).isEqualTo(300L);
    assertThat(assembler.apply(increment(301, 300, "100.0,0.9"))).hasSize(1);
    assertThat(amount(assembler.currentBook(), true, "100.0")).isEqualByComparingTo("0.9");
  }

  @Test
  public void testZeroQuantityRemovesThePriceLevel() {
    CryptoComOrderBookData zeroUpdate = increment(101, 100, "100.0,0.0");
    assembler.apply(snapshot());
    List<OrderBook> emitted = assembler.apply(zeroUpdate);
    assertThat(emitted).hasSize(1);
    assertThat(amount(emitted.get(0), true, "100.0")).isNull();
    assertThat(emitted.get(0).getBids()).hasSize(1);
  }

  @Test
  public void testBookIsTrimmedToSubscribedDepth() {
    CryptoComOrderBookAssembler depthTwo = new CryptoComOrderBookAssembler(CHANNEL, PAIR, 2);
    CryptoComOrderBookData wideSnapshot = new CryptoComOrderBookData();
    wideSnapshot.setSequence(10L);
    wideSnapshot.setBids(rows("100.0,1", "99.0,2", "98.0,3", "97.0,4"));
    wideSnapshot.setAsks(rows("101.0,1", "102.0,2", "103.0,3"));

    List<OrderBook> emitted = depthTwo.apply(wideSnapshot);

    assertThat(emitted).hasSize(1);
    assertThat(emitted.get(0).getBids()).hasSize(2);
    assertThat(emitted.get(0).getBids().get(0).getLimitPrice()).isEqualByComparingTo("100.0");
    assertThat(emitted.get(0).getBids().get(1).getLimitPrice()).isEqualByComparingTo("99.0");
    assertThat(emitted.get(0).getAsks()).hasSize(2);
    assertThat(emitted.get(0).getAsks().get(0).getLimitPrice()).isEqualByComparingTo("101.0");
  }
}