package org.knowm.xchange.okx.dto.subaccount;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

/** Offline Jackson wire-binding tests for {@link OkxSubAccountDetails}. */
public class OkxSubAccountDetailsTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  public void testGAuthWirePropertyRoundTrip() throws Exception {
    String json = "{\"gAuth\":\"abc123\",\"subAcct\":\"sub-1\"}";

    OkxSubAccountDetails details = mapper.readValue(json, OkxSubAccountDetails.class);

    assertThat(details.getGAuth()).isEqualTo("abc123");
    assertThat(details.getSubAcct()).isEqualTo("sub-1");

    // The wire key is "gAuth": serialization must not emit a second, mangled "gauth" property.
    String serialized = mapper.writeValueAsString(details);
    assertThat(serialized).contains("\"gAuth\":\"abc123\"");
    assertThat(serialized).doesNotContain("gauth");
  }
}
