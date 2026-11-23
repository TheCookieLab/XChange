package org.knowm.xchange.bitso.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.bitso.BitsoExchange;
import org.knowm.xchange.bitso.dto.trade.BitsoOrder;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParamCurrencyPair;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParams;

class BitsoOpenOrdersContractTest {
  @Test
  void factoryParamsForwardPairAndUnfilteredCallsRemainUnfiltered() throws Exception {
    Exchange exchange = mock(Exchange.class);
    when(exchange.getExchangeSpecification()).thenReturn(new BitsoExchange().getDefaultExchangeSpecification());
    BitsoTradeService service = spy(new BitsoTradeService(exchange));
    doReturn(new BitsoOrder[0]).when(service).getBitsoOpenOrders();
    doReturn(new BitsoOrder[0]).when(service).getBitsoOpenOrders("btc_mxn", null);
    OpenOrdersParams params = service.createOpenOrdersParams();
    assertInstanceOf(OpenOrdersParamCurrencyPair.class, params);
    service.getOpenOrders(params);
    service.getOpenOrders();
    verify(service, times(2)).getBitsoOpenOrders();
    ((OpenOrdersParamCurrencyPair) params).setCurrencyPair(new CurrencyPair("BTC/MXN"));
    service.getOpenOrders(params);
    verify(service).getBitsoOpenOrders("btc_mxn", null);
  }
}
