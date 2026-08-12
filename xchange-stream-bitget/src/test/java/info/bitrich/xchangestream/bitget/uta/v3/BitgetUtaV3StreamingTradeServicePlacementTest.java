package info.bitrich.xchangestream.bitget.uta.v3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subjects.PublishSubject;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.bitget.uta.v3.BitgetUtaV3UnknownOutcomeException;
import org.knowm.xchange.bitget.uta.v3.service.BitgetUtaV3TradeService;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.exceptions.FundsExceededException;

/**
 * Placement failure semantics of {@link BitgetUtaV3StreamingTradeService}.
 *
 * <p>The unknown-outcome guardrail must only fire for placements whose outcome is genuinely
 * unknown (accepted by REST but never confirmed on the private socket). A placement that was
 * definitively rejected by the provider must never resurface as an unknown outcome on a later,
 * unrelated socket disconnect.
 */
class BitgetUtaV3StreamingTradeServicePlacementTest {

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
    return new MarketOrder.Builder(OrderType.BID, CurrencyPair.BTC_USDT)
        .originalAmount(new BigDecimal("0.001"))
        .build();
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
  void acceptedButUnconfirmedPlacementFailsAsUnknownOutcomeOnDisconnect() throws Exception {
    when(restTradeService.placeMarketOrder(any(MarketOrder.class))).thenReturn("oid-1");

    TestObserver<Integer> placement = tradeService.placeMarketOrder(marketOrder()).test();
    placement.assertValue(0);

    // guardrail: accepted placement with no confirmation push is an unknown outcome
    TestObserver<Throwable> failures = tradeService.subscribePlacementFailures().test();
    disconnectSubject.onNext(new Object());
    failures.assertValueCount(1);
    failures.assertValue(throwable -> throwable instanceof BitgetUtaV3UnknownOutcomeException);
  }
}
