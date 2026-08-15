package info.bitrich.xchangestream.mexc;

import info.bitrich.xchangestream.core.StreamingAccountService;
import io.reactivex.rxjava3.core.Observable;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.Balance;

/**
 * Account stream for MEXC Spot v3 ({@code spot@private.account.v3.api.pb}).
 *
 * <p>The channel is global: it pushes balance events for every currency on the account. Events are
 * filtered by {@link #getBalanceChanges(Currency, Object...)}'s currency argument; pass {@code
 * null} to receive every currency. Requires a connected private stream (listen key), see {@link
 * MexcV3StreamingExchange}.
 */
public class MexcV3StreamingAccountService implements StreamingAccountService {

  private static final String CHANNEL_ACCOUNT = "spot@private.account.v3.api.pb";

  private final MexcV3StreamingService streamingService;

  public MexcV3StreamingAccountService(MexcV3StreamingService streamingService) {
    this.streamingService = streamingService;
  }

  @Override
  public Observable<Balance> getBalanceChanges(Currency currency, Object... args) {
    return streamingService
        .subscribeChannel(CHANNEL_ACCOUNT)
        .map(MexcV3StreamingAdapters::adaptAccountPush)
        .filter(balance -> currency == null || currency.equals(balance.getCurrency()));
  }
}
