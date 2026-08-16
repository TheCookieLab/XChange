package org.knowm.xchange.cryptocom.dto.trade;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.cryptocom.dto.CryptoComResponse;

/** Maps the phase-3 order extensions (notional, position side, close position, exec instr). */
public class CryptoComOrderDtoTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  public void order_mapsExtendedPlacementFields() throws Exception {
    List<CryptoComOrder> orders = readOrders();

    assertThat(orders).hasSize(1);
    CryptoComOrder order = orders.get(0);
    assertThat(order.getOrderId()).isEqualTo("7000000000000000000");
    assertThat(order.getClientOid()).isEqualTo("client-oid-1");
    assertThat(order.getOrderType()).isEqualTo("LIMIT");
    assertThat(order.getQuantity()).isEqualTo("0.5");
    assertThat(order.getLimitPrice()).isEqualTo("50000.0");
    assertThat(order.getNotional()).isEqualTo("25000.0");
    assertThat(order.getPositionSide()).isEqualTo("LONG");
    assertThat(order.getClosePosition()).isFalse();
    assertThat(order.getExecInst()).isEqualTo("POST_ONLY");
    assertThat(order.getTriggerPrice()).isEqualTo("49000.0");
    assertThat(order.getStatus()).isEqualTo("ACTIVE");
    assertThat(order.getCreateTime()).isEqualTo(1700000001000L);
    assertThat(order.getUpdateTime()).isEqualTo(1700000002000L);
  }

  @Test
  public void order_closePositionTrue_isParsedAsBoolean() throws Exception {
    String json = "{\"id\":1,\"method\":\"x\",\"code\":0,\"result\":{\"data\":[{\"close_position\":true}]}}";
    CryptoComResponse response = mapper.readValue(json, CryptoComResponse.class);

    List<CryptoComOrder> orders =
        mapper.convertValue(
            response.getResult().get("data"),
            mapper.getTypeFactory().constructCollectionType(List.class, CryptoComOrder.class));

    assertThat(orders.get(0).getClosePosition()).isTrue();
  }

  private List<CryptoComOrder> readOrders() throws Exception {
    InputStream is = getClass().getResourceAsStream("order.json");
    CryptoComResponse response = mapper.readValue(is, CryptoComResponse.class);
    return mapper.convertValue(
        response.getResult().get("data"),
        mapper.getTypeFactory().constructCollectionType(List.class, CryptoComOrder.class));
  }
}