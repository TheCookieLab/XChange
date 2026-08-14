package info.bitrich.xchangestream.mexc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mxc.push.common.protobuf.PrivateAccountV3Api;
import com.mxc.push.common.protobuf.PushDataV3ApiWrapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.Balance;

/** Account stream: channel name, balance adaptation, and per-currency filtering. */
class MexcV3StreamingAccountServiceTest {

  private static final String CHANNEL = "spot@private.account.v3.api.pb";

  private static String accountPushJson(String vcoinName, String balance, String frozen) {
    PrivateAccountV3Api account =
        PrivateAccountV3Api.newBuilder()
            .setVcoinName(vcoinName)
            .setBalanceAmount(balance)
            .setFrozenAmount(frozen)
            .setTime(1_712_345_678_901L)
            .build();
    PushDataV3ApiWrapper wrapper =
        PushDataV3ApiWrapper.newBuilder()
            .setChannel(CHANNEL)
            .setSymbol("BTCUSDT")
            .setPrivateAccount(account)
            .build();
    try {
      return MexcV3ProtoCodec.toJson(wrapper);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void getBalanceChangesSubscribesChannelAndMapsBalance() {
    StubStreamingService service = new StubStreamingService();
    service.enqueue(CHANNEL, accountPushJson("BTC", "1.50000000", "0.25000000"));
    MexcV3StreamingAccountService accountService = new MexcV3StreamingAccountService(service);

    Balance balance = accountService.getBalanceChanges(Currency.BTC).blockingFirst();

    assertEquals(Currency.BTC, balance.getCurrency());
    assertEquals(new BigDecimal("1.50000000"), balance.getTotal());
    assertEquals(new BigDecimal("0.25000000"), balance.getFrozen());
    assertEquals(new BigDecimal("1.25000000"), balance.getAvailable());
    assertTrue(service.subscribedChannels.contains(CHANNEL));
  }

  @Test
  void getBalanceChangesFiltersOtherCurrencies() {
    StubStreamingService service = new StubStreamingService();
    service.enqueue(CHANNEL, accountPushJson("ETH", "2.0", "0.5"));
    service.enqueue(CHANNEL, accountPushJson("BTC", "1.5", "0"));
    MexcV3StreamingAccountService accountService = new MexcV3StreamingAccountService(service);

    accountService
        .getBalanceChanges(Currency.USDT)
        .test()
        .awaitDone(1, java.util.concurrent.TimeUnit.SECONDS)
        .assertNoValues();

    Balance all = accountService.getBalanceChanges(null).blockingFirst();
    assertEquals(Currency.BTC, all.getCurrency(), "null currency receives every balance event");
  }

  /** Captures subscribed channels and serves enqueued canned pushes. */
  private static final class StubStreamingService extends MexcV3StreamingService {

    private final List<String> subscribedChannels = new ArrayList<>();
    private final Map<String, Queue<String>> pushes = new HashMap<>();

    StubStreamingService() {
      super("ws://stub/ws");
    }

    void enqueue(String channel, String json) {
      pushes.computeIfAbsent(channel, k -> new LinkedList<>()).add(json);
    }

    @Override
    public io.reactivex.rxjava3.core.Observable<String> subscribeChannel(
        String channelName, Object... args) {
      subscribedChannels.add(channelName);
      return io.reactivex.rxjava3.core.Observable.defer(
          () -> {
            Queue<String> queue = pushes.get(channelName);
            String next = queue == null ? null : queue.poll();
            return next == null
                ? io.reactivex.rxjava3.core.Observable.never()
                : io.reactivex.rxjava3.core.Observable.just(next);
          });
    }
  }
}
