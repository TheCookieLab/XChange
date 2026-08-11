package info.bitrich.xchangestream.bybit.dto.trade;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.service.netty.StreamingObjectMapperHelper;
import java.math.BigDecimal;
import java.util.Date;
import org.junit.Test;
import org.knowm.xchange.bybit.dto.trade.BybitSide;

/**
 * Regression: the {@code bT} field must bind from the exact-case JSON key {@code BT} on both read
 * and write. Lombok derives {@code isBT()/getBT()}, which Jackson's legacy bean mangling would map
 * to {@code bt} without the explicit {@code @JsonProperty("BT")}.
 */
public class BybitTradeJsonTest {

  private static final ObjectMapper MAPPER = StreamingObjectMapperHelper.getObjectMapper();

  @Test
  public void deserializesBlockTradeFlagFromExactCaseKey() throws Exception {
    String json =
        "{\"T\":1577836800000,\"s\":\"BTCUSDT\",\"S\":\"Buy\",\"v\":\"0.01\",\"p\":\"90000\","
            + "\"L\":\"Rise\",\"i\":\"tid1\",\"BT\":true}";

    BybitTrade trade = MAPPER.readValue(json, BybitTrade.class);

    assertThat(trade.getTimestamp().getTime()).isEqualTo(1577836800000L);
    assertThat(trade.getInstId()).isEqualTo("BTCUSDT");
    assertThat(trade.getSide()).isEqualTo(BybitSide.BUY);
    assertThat(trade.getTradeId()).isEqualTo("tid1");
    assertThat(trade.isBT()).isTrue();
  }

  @Test
  public void serializesBlockTradeFlagWithExactCaseKey() throws Exception {
    BybitTrade trade =
        new BybitTrade(
            new Date(1577836800000L),
            "BTCUSDT",
            "Buy",
            new BigDecimal("0.01"),
            new BigDecimal("90000"),
            "Rise",
            "tid1",
            true);

    String json = MAPPER.writeValueAsString(trade);

    assertThat(json).contains("\"BT\":true");
  }
}
