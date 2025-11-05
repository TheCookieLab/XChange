package org.knowm.xchange.coinbase.v3.dto.accounts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import org.junit.Test;

/**
 * Unit tests for CoinbaseAccountsResponse JSON parsing.
 * Verifies correct deserialization of account data from Coinbase API responses.
 */
public class CoinbaseAccountsJsonTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  public void testDeserializeAccountsResponse() throws IOException {
    InputStream is = CoinbaseAccountsJsonTest.class.getResourceAsStream(
        "/org/knowm/xchange/coinbase/dto/v3/accounts/example-accounts-response.json");
    
    CoinbaseAccountsResponse response = mapper.readValue(is, CoinbaseAccountsResponse.class);

    assertNotNull("Response should not be null", response);
    assertNotNull("Accounts should not be null", response.getAccounts());
    assertEquals("Should have 2 accounts", 2, response.getAccounts().size());
    assertEquals("Size should match", Integer.valueOf(2), response.getSize());
    assertFalse("Has next should be false", response.getHasNext());
    
    // Verify first account (BTC)
    CoinbaseAccount btcAccount = response.getAccounts().get(0);
    assertEquals("BTC account UUID should match", "acc-123-456-789", btcAccount.getUuid());
    assertEquals("BTC account name should match", "BTC Wallet", btcAccount.getName());
    assertEquals("BTC account currency should match", "BTC", btcAccount.getCurrency());
    
    CoinbaseAmount btcBalance = btcAccount.getBalance();
    assertNotNull("BTC balance should not be null", btcBalance);
    assertEquals("BTC balance value should match", new BigDecimal("1.5"), btcBalance.getValue());
    assertEquals("BTC balance currency should match", "BTC", btcBalance.getCurrency());
    
    // Verify second account (USD)
    CoinbaseAccount usdAccount = response.getAccounts().get(1);
    assertEquals("USD account UUID should match", "acc-987-654-321", usdAccount.getUuid());
    assertEquals("USD account name should match", "USD Wallet", usdAccount.getName());
    assertEquals("USD account currency should match", "USD", usdAccount.getCurrency());
    
    CoinbaseAmount usdBalance = usdAccount.getBalance();
    assertNotNull("USD balance should not be null", usdBalance);
    assertEquals("USD balance value should match", new BigDecimal("10000.50"), usdBalance.getValue());
    assertEquals("USD balance currency should match", "USD", usdBalance.getCurrency());
  }

  @Test
  public void testDeserializeAccountWithMinimalFields() throws Exception {
    String json = "{\n" +
        "  \"uuid\": \"test-uuid\",\n" +
        "  \"name\": \"Test Account\",\n" +
        "  \"currency\": \"BTC\",\n" +
        "  \"available_balance\": {\n" +
        "    \"value\": \"0\",\n" +
        "    \"currency\": \"BTC\"\n" +
        "  }\n" +
        "}";

    CoinbaseAccount account = mapper.readValue(json, CoinbaseAccount.class);

    assertNotNull("Account should not be null", account);
    assertEquals("UUID should match", "test-uuid", account.getUuid());
    assertEquals("Name should match", "Test Account", account.getName());
    assertEquals("Currency should match", "BTC", account.getCurrency());
  }

  @Test
  public void testDeserializeAmountWithLargeValue() throws Exception {
    String json = "{\n" +
        "  \"value\": \"123456789.123456789\",\n" +
        "  \"currency\": \"USD\"\n" +
        "}";

    CoinbaseAmount amount = mapper.readValue(json, CoinbaseAmount.class);

    assertNotNull("Amount should not be null", amount);
    assertEquals("Value should match", new BigDecimal("123456789.123456789"), amount.getValue());
    assertEquals("Currency should match", "USD", amount.getCurrency());
  }

  @Test
  public void testDeserializeAmountWithZeroValue() throws Exception {
    String json = "{\n" +
        "  \"value\": \"0\",\n" +
        "  \"currency\": \"BTC\"\n" +
        "}";

    CoinbaseAmount amount = mapper.readValue(json, CoinbaseAmount.class);

    assertNotNull("Amount should not be null", amount);
    assertEquals("Value should be zero", BigDecimal.ZERO.compareTo(amount.getValue()), 0);
    assertEquals("Currency should match", "BTC", amount.getCurrency());
  }
}

