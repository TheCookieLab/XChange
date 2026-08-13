package info.bitrich.xchangestream.bybit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.math.BigDecimal;
import java.util.List;
import org.junit.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bybit.BybitExchange;
import org.knowm.xchange.bybit.dto.trade.BybitCancelOrderParams;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.bybit.dto.trade.BybitCancelOrderParams;

/**
 * In the demo environment the order-entry (trade) transport is not constructed; order operations
 * must fail with a clear explanation instead of a NullPointerException or a silent reroute.
 */
public class BybitStreamingTradeServiceTest {

  private final BybitStreamingTradeService tradeServiceWithoutOrderEntryTransport;

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
}
