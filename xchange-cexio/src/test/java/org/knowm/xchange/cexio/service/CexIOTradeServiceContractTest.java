package org.knowm.xchange.cexio.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.cexio.CexIOAuthenticated;
import org.knowm.xchange.cexio.CexIOExchange;
import org.knowm.xchange.cexio.dto.trade.CexIOFullOrder;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParamCurrencyPair;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParams;

class CexIOTradeServiceContractTest {
  private CexIOTradeService service() {
    Exchange exchange = mock(Exchange.class);
    when(exchange.getExchangeSpecification()).thenReturn(new CexIOExchange().getDefaultExchangeSpecification());
    return spy(new CexIOTradeService(exchange));
  }

  @Test
  void openOrdersUsePairOnlyWhenPresent() throws Exception {
    CexIOTradeService service = service();
    doReturn(Collections.emptyList()).when(service).getCexIOOpenOrders();
    doReturn(Collections.emptyList()).when(service).getCexIOOpenOrders(CurrencyPair.BTC_USD);
    OpenOrdersParams params = service.createOpenOrdersParams();
    assertInstanceOf(OpenOrdersParamCurrencyPair.class, params);
    service.getOpenOrders(params);
    service.getOpenOrders();
    verify(service, times(2)).getCexIOOpenOrders();
    ((OpenOrdersParamCurrencyPair) params).setCurrencyPair(CurrencyPair.BTC_USD);
    service.getOpenOrders(params);
    verify(service).getCexIOOpenOrders(CurrencyPair.BTC_USD);
  }

  @Test
  void reusableMapperRetainsDynamicFeesAndIgnoresUnrecognizedFields() throws Exception {
    CexIOTradeService service = service();
    CexIOAuthenticated api = mock(CexIOAuthenticated.class);
    Field field = CexIOBaseService.class.getDeclaredField("cexIOAuthenticated");
    field.setAccessible(true);
    field.set(service, api);
    Map<String, Object> raw = new HashMap<>();
    raw.put("symbol1", "BTC");
    raw.put("symbol2", "USD");
    raw.put("id", "42");
    raw.put("time", 1234L);
    raw.put("ta:USD", "10");
    raw.put("tta:USD", "20");
    raw.put("fa:USD", "0.1");
    raw.put("tfa:USD", "0.2");
    raw.put("future_field", "ignored");
    when(api.getOrderRaw(any(), any())).thenReturn(raw);
    CexIOFullOrder first = service.getOrderFullDetail("42");
    CexIOFullOrder second = service.getOrderFullDetail("42");
    assertEquals("42", first.id);
    assertEquals("10", first.totalAmountMaker);
    assertEquals("20", first.totalAmountTaker);
    assertEquals("0.1", first.feeMaker);
    assertEquals("0.2", first.feeTaker);
    assertEquals(first.id, second.id);
  }
}
