package org.knowm.xchange.dase.service;

import java.io.IOException;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.dase.dto.account.ApiGetAccountTxnsOutput;
import org.knowm.xchange.dase.dto.account.DaseBalancesResponse;
import org.knowm.xchange.dase.dto.account.DaseSingleBalance;
import org.knowm.xchange.dase.dto.user.DaseUserProfile;

/** Raw access to authenticated DASE endpoints. */
public class DaseAccountServiceRaw extends DaseBaseService {

  public DaseAccountServiceRaw(Exchange exchange) {
    super(exchange);
  }

  public DaseUserProfile getUserProfile() throws IOException {
    ensureCredentialsPresent();
    return daseAuth.getUserProfile(apiKey, signatureCreator, timestampFactory);
  }

  public ApiGetAccountTxnsOutput getAccountTransactions(Integer limit, String before)
      throws IOException {
    ensureCredentialsPresent();
    return daseAuth.getAccountTransactions(apiKey, signatureCreator, timestampFactory, limit, before);
  }

  public DaseBalancesResponse getDaseBalances() throws IOException {
    ensureCredentialsPresent();
    return daseAuth.getBalances(apiKey, signatureCreator, timestampFactory);
  }

  public DaseSingleBalance getDaseBalance(String currency) throws IOException {
    ensureCredentialsPresent();
    return daseAuth.getBalance(currency, apiKey, signatureCreator, timestampFactory);
  }
}


