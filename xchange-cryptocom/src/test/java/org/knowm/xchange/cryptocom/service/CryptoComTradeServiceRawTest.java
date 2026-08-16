package org.knowm.xchange.cryptocom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.cryptocom.CryptoCom;
import org.knowm.xchange.cryptocom.CryptoComExchange;
import org.knowm.xchange.cryptocom.dto.CryptoComRequest;
import org.knowm.xchange.cryptocom.dto.CryptoComResponse;
import org.knowm.xchange.cryptocom.dto.CryptoComUnknownOrderOutcomeException;
import org.knowm.xchange.cryptocom.dto.trade.CryptoComOrder;
import org.knowm.xchange.cryptocom.dto.trade.CryptoComOrderPlacementResult;
import org.knowm.xchange.cryptocom.dto.trade.CryptoComOrderSide;
import org.knowm.xchange.cryptocom.dto.trade.CryptoComOrderType;
import org.knowm.xchange.cryptocom.dto.trade.CryptoComPlacementOutcome;
import org.knowm.xchange.cryptocom.dto.trade.CryptoComTimeInForce;
import org.knowm.xchange.cryptocom.dto.trade.CryptoComUserTrade;
import org.knowm.xchange.exceptions.ExchangeSecurityException;

public class CryptoComTradeServiceRawTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  public void marketBuy_sendsNotionalInsteadOfQuantity() throws Exception {
    CryptoComTradeServiceRaw raw = newRaw();

    raw.createCryptoComOrder(
        "BTC_USDT", CryptoComOrderSide.BUY, CryptoComOrderType.MARKET, null, "100", null, null);

    CryptoComRequest sent = lastRequest;
    assertThat(sent.getParams()).containsEntry("notional", "100");
    assertThat(sent.getParams()).doesNotContainKey("quantity");
  }

  @Test
  public void marketSell_sendsQuantity() throws Exception {
    CryptoComTradeServiceRaw raw = newRaw();

    raw.createCryptoComOrder(
        "BTC_USDT", CryptoComOrderSide.SELL, CryptoComOrderType.MARKET, null, "0.5", null, null);

    CryptoComRequest sent = lastRequest;
    assertThat(sent.getParams()).containsEntry("quantity", "0.5");
    assertThat(sent.getParams()).doesNotContainKey("notional");
  }

  @Test
  public void limitBuy_sendsQuantity() throws Exception {
    CryptoComTradeServiceRaw raw = newRaw();

    raw.createCryptoComOrder(
        "BTC_USDT",
        CryptoComOrderSide.BUY,
        CryptoComOrderType.LIMIT,
        "50000",
        "0.5",
        null,
        null);

    CryptoComRequest sent = lastRequest;
    assertThat(sent.getParams()).containsEntry("quantity", "0.5");
    assertThat(sent.getParams()).doesNotContainKey("notional");
  }

  @Test
  public void createOrder_withExecInst_andClientOid_transmitsBoth() throws Exception {
    CryptoComTradeServiceRaw raw = newRaw();

    CryptoComOrderPlacementResult placement =
        raw.createCryptoComOrder(
            "BTC_USDT",
            CryptoComOrderSide.BUY,
            CryptoComOrderType.LIMIT,
            "50000",
            "0.5",
            CryptoComTimeInForce.GOOD_TILL_CANCEL,
            "client-ABC",
            "POST_ONLY");

    assertThat(placement.getOutcome()).isEqualTo(CryptoComPlacementOutcome.ACKED);
    assertThat(placement.getOrderId()).isEqualTo("1");
    assertThat(placement.getClientOid()).isEqualTo("client-ABC");
    assertThat(placement.getRequestId()).isEqualTo(1L);
    CryptoComRequest sent = lastRequest;
    assertThat(sent.getParams()).containsEntry("client_oid", "client-ABC");
    assertThat(sent.getParams()).containsEntry("exec_inst", "POST_ONLY");
    assertThat(sent.getParams()).containsEntry("time_in_force", "GOOD_TILL_CANCEL");
  }

  @Test
  public void advancedOrder_sendsTriggerBlockAndClientOid() throws Exception {
    CryptoComTradeServiceRaw raw = newRawWithAdvancedOrder();

    CryptoComOrderPlacementResult placement =
        raw.createCryptoComAdvancedOrder(
            "BTC_USDT",
            CryptoComOrderSide.SELL,
            CryptoComOrderType.STOP_LOSS,
            null,
            "0.5",
            null,
            "49000",
            CryptoComTimeInForce.GOOD_TILL_CANCEL,
            "client-STOP");

    assertThat(placement.getOutcome()).isEqualTo(CryptoComPlacementOutcome.ACKED);
    assertThat(placement.getOrderId()).isEqualTo("9");
    CryptoComRequest sent = lastRequest;
    assertThat(sent.getParams()).containsEntry("type", "STOP_LOSS");
    assertThat(sent.getParams()).containsEntry("client_oid", "client-STOP");
    @SuppressWarnings("unchecked")
    java.util.Map<String, Object> trigger = (java.util.Map<String, Object>) sent.getParams().get("trigger");
    assertThat(trigger).containsEntry("trigger_price", "49000");
    assertThat(trigger).containsEntry("time_in_force", "GOOD_TILL_CANCEL");
  }

  @Test
  public void transportFailure_orderRecoveredFromOpenOrders_isReconciled() throws Exception {
    CryptoCom cryptoCom = mock(CryptoCom.class);
    when(cryptoCom.createOrder(any())).thenThrow(new IOException("connection reset"));
    when(cryptoCom.getOpenOrders(any()))
        .thenAnswer(invocation -> response(order("o-7000000000000000001", "client-ABC")));
    when(cryptoCom.getOrderHistory(any())).thenThrow(new IOException("should not be queried"));

    CryptoComTradeServiceRaw raw = newRaw(cryptoCom);

    CryptoComOrderPlacementResult placement =
        raw.createCryptoComOrder(
            "BTC_USDT", CryptoComOrderSide.BUY, CryptoComOrderType.LIMIT, "50000", "0.5", null, "client-ABC");

    assertThat(placement.getOutcome()).isEqualTo(CryptoComPlacementOutcome.RECONCILED);
    assertThat(placement.getOrderId()).isEqualTo("o-7000000000000000001");
    verify(cryptoCom, times(1)).createOrder(any());
  }

  @Test
  public void transportFailure_orderRecoveredInHistory_isReconciled() throws Exception {
    CryptoCom cryptoCom = mock(CryptoCom.class);
    when(cryptoCom.createOrder(any())).thenThrow(new IOException("timeout"));
    when(cryptoCom.getOpenOrders(any())).thenAnswer(invocation -> responseDataArray());
    when(cryptoCom.getOrderHistory(any()))
        .thenAnswer(
            invocation -> response(order("o-2", "client-ABC", "FILLED")));

    CryptoComTradeServiceRaw raw = newRaw(cryptoCom);

    CryptoComOrderPlacementResult placement =
        raw.createCryptoComOrder(
            "BTC_USDT", CryptoComOrderSide.BUY, CryptoComOrderType.LIMIT, "50000", "0.5", null, "client-ABC");

    assertThat(placement.getOutcome()).isEqualTo(CryptoComPlacementOutcome.RECONCILED);
    assertThat(placement.getOrderId()).isEqualTo("o-2");
  }

  @Test
  public void transportFailure_orderProvablyAbsent_isNotFound() throws Exception {
    CryptoCom cryptoCom = mock(CryptoCom.class);
    when(cryptoCom.createOrder(any())).thenThrow(new IOException("timeout"));
    when(cryptoCom.getOpenOrders(any())).thenAnswer(inv -> responseDataArray());
    when(cryptoCom.getOrderHistory(any())).thenAnswer(inv -> response(order("o-2", "other-client")));

    CryptoComTradeServiceRaw raw = newRaw(cryptoCom);

    CryptoComOrderPlacementResult placement =
        raw.createCryptoComOrder(
            "BTC_USDT", CryptoComOrderSide.BUY, CryptoComOrderType.LIMIT, "50000", "0.5", null, "client-ABC");

    assertThat(placement.getOutcome()).isEqualTo(CryptoComPlacementOutcome.NOT_FOUND);
    assertThat(placement.getOrderId()).isNull();
  }

  @Test
  public void transportFailure_reconciliationFails_isAmbiguousAndNeverReplayed() throws Exception {
    CryptoCom cryptoCom = mock(CryptoCom.class);
    when(cryptoCom.createOrder(any())).thenThrow(new IOException("connection reset"));
    when(cryptoCom.getOpenOrders(any())).thenThrow(new IOException("reconciliation unavailable"));

    CryptoComTradeServiceRaw raw = newRaw(cryptoCom);

    assertThatThrownBy(
            () ->
                raw.createCryptoComOrder(
                    "BTC_USDT",
                    CryptoComOrderSide.BUY,
                    CryptoComOrderType.LIMIT,
                    "50000",
                    "0.5",
                    null,
                    "client-ABC"))
        .isInstanceOf(CryptoComUnknownOrderOutcomeException.class)
        .hasMessageContaining("NOT automatically re-sent");

    verify(cryptoCom, times(1)).createOrder(any());
  }

  @Test
  public void transportFailure_withoutClientOid_isAmbiguous() throws Exception {
    CryptoCom cryptoCom = mock(CryptoCom.class);
    when(cryptoCom.createOrder(any())).thenThrow(new IOException("timeout"));

    CryptoComTradeServiceRaw raw = newRaw(cryptoCom);

    assertThatThrownBy(
            () ->
                raw.createCryptoComOrder(
                    "BTC_USDT", CryptoComOrderSide.BUY, CryptoComOrderType.LIMIT, "50000", "0.5", null, null))
        .isInstanceOf(CryptoComUnknownOrderOutcomeException.class);
  }

  @Test
  public void orderHistory_pagesBoundedByPageRepeats() throws Exception {
    CryptoCom cryptoCom = mock(CryptoCom.class);
    // Every full page returns the same 100 rows: the helper must stop on the repeat.
    when(cryptoCom.getOrderHistory(any())).thenAnswer(inv -> response(fullPageOf(order("o-loop", "c1"))));

    CryptoComTradeServiceRaw raw = newRaw(cryptoCom);

    List<CryptoComOrder> history =
        raw.getCryptoComOrderHistory("BTC_USDT", 1L, 2L, null);

    assertThat(history).hasSize(CryptoComTradeServiceRaw.DEFAULT_HISTORY_PAGE_SIZE);
    verify(cryptoCom, times(2)).getOrderHistory(any());
  }

  @Test
  public void orderHistory_respectsCallerLimitWithoutOverFetching() throws Exception {
    CryptoCom cryptoCom = mock(CryptoCom.class);
    when(cryptoCom.getOrderHistory(any()))
        .thenAnswer(
            invocation ->
                response(
                    order("o-1", "c1"),
                    order("o-2", "c2"),
                    order("o-3", "c3"),
                    order("o-4", "c4"),
                    order("o-5", "c5")));

    CryptoComTradeServiceRaw raw = newRaw(cryptoCom);

    List<CryptoComOrder> history = raw.getCryptoComOrderHistory("BTC_USDT", 1L, 2L, 3);

    assertThat(history).extracting(CryptoComOrder::getClientOid).containsExactly("c1", "c2", "c3");
    verify(cryptoCom, times(1)).getOrderHistory(any());
  }

  @Test
  public void userTrades_pagesUntilShortPage() throws Exception {
    CryptoCom cryptoCom = mock(CryptoCom.class);
    CryptoComRequest[] captured = new CryptoComRequest[2];
    when(cryptoCom.getUserTrades(any()))
        .thenAnswer(
            invocation -> {
              int idx = captured[0] == null ? 0 : 1;
              captured[idx] = invocation.getArgument(0);
              if (idx == 0) {
                ObjectNode[] page = new ObjectNode[CryptoComTradeServiceRaw.DEFAULT_HISTORY_PAGE_SIZE];
                for (int i = 0; i < page.length; i++) {
                  page[i] = userTrade("t-" + (i + 1));
                }
                return response(page);
              }
              return response(userTrade("t-last"));
            });

    CryptoComTradeServiceRaw raw = newRaw(cryptoCom);

    List<CryptoComUserTrade> trades = raw.getCryptoComUserTrades("BTC_USDT", 1L, 2L, null);

    assertThat(trades).hasSize(CryptoComTradeServiceRaw.DEFAULT_HISTORY_PAGE_SIZE + 1);
    assertThat(trades.get(trades.size() - 1).getTradeId()).isEqualTo("t-last");
    assertThat(captured[0].getParams()).containsEntry("page", 1).containsEntry("page_size", 100);
    assertThat(captured[1].getParams()).containsEntry("page", 2);
  }

  @Test
  public void missingCredentials_rejectedBeforeSigning() throws Exception {
    CryptoComTradeServiceRaw raw = newRaw(null, null);

    assertThatThrownBy(
            () ->
                raw.createCryptoComOrder(
                    "BTC_USDT",
                    CryptoComOrderSide.BUY,
                    CryptoComOrderType.MARKET,
                    null,
                    "100",
                    null,
                    null))
        .isInstanceOf(ExchangeSecurityException.class);
  }

  // --- helpers ---

  private ObjectNode order(String orderId, String clientOid) {
    ObjectNode node = mapper.createObjectNode();
    node.put("order_id", orderId);
    node.put("client_oid", clientOid);
    return node;
  }

  private ObjectNode order(String orderId, String clientOid, String status) {
    ObjectNode node = order(orderId, clientOid);
    node.put("status", status);
    return node;
  }

  private ObjectNode userTrade(String tradeId) {
    ObjectNode node = mapper.createObjectNode();
    node.put("trade_id", tradeId);
    return node;
  }

  private CryptoComResponse response(ObjectNode... orders) {
    CryptoComResponse response = new CryptoComResponse();
    response.setResult(responseData(orders));
    return response;
  }

  private CryptoComResponse responseDataArray() {
    CryptoComResponse response = new CryptoComResponse();
    ObjectNode result = mapper.createObjectNode();
    result.set("data", mapper.createArrayNode());
    response.setResult(result);
    return response;
  }

  private ObjectNode[] fullPageOf(ObjectNode row) {
    ObjectNode[] page = new ObjectNode[CryptoComTradeServiceRaw.DEFAULT_HISTORY_PAGE_SIZE];
    for (int i = 0; i < page.length; i++) {
      page[i] = row;
    }
    return page;
  }

  private ObjectNode responseData(ObjectNode... orders) {
    ObjectNode result = mapper.createObjectNode();
    ArrayNode data = mapper.createArrayNode();
    for (ObjectNode o : orders) {
      data.add(o);
    }
    result.set("data", data);
    return result;
  }

  private CryptoComRequest lastRequest;

  private CryptoComTradeServiceRaw newRaw() throws Exception {
    return newRaw("key", "secret");
  }

  private CryptoComTradeServiceRaw newRawWithAdvancedOrder() throws Exception {
    CryptoCom cryptoCom = mock(CryptoCom.class);
    when(cryptoCom.createAdvancedOrder(any()))
        .thenAnswer(
            invocation -> {
              lastRequest = invocation.getArgument(0);
              ObjectNode result = mapper.createObjectNode();
              result.put("order_id", "9");
              CryptoComResponse response = new CryptoComResponse();
              response.setResult(result);
              return response;
            });
    return newRaw(cryptoCom);
  }

  private CryptoComTradeServiceRaw newRaw(String apiKey, String secretKey) throws Exception {
    CryptoCom cryptoCom = mock(CryptoCom.class);
    when(cryptoCom.createOrder(any()))
        .thenAnswer(
            invocation -> {
              lastRequest = invocation.getArgument(0);
              ObjectNode result = mapper.createObjectNode();
              result.put("order_id", "1");
              CryptoComResponse response = new CryptoComResponse();
              response.setResult(result);
              return response;
            });
    return newRaw(cryptoCom, apiKey, secretKey);
  }

  private CryptoComTradeServiceRaw newRaw(CryptoCom cryptoCom) throws Exception {
    return newRaw(cryptoCom, "key", "secret");
  }

  private CryptoComTradeServiceRaw newRaw(CryptoCom cryptoCom, String apiKey, String secretKey)
      throws Exception {
    CryptoComExchange exchange = mock(CryptoComExchange.class);
    ExchangeSpecification spec = new ExchangeSpecification(CryptoComExchange.class);
    spec.setApiKey(apiKey);
    spec.setSecretKey(secretKey);
    when(exchange.getExchangeSpecification()).thenReturn(spec);
    when(exchange.getCryptoCom()).thenReturn(cryptoCom);
    when(exchange.nextRequestId()).thenReturn(1L);
    return new CryptoComTradeServiceRaw(exchange, new ResilienceRegistries());
  }
}