package org.knowm.xchange.coinbasederivatives.service;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.knowm.xchange.coinbasederivatives.CoinbaseDerivativesAdapters;
import org.knowm.xchange.coinbasederivatives.CoinbaseDerivativesExchange;
import org.knowm.xchange.coinbasederivatives.dto.account.CoinbaseDerivativesAccountSummary;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.account.Wallet.WalletFeature;
import org.knowm.xchange.service.account.AccountService;

/** Generic XChange account facade for the default collateral currency. */
public class CoinbaseDerivativesAccountService extends CoinbaseDerivativesAccountServiceRaw
    implements AccountService {
  public static final String DEFAULT_COLLATERAL_CURRENCY = "USDC";

  public CoinbaseDerivativesAccountService(CoinbaseDerivativesExchange exchange) {
    super(exchange);
  }

  @Override
  public AccountInfo getAccountInfo() throws IOException {
    CoinbaseDerivativesAccountSummary account =
        getAccountSummary(DEFAULT_COLLATERAL_CURRENCY, true);
    Wallet wallet =
        new Wallet.Builder()
            .id(account.id() == null ? account.currency() : account.id())
            .features(Set.of(WalletFeature.TRADING, WalletFeature.FUTURES_TRADING))
            .balances(List.of(CoinbaseDerivativesAdapters.adaptBalance(account)))
            .build();
    return new AccountInfo(
        null,
        null,
        List.of(wallet),
        getPositions(null, null).stream().map(CoinbaseDerivativesAdapters::adaptPosition).toList(),
        null);
  }
}
