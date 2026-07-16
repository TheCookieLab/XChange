package info.bitrich.xchangestream.coinbasederivatives;

import info.bitrich.xchangestream.core.StreamingAccountService;
import io.reactivex.rxjava3.core.Observable;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.Balance;

/** Private portfolio and collateral subscriptions for Coinbase derivatives accounts. */
public final class CoinbaseDerivativesStreamingAccountService implements StreamingAccountService {

  private final CoinbaseDerivativesStreamingService streamingService;

  public CoinbaseDerivativesStreamingAccountService(
      CoinbaseDerivativesStreamingService streamingService) {
    this.streamingService = streamingService;
  }

  @Override
  public Observable<Balance> getBalanceChanges(Currency currency, Object... args) {
    return streamingService
        .subscribePrivateChannel("user.portfolio." + currency.getCurrencyCode())
        .map(CoinbaseDerivativesStreamingAdapters::data)
        .map(CoinbaseDerivativesStreamingAdapters::toBalance);
  }
}
