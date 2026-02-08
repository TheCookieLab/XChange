package org.knowm.xchange.coinbase.v3.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.coinbase.CoinbaseAdapters;
import org.knowm.xchange.coinbase.v3.dto.accounts.CoinbaseAccount;
import org.knowm.xchange.coinbase.v3.dto.accounts.CoinbaseAmount;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseFuturesBalanceSummaryResponse;
import org.knowm.xchange.coinbase.v3.dto.perpetuals.CoinbasePerpetualsBalancesResponse;
import org.knowm.xchange.coinbase.v3.dto.transactions.CoinbaseTransactionSummaryResponse;
import org.knowm.xchange.coinbase.v3.dto.transactions.CoinbaseFeeTier;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.Fee;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;
import org.knowm.xchange.service.trade.params.WithdrawFundsParams;

/**
 * Account service implementation for Coinbase Advanced Trade (v3) API.
 * <p>
 * This service provides access to account-related operations including account information,
 * balances, withdrawals, and trading fees. It extends {@link CoinbaseAccountServiceRaw} to provide
 * high-level XChange DTOs mapped from Coinbase-specific responses.
 * </p>
 * <p>
 * All methods in this service map Coinbase API responses to standard XChange account objects
 * such as {@link AccountInfo}, {@link Wallet}, {@link Balance}, and {@link Fee}.
 * </p>
 */
public final class CoinbaseAccountService extends CoinbaseAccountServiceRaw implements
    AccountService {

  /**
   * Constructs a new account service using the exchange's default configuration.
   *
   * @param exchange The exchange instance containing API credentials and configuration.
   */
  public CoinbaseAccountService(Exchange exchange) {

    super(exchange);
  }

  /**
   * Retrieves account information including all wallets and balances for the authenticated user.
   * <p>
   * This method fetches all accounts from Coinbase and maps them to XChange {@link Wallet} objects,
   * each containing balance information for a specific currency. The accounts are retrieved with
   * automatic pagination to ensure all accounts are included.
   * </p>
   *
   * @return An {@link AccountInfo} object containing a list of wallets, each representing an account
   *         with its balance for a specific currency.
   * @throws IOException If there is an error communicating with the Coinbase API.
   */
  @Override
  public AccountInfo getAccountInfo() throws IOException {
    List<Wallet> wallets = new ArrayList<>();

    List<CoinbaseAccount> coinbaseAccounts = getCoinbaseAccounts();
    for (CoinbaseAccount coinbaseAccount : coinbaseAccounts) {
      CoinbaseAmount balance = coinbaseAccount.getBalance();
      Wallet wallet = Wallet.Builder.from(Arrays.asList(
              new Balance(Currency.getInstance(balance.getCurrency()), balance.getValue())))
          .id(coinbaseAccount.getUuid()).build();
      wallets.add(wallet);
    }

    return new AccountInfo(wallets);
  }

  /**
   * Withdraws funds from the account to an external address.
   * <p>
   * This method supports withdrawals using {@link DefaultWithdrawFundsParams}, which includes
   * the currency, amount, and destination address. Other parameter types are not currently supported.
   * </p>
   *
   * @param params Withdrawal parameters. Must be an instance of {@link DefaultWithdrawFundsParams}
   *               containing the currency, amount, and destination address.
   * @return The transaction ID or withdrawal identifier as a string.
   * @throws ExchangeException                  If the exchange returns an error response.
   * @throws NotAvailableFromExchangeException  If withdrawal is not available for this exchange.
   * @throws NotYetImplementedForExchangeException If the withdrawal operation is not yet implemented.
   * @throws IOException                        If there is an error communicating with the Coinbase API.
   * @throws IllegalStateException              If the provided params are not of a supported type.
   */
  @Override
  public String withdrawFunds(WithdrawFundsParams params)
      throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
    throw new NotAvailableFromExchangeException("withdrawFunds");
  }

  /**
   * Creates parameters for retrieving funding history (deposits and withdrawals).
   * <p>
   * This operation is not currently available for Coinbase Advanced Trade API.
   * </p>
   *
   * @return This method always throws {@link NotAvailableFromExchangeException}.
   * @throws NotAvailableFromExchangeException Always thrown, as funding history is not available.
   */
  @Override
  public TradeHistoryParams createFundingHistoryParams() {
    throw new NotAvailableFromExchangeException();
  }

  /**
   * Retrieves dynamic trading fees by instrument.
   * <p>
   * This method fetches the current fee tier from the transaction summary endpoint and returns
   * a map that provides the same maker and taker fee rates for all instruments. Coinbase Advanced
   * Trade uses a global fee structure, so all trading pairs share the same fee rates based on the
   * user's fee tier.
   * </p>
   * <p>
   * The returned map is an unmodifiable view that returns the same {@link Fee} object for any
   * instrument key. The fee rates are determined by the user's current fee tier, which is based
   * on their trading volume and other factors.
   * </p>
   *
   * @return An unmodifiable map that provides the same {@link Fee} (with maker and taker rates)
   *         for any instrument key. The map contains a single representative entry for
   *         {@link CurrencyPair#BTC_USD}.
   * @throws IOException If there is an error communicating with the Coinbase API.
   */
  @Override
  public Map<Instrument, Fee> getDynamicTradingFeesByInstrument(String... category)
      throws IOException {
    TransactionSummaryFilters filters = resolveTransactionSummaryFilters(category);
    CoinbaseTransactionSummaryResponse response = getTransactionSummary(filters.productType(), null, filters.productVenue());
    CoinbaseFeeTier feeTier = response.getFeeTier();

    final Fee globalFee = new Fee(feeTier.getMakerFeeRate(), feeTier.getTakerFeeRate());

    // Return a Map that provides the same fee for any Instrument key without enumerating pairs
    return Collections.unmodifiableMap(new AbstractMap<Instrument, Fee>() {
      private final Instrument representativeKey = CurrencyPair.BTC_USD; // arbitrary representative

      @Override
      public Fee get(Object key) {
        return globalFee;
      }

      @Override
      public Set<Map.Entry<Instrument, Fee>> entrySet() {
        return Collections.singleton(new AbstractMap.SimpleImmutableEntry<>(representativeKey, globalFee));
      }

      @Override
      public int size() {
        return 1;
      }
    });
  }

  static TransactionSummaryFilters resolveTransactionSummaryFilters(String... category) {
    String productType = normalizeCategory(category, 0);
    String productVenue = normalizeCategory(category, 1);
    if ("FUTURES".equals(productType)) {
      productType = "FUTURE";
    }
    if ("PERPETUAL".equals(productType) || "PERPETUALS".equals(productType) || "INTX".equals(productType)) {
      productType = "FUTURE";
      if (productVenue == null) {
        productVenue = "INTX";
      }
    }
    return new TransactionSummaryFilters(productType, productVenue);
  }

  static final class TransactionSummaryFilters {

    private final String productType;
    private final String productVenue;

    private TransactionSummaryFilters(String productType, String productVenue) {
      this.productType = productType;
      this.productVenue = productVenue;
    }

    String productType() {
      return productType;
    }

    String productVenue() {
      return productVenue;
    }
  }

  private static String normalizeCategory(String[] category, int index) {
    if (category == null || category.length <= index) {
      return null;
    }
    String value = category[index];
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    if (normalized.isEmpty()) {
      return null;
    }
    return normalized.toUpperCase();
  }

  /**
   * Retrieves the futures balance summary and adapts it to an XChange futures wallet.
   *
   * @return Futures wallet with USD balance and futures feature.
   * @throws IOException If there is an error communicating with the Coinbase API.
   */
  public Wallet getFuturesWallet() throws IOException {
    if (!hasAuthentication()) {
      throw new NotAvailableFromExchangeException("Futures balances require authentication");
    }
    CoinbaseFuturesBalanceSummaryResponse response = getFuturesBalanceSummary();
    return CoinbaseAdapters.adaptFuturesWallet(response);
  }

  /**
   * Retrieves perpetuals portfolio balances and adapts them to an XChange futures wallet.
   *
   * @param portfolioUuid Portfolio UUID.
   * @return Perpetuals wallet with collateral balance and futures feature.
   * @throws IOException If there is an error communicating with the Coinbase API.
   */
  public Wallet getPerpetualsWallet(String portfolioUuid) throws IOException {
    if (!hasAuthentication()) {
      throw new NotAvailableFromExchangeException("Perpetuals balances require authentication");
    }
    CoinbasePerpetualsBalancesResponse response = getPerpetualsPortfolioBalances(portfolioUuid);
    return CoinbaseAdapters.adaptPerpetualsWallet(response);
  }

}
