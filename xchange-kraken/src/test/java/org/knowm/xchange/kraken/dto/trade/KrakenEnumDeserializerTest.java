package org.knowm.xchange.kraken.dto.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class KrakenEnumDeserializerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void deserializesStringsAndNulls() throws Exception {
    assertThat(objectMapper.readValue("\"B\"", KrakenType.class)).isEqualTo(KrakenType.BUY);
    assertThat(objectMapper.readValue("\"sell\"", KrakenType.class)).isEqualTo(KrakenType.SELL);
    assertThat(objectMapper.readValue("\"stop-loss\"", KrakenOrderType.class))
        .isEqualTo(KrakenOrderType.STOP_LOSS);
    assertThat(objectMapper.readValue("\"M\"", KrakenOrderType.class))
        .isEqualTo(KrakenOrderType.MARKET);
    assertThat(objectMapper.readValue("\"CLOSED\"", KrakenOrderStatus.class))
        .isEqualTo(KrakenOrderStatus.CLOSED);
    assertThat(objectMapper.readValue("\"unknown\"", KrakenType.class)).isNull();
    assertThat(objectMapper.readValue("null", KrakenType.class)).isNull();
    assertThat(objectMapper.readValue("null", KrakenOrderType.class)).isNull();
    assertThat(objectMapper.readValue("null", KrakenOrderStatus.class)).isNull();
  }

  @Test
  void rejectsNonStringValues() {
    for (Class<?> type :
        new Class<?>[] {KrakenType.class, KrakenOrderType.class, KrakenOrderStatus.class}) {
      for (String value : new String[] {"123", "true", "{}", "[]"}) {
        assertThatThrownBy(() -> objectMapper.readValue(value, type))
            .isInstanceOf(JsonMappingException.class);
      }
    }
  }
}
