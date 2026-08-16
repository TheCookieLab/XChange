package info.bitrich.xchangestream.cryptocom;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Behavioral tests for {@link CryptoComStreamingService}: envelope handling (book subscription
 * parameters, heartbeat responses, subscribe/unsubscribe confirmations), and the per-connection
 * state reset performed by {@code resubscribeChannels()}. Deterministic fixtures only - no
 * network or live calls.
 */
public class CryptoComStreamingServiceTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  /** Records outgoing messages instead of touching the network. */
  static final class RecordingService extends CryptoComStreamingService {

    final List<ObjectNode> sent = new ArrayList<>();

    RecordingService() {
      super("wss://stream.crypto.com/exchange/v1/market");
    }

    @Override
    protected void sendObjectMessage(Object message) {
      sent.add((ObjectNode) message);
    }

    RecordingService deliver(JsonNode message) {
      handleMessage(message);
      return this;
    }
  }

  @Test
  public void testBookSubscriptionMessageCarriesOfficialSnapshotAndUpdateParams() throws IOException {
    // given
    RecordingService service = new RecordingService();

    // when
    String raw = service.getSubscribeMessage("book.BTC_USDT.10");

    // then
    JsonNode message = objectMapper.readTree(raw);
    assertThat(message.path("method").asText()).isEqualTo("subscribe");
    assertThat(message.path("id").asLong()).isPositive();
    assertThat(message.at("/params/channels/0").asText()).isEqualTo("book.BTC_USDT.10");
    assertThat(message.at("/params/book_subscription_type").asText())
        .isEqualTo("SNAPSHOT_AND_UPDATE");
    // The pending id must be correlated for the later confirmation.
    assertThat(service.isChannelActive("book.BTC_USDT.10")).isFalse();
  }

  @Test
  public void testNonBookSubscriptionCarriesNoBookParams() throws IOException {
    // given
    RecordingService service = new RecordingService();

    // when
    JsonNode tradeMessage = objectMapper.readTree(service.getSubscribeMessage("trade.BTC_USDT"));
    JsonNode tickerMessage = objectMapper.readTree(service.getSubscribeMessage("ticker.BTC_USDT"));
    JsonNode userMessage = objectMapper.readTree(service.getSubscribeMessage("user.balance"));

    // then
    for (JsonNode message : new JsonNode[] {tradeMessage, tickerMessage, userMessage}) {
      assertThat(message.at("/params/book_subscription_type").isMissingNode()).isTrue();
      assertThat(message.at("/params/channels/0").asText()).isNotBlank();
    }
  }

  @Test
  public void testSubscriptionConfirmationTracksActiveChannel() throws IOException {
    // given
    RecordingService service = new RecordingService();
    long id = objectMapper.readTree(service.getSubscribeMessage("book.BTC_USDT.10")).path("id").asLong();

    // when (server confirms the book subscription)
    service.deliver(message("{\"id\":" + id + ",\"method\":\"subscribe\",\"code\":0}"));

    // then
    assertThat(service.isChannelActive("book.BTC_USDT.10")).isTrue();
    assertThat(service.getActiveChannels()).containsExactly("book.BTC_USDT.10");

    // and a rejected subscription never becomes active
    RecordingService rejected = new RecordingService();
    long rejectedId =
        objectMapper.readTree(rejected.getSubscribeMessage("trade.ETH_USDT")).path("id").asLong();
    rejected.deliver(
        message("{\"id\":" + rejectedId + ",\"method\":\"subscribe\",\"code\":10001,\"message\":\"nope\"}"));
    assertThat(rejected.isChannelActive("trade.ETH_USDT")).isFalse();
    assertThat(rejected.getActiveChannels()).isEmpty();
  }

  @Test
  public void testUnsubscribeConfirmationRemovesActiveChannel() throws IOException {
    // given
    RecordingService service = new RecordingService();
    long subscribeId =
        objectMapper.readTree(service.getSubscribeMessage("book.BTC_USDT.10")).path("id").asLong();
    service.deliver(message("{\"id\":" + subscribeId + ",\"method\":\"subscribe\",\"code\":0}"));
    assertThat(service.isChannelActive("book.BTC_USDT.10")).isTrue();

    // when
    JsonNode unsub = objectMapper.readTree(service.getUnsubscribeMessage("book.BTC_USDT.10"));
    service.deliver(
        message("{\"id\":" + unsub.path("id").asLong() + ",\"method\":\"unsubscribe\",\"code\":0}"));

    // then
    assertThat(service.isChannelActive("book.BTC_USDT.10")).isFalse();
  }

  @Test
  public void testHeartbeatIsAnsweredWithMatchingResponseId() {
    // given
    RecordingService service = new RecordingService();

    // when
    service.deliver(message("{\"method\":\"public/heartbeat\",\"id\":1785800000042}"));

    // then
    assertThat(service.sent).hasSize(1);
    ObjectNode response = service.sent.get(0);
    assertThat(response.path("method").asText()).isEqualTo("public/respond-heartbeat");
    assertThat(response.path("id").asLong()).isEqualTo(1785800000042L);
  }

  @Test
  public void testResubscribeChannelsResetsPerConnectionState() throws IOException {
    // given: a connected-ish session with one confirmed channel
    RecordingService service = new RecordingService();
    long id = objectMapper.readTree(service.getSubscribeMessage("book.BTC_USDT.10")).path("id").asLong();
    service.deliver(message("{\"id\":" + id + ",\"method\":\"subscribe\",\"code\":0}"));
    assertThat(service.isChannelActive("book.BTC_USDT.10")).isTrue();

    // when: a reconnect re-subscribes every channel from scratch
    service.resubscribeChannels();

    // then: confirmation state from the superseded connection is gone and the current
    // connection is unconfirmed until the server confirms anew
    assertThat(service.getActiveChannels()).isEmpty();
    assertThat(service.isChannelActive("book.BTC_USDT.10")).isFalse();
    assertThat(service.getConnectionGeneration())
        .isEqualTo(service.getGeneration());
  }

  @Test
  public void testNeverConnectedServiceIsNotCurrentConnection() {
    // given
    RecordingService service = new RecordingService();

    // then
    assertThat(service.getConnectionGeneration()).isZero();
    assertThat(service.isCurrentConnection()).isFalse();
  }

  private JsonNode message(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }
}