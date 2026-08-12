package info.bitrich.xchangestream.bitget.uta.v3;

import info.bitrich.xchangestream.bitget.config.Config;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3AccountData;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3Channel;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3InstType;
import info.bitrich.xchangestream.core.StreamingAccountService;
import io.reactivex.rxjava3.core.Observable;
import java.util.LinkedHashMap;
import java.util.Map;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.Balance;

/**
 * Bitget UTA v3 private WebSocket account service.
 *
 * <p>The {@code account} channel (instType {@code UTA}, account-wide) pushes the full margin
 * account snapshot on every change; each push contains per-coin entries. The service filters by the
 * requested currency and emits an XChange {@link Balance} only when the entry changed since the
 * last push (bounded LRU dedupe that survives reconnects, so a replayed snapshot does not re-emit
 * stale balances).
 *
 * @since 5.1.0
 */
public class BitgetUtaV3StreamingAccountService implements StreamingAccountService {

  private static final int DEDUPE_CAPACITY = 1000;

  private final BitgetUtaV3PrivateStreamingService service;

  public BitgetUtaV3StreamingAccountService(BitgetUtaV3PrivateStreamingService service) {
    this.service = service;
  }

  @Override
  public Observable<Balance> getBalanceChanges(Currency currency, Object... args) {
    BitgetUtaV3Channel channel =
        BitgetUtaV3Channel.builder().instType(BitgetUtaV3InstType.UTA).topic("account").build();
    String expectedCoin = currency.getCurrencyCode();
    Map<String, Balance> dedupe = boundedLru();
    return service
        .subscribeChannel(null, channel)
        .flatMap(
            notification ->
                Observable.fromIterable(notification.getPayloadItems())
                    .map(
                        item ->
                            Config.getInstance()
                                .getObjectMapper()
                                .treeToValue(item, BitgetUtaV3AccountData.class))
                    .flatMapIterable(BitgetUtaV3AccountData::getCoins))
        .filter(dto -> expectedCoin.equals(dto.getCoin()))
        .flatMap(
            dto -> {
              Balance balance = BitgetUtaV3StreamingAdapters.toBalance(dto, currency);
              Balance previous = dedupe.put(expectedCoin, balance);
              if (balance.equals(previous)) {
                return Observable.empty();
              }
              return Observable.just(balance);
            });
  }

  private static Map<String, Balance> boundedLru() {
    return new LinkedHashMap<String, Balance>(16, 0.75f, true) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<String, Balance> eldest) {
        return size() > DEDUPE_CAPACITY;
      }
    };
  }
}
