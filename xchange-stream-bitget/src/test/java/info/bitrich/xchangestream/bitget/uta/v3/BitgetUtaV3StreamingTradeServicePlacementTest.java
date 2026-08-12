package info.bitrich.xchangestream.bitget.uta.v3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import info.bitrich.xchangestream.service.netty.NettyStreamingService;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.bitget.uta.v3.service.BitgetUtaV3TradeService;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.exceptions.FundsExceededException;

/**
 * Placement outcome semantics of {@link BitgetUtaV3StreamingTradeService}.
 *
 * <p>The unknown-outcome guardrail must fire only for placements whose outcome is genuinely
 * unknown: the private socket disconnects while the REST placement is in flight. Placements that
 * were never submitted, definitively rejected by the provider, or already confirmed by the REST
 * call have a known outcome and must never resurface as unknown on a later, unrelated socket
 * disconnect.
 */
class BitgetUtaV3StreamingTradeServicePlacementTest {

  private static final CurrencyPair PAIR = CurrencyPair.BTC_USDT;

  private BitgetUtaV3PrivateStreamingService service;
  private BitgetUtaV3TradeService restTradeService;
  private PublishSubject<Object> disconnectSubject;
  private BitgetUtaV3StreamingTradeService tradeService;

  @BeforeEach
  void setUp() {
    service = mock(BitgetUtaV3PrivateStreamingService.class);
    restTradeService = mock(BitgetUtaV3TradeService.class);
    disconnectSubject = PublishSubject.create();
    when(service.subscribeDisconnect()).thenReturn(disconnectSubject);
    tradeService = new BitgetUtaV3StreamingTradeService(service, restTradeService);
  }

  private MarketOrder marketOrder() {
    return new MarketOrder.Builder(OrderType.BID, PAIR)
        .originalAmount(new BigDecimal("0.001"))
        .build();
  }

  private static MarketOrder marketOrder(String clientOid) {
    return new MarketOrder.Builder(OrderType.BID, PAIR)
        .originalAmount(new BigDecimal("0.01"))
        .userReference(clientOid)
        .build();
  }

  private static BitgetUtaV3PrivateStreamingService newPrivateService() {
    return new BitgetUtaV3PrivateStreamingService(
        "wss://localhost/private", "apiKey", "apiSecret", "passphrase");
  }

  private static void fireDisconnect(BitgetUtaV3StreamingService service) throws Exception {
    Field field = NettyStreamingService.class.getDeclaredField("disconnectEmitters");
    field.setAccessible(true);
    ((io.reactivex.rxjava3.subjects.Subject<Object>) field.get(service)).onNext(new Object());
  }

  @Test
  void rejectedPlacementMustNotFailAsUnknownOutcomeOnLaterDisconnect() throws Exception {
    when(restTradeService.placeMarketOrder(any(MarketOrder.class)))
        .thenThrow(new FundsExceededException("insufficient balance"));

    // the rejection is delivered synchronously to the placement caller
    TestObserver<Integer> placement = tradeService.placeMarketOrder(marketOrder()).test();
    placement.assertError(FundsExceededException.class);

    // the rejected placement's outcome is known (rejected), so a later disconnect must
    // not raise it again as an unknown outcome
    TestObserver<Throwable> failures = tradeService.subscribePlacementFailures().test();
    disconnectSubject.onNext(new Object());
    failures.assertNoValues();
  }

  @Test
  void disconnectBeforePlacementSingleIsSubscribedDoesNotReportUnknownOutcome() throws Exception {
    BitgetUtaV3PrivateStreamingService ws = newPrivateService();
    BitgetUtaV3TradeService rest = mock(BitgetUtaV3TradeService.class);
    when(rest.placeMarketOrder(any())).thenReturn("order-1");
    BitgetUtaV3StreamingTradeService trade = new BitgetUtaV3StreamingTradeService(ws, rest);

    TestObserver<Throwable> failures = trade.subscribePlacementFailures().test();

    // the placement is created but never subscribed: it must not be treated as pending
    Single<Integer> pending = trade.placeMarketOrder(marketOrder("client-1"));
    fireDisconnect(ws);

    failures.assertNoValues();

    // the placement still proceeds once subscribed, without any failure being reported
    pending.test().assertValue(0).assertNoErrors();
    verify(rest).placeMarketOrder(any());
  }

  @Test
  void successfullyPlacedOrdersAreNotReportedAsUnknownOutcomeOnDisconnect() throws Exception {
    BitgetUtaV3PrivateStreamingService ws = newPrivateService();
    BitgetUtaV3TradeService rest = mock(BitgetUtaV3TradeService.class);
    when(rest.placeMarketOrder(any())).thenReturn("order-1");
    BitgetUtaV3StreamingTradeService trade = new BitgetUtaV3StreamingTradeService(ws, rest);

    TestObserver<Throwable> failures = trade.subscribePlacementFailures().test();

    for (int i = 0; i < 3; i++) {
      trade.placeMarketOrder(marketOrder("client-" + i)).test().assertValue(0).assertNoErrors();
    }

    fireDisconnect(ws);

    // confirmed placements are not pending; a disconnect must not report them as unknown
    failures.assertNoValues();
  }

  @Test
  void disconnectWhilePlacementIsInFlightStillReportsUnknownOutcome() throws Exception {
    BitgetUtaV3PrivateStreamingService ws = newPrivateService();
    BitgetUtaV3TradeService rest = mock(BitgetUtaV3TradeService.class);
    CountDownLatch requestStarted = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    when(rest.placeMarketOrder(any()))
        .thenAnswer(
            invocation -> {
              requestStarted.countDown();
              release.await(10, TimeUnit.SECONDS);
              return "order-1";
            });
    BitgetUtaV3StreamingTradeService trade = new BitgetUtaV3StreamingTradeService(ws, rest);

    TestObserver<Throwable> failures = trade.subscribePlacementFailures().test();

    Single<Integer> pending =
        trade.placeMarketOrder(marketOrder("client-1")).subscribeOn(Schedulers.computation());
    TestObserver<Integer> result = pending.test();

    assertThat(requestStarted.await(10, TimeUnit.SECONDS)).isTrue();

    // the guardrail must still fire while the REST call is in flight (unknown outcome)
    fireDisconnect(ws);
    failures.assertValueCount(1);

    release.countDown();
    result.awaitDone(10, TimeUnit.SECONDS).assertValue(0).assertNoErrors();
    failures.assertValueCount(1);
  }
}
