package org.knowm.xchange.kucoin.uta;

import static org.knowm.xchange.kucoin.uta.UtaResilience.UTA_PRIVATE_REST_ENDPOINT_RATE_LIMITER;
import static org.knowm.xchange.kucoin.uta.service.UtaConstants.KEY_VERSION;
import static org.knowm.xchange.kucoin.uta.service.UtaExceptionClassifier.callOrThrow;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.kucoin.KucoinExchange;
import org.knowm.xchange.kucoin.uta.dto.UtaAccountBalance;
import org.knowm.xchange.kucoin.uta.dto.UtaAccountModeResponse;
import org.knowm.xchange.kucoin.uta.dto.UtaAccountOverview;
import org.knowm.xchange.kucoin.uta.dto.UtaFeeRates;
import org.knowm.xchange.kucoin.uta.dto.UtaLedgerEntry;
import org.knowm.xchange.kucoin.uta.dto.UtaModifyLeverageRequest;
import org.knowm.xchange.kucoin.uta.dto.UtaModifyLeverageResult;
import org.knowm.xchange.kucoin.uta.dto.UtaTransferQuota;
import org.knowm.xchange.kucoin.uta.dto.UtaTransferRequest;
import org.knowm.xchange.kucoin.uta.dto.UtaTransferResult;
import org.knowm.xchange.kucoin.uta.service.UtaAccountAPI;
import org.knowm.xchange.kucoin.uta.service.UtaApiException;
import org.knowm.xchange.kucoin.uta.service.UtaApiException.RetryClassification;
import org.knowm.xchange.service.account.AccountService;

/**
 * UTA unified account service: account-mode probe, unified balances with liabilities, transfer
 * quotas, flex transfers, and fee rates.
 */
public class UtaAccountService extends UtaBaseService implements AccountService {

  private final UtaAccountAPI accountApi;

  public UtaAccountService(KucoinExchange exchange, ResilienceRegistries registries) {
    super(exchange, registries);
    this.accountApi = service(UtaAccountAPI.class);
  }

  // ---- raw API ---------------------------------------------------------------

  /**
   * Probes the credential's account mode. UTA trading requires {@code selfAccountMode ==
   * "UNIFIED"}; this probe must run before any trading operation and never silently switches modes.
   */
  public UtaAccountModeResponse getUtaAccountMode() throws IOException {
    checkAuthenticated();
    return callOrThrow(
        () ->
            decorateApiCall(
                    () ->
                        accountApi.getAccountMode(
                            apiKey, digest, nonceFactory, encryptedPassphrase, KEY_VERSION))
                .withRetry(retry("utaAccountMode"))
                .withRateLimiter(rateLimiter(UTA_PRIVATE_REST_ENDPOINT_RATE_LIMITER))
                .call(),
        UtaDomains.ACCOUNT,
        "GET /api/ua/v1/account/mode");
  }

  /** Verifies the credentials belong to a UNIFIED account; fails actionably otherwise. */
  public void verifyUnifiedMode() throws IOException {
    UtaAccountModeResponse mode = getUtaAccountMode();
    if (mode == null || !"UNIFIED".equalsIgnoreCase(mode.getSelfAccountMode())) {
      throw new UtaApiException(
          "KuCoin credentials are not in UTA (UNIFIED) account mode: "
              + (mode == null ? "no account-mode data" : "selfAccountMode=" + mode.getSelfAccountMode()),
          null,
          org.knowm.xchange.kucoin.KucoinApiMode.UTA,
          UtaDomains.ACCOUNT,
          "GET /api/ua/v1/account/mode",
          null,
          null,
          null,
          RetryClassification.NON_RETRYABLE);
    }
  }

  /** UTA account-level funds summary (equity, liability, margins, risk ratio). */
  public UtaAccountOverview getUtaAccountOverview() throws IOException {
    checkAuthenticated();
    return callOrThrow(
        () ->
            decorateApiCall(
                    () ->
                        accountApi.getAccountOverview(
                            apiKey, digest, nonceFactory, encryptedPassphrase, KEY_VERSION))
                .withRetry(retry("utaAccountOverview"))
                .withRateLimiter(rateLimiter(UTA_PRIVATE_REST_ENDPOINT_RATE_LIMITER))
                .call(),
        UtaDomains.ACCOUNT,
        "GET /api/ua/v1/unified/account/overview");
  }

  /** Currency-level UTA balances including equity, liability and collateral status. */
  public UtaAccountBalance getUtaAccountBalance() throws IOException {
    checkAuthenticated();
    return callOrThrow(
        () ->
            decorateApiCall(
                    () ->
                        accountApi.getAccountBalance(
                            apiKey, digest, nonceFactory, encryptedPassphrase, KEY_VERSION))
                .withRetry(retry("utaAccountBalance"))
                .withRateLimiter(rateLimiter(UTA_PRIVATE_REST_ENDPOINT_RATE_LIMITER))
                .call(),
        UtaDomains.ACCOUNT,
        "GET /api/ua/v1/unified/account/balance");
  }

  public UtaTransferQuota getUtaTransferQuota(String currency, String accountType, String symbol)
      throws IOException {
    checkAuthenticated();
    return callOrThrow(
        () ->
            decorateApiCall(
                    () ->
                        accountApi.getTransferQuota(
                            apiKey,
                            digest,
                            nonceFactory,
                            encryptedPassphrase,
                            KEY_VERSION,
                            currency,
                            accountType,
                            symbol))
                .withRetry(retry("utaTransferQuota"))
                .withRateLimiter(rateLimiter(UTA_PRIVATE_REST_ENDPOINT_RATE_LIMITER))
                .call(),
        UtaDomains.ASSET,
        "GET /api/ua/v1/account/transfer-quota");
  }

  public UtaTransferResult transfer(UtaTransferRequest request) throws IOException {
    checkAuthenticated();
    return callOrThrow(
        () ->
            decorateApiCall(
                    () ->
                        accountApi.transfer(
                            apiKey,
                            digest,
                            nonceFactory,
                            encryptedPassphrase,
                            KEY_VERSION,
                            request))
                .withRetry(retry("utaTransfer"))
                .withRateLimiter(rateLimiter(UTA_PRIVATE_REST_ENDPOINT_RATE_LIMITER))
                .call(),
        UtaDomains.ASSET,
        "POST /api/ua/v1/account/transfer");
  }

  public UtaFeeRates getUtaFeeRate(String tradeType, String symbol) throws IOException {
    checkAuthenticated();
    return callOrThrow(
        () ->
            decorateApiCall(
                    () ->
                        accountApi.getFeeRate(
                            apiKey,
                            digest,
                            nonceFactory,
                            encryptedPassphrase,
                            KEY_VERSION,
                            tradeType,
                            symbol))
                .withRetry(retry("utaFeeRate"))
                .withRateLimiter(rateLimiter(UTA_PRIVATE_REST_ENDPOINT_RATE_LIMITER))
                .call(),
        UtaDomains.FEE,
        "GET /api/ua/v1/user/fee-rate");
  }

  /** Modifies the futures leverage of a symbol. */
  public UtaModifyLeverageResult modifyLeverage(UtaModifyLeverageRequest request)
      throws IOException {
    checkAuthenticated();
    return callOrThrow(
        () ->
            decorateApiCall(
                    () ->
                        accountApi.modifyLeverage(
                            apiKey,
                            digest,
                            nonceFactory,
                            encryptedPassphrase,
                            KEY_VERSION,
                            request))
                .withRetry(retry("utaModifyLeverage"))
                .withRateLimiter(rateLimiter(UTA_PRIVATE_REST_ENDPOINT_RATE_LIMITER))
                .call(),
        UtaDomains.POSITION,
        "POST /api/ua/v1/unified/account/modify-leverage");
  }

  /** Account ledger records (transfers and balance changes) with cursor pagination. */
  public java.util.List<UtaLedgerEntry> getLedger(
      String accountType,
      String currency,
      String direction,
      String businessType,
      Long lastId,
      Long startAt,
      Long endAt,
      Integer pageSize)
      throws IOException {
    checkAuthenticated();
    return callOrThrow(
        () ->
            decorateApiCall(
                    () ->
                        accountApi.getLedger(
                            apiKey,
                            digest,
                            nonceFactory,
                            encryptedPassphrase,
                            KEY_VERSION,
                            accountType,
                            currency,
                            direction,
                            businessType,
                            lastId,
                            startAt,
                            endAt,
                            pageSize))
                .withRetry(retry("utaLedger"))
                .withRateLimiter(rateLimiter(UTA_PRIVATE_REST_ENDPOINT_RATE_LIMITER))
                .call(),
        UtaDomains.ACCOUNT,
        "GET /api/ua/v1/account/ledger");
  }

  // ---- high-level XChange API ------------------------------------------------

  @Override
  public AccountInfo getAccountInfo() throws IOException {
    UtaAccountBalance balance = getUtaAccountBalance();
    List<Balance> balances =
        balance == null || balance.getAccounts() == null
            ? java.util.Collections.emptyList()
            : balance.getAccounts().stream()
                .flatMap(g -> g.getCurrencies().stream())
                .map(UtaAdapters::adaptBalance)
                .collect(Collectors.toList());
    return new AccountInfo(Wallet.Builder.from(balances).build());
  }
}
