package info.bitrich.xchangestream.mexc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.InvalidProtocolBufferException;
import com.mxc.push.common.protobuf.PublicAggreDealsV3Api;
import com.mxc.push.common.protobuf.PublicAggreDealsV3ApiItem;
import com.mxc.push.common.protobuf.PushDataV3ApiWrapper;
import org.junit.jupiter.api.Test;

/**
 * Round-trip tests for {@link MexcV3ProtoCodec}: binary {@link PushDataV3ApiWrapper} decoding,
 * canonical-JSON printing, and JSON re-parsing.
 */
class MexcV3ProtoCodecTest {

  private static final String CHANNEL = "spot@public.aggre.deals.v3.api.pb@100ms@BTCUSDT";

  private PushDataV3ApiWrapper sampleWrapper() {
    PublicAggreDealsV3ApiItem item =
        PublicAggreDealsV3ApiItem.newBuilder()
            .setTradeId("123456789")
            .setPrice("65432.12")
            .setQuantity("0.123")
            .setTradeType(1)
            .setTime(1_712_345_678_901L)
            .build();
    PublicAggreDealsV3Api deals =
        PublicAggreDealsV3Api.newBuilder().addDeals(item).build();
    return PushDataV3ApiWrapper.newBuilder()
        .setChannel(CHANNEL)
        .setSymbol("BTCUSDT")
        .setCreateTime(1_712_345_678_902L)
        .setPublicAggreDeals(deals)
        .build();
  }

  @Test
  void decodeRoundTripsToEqualWrapper() throws InvalidProtocolBufferException {
    PushDataV3ApiWrapper original = sampleWrapper();
    PushDataV3ApiWrapper decoded = MexcV3ProtoCodec.decode(original.toByteArray());
    assertEquals(original, decoded);
    assertEquals(PushDataV3ApiWrapper.BodyCase.PUBLICAGGREDEALS, decoded.getBodyCase());
    assertEquals(original.getChannel(), decoded.getChannel());
    assertEquals(original.getSymbol(), decoded.getSymbol());
    assertEquals(original.getCreateTime(), decoded.getCreateTime());
  }

  @Test
  void toJsonPrintsCanonicalEnvelopeAndBody() throws InvalidProtocolBufferException {
    String json = MexcV3ProtoCodec.toJson(sampleWrapper());
    assertTrue(json.contains("\"channel\":\"" + CHANNEL + "\""), json);
    assertTrue(json.contains("\"createTime\""), json);
    assertTrue(json.contains("\"publicAggreDeals\""), json);
    assertTrue(json.contains("\"tradeId\":\"123456789\""), json);
  }

  @Test
  void fromJsonRoundTripsWrapper() throws InvalidProtocolBufferException {
    PushDataV3ApiWrapper original = sampleWrapper();
    String json = MexcV3ProtoCodec.toJson(original);
    PushDataV3ApiWrapper reparsed = MexcV3ProtoCodec.fromJson(json);
    assertEquals(original, reparsed);
  }

  @Test
  void decodeRejectsMalformedBytes() {
    assertThrows(
        InvalidProtocolBufferException.class, () -> MexcV3ProtoCodec.decode(new byte[] {1, 2, 3}));
  }
}
