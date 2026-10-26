package org.knowm.xchange.cexio.dto.trade;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.IOException;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

public class CexIOArchivedOrderTest {

  @Test
  public void testDeserializeArchivedOrder() throws IOException {
    String json = "{\n" +
        "    \"id\": \"1\",\n" +
        "    \"type\": \"sell\",\n" +
        "    \"time\": \"2017-06-19T12:13:28.883Z\",\n" +
        "    \"lastTxTime\": \"2017-06-19T12:25:09.900Z\",\n" +
        "    \"lastTx\": \"3912741373\",\n" +
        "    \"pos\": null,\n" +
        "    \"status\": \"d\",\n" +
        "    \"symbol1\": \"BTC\",\n" +
        "    \"symbol2\": \"GBP\",\n" +
        "    \"amount\": \"0.01000000\",\n" +
        "    \"price\": \"2059.7258\",\n" +
        "    \"tfacf\": \"1\",\n" +
        "    \"fa:GBP\": \"0.00\",\n" +
        "    \"ta:GBP\": \"1.62\",\n" +
        "    \"remains\": \"0.00000000\",\n" +
        "    \"tfa:GBP\": \"0.04\",\n" +
        "    \"tta:GBP\": \"18.97\",\n" +
        "    \"a:BTC:cds\": \"0.01000000\",\n" +
        "    \"a:GBP:cds\": \"20.59\",\n" +
        "    \"f:GBP:cds\": \"0.04\",\n" +
        "    \"tradingFeeMaker\": \"0\",\n" +
        "    \"tradingFeeTaker\": \"0.20\",\n" +
        "    \"tradingFeeUserVolumeAmount\": \"75631402\",\n" +
        "    \"orderId\": \"3912722201\"\n" +
        "}";

    ObjectMapper mapper = new ObjectMapper();
    CexIOArchivedOrder order = mapper.readValue(json, CexIOArchivedOrder.class);

    assertThat(order.id).isEqualTo("1");
    assertThat(order.type).isEqualTo("sell");
    assertThat(order.symbol1).isEqualTo("BTC");
    assertThat(order.symbol2).isEqualTo("GBP");
    assertThat(order.amount).isEqualTo(new BigDecimal("0.01000000"));
    assertThat(order.orderPrice).isEqualTo(new BigDecimal("2059.7258"));
    assertThat(order.feeValue).isEqualTo(new BigDecimal("0.04"));
    assertThat(order.feeCcy).isEqualTo("GBP");
    assertThat(order.orderId).isEqualTo("3912722201");
  }
}
