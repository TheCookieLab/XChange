package info.bitrich.xchangestream.bitget.uta.v3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3Action;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3OrderBookData;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3OrderBookLevel;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Sequence-continuity behavior of the UTA v3 incremental order-book assembler.
 *
 * <p>The v3 protocol has no checksum; integrity rests on {@code pseq}/{@code seq} continuity:
 * snapshot first, first update P ≤ S ≤ U, subsequent updates pseq == last seq, {@code pseq=0} means
 * the provider restarted its sequence space.
 */
class BitgetUtaV3OrderBookAssemblerTest {

  private static final String SUBSCRIPTION_ID = "spot_books_BTCUSDT";

  private BitgetUtaV3OrderBookData snapshot(long seq, BigDecimal... bidSizes) {
    List<BitgetUtaV3OrderBookLevel> bids =
        List.of(
            BitgetUtaV3OrderBookLevel.fromArray(List.of("100.0", bidSizes[0].toPlainString())),
            BitgetUtaV3OrderBookLevel.fromArray(List.of("99.0", bidSizes[1].toPlainString())));
    List<BitgetUtaV3OrderBookLevel> asks =
        List.of(
            BitgetUtaV3OrderBookLevel.fromArray(List.of("101.0", bidSizes[2].toPlainString())),
            BitgetUtaV3OrderBookLevel.fromArray(List.of("102.0", bidSizes[3].toPlainString())));
    return BitgetUtaV3OrderBookData.builder()
        .bids(bids)
        .asks(asks)
        .seq(seq)
        .maxDepth(50)
        .ts(1_700_000_000_000L)
        .build();
  }

  private BitgetUtaV3OrderBookData update(
      long pseq, long seq, String side, BigDecimal price, BigDecimal size) {
    List<BitgetUtaV3OrderBookLevel> levels =
        List.of(
            BitgetUtaV3OrderBookLevel.fromArray(
                List.of(price.toPlainString(), size.toPlainString())));
    return BitgetUtaV3OrderBookData.builder()
        .bids("bid".equals(side) ? levels : null)
        .asks("ask".equals(side) ? levels : null)
        .pseq(pseq)
        .seq(seq)
        .ts(1_700_000_000_001L)
        .build();
  }

  @Test
  void snapshotThenFirstUpdateWithinWindow() {
    BitgetUtaV3OrderBookAssembler assembler = new BitgetUtaV3OrderBookAssembler();
    assembler.apply(
        snapshot(10, one(), one(), one(), one()), BitgetUtaV3Action.SNAPSHOT, SUBSCRIPTION_ID);

    assertThat(assembler.hasSnapshot()).isTrue();
    assertThat(assembler.getLastSeq()).isEqualTo(10L);
    assertThat(assembler.getBids()).hasSize(2);
    assertThat(assembler.getAsks()).hasSize(2);

    // first update: pseq P=5 <= snapshot seq S=10 <= update seq U=12
    assembler.apply(
        update(5, 12, "bid", new BigDecimal("99.5"), one()),
        BitgetUtaV3Action.UPDATE,
        SUBSCRIPTION_ID);

    assertThat(assembler.getBids()).hasSize(3);
    assertThat(assembler.getLastSeq()).isEqualTo(12L);
  }

  @Test
  void subsequentUpdateRequiresExactPseq() {
    BitgetUtaV3OrderBookAssembler assembler = new BitgetUtaV3OrderBookAssembler();
    assembler.apply(
        snapshot(10, one(), one(), one(), one()), BitgetUtaV3Action.SNAPSHOT, SUBSCRIPTION_ID);
    assembler.apply(
        update(10, 11, "bid", new BigDecimal("99.5"), one()),
        BitgetUtaV3Action.UPDATE,
        SUBSCRIPTION_ID);

    // gap: pseq 9 != last applied seq 11
    assertThatThrownBy(
            () ->
                assembler.apply(
                    update(9, 12, "ask", new BigDecimal("101.5"), one()),
                    BitgetUtaV3Action.UPDATE,
                    SUBSCRIPTION_ID))
        .isInstanceOf(BitgetUtaV3OrderBookContinuityException.class)
        .hasMessageContaining(SUBSCRIPTION_ID);

    // assembler reset; a fresh snapshot rebuilds the book
    assertThat(assembler.hasSnapshot()).isFalse();
    assertThat(assembler.getLastSeq()).isNull();
    assembler.apply(
        snapshot(20, one(), one(), one(), one()), BitgetUtaV3Action.SNAPSHOT, SUBSCRIPTION_ID);
    assertThat(assembler.getBids()).hasSize(2);
    assertThat(assembler.getLastSeq()).isEqualTo(20L);
  }

  @Test
  void firstUpdateWithPseqGreaterThanSnapshotSeqFails() {
    BitgetUtaV3OrderBookAssembler assembler = new BitgetUtaV3OrderBookAssembler();
    assembler.apply(
        snapshot(10, one(), one(), one(), one()), BitgetUtaV3Action.SNAPSHOT, SUBSCRIPTION_ID);

    // P=11 > S=10 violates P <= S <= U
    assertThatThrownBy(
            () ->
                assembler.apply(
                    update(11, 12, "bid", new BigDecimal("99.5"), one()),
                    BitgetUtaV3Action.UPDATE,
                    SUBSCRIPTION_ID))
        .isInstanceOf(BitgetUtaV3OrderBookContinuityException.class);
  }

  @Test
  void pseqZeroSignalsProviderSequenceReset() {
    BitgetUtaV3OrderBookAssembler assembler = new BitgetUtaV3OrderBookAssembler();
    assembler.apply(
        snapshot(10, one(), one(), one(), one()), BitgetUtaV3Action.SNAPSHOT, SUBSCRIPTION_ID);

    assertThatThrownBy(
            () ->
                assembler.apply(
                    update(0, 11, "bid", new BigDecimal("99.5"), one()),
                    BitgetUtaV3Action.UPDATE,
                    SUBSCRIPTION_ID))
        .isInstanceOf(BitgetUtaV3OrderBookContinuityException.class)
        .hasMessageContaining("sequence-space reset");
  }

  @Test
  void staleOrDuplicateUpdateDropped() {
    BitgetUtaV3OrderBookAssembler assembler = new BitgetUtaV3OrderBookAssembler();
    assembler.apply(
        snapshot(10, one(), one(), one(), one()), BitgetUtaV3Action.SNAPSHOT, SUBSCRIPTION_ID);
    assembler.apply(
        update(10, 11, "bid", new BigDecimal("99.5"), one()),
        BitgetUtaV3Action.UPDATE,
        SUBSCRIPTION_ID);

    // same seq as the applied update
    assembler.apply(
        update(11, 11, "bid", new BigDecimal("98.5"), one()),
        BitgetUtaV3Action.UPDATE,
        SUBSCRIPTION_ID);
    // older seq than the applied update
    assembler.apply(
        update(5, 9, "ask", new BigDecimal("101.5"), one()),
        BitgetUtaV3Action.UPDATE,
        SUBSCRIPTION_ID);

    assertThat(assembler.getBids()).hasSize(3);
    assertThat(assembler.getAsks()).hasSize(2);
    assertThat(assembler.getLastSeq()).isEqualTo(11L);
  }

  @Test
  void updateBeforeSnapshotDropped() {
    BitgetUtaV3OrderBookAssembler assembler = new BitgetUtaV3OrderBookAssembler();
    assembler.apply(
        update(1, 2, "bid", new BigDecimal("99.5"), one()),
        BitgetUtaV3Action.UPDATE,
        SUBSCRIPTION_ID);

    assertThat(assembler.hasSnapshot()).isFalse();
    assertThat(assembler.getBids()).isEmpty();
  }

  @Test
  void negativeSizeLevelIsNotInserted() {
    BitgetUtaV3OrderBookAssembler assembler = new BitgetUtaV3OrderBookAssembler();
    assembler.apply(
        snapshot(10, one(), one(), one(), one()), BitgetUtaV3Action.SNAPSHOT, SUBSCRIPTION_ID);

    // negative size is not a valid quantity: it must not be inserted as a level
    assembler.apply(
        update(10, 11, "bid", new BigDecimal("98.0"), new BigDecimal("-2")),
        BitgetUtaV3Action.UPDATE,
        SUBSCRIPTION_ID);

    assertThat(assembler.getBids()).hasSize(2);
    assertThat(assembler.getBids().get(0).getSize()).isPositive();
    assertThat(assembler.getBids().get(1).getSize()).isPositive();
  }

  @Test
  void zeroSizeDeletesLevel() {
    BitgetUtaV3OrderBookAssembler assembler = new BitgetUtaV3OrderBookAssembler();
    assembler.apply(
        snapshot(10, one(), one(), one(), one()), BitgetUtaV3Action.SNAPSHOT, SUBSCRIPTION_ID);

    assembler.apply(
        update(10, 11, "bid", new BigDecimal("100.0"), BigDecimal.ZERO),
        BitgetUtaV3Action.UPDATE,
        SUBSCRIPTION_ID);

    assertThat(assembler.getBids()).hasSize(1);
    assertThat(assembler.getBids().get(0).getPrice()).isEqualByComparingTo("99.0");
  }

  @Test
  void snapshotReplacesBook() {
    BitgetUtaV3OrderBookAssembler assembler = new BitgetUtaV3OrderBookAssembler();
    assembler.apply(
        snapshot(10, one(), one(), one(), one()), BitgetUtaV3Action.SNAPSHOT, SUBSCRIPTION_ID);
    assembler.apply(
        update(10, 11, "bid", new BigDecimal("99.5"), one()),
        BitgetUtaV3Action.UPDATE,
        SUBSCRIPTION_ID);

    assembler.apply(
        snapshot(20, one(), one(), one(), one()), BitgetUtaV3Action.SNAPSHOT, SUBSCRIPTION_ID);

    assertThat(assembler.getBids()).hasSize(2);
    assertThat(assembler.getAsks()).hasSize(2);
    assertThat(assembler.getLastSeq()).isEqualTo(20L);
  }

  @Test
  void emptyPayloadIsNoOp() {
    BitgetUtaV3OrderBookAssembler assembler = new BitgetUtaV3OrderBookAssembler();
    assembler.apply(
        BitgetUtaV3OrderBookData.builder().build(), BitgetUtaV3Action.UPDATE, SUBSCRIPTION_ID);
    assertThat(assembler.hasSnapshot()).isFalse();
  }

  private static BigDecimal one() {
    return BigDecimal.ONE;
  }
}
