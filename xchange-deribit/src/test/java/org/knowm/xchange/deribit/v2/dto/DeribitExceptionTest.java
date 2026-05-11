package org.knowm.xchange.deribit.v2.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.deribit.v2.config.DeribitJacksonObjectMapperFactory;

class DeribitExceptionTest {

  private final ObjectMapper mapper = new DeribitJacksonObjectMapperFactory().createObjectMapper();

  @Test
  void createsFallbackMessageWhenErrorIsNull() {
    DeribitException exception = new DeribitException(null);

    assertThat(exception.getError()).isNull();
    assertThat(exception).hasMessageContaining("Operation failed without any error message");
  }

  @Test
  void deserializesMissingErrorPayload() throws Exception {
    DeribitException exception = mapper.readValue("{}", DeribitException.class);

    assertThat(exception.getError()).isNull();
    assertThat(exception).hasMessageContaining("Operation failed without any error message");
  }

  @Test
  void deserializesNullErrorPayload() throws Exception {
    DeribitException exception = mapper.readValue("{\"error\":null}", DeribitException.class);

    assertThat(exception.getError()).isNull();
    assertThat(exception).hasMessageContaining("Operation failed without any error message");
  }
}
