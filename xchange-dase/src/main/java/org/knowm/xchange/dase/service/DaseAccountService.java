package org.knowm.xchange.dase.service;

import java.io.IOException;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.dase.DaseAdapters;
import org.knowm.xchange.dase.dto.account.DaseBalancesResponse;
import org.knowm.xchange.dase.dto.user.DaseUserProfile;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.service.account.AccountService;

public class DaseAccountService extends DaseAccountServiceRaw implements AccountService {

  public DaseAccountService(Exchange exchange) {
    super(exchange);
  }

  @Override
  public AccountInfo getAccountInfo() throws IOException {
    DaseUserProfile profile = getUserProfile();
    DaseBalancesResponse balances = getDaseBalances();
    return DaseAdapters.adaptAccountInfo(
        profile == null ? null : profile.getPortfolioId(), balances);
  }
}


