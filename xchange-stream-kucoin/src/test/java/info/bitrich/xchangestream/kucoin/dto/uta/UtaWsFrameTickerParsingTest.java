package info.bitrich.xchangestream.kucoin.dto.uta;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Regression: ticker frame fields a/A/b/B/l/q must map without Lombok getter collisions. */
class UtaWsFrameTickerParsingTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void parsesSpotTickerFrame() throws Exception {
    String frame =
        "{\"T\":\"ticker.SPOT\",\"P\":1768206966101166007,"
            + "\"d\":{\"A\":\"0.97675941\",\"B\":\"0.02052839\",\"E\":25958853459,"
            + "\"M\":1768206966096000000,\"S\":\"BUY\",\"a\":\"90968.2\",\"b\":\"90968.1\","
            + "\"l\":\"90968.2\",\"q\":\"0.00109929\",\"s\":\"BTC-USDT\"}}";

    UtaWsFrame wsFrame = MAPPER.readValue(frame, UtaWsFrame.class);
    UtaWsFrame.TickerData d = MAPPER.treeToValue(wsFrame.getData(), UtaWsFrame.TickerData.class);

    assertEquals(new BigDecimal("90968.1"), d.getBid());
    assertEquals(new BigDecimal("0.02052839"), d.getBidSize());
    assertEquals(new BigDecimal("90968.2"), d.getAsk());
    assertEquals(new BigDecimal("0.97675941"), d.getAskSize());
    assertEquals(new BigDecimal("90968.2"), d.getLast());
    assertEquals(new BigDecimal("0.00109929"), d.getLastSize());
    assertEquals("BTC-USDT", d.getSymbol());
    assertEquals("ticker", wsFrame.channel());
    assertEquals("SPOT", wsFrame.tradeType());
  }
}
