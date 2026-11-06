package org.knowm.xchange.coinbase.v2.dto.accounts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

public class CoinbaseV2AccountsJsonTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  public void deserializeAccountsResponse() throws Exception {
    InputStream is = CoinbaseV2AccountsJsonTest.class.getResourceAsStream(
        "/org/knowm/xchange/coinbase/dto/account/example-accounts-data.json");
    assertNotNull(is, "Fixture should be present");

    CoinbaseV2AccountsResponse response = mapper.readValue(is, CoinbaseV2AccountsResponse.class);
    assertNotNull(response, "Response should not be null");
    assertNotNull(response.getPagination(), "Pagination should not be null");
    assertNotNull(response.getData(), "Data list should not be null");
    assertFalse(response.getData().isEmpty(), "Data should not be empty");

    CoinbaseV2Account first = response.getData().get(0);
    assertNotNull(first.getId());
    assertNotNull(first.getCurrency());
    assertEquals("EUR", first.getCurrency().getCode());
    assertNotNull(first.getBalance());
    assertEquals(new BigDecimal("322.20"), first.getBalance().getAmount());
    assertEquals("EUR", first.getBalance().getCurrency());
  }
}

