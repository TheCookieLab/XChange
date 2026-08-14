package org.knowm.xchange.okx.dto.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.Test;
import org.knowm.xchange.okx.dto.OkxResponse;

/**
 * Offline deserialization/serialization tests for the DTOs backing the new OKX v5 endpoints (asset
 * transfer, positions history, bills archive, set position mode).
 */
public class OkxEndpointDtoTest {

  private final ObjectMapper mapper = new ObjectMapper();

  public OkxEndpointDtoTest() {
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  @Test
  public void testAssetTransferResponseDeserialization() throws Exception {
    OkxResponse<List<OkxTransferResponse>> response =
        mapper.readValue(
            "{\"code\":\"0\",\"data\":[{\"transId\":\"1234567890\"}]}",
            new TypeReference<OkxResponse<List<OkxTransferResponse>>>() {});

    assertThat(response.isSuccess()).isTrue();
    assertThat(response.getData()).hasSize(1);
    assertThat(response.getData().get(0).getTransferId()).isEqualTo("1234567890");
  }

  @Test
  public void testAssetTransferRequestSerialization() throws Exception {
    OkxTransferRequest request =
        OkxTransferRequest.builder()
            .currency("USDT")
            .amount("100")
            .fromAccount("6")
            .toAccount("18")
            .type("0")
            .instrumentId("BTC-USDT")
            .toInstrumentId("BTC-USDT-SWAP")
            .build();

    String json = mapper.writeValueAsString(request);

    assertThat(json)
        .contains("\"ccy\":\"USDT\"")
        .contains("\"amt\":\"100\"")
        .contains("\"from\":\"6\"")
        .contains("\"to\":\"18\"")
        .contains("\"type\":\"0\"")
        .contains("\"instId\":\"BTC-USDT\"")
        .contains("\"toInstId\":\"BTC-USDT-SWAP\"");
  }

  @Test
  public void testPositionsHistoryResponseDeserialization() throws Exception {
    OkxResponse<List<OkxPosition>> response =
        mapper.readValue(
            "{\"code\":\"0\",\"data\":[{\"instType\":\"SWAP\",\"instId\":\"BTC-USDT-SWAP\","
                + "\"posSide\":\"long\",\"pos\":\"1.5\",\"avgPx\":\"65000\",\"upl\":\"123.45\","
                + "\"mgnMode\":\"cross\",\"lever\":\"10\",\"cTime\":\"1627631240000\"}]}",
            new TypeReference<OkxResponse<List<OkxPosition>>>() {});

    assertThat(response.isSuccess()).isTrue();
    OkxPosition position = response.getData().get(0);
    assertThat(position.getInstrumentType()).isEqualTo("SWAP");
    assertThat(position.getInstrumentId()).isEqualTo("BTC-USDT-SWAP");
    assertThat(position.getPositionSide()).isEqualTo("long");
    assertThat(position.getPosition()).isEqualByComparingTo(new BigDecimal("1.5"));
    assertThat(position.getAverageOpenPrice()).isEqualByComparingTo(new BigDecimal("65000"));
    assertThat(position.getUnrealizedPnL()).isEqualByComparingTo(new BigDecimal("123.45"));
    assertThat(position.getMarginMode()).isEqualTo("cross");
    assertThat(position.getLeverage()).isEqualTo("10");
    assertThat(position.getCreationTime()).isEqualTo("1627631240000");
  }

  @Test
  public void testBillsArchiveResponseDeserialization() throws Exception {
    OkxResponse<List<OkxBillDetails>> response =
        mapper.readValue(
            "{\"code\":\"0\",\"data\":[{\"instType\":\"SPOT\",\"billId\":\"123456789\","
                + "\"type\":\"2\",\"subType\":\"1\",\"ts\":\"1627631240000\","
                + "\"balChg\":\"-0.5\",\"bal\":\"10\",\"sz\":\"0.5\",\"ccy\":\"BTC\","
                + "\"instId\":\"BTC-USDT\",\"notes\":\"test\"}]}",
            new TypeReference<OkxResponse<List<OkxBillDetails>>>() {});

    assertThat(response.isSuccess()).isTrue();
    OkxBillDetails bill = response.getData().get(0);
    assertThat(bill.getInstType()).isEqualTo("SPOT");
    assertThat(bill.getBillId()).isEqualTo("123456789");
    assertThat(bill.getBillType()).isEqualTo("2");
    assertThat(bill.getBillSubType()).isEqualTo("1");
    assertThat(bill.getTimestamp()).isEqualTo("1627631240000");
    assertThat(bill.getAccountBalanceChange()).isEqualTo("-0.5");
    assertThat(bill.getAccountBalance()).isEqualTo("10");
    assertThat(bill.getQuantity()).isEqualTo("0.5");
    assertThat(bill.getCurrency()).isEqualTo("BTC");
    assertThat(bill.getInstId()).isEqualTo("BTC-USDT");
    assertThat(bill.getNotes()).isEqualTo("test");
  }

  @Test
  public void testSetPositionModeResponseDeserialization() throws Exception {
    OkxResponse<List<OkxSetPositionModeResponse>> response =
        mapper.readValue(
            "{\"code\":\"0\",\"data\":[{\"posMode\":\"net_mode\",\"acctLv\":\"1\"}]}",
            new TypeReference<OkxResponse<List<OkxSetPositionModeResponse>>>() {});

    assertThat(response.isSuccess()).isTrue();
    assertThat(response.getData()).hasSize(1);
    assertThat(response.getData().get(0).getPositionMode()).isEqualTo("net_mode");
    assertThat(response.getData().get(0).getAccountLevel()).isEqualTo("1");
  }

  @Test
  public void testSetPositionModeRequestSerialization() throws Exception {
    OkxSetPositionModeRequest request =
        OkxSetPositionModeRequest.builder().positionMode("net_mode").accountLevel("1").build();

    String json = mapper.writeValueAsString(request);

    assertThat(json).contains("\"posMode\":\"net_mode\"").contains("\"acctLv\":\"1\"");
  }

  @Test
  public void testErrorResponseDeserializationPreservesIdCodeMsg() throws Exception {
    OkxResponse<List<OkxTransferResponse>> response =
        mapper.readValue(
            "{\"code\":\"50102\",\"msg\":\"Insufficient balance\",\"id\":\"req-42\",\"data\":[]}",
            new TypeReference<OkxResponse<List<OkxTransferResponse>>>() {});

    assertThat(response.isSuccess()).isFalse();
    assertThat(response.getCode()).isEqualTo("50102");
    assertThat(response.getMsg()).isEqualTo("Insufficient balance");
    assertThat(response.getId()).isEqualTo("req-42");
    assertThat(response.getData()).isEmpty();
  }
}
