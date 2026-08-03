package info.bitrich.xchangestream.kalshi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.service.netty.StreamingObjectMapperHelper;
import io.reactivex.rxjava3.core.Observable;
import java.math.BigDecimal;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.Order.OrderStatus;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.exceptions.ExchangeSecurityException;
import org.knowm.xchange.kalshi.client.KalshiDigest;
import org.knowm.xchange.prediction.PredictionMarketContract;

/**
 * Authenticated user-stream tests for {@link KalshiStreamingTradeService}: fill and order-update
 * mapping plus the credentials guard, without a live WebSocket.
 */
class KalshiStreamingTradeServiceTest {

  private static final String TICKER = "KXSB-26";
  private static final PredictionMarketContract CONTRACT =
      new PredictionMarketContract("kalshi", null, TICKER, "YES", Currency.USD);
  private static final ObjectMapper MAPPER = StreamingObjectMapperHelper.getObjectMapper();

  private static KalshiDigest digest;

  /** Feeds scripted messages; optionally carries credentials. */
  private static final class FakeService extends KalshiStreamingService {
    private final List<JsonNode> scripted;
    private final List<String> subscriptions = new ArrayList<>();

    FakeService(List<JsonNode> scripted, boolean withCredentials) {
      super(
          "wss://stream.test/ws",
          withCredentials ? "key-id" : null,
          withCredentials ? digest : null);
      this.scripted = scripted;
    }

    @Override
    public Observable<JsonNode> subscribeChannel(String channelName, Object... args) {
      subscriptions.add(channelName);
      return Observable.fromIterable(scripted);
    }
  }

  @BeforeAll
  static void generateKeyPair() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    String pem =
        "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[] {'\n'})
                .encodeToString(generator.generateKeyPair().getPrivate().getEncoded())
            + "\n-----END PRIVATE KEY-----";
    digest = KalshiDigest.createInstance(pem);
  }

  @Test
  void userFillsMapThroughTheLegacyNoComplementRule() throws Exception {
    FakeService fake =
        new FakeService(
            List.of(
                MAPPER.readTree(
                    fillJson("yes", "buy", "0.750", "278.00")),
                MAPPER.readTree(
                    fillJson("no", "buy", "0.250", "10.00"))),
            true);
    KalshiStreamingTradeService service = new KalshiStreamingTradeService(fake);

    List<UserTrade> fills = service.getUserTrades(CONTRACT).toList().blockingGet();

    assertEquals(List.of("fill"), fake.subscriptions);
    assertEquals(2, fills.size());
    UserTrade buyYes = fills.get(0);
    assertEquals(OrderType.BID, buyYes.getType());
    assertEquals(new BigDecimal("0.750"), buyYes.getPrice());
    assertEquals(new BigDecimal("278.00"), buyYes.getOriginalAmount());
    assertEquals(CONTRACT, buyYes.getInstrument());
    assertEquals("d91bc706", buyYes.getId());
    assertEquals("ee587a1c", buyYes.getOrderId());
    assertEquals(new BigDecimal("1.50"), buyYes.getFeeAmount());
    assertEquals(Currency.USD, buyYes.getFeeCurrency());
    assertEquals(new Date(1671899397000L), buyYes.getTimestamp());
    // RULE_LEGACY_NO_COMPLEMENT: buying NO reads as selling YES at the YES price.
    UserTrade buyNo = fills.get(1);
    assertEquals(OrderType.ASK, buyNo.getType());
    assertEquals(new BigDecimal("0.250"), buyNo.getPrice());
  }

  @Test
  void userOrderUpdatesMapStatusAndBookSide() throws Exception {
    FakeService fake =
        new FakeService(
            List.of(
                MAPPER.readTree(
                    userOrderJson("resting", "ask", "0.3500", "4.00", "10.00")),
                MAPPER.readTree(
                    userOrderJson("executed", "bid", "0.6100", "10.00", "10.00"))),
            true);
    KalshiStreamingTradeService service = new KalshiStreamingTradeService(fake);

    List<LimitOrder> orders =
        service
            .getOrderChanges(CONTRACT)
            .map(order -> (LimitOrder) order)
            .toList()
            .blockingGet();

    assertEquals(List.of("user_orders"), fake.subscriptions);
    assertEquals(2, orders.size());
    LimitOrder resting = orders.get(0);
    assertEquals(OrderType.ASK, resting.getType(), "YES-book ask side sells the YES outcome");
    assertEquals(OrderStatus.PARTIALLY_FILLED, resting.getStatus());
    assertEquals(new BigDecimal("0.3500"), resting.getLimitPrice());
    assertEquals(new BigDecimal("4.00"), resting.getCumulativeAmount());
    assertEquals(new BigDecimal("10.00"), resting.getOriginalAmount());
    assertEquals("ee587a1c", resting.getId());
    assertEquals("my-order-1", resting.getUserReference());
    assertEquals(new Date(1733047200000L), resting.getTimestamp());
    assertEquals(OrderStatus.FILLED, orders.get(1).getStatus());
    assertEquals(OrderType.BID, orders.get(1).getType());
  }

  @Test
  void userStreamsRequireCredentialsBeforeSubscribing() {
    FakeService fake = new FakeService(List.of(), false);
    KalshiStreamingTradeService service = new KalshiStreamingTradeService(fake);

    ExchangeSecurityException fillsError =
        assertThrows(ExchangeSecurityException.class, () -> service.getUserTrades(CONTRACT));
    assertThrows(ExchangeSecurityException.class, () -> service.getOrderChanges(CONTRACT));
    assertTrue(fillsError.getMessage().contains("apiKey"));
    assertTrue(fake.subscriptions.isEmpty(), "no subscription may be attempted anonymously");
  }

  private static String fillJson(String side, String action, String yesPrice, String count) {
    return "{\"type\":\"fill\",\"sid\":13,\"msg\":{"
        + "\"trade_id\":\"d91bc706\",\"order_id\":\"ee587a1c\",\"market_ticker\":\""
        + TICKER
        + "\",\"is_taker\":true,\"side\":\""
        + side
        + "\",\"yes_price_dollars\":\""
        + yesPrice
        + "\",\"count_fp\":\""
        + count
        + "\",\"fee_cost\":\"1.50\",\"action\":\""
        + action
        + "\",\"ts\":1671899397,\"ts_ms\":1671899397000,\"post_position_fp\":\"500.00\","
        + "\"purchased_side\":\"yes\"}}";
  }

  private static String userOrderJson(
      String status, String bookSide, String yesPrice, String filled, String initial) {
    return "{\"type\":\"user_order\",\"sid\":22,\"msg\":{"
        + "\"order_id\":\"ee587a1c\",\"user_id\":\"a1b2c3d4\",\"ticker\":\""
        + TICKER
        + "\",\"status\":\""
        + status
        + "\",\"side\":\"yes\",\"is_yes\":true,\"outcome_side\":\"yes\",\"book_side\":\""
        + bookSide
        + "\",\"yes_price_dollars\":\""
        + yesPrice
        + "\",\"fill_count_fp\":\""
        + filled
        + "\",\"remaining_count_fp\":\"6.00\",\"initial_count_fp\":\""
        + initial
        + "\",\"taker_fill_cost_dollars\":\"0.0000\",\"maker_fill_cost_dollars\":\"0.0000\","
        + "\"taker_fees_dollars\":\"0.0000\",\"maker_fees_dollars\":\"0.0000\","
        + "\"client_order_id\":\"my-order-1\",\"created_time\":\"2024-12-01T10:00:00Z\","
        + "\"created_ts_ms\":1733047200000}}";
  }
}
