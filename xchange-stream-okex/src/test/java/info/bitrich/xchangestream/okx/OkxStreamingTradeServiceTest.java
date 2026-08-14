package info.bitrich.xchangestream.okx;

import static info.bitrich.xchangestream.okx.OkxPrivateStreamingService.PLACE_ORDER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import info.bitrich.xchangestream.service.netty.StreamingObjectMapperHelper;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.account.OpenPosition;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.dto.meta.InstrumentMetaData;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.OrderNotValidException;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.okx.OkxResilience;
import org.knowm.xchange.okx.dto.trade.OkxTradeParams.OkxCancelOrderParams;

/** Offline tests for private-event dedupe, fill correlation and typed placement errors. */
public class OkxStreamingTradeServiceTest {

  private static final Instrument SPOT = CurrencyPair.BTC_USDT;
  private static final Instrument SWAP = new FuturesContract("BTC/USDT/SWAP");
  private static final String ORDER_CHANNEL = "ordersBTC-USDT";
  private static final String POSITION_CHANNEL = "positionsBTC-USDT-SWAP";

  private final ObjectMapper mapper = StreamingObjectMapperHelper.getObjectMapper();
  private OkxStreamingTradeService tradeService;
  private OkxPrivateStreamingService privateStreamingService;

  @Before
  public void setUp() {
    privateStreamingService = mock(OkxPrivateStreamingService.class);
    Map<Instrument, InstrumentMetaData> instruments = new HashMap<>();
    instruments.put(SPOT, InstrumentMetaData.builder().build());
    instruments.put(
        SWAP, InstrumentMetaData.builder().contractValue(BigDecimal.ONE).build());
    ExchangeMetaData exchangeMetaData =
        new ExchangeMetaData(instruments, Collections.emptyMap(), null, null, null);
    tradeService =
        new OkxStreamingTradeService(
            privateStreamingService,
            exchangeMetaData,
            OkxResilience.createRegistries());
  }

  private ObjectNode orderEvent(
      String ordId,
      String clOrdId,
      String uTime,
      String tradeId,
      String fillPx,
      String fillSz,
      String fillTime,
      String fee) {
    ObjectNode root = mapper.createObjectNode();
    ObjectNode arg = root.putObject("arg");
    arg.put("channel", "orders");
    arg.put("instId", "BTC-USDT");
    ObjectNode data = root.putArray("data").addObject();
    data.put("instType", "SPOT");
    data.put("instId", "BTC-USDT");
    data.put("tdMode", "cash");
    data.put("ordId", ordId);
    data.put("clOrdId", clOrdId);
    data.put("side", "buy");
    data.put("ordType", "limit");
    data.put("sz", "1");
    data.put("px", "100.0");
    data.put("accFillSz", "1");
    data.put("fillPx", fillPx);
    data.put("tradeId", tradeId);
    data.put("fillSz", fillSz);
    data.put("fillTime", fillTime);
    data.put("avgPx", fillPx);
    data.put("state", "filled");
    data.put("feeCcy", "USDT");
    data.put("fee", fee);
    data.put("uTime", uTime);
    data.put("cTime", "1699999999000");
    return root;
  }

  private JsonNode positionEvent(
      String posId, String posSide, String position, String avgPx, String uTime) {
    ObjectNode root = mapper.createObjectNode();
    ObjectNode arg = root.putObject("arg");
    arg.put("channel", "positions");
    arg.put("instId", "BTC-USDT-SWAP");
    ObjectNode data = root.putArray("data").addObject();
    data.put("instType", "SWAP");
    data.put("instId", "BTC-USDT-SWAP");
    data.put("mgnMode", "cross");
    data.put("posId", posId);
    data.put("posSide", posSide);
    data.put("pos", position);
    data.put("avgPx", avgPx);
    data.put("upl", "1.5");
    data.put("liqPx", "90.0");
    data.put("uTime", uTime);
    data.put("lever", "10x");
    return root;
  }

  private JsonNode orderOpResponse(String code, String clOrdId, String sCode, String sMsg) {
    ObjectNode root = mapper.createObjectNode();
    root.put("id", "123456789");
    root.put("op", "order");
    root.put("code", code);
    root.put("msg", "");
    ArrayNode data = root.putArray("data");
    ObjectNode entry = data.addObject();
    entry.put("ordId", "o-123");
    if (clOrdId != null) {
      entry.put("clOrdId", clOrdId);
    }
    entry.put("sCode", sCode);
    entry.put("sMsg", sMsg);
    return root;
  }

  private LimitOrder limitOrder(String userReference) {
    return new LimitOrder.Builder(OrderType.BID, SPOT)
        .limitPrice(new BigDecimal("100.0"))
        .originalAmount(new BigDecimal("1"))
        .userReference(userReference)
        .build();
  }

  @Test
  public void testGetUserTradesDeduplicatesRedeliveredEvents() {
    JsonNode first = orderEvent("123", "client-1", "1699999999999", "T-1", "100.0", "1", "1699999999000", "0.1");
    JsonNode duplicate = orderEvent("123", "client-1", "1699999999999", "T-1", "100.0", "1", "1699999999000", "0.1");
    JsonNode second = orderEvent("124", "client-2", "1699999999998", "T-2", "99.5", "2", "1699999998000", "0.2");

    when(privateStreamingService.subscribeChannel(ORDER_CHANNEL))
        .thenReturn(Observable.just(first, duplicate, second));

    TestObserver<UserTrade> observer = tradeService.getUserTrades(SPOT).test();

    observer.assertNoErrors().assertValueCount(2);
    assertThat(observer.values().get(0).getId()).isEqualTo("T-1");
    assertThat(observer.values().get(0).getOrderId()).isEqualTo("123");
    assertThat(observer.values().get(1).getId()).isEqualTo("T-2");
  }

  @Test
  public void testGetOrderChangesDeduplicatesRedeliveredEvents() {
    JsonNode first = orderEvent("123", "client-1", "1699999999999", "T-1", "100.0", "1", "1699999999000", "0.1");
    JsonNode duplicate = orderEvent("123", "client-1", "1699999999999", "T-1", "100.0", "1", "1699999999000", "0.1");

    when(privateStreamingService.subscribeChannel(ORDER_CHANNEL))
        .thenReturn(Observable.just(first, duplicate));

    TestObserver<org.knowm.xchange.dto.Order> observer =
        tradeService.getOrderChanges(SPOT).test();

    observer.assertNoErrors().assertValueCount(1);
    assertThat(observer.values().get(0).getId()).isEqualTo("123");
    assertThat(observer.values().get(0).getUserReference()).isEqualTo("client-1");
  }

  @Test
  public void testGetPositionChangesDeduplicatesRedeliveredEvents() {
    JsonNode first = positionEvent("pos-1", "long", "1", "100.0", "1699999999999");
    JsonNode duplicate = positionEvent("pos-1", "long", "1", "100.0", "1699999999999");

    when(privateStreamingService.subscribeChannel(POSITION_CHANNEL))
        .thenReturn(Observable.just(first, duplicate));

    TestObserver<OpenPosition> observer = tradeService.getPositionChanges(SWAP).test();

    observer.assertNoErrors().assertValueCount(1);
    assertThat(observer.values().get(0).getSize()).isEqualByComparingTo("1");
  }

  @Test
  public void testDedupeCacheIsBounded() {
    tradeService =
        new OkxStreamingTradeService(
            privateStreamingService,
            new ExchangeMetaData(
                Collections.singletonMap(
                    SPOT, InstrumentMetaData.builder().build()),
                Collections.emptyMap(),
                null,
                null,
                null),
            OkxResilience.createRegistries(),
            2);

    JsonNode e1 = orderEvent("1", "c1", "1699999999999", "T-1", "100.0", "1", "1699999999000", "0.1");
    JsonNode e2 = orderEvent("2", "c2", "1699999999998", "T-2", "99.5", "2", "1699999998000", "0.2");
    JsonNode e3 = orderEvent("3", "c3", "1699999999997", "T-3", "99.0", "3", "1699999997000", "0.3");
    JsonNode e4 = orderEvent("4", "c4", "1699999999996", "T-4", "98.5", "4", "1699999996000", "0.4");

    when(privateStreamingService.subscribeChannel(ORDER_CHANNEL))
        .thenReturn(Observable.just(e1, e2, e3, e4, e1));

    TestObserver<UserTrade> observer = tradeService.getUserTrades(SPOT).test();

    // e1..e4 emitted once each; the re-delivered e1 was evicted from the cap-2 cache, so it is
    // emitted again — proving the cache stays bounded instead of growing without limit.
    observer.assertNoErrors().assertValueCount(5);
    assertThat(observer.values().get(4).getId()).isEqualTo("T-1");
  }

  @Test
  public void testUserTradeCarriesFillLevelCorrelationFields() {
    JsonNode first = orderEvent("123", "client-1", "1699999999999", "T-1", "100.0", "1", "1699999999000", "0.1");

    when(privateStreamingService.subscribeChannel(ORDER_CHANNEL))
        .thenReturn(Observable.just(first));

    UserTrade trade = tradeService.getUserTrades(SPOT).blockingFirst();

    assertThat(trade.getId()).isEqualTo("T-1"); // fill tradeId
    assertThat(trade.getOrderId()).isEqualTo("123"); // ordId
    assertThat(trade.getOrderUserReference()).isEqualTo("client-1"); // clOrdId
    assertThat(trade.getFeeAmount()).isEqualByComparingTo("0.1");
    assertThat(trade.getFeeCurrency().getCurrencyCode()).isEqualTo("USDT");
    assertThat(trade.getType()).isEqualTo(OrderType.BID);
    assertThat(trade.getPrice()).isEqualByComparingTo("100.0");
  }

  @Test
  public void testPlaceLimitOrderSucceedsWithMatchingClOrdId() {
    when(privateStreamingService.isLoginDone()).thenReturn(true);
    when(privateStreamingService.subscribeChannel(anyString(), eq(PLACE_ORDER), any()))
        .thenReturn(Observable.just(orderOpResponse("0", "client-1", "0", "")));

    assertThat(tradeService.placeLimitOrder(limitOrder("client-1")).blockingGet()).isEqualTo(0);
  }

  @Test
  public void testPlaceLimitOrderRejectsClOrdIdMismatch() {
    when(privateStreamingService.isLoginDone()).thenReturn(true);
    when(privateStreamingService.subscribeChannel(anyString(), eq(PLACE_ORDER), any()))
        .thenReturn(Observable.just(orderOpResponse("0", "someone-else", "0", "")));

    Single<Integer> single = tradeService.placeLimitOrder(limitOrder("client-1"));

    assertThatThrownBy(single::blockingGet)
        .isInstanceOf(OrderNotValidException.class)
        .hasMessageContaining("clOrdId")
        .hasMessageContaining("someone-else")
        .hasMessageContaining("client-1");
  }

  @Test
  public void testPlaceMarketOrderSurfacesPerOrderRejection() {
    when(privateStreamingService.isLoginDone()).thenReturn(true);
    when(privateStreamingService.subscribeChannel(anyString(), eq(PLACE_ORDER), any()))
        .thenReturn(Observable.just(orderOpResponse("0", "client-1", "51000", "Insufficient balance")));

    MarketOrder marketOrder =
        new MarketOrder.Builder(OrderType.BID, SPOT)
            .originalAmount(new BigDecimal("1"))
            .userReference("client-1")
            .build();

    Single<Integer> single = tradeService.placeMarketOrder(marketOrder);

    assertThatThrownBy(single::blockingGet)
        .isInstanceOf(OrderNotValidException.class)
        .hasMessageContaining("51000")
        .hasMessageContaining("Insufficient balance");
  }

  @Test
  public void testCancelOrderSurfacesResponseLevelFailure() {
    when(privateStreamingService.isLoginDone()).thenReturn(true);
    when(privateStreamingService.subscribeChannel(anyString(), eq(OkxPrivateStreamingService.CANCEL_ORDER), any()))
        .thenReturn(Observable.just(orderOpResponse("1", null, null, "Operation failed")));

    OkxCancelOrderParams params = new OkxCancelOrderParams(SPOT, "o-123", "client-1");

    Single<Integer> single = tradeService.cancelOrder(params);

    assertThatThrownBy(single::blockingGet)
        .isInstanceOf(OrderNotValidException.class)
        .hasMessageContaining("Operation failed");
  }

  @Test
  public void testPlaceOrderThrowsExchangeExceptionWhenNotAuthorized() {
    when(privateStreamingService.isLoginDone()).thenReturn(false);

    assertThatThrownBy(() -> tradeService.placeLimitOrder(limitOrder("client-1")))
        .isInstanceOf(ExchangeException.class)
        .hasMessageContaining("not authorized");
  }
}
