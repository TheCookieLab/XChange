package org.knowm.xchange.coinbase.v2.dto.accounts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.math.BigDecimal;
import org.junit.Test;

public class CoinbaseV2AccountsJsonTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  public void deserializeAccountsResponse() throws Exception {
    InputStream is = CoinbaseV2AccountsJsonTest.class.getResourceAsStream(
        "/org/knowm/xchange/coinbase/dto/account/example-accounts-data.json");
    assertNotNull("Fixture should be present", is);

    CoinbaseV2AccountsResponse response = mapper.readValue(is, CoinbaseV2AccountsResponse.class);
    assertNotNull("Response should not be null", response);
    assertNotNull("Pagination should not be null", response.getPagination());
    assertNotNull("Data list should not be null", response.getData());
    assertFalse("Data should not be empty", response.getData().isEmpty());

    CoinbaseV2Account first = response.getData().get(0);
    assertNotNull(first.getId());
    assertNotNull(first.getCurrency());
    assertEquals("EUR", first.getCurrency().getCode());
    assertNotNull(first.getBalance());
    assertEquals(new BigDecimal("322.20"), first.getBalance().getAmount());
    assertEquals("EUR", first.getBalance().getCurrency());
  }
}

