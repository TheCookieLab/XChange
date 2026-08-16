package info.bitrich.xchangestream.cryptocom;

import info.bitrich.xchangestream.core.StreamingAccountService;
import io.reactivex.rxjava3.core.Observable;
import java.util.Collections;
import java.util.List;
import org.knowm.xchange.cryptocom.dto.account.CryptoComBalance;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.Balance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CryptoComStreamingAccountService implements StreamingAccountService {

  private static final Logger LOG = LoggerFactory.getLogger(CryptoComStreamingAccountService.class);
  private static final String BALANCE_CHANNEL = "user.balance";

  private final CryptoComPrivateStreamingService service;
  private final CryptoComStreamingEventDeduplicator deduplicator;

  public CryptoComStreamingAccountService(
      CryptoComPrivateStreamingService service,
      CryptoComStreamingEventDeduplicator deduplicator) {
    this.service = service;
    this.deduplicator = deduplicator;
  }

  @Override
  public Observable<Balance> getBalanceChanges(Currency currency, Object... args) {
    return service
        .subscribeChannel(BALANCE_CHANNEL)
        .flatMapIterable(message -> service.extractData(message, CryptoComBalance.class))
        .filter(balance -> !isDuplicateBalance(deduplicator, balance))
        .flatMapIterable(CryptoComStreamingAccountService::positionsOf)
        .filter(position -> currency.getCurrencyCode().equals(position.getInstrumentName()))
        .map(CryptoComStreamingAdapters::adaptBalance);
  }

  /**
   * {@code true} when the balance push is a replay of one already delivered. A {@code
   * user.balance} push is a full account state snapshot; on reconnect the server replays the
   * latest snapshot, which is only a meaningful change notification the first time it is seen.
   * Package-private for deterministic unit tests.
   */
  static boolean isDuplicateBalance(
      CryptoComStreamingEventDeduplicator deduplicator, CryptoComBalance balance) {
    String key = BALANCE_CHANNEL + "." + balance;
    boolean duplicate = deduplicator.isDuplicate(key);
    if (duplicate) {
      LOG.debug("Dropping replayed balance snapshot on reconnect");
    }
    return duplicate;
  }

  private static List<CryptoComBalance.PositionBalance> positionsOf(CryptoComBalance balance) {
    return balance.getPositionBalances() == null
        ? Collections.emptyList()
        : balance.getPositionBalances();
  }
}
