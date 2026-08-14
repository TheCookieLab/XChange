package org.knowm.xchange.okx.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.derivative.OptionsContract;
import org.knowm.xchange.okx.OkxExchange;
import org.knowm.xchange.okx.dto.OkxException;
import org.knowm.xchange.okx.dto.OkxResponse;
import org.knowm.xchange.okx.dto.trade.OkxOrderResponse;

/** Verifies the {@code instType} mapping used for order-history queries. */
public class OkxTradeServiceTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private OkxTradeService service() {
    OkxExchange exchange = new OkxExchange();
    exchange.applySpecification(exchange.getDefaultExchangeSpecification());
    return new OkxTradeService(exchange, new ResilienceRegistries());
  }

  @Test
  public void historyInstrumentTypeMapsPerInstrumentFamily() {
    assertThat(OkxTradeService.historyInstrumentType(new CurrencyPair("BTC/USDT")))
        .isEqualTo("SPOT");
    assertThat(OkxTradeService.historyInstrumentType(new FuturesContract("BTC/USDT/SWAP")))
        .isEqualTo("SWAP");
    assertThat(OkxTradeService.historyInstrumentType(new FuturesContract("BTC/USD/SWAP")))
        .isEqualTo("SWAP");
    assertThat(OkxTradeService.historyInstrumentType(new FuturesContract("BTC/USDT/260814")))
        .isEqualTo("FUTURES");
    assertThat(OkxTradeService.historyInstrumentType(new FuturesContract("BTC/USD/260814")))
        .isEqualTo("FUTURES");
    assertThat(
            OkxTradeService.historyInstrumentType(new OptionsContract("BTC/USD/260828/110000/C")))
        .isEqualTo("OPTION");
  }

  @Test
  public void orderExceptionFallsBackToTopLevelCodeAndMsg() throws Exception {
    // Top-level failures (authentication, request-wide validation) carry no per-order entry.
    OkxResponse<List<OkxOrderResponse>> response =
        new OkxResponse<>(null, "50111", "Invalid OK Access Key", null);

    OkxException exception = service().orderException(response, "/trade/order");

    assertThat(exception.getCode()).isEqualTo(50111);
    assertThat(exception.getMessage()).contains("Invalid OK Access Key");
    assertThat(exception.getEndpoint()).isEqualTo("/trade/order");
    assertThat(exception.getRequestId()).isNull();
    assertThat(exception.getRetryClassification())
        .isEqualTo(OkxException.RetryClassification.NON_RETRYABLE);
  }

  @Test
  public void orderExceptionPrefersPerOrderEntryWhenPresent() throws Exception {
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    OkxOrderResponse failed =
        mapper.readValue(
            "{\"sCode\":\"51001\",\"sMsg\":\"Order not found\",\"ordId\":\"123\"}",
            OkxOrderResponse.class);
    OkxResponse<List<OkxOrderResponse>> response =
        new OkxResponse<>(null, "0", null, Collections.singletonList(failed));

    OkxException exception = service().orderException(response, "/trade/cancel-order");

    assertThat(exception.getCode()).isEqualTo(51001);
    assertThat(exception.getMessage()).contains("Order not found");
    assertThat(exception.getRequestId()).isEqualTo("123");
  }
}
