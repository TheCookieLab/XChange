package org.knowm.xchange.okx.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.derivative.OptionsContract;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.okx.OkxAuthenticated;
import org.knowm.xchange.okx.OkxExchange;
import org.knowm.xchange.okx.dto.OkxException;
import org.knowm.xchange.okx.dto.OkxResponse;
import org.knowm.xchange.okx.dto.trade.OkxAmendOrderRequest;
import org.knowm.xchange.okx.dto.trade.OkxCancelOrderRequest;
import org.knowm.xchange.okx.dto.trade.OkxOrderRequest;
import org.knowm.xchange.okx.dto.trade.OkxOrderResponse;
import org.knowm.xchange.okx.dto.trade.OkxTradeParams;

/** Verifies the {@code instType} mapping used for order-history queries. */
public class OkxTradeServiceTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private OkxTradeService service() {
    OkxExchange exchange = new OkxExchange();
    exchange.applySpecification(exchange.getDefaultExchangeSpecification());
    return new OkxTradeService(exchange, new ResilienceRegistries());
  }

  /**
   * Testable subclass that answers single-order operations from canned envelopes instead of
   * reaching the authenticated REST endpoints.
   */
  private static class StubTradeService extends OkxTradeService {
    OkxResponse<List<OkxOrderResponse>> placeResponse;
    OkxResponse<List<OkxOrderResponse>> amendResponse;
    OkxResponse<List<OkxOrderResponse>> cancelResponse;

    StubTradeService(OkxExchange exchange) {
      super(exchange, new ResilienceRegistries());
    }

    @Override
    public OkxResponse<List<OkxOrderResponse>> placeOkxOrder(OkxOrderRequest order)
        throws IOException {
      return placeResponse;
    }

    @Override
    public OkxResponse<List<OkxOrderResponse>> amendOkxOrder(OkxAmendOrderRequest order)
        throws IOException {
      return amendResponse;
    }

    @Override
    public OkxResponse<List<OkxOrderResponse>> amendOkxOrder(List<OkxAmendOrderRequest> orders)
        throws IOException {
      return amendResponse;
    }

    @Override
    public OkxResponse<List<OkxOrderResponse>> cancelOkxOrder(OkxCancelOrderRequest order)
        throws IOException {
      return cancelResponse;
    }
  }

  private StubTradeService stubService() {
    OkxExchange exchange = new OkxExchange();
    exchange.applySpecification(exchange.getDefaultExchangeSpecification());
    return new StubTradeService(exchange);
  }

  private OkxOrderResponse orderResponse(String json) throws IOException {
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    return mapper.readValue(json, OkxOrderResponse.class);
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

  @Test
  public void perOrderSucceededRequiresZeroPerOrderCode() throws Exception {
    OkxOrderResponse ok = orderResponse("{\"sCode\":\"0\",\"sMsg\":\"\",\"ordId\":\"123\"}");
    OkxOrderResponse rejected =
        orderResponse("{\"sCode\":\"51008\",\"sMsg\":\"Insufficient balance\"}");
    OkxOrderResponse missingCode = orderResponse("{\"sMsg\":\"Insufficient balance\"}");

    assertThat(
            OkxTradeService.perOrderSucceeded(
                new OkxResponse<>(null, "0", null, Collections.singletonList(ok))))
        .isTrue();
    assertThat(
            OkxTradeService.perOrderSucceeded(
                new OkxResponse<>(null, "0", null, Collections.singletonList(rejected))))
        .isFalse();
    assertThat(
            OkxTradeService.perOrderSucceeded(
                new OkxResponse<>(null, "0", null, Collections.singletonList(missingCode))))
        .isFalse();
    assertThat(
            OkxTradeService.perOrderSucceeded(
                new OkxResponse<>(null, "0", null, Collections.emptyList())))
        .isFalse();
    assertThat(OkxTradeService.perOrderSucceeded(new OkxResponse<>(null, "0", null, null)))
        .isFalse();
  }

  @Test
  public void placeMarketOrderRejectsPerOrderFailure() throws Exception {
    StubTradeService service = stubService();
    service.placeResponse =
        new OkxResponse<>(
            null,
            "0",
            null,
            Collections.singletonList(
                orderResponse(
                    "{\"sCode\":\"51008\",\"sMsg\":\"Insufficient balance\",\"clOrdId\":\"cl-1\"}")));

    assertThatThrownBy(
            () ->
                service.placeMarketOrder(
                    new MarketOrder(
                        Order.OrderType.BID,
                        new BigDecimal("0.001"),
                        new CurrencyPair("BTC/USDT"))))
        .isInstanceOf(OkxException.class)
        .hasMessageContaining("Insufficient balance")
        .satisfies(
            e -> {
              assertThat(((OkxException) e).getCode()).isEqualTo(51008);
              assertThat(((OkxException) e).getEndpoint())
                  .isEqualTo(OkxAuthenticated.placeOrderPath);
              assertThat(((OkxException) e).getRequestId()).isEqualTo("cl-1");
            });
  }

  @Test
  public void placeLimitOrderRejectsPerOrderFailure() throws Exception {
    StubTradeService service = stubService();
    service.placeResponse =
        new OkxResponse<>(
            null,
            "0",
            null,
            Collections.singletonList(
                orderResponse("{\"sCode\":\"51008\",\"sMsg\":\"Insufficient balance\"}")));

    assertThatThrownBy(
            () ->
                service.placeLimitOrder(
                    new LimitOrder(
                        Order.OrderType.BID,
                        new BigDecimal("0.001"),
                        new CurrencyPair("BTC/USDT"),
                        "id-1",
                        null,
                        new BigDecimal("60000"))))
        .isInstanceOf(OkxException.class)
        .hasMessageContaining("Insufficient balance");
  }

  @Test
  public void changeOrderRejectsPerOrderFailure() throws Exception {
    StubTradeService service = stubService();
    service.amendResponse =
        new OkxResponse<>(
            null,
            "0",
            null,
            Collections.singletonList(
                orderResponse("{\"sCode\":\"51401\",\"sMsg\":\"Order not found\"}")));

    assertThatThrownBy(
            () ->
                service.changeOrder(
                    new LimitOrder(
                        Order.OrderType.BID,
                        new BigDecimal("0.001"),
                        new CurrencyPair("BTC/USDT"),
                        "id-1",
                        null,
                        new BigDecimal("60000"))))
        .isInstanceOf(OkxException.class)
        .hasMessageContaining("Order not found")
        .satisfies(
            e ->
                assertThat(((OkxException) e).getEndpoint())
                    .isEqualTo(OkxAuthenticated.amendOrderPath));
  }

  @Test
  public void cancelOrderRejectsPerOrderFailure() throws Exception {
    StubTradeService service = stubService();
    service.cancelResponse =
        new OkxResponse<>(
            null,
            "0",
            null,
            Collections.singletonList(
                orderResponse("{\"sCode\":\"51401\",\"sMsg\":\"Order not found\"}")));

    assertThatThrownBy(
            () ->
                service.cancelOrder(
                    new OkxTradeParams.OkxCancelOrderParams(
                        new CurrencyPair("BTC/USDT"), "order-1")))
        .isInstanceOf(OkxException.class)
        .hasMessageContaining("Order not found")
        .satisfies(
            e ->
                assertThat(((OkxException) e).getEndpoint())
                    .isEqualTo(OkxAuthenticated.cancelOrderPath));
  }

  @Test
  public void changeOrderBatchRejectsPerItemFailure() throws Exception {
    StubTradeService service = stubService();
    OkxOrderResponse ok = orderResponse("{\"sCode\":\"0\",\"sMsg\":\"\",\"ordId\":\"100\"}");
    OkxOrderResponse rejected =
        orderResponse("{\"sCode\":\"51001\",\"sMsg\":\"Order not found\",\"clOrdId\":\"cl-2\"}");
    service.amendResponse = new OkxResponse<>(null, "0", null, Arrays.asList(ok, rejected));

    assertThatThrownBy(
            () ->
                service.changeOrder(
                    Arrays.asList(
                        new LimitOrder(
                            Order.OrderType.BID,
                            new BigDecimal("0.001"),
                            new CurrencyPair("BTC/USDT"),
                            "id-1",
                            null,
                            new BigDecimal("60000")),
                        new LimitOrder(
                            Order.OrderType.BID,
                            new BigDecimal("0.002"),
                            new CurrencyPair("BTC/USDT"),
                            "id-2",
                            null,
                            new BigDecimal("61000")))))
        .isInstanceOf(ExchangeException.class)
        .hasMessageContaining("OKX rejected batch amendment")
        .hasMessageContaining("cl-2")
        .hasMessageContaining("index 1")
        .hasMessageContaining("51001")
        .hasMessageContaining("Order not found");
  }

  @Test
  public void changeOrderBatchReturnsAllOrderIdsWhenPerOrderCodesZero() throws Exception {
    StubTradeService service = stubService();
    OkxOrderResponse first = orderResponse("{\"sCode\":\"0\",\"sMsg\":\"\",\"ordId\":\"100\"}");
    OkxOrderResponse second = orderResponse("{\"sCode\":\"0\",\"sMsg\":\"\",\"ordId\":\"200\"}");
    service.amendResponse = new OkxResponse<>(null, "0", null, Arrays.asList(first, second));

    assertThat(
            service.changeOrder(
                Arrays.asList(
                    new LimitOrder(
                        Order.OrderType.BID,
                        new BigDecimal("0.001"),
                        new CurrencyPair("BTC/USDT"),
                        "id-1",
                        null,
                        new BigDecimal("60000")),
                    new LimitOrder(
                        Order.OrderType.BID,
                        new BigDecimal("0.002"),
                        new CurrencyPair("BTC/USDT"),
                        "id-2",
                        null,
                        new BigDecimal("61000")))))
        .containsExactly("100", "200");
  }

  @Test
  public void singleOrderOperationsReturnSuccessWhenPerOrderCodeIsZero() throws Exception {
    StubTradeService service = stubService();
    service.placeResponse =
        new OkxResponse<>(
            null,
            "0",
            null,
            Collections.singletonList(
                orderResponse("{\"sCode\":\"0\",\"sMsg\":\"\",\"ordId\":\"123\"}")));
    service.amendResponse =
        new OkxResponse<>(
            null,
            "0",
            null,
            Collections.singletonList(
                orderResponse("{\"sCode\":\"0\",\"sMsg\":\"\",\"ordId\":\"123\"}")));
    service.cancelResponse =
        new OkxResponse<>(
            null,
            "0",
            null,
            Collections.singletonList(orderResponse("{\"sCode\":\"0\",\"sMsg\":\"\"}")));

    assertThat(
            service.placeMarketOrder(
                new MarketOrder(
                    Order.OrderType.BID, new BigDecimal("0.001"), new CurrencyPair("BTC/USDT"))))
        .isEqualTo("123");
    assertThat(
            service.placeLimitOrder(
                new LimitOrder(
                    Order.OrderType.BID,
                    new BigDecimal("0.001"),
                    new CurrencyPair("BTC/USDT"),
                    "id-1",
                    null,
                    new BigDecimal("60000"))))
        .isEqualTo("123");
    assertThat(
            service.changeOrder(
                new LimitOrder(
                    Order.OrderType.BID,
                    new BigDecimal("0.001"),
                    new CurrencyPair("BTC/USDT"),
                    "id-1",
                    null,
                    new BigDecimal("60000"))))
        .isEqualTo("123");
    assertThat(
            service.cancelOrder(
                new OkxTradeParams.OkxCancelOrderParams(new CurrencyPair("BTC/USDT"), "order-1")))
        .isTrue();
  }
}
