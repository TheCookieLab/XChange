package info.bitrich.xchangestream.mexc;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.mxc.push.common.protobuf.PushDataV3ApiWrapper;

/**
 * Binary codec for MEXC Spot v3 WebSocket pushes.
 *
 * <p>Every server push is a WebSocket <em>binary</em> frame whose entire payload is a serialized
 * {@link PushDataV3ApiWrapper} (channel in field 1, oneof body 301-315, symbol fields 3-6). This
 * codec parses the protobuf payload and converts it to and from the canonical JSON representation
 * used as the message type ({@code String}) of {@link MexcV3StreamingService}. The canonical JSON
 * preserves every wrapper field so subscribers can re-parse the typed body.
 */
public final class MexcV3ProtoCodec {

  private static final JsonFormat.Printer PRINTER =
      JsonFormat.printer().omittingInsignificantWhitespace();
  private static final JsonFormat.Parser PARSER = JsonFormat.parser().ignoringUnknownFields();

  private MexcV3ProtoCodec() {}

  /** Parses a binary push payload into the typed wrapper. */
  public static PushDataV3ApiWrapper decode(byte[] payload) throws InvalidProtocolBufferException {
    return PushDataV3ApiWrapper.parseFrom(payload);
  }

  /** Serializes the wrapper to canonical JSON (channel + oneof body + symbol fields). */
  public static String toJson(PushDataV3ApiWrapper wrapper) throws InvalidProtocolBufferException {
    return PRINTER.print(wrapper);
  }

  /** Parses canonical JSON back into the typed wrapper. Unknown fields are ignored. */
  public static PushDataV3ApiWrapper fromJson(String json) throws InvalidProtocolBufferException {
    PushDataV3ApiWrapper.Builder builder = PushDataV3ApiWrapper.newBuilder();
    PARSER.merge(json, builder);
    return builder.build();
  }
}
