package org.knowm.xchange.bibox.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class BiboxErrorTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void readsErrorCodeAndMessage() throws Exception {
    BiboxError error =
        mapper.readValue("{\"code\":2001,\"msg\":\"Insufficient balance\"}", BiboxError.class);

    assertThat(error.getCode()).isEqualTo(2001);
    assertThat(error.getMsg()).isEqualTo("Insufficient balance");
  }

  @Test
  void readsAnErrorNestedInAResponse() throws Exception {
    BiboxResponse<?> response =
        mapper.readValue("{\"error\":{\"code\":400,\"msg\":\"Bad Request\"}}", BiboxResponse.class);

    assertThat(response.getError()).isNotNull();
    assertThat(response.getError().getCode()).isEqualTo(400);
    assertThat(response.getError().getMsg()).isEqualTo("Bad Request");
  }

  @Test
  void missingFieldsUseDefaultValues() throws Exception {
    BiboxError error = mapper.readValue("{}", BiboxError.class);

    assertThat(error.getCode()).isZero();
    assertThat(error.getMsg()).isNull();
  }
}
