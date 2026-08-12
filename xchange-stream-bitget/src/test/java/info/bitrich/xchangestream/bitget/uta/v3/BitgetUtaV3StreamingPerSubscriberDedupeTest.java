package info.bitrich.xchangestream.bitget.uta.v3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import info.bitrich.xchangestream.bitget.config.Config;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3AccountData;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3AccountData.BitgetUtaV3CoinData;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3Channel;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3FillData;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3InstType;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3WsNotification;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subjects.PublishSubject;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.bitget.uta.v3.service.BitgetUtaV3TradeService;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.trade.UserTrade;

/**
 * Deduping is per subscriber, not per service: two subscribers on the same push stream must each
 * observe every distinct push.
 *
 * <p>The trade and account pipelines share one underlying channel stream but keep a bounded LRU of
 * last-seen payloads to suppress repeats (replayed snapshots after a reconnect must not re-emit
 * stale state). That dedupe is built per subscription (inside an {@link
 * io.reactivex.rxjava3.core.Observable#defer}), so one subscriber seeing a push must not suppress
 * it for another subscriber of the same channel.
 */
class BitgetUtaV3StreamingPerSubscriberDedupeTest {

  private static final CurrencyPair BTC = CurrencyPair.BTC_USDT;

  private static BitgetUtaV3WsNotification fillPush(String execId) {
    return BitgetUtaV3WsNotification.builder()
        .channel(
            BitgetUtaV3Channel.builder().instType(BitgetUtaV3InstType.UTA).topic("fill").build())
        .payloadItem(
            Config.getInstance()
                .getObjectMapper()
                .valueToTree(
                    BitgetUtaV3FillData.builder()
                        .execId(execId)
                        .symbol("BTCUSDT")
                        .category("spot")
                        .side("buy")
                        .execQty(new BigDecimal("0.1"))
                        .execPrice(new BigDecimal("60000"))
                        .orderId("o1")
                        .build()))
        .build();
  }

  private static BitgetUtaV3WsNotification balancePush() {
    return BitgetUtaV3WsNotification.builder()
        .channel(
            BitgetUtaV3Channel.builder()
                .instType(BitgetUtaV3InstType.UTA)
                .topic("account")
                .build())
        .payloadItem(
            Config.getInstance()
                .getObjectMapper()
                .valueToTree(
                    BitgetUtaV3AccountData.builder()
                        .coins(
                            List.of(
                                BitgetUtaV3CoinData.builder()
                                    .coin("BTC")
                                    .balance(new BigDecimal("1.5"))
                                    .equity(new BigDecimal("1.5"))
                                    .available(new BigDecimal("1.2"))
                                    .locked(new BigDecimal("0.3"))
                                    .build()))
                        .build()))
        .build();
  }

  private static BitgetUtaV3PrivateStreamingService newMockedService(
      PublishSubject<BitgetUtaV3WsNotification> pushes) {
    BitgetUtaV3PrivateStreamingService service =
        mock(BitgetUtaV3PrivateStreamingService.class);
    when(service.sharedChannel(any(BitgetUtaV3Channel.class))).thenReturn(pushes);
    when(service.subscribeDisconnect()).thenReturn(PublishSubject.create());
    return service;
  }

  @Test
  void twoTradeSubscribersEachSeeEveryDistinctFill() {
    PublishSubject<BitgetUtaV3WsNotification> pushes = PublishSubject.create();
    BitgetUtaV3StreamingTradeService trade =
        new BitgetUtaV3StreamingTradeService(
            newMockedService(pushes), mock(BitgetUtaV3TradeService.class));

    // both subscribers share the returned pipeline; per-subscriber dedupe must not let the first
    // subscriber's dedupe starve the second
    Observable<UserTrade> stream = trade.getUserTrades(BTC);
    TestObserver<UserTrade> first = stream.test();
    TestObserver<UserTrade> second = stream.test();

    pushes.onNext(fillPush("e1"));
    pushes.onNext(fillPush("e2"));

    assertThat(first.values()).hasSize(2);
    assertThat(second.values()).hasSize(2);
    assertThat(first.values().get(0).getId()).isEqualTo("e1");
    assertThat(second.values().get(1).getId()).isEqualTo("e2");
  }

  @Test
  void allTradesSubscribersEachSeeEveryDistinctFill() {
    PublishSubject<BitgetUtaV3WsNotification> pushes = PublishSubject.create();
    BitgetUtaV3StreamingTradeService trade =
        new BitgetUtaV3StreamingTradeService(
            newMockedService(pushes), mock(BitgetUtaV3TradeService.class));

    Observable<UserTrade> stream = trade.getUserTrades();
    TestObserver<UserTrade> first = stream.test();
    TestObserver<UserTrade> second = stream.test();

    pushes.onNext(fillPush("e1"));

    assertThat(first.values()).hasSize(1);
    assertThat(second.values()).hasSize(1);
  }

  @Test
  void twoBalanceSubscribersEachSeeEveryDistinctPush() {
    PublishSubject<BitgetUtaV3WsNotification> pushes = PublishSubject.create();
    BitgetUtaV3StreamingAccountService account =
        new BitgetUtaV3StreamingAccountService(newMockedService(pushes));

    Observable<Balance> stream = account.getBalanceChanges(Currency.BTC);
    TestObserver<Balance> first = stream.test();
    TestObserver<Balance> second = stream.test();

    pushes.onNext(balancePush());

    assertThat(first.values()).hasSize(1);
    assertThat(second.values()).hasSize(1);
  }
}
