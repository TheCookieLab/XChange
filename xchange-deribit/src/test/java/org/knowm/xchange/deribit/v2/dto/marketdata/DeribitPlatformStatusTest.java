package org.knowm.xchange.deribit.v2.dto.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.fasterxml.jackson.databind.JsonMappingException;
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
  void deserializesBooleanTrueAsLocked() throws Exception {
    DeribitPlatformStatus status =
        mapper.readValue("{\"locked\":true}", DeribitPlatformStatus.class);

    assertThat(status.getLocked()).isTrue();
  }

  @Test
  void deserializesStringLockedStatus() throws Exception {
    DeribitPlatformStatus status =
        mapper.readValue("{\"locked\":\"false\"}", DeribitPlatformStatus.class);

    assertThat(status.getLocked()).isFalse();
  }

  @Test
  void deserializesStringTrueAsLocked() throws Exception {
    DeribitPlatformStatus status =
        mapper.readValue("{\"locked\":\"true\"}", DeribitPlatformStatus.class);

    assertThat(status.getLocked()).isTrue();
  }

  @Test
  void deserializesNullLockedStatus() throws Exception {
    DeribitPlatformStatus status =
        mapper.readValue("{\"locked\":null}", DeribitPlatformStatus.class);

    assertThat(status.getLocked()).isNull();
  }

  @Test
  void rejectsInvalidLockedValue() {
    assertThatExceptionOfType(JsonMappingException.class)
        .isThrownBy(
            () -> mapper.readValue("{\"locked\":\"invalid\"}", DeribitPlatformStatus.class));
  }

  @Test
  void rejectsUnexpectedLockedTokenShape() {
    assertThatExceptionOfType(JsonMappingException.class)
        .isThrownBy(() -> mapper.readValue("{\"locked\":{}}", DeribitPlatformStatus.class));
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
