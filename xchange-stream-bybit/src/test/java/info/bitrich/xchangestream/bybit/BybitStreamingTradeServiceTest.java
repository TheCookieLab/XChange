package info.bitrich.xchangestream.bybit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.service.netty.StreamingObjectMapperHelper;
import io.reactivex.rxjava3.core.Observable;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bybit.BybitExchange;
import org.knowm.xchange.bybit.dto.BybitCategory;
import org.knowm.xchange.bybit.dto.trade.BybitCancelOrderParams;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.instrument.Instrument;

/** Tests order-entry availability and private order-change subscriptions. */
public class BybitStreamingTradeServiceTest {

  private final BybitStreamingTradeService tradeServiceWithoutOrderEntryTransport;
  private BybitUserDataStreamingService userDataStreamingService;
  private BybitStreamingTradeService tradeService;
  private final ObjectMapper mapper = StreamingObjectMapperHelper.getObjectMapper();

  public BybitStreamingTradeServiceTest() {
    BybitStreamingExchange exchange = new BybitStreamingExchange();
    ExchangeSpecification spec = new ExchangeSpecification(BybitExchange.class);
    spec.setShouldLoadRemoteMetaData(false);
    exchange.applySpecification(spec);
    // The demo environment leaves the order-entry transport null; only the private
    // user-data stream is constructed.
    this.tradeServiceWithoutOrderEntryTransport =
        new BybitStreamingTradeService(
            null, null, exchange.getResilienceRegistries(), exchange);
  }

  @BeforeEach
  void setUp() {
    BybitExchange exchange = new BybitExchange();
    ExchangeSpecification specification = exchange.getDefaultExchangeSpecification();
    specification.setShouldLoadRemoteMetaData(false);
    specification.setApiKey("api-key");
    specification.setSecretKey("api-secret");
    exchange.applySpecification(specification);

    userDataStreamingService = mock(BybitUserDataStreamingService.class);
    tradeService =
        new BybitStreamingTradeService(
            userDataStreamingService,
            mock(BybitUserTradeStreamingService.class),
            exchange.getResilienceRegistries(),
            exchange);
  }

  private static final class InstrumentStub extends org.knowm.xchange.instrument.Instrument {
    @Override
    public org.knowm.xchange.currency.Currency getBase() {
      return org.knowm.xchange.currency.Currency.BTC;
    }

    @Override
    public org.knowm.xchange.currency.Currency getCounter() {
      return org.knowm.xchange.currency.Currency.USDT;
    }
  }

  @Test
  public void placeMarketOrderFailsClearlyWithoutOrderEntryTransport() {
    MarketOrder order =
        new MarketOrder.Builder(OrderType.BID, CurrencyPair.BTC_USDT)
            .originalAmount(BigDecimal.ONE)
            .build();
    Throwable thrown =
        catchThrowable(() -> tradeServiceWithoutOrderEntryTransport.placeMarketOrder(order));
    assertDemoTradeUnsupported(thrown);
  }

  @Test
  public void placeLimitOrderFailsClearlyWithoutOrderEntryTransport() {
    LimitOrder order =
        new LimitOrder.Builder(OrderType.BID, CurrencyPair.BTC_USDT)
            .originalAmount(BigDecimal.ONE)
            .limitPrice(BigDecimal.ONE)
            .build();
    Throwable thrown =
        catchThrowable(() -> tradeServiceWithoutOrderEntryTransport.placeLimitOrder(order));
    assertDemoTradeUnsupported(thrown);
  }

  @Test
  public void changeOrderFailsClearlyWithoutOrderEntryTransport() {
    LimitOrder order =
        new LimitOrder.Builder(OrderType.BID, CurrencyPair.BTC_USDT)
            .originalAmount(BigDecimal.ONE)
            .limitPrice(BigDecimal.ONE)
            .build();
    Throwable thrown = catchThrowable(() -> tradeServiceWithoutOrderEntryTransport.changeOrder(order));
    assertDemoTradeUnsupported(thrown);
  }

  @Test
  public void cancelOrderFailsClearlyWithoutOrderEntryTransport() {
    BybitCancelOrderParams params = new BybitCancelOrderParams(new InstrumentStub(), "order-id", null);
    Throwable thrown =
        catchThrowable(() -> tradeServiceWithoutOrderEntryTransport.cancelOrder(params));
    assertDemoTradeUnsupported(thrown);
  }

  @Test
  public void batchOperationsFailClearlyWithoutOrderEntryTransport() {
    LimitOrder order =
        new LimitOrder.Builder(OrderType.BID, CurrencyPair.BTC_USDT)
            .originalAmount(BigDecimal.ONE)
            .limitPrice(BigDecimal.ONE)
            .build();
    assertDemoTradeUnsupported(
        catchThrowable(() -> tradeServiceWithoutOrderEntryTransport.batchChangeOrder(List.of(order))));
    assertDemoTradeUnsupported(
        catchThrowable(() -> tradeServiceWithoutOrderEntryTransport.batchCancelOrder(List.of())));
  }

  private void assertDemoTradeUnsupported(Throwable thrown) {
    assertThat(thrown)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("demo trading does not support the WebSocket order-entry")
        .hasMessageContaining("REST trade service");
  }

  private void givenOrderChanges() throws Exception {
    JsonNode jsonNode =
        mapper.readTree(
            ClassLoader.getSystemClassLoader()
                .getResourceAsStream("orderChangeSpotResponse.json5"));
    when(userDataStreamingService.subscribeChannel("order")).thenReturn(Observable.just(jsonNode));
    when(userDataStreamingService.subscribeChannel("order.spot"))
        .thenReturn(Observable.just(jsonNode));
  }

  @Test
  void getOrderChangesWithoutCategoryArgument_shouldSubscribeToTheAllCategoriesTopic()
      throws Exception {
    givenOrderChanges();

    List<Order> orders = tradeService.getOrderChanges(CurrencyPair.XRP_USDT).toList().blockingGet();

    verify(userDataStreamingService).subscribeChannel("order");
    assertThat(orders).hasSize(1);
    assertThat(orders.get(0).getInstrument()).isEqualTo(CurrencyPair.XRP_USDT);
  }

  @Test
  void getOrderChangesWithCategoryArgument_shouldSubscribeToTheCategorisedTopic() throws Exception {
    givenOrderChanges();

    List<Order> orders =
        tradeService
            .getOrderChanges(CurrencyPair.XRP_USDT, BybitCategory.SPOT)
            .toList()
            .blockingGet();

    verify(userDataStreamingService).subscribeChannel("order.spot");
    assertThat(orders).hasSize(1);
  }

  @Test
  void getOrderChangesWithoutInstrument_shouldEmitEveryInstrument() throws Exception {
    givenOrderChanges();

    List<Order> orders = tradeService.getOrderChanges((Instrument) null).toList().blockingGet();

    assertThat(orders).hasSize(2);
    assertThat(orders).extracting(Order::getInstrument).contains(CurrencyPair.XRP_USDT);
  }

  @Test
  void getOrderChangesWithUnknownInstrument_shouldEmitNothing() throws Exception {
    givenOrderChanges();

    List<Order> orders = tradeService.getOrderChanges(CurrencyPair.ETH_USDT).toList().blockingGet();

    assertThat(orders).isEmpty();
  }
}
