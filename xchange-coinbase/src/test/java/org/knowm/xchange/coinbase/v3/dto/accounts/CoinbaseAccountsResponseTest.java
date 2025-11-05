package org.knowm.xchange.coinbase.v3.dto.accounts;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.Test;

/**
 * Unit tests for CoinbaseAccountsResponse.
 * Tests null-safe list handling to prevent NPEs when API returns null for accounts list.
 */
public class CoinbaseAccountsResponseTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  public void testWithNullAccountsList() throws Exception {
    String json = "{\"has_next\": false, \"cursor\": null, \"size\": 0}";
    CoinbaseAccountsResponse response = mapper.readValue(json, CoinbaseAccountsResponse.class);

    assertNotNull("Response should not be null", response);
    List<CoinbaseAccount> accounts = response.getAccounts();
    assertNotNull("Accounts list should not be null", accounts);
    assertTrue("Accounts list should be empty", accounts.isEmpty());
  }

  @Test
  public void testWithEmptyAccountsList() throws Exception {
    String json = "{\"accounts\": [], \"has_next\": false, \"cursor\": null, \"size\": 0}";
    CoinbaseAccountsResponse response = mapper.readValue(json, CoinbaseAccountsResponse.class);

    assertNotNull("Response should not be null", response);
    List<CoinbaseAccount> accounts = response.getAccounts();
    assertNotNull("Accounts list should not be null", accounts);
    assertTrue("Accounts list should be empty", accounts.isEmpty());
  }

  @Test(expected = UnsupportedOperationException.class)
  public void testReturnsImmutableList() throws Exception {
    String json = "{\"accounts\": [], \"has_next\": false, \"cursor\": null, \"size\": 0}";
    CoinbaseAccountsResponse response = mapper.readValue(json, CoinbaseAccountsResponse.class);

    // Should throw UnsupportedOperationException
    response.getAccounts().add(null);
  }
}

