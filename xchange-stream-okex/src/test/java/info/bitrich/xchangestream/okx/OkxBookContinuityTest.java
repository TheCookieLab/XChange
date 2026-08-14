package info.bitrich.xchangestream.okx;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import info.bitrich.xchangestream.service.netty.StreamingObjectMapperHelper;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import org.junit.Test;

/** Offline unit tests for the OKX "books" channel continuity guard. */
public class OkxBookContinuityTest {

  private static final String INST_ID = "BTC-USDT";
  private static final String[][] BID_100 = {{"100.0", "10"}};
  private static final String[][] ASK_101 = {{"101.0", "5"}};
  private static final String[][] BID_99 = {{"99.0", "1"}};
  private static final String[][] EMPTY = new String[0][0];

  private final ObjectMapper mapper = StreamingObjectMapperHelper.getObjectMapper();
  private final OkxBookContinuity continuity = new OkxBookContinuity();

  /**
   * Independent re-implementation of the OKX checksum spec used to build expected fixtures: CRC32
   * of {@code price:size} for every bid (highest first) then every ask (lowest first), no separator
   * between levels.
   */
  private static long expectedChecksum(String[][] bids, String[][] asks) {
    CRC32 crc = new CRC32();
    for (String[] level : bids) {
      crc.update((level[0] + ":" + level[1]).getBytes(StandardCharsets.UTF_8));
    }
    for (String[] level : asks) {
      crc.update((level[0] + ":" + level[1]).getBytes(StandardCharsets.UTF_8));
    }
    return crc.getValue();
  }

  private JsonNode data(long seqId, long checksum, String[][] bids, String[][] asks) {
    ObjectNode data = mapper.createObjectNode();
    ArrayNode bidsArray = data.putArray("bids");
    for (String[] level : bids) {
      bidsArray.addArray().add(level[0]).add(level[1]);
    }
    ArrayNode asksArray = data.putArray("asks");
    for (String[] level : asks) {
      asksArray.addArray().add(level[0]).add(level[1]);
    }
    data.put("ts", "1699999999999");
    data.put("seqId", seqId);
    data.put("checksum", checksum);
    return data;
  }

  @Test
  public void testChecksumAlgorithmMatchesDocumentedVector() {
    // Bids "100.0:10" + asks "101.0:5" concatenate to "100.0:10101.0:5" (no separator).
    ArrayNode bids = mapper.createArrayNode().add(mapper.createArrayNode().add("100.0").add("10"));
    ArrayNode asks = mapper.createArrayNode().add(mapper.createArrayNode().add("101.0").add("5"));
    assertThat(OkxBookContinuity.checksum(bids, asks)).isEqualTo(2511280408L);
  }

  @Test
  public void testSnapshotThenConsecutiveUpdateAccepted() {
    continuity.snapshot(INST_ID, data(1, expectedChecksum(BID_100, ASK_101), BID_100, ASK_101));

    // Checksum covers the full book after applying the update: bids 100.0 and 99.0, ask 101.0.
    String[][] bidsAfter = {{"100.0", "10"}, {"99.0", "1"}};
    JsonNode update = data(2, expectedChecksum(bidsAfter, ASK_101), BID_99, EMPTY);

    assertThat(continuity.gateUpdate(INST_ID, update)).isEqualTo(OkxBookContinuity.Gate.ACCEPT);
    assertThat(continuity.isRebuilding(INST_ID)).isFalse();
    assertThat(continuity.levelCount(INST_ID)).isEqualTo(3);
  }

  @Test
  public void testDuplicateUpdateDropped() {
    continuity.snapshot(INST_ID, data(1, expectedChecksum(BID_100, ASK_101), BID_100, ASK_101));
    String[][] bidsAfter = {{"100.0", "10"}, {"99.0", "1"}};
    JsonNode update = data(2, expectedChecksum(bidsAfter, ASK_101), BID_99, EMPTY);
    assertThat(continuity.gateUpdate(INST_ID, update)).isEqualTo(OkxBookContinuity.Gate.ACCEPT);

    // Same seqId re-delivered: dropped, state untouched.
    assertThat(continuity.gateUpdate(INST_ID, update)).isEqualTo(OkxBookContinuity.Gate.DROP_STALE);
    assertThat(continuity.levelCount(INST_ID)).isEqualTo(3);
  }

  @Test
  public void testOutOfOrderUpdateDropped() {
    continuity.snapshot(INST_ID, data(1, expectedChecksum(BID_100, ASK_101), BID_100, ASK_101));

    // Update with seqId <= snapshot seqId.
    assertThat(continuity.gateUpdate(INST_ID, data(1, 0, BID_99, EMPTY)))
        .isEqualTo(OkxBookContinuity.Gate.DROP_STALE);
    assertThat(continuity.levelCount(INST_ID)).isEqualTo(2);
  }

  @Test
  public void testSequenceGapTriggersRebuildAndRecoversOnSnapshot() {
    continuity.snapshot(INST_ID, data(1, expectedChecksum(BID_100, ASK_101), BID_100, ASK_101));

    assertThat(continuity.gateUpdate(INST_ID, data(5, 0, BID_99, EMPTY)))
        .isEqualTo(OkxBookContinuity.Gate.REBUILD);
    assertThat(continuity.isRebuilding(INST_ID)).isTrue();

    // Everything is dropped until a fresh snapshot resets the state.
    assertThat(continuity.gateUpdate(INST_ID, data(6, 0, BID_99, EMPTY)))
        .isEqualTo(OkxBookContinuity.Gate.DROP_STALE);

    continuity.snapshot(INST_ID, data(100, expectedChecksum(BID_99, ASK_101), BID_99, ASK_101));
    assertThat(continuity.isRebuilding(INST_ID)).isFalse();
    assertThat(continuity.levelCount(INST_ID)).isEqualTo(2);

    assertThat(continuity.gateUpdate(INST_ID, data(101, 0, EMPTY, ASK_101)))
        .isEqualTo(OkxBookContinuity.Gate.ACCEPT);
  }

  @Test
  public void testChecksumMismatchTriggersRebuild() {
    continuity.snapshot(INST_ID, data(1, expectedChecksum(BID_100, ASK_101), BID_100, ASK_101));

    // Correct sequence but wrong checksum.
    assertThat(continuity.gateUpdate(INST_ID, data(2, 12345L, BID_99, EMPTY)))
        .isEqualTo(OkxBookContinuity.Gate.REBUILD);
    assertThat(continuity.isRebuilding(INST_ID)).isTrue();
    assertThat(continuity.gateUpdate(INST_ID, data(3, 0, BID_99, EMPTY)))
        .isEqualTo(OkxBookContinuity.Gate.DROP_STALE);
  }

  @Test
  public void testZeroChecksumSkipsVerification() {
    continuity.snapshot(INST_ID, data(1, 0, BID_100, ASK_101));

    // Modern OKX sends checksum 0: only sequence continuity is enforced.
    assertThat(continuity.gateUpdate(INST_ID, data(2, 0, BID_99, EMPTY)))
        .isEqualTo(OkxBookContinuity.Gate.ACCEPT);
    assertThat(continuity.gateUpdate(INST_ID, data(2, 0, BID_99, EMPTY)))
        .isEqualTo(OkxBookContinuity.Gate.DROP_STALE);
  }

  @Test
  public void testZeroSizeRemovesLevelAndEmptySizeLeavesItUntouched() {
    continuity.snapshot(INST_ID, data(1, 0, BID_100, ASK_101));

    // size "0" removes the level.
    assertThat(continuity.gateUpdate(INST_ID, data(2, 0, new String[][] {{"100.0", "0"}}, EMPTY)))
        .isEqualTo(OkxBookContinuity.Gate.ACCEPT);
    assertThat(continuity.levelCount(INST_ID)).isEqualTo(1);

    // empty size is a no-change marker.
    assertThat(continuity.gateUpdate(INST_ID, data(3, 0, new String[][] {{"100.0", ""}}, EMPTY)))
        .isEqualTo(OkxBookContinuity.Gate.ACCEPT);
    assertThat(continuity.levelCount(INST_ID)).isEqualTo(1);
  }

  @Test
  public void testUpdateBeforeSnapshotRequestsRebuild() {
    assertThat(continuity.gateUpdate(INST_ID, data(2, 0, BID_99, EMPTY)))
        .isEqualTo(OkxBookContinuity.Gate.REBUILD);
  }
  
  @Test
  public void testChecksumInterleavesTopBidsAndAsks() {
    String[][] bids = {{"102", "2"}, {"100", "1"}};
    String[][] asks = {{"103", "3"}, {"105", "4"}};
    JsonNode snapshot = data(1, 0, bids, asks);

    // OKX orders the checksum input bid1, ask1, bid2, ask2 (top 25 on each side).
    assertThat(OkxBookContinuity.checksum(snapshot.get("bids"), snapshot.get("asks")))
        .isEqualTo(2334835581L);
    continuity.snapshot(INST_ID, snapshot);

    // A no-op update still carries the checksum for the complete reconstructed book.
    assertThat(continuity.gateUpdate(INST_ID, data(2, 2334835581L, EMPTY, EMPTY)))
        .isEqualTo(OkxBookContinuity.Gate.ACCEPT);
  }
}
