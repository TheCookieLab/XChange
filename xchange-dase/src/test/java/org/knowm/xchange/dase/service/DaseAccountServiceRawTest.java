package org.knowm.xchange.dase.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.dase.dto.user.DaseUserProfile;

public class DaseAccountServiceRawTest {

  @Test
  public void deserialize_user_profile_stub() throws Exception {
    String json = "{\n  \"portfolio_id\": \"cbd1e8f4-8b94-4e90-a2b0-20d3a2a2b11f\"\n}";
    ObjectMapper mapper = new ObjectMapper();
    DaseUserProfile dto = mapper.readValue(json, DaseUserProfile.class);
    assertThat(dto.getPortfolioId()).isEqualTo("cbd1e8f4-8b94-4e90-a2b0-20d3a2a2b11f");
  }
}


