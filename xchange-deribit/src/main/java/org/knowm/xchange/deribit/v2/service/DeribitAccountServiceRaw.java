package org.knowm.xchange.deribit.v2.service;

import java.io.IOException;
import java.util.List;
import org.knowm.xchange.deribit.v2.DeribitExchange;
import org.knowm.xchange.deribit.v2.dto.account.DeribitAccountSummary;
import org.knowm.xchange.deribit.v2.dto.account.DeribitDeposit;

public class DeribitAccountServiceRaw extends DeribitBaseService {

  public DeribitAccountServiceRaw(DeribitExchange exchange) {
    super(exchange);
  }

  public DeribitAccountSummary getAccountSummary(String currency, Boolean extended) throws IOException {
    return deribitAuthenticated.getAccountSummary(currency, extended, deribitDigest).getResult();
  }

  public List<DeribitAccountSummary> getAccountSummaries(Boolean extended) throws IOException {
    return deribitAuthenticated.getAccountSummaries(extended, deribitDigest).getResult().getAccountSummaries();
  }

  public List<DeribitDeposit> getDeposits(String currency, Integer count, Long offset) throws IOException {
    return deribitAuthenticated.getDeposits(currency, count, offset, deribitDigest).getResult().getData();
  }

}
