package org.knowm.xchange.coinbase.v3.dto.accounts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

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

    assertNotNull(response, "Response should not be null");
    assertNotNull(response.getAccounts(), "Accounts should not be null");
    assertEquals(2, response.getAccounts().size(), "Should have 2 accounts");
    assertEquals(Integer.valueOf(2), response.getSize(), "Size should match");
    assertFalse(response.getHasNext(), "Has next should be false");
    
    // Verify first account (BTC)
    CoinbaseAccount btcAccount = response.getAccounts().get(0);
    assertEquals("acc-123-456-789", btcAccount.getUuid(), "BTC account UUID should match");
    assertEquals("BTC Wallet", btcAccount.getName(), "BTC account name should match");
    assertEquals("BTC", btcAccount.getCurrency(), "BTC account currency should match");
    
    CoinbaseAmount btcBalance = btcAccount.getBalance();
    assertNotNull(btcBalance, "BTC balance should not be null");
    assertEquals(new BigDecimal("1.5"), btcBalance.getValue(), "BTC balance value should match");
    assertEquals("BTC", btcBalance.getCurrency(), "BTC balance currency should match");
    
    // Verify second account (USD)
    CoinbaseAccount usdAccount = response.getAccounts().get(1);
    assertEquals("acc-987-654-321", usdAccount.getUuid(), "USD account UUID should match");
    assertEquals("USD Wallet", usdAccount.getName(), "USD account name should match");
    assertEquals("USD", usdAccount.getCurrency(), "USD account currency should match");
    
    CoinbaseAmount usdBalance = usdAccount.getBalance();
    assertNotNull(usdBalance, "USD balance should not be null");
    assertEquals(new BigDecimal("10000.50"), usdBalance.getValue(), "USD balance value should match");
    assertEquals("USD", usdBalance.getCurrency(), "USD balance currency should match");
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

    assertNotNull(account, "Account should not be null");
    assertEquals("test-uuid", account.getUuid(), "UUID should match");
    assertEquals("Test Account", account.getName(), "Name should match");
    assertEquals("BTC", account.getCurrency(), "Currency should match");
  }

  @Test
  public void testDeserializeHoldKeepsItsCurrencyAndPrecision() throws Exception {
    CoinbaseAmount hold =
        accountWithHold("{\"value\": \"0.123456789012345678\", \"currency\": \"BTC\"}").getHold();

    assertEquals(new BigDecimal("0.123456789012345678"), hold.getValue(), "Hold value should keep its precision");
    assertEquals("BTC", hold.getCurrency(), "Hold currency should match");
  }

  @Test
  public void testDeserializeZeroHoldIsNotAMissingHold() throws Exception {
    CoinbaseAmount hold =
        accountWithHold("{\"value\": \"0\", \"currency\": \"BTC\"}").getHold();

    assertNotNull(hold, "A reported zero hold should be kept");
    assertEquals(0, hold.getValue().signum(), "Hold should be zero");
  }

  @Test
  public void testDeserializeAccountWithoutHoldLeavesItNull() throws Exception {
    assertNull(accountWithHold("null").getHold(), "An explicit null hold proves nothing");
    assertNull(mapper
        .readValue(
            "{\"uuid\": \"test-uuid\", \"currency\": \"BTC\", "
                + "\"available_balance\": {\"value\": \"1\", \"currency\": \"BTC\"}}",
            CoinbaseAccount.class)
        .getHold(), "An omitted hold proves nothing");
  }

  @Test
  public void testDeserializeMalformedHoldFails() {
    for (String malformed :
        new String[] {
          "{\"currency\": \"BTC\"}",
          "{\"value\": \"1\"}",
          "{\"value\": \"\", \"currency\": \"BTC\"}",
          "{\"value\": \"not-a-number\", \"currency\": \"BTC\"}",
          "\"3\""
        }) {
      assertThrows(JsonMappingException.class, () -> accountWithHold(malformed), malformed);
    }
  }

  private CoinbaseAccount accountWithHold(String hold) throws Exception {
    return mapper.readValue(
        "{\"uuid\": \"test-uuid\", \"currency\": \"BTC\", "
            + "\"available_balance\": {\"value\": \"1\", \"currency\": \"BTC\"}, "
            + "\"hold\": " + hold + "}",
        CoinbaseAccount.class);
  }

  @Test
  public void testDeserializeAmountWithLargeValue() throws Exception {
    String json = "{\n" +
        "  \"value\": \"123456789.123456789\",\n" +
        "  \"currency\": \"USD\"\n" +
        "}";

    CoinbaseAmount amount = mapper.readValue(json, CoinbaseAmount.class);

    assertNotNull(amount, "Amount should not be null");
    assertEquals(new BigDecimal("123456789.123456789"), amount.getValue(), "Value should match");
    assertEquals("USD", amount.getCurrency(), "Currency should match");
  }

  @Test
  public void testDeserializeAmountWithZeroValue() throws Exception {
    String json = "{\n" +
        "  \"value\": \"0\",\n" +
        "  \"currency\": \"BTC\"\n" +
        "}";

    CoinbaseAmount amount = mapper.readValue(json, CoinbaseAmount.class);

    assertNotNull(amount, "Amount should not be null");
    assertEquals(BigDecimal.ZERO.compareTo(amount.getValue()), 0, "Value should be zero");
    assertEquals("BTC", amount.getCurrency(), "Currency should match");
  }
}

