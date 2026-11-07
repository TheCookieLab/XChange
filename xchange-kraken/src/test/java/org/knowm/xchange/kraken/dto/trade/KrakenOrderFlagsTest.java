package org.knowm.xchange.kraken.dto.trade;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.kraken.dto.trade.KrakenOrderFlags.KrakenOrderFlagsDeserializer;

class KrakenOrderFlagsTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  static class FlagsContainer {
    @JsonProperty("oflags")
    @JsonDeserialize(using = KrakenOrderFlagsDeserializer.class)
    private Set<KrakenOrderFlags> flags;

    public Set<KrakenOrderFlags> getFlags() {
      return flags;
    }
  }

  @Test
  void testDeserializeEmptyString() throws Exception {
    FlagsContainer container =
        objectMapper.readValue("{\"oflags\": \"\"}", FlagsContainer.class);
    assertThat(container.getFlags()).isNotNull().isEmpty();
  }

  @Test
  void testDeserializeSingleFlag() throws Exception {
    FlagsContainer container =
        objectMapper.readValue("{\"oflags\": \"fciq\"}", FlagsContainer.class);
    assertThat(container.getFlags()).containsExactly(KrakenOrderFlags.FCIQ);
  }

  @Test
  void testDeserializeMultipleFlags() throws Exception {
    FlagsContainer container =
        objectMapper.readValue(
            "{\"oflags\": \"post,fcib,nompp\"}", FlagsContainer.class);
    assertThat(container.getFlags())
        .containsExactlyInAnyOrder(
            KrakenOrderFlags.POST, KrakenOrderFlags.FCIB, KrakenOrderFlags.NOMPP);
  }

  @Test
  void testDeserializeWithWhitespaceAndMixedCase() throws Exception {
    FlagsContainer container =
        objectMapper.readValue(
            "{\"oflags\": \" VIQC , post \"}", FlagsContainer.class);
    assertThat(container.getFlags())
        .containsExactlyInAnyOrder(KrakenOrderFlags.VIQC, KrakenOrderFlags.POST);
  }

  @Test
  void testDeserializeTrailingOrConsecutiveCommas() throws Exception {
    FlagsContainer container =
        objectMapper.readValue(
            "{\"oflags\": \",fcib,,viqc,\"}", FlagsContainer.class);
    assertThat(container.getFlags())
        .containsExactlyInAnyOrder(KrakenOrderFlags.FCIB, KrakenOrderFlags.VIQC);
  }

  @Test
  void testDeserializeNullString() throws Exception {
    FlagsContainer container =
        objectMapper.readValue("{\"oflags\": null}", FlagsContainer.class);
    assertThat(container.getFlags()).isNull();
  }
}
