package info.bitrich.xchangestream.kraken.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.kraken.config.Config;
import org.junit.jupiter.api.Test;

class KrakenMessageTest {

  @Test
  void render() throws Exception {
    var status = "{\"channel\":\"status\",\"type\":\"update\",\"data\":[{\"version\":\"2.0.10\",\"system\":\"online\",\"api_version\":\"v2\",\"connection_id\":10686724941062004662}]}";
    var method = "{\"method\":\"subscribe\",\"result\":{\"channel\":\"trade\",\"snapshot\":true,\"symbol\":\"BTC/USDT\"},\"success\":true,\"time_in\":\"2025-09-09T21:31:48.558238Z\",\"time_out\":\"2025-09-09T21:31:48.558280Z\"}";
    var hb = "{\"channel\":\"heartbeat\"}";
    var trade = "{\"channel\":\"trade\",\"type\":\"update\",\"data\":[{\"symbol\":\"BTC/USD\",\"side\":\"buy\",\"price\":111399.1,\"qty\":0.00037711,\"ord_type\":\"limit\",\"trade_id\":86881079,\"timestamp\":\"2025-09-09T21:31:50.716224Z\"}]}";

    ObjectMapper om = Config.getInstance().getObjectMapper();

    assertThat(om.readValue(status, KrakenMessage.class)).isInstanceOf(KrakenStatusMessage.class);
    assertThat(om.readValue(method, KrakenMessage.class)).isInstanceOf(KrakenControlMessage.class);
    assertThat(om.readValue(hb, KrakenMessage.class)).isInstanceOf(KrakenHeartbeatMessage.class);
    assertThat(om.readValue(trade, KrakenMessage.class)).isInstanceOf(KrakenTradeMessage.class);
  }

}