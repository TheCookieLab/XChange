package org.knowm.xchange.deribit.v2.dto.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class DeribitPlatformStatusTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void deserializesBooleanLockedStatus() throws Exception {
    DeribitPlatformStatus status =
        mapper.readValue("{\"locked\":false}", DeribitPlatformStatus.class);

    assertThat(status.getLocked()).isFalse();
  }

  @Test
  void deserializesStringLockedStatus() throws Exception {
    DeribitPlatformStatus status =
        mapper.readValue("{\"locked\":\"false\"}", DeribitPlatformStatus.class);

    assertThat(status.getLocked()).isFalse();
  }

  @Test
  void deserializesPartialLockedStatusAsOnline() throws Exception {
    DeribitPlatformStatus status =
        mapper.readValue(
            "{\"locked\":\"partial\",\"locked_indices\":[\"btc_usdc\"]}",
            DeribitPlatformStatus.class);

    assertThat(status.getLocked()).isFalse();
    assertThat(status.getLockedCurrencies()).containsExactly("btc_usdc");
  }
}
