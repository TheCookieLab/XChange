package info.bitrich.xchangestream.mexc;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.mxc.push.common.protobuf.PublicAggreDealsV3Api;
import com.mxc.push.common.protobuf.PublicAggreDepthV3ApiItem;
import com.mxc.push.common.protobuf.PublicAggreDepthsV3Api;
import com.mxc.push.common.protobuf.PushDataV3ApiWrapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.mexc.v3.MexcV3Exchange;
import org.knowm.xchange.mexc.v3.service.MexcV3MarketDataServiceRaw;

/**
 * Incremental order-book reconciliation: REST snapshot fetch on first push/version gap, delta
 * application, quantity-zero removals, and stale-push discards. REST is served by WireMock; pushes
 * are canonical JSON of real protobuf wrappers.
 */
class MexcV3StreamingOrderBookTest {

  private static final CurrencyPair PAIR = new CurrencyPair(Currency.BTC, Currency.USDT);
  private static final String DEPTH_PATH = "/api/v3/depth?symbol=BTCUSDT&limit=5000";

  private WireMockServer wireMock;
  private MexcV3StreamingOrderBook orderBook;

  @BeforeEach
  void setUp() throws IOException {
    wireMock = new WireMockServer(wireMockConfig().dynamicPort());
    wireMock.start();
    orderBook =
        new MexcV3StreamingOrderBook(PAIR, createRawMarketDataService(wireMock.port()));
  }

  @AfterEach
  void tearDown() {
    wireMock.stop();
  }

  private static MexcV3MarketDataServiceRaw createRawMarketDataService(int port) throws IOException {
    MexcV3Exchange exchange =
        (MexcV3Exchange)
            ExchangeFactory.INSTANCE.createExchangeWithoutSpecification(MexcV3Exchange.class);
    ExchangeSpecification specification = exchange.getDefaultExchangeSpecification();
    specification.setHost("localhost");
    specification.setSslUri("http://localhost:" + port);
    specification.setPort(port);
    specification.setShouldLoadRemoteMetaData(false);
    exchange.applySpecification(specification);
    return (MexcV3MarketDataServiceRaw) exchange.getMarketDataService();
  }

  private static String snapshotJson(long lastUpdateId) {
    return "{\"lastUpdateId\":" + lastUpdateId + ","
        + "\"bids\":[[\"100.0\",\"1.0\"],[\"99.0\",\"2.0\"]],"
        + "\"asks\":[[\"101.0\",\"3.0\"]]}";
  }

  private static void stubSnapshot(WireMockServer wireMock, long lastUpdateId) {
    wireMock.stubFor(get(urlEqualTo(DEPTH_PATH)).willReturn(aResponse().withBody(snapshotJson(lastUpdateId))));
  }

  private static String depthPushJson(long fromVersion, long toVersion, long createTime) {
    PublicAggreDepthsV3Api delta =
        PublicAggreDepthsV3Api.newBuilder()
            .setFromVersion(String.valueOf(fromVersion))
            .setToVersion(String.valueOf(toVersion))
            .addBids(PublicAggreDepthV3ApiItem.newBuilder().setPrice("100.0").setQuantity("0.5"))
            .addBids(PublicAggreDepthV3ApiItem.newBuilder().setPrice("98.0").setQuantity("4.0"))
            .addAsks(PublicAggreDepthV3ApiItem.newBuilder().setPrice("101.0").setQuantity("2.5"))
            .build();
    PushDataV3ApiWrapper wrapper =
        PushDataV3ApiWrapper.newBuilder()
            .setChannel("spot@public.aggre.depth.v3.api.pb@100ms@BTCUSDT")
            .setSymbol("BTCUSDT")
            .setCreateTime(createTime)
            .setPublicAggreDepths(delta)
            .build();
    try {
      return MexcV3ProtoCodec.toJson(wrapper);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static String removalPushJson(long fromVersion, long toVersion) {
    PublicAggreDepthsV3Api delta =
        PublicAggreDepthsV3Api.newBuilder()
            .setFromVersion(String.valueOf(fromVersion))
            .setToVersion(String.valueOf(toVersion))
            .addBids(PublicAggreDepthV3ApiItem.newBuilder().setPrice("99.0").setQuantity("0"))
            .build();
    PushDataV3ApiWrapper wrapper =
        PushDataV3ApiWrapper.newBuilder()
            .setChannel("spot@public.aggre.depth.v3.api.pb@100ms@BTCUSDT")
            .setSymbol("BTCUSDT")
            .setCreateTime(99L)
            .setPublicAggreDepths(delta)
            .build();
    try {
      return MexcV3ProtoCodec.toJson(wrapper);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }


  @Test
  void firstPushFetchesSnapshotAndAppliesDelta() {
    stubSnapshot(wireMock, 100L);

    OrderBook book =
        orderBook
            .onDelta(depthPushJson(101L, 101L, 1_712_345_678_901L))
            .blockingFirst();

    assertBook(book, 1_712_345_678_901L);
    wireMock.verify(1, getRequestedFor(urlEqualTo(DEPTH_PATH)));
  }

  @Test
  void overlappingSnapshotDeltaIsApplied() {
    stubSnapshot(wireMock, 100L);

    OrderBook book =
        orderBook.onDelta(depthPushJson(95L, 105L, 1_712_345_678_901L)).blockingFirst();
    assertBook(book, 1_712_345_678_901L);
    wireMock.verify(1, getRequestedFor(urlEqualTo(DEPTH_PATH)));
  }

  @Test
  void quantityZeroRemovesLevel() {
    stubSnapshot(wireMock, 100L);
    orderBook.onDelta(depthPushJson(101L, 101L, 1L)).blockingFirst();

    OrderBook book = orderBook.onDelta(removalPushJson(102L, 102L)).blockingFirst();

    assertEquals(2, book.getBids().size(), "level with quantity 0 must be removed");
    assertEquals(
        new BigDecimal("100.0"),
        book.getBids().get(0).getLimitPrice(),
        "removed level must not reappear");
    assertEquals(
        new BigDecimal("98.0"),
        book.getBids().get(1).getLimitPrice(),
        "unrelated levels survive the removal");
    wireMock.verify(1, getRequestedFor(urlEqualTo(DEPTH_PATH)));
  }

  @Test
  void snapshotWithinPushWindowIsApplied() {
    stubSnapshot(wireMock, 102L);

    OrderBook book =
        orderBook
            .onDelta(depthPushJson(101L, 103L, 1_712_345_678_901L))
            .blockingFirst();

    assertBook(book, 1_712_345_678_901L);
    wireMock.verify(1, getRequestedFor(urlEqualTo(DEPTH_PATH)));
  }

  @Test
  void stalePushIsDropped() {
    stubSnapshot(wireMock, 100L);
    orderBook.onDelta(depthPushJson(101L, 101L, 1L)).blockingFirst();

    long emissions = orderBook.onDelta(depthPushJson(99L, 100L, 2L)).count().blockingGet();

    assertEquals(0, emissions, "push whose toVersion is behind the snapshot must be dropped");
    wireMock.verify(1, getRequestedFor(urlEqualTo(DEPTH_PATH)));
  }



  @Test
  void versionGapRefetchesAndDropsMisalignedPush() {
    stubSnapshot(wireMock, 100L);

    long gapEmissions =
        orderBook.onDelta(depthPushJson(105L, 106L, 1L)).count().blockingGet();

    assertEquals(0, gapEmissions, "push that still does not continue from the snapshot is dropped");

    OrderBook book = orderBook.onDelta(depthPushJson(101L, 101L, 2L)).blockingFirst();

    assertBook(book, 2L);
    wireMock.verify(1, getRequestedFor(urlEqualTo(DEPTH_PATH)));
  }

  @Test
  void staleSnapshotCannotRewindSequenceAfterGap() {
    stubSnapshot(wireMock, 100L);

    orderBook.onDelta(depthPushJson(101L, 101L, 1L)).blockingFirst();

    long gapEmissions =
        orderBook.onDelta(depthPushJson(105L, 106L, 2L)).count().blockingGet();
    assertEquals(0, gapEmissions, "a gap push must be dropped while the snapshot is reconciled");

    long staleEmissions =
        orderBook.onDelta(depthPushJson(101L, 101L, 3L)).count().blockingGet();
    assertEquals(
        0,
        staleEmissions,
        "a stale REST snapshot must not rewind the local sequence and re-apply an old push");
    wireMock.verify(2, getRequestedFor(urlEqualTo(DEPTH_PATH)));
  }

  @Test
  void wrongBodyFailsTheStream() {
    PushDataV3ApiWrapper dealsWrapper =
        PushDataV3ApiWrapper.newBuilder()
            .setChannel("spot@public.aggre.depth.v3.api.pb@100ms@BTCUSDT")
            .setSymbol("BTCUSDT")
            .setPublicAggreDeals(PublicAggreDealsV3Api.getDefaultInstance())
            .build();
    String json;
    try {
      json = MexcV3ProtoCodec.toJson(dealsWrapper);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }

    orderBook
        .onDelta(json)
        .test()
        .awaitDone(2, java.util.concurrent.TimeUnit.SECONDS)
        .assertError(IllegalArgumentException.class);
  }

  private static void assertBook(OrderBook book, long expectedCreateTime) {
    assertEquals(new Date(expectedCreateTime), book.getTimeStamp());
    List<LimitOrder> bids = book.getBids();
    List<LimitOrder> asks = book.getAsks();
    assertEquals(3, bids.size());
    assertEquals(1, asks.size());
    assertEquals(new BigDecimal("100.0"), bids.get(0).getLimitPrice());
    assertEquals(new BigDecimal("0.5"), bids.get(0).getOriginalAmount());
    assertEquals(new BigDecimal("99.0"), bids.get(1).getLimitPrice());
    assertEquals(new BigDecimal("2.0"), bids.get(1).getOriginalAmount());
    assertEquals(new BigDecimal("98.0"), bids.get(2).getLimitPrice());
    assertEquals(new BigDecimal("4.0"), bids.get(2).getOriginalAmount());
    assertEquals(new BigDecimal("101.0"), asks.get(0).getLimitPrice());
    assertEquals(new BigDecimal("2.5"), asks.get(0).getOriginalAmount());
  }
}
