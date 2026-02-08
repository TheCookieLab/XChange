package org.knowm.xchange.coinbase.v3.service;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.coinbase.v2.CoinbaseV2Authenticated;
import org.knowm.xchange.coinbase.v2.dto.accounts.CoinbaseV2AccountsResponse;
import org.knowm.xchange.coinbase.v2.dto.transactions.CoinbaseV2TransactionsResponse;
import org.knowm.xchange.coinbase.v3.CoinbaseAuthenticated;
import org.mockito.Mockito;
import si.mazi.rescu.ParamsDigest;

public class CoinbaseAccountServiceV2UnitTest {

  private CoinbaseAccountServiceRaw raw;
  private CoinbaseAuthenticated v3Api;
  private CoinbaseV2Authenticated v2Api;
  private ParamsDigest digest;
  private Exchange exchange;

  @Before
  public void setUp() {
    exchange = Mockito.mock(Exchange.class);
    v3Api = Mockito.mock(CoinbaseAuthenticated.class);
    v2Api = Mockito.mock(CoinbaseV2Authenticated.class);
    digest = Mockito.mock(ParamsDigest.class);
    raw = new CoinbaseAccountServiceRaw(exchange, v3Api, digest, v2Api);
  }

  @Test
  public void listV2AccountsPassesPaginationParams() throws IOException {
    CoinbaseV2AccountsResponse response = new CoinbaseV2AccountsResponse(null, Collections.emptyList());
    when(v2Api.listAccounts(eq(digest), eq(50), eq("start"), eq("end"), eq("desc")))
        .thenReturn(response);

    CoinbaseV2AccountsResponse got = raw.listV2Accounts(50, "start", "end", "desc");
    assertNotNull(got);

    verify(v2Api).listAccounts(eq(digest), eq(50), eq("start"), eq("end"), eq("desc"));
  }

  @Test
  public void listV2AccountTransactionsPassesAccountIdAndPaginationParams() throws IOException {
    CoinbaseV2TransactionsResponse response = new CoinbaseV2TransactionsResponse(null,
        Collections.emptyList());
    when(v2Api.listTransactions(eq(digest), eq("account-1"), eq(25), eq(null), eq(null), eq("desc")))
        .thenReturn(response);

    CoinbaseV2TransactionsResponse got = raw.listV2AccountTransactions("account-1", 25, null, null, "desc");
    assertNotNull(got);

    verify(v2Api).listTransactions(eq(digest), eq("account-1"), eq(25), eq(null), eq(null), eq("desc"));
  }
}

