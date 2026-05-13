package org.knowm.xchange.deribit.v2.dto.marketdata;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.IOException;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/** Health status returned by Deribit's public platform status endpoint. */
@Data
@Builder
@Jacksonized
public class DeribitPlatformStatus {

  @JsonProperty("locked")
  @JsonDeserialize(using = LockedStatusDeserializer.class)
  private Boolean locked;

  @JsonProperty("locked_indices")
  private List<String> lockedCurrencies;

  /**
   * Converts Deribit's global lock flag into the existing Boolean model.
   *
   * <p>Deribit can return {@code "partial"} when only some indices are locked. XChange treats that
   * as globally online because the platform is still serving public market-data requests.
   */
  static class LockedStatusDeserializer extends JsonDeserializer<Boolean> {

    @Override
    public Boolean deserialize(JsonParser parser, DeserializationContext context)
        throws IOException {
      String value = parser.getValueAsString();
      if (value == null) {
        return (Boolean) context.handleUnexpectedToken(Boolean.class, parser);
      }

      if ("partial".equalsIgnoreCase(value)) {
        return false;
      }

      if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
        return Boolean.valueOf(value);
      }

      return (Boolean)
          context.handleWeirdStringValue(
              Boolean.class, value, "Expected true, false, or partial");
    }
  }
}
