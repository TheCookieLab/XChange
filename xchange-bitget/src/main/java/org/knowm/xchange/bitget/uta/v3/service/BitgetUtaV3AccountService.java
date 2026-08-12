package org.knowm.xchange.bitget.uta.v3.service;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.knowm.xchange.bitget.BitgetExchange;
import org.knowm.xchange.bitget.uta.v3.BitgetUtaV3ErrorAdapter;
import org.knowm.xchange.bitget.uta.v3.account.BitgetUtaV3Asset;
import org.knowm.xchange.bitget.uta.v3.account.BitgetUtaV3TransferRequest;
import org.knowm.xchange.bitget.uta.v3.account.BitgetUtaV3TransferResult;
import org.knowm.xchange.bitget.uta.v3.common.BitgetUtaV3Exception;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.account.Wallet.WalletFeature;
import org.knowm.xchange.service.account.AccountService;

/**
 * UTA v3 account service.
 *
 * <p>The unified UTA account is exposed as a single {@link Wallet} (with {@link
 * WalletFeature#TRADING}, {@link WalletFeature#MARGIN_TRADING} and {@link
 * WalletFeature#FUTURES_TRADING}) whose balances carry the provider's unified semantics: {@code
 * available} excludes locked margin, {@code total} is the equity (balance + frozen margin +
 * unrealized PnL). Transfers between account types are exposed as-is: they are asynchronous and
 * must be confirmed via balances.
 */
public class BitgetUtaV3AccountService extends BitgetUtaV3AccountServiceRaw
    implements AccountService {

  public BitgetUtaV3AccountService(BitgetExchange exchange) {
    super(exchange);
  }

  @Override
  public AccountInfo getAccountInfo() throws IOException {
    try {
      List<BitgetUtaV3Asset> assets = getAssets(null);
      Wallet wallet =
          Wallet.Builder.from(
                  assets.stream()
                      .map(BitgetUtaV3AccountService::toBalance)
                      .collect(Collectors.toList()))
              .features(
                  java.util.Set.of(
                      WalletFeature.TRADING,
                      WalletFeature.MARGIN_TRADING,
                      WalletFeature.FUTURES_TRADING))
              .build();
      return new AccountInfo(wallet);
    } catch (BitgetUtaV3Exception e) {
      throw BitgetUtaV3ErrorAdapter.adapt(e);
    }
  }

  /**
   * Initiates an asynchronous transfer between account types (e.g. {@code uta} to {@code spot}).
   * Confirmation must come from the receiving account's balances.
   */
  public BitgetUtaV3TransferResult transfer(BitgetUtaV3TransferRequest request) throws IOException {
    return super.transfer(request);
  }

  private static Balance toBalance(BitgetUtaV3Asset asset) {
    return new Balance.Builder()
        .currency(Currency.getInstance(asset.getCoin()))
        .total(asset.getEquity())
        .available(asset.getAvailable())
        // frozen = margin committed to futures positions plus spot-order locked funds; reporting
        // only getFrozen() understates what is committed to open orders when locked > 0.
        .frozen(asset.getFrozen().add(asset.getLocked()))
        .borrowed(asset.getDebts())
        .build();
  }
}
