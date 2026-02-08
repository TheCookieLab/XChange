package org.knowm.xchange.coinbase.v2.dto.transactions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.math.BigDecimal;
import org.junit.Test;

public class CoinbaseV2TransactionsJsonTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  public void deserializeTransactionsResponse() throws Exception {
    InputStream is = CoinbaseV2TransactionsJsonTest.class.getResourceAsStream(
        "/org/knowm/xchange/coinbase/dto/v2/transactions/example-transactions-response.json");
    assertNotNull("Fixture should be present", is);

    CoinbaseV2TransactionsResponse response = mapper.readValue(is, CoinbaseV2TransactionsResponse.class);
    assertNotNull("Response should not be null", response);
    assertNotNull("Pagination should not be null", response.getPagination());
    assertNotNull("Data list should not be null", response.getData());
    assertEquals(2, response.getData().size());

    CoinbaseV2Transaction deposit = response.getData().get(0);
    assertEquals("tx-1", deposit.getId());
    assertEquals("fiat_deposit", deposit.getType());
    assertEquals("completed", deposit.getStatus());
    assertNotNull(deposit.getAmount());
    assertEquals(new BigDecimal("10.00"), deposit.getAmount().getAmount());
    assertEquals("USD", deposit.getAmount().getCurrency());
    assertNotNull(deposit.getDetails());
    assertEquals("Deposit", deposit.getDetails().getTitle());

    CoinbaseV2Transaction withdrawal = response.getData().get(1);
    assertEquals(new BigDecimal("-5.00"), withdrawal.getAmount().getAmount());
  }
}

