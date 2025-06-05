package org.knowm.xchange.coinsph.dto.account;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class CoinsphFiatDtoTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  public void setUp() {
    objectMapper = new ObjectMapper();
  }

  @Test
  public void testCoinsphFiatChannelSerialization() throws JsonProcessingException {
    // given
    CoinsphFiatChannel channel = new CoinsphFiatChannel("INSTAPAY", "BDO Network Bank", 1, "PHP");

    // when
    String json = objectMapper.writeValueAsString(channel);

    // then
    assertThat(json).contains("\"channelName\":\"INSTAPAY\"");
    assertThat(json).contains("\"channelSubject\":\"BDO Network Bank\"");
    assertThat(json).contains("\"status\":1");
    assertThat(json).contains("\"currency\":\"PHP\"");
  }

  @Test
  public void testCoinsphFiatChannelDeserialization() throws JsonProcessingException {
    // given
    String json =
        "{\"channelName\":\"PESONET\",\"channelSubject\":\"Union Bank\",\"status\":1,\"currency\":\"PHP\"}";

    // when
    CoinsphFiatChannel channel = objectMapper.readValue(json, CoinsphFiatChannel.class);

    // then
    assertThat(channel.getChannelName()).isEqualTo("PESONET");
    assertThat(channel.getChannelSubject()).isEqualTo("Union Bank");
    assertThat(channel.getStatus()).isEqualTo(1);
    assertThat(channel.getCurrency()).isEqualTo("PHP");
  }

  @Test
  public void testCoinsphFiatChannelUnavailable() throws JsonProcessingException {
    // given
    String json =
        "{\"channelName\":\"DRAGONPAY\",\"channelSubject\":\"Dragonpay\",\"status\":0,\"currency\":\"PHP\"}";

    // when
    CoinsphFiatChannel channel = objectMapper.readValue(json, CoinsphFiatChannel.class);

    // then
    assertThat(channel.getChannelName()).isEqualTo("DRAGONPAY");
    assertThat(channel.getChannelSubject()).isEqualTo("Dragonpay");
    assertThat(channel.getStatus()).isEqualTo(0); // unavailable
    assertThat(channel.getCurrency()).isEqualTo("PHP");
  }

  @Test
  public void testCoinsphCashOutRequestSerialization() throws JsonProcessingException {
    // given
    Map<String, Object> extendInfo = new HashMap<>();
    extendInfo.put("recipientAccountNumber", "046800021457");
    extendInfo.put("recipientName", "NEDDIE C QUINONAS");
    extendInfo.put("recipientAddress", "29 P.G. Almendras, Danao City, Cebu, PH");
    extendInfo.put("remarks", "BVNK test payment");

    CoinsphCashOutRequest request =
        CoinsphCashOutRequest.builder()
            .amount(new BigDecimal("60"))
            .internalOrderId("test-order-123")
            .currency("PHP")
            .channelName("INSTAPAY")
            .channelSubject("BDO Network Bank")
            .extendInfo(extendInfo)
            .build();

    // when
    String json = objectMapper.writeValueAsString(request);

    // then
    assertThat(json).contains("\"amount\":60");
    assertThat(json).contains("\"internalOrderId\":\"test-order-123\"");
    assertThat(json).contains("\"currency\":\"PHP\"");
    assertThat(json).contains("\"channelName\":\"INSTAPAY\"");
    assertThat(json).contains("\"channelSubject\":\"BDO Network Bank\"");
    assertThat(json).contains("\"recipientAccountNumber\":\"046800021457\"");
    assertThat(json).contains("\"recipientName\":\"NEDDIE C QUINONAS\"");
    assertThat(json).contains("\"recipientAddress\":\"29 P.G. Almendras, Danao City, Cebu, PH\"");
    assertThat(json).contains("\"remarks\":\"BVNK test payment\"");
  }

  @Test
  public void testCoinsphCashOutRequestDeserialization() throws JsonProcessingException {
    // given
    String json =
        "{\"amount\":100,\"internalOrderId\":\"order-456\",\"currency\":\"PHP\","
            + "\"channelName\":\"PESONET\",\"channelSubject\":\"Union Bank\","
            + "\"extendInfo\":{\"recipientAccountNumber\":\"123456789\","
            + "\"recipientName\":\"JUAN DELA CRUZ\",\"remarks\":\"Payment\"}}";

    // when
    CoinsphCashOutRequest request = objectMapper.readValue(json, CoinsphCashOutRequest.class);

    // then
    assertThat(request.getAmount()).isEqualByComparingTo(new BigDecimal("100"));
    assertThat(request.getInternalOrderId()).isEqualTo("order-456");
    assertThat(request.getCurrency()).isEqualTo("PHP");
    assertThat(request.getChannelName()).isEqualTo("PESONET");
    assertThat(request.getChannelSubject()).isEqualTo("Union Bank");
    assertThat(request.getExtendInfo()).containsEntry("recipientAccountNumber", "123456789");
    assertThat(request.getExtendInfo()).containsEntry("recipientName", "JUAN DELA CRUZ");
    assertThat(request.getExtendInfo()).containsEntry("remarks", "Payment");
  }

  @Test
  public void testCoinsphCashOutRequestMinimal() throws JsonProcessingException {
    // given
    CoinsphCashOutRequest request =
        CoinsphCashOutRequest.builder()
            .amount(new BigDecimal("50"))
            .internalOrderId("minimal-order")
            .currency("PHP")
            .channelName("INSTAPAY")
            .channelSubject("BDO")
            .extendInfo(new HashMap<>())
            .build();

    // when
    String json = objectMapper.writeValueAsString(request);

    // then
    assertThat(json).contains("\"amount\":50");
    assertThat(json).contains("\"internalOrderId\":\"minimal-order\"");
    assertThat(json).contains("\"currency\":\"PHP\"");
    assertThat(json).contains("\"channelName\":\"INSTAPAY\"");
    assertThat(json).contains("\"channelSubject\":\"BDO\"");
    assertThat(json).contains("\"extendInfo\":{}");
  }

  @Test
  public void testCoinsphCashOutResponseSerialization() throws JsonProcessingException {
    // given
    CoinsphCashOutResponse response = new CoinsphCashOutResponse("cash-out-order-789");

    // when
    String json = objectMapper.writeValueAsString(response);

    // then
    assertThat(json).contains("\"orderId\":\"cash-out-order-789\"");
  }

  @Test
  public void testCoinsphCashOutResponseDeserialization() throws JsonProcessingException {
    // given
    String json = "{\"orderId\":\"response-order-abc\"}";

    // when
    CoinsphCashOutResponse response = objectMapper.readValue(json, CoinsphCashOutResponse.class);

    // then
    assertThat(response.getOrderId()).isEqualTo("response-order-abc");
  }

  @Test
  public void testCoinsphCashOutRequestWithComplexExtendInfo() throws JsonProcessingException {
    // given
    Map<String, Object> extendInfo = new HashMap<>();
    extendInfo.put("recipientAccountNumber", "098765432109");
    extendInfo.put("recipientName", "MARIA SANTOS");
    extendInfo.put("recipientAddress", "456 Quezon Avenue, Quezon City, Metro Manila, Philippines");
    extendInfo.put("remarks", "Tuition fee payment");
    extendInfo.put("recipientMobileNumber", "+639987654321");
    extendInfo.put("purposeOfTransfer", "Education");
    extendInfo.put("recipientBankCode", "BPI");
    extendInfo.put("relationshipToSender", "Child");

    CoinsphCashOutRequest request =
        CoinsphCashOutRequest.builder()
            .amount(new BigDecimal("15000"))
            .internalOrderId("education-payment-001")
            .currency("PHP")
            .channelName("INSTAPAY")
            .channelSubject("Bank of the Philippine Islands")
            .extendInfo(extendInfo)
            .build();

    // when
    String json = objectMapper.writeValueAsString(request);
    CoinsphCashOutRequest deserializedRequest =
        objectMapper.readValue(json, CoinsphCashOutRequest.class);

    // then
    assertThat(deserializedRequest.getAmount()).isEqualByComparingTo(new BigDecimal("15000"));
    assertThat(deserializedRequest.getInternalOrderId()).isEqualTo("education-payment-001");
    assertThat(deserializedRequest.getCurrency()).isEqualTo("PHP");
    assertThat(deserializedRequest.getChannelName()).isEqualTo("INSTAPAY");
    assertThat(deserializedRequest.getChannelSubject()).isEqualTo("Bank of the Philippine Islands");

    Map<String, Object> deserializedExtendInfo = deserializedRequest.getExtendInfo();
    assertThat(deserializedExtendInfo).containsEntry("recipientAccountNumber", "098765432109");
    assertThat(deserializedExtendInfo).containsEntry("recipientName", "MARIA SANTOS");
    assertThat(deserializedExtendInfo).containsEntry("recipientMobileNumber", "+639987654321");
    assertThat(deserializedExtendInfo).containsEntry("purposeOfTransfer", "Education");
    assertThat(deserializedExtendInfo).containsEntry("recipientBankCode", "BPI");
    assertThat(deserializedExtendInfo).containsEntry("relationshipToSender", "Child");
  }
}
