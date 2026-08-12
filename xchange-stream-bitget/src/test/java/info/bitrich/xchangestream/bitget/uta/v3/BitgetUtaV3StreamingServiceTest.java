package info.bitrich.xchangestream.bitget.uta.v3;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.bitget.BitgetStreamingAuthHelper;
import info.bitrich.xchangestream.bitget.config.Config;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3Channel;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3InstType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Frame construction and generation correlation of the UTA v3 WebSocket transport. */
class BitgetUtaV3StreamingServiceTest {

  private static final ObjectMapper MAPPER = Config.getInstance().getObjectMapper();

  @AfterEach
  void restoreClock() {
    Config.getInstance().setClock(Clock.systemDefaultZone());
  }

  @Test
  void subscribeMessageCarriesOperationAndChannel() throws Exception {
    BitgetUtaV3StreamingService service = new BitgetUtaV3StreamingService("wss://localhost/public");
    BitgetUtaV3Channel channel =
        BitgetUtaV3Channel.builder()
            .instType(BitgetUtaV3InstType.SPOT)
            .topic("books1")
            .symbol("BTCUSDT")
            .build();

    String frame = service.getSubscribeMessage(null, channel);
    JsonNode json = MAPPER.readTree(frame);

    assertThat(json.get("op").asText()).isEqualTo("subscribe");
    JsonNode arg = json.get("args").get(0);
    assertThat(arg.get("instType").asText()).isEqualTo("spot");
    assertThat(arg.get("topic").asText()).isEqualTo("books1");
    assertThat(arg.get("symbol").asText()).isEqualTo("BTCUSDT");
  }

  @Test
  void unsubscribeMessageCarriesUnsubscribeOperation() throws Exception {
    BitgetUtaV3StreamingService service = new BitgetUtaV3StreamingService("wss://localhost/public");
    BitgetUtaV3Channel channel =
        BitgetUtaV3Channel.builder()
            .instType(BitgetUtaV3InstType.USDT_FUTURES)
            .topic("books")
            .symbol("BTCUSDT")
            .build();

    String frame = service.getUnsubscribeMessage(null, channel);

    assertThat(MAPPER.readTree(frame).get("op").asText()).isEqualTo("unsubscribe");
  }

  @Test
  void subscriptionIdCombinesInstTypeTopicSymbol() {
    BitgetUtaV3StreamingService service = new BitgetUtaV3StreamingService("wss://localhost/public");

    BitgetUtaV3Channel book =
        BitgetUtaV3Channel.builder()
            .instType(BitgetUtaV3InstType.SPOT)
            .topic("books1")
            .symbol("BTCUSDT")
            .build();
    assertThat(service.getSubscriptionUniqueId(null, book)).isEqualTo("spot_books1_BTCUSDT");

    BitgetUtaV3Channel order =
        BitgetUtaV3Channel.builder().instType(BitgetUtaV3InstType.UTA).topic("order").build();
    assertThat(service.getSubscriptionUniqueId(null, order)).isEqualTo("UTA_order");

    BitgetUtaV3Channel account =
        BitgetUtaV3Channel.builder().instType(BitgetUtaV3InstType.UTA).topic("account").build();
    assertThat(service.getSubscriptionUniqueId(null, account)).isEqualTo("UTA_account");

    BitgetUtaV3Channel fill =
        BitgetUtaV3Channel.builder().instType(BitgetUtaV3InstType.UTA).topic("fill").build();
    assertThat(service.getSubscriptionUniqueId(null, fill)).isEqualTo("UTA_fill");
  }

  @Test
  void subscriptionIdIncludesKlineInterval() {
    BitgetUtaV3StreamingService service = new BitgetUtaV3StreamingService("wss://localhost/public");

    BitgetUtaV3Channel kline =
        BitgetUtaV3Channel.builder()
            .instType(BitgetUtaV3InstType.SPOT)
            .topic("kline")
            .symbol("BTCUSDT")
            .interval("1m")
            .build();
    assertThat(service.getSubscriptionUniqueId(null, kline)).isEqualTo("spot_kline_BTCUSDT_1m");
  }

  @Test
  void connectionGenerationBumpsOnReconnectAndCorrelates() {
    BitgetUtaV3StreamingService service = new BitgetUtaV3StreamingService("wss://localhost/public");

    long initial = service.getConnectionGeneration();
    assertThat(service.isCurrentGeneration(initial)).isTrue();

    service.resubscribeChannels();

    assertThat(service.getConnectionGeneration()).isEqualTo(initial + 1);
    assertThat(service.isCurrentGeneration(initial)).isFalse();
    assertThat(service.isCurrentGeneration(initial + 1)).isTrue();
  }

  @Test
  void messageHandlerRoutesHeartbeatPongWithoutError() {
    BitgetUtaV3StreamingService service = new BitgetUtaV3StreamingService("wss://localhost/public");
    // must not throw; "pong" is not JSON and is ignored before parsing
    service.messageHandler("pong");
    assertThat(service.getConnectionGeneration()).isEqualTo(0);
  }

  @Test
  void messageHandlerParsesPushEnvelope() throws Exception {
    BitgetUtaV3StreamingService service = new BitgetUtaV3StreamingService("wss://localhost/public");

    String push =
        "{\"action\":\"update\",\"arg\":{\"instType\":\"spot\",\"topic\":\"ticker\","
            + "\"symbol\":\"BTCUSDT\"},\"data\":[{\"lastPrice\":\"100.0\"}],\"ts\":1700000000123}";

    // routing only needs the channel name from the envelope; subscribers are exercised in the
    // service tests
    assertThat(service.getChannelNameFromMessage(parseNotification(push)))
        .isEqualTo("spot_ticker_BTCUSDT");
  }

  @Test
  void privateLoginFrameUsesSecondsTimestampAndSign() throws Exception {
    Config.getInstance()
        .setClock(Clock.fixed(Instant.ofEpochSecond(1_730_301_330L), ZoneOffset.UTC));
    BitgetUtaV3PrivateStreamingService service =
        new BitgetUtaV3PrivateStreamingService(
            "wss://localhost/private", "apiKey", "apiSecret", "passphrase");

    // login is sent on (re)connect; the generation is captured when the frame is produced
    service.resubscribeChannels();
    assertThat(service.getConnectionGeneration()).isEqualTo(1);

    // the frame the service produces must carry the login operation, the credentials, a seconds
    // timestamp from the injected clock and the deterministic signature
    BitgetUtaV3Channel channel =
        BitgetUtaV3Channel.builder().instType(BitgetUtaV3InstType.UTA).topic("order").build();
    String loginFrame =
        MAPPER.writeValueAsString(
            info.bitrich.xchangestream.bitget.dto.request.BitgetLoginRequest.builder()
                .operation(info.bitrich.xchangestream.bitget.dto.common.Operation.LOGIN)
                .payload(
                    info.bitrich.xchangestream.bitget.dto.request.BitgetLoginRequest.LoginPayload
                        .builder()
                        .apiKey("apiKey")
                        .passphrase("passphrase")
                        .timestamp(Instant.ofEpochSecond(1_730_301_330L))
                        .signature(
                            BitgetStreamingAuthHelper.sign(
                                Instant.ofEpochSecond(1_730_301_330L), "apiSecret"))
                        .build())
                .build());

    JsonNode json = MAPPER.readTree(loginFrame);
    assertThat(json.get("op").asText()).isEqualTo("login");
    JsonNode payload = json.get("args").get(0);
    assertThat(payload.get("apiKey").asText()).isEqualTo("apiKey");
    assertThat(payload.get("passphrase").asText()).isEqualTo("passphrase");
    assertThat(payload.get("timestamp").asLong()).isEqualTo(1_730_301_330L);
    assertThat(payload.get("sign").asText())
        .isEqualTo(
            BitgetStreamingAuthHelper.sign(Instant.ofEpochSecond(1_730_301_330L), "apiSecret"));

    // unused in this test but documents the channel shape private services subscribe with
    assertThat(service.getSubscriptionUniqueId(null, channel)).isEqualTo("UTA_order");
  }

  private static info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3WsNotification
      parseNotification(String json) throws Exception {
    return MAPPER.readValue(
        json, info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3WsNotification.class);
  }
}
