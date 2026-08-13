package org.knowm.xchange.gateio.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

class GateioWiremockContractTest {

  private static final List<String> BODY_ENDPOINT_MAPPINGS =
      List.of(
          "api_v4_spot_orders_patch.json",
          "api_v4_spot_batch_orders.json",
          "api_v4_spot_cancel_batch_orders.json",
          "api_v4_spot_countdown_cancel_all.json");

  @Test
  void authenticatedBodyEndpoints_matchRequestBodies() throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    for (String mappingName : BODY_ENDPOINT_MAPPINGS) {
      try (InputStream in = getClass().getResourceAsStream("/mappings/" + mappingName)) {
        assertThat(in).as("missing mapping %s", mappingName).isNotNull();
        JsonNode mapping = mapper.readTree(in);
        JsonNode bodyPatterns = mapping.path("request").path("bodyPatterns");
        assertThat(bodyPatterns.isArray())
            .as("%s must constrain the request body", mappingName)
            .isTrue();
        assertThat(bodyPatterns.size())
            .as("%s must include at least one body matcher", mappingName)
            .isPositive();
      }
    }
  }
}
