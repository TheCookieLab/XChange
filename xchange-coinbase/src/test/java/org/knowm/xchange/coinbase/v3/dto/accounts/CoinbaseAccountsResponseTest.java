package org.knowm.xchange.coinbase.v3.dto.accounts;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

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

    assertNotNull(response, "Response should not be null");
    List<CoinbaseAccount> accounts = response.getAccounts();
    assertNotNull(accounts, "Accounts list should not be null");
    assertTrue(accounts.isEmpty(), "Accounts list should be empty");
  }

  @Test
  public void testWithEmptyAccountsList() throws Exception {
    String json = "{\"accounts\": [], \"has_next\": false, \"cursor\": null, \"size\": 0}";
    CoinbaseAccountsResponse response = mapper.readValue(json, CoinbaseAccountsResponse.class);

    assertNotNull(response, "Response should not be null");
    List<CoinbaseAccount> accounts = response.getAccounts();
    assertNotNull(accounts, "Accounts list should not be null");
    assertTrue(accounts.isEmpty(), "Accounts list should be empty");
  }

  @Test
  public void testReturnsImmutableList() throws Exception {
    String json = "{\"accounts\": [], \"has_next\": false, \"cursor\": null, \"size\": 0}";
    CoinbaseAccountsResponse response = mapper.readValue(json, CoinbaseAccountsResponse.class);

    assertThrows(UnsupportedOperationException.class, () -> response.getAccounts().add(null));
  }
}

