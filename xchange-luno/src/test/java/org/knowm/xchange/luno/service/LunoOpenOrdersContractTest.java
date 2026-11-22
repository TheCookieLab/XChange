package org.knowm.xchange.luno.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.luno.LunoAPI;
import org.knowm.xchange.luno.LunoExchange;
import org.knowm.xchange.luno.dto.trade.LunoOrders;
import org.knowm.xchange.luno.dto.trade.State;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParamCurrencyPair;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParams;

class LunoOpenOrdersContractTest {
  @Test
  void factoryParamsUseProviderPairAndNullKeepsUnfilteredCall() throws Exception {
    Exchange exchange = mock(Exchange.class);
    when(exchange.getExchangeSpecification()).thenReturn(new LunoExchange().getDefaultExchangeSpecification());
    LunoTradeService service = new LunoTradeService(exchange);
    LunoAPI api = mock(LunoAPI.class);
    Field field = LunoBaseService.class.getDeclaredField("lunoAPI");
    field.setAccessible(true);
    field.set(service, api);
    when(api.listOrders(any(), any())).thenReturn(new LunoOrders(null));
    when(api.listOrders(State.PENDING, null)).thenReturn(new LunoOrders(null));
    OpenOrdersParams params = service.createOpenOrdersParams();
    assertInstanceOf(OpenOrdersParamCurrencyPair.class, params);
    service.getOpenOrders(params);
    service.getOpenOrders();
    verify(api, times(2)).listOrders(State.PENDING, null);
    ((OpenOrdersParamCurrencyPair) params).setCurrencyPair(new CurrencyPair("BTC/ZAR"));
    service.getOpenOrders(params);
    verify(api).listOrders(State.PENDING, "XBTZAR");
  }
}
